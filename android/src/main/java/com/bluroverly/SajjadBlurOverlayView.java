package com.bluroverly;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.views.view.ReactViewGroup;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Draws a blurred copy of whatever sits behind this view.
 *
 * <p>Two ways of getting there, chosen by {@code blurMode}:
 *
 * <ul>
 *   <li><b>snapshot</b> (the default, and all this view could do before 3.1):
 *       once, when the overlay appears, draw the activity's content view into a
 *       software bitmap, blur it on a worker thread with {@link StackBlur} and
 *       set it as the background. The overlay excludes itself from that capture
 *       with the {@code capturing} flag below. Cheap, and frozen.
 *   <li><b>live</b> (API 31+): every drawn frame, re-record a
 *       {@code <BlurTarget />} subtree into a {@link android.graphics.RenderNode}
 *       and hand it to the RenderThread with a blur {@code RenderEffect}. The
 *       target must not contain this overlay — see {@link SajjadBlurTargetView}.
 * </ul>
 *
 * <p>Live blur falls back to a snapshot below API 31, when no target is found,
 * and if a capture ever throws.
 */
public class SajjadBlurOverlayView extends ReactViewGroup
    implements ViewTreeObserver.OnPreDrawListener {

  private static final String TAG = "BlurOverlay";

  static final String MODE_LIVE = "live";

  /** The first API level with {@code RenderEffect}. */
  private static final int LIVE_SDK = Build.VERSION_CODES.S;

  /** One shared worker: blurring is memory heavy, and overlaps are pointless. */
  private static final ExecutorService BLUR_EXECUTOR =
      Executors.newSingleThreadExecutor(
          runnable -> {
            final Thread thread = new Thread(runnable, "react-native-blur-overlay");
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
          });

  private int radius = 20;
  private float downsampling = 1f;
  private float brightness = 0f;
  private String blurMode = "snapshot";
  private String blurTargetId = SajjadBlurTargetView.DEFAULT_ID;
  private int maxUpdateFps = 30;
  private float captureOutset = 0f;

  private boolean captureScheduled = false;
  private boolean capturing = false;
  /** Guards against a stale blur landing after a newer one was requested. */
  private int generation = 0;

  /** A {@link LiveBlur}, or null. Untyped so ART never resolves it below API 31. */
  @Nullable private Object live;

  private boolean liveRunning;
  /** Live mode is on but has nothing to capture, so the snapshot has to cover. */
  private boolean liveStalled;
  /** Reused: captureFrame() runs on every drawn frame and should not allocate. */
  private final int[] targetLocation = new int[2];

  private final int[] ownLocation = new int[2];

  private boolean liveDrawn;
  private boolean liveCaptured;
  @Nullable private WeakReference<SajjadBlurTargetView> targetReference;
  private int lastTargetGeneration;
  private int lastOffsetX;
  private int lastOffsetY;
  private int lastWidth;
  private int lastHeight;
  private long lastCaptureNanos;
  private boolean catchUpScheduled;

  /** `adb shell setprop log.tag.BlurOverlay DEBUG` to see how often it captures. */
  private boolean debugLogging;

  private int liveFrames;
  private int liveCaptures;

  private boolean warnedNoTarget;
  private boolean warnedAncestor;
  private boolean warnedUnsupported;

  private final Runnable catchUp =
      () -> {
        catchUpScheduled = false;

        if (liveRunning) {
          invalidate();
        }
      };

  public SajjadBlurOverlayView(Context context) {
    super(context);
    // Live blur draws in onDraw(), which a ViewGroup skips by default.
    setWillNotDraw(false);
  }

  public void setBlurRadius(int radius) {
    if (this.radius != radius) {
      this.radius = radius;
      onBlurInputChanged();
    }
  }

  public void setDownsamplingFactor(float downsampling) {
    if (this.downsampling != downsampling) {
      this.downsampling = downsampling;
      onBlurInputChanged();
    }
  }

  public void setBrightnessOffset(float brightness) {
    if (this.brightness != brightness) {
      this.brightness = brightness;
      onBlurInputChanged();
    }
  }

  public void setBlurMode(@Nullable String mode) {
    final String next = mode == null || mode.isEmpty() ? "snapshot" : mode;

    if (!next.equals(blurMode)) {
      blurMode = next;
      syncMode();
    }
  }

  public void setBlurTargetId(@Nullable String id) {
    final String next = id == null || id.isEmpty() ? SajjadBlurTargetView.DEFAULT_ID : id;

    if (!next.equals(blurTargetId)) {
      blurTargetId = next;
      targetReference = null;
      warnedNoTarget = false;
      warnedAncestor = false;
      onBlurInputChanged();
    }
  }

  public void setMaxUpdateFps(int fps) {
    if (this.maxUpdateFps != fps) {
      this.maxUpdateFps = fps;
      onBlurInputChanged();
    }
  }

  public void setCaptureOutset(float outset) {
    if (this.captureOutset != outset) {
      this.captureOutset = outset;
      onBlurInputChanged();
    }
  }

  /** Drops the blur, for when the view is recycled. */
  public void reset() {
    generation++;
    captureScheduled = false;
    stopLive();
    setBackground(null);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    syncMode();
  }

  @Override
  protected void onDetachedFromWindow() {
    stopLive();
    super.onDetachedFromWindow();
  }

  @Override
  protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);

    if (!liveOwnsBackdrop()) {
      scheduleBlur();
    }
  }

  // Both entry points are guarded: a view without a background is drawn through
  // dispatchDraw(), one with a background through draw(). This is the snapshot
  // path's self-exclusion, and it only works because that capture runs on a
  // software canvas — Phase 0 proved it is destructive on a hardware one, which
  // is why the live path never sets the flag.

  @Override
  public void draw(Canvas canvas) {
    if (capturing) {
      return;
    }

    super.draw(canvas);
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    if (capturing) {
      return;
    }

    super.dispatchDraw(canvas);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    if (liveRunning && live != null && liveDraw(live, canvas, getWidth(), getHeight())) {
      liveDrawn = true;
    }
  }

  // --- live ---------------------------------------------------------------

  /**
   * Runs once per frame the window draws — and not at all otherwise, which is
   * what keeps a live overlay free on a still screen.
   */
  @Override
  public boolean onPreDraw() {
    if (liveRunning) {
      captureFrame();
    }

    return true;
  }

  private void syncMode() {
    final boolean wantLive =
        MODE_LIVE.equals(blurMode) && Build.VERSION.SDK_INT >= LIVE_SDK && isAttachedToWindow();

    if (wantLive && !liveRunning) {
      live = newLiveBlur();
      liveRunning = true;
      liveStalled = false;
      liveDrawn = false;
      liveCaptured = false;
      debugLogging = Log.isLoggable(TAG, Log.DEBUG);
      liveFrames = 0;
      liveCaptures = 0;
      getViewTreeObserver().addOnPreDrawListener(this);
      invalidate();
    } else if (!wantLive && liveRunning) {
      stopLive();
    }

    if (!liveRunning) {
      if (MODE_LIVE.equals(blurMode) && Build.VERSION.SDK_INT < LIVE_SDK && !warnedUnsupported) {
        warnedUnsupported = true;
        Log.i(
            TAG,
            "blurMode=\"live\" needs API "
                + LIVE_SDK
                + " or newer; this device is API "
                + Build.VERSION.SDK_INT
                + ", so the overlay stays on the snapshot blur.");
      }

      scheduleBlur();
    }
  }

  private void stopLive() {
    if (!liveRunning) {
      return;
    }

    liveRunning = false;
    liveStalled = false;
    liveDrawn = false;
    liveCaptured = false;
    targetReference = null;
    removeCallbacks(catchUp);
    catchUpScheduled = false;

    if (isAttachedToWindow()) {
      getViewTreeObserver().removeOnPreDrawListener(this);
    }

    if (live != null) {
      liveRelease(live);
      live = null;
    }

    invalidate();
  }

  /**
   * Whether the live path is both running and actually producing a backdrop.
   *
   * <p>Not the same as {@code liveRunning}: live mode with no target in the
   * window is running and showing nothing, which is exactly when the snapshot
   * has to take over. Guarding the snapshot on {@code liveRunning} instead
   * leaves that overlay transparent.
   */
  private boolean liveOwnsBackdrop() {
    return liveRunning && !liveStalled;
  }

  /** A prop that feeds the blur changed: redo it, whichever path is running. */
  private void onBlurInputChanged() {
    if (liveOwnsBackdrop()) {
      liveCaptured = false;
      invalidate();
    } else {
      scheduleBlur();
    }
  }

  private void captureFrame() {
    final SajjadBlurTargetView target = resolveTarget();

    if (target == null) {
      fallBackToSnapshot();
      return;
    }

    final int width = getWidth();
    final int height = getHeight();

    if (width <= 0 || height <= 0) {
      return;
    }

    if (debugLogging && ++liveFrames % 120 == 0) {
      Log.d(TAG, "live: " + liveCaptures + " captures over " + liveFrames + " drawn frames");
    }

    target.getLocationInWindow(targetLocation);
    getLocationInWindow(ownLocation);

    final int offsetX = ownLocation[0] - targetLocation[0];
    final int offsetY = ownLocation[1] - targetLocation[1];
    final int targetGeneration = target.getDrawGeneration();

    final boolean changed =
        !liveCaptured
            || targetGeneration != lastTargetGeneration
            || offsetX != lastOffsetX
            || offsetY != lastOffsetY
            || width != lastWidth
            || height != lastHeight;

    if (!changed) {
      return;
    }

    // Cadence cap. A blur that is one frame stale is far less noticeable than
    // a scroll that stutters, so this is a knob rather than a constant.
    if (liveCaptured && maxUpdateFps > 0) {
      final long interval = 1_000_000_000L / maxUpdateFps;
      final long elapsed = System.nanoTime() - lastCaptureNanos;

      if (elapsed < interval) {
        // Nothing else will ask for a frame once the scroll stops, so make sure
        // the last state of the backdrop is not the one we skipped.
        if (!catchUpScheduled) {
          catchUpScheduled = true;
          postDelayed(catchUp, Math.max(1L, (interval - elapsed) / 1_000_000L));
        }

        return;
      }
    }

    final float factor = Math.max(1f, downsampling);
    final float inputScale = 1f / factor;
    // An outset that would collapse the capture is not worth honouring.
    final float outset = Math.max(captureOutset, 1f - Math.min(width, height) / 2f);

    if (!liveCapture(
        live, target, width, height, offsetX, offsetY, outset, inputScale, radius, brightness)) {
      Log.w(TAG, "Could not capture the blur target, falling back to a snapshot.");
      stopLive();
      blurMode = "snapshot";
      scheduleBlur();
      return;
    }

    liveCaptures++;
    liveCaptured = true;
    liveStalled = false;
    lastTargetGeneration = targetGeneration;
    lastOffsetX = offsetX;
    lastOffsetY = offsetY;
    lastWidth = width;
    lastHeight = height;
    lastCaptureNanos = System.nanoTime();

    if (getBackground() != null) {
      // Leaving a stale snapshot underneath would show through wherever the
      // live capture is translucent.
      setBackground(null);
    }

    if (!liveDrawn) {
      // Only needed until the overlay's own display list holds a reference to
      // the capture node. After that, re-recording the capture node is enough
      // and invalidating every frame would be pure waste.
      invalidate();
    }
  }

  /**
   * The target to capture, or null if there is nothing usable — in which case
   * the overlay quietly stays on the snapshot blur.
   */
  @Nullable
  private SajjadBlurTargetView resolveTarget() {
    final SajjadBlurTargetView cached = targetReference == null ? null : targetReference.get();

    if (cached != null
        && cached.isAttachedToWindow()
        && cached.getRootView() == getRootView()
        && blurTargetId.equals(cached.getBlurTargetId())) {
      return cached;
    }

    final SajjadBlurTargetView target = BlurTargetRegistry.find(blurTargetId, this);

    if (target == null) {
      targetReference = null;
      return null;
    }

    // Phase 0's finding, guarded in code: capturing an ancestor of the overlay
    // cannot work on a hardware canvas. It does not merely look wrong — either
    // the overlay blanks itself or HWUI drops the backdrop — and neither failure
    // shows up in logcat, so catch it here rather than leaving it to a visual
    // check.
    if (isDescendantOf(target)) {
      if (!warnedAncestor) {
        warnedAncestor = true;
        Log.w(
            TAG,
            "The <BlurTarget id=\""
                + blurTargetId
                + "\"> contains this overlay, so it cannot be captured: an overlay cannot blur a "
                + "subtree it is part of. Move the overlay outside the target (a sibling, or "
                + "further up the tree). Falling back to the snapshot blur.");
      }

      targetReference = null;
      return null;
    }

    targetReference = new WeakReference<>(target);

    return target;
  }

  private boolean isDescendantOf(View ancestor) {
    for (ViewParent parent = getParent(); parent != null; parent = parent.getParent()) {
      if (parent == ancestor) {
        return true;
      }
    }

    return false;
  }

  private void fallBackToSnapshot() {
    liveStalled = true;
    liveCaptured = false;

    if (!warnedNoTarget) {
      warnedNoTarget = true;
      Log.w(
          TAG,
          "blurMode=\"live\" found no <BlurTarget id=\""
              + blurTargetId
              + "\"> in this window. Wrap the content to blur in one, or give both the same id. "
              + "Falling back to the snapshot blur.");
    }

    if (getBackground() == null) {
      scheduleBlur();
    }
  }

  // Out of line and guarded, so that loading this class on an older device
  // never makes ART resolve LiveBlur or RenderEffect.

  @RequiresApi(LIVE_SDK)
  private static Object newLiveBlur() {
    return new LiveBlur();
  }

  @RequiresApi(LIVE_SDK)
  private static boolean liveCapture(
      Object live,
      SajjadBlurTargetView target,
      int width,
      int height,
      float dx,
      float dy,
      float outset,
      float inputScale,
      float radius,
      float brightness) {
    return ((LiveBlur) live)
        .capture(target, width, height, dx, dy, outset, inputScale, radius, brightness);
  }

  @RequiresApi(LIVE_SDK)
  private static boolean liveDraw(Object live, Canvas canvas, int width, int height) {
    return ((LiveBlur) live).draw(canvas, width, height);
  }

  @RequiresApi(LIVE_SDK)
  private static void liveRelease(Object live) {
    ((LiveBlur) live).release();
  }

  // --- snapshot -----------------------------------------------------------

  private void scheduleBlur() {
    if (captureScheduled || liveOwnsBackdrop()) {
      return;
    }

    captureScheduled = true;
    // Wait for the current layout pass, so the snapshot sees the final screen.
    post(this::captureAndBlur);
  }

  private void captureAndBlur() {
    captureScheduled = false;

    if (liveOwnsBackdrop()) {
      return;
    }

    final View root = findCaptureRoot();

    if (root == null
        || !isAttachedToWindow()
        || getWidth() <= 0
        || getHeight() <= 0
        || root.getWidth() <= 0
        || root.getHeight() <= 0) {
      // onSizeChanged() will ask again once there is something to capture.
      return;
    }

    final float factor = Math.max(1f, downsampling);
    final int width = Math.max(1, Math.round(getWidth() / factor));
    final int height = Math.max(1, Math.round(getHeight() / factor));

    final Bitmap snapshot;

    try {
      snapshot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    } catch (OutOfMemoryError error) {
      Log.w(TAG, "Could not allocate the snapshot, try a higher `downsampling`", error);
      return;
    }

    final int[] rootLocation = new int[2];
    root.getLocationInWindow(rootLocation);
    getLocationInWindow(ownLocation);

    final Canvas canvas = new Canvas(snapshot);
    // Scale first, then offset, so that the snapshot holds exactly the region
    // of the screen this view covers.
    canvas.scale(1f / factor, 1f / factor);
    canvas.translate(rootLocation[0] - ownLocation[0], rootLocation[1] - ownLocation[1]);

    capturing = true;

    try {
      root.draw(canvas);
    } catch (RuntimeException error) {
      Log.w(TAG, "Could not capture the screen behind the overlay", error);
      return;
    } finally {
      capturing = false;
    }

    final int requested = ++generation;
    final int blurRadius = Math.max(1, Math.round(radius / factor));

    BLUR_EXECUTOR.execute(
        () -> {
          try {
            StackBlur.blur(snapshot, blurRadius);
          } catch (RuntimeException | OutOfMemoryError error) {
            Log.w(TAG, "Could not blur the snapshot", error);
            return;
          }

          post(
              () -> {
                if (requested != generation || liveOwnsBackdrop()) {
                  // A newer snapshot is already on its way.
                  return;
                }

                applySnapshot(snapshot);
              });
        });
  }

  private void applySnapshot(Bitmap snapshot) {
    final BitmapDrawable drawable = new BitmapDrawable(getResources(), snapshot);

    if (brightness != 0f) {
      final float offset = Math.max(-255f, Math.min(255f, brightness));

      drawable.setColorFilter(
          new ColorMatrixColorFilter(
              new ColorMatrix(
                  new float[] {
                    1, 0, 0, 0, offset,
                    0, 1, 0, 0, offset,
                    0, 0, 1, 0, offset,
                    0, 0, 0, 1, 0
                  })));
    }

    setBackground(drawable);
  }

  /**
   * The view to capture: the activity's content view, which leaves out the
   * status and navigation bars.
   */
  @Nullable
  private View findCaptureRoot() {
    Activity activity = null;

    final Context context = getContext();

    if (context instanceof ThemedReactContext) {
      activity = ((ThemedReactContext) context).getCurrentActivity();
    }

    if (activity == null) {
      Context current = context;

      while (activity == null && current instanceof ContextWrapper) {
        if (current instanceof Activity) {
          activity = (Activity) current;
        } else {
          current = ((ContextWrapper) current).getBaseContext();
        }
      }
    }

    if (activity != null) {
      final View content = activity.getWindow().getDecorView().findViewById(android.R.id.content);

      if (content != null) {
        return content;
      }
    }

    final View root = getRootView();

    return root == this ? null : root;
  }
}
