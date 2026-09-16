package com.bluroverly;

import android.content.Context;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.function.Consumer;

/**
 * Live blur for an overlay that lives in a Dialog window — which is what React
 * Native's {@code <Modal />} renders into.
 *
 * <p>No capture can reach across windows. A modal's content is a separate
 * surface from the activity's, so {@link SajjadBlurTargetView} and every other
 * capture-based technique is structurally unable to blur the app behind a
 * modal; this is the wall {@code expo-blur} hits too. The system, however, can
 * do it for free: since API 31 a window may ask for everything behind it to be
 * blurred, and SurfaceFlinger composites that live, at no per-frame cost to us.
 *
 * <p>We reach it through {@link WindowManager.LayoutParams#FLAG_BLUR_BEHIND}
 * and {@link WindowManager.LayoutParams#setBlurBehindRadius(int)} on the root
 * view's layout params. {@code Window#setBackgroundBlurRadius} is the other
 * half of the same platform feature and would let the blur be masked to a
 * shape, but it needs an {@link android.view.Window} and there is no public
 * route from a {@link View} to the window hosting it. React Native added one
 * ({@code ExtraWindowEventListener}) after the 0.80 this library supports, so
 * the layout-params route is the one that stays inside the "public SDK only"
 * rule on every supported version.
 *
 * <p>Consequence worth knowing: this is a <em>window</em>-wide effect. It blurs
 * everything behind the window, not merely what is behind this view, so
 * {@link #apply} refuses when the overlay does not cover the window rather than
 * blurring pixels the caller never asked for.
 */
@RequiresApi(Build.VERSION_CODES.S)
final class WindowBlur {

  /** The radius is on the window. */
  static final int OK = 0;

  /** Not in a window we can set flags on — nothing was changed. */
  static final int NOT_A_WINDOW = 1;

  /** Cross-window blur is off: unsupported GPU, battery saver, or a user setting. */
  static final int DISABLED = 2;

  /** The overlay covers only part of the window, so a window-wide blur would be wrong. */
  static final int PARTIAL = 3;

  /** The window manager refused the update. */
  static final int FAILED = 4;

  /** Nothing is laid out yet, so coverage cannot be judged. Ask again later. */
  static final int NOT_READY = 5;

  private final View owner;
  private final Runnable onEnabledChanged;
  private final Consumer<Boolean> enabledListener;

  /** The root we last put a radius on, so that it can be taken off again. */
  @Nullable private View blurredRoot;

  @Nullable private WindowManager listeningOn;
  private int appliedRadius;

  WindowBlur(View owner, Runnable onEnabledChanged) {
    this.owner = owner;
    this.onEnabledChanged = onEnabledChanged;
    // The single-argument overload calls back on the main thread, which is
    // where the overlay needs to re-decide anyway.
    this.enabledListener = enabled -> this.onEnabledChanged.run();
  }

  /**
   * Puts {@code radius} px of blur behind this overlay's window, or says why
   * it could not.
   */
  int apply(int radius) {
    final View root = owner.getRootView();
    final ViewGroup.LayoutParams layout = root.getLayoutParams();

    if (!(layout instanceof WindowManager.LayoutParams)) {
      return NOT_A_WINDOW;
    }

    final WindowManager manager = managerFor(root);

    if (manager == null) {
      return NOT_A_WINDOW;
    }

    // Watch from here on, so that battery saver turning the feature off mid-way
    // reaches the overlay and it can put the snapshot back.
    listen(manager);

    if (!manager.isCrossWindowBlurEnabled()) {
      clear();
      return DISABLED;
    }

    final int coverage = coverage(root);

    if (coverage != OK) {
      clear();
      return coverage;
    }

    final WindowManager.LayoutParams params = (WindowManager.LayoutParams) layout;

    if (blurredRoot == root
        && appliedRadius == radius
        && (params.flags & WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0) {
      return OK;
    }

    params.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
    params.setBlurBehindRadius(radius);

    if (!update(manager, root, params)) {
      return FAILED;
    }

    blurredRoot = root;
    appliedRadius = radius;

    return OK;
  }

  /** Takes the blur back off the window, leaving it as it was found. */
  void clear() {
    final View root = blurredRoot;

    if (root == null) {
      return;
    }

    blurredRoot = null;
    appliedRadius = 0;

    final ViewGroup.LayoutParams layout = root.getLayoutParams();

    if (!(layout instanceof WindowManager.LayoutParams)) {
      return;
    }

    final WindowManager manager = managerFor(root);

    if (manager == null) {
      return;
    }

    final WindowManager.LayoutParams params = (WindowManager.LayoutParams) layout;
    params.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
    params.setBlurBehindRadius(0);

    update(manager, root, params);
  }

  /** Clears the blur and stops listening. The overlay is done with this. */
  void release() {
    clear();

    if (listeningOn != null) {
      listeningOn.removeCrossWindowBlurEnabledListener(enabledListener);
      listeningOn = null;
    }
  }

  /**
   * {@link #OK} when the overlay reaches every edge of the window it is in.
   *
   * <p>Insets are deducted first: a dialog that is not drawing edge to edge is
   * laid out below the status bar and above the navigation bar, so its
   * full-screen overlay legitimately stops short of the window's own bounds.
   *
   * <p>{@link #NOT_READY} rather than {@link #PARTIAL} before the first layout:
   * an overlay that has no size yet covers nothing, and saying so out loud
   * would make every modal warn once on the way up.
   */
  private int coverage(View root) {
    final int width = owner.getWidth();
    final int height = owner.getHeight();

    if (width <= 0 || height <= 0 || root.getWidth() <= 0 || root.getHeight() <= 0) {
      return NOT_READY;
    }

    int left = 0;
    int top = 0;
    int right = root.getWidth();
    int bottom = root.getHeight();

    final WindowInsets insets = root.getRootWindowInsets();

    if (insets != null) {
      final Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
      left += bars.left;
      top += bars.top;
      right -= bars.right;
      bottom -= bars.bottom;
    }

    final int[] location = new int[2];
    owner.getLocationInWindow(location);

    // A pixel of slack: layout rounding should not decide this.
    final boolean covers =
        location[0] <= left + 1
            && location[1] <= top + 1
            && location[0] + width >= right - 1
            && location[1] + height >= bottom - 1;

    return covers ? OK : PARTIAL;
  }

  private void listen(WindowManager manager) {
    if (listeningOn == manager) {
      return;
    }

    if (listeningOn != null) {
      listeningOn.removeCrossWindowBlurEnabledListener(enabledListener);
    }

    listeningOn = manager;
    manager.addCrossWindowBlurEnabledListener(enabledListener);
  }

  private static boolean update(WindowManager manager, View root, WindowManager.LayoutParams p) {
    try {
      manager.updateViewLayout(root, p);
      return true;
    } catch (IllegalArgumentException | IllegalStateException error) {
      // The dialog can be dismissed out from under us between the last layout
      // and this call, which takes its decor view off the window manager.
      return false;
    }
  }

  @Nullable
  private static WindowManager managerFor(View root) {
    // The decor view's own context is the Dialog's, so its window manager is
    // the one the decor view was added through.
    final Context context = root.getContext();

    return context == null ? null : context.getSystemService(WindowManager.class);
  }
}
