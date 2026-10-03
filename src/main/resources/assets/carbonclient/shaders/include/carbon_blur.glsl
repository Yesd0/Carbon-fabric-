vec4 carbonGaussianBlur(sampler2D sourceTexture, vec2 uv, vec2 texelSize, vec2 axis, float radius) {
    float sigma = max(radius * 0.5, 0.5);
    vec4 accumulated = vec4(0.0);
    float totalWeight = 0.0;

    for (int tap = -8; tap <= 8; ++tap) {
        float offset = float(tap);
        float weight = exp(-(offset * offset) / (2.0 * sigma * sigma));
        accumulated += texture(sourceTexture, uv + axis * texelSize * offset) * weight;
        totalWeight += weight;
    }

    return accumulated / max(totalWeight, 0.0001);
}
