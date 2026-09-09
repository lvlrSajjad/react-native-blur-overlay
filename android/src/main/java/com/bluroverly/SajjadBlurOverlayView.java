package com.bluroverly;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.views.view.ReactViewGroup;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Draws a blurred snapshot of whatever sits behind this view as its background.
 *
 * <p>The snapshot is cropped to this view's position on screen, so an overlay
 * that only covers part of the screen blurs only that part, and it is taken
 * fresh every time the overlay is shown. This view excludes itself (and its
 * children) from the snapshot while capturing.
 */
public class SajjadBlurOverlayView extends ReactViewGroup {

  private static final String TAG = "BlurOverlay";

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

  private boolean captureScheduled = false;
  private boolean capturing = false;
  /** Guards against a stale blur landing after a newer one was requested. */
  private int generation = 0;

  public SajjadBlurOverlayView(Context context) {
    super(context);
  }

  public void setBlurRadius(int radius) {
    if (this.radius != radius) {
      this.radius = radius;
      scheduleBlur();
    }
  }

  public void setDownsamplingFactor(float downsampling) {
    if (this.downsampling != downsampling) {
      this.downsampling = downsampling;
      scheduleBlur();
    }
  }

  public void setBrightnessOffset(float brightness) {
    if (this.brightness != brightness) {
      this.brightness = brightness;
      scheduleBlur();
    }
  }

  /** Drops the snapshot, for when the view is recycled. */
  public void reset() {
    generation++;
    captureScheduled = false;
    setBackground(null);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    scheduleBlur();
  }

  @Override
  protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    scheduleBlur();
  }

  // Both entry points are guarded: a view without a background is drawn through
  // dispatchDraw(), one with a background through draw().

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

  private void scheduleBlur() {
    if (captureScheduled) {
      return;
    }

    captureScheduled = true;
    // Wait for the current layout pass, so the snapshot sees the final screen.
    post(this::captureAndBlur);
  }

  private void captureAndBlur() {
    captureScheduled = false;

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
    final int[] ownLocation = new int[2];
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
                if (requested != generation) {
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
