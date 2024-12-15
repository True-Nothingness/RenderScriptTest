#version 320 es
precision mediump float;

in vec2 v_TexCoord;       // Interpolated texture coordinates
out vec4 FragColor;

uniform sampler2D uTexture; // Input texture

void main() {
    vec4 color = texture(uTexture, v_TexCoord);
    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114)); // Grayscale weights
    FragColor = vec4(vec3(gray), color.a); // Set RGB to gray, preserve alpha
}
