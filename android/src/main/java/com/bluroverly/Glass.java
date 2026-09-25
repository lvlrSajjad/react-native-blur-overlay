package com.bluroverly;

import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;

import androidx.annotation.RequiresApi;

/**
 * The glass lens, API 33: an AGSL pass over the frosted blur that bends it
 * inward along the border and lights the border with a hairline.
 *
 * <p>Applied after the blur, as one pass over the whole overlay: the interior
 * passes through untouched, and only a band along the border is displaced. It
 * draws on two open-source Android implementations of iOS 26 Liquid Glass:
 *
 * <ul>
 *   <li>The lens profile — a quarter-circle height map, {@code 1 - sqrt(1 - x²)},
 *       pulling the backdrop inward so that content folds back on itself at the
 *       border — follows Kyant0/AndroidLiquidGlass (Copyright 2025 Kyant,
 *       Apache License 2.0), {@code RoundedRectRefractionShaderString}.
 *   <li>The highlight — two equal lobes, facing and opposite the light, on a
 *       hairline, plus a soft inward glow on the lit side only — and the
 *       per-channel dispersion follow QWEA0/Liquid-Glass-Android (Copyright
 *       (c) 2025-2026 pandadog, MIT License), {@code GlassLensRenderer}.
 * </ul>
 *
 * <p>Both were rewritten for a uniform-radius round rect, a downscaled capture
 * with a bleed margin, and no touch or tint terms. See the glass sections of
 * docs/live-blur/RESULTS.md for how the shape was chosen.
 */
@RequiresApi(33)
final class Glass {

  static final String AGSL =
      "uniform shader content;\n"
          + "uniform float2 uSize;\n" // the overlay, in capture pixels
          + "uniform float uBleed;\n" // capture margin on every side
          + "uniform float2 uBounds;\n" // the whole capture, for clamping samples
          + "uniform float uRadius;\n"
          + "uniform float uHeight;\n" // width of the lens band
          + "uniform float uAmount;\n" // displacement at the border
          + "uniform float uDispersion;\n"
          + "uniform float2 uLight;\n" // direction the light travels
          + "uniform float uSpecular;\n"
          + "uniform float uLine;\n" // hairline width
          + "uniform float uGlow;\n" // glow width, inward of the hairline
          + "\n"
          + "float sdRoundRect(float2 p, float2 h, float r) {\n"
          + "  float2 q = abs(p) - h + r;\n"
          + "  return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;\n"
          + "}\n"
          + "\n"
          + "half4 main(float2 coord) {\n"
          + "  float2 h = uSize * 0.5;\n"
          + "  float2 p = coord - float2(uBleed) - h;\n"
          + "  float d = sdRoundRect(p, h, uRadius);\n"
          + "  if (d < -uHeight || d > 1.0) {\n"
          + "    return content.eval(coord);\n"
          + "  }\n"
          // AGSL derivatives are not dependable, so the normal is a numerical
          // gradient of the distance field.
          + "  float2 n = float2(\n"
          + "      sdRoundRect(p + float2(1.0, 0.0), h, uRadius) - sdRoundRect(p - float2(1.0, 0.0), h, uRadius),\n"
          + "      sdRoundRect(p + float2(0.0, 1.0), h, uRadius) - sdRoundRect(p - float2(0.0, 1.0), h, uRadius));\n"
          + "  float nl = length(n);\n"
          + "  n = nl > 0.0001 ? n / nl : float2(0.0, -1.0);\n"
          // x: 0 at the inner edge of the band, 1 at the border
          + "  float x = clamp(1.0 + d / uHeight, 0.0, 1.0);\n"
          + "  float bend = (1.0 - sqrt(max(0.0, 1.0 - x * x))) * uAmount;\n"
          + "  float2 off = -n * bend;\n"
          + "  float2 lo = float2(0.5);\n"
          + "  float2 hi = uBounds - 0.5;\n"
          + "  half4 g = content.eval(clamp(coord + off, lo, hi));\n"
          + "  half r = content.eval(clamp(coord + off * (1.0 - uDispersion * x), lo, hi)).r;\n"
          + "  half b = content.eval(clamp(coord + off * (1.0 + uDispersion * x), lo, hi)).b;\n"
          + "  half4 col = half4(r, g.g, b, g.a);\n"
          + "\n"
          // Lobes tightened from QWEA0's 4.5 to 7: the light gathers at the two
          // corners it catches instead of washing round the curve.
          + "  float facing = dot(n, -uLight);\n"
          + "  float lobeF = pow(max(facing, 0.0), 7.0);\n"
          + "  float lobeB = pow(max(-facing, 0.0), 7.0);\n"
          + "  float inside = -d;\n"
          + "  float hair = clamp(1.0 - abs(inside - uLine * 0.5) / uLine, 0.0, 1.0);\n"
          + "  float glow = clamp((inside - uLine) / uLine, 0.0, 1.0)\n"
          + "      * pow(clamp(1.0 - (inside - uLine) / uGlow, 0.0, 1.0), 1.5);\n"
          + "  float spec = (hair * 0.70 * (lobeF + lobeB) + glow * 0.10 * lobeF) * uSpecular;\n"
          + "  col.rgb = min(col.rgb + half3(spec) * col.a, half3(col.a));\n"
          + "  return col;\n"
          + "}\n";

  /**
   * Constructing a RuntimeShader compiles the AGSL — doing it per frame cost 4ms
   * of UI thread on a Helio G80, 20x the whole capture. So it is compiled once,
   * and the (immutable) RenderEffect is rebuilt only when a uniform changes,
   * which during a scroll is never.
   */
  private RuntimeShader shader;

  private RenderEffect cached;
  private float[] last;

  RenderEffect lens(
      float width,
      float height,
      float bleed,
      float boundsWidth,
      float boundsHeight,
      float corner,
      float band,
      float amount,
      float dispersion,
      float specular,
      float line,
      float glow) {
    final float[] now = {
      width, height, bleed, boundsWidth, boundsHeight, corner, band, amount, dispersion,
      specular, line, glow
    };

    if (cached != null && java.util.Arrays.equals(now, last)) {
      return cached;
    }

    if (shader == null) {
      shader = new RuntimeShader(AGSL);
    }

    shader.setFloatUniform("uSize", width, height);
    shader.setFloatUniform("uBleed", bleed);
    shader.setFloatUniform("uBounds", boundsWidth, boundsHeight);
    shader.setFloatUniform("uRadius", corner);
    shader.setFloatUniform("uHeight", band);
    shader.setFloatUniform("uAmount", amount);
    shader.setFloatUniform("uDispersion", dispersion);
    // Light from the upper left, about 30 degrees above horizontal, as iOS has it.
    shader.setFloatUniform("uLight", 0.866f, 0.5f);
    shader.setFloatUniform("uSpecular", specular);
    shader.setFloatUniform("uLine", line);
    shader.setFloatUniform("uGlow", glow);
    cached = RenderEffect.createRuntimeShaderEffect(shader, "content");
    last = now;

    return cached;
  }
}
