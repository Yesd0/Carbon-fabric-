#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 localUv;
in vec4 vertexColor;
flat in float cornerRadius;

out vec4 fragColor;

float roundedBoxDistance(vec2 point, vec2 halfSize, float radius) {
    vec2 q = abs(point) - (halfSize - vec2(radius));
    return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
}

float monochromeNoise(vec2 pixel) {
    vec2 p = floor(pixel);
    float hashValue = dot(p, vec2(12.9898, 78.233));
    return fract(sin(hashValue) * 43758.5453) - 0.5;
}

void main() {
    // UV derivatives recover framebuffer-pixel dimensions after design/UI scale transforms.
    vec2 pixelSize = 1.0 / max(fwidth(localUv), vec2(0.00001));
    vec2 halfSize = pixelSize * 0.5;
    vec2 point = (localUv - vec2(0.5)) * pixelSize;
    float radius = clamp(abs(cornerRadius), 0.0, min(halfSize.x, halfSize.y));
    float distanceToEdge = roundedBoxDistance(point, halfSize, radius);
    float edgeWidth = max(fwidth(distanceToEdge), 0.75);
    float coverage = 1.0 - smoothstep(-0.5 * edgeWidth, 0.5 * edgeWidth, distanceToEdge);

    vec4 color = vertexColor * ColorModulator;
    if (cornerRadius < 0.0) {
        float radialDistance = length((localUv - vec2(0.5)) * 2.0);
        color.a *= smoothstep(0.18, 1.0, radialDistance);
    }

    // A subtle two-percent monochrome dither keeps large matte gradients from banding.
    float grain = monochromeNoise(gl_FragCoord.xy) * 0.02;
    color.rgb = clamp(color.rgb + vec3(grain), 0.0, 1.0);
    fragColor = vec4(color.rgb, color.a * coverage);
}
