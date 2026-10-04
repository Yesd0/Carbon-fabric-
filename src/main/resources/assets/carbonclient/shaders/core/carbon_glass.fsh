#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 localUv;
in vec4 vertexColor;
flat in float shapeData;

out vec4 fragColor;

const float MODE_STEP = 100000.0;
const int MODE_MAIN = 0;
const int MODE_CARD_OFF = 1;
const int MODE_CARD_ON = 2;
const int MODE_LEGACY_TINT = 3;
const int MODE_SHADOW_MAIN = 4;
const int MODE_SHADOW_CARD = 5;
const int MODE_GLOW_CARD = 6;

float roundedBoxDistance(vec2 point, vec2 halfSize, float radius) {
    vec2 q = abs(point) - (halfSize - vec2(radius));
    return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
}

float carbonNoise(vec2 pixel) {
    uvec2 cell = uvec2(floor(pixel));
    uint value = cell.x * 0x8da6b343u + cell.y * 0xd8163841u;
    value ^= value >> 13u;
    value *= 0x85ebca6bu;
    value ^= value >> 16u;
    return float(value & 0x00ffffffu) / 16777215.0 - 0.5;
}

void main() {
    float mode = floor(shapeData / MODE_STEP);
    float packed = shapeData - mode * MODE_STEP;
    float scaleCode = floor(packed / 100.0);
    float pixelScale = scaleCode / 100.0;
    float radiusDesign = packed - scaleCode * 100.0;

    vec2 uvDerivative = max(fwidth(localUv), vec2(0.00001));
    vec2 pixelSize = 1.0 / uvDerivative;
    vec2 halfSize = pixelSize * 0.5;
    vec2 centeredPoint = (localUv - vec2(0.5)) * pixelSize;
    float radius = max(0.0, radiusDesign * pixelScale);

    if (mode == float(MODE_SHADOW_MAIN)
            || mode == float(MODE_SHADOW_CARD)
            || mode == float(MODE_GLOW_CARD)) {
        float spreadDesign = mode == float(MODE_SHADOW_MAIN) ? 60.0
                : mode == float(MODE_SHADOW_CARD) ? 24.0 : 20.0;
        float spread = spreadDesign * pixelScale;
        vec2 panelHalfSize = max(vec2(1.0), halfSize - vec2(spread));
        float panelDistance = roundedBoxDistance(centeredPoint, panelHalfSize, radius);
        float shadowAlpha = (mode == float(MODE_SHADOW_MAIN) ? 0.45 : 0.30)
                * (1.0 - smoothstep(0.0, max(spread, 1.0), panelDistance));
        vec3 shadowColor = mode == float(MODE_GLOW_CARD)
                ? vec3(0.13, 0.88, 0.48) : vec3(0.0);
        fragColor = vec4(shadowColor * ColorModulator.rgb,
                shadowAlpha * ColorModulator.a);
        return;
    }

    float distanceToEdge = roundedBoxDistance(centeredPoint, halfSize, radius);
    float antialiasWidth = max(fwidth(distanceToEdge), 0.75);
    float coverage = 1.0 - smoothstep(-antialiasWidth, antialiasWidth, distanceToEdge);

    vec3 baseTint;
    float baseOpacity;
    vec3 borderTint = vec3(1.0);
    float borderStrength = 1.0;
    if (mode == float(MODE_MAIN)) {
        baseTint = vec3(8.0, 10.0, 12.0) / 255.0;
        baseOpacity = 0.55;
    } else if (mode == float(MODE_CARD_ON)) {
        float diagonal = clamp((localUv.x + localUv.y) * 0.5, 0.0, 1.0);
        baseTint = mix(vec3(34.0, 224.0, 122.0), vec3(15.0, 168.0, 90.0), diagonal) / 255.0;
        baseOpacity = mix(0.38, 0.24, diagonal);
        borderTint = vec3(77.0, 255.0, 154.0) / 255.0;
        borderStrength = 0.70;
    } else if (mode == float(MODE_LEGACY_TINT)) {
        baseTint = vertexColor.rgb;
        baseOpacity = min(vertexColor.a, 0.55);
    } else {
        // Clear card surfaces matter for hit targets and hierarchy; keep the SDF glass look,
        // but lift the pale off-state opacity so cards remain distinct over the dark main pane.
        baseTint = vec3(1.0);
        baseOpacity = 0.18;
    }

    float grain = carbonNoise(gl_FragCoord.xy) * 0.05;
    vec3 color = clamp(baseTint + vec3(grain), 0.0, 1.0);
    float alpha = baseOpacity;

    float localHeight = pixelSize.y;
    float topDistance = localUv.y * localHeight;
    float highlightAlpha = 0.08 * (1.0 - smoothstep(0.0, 40.0 * pixelScale, topDistance));
    color = mix(color, vec3(1.0), highlightAlpha);
    alpha = highlightAlpha + alpha * (1.0 - highlightAlpha);

    float borderWidth = max(1.0, pixelScale);
    float edgeDistance = abs(distanceToEdge);
    float borderMask = 1.0 - smoothstep(borderWidth - antialiasWidth,
            borderWidth + antialiasWidth, edgeDistance);
    float borderGradient = clamp((localUv.x + localUv.y) * 0.5, 0.0, 1.0);
    float borderAlpha = mix(0.18, 0.06, borderGradient) * borderStrength * borderMask * coverage;
    color = mix(color, borderTint, borderAlpha);
    alpha = borderAlpha + alpha * (1.0 - borderAlpha);

    fragColor = vec4(color * ColorModulator.rgb, alpha * coverage * ColorModulator.a);
}
