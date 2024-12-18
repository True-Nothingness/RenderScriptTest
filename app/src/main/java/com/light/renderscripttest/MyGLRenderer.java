package com.light.renderscripttest;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES32;
import android.opengl.GLSurfaceView;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLUtils;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class MyGLRenderer implements GLSurfaceView.Renderer {
    private Context context;
    private int program, mode;
    private float buffer, aspectRatio;
    private int textureId;
    private Bitmap bitmap;  // The input image
    private FloatBuffer vertexBuffer, texCoordBuffer;

    private final float[] vertexData = {
            -1.0f, -1.0f, 0.0f,  // Bottom-left
            1.0f, -1.0f, 0.0f,  // Bottom-right
            -1.0f,  1.0f, 0.0f,  // Top-left
            1.0f,  1.0f, 0.0f   // Top-right
    };

    private final float[] texCoordData = {
            0.0f, 1.0f,  // Bottom-left
            1.0f, 1.0f,  // Bottom-right
            0.0f, 0.0f,  // Top-left
            1.0f, 0.0f   // Top-right
    };

    public MyGLRenderer(Context context, Bitmap bitmap, int mode, float aspectRatio) {
        this.context = context;
        this.bitmap = bitmap;
        this.mode = mode;
        this.aspectRatio = aspectRatio;
    }
    public MyGLRenderer(Context context, Bitmap bitmap, int mode, float buffer, float aspectRatio) {
        this.context = context;
        this.bitmap = bitmap;
        this.mode = mode;
        this.buffer = buffer;
        this.aspectRatio = aspectRatio;
    }

    private void setupBuffers() {
        ByteBuffer vb = ByteBuffer.allocateDirect(vertexData.length * 4);
        vb.order(ByteOrder.nativeOrder());
        vertexBuffer = vb.asFloatBuffer();
        vertexBuffer.put(vertexData);
        vertexBuffer.position(0);

        ByteBuffer tb = ByteBuffer.allocateDirect(texCoordData.length * 4);
        tb.order(ByteOrder.nativeOrder());
        texCoordBuffer = tb.asFloatBuffer();
        texCoordBuffer.put(texCoordData);
        texCoordBuffer.position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        setupBuffers();
        String vertexShaderCode = ShaderUtils.loadShaderFromResource(context, R.raw.vertex_shader);
        String fragmentShaderCode;
        switch (mode) {
            case 0:
                fragmentShaderCode = ShaderUtils.loadShaderFromResource(context, R.raw.grayscale); // Grayscale shader
                break;
            case 1:
                fragmentShaderCode = ShaderUtils.loadShaderFromResource(context, R.raw.blur); // Blur shader
                break;
            case 2:
                fragmentShaderCode = ShaderUtils.loadShaderFromResource(context, R.raw.sobel); // Edge shader
                break;
            default:
                fragmentShaderCode = ShaderUtils.loadShaderFromResource(context, R.raw.grayscale); // Default to grayscale
                break;
        }
        int vertexShader = compileShader(GLES32.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = compileShader(GLES32.GL_FRAGMENT_SHADER, fragmentShaderCode);

        program = GLES32.glCreateProgram();
        GLES32.glAttachShader(program, vertexShader);
        GLES32.glAttachShader(program, fragmentShader);
        GLES32.glLinkProgram(program);

        GLES32.glUseProgram(program);

        // Load the bitmap as a texture
        textureId = loadTexture(bitmap);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT | GLES32.GL_DEPTH_BUFFER_BIT);

        GLES32.glUseProgram(program);

        // Bind the texture
        GLES32.glActiveTexture(GLES32.GL_TEXTURE0);
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, textureId);

        // Bind the uniform sampler to texture unit 0
        int textureLocation = GLES32.glGetUniformLocation(program, "uTexture");
        GLES32.glUniform1i(textureLocation, 0);
        if (mode==1) {
            int blurSizeLocation = GLES32.glGetUniformLocation(program, "u_BlurSize");
            GLES32.glUniform1f(blurSizeLocation, buffer);
        }
        // Draw your geometry here (e.g., a quad displaying the texture)
        GLES32.glEnableVertexAttribArray(0);
        GLES32.glVertexAttribPointer(0, 3, GLES32.GL_FLOAT, false, 0, vertexBuffer);

        GLES32.glEnableVertexAttribArray(1);
        GLES32.glVertexAttribPointer(1, 2, GLES32.GL_FLOAT, false, 0, texCoordBuffer);

        GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, 4);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES32.glViewport(0, 0, width, height);  // Set the OpenGL viewport

        // Calculate aspect ratio
        float ratio = (float) width / height;

        // Adjust projection matrix to maintain the aspect ratio
        float[] projectionMatrix = new float[16];
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1.0f, 1.0f, 3.0f, 7.0f);

        // Set the projection matrix to the shader
        int projectionMatrixHandle = GLES32.glGetUniformLocation(program, "uProjectionMatrix");
        GLES32.glUniformMatrix4fv(projectionMatrixHandle, 1, false, projectionMatrix, 0);
    }


    private int compileShader(int type, String shaderCode) {
        int shader = GLES32.glCreateShader(type);
        GLES32.glShaderSource(shader, shaderCode);
        GLES32.glCompileShader(shader);

        int[] compileStatus = new int[1];
        GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, compileStatus, 0);

        if (compileStatus[0] == 0) {
            String log = GLES32.glGetShaderInfoLog(shader);
            GLES32.glDeleteShader(shader);
            throw new RuntimeException("Shader compilation failed: " + log);
        }
        return shader;
    }

    private int loadTexture(Bitmap bitmap) {
        int[] textureHandle = new int[1];
        GLES32.glGenTextures(1, textureHandle, 0);

        if (textureHandle[0] != 0) {
            GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, textureHandle[0]);

            // Set texture parameters
            GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR);
            GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR);
            GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_S, GLES32.GL_CLAMP_TO_EDGE);
            GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_T, GLES32.GL_CLAMP_TO_EDGE);

            // Upload the bitmap data to the texture
            GLUtils.texImage2D(GLES32.GL_TEXTURE_2D, 0, bitmap, 0);

            // Unbind the texture
            GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, 0);
        } else {
            throw new RuntimeException("Error loading texture.");
        }

        return textureHandle[0];
    }

}

