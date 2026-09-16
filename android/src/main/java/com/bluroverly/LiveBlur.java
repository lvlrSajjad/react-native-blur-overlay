package com.bluroverly;

import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;

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
   * Constructing a RenderEffect is not free and the object is immutable, so it
   * is rebuilt only when one of the values baked into it actually changes.
   */
  private RenderEffect effect;

  private float effectRadius = -1f;
  private float effectBrightness = 0f;

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
      float brightness) {
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

    captureNode.setRenderEffect(effectFor(Math.max(0.5f, radius * inputScale), brightness));

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

  /** @return whether anything was drawn. */
  boolean draw(Canvas canvas, int width, int height) {
    if (!(canvas instanceof RecordingCanvas) || !captureNode.hasDisplayList()) {
      return false;
    }

    // An outset capture node is wider than the overlay, and a parent that does
    // not clip its children would happily let it spill out.
    canvas.save();
    canvas.clipRect(0, 0, width, height);
    ((RecordingCanvas) canvas).drawRenderNode(captureNode);
    canvas.restore();

    return true;
  }

  void release() {
    captureNode.discardDisplayList();
    captureNode.setRenderEffect(null);
    effect = null;
    effectRadius = -1f;
  }

  private RenderEffect effectFor(float radius, float brightness) {
    if (effect != null && radius == effectRadius && brightness == effectBrightness) {
      return effect;
    }

    final RenderEffect blur = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP);

    // Brightness is applied after the blur, matching what the snapshot path
    // does with its ColorMatrixColorFilter on the finished bitmap.
    effect = brightness == 0f ? blur : RenderEffect.createColorFilterEffect(filter(brightness), blur);
    effectRadius = radius;
    effectBrightness = brightness;

    return effect;
  }

  private static ColorMatrixColorFilter filter(float brightness) {
    final float offset = Math.max(-255f, Math.min(255f, brightness));

    return new ColorMatrixColorFilter(
        new ColorMatrix(
            new float[] {
              1, 0, 0, 0, offset,
              0, 1, 0, 0, offset,
              0, 0, 1, 0, offset,
              0, 0, 0, 1, 0
            }));
  }
}
