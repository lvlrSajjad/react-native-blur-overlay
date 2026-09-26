package com.bluroverly;

import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Outline;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.RequiresApi;

/**
 * The live path, API 31+: re-record the target subtree into a {@link RenderNode}
 * and let the RenderThread blur it with a {@link RenderEffect}.
 *
 * <p>Isolated in its own class, and only ever touched through the out-of-line
 * helpers in {@link SajjadBlurOverlayView}, so that ART never has to resolve
 * {@code RenderEffect} on a device that does not have it.
 *
 * <p>Phase 0 measured the re-record at 0.17–0.21ms median on two mid-range
 * SoCs, and the blur itself at ~2ms at full resolution against under 1ms at
 * half — which is why live blur defaults {@code downsampling} to 2.
 */
@RequiresApi(31)
final class LiveBlur {

  private final RenderNode captureNode = new RenderNode("react-native-blur-overlay");

  /**
   * Holds the capture node, clipped to the overlay's rounded shape.
   *
   * <p>It exists because a {@code clipPath} on the canvas does not work here:
   * HWUI silently drops a {@code drawRenderNode} recorded under a
   * non-rectangular clip — the blur simply does not appear, with nothing in the
   * logs. The snapshot path, which draws a bitmap, survives the same clip. So
   * the rounding is carried as this node's own outline instead, which HWUI
   * applies on the RenderThread.
   *
   * <p>It wraps the capture node rather than clipping it directly so that the
   * corner is cut at the overlay's resolution and not at the downsampled one.
   * Re-recorded only when the shape changes: a node that references another
   * node follows that node's re-records on its own.
   */
  private final RenderNode clipNode = new RenderNode("react-native-blur-overlay/clip");

  private final Outline outline = new Outline();

  /** A {@link Glass}, or null. Untyped so ART never resolves it below API 33. */
  private Object glass;

  /** The lens chained after the frost; rebuilt only when either changes. */
  private RenderEffect glassChain;

  private RenderEffect glassChainLens;
  private RenderEffect glassChainFrost;

  private int clipWidth = -1;
  private int clipHeight = -1;
  private float clipRadius = -1f;

  /**
   * Constructing a RenderEffect is not free and the object is immutable, so it
   * is rebuilt only when one of the values baked into it actually changes.
   */
  private RenderEffect effect;

  private float effectRadius = -1f;
  private float effectBrightness = 0f;
  private float effectSaturation = 1f;

  /**
   * Records the target into the capture node, scaled down by {@code inputScale}
   * and offset so that the node holds exactly the region behind the overlay,
   * grown by {@code outset} on every side.
   *
   * @param dx the overlay's x offset within the target, in pixels
   * @param dy the overlay's y offset within the target, in pixels
   * @return whether the capture succeeded
   */
  boolean capture(
      SajjadBlurTargetView target,
      int width,
      int height,
      float dx,
      float dy,
      float outset,
      float inputScale,
      float radius,
      float brightness,
      float saturation,
      boolean wantGlass,
      float corner,
      float density) {
    final boolean useGlass = wantGlass && Build.VERSION.SDK_INT >= GLASS_SDK;

    if (useGlass) {
      // Measured on a Galaxy A22 at 90Hz: full resolution collapses to 100%
      // jank, half holds 90fps. Not a caller's choice to make.
      inputScale = Math.min(inputScale, GLASS_MAX_INPUT_SCALE);
      // No capture margin of its own: the lens only ever samples inward, and
      // an 8dp margin made the layer HWUI re-renders every frame ~30% larger
      // (330x75 against live's 315x60 on the demo tab bar), which is most of
      // what glass cost over live. `captureOutset` still applies if set.
    }

    final int scaledWidth = Math.max(1, Math.round((width + 2f * outset) * inputScale));
    final int scaledHeight = Math.max(1, Math.round((height + 2f * outset) * inputScale));

    captureNode.setPosition(0, 0, scaledWidth, scaledHeight);

    final RecordingCanvas canvas = captureNode.beginRecording(scaledWidth, scaledHeight);

    try {
      canvas.scale(inputScale, inputScale);
      canvas.translate(outset - dx, outset - dy);
      target.setCapturing(true);
      target.draw(canvas);
    } catch (RuntimeException error) {
      return false;
    } finally {
      target.setCapturing(false);
      captureNode.endRecording();
    }

    final RenderEffect frost = effectFor(Math.max(0.5f, radius * inputScale), brightness, saturation);

    captureNode.setRenderEffect(
        useGlass
            ? glassEffect(
                frost, scaledWidth, scaledHeight, width, height, outset, inputScale, corner,
                density)
            : frost);

    // The node carries the upscale itself. Scaling the canvas around a
    // drawRenderNode() instead was tried in the spike and is not reliable.
    captureNode.setPivotX(0f);
    captureNode.setPivotY(0f);
    captureNode.setScaleX(1f / inputScale);
    captureNode.setScaleY(1f / inputScale);
    captureNode.setTranslationX(-outset);
    captureNode.setTranslationY(-outset);

    return true;
  }

  // --- glass --------------------------------------------------------------

  /** The first API level with {@code RuntimeShader}. */
  static final int GLASS_SDK = Build.VERSION_CODES.TIRAMISU;

  private static final float GLASS_MAX_INPUT_SCALE = 0.5f;

  /**
   * The lens band, in dp: 24, as Kyant0/AndroidLiquidGlass's bottom tab bar
   * has it, clamped to 0.4 of the short side so a small overlay is not all
   * edge. Absolute rather than a fraction of the pane, because a real sheet of
   * glass has a physical edge and a bigger pane does not get a bigger one.
   */
  private static float glassBand(int width, int height, float density) {
    return Math.min(24f * density, Math.min(width, height) * 0.4f);
  }

  /**
   * How far the border reaches in. QWEA0/Liquid-Glass-Android defaults to about
   * 3.3x its band (160px over 48px): that is what squeezes whole rows of content
   * into the rim. 2.5x here, and never more than 0.7 of the short side, so a
   * tab bar folds in its own opposite half rather than sampling past it.
   */
  private static float glassPull(int width, int height, float density) {
    return Math.min(glassBand(width, height, density) * 2.5f, Math.min(width, height) * 0.7f);
  }

  @RequiresApi(GLASS_SDK)
  private RenderEffect glassEffect(
      RenderEffect frost,
      int scaledWidth,
      int scaledHeight,
      int width,
      int height,
      float outset,
      float inputScale,
      float corner,
      float density) {
    if (glass == null) {
      glass = new Glass();
    }

    final float band = glassBand(width, height, density) * inputScale;
    final RenderEffect lens =
        ((Glass) glass)
            .lens(
                width * inputScale,
                height * inputScale,
                outset * inputScale,
                scaledWidth,
                scaledHeight,
                corner * inputScale,
                band,
                glassPull(width, height, density) * inputScale,
                // QWEA0 defaults to 0.10 and its hero shot uses 0.16.
                0.15f,
                1.6f,
                // Below a capture pixel on purpose: antialiased, it reads thinner
                // than the 1dp a whole pixel would be at half resolution.
                0.75f * density * inputScale,
                3f * density * inputScale);

    // createChainEffect(outer, inner) runs inner first: blur, then the lens.
    if (glassChain == null || lens != glassChainLens || frost != glassChainFrost) {
      glassChain = RenderEffect.createChainEffect(lens, frost);
      glassChainLens = lens;
      glassChainFrost = frost;
    }

    return glassChain;
  }

  /**
   * @param radius the overlay's corner radius in pixels, or 0 for a square one
   * @return whether anything was drawn
   */
  boolean draw(Canvas canvas, int width, int height, float radius) {
    if (!(canvas instanceof RecordingCanvas) || !captureNode.hasDisplayList()) {
      return false;
    }

    if (width != clipWidth
        || height != clipHeight
        || radius != clipRadius
        || !clipNode.hasDisplayList()) {
      // An outset capture node is wider than the overlay; a RenderNode clips to
      // its own bounds, so positioning this one at the overlay's size is what
      // keeps the bleed from spilling out.
      clipNode.setPosition(0, 0, width, height);

      if (radius > 0f) {
        outline.setRoundRect(0, 0, width, height, radius);
        clipNode.setOutline(outline);
        clipNode.setClipToOutline(true);
      } else {
        clipNode.setOutline(null);
        clipNode.setClipToOutline(false);
      }

      final RecordingCanvas recording = clipNode.beginRecording(width, height);

      try {
        recording.drawRenderNode(captureNode);
      } finally {
        clipNode.endRecording();
      }

      clipWidth = width;
      clipHeight = height;
      clipRadius = radius;
    }

    ((RecordingCanvas) canvas).drawRenderNode(clipNode);

    return true;
  }

  void release() {
    clipNode.discardDisplayList();
    clipNode.setOutline(null);
    clipNode.setClipToOutline(false);
    clipWidth = -1;
    clipHeight = -1;
    clipRadius = -1f;
    captureNode.discardDisplayList();
    captureNode.setRenderEffect(null);
    glassChain = null;
    glassChainLens = null;
    glassChainFrost = null;
    effect = null;
    effectRadius = -1f;
  }

  private RenderEffect effectFor(float radius, float brightness, float saturation) {
    if (effect != null
        && radius == effectRadius
        && brightness == effectBrightness
        && saturation == effectSaturation) {
      return effect;
    }

    final RenderEffect blur = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP);

    // Saturation and brightness are applied after the blur, matching what the
    // snapshot path does with its ColorMatrixColorFilter on the finished bitmap.
    final ColorMatrix matrix = SajjadBlurOverlayView.colorMatrix(brightness, saturation);
    effect =
        matrix == null
            ? blur
            : RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(matrix), blur);
    effectRadius = radius;
    effectBrightness = brightness;
    effectSaturation = saturation;

    return effect;
  }
}
