#version 320 es
precision mediump float;

in vec4 a_Position;  // Vertex position
in vec2 a_TexCoord;  // Texture coordinates (this should be passed from Java)

out vec2 v_TexCoord;  // Output to fragment shader

void main() {
    gl_Position = a_Position;  // Set the vertex position
    v_TexCoord = a_TexCoord;   // Pass texture coordinates to the fragment shader
}