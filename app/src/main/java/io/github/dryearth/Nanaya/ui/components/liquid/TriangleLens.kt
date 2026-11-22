// Triangle variant of the liquid-glass lens effect.
// The miuix `lens` effect only understands rounded rectangles (CornerBasedShape);
// this mirror keeps the same refraction model but uses a triangle SDF so the
// back-button glass can be a rounded triangle while still refracting.

package io.github.dryearth.Nanaya.ui.components.liquid

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect

fun BackdropEffectScope.triangleLens(
    refractionHeight: Float,
    refractionAmount: Float,
    cornerRadius: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Float = 0f,
) {
    if (!isRuntimeShaderSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    if (padding < refractionAmount) {
        padding = refractionAmount
    }

    val sf = downscaleFactor.coerceAtLeast(1).toFloat()
    runtimeShaderEffect(
        key = "LiquidGlassTriangleLens",
        shaderString = TRIANGLE_REFRACTION_SHADER,
        uniformShaderName = "content",
    ) {
        setFloatUniform("size", size.width / sf, size.height / sf)
        setFloatUniform("offset", -padding / sf, -padding / sf)
        setFloatUniform("refractionHeight", refractionHeight / sf)
        setFloatUniform("refractionAmount", -refractionAmount / sf)
        setFloatUniform("cornerRadius", cornerRadius / sf)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        setFloatUniform("chromaticAberration", chromaticAberration)
    }
}

private const val TRIANGLE_REFRACTION_SHADER = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float cornerRadius;
uniform float depthEffect;
uniform float chromaticAberration;

float sdTriangle(float2 p, float2 p0, float2 p1, float2 p2) {
    float2 e0 = p1 - p0;
    float2 e1 = p2 - p1;
    float2 e2 = p0 - p2;
    float2 v0 = p - p0;
    float2 v1 = p - p1;
    float2 v2 = p - p2;
    float2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
    float2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
    float2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
    float s = sign(e0.x * e2.y - e0.y * e2.x);
    float2 d = min(
        min(
            float2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)),
            float2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))
        ),
        float2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x))
    );
    return -sqrt(d.x) * sign(d.y);
}

float sdRoundedTriangle(float2 p, float2 p0, float2 p1, float2 p2, float r) {
    return sdTriangle(p, p0, p1, p2) - r;
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;

    float2 p0 = float2(-0.36 * size.x, 0.0);
    float2 p1 = float2(0.34 * size.x, -0.34 * size.y);
    float2 p2 = float2(0.34 * size.x, 0.34 * size.y);

    float sd = sdRoundedTriangle(centeredCoord, p0, p1, p2, cornerRadius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float2 eps = float2(0.5, 0.0);
    float gx = sdRoundedTriangle(centeredCoord + eps.xy, p0, p1, p2, cornerRadius)
             - sdRoundedTriangle(centeredCoord - eps.xy, p0, p1, p2, cornerRadius);
    float gy = sdRoundedTriangle(centeredCoord + eps.yx, p0, p1, p2, cornerRadius)
             - sdRoundedTriangle(centeredCoord - eps.yx, p0, p1, p2, cornerRadius);
    float2 edgeGrad = normalize(float2(gx, gy) + float2(0.000001, 0.000001));
    float2 radial = normalize(centeredCoord + float2(0.000001, 0.000001));
    float2 grad = normalize(edgeGrad + depthEffect * radial);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float2 refractedCoord = coord + d * grad;

    float dispersionIntensity = chromaticAberration
        * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}
"""
