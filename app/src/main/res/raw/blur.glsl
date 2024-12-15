#version 320 es
precision mediump float;

uniform sampler2D u_Texture;
uniform float u_BlurSize;

in vec2 v_TexCoord;
out vec4 fragColor;

float gaussian(float x, float sigma) {
    return exp(-(x * x) / (2.0 * sigma * sigma)) / (sqrt(2.0 * 3.14159) * sigma);
}

void main() {
    vec2 texelSize = 1.0 / vec2(textureSize(u_Texture, 0)); // Corrected texel size calculation
    
    vec4 color = vec4(0.0);
    float weightSum = 0.0;

    // Horizontal blur loop
    for (int i = -7; i <= 7; i++) { // Expanded range for a larger kernel
        float weight = gaussian(float(i), 10.0);  // Test with a smaller sigma
        vec2 offset = vec2(texelSize.x * float(i) * u_BlurSize, 0.0);
        color += texture(u_Texture, v_TexCoord + offset) * weight;
        weightSum += weight;
    }

    // Vertical blur loop
    for (int i = -7; i <= 7; i++) { // Expanded range for a larger kernel
        float weight = gaussian(float(i), 10.0);  // Test with a smaller sigma
        vec2 offset = vec2(0.0, texelSize.y * float(i) * u_BlurSize);  // Vertical offset
        color += texture(u_Texture, v_TexCoord + offset) * weight;
        weightSum += weight;
    }

    fragColor = color / weightSum;
}
