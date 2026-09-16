package com.bluespike;

import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;

import androidx.annotation.RequiresApi;

/**
 * v3. Two changes that matter more than any amount of parameter twiddling:
 *
 * 1. This shader now runs on a SHARP capture, not a blurred one. The frosted interior is a
 *    separate RenderNode drawn underneath (see BlurPanel). A real glass rim refracts a
 *    crisp image; refracting an already-blurred one is why v2 looked like a smudge.
 *    The shader is transparent in the interior so the frost shows through.
 *
 * 2. The bevel is Snell's law over a circular edge profile rather than an invented curve.
 *    sin(incidence) ramps 0 -> 1 across the rim, and the displacement is
 *    thickness * tan(incidence - refraction). That blows up near the border, which is
 *    exactly the hard squeeze that reads as thick glass.
 *
 * Also supersamples along the displacement, because the displacement gradient is steep
 * enough at the rim to alias badly with a single tap.
 */
@RequiresApi(33)
final class Glass {

    private Glass() {}

    static final String AGSL =
        "uniform shader content;\n" +
        "uniform float2 uSize;\n" +
        "uniform float uBleed;\n" +
        "uniform float uRadius;\n" +
        "uniform float uBand;\n" +       // rim width == bevel thickness
        "uniform float uThickness;\n" +  // how far the bevel displaces
        "uniform float uIor;\n" +
        "uniform float uSpecular;\n" +
        "uniform float uRim;\n" +
        "uniform float uTint;\n" +
        "\n" +
        "float sdRoundRect(float2 p, float2 h, float r) {\n" +
        "    float2 q = abs(p) - h + r;\n" +
        "    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;\n" +
        "}\n" +
        "\n" +
        "half4 main(float2 coord) {\n" +
        "    float2 h = uSize * 0.5;\n" +
        "    float2 p = coord - float2(uBleed, uBleed) - h;\n" +
        "    float d = sdRoundRect(p, h, uRadius);\n" +
        "\n" +
        // interior and exterior are both the frosted node's business
        "    if (d < -uBand || d > 1.0) {\n" +
        "        return half4(0.0, 0.0, 0.0, 0.0);\n" +
        "    }\n" +
        "\n" +
        "    float e = 1.0;\n" +
        "    float2 n = normalize(float2(\n" +
        "        sdRoundRect(p + float2(e, 0.0), h, uRadius) - sdRoundRect(p - float2(e, 0.0), h, uRadius),\n" +
        "        sdRoundRect(p + float2(0.0, e), h, uRadius) - sdRoundRect(p - float2(0.0, e), h, uRadius)));\n" +
        "\n" +
        // t: 0 at the inner lip of the rim, 1 at the border
        "    float t = clamp((d + uBand) / uBand, 0.0, 1.0);\n" +
        "\n" +
        // Snell over a circular bevel: sin(theta_i) = t
        "    float si = clamp(t, 0.0, 0.995);\n" +
        "    float thetaI = asin(si);\n" +
        "    float thetaR = asin(clamp(si / uIor, 0.0, 0.995));\n" +
        "    float bend = uThickness * tan(thetaI - thetaR);\n" +
        "\n" +
        // 3 taps spread along the displacement -- the gradient is steep here and one tap
        // staircases visibly
        "    half4 col = half4(0.0, 0.0, 0.0, 0.0);\n" +
        "    col += content.eval(coord + n * (bend * 0.60));\n" +
        "    col += content.eval(coord + n * (bend * 0.80));\n" +
        "    col += content.eval(coord + n * bend);\n" +
        "    col += content.eval(coord + n * (bend * 1.20));\n" +
        "    col += content.eval(coord + n * (bend * 1.40));\n" +
        "    col = col / 5.0;\n" +
        "\n" +
        "    col.rgb += half3(uTint, uTint, uTint);\n" +
        "\n" +
        // two specular lobes: a lit bevel is bright top-left and again bottom-right
        "    float l1 = clamp(dot(n, normalize(float2(-0.55, -1.0))), 0.0, 1.0);\n" +
        "    float l2 = clamp(dot(n, normalize(float2(0.55, 1.0))), 0.0, 1.0);\n" +
        "    float spec = pow(l1, 5.0) + 0.55 * pow(l2, 7.0);\n" +
        "    spec *= smoothstep(0.15, 0.95, t) * uSpecular;\n" +
        "    col.rgb += half3(half(spec));\n" +
        "\n" +
        // hairline border
        "    float line = 1.0 - smoothstep(0.0, 2.0, abs(d + 1.0));\n" +
        "    col.rgb += half3(half(line * uRim));\n" +
        "\n" +
        // fade into the frosted interior at the inner lip, antialias at the border
        "    float inner = smoothstep(0.0, 0.35, t);\n" +
        "    float outer = 1.0 - smoothstep(-1.0, 0.75, d);\n" +
        "    return col * half(inner * outer);\n" +
        "}\n";

    static RenderEffect rimEffect(float w, float h, float bleed, float corner, float band,
                                  float thickness, float ior,
                                  float specular, float rim, float tint) {
        RuntimeShader s = new RuntimeShader(AGSL);
        s.setFloatUniform("uSize", w, h);
        s.setFloatUniform("uBleed", bleed);
        s.setFloatUniform("uRadius", corner);
        s.setFloatUniform("uBand", band);
        s.setFloatUniform("uThickness", thickness);
        s.setFloatUniform("uIor", ior);
        s.setFloatUniform("uSpecular", specular);
        s.setFloatUniform("uRim", rim);
        s.setFloatUniform("uTint", tint);
        return RenderEffect.createRuntimeShaderEffect(s, "content");
    }
}
