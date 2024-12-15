#version 320 es
precision mediump float;

in vec2 v_TexCoord;
out vec4 FragColor;

uniform sampler2D uTexture;

void main() {
    // Offsets for the convolution
    float offsets[3] = float[](-1.0, 0.0, 1.0);

    // Sobel kernels for X and Y gradient
    float kernelX[3][3] = float[][](
        float[](-1.0, 0.0, 1.0),
        float[](-2.0, 0.0, 2.0),
        float[](-1.0, 0.0, 1.0)
    );
    float kernelY[3][3] = float[][](
        float[](-1.0, -2.0, -1.0),
        float[](0.0, 0.0, 0.0),
        float[](1.0, 2.0, 1.0)
    );

    // Initialize gradients in the X and Y directions
    vec3 gradientX = vec3(0.0);
    vec3 gradientY = vec3(0.0);

    // Calculate texel size (pixel size) based on the texture dimensions
    vec2 texelSize = 1.0 / vec2(textureSize(uTexture, 0));

    // Loop through the 3x3 kernel to sample the surrounding pixels
    for (int i = 0; i < 3; i++) {
        for (int j = 0; j < 3; j++) {
            // Calculate the offset for each pixel
            vec2 offset = vec2(offsets[i] * texelSize.x, offsets[j] * texelSize.y);

            // Sample the texture at the current offset
            vec4 sampledTexel = texture(uTexture, v_TexCoord + offset);

            // Rename 'sample' to 'texelSample' to avoid conflicts
            vec3 texelSample = sampledTexel.rgb;  // Access RGB components of the texel

            // Apply the kernel to the sampled pixel
            gradientX += texelSample * kernelX[i][j];
            gradientY += texelSample * kernelY[i][j];
        }
    }

    // Calculate the magnitude of the gradient (edge strength)
    float gradient = length(gradientX + gradientY);

    // Output the result (edge detection effect)
    FragColor = vec4(vec3(gradient), 1.0);
}
