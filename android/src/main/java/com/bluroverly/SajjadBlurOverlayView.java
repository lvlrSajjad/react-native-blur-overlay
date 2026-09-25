package com.bluroverly;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.facebook.react.uimanager.BackgroundStyleApplicator;
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
 *       software bitmap and blur it on a worker thread with {@link StackBlur}.
 *       The overlay excludes itself from that capture with the {@code capturing}
 *       flag below. Cheap, and frozen — unless {@code snapshotUpdateFps} asks
 *       for it to be repeated.
 *   <li><b>live</b> (API 31+): every drawn frame, re-record a
 *       {@code <BlurTarget />} subtree into a {@link android.graphics.RenderNode}
 *       and hand it to the RenderThread with a blur {@code RenderEffect}. The
 *       target must not contain this overlay — see {@link SajjadBlurTargetView}.
 * </ul>
 *
 * <p>An overlay inside a Dialog window — which is what an RN {@code <Modal />}
 * is — takes a third route in live mode: no capture can cross a window
 * boundary, so it asks the system to blur behind the whole window instead. See
 * {@link WindowBlur}.
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
  private int snapshotUpdateFps = 0;

  private boolean captureScheduled = false;
  private boolean capturing = false;

  /**
   * The finished snapshot blur.
   *
   * <p>Held rather than handed to {@link #setBackground}, which is what 3.0 did:
   * a background drawable is React Native's to own, and replacing it is what
   * made {@code borderRadius}, {@code borderWidth} and {@code backgroundColor}
   * do nothing on the Android overlay. Drawing it ourselves, under everything
   * React Native draws for this view, gives all three back.
   */
  @Nullable private Bitmap snapshot;

  @Nullable private Paint snapshotPaint;
  /** Reused: the snapshot is drawn scaled up to the overlay's bounds. */
  private final Rect snapshotBounds = new Rect();

  /** Reused: read per draw to find the corner radius the live path clips to. */
  private final Outline shape = new Outline();

  /** When the capture behind the current snapshot started, for the re-blur cadence. */
  private long snapshotStartedAt;

  private boolean reblurScheduled;

  /**
   * The bitmap the previous snapshot used, kept to capture the next one into.
   *
   * <p>Two buffers, never one: between a capture and the blurred result landing
   * the worker owns its bitmap while {@link #snapshot} is still being drawn, so
   * reusing a single one would tear. The displayed snapshot becomes the spare
   * when it is replaced.
   */
  @Nullable private Bitmap spare;

  /** Something other than this overlay has drawn since the last capture. */
  private boolean drewSinceCapture = true;

  /** The re-blur timer fired on a still screen and is waiting for a draw. */
  private boolean reblurDue;

  /** Swallows the one frame that {@link #applySnapshot} itself causes. */
  private boolean ignoreOwnDraw;

  private boolean preDrawRegistered;
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
  private int snapshotCaptures;

  private boolean warnedNoTarget;
  private boolean warnedAncestor;
  private boolean warnedUnsupported;
  private boolean warnedWindowBlurOff;
  private boolean warnedWindowBlurPartial;

  /** A {@link WindowBlur}, or null. Untyped so ART never resolves it below API 31. */
  @Nullable private Object windowBlur;

  /** The system is blurring behind this overlay's window right now. */
  private boolean windowBlurring;

  /** This overlay is hosted in a Dialog window rather than the activity's. */
  private boolean inDialogWindow;

  /** The last thing {@link WindowBlur#apply} said. */
  private int windowBlurStatus = WindowBlur.NOT_READY;

  private boolean reportScheduled;

  /**
   * Explains a live blur that did not happen — a second after the fact.
   *
   * <p>A modal takes a few frames to reach its final size and insets, and on
   * the way there the overlay genuinely does not cover its window and genuinely
   * has no target. Warning about either the moment it is true fills logcat with
   * lines that are wrong by the time anyone reads them, so the reasons are
   * collected and only the ones that outlive the layout get printed.
   */
  private final Runnable reportLiveFallback =
      () -> {
        reportScheduled = false;

        if (!windowBlurring && windowBlurStatus == WindowBlur.DISABLED && !warnedWindowBlurOff) {
          warnedWindowBlurOff = true;
          Log.w(
              TAG,
              "blurMode=\"live\" inside a <Modal /> blurs the app behind the modal through the "
                  + "system, and this device has cross-window blur off — some GPUs never support "
                  + "it, and battery saver turns it off everywhere. Falling back to the snapshot "
                  + "blur, which still shows the app behind the modal, frozen.");
        }

        if (!windowBlurring && windowBlurStatus == WindowBlur.PARTIAL && !warnedWindowBlurPartial) {
          warnedWindowBlurPartial = true;
          Log.w(
              TAG,
              "blurMode=\"live\" inside a <Modal /> blurs behind the whole window — the system "
                  + "has no way to blur behind part of one — but this overlay covers only part of "
                  + "it, so it would blur pixels you did not ask for. Give the overlay the whole "
                  + "modal, or leave it on the snapshot blur, which is scoped to the overlay's "
                  + "own bounds.");
        }

        if (liveStalled && !warnedNoTarget) {
          warnedNoTarget = true;
          Log.w(
              TAG,
              "blurMode=\"live\" found no <BlurTarget id=\""
                  + blurTargetId
                  + "\"> in this window. A <Modal /> is a window of its own, so an overlay inside "
                  + "one can only capture a <BlurTarget> that is inside the same <Modal />. "
                  + "Falling back to the snapshot blur.");
        }
      };

  private final Runnable catchUp =
      () -> {
        catchUpScheduled = false;

        if (liveRunning) {
          invalidate();
        }
      };

  /** The optional periodic re-blur of the snapshot — see {@code snapshotUpdateFps}. */
  private final Runnable reblur =
      () -> {
        reblurScheduled = false;

        if (!drewSinceCapture) {
          // Nothing has drawn since the last snapshot, so a new one would be
          // identical. Wait for the screen to move rather than spend a
          // full-screen capture and a blur on proving it has not.
          reblurDue = true;
          return;
        }

        scheduleBlur();
      };

  public SajjadBlurOverlayView(Context context) {
    super(context);
    // Both blur paths draw in draw(), which a ViewGroup without a background
    // skips entirely — it goes straight to dispatchDraw().
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

  public void setSnapshotUpdateFps(int fps) {
    if (this.snapshotUpdateFps == fps) {
      return;
    }

    this.snapshotUpdateFps = fps;
    removeCallbacks(reblur);
    reblurScheduled = false;
    reblurDue = false;
    syncPreDrawListener();

    if (fps > 0) {
      // Turning it on mid-flight should not wait for the next capture to land,
      // because on a frozen snapshot there is no next capture.
      scheduleBlur();
    }
  }

  /** Drops the blur, for when the view is recycled. */
  public void reset() {
    generation++;
    captureScheduled = false;
    stopLive();
    releaseWindowBlur();
    stopPeriodicReblur();
    clearSnapshot();
    spare = null;
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    syncMode();
  }

  @Override
  protected void onDetachedFromWindow() {
    stopLive();
    stopPeriodicReblur();
    // The window is not ours, so leave it as it was found — and do it before
    // super(), while the dialog's decor view is still on the window manager.
    releaseWindowBlur();
    super.onDetachedFromWindow();
  }

  @Override
  protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);

    // Whether a window blur is appropriate depends on whether this overlay
    // covers the window, so the decision has to be re-made at every size —
    // posted, because it can end in updateViewLayout() on the host window and
    // that is not a thing to do from inside a layout pass.
    post(this::syncMode);

    if (!liveOwnsBackdrop()) {
      scheduleBlur();
    }
  }

  // Both entry points are guarded, because which of the two HWUI uses depends
  // on flags this view does not fully control. This is the snapshot path's
  // self-exclusion, and it only works because that capture runs on a software
  // canvas — Phase 0 proved it is destructive on a hardware one, which is why
  // the live path never sets the flag.

  @Override
  public void draw(Canvas canvas) {
    if (capturing) {
      return;
    }

    // The blur goes in *before* super.draw(), so that everything React Native
    // draws for this view lands on top of it: a translucent `backgroundColor`
    // tints the blur, a `borderWidth` frames it. Painting it as the background
    // drawable instead — which is what 3.0 did — meant replacing the drawable
    // that renders all three.
    drawBackdrop(canvas);

    super.draw(canvas);
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    if (capturing) {
      return;
    }

    super.dispatchDraw(canvas);
  }

  /**
   * Draws whichever of the three backdrops this overlay currently owns, clipped
   * to the shape React Native styled the overlay with.
   *
   * <p>{@link BackgroundStyleApplicator#clipToPaddingBox} is the same helper a
   * {@code ReactViewGroup} uses for {@code overflow: "hidden"}, so the overlay's
   * own {@code borderRadius} — per-corner and logical radii included — and its
   * border widths shape the blur exactly as they shape anything else.
   *
   * <p>The clip is recorded into the overlay's display list rather than re-run
   * per frame: once a live capture node is referenced there, it re-records on
   * the RenderThread without this view drawing again.
   */
  private void drawBackdrop(Canvas canvas) {
    final int width = getWidth();
    final int height = getHeight();
    final boolean liveReady = liveRunning && live != null;

    if (width <= 0 || height <= 0 || (!windowBlurring && !liveReady && snapshot == null)) {
      return;
    }

    // The live path is clipped by the capture node's own outline rather than by
    // the canvas — see LiveBlur#draw for why a canvas clip cannot work there.
    if (liveReady && liveDraw(live, canvas, width, height, cornerRadius())) {
      liveDrawn = true;
      return;
    }

    final int save = canvas.save();

    try {
      BackgroundStyleApplicator.clipToPaddingBox(this, canvas);

      if (windowBlurring) {
        // The blur itself is behind the window, so all this view contributes is
        // the tint. Anything opaque here would hide the blur instead.
        drawBrightnessScrim(canvas);
        return;
      }

      // Live mode can be on and still have nothing to show — no target, or no
      // capture yet. The snapshot underneath is what keeps the overlay from
      // being a transparent hole until it does.
      if (snapshot != null) {
        snapshotBounds.set(0, 0, width, height);
        canvas.drawBitmap(snapshot, null, snapshotBounds, snapshotPaint);
      }
    } finally {
      canvas.restoreToCount(save);
    }
  }

  /**
   * The overlay's corner radius in pixels, or 0 if it has none the live path
   * can use.
   *
   * <p>Read from the outline React Native's background drawable publishes,
   * which is also what casts the view's shadow, so it is the same shape the
   * platform already believes the overlay has. An outline that is not a uniform
   * round rect — per-corner radii — cannot be a RenderNode clip at all, so a
   * live blur stays square there while a snapshot, clipped by path, does not.
   */
  private float cornerRadius() {
    final ViewOutlineProvider provider = getOutlineProvider();

    if (provider == null) {
      return 0f;
    }

    shape.setEmpty();
    provider.getOutline(this, shape);

    final float radius = shape.getRadius();

    return radius > 0f && shape.canClip() ? radius : 0f;
  }

  /**
   * {@code brightness} for the window-blur path.
   *
   * <p>An approximation, and knowingly so: the other two paths own their pixels
   * and shift them with a {@link ColorMatrix}, which can brighten past white.
   * This one does not own anything — the blur belongs to the compositor — so the
   * same number becomes a scrim over it. Equal-looking at the tints an overlay
   * actually uses, and it degrades sensibly at the extremes.
   */
  private void drawBrightnessScrim(Canvas canvas) {
    if (brightness == 0f) {
      return;
    }

    final float clamped = Math.max(-255f, Math.min(255f, brightness));
    final int alpha = Math.round(Math.abs(clamped));
    final int tone = clamped < 0f ? 0 : 255;

    canvas.drawColor(Color.argb(alpha, tone, tone, tone));
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
      return true;
    }

    // Applying a snapshot invalidates this view, which draws a frame, which
    // would look like content moving and ask for another snapshot — forever.
    if (ignoreOwnDraw) {
      ignoreOwnDraw = false;
      return true;
    }

    drewSinceCapture = true;

    if (reblurDue) {
      // The timer came due while the screen was still. Now it is not.
      reblurDue = false;
      scheduleBlur();
    }

    return true;
  }

  /**
   * Keeps the pre-draw listener registered for exactly as long as one of the
   * two paths needs it.
   *
   * <p>For live blur it is what drives the capture. For the periodic re-blur it
   * is what makes a still screen free: nothing draws, so the listener never
   * fires, so no snapshot is taken. Without it, {@code snapshotUpdateFps} would
   * recapture and re-blur the whole screen on a timer whether or not anything
   * had changed.
   */
  private void syncPreDrawListener() {
    final boolean want =
        isAttachedToWindow()
            && (liveRunning || (snapshotUpdateFps > 0 && !liveOwnsBackdrop()));

    if (want == preDrawRegistered) {
      return;
    }

    preDrawRegistered = want;

    if (want) {
      getViewTreeObserver().addOnPreDrawListener(this);
    } else if (isAttachedToWindow()) {
      getViewTreeObserver().removeOnPreDrawListener(this);
    }
  }

  private void syncMode() {
    // A window blur, where one is possible, is both cheaper and more capable
    // than anything this view can capture, so it gets first refusal.
    syncWindowBlur();

    final boolean wantLive =
        !windowBlurring
            && MODE_LIVE.equals(blurMode)
            && Build.VERSION.SDK_INT >= LIVE_SDK
            && isAttachedToWindow();

    if (wantLive && !liveRunning) {
      live = newLiveBlur();
      liveRunning = true;
      liveStalled = false;
      liveDrawn = false;
      liveCaptured = false;
      debugLogging = Log.isLoggable(TAG, Log.DEBUG);
      liveFrames = 0;
      liveCaptures = 0;
      preDrawRegistered = true;
      getViewTreeObserver().addOnPreDrawListener(this);
      invalidate();
    } else if (!wantLive && liveRunning) {
      stopLive();
    }

    syncPreDrawListener();

    if (!liveRunning && !windowBlurring) {
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

    if (preDrawRegistered && isAttachedToWindow()) {
      preDrawRegistered = false;
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
    return windowBlurring || (liveRunning && !liveStalled);
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

    // Leaving a stale snapshot underneath would show through wherever the live
    // capture is translucent, and a periodic re-blur behind a live overlay is
    // pure waste.
    stopPeriodicReblur();
    clearSnapshot();

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

    if (inDialogWindow) {
      // In a modal this is usually not the caller's mistake at all — the window
      // blur just has not engaged yet — so it goes through the deferred report,
      // which prints it only if it is still true a second from now.
      scheduleLiveFallbackReport();
    } else if (!warnedNoTarget) {
      warnedNoTarget = true;
      Log.w(
          TAG,
          "blurMode=\"live\" found no <BlurTarget id=\""
              + blurTargetId
              + "\"> in this window. Wrap the content to blur in one, or give both the same id. "
              + "Falling back to the snapshot blur.");
    }

    if (snapshot == null) {
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
  private static boolean liveDraw(
      Object live, Canvas canvas, int width, int height, float radius) {
    return ((LiveBlur) live).draw(canvas, width, height, radius);
  }

  @RequiresApi(LIVE_SDK)
  private static void liveRelease(Object live) {
    ((LiveBlur) live).release();
  }

  // --- window blur --------------------------------------------------------

  /**
   * Decides whether the system should blur behind this overlay's window.
   *
   * <p>Only in live mode, and only in a Dialog window: in the activity's own
   * window the capture path is both scoped to the overlay and already proven,
   * while a window blur there would blur the whole screen.
   */
  private void syncWindowBlur() {
    final boolean wantWindowBlur =
        MODE_LIVE.equals(blurMode)
            && Build.VERSION.SDK_INT >= LIVE_SDK
            && isAttachedToWindow()
            && isInDialogWindow();

    if (!wantWindowBlur) {
      releaseWindowBlur();
      return;
    }

    if (windowBlur == null) {
      windowBlur = newWindowBlur(this, () -> post(this::syncMode));
    }

    // `radius` is in pre-downsampling pixels — i.e. already in screen pixels —
    // and the system does its own downscaling, so `downsampling` has nothing to
    // say here and is ignored rather than applied twice.
    final int status = windowBlurApply(windowBlur, Math.max(1, radius));
    // These comparisons are against compile-time constants, so they inline to
    // literals and never make ART resolve WindowBlur on an older device.
    final boolean blurring = status == WindowBlur.OK;

    if (blurring != windowBlurring) {
      windowBlurring = blurring;

      if (blurring) {
        // The compositor owns the backdrop now. A snapshot left underneath
        // would sit on top of it, and there is nothing across a window
        // boundary for the capture path to record.
        stopLive();
        generation++;
        captureScheduled = false;
        stopPeriodicReblur();
        clearSnapshot();
      }

      invalidate();
    }

    windowBlurStatus = status;

    if (status == WindowBlur.DISABLED || status == WindowBlur.PARTIAL) {
      scheduleLiveFallbackReport();
    }
  }

  private void scheduleLiveFallbackReport() {
    if (reportScheduled) {
      return;
    }

    reportScheduled = true;
    postDelayed(reportLiveFallback, 1000L);
  }

  /** Puts the window back the way it was found, and stops watching it. */
  private void releaseWindowBlur() {
    removeCallbacks(reportLiveFallback);
    reportScheduled = false;

    if (windowBlur != null) {
      windowBlurRelease(windowBlur);
      windowBlur = null;
    }

    if (windowBlurring) {
      windowBlurring = false;
      invalidate();
      scheduleBlur();
    }
  }

  /**
   * Whether this overlay is in a window other than the activity's — which for
   * a React Native app means a {@code <Modal />}, since that is a Dialog.
   */
  private boolean isInDialogWindow() {
    final Activity activity = findActivity();

    if (activity == null || activity.getWindow() == null) {
      // Nothing to compare against, so which window this is cannot be told.
      return false;
    }

    inDialogWindow = getRootView() != activity.getWindow().getDecorView();

    return inDialogWindow;
  }

  @RequiresApi(LIVE_SDK)
  private static Object newWindowBlur(View owner, Runnable onEnabledChanged) {
    return new WindowBlur(owner, onEnabledChanged);
  }

  @RequiresApi(LIVE_SDK)
  private static int windowBlurApply(Object blur, int radius) {
    return ((WindowBlur) blur).apply(radius);
  }

  @RequiresApi(LIVE_SDK)
  private static void windowBlurRelease(Object blur) {
    ((WindowBlur) blur).release();
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

    final Bitmap bitmap;

    if (Log.isLoggable(TAG, Log.DEBUG)) {
      // `adb shell setprop log.tag.BlurOverlay DEBUG`. On a still screen with
      // snapshotUpdateFps set, this must stop counting — that is the whole
      // point of the pre-draw gate.
      Log.d(TAG, "snapshot: capture #" + (++snapshotCaptures));
    }

    snapshotStartedAt = SystemClock.uptimeMillis();
    drewSinceCapture = false;

    // At `snapshotUpdateFps` 30 this runs thirty times a second, so the
    // previous snapshot's bitmap is reused rather than allocating a new one
    // each time and leaving the collector to clean up after it.
    if (spare != null && spare.getWidth() == width && spare.getHeight() == height) {
      bitmap = spare;
      spare = null;
      bitmap.eraseColor(Color.TRANSPARENT);
    } else {
      spare = null;

      try {
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      } catch (OutOfMemoryError error) {
        Log.w(TAG, "Could not allocate the snapshot, try a higher `downsampling`", error);
        return;
      }
    }

    final int[] rootLocation = new int[2];
    root.getLocationInWindow(rootLocation);
    getLocationInWindow(ownLocation);

    final Canvas canvas = new Canvas(bitmap);
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
            StackBlur.blur(bitmap, blurRadius);
          } catch (RuntimeException | OutOfMemoryError error) {
            Log.w(TAG, "Could not blur the snapshot", error);
            return;
          }

          post(
              () -> {
                if (requested != generation || liveOwnsBackdrop()) {
                  // A newer snapshot is already on its way. Hand the bitmap
                  // back rather than dropping it on the floor.
                  if (spare == null) {
                    spare = bitmap;
                  }

                  return;
                }

                applySnapshot(bitmap);
              });
        });
  }

  private void applySnapshot(Bitmap bitmap) {
    final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

    if (brightness != 0f) {
      final float offset = Math.max(-255f, Math.min(255f, brightness));

      paint.setColorFilter(
          new ColorMatrixColorFilter(
              new ColorMatrix(
                  new float[] {
                    1, 0, 0, 0, offset,
                    0, 1, 0, 0, offset,
                    0, 0, 1, 0, offset,
                    0, 0, 0, 1, 0
                  })));
    }

    if (snapshot != null && snapshot != bitmap && spare == null) {
      spare = snapshot;
    }

    snapshot = bitmap;
    snapshotPaint = paint;
    // The frame this causes is ours, not the content moving.
    ignoreOwnDraw = true;
    invalidate();

    schedulePeriodicReblur();
  }

  private void clearSnapshot() {
    if (snapshot == null) {
      return;
    }

    if (spare == null) {
      spare = snapshot;
    }

    snapshot = null;
    snapshotPaint = null;
    invalidate();
  }

  /**
   * Queues the next snapshot, when {@code snapshotUpdateFps} asks for one.
   *
   * <p>The interval runs from the start of the previous capture rather than
   * from when its blur landed, so the rate asked for is the rate delivered
   * instead of that minus however long a blur took.
   */
  private void schedulePeriodicReblur() {
    if (reblurScheduled || snapshotUpdateFps <= 0 || liveOwnsBackdrop() || !isAttachedToWindow()) {
      return;
    }

    final long interval = 1000L / snapshotUpdateFps;
    final long due = snapshotStartedAt + interval - SystemClock.uptimeMillis();

    reblurScheduled = true;
    postDelayed(reblur, Math.max(1L, due));
  }

  private void stopPeriodicReblur() {
    reblurDue = false;

    if (!reblurScheduled) {
      return;
    }

    removeCallbacks(reblur);
    reblurScheduled = false;
  }

  /**
   * The view to capture: the activity's content view, which leaves out the
   * status and navigation bars.
   */
  @Nullable
  private View findCaptureRoot() {
    final Activity activity = findActivity();

    if (activity != null) {
      final View content = activity.getWindow().getDecorView().findViewById(android.R.id.content);

      if (content != null) {
        return content;
      }
    }

    final View root = getRootView();

    return root == this ? null : root;
  }

  /** The activity hosting this overlay, whichever window it ended up in. */
  @Nullable
  private Activity findActivity() {
    final Context context = getContext();

    if (context instanceof ThemedReactContext) {
      final Activity activity = ((ThemedReactContext) context).getCurrentActivity();

      if (activity != null) {
        return activity;
      }
    }

    Context current = context;

    while (current instanceof ContextWrapper) {
      if (current instanceof Activity) {
        return (Activity) current;
      }

      current = ((ContextWrapper) current).getBaseContext();
    }

    return null;
  }
}
