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

void main() {
    // Recover framebuffer-pixel dimensions from the quad's interpolated UV gradient so the
    // physical corner radius stays round at every Minecraft GUI scale and window size.
    vec2 pixelSize = 1.0 / max(fwidth(localUv), vec2(0.00001));
    vec2 halfSize = pixelSize * 0.5;
    vec2 point = (localUv - vec2(0.5)) * pixelSize;
    float radius = clamp(cornerRadius, 0.0, min(halfSize.x, halfSize.y));
    float distanceToEdge = roundedBoxDistance(point, halfSize, radius);
    float edgeWidth = max(fwidth(distanceToEdge), 0.75);
    float coverage = 1.0 - smoothstep(-0.5 * edgeWidth, 0.5 * edgeWidth, distanceToEdge);

    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * coverage);
}
