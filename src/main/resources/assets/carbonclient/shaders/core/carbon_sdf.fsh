#version 330

#moj_import <carbonclient:carbon_blur.glsl>

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
    vec2 uvDerivative = max(fwidth(localUv), vec2(0.00001));
    vec2 pixelSize = 1.0 / uvDerivative;
    vec2 halfSize = pixelSize * 0.5;
    vec2 centeredPoint = (localUv - vec2(0.5)) * pixelSize;
    float radius = clamp(cornerRadius, 0.0, min(halfSize.x, halfSize.y));
    float distanceToEdge = roundedBoxDistance(centeredPoint, halfSize, radius);
    float antialiasWidth = max(fwidth(distanceToEdge), 0.75);
    float coverage = 1.0 - smoothstep(-antialiasWidth, antialiasWidth, distanceToEdge);

    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * coverage);
}
