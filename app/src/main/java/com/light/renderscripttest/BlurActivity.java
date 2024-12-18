package com.light.renderscripttest;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.Choreographer;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.renderscript.Allocation;
import androidx.renderscript.Element;
import androidx.renderscript.RenderScript;
import androidx.renderscript.ScriptIntrinsicBlur;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlurActivity extends AppCompatActivity {
    private Bitmap mBitmapIn, mBitmapOutRS, mBitmapOutJava;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private MyGLSurfaceView glSurfaceView;
    public long timeJava, timeRS, timeGL;
    public int javaRadius, rsRadius;
    public float javaSigma;
    public float buffer;
    private RenderScript mRS;
    private Allocation inAllocation, outAllocation;
    private ScriptIntrinsicBlur blurScript;
    private GaussianBlur gaussianBlur;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blur);
        Intent intent = getIntent();
        int size = intent.getIntExtra("size", 256);
        switch (size) {
            case 256:
                mBitmapIn = loadBitmap(R.drawable.i256x256);
                javaSigma = 3.3f;
                javaRadius = 10;
                rsRadius = 10;
                buffer = 1.5f;
                break;
            case 640:
                mBitmapIn = loadBitmap(R.drawable.i640x480);
                javaSigma = 5.3f;
                javaRadius = 15;
                rsRadius = 15;
                buffer = 2.0f;
                break;
            case 1024:
                mBitmapIn = loadBitmap(R.drawable.i1024x1024);
                javaSigma = 9.3f;
                javaRadius = 22;
                rsRadius = 22;
                buffer = 3.0f;
                break;
            case 1920:
                mBitmapIn = loadBitmap(R.drawable.i1920x1080);
                javaSigma = 16.3f;
                javaRadius = 25;
                rsRadius = 25;
                buffer = 4.0f;
                break;
        }
        int w = mBitmapIn.getWidth();
        int h = mBitmapIn.getHeight();
        gaussianBlur = new GaussianBlur(javaRadius, javaSigma);
        ImageView in = findViewById(R.id.inputImage);
        in.setImageBitmap(mBitmapIn);
        mBitmapOutRS = Bitmap.createBitmap(w, h, mBitmapIn.getConfig());
        mBitmapOutJava = Bitmap.createBitmap(w, h, mBitmapIn.getConfig());

        TextView timeViewJava = findViewById(R.id.timeJava);
        TextView timeViewRS = findViewById(R.id.timeRS);
        glSurfaceView = findViewById(R.id.outputGL);

        // Initialize RenderScript
        mRS = RenderScript.create(this);

        // Create input and output allocations
        inAllocation = Allocation.createFromBitmap(mRS, mBitmapIn, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        outAllocation = Allocation.createTyped(mRS, inAllocation.getType());

        // Create an intrinsic blur script
        blurScript = ScriptIntrinsicBlur.create(mRS, Element.U8_4(mRS));
        blurScript.setRadius(rsRadius); // Set desired blur radius

        // Benchmark GLSurfaceView rendering
        benchmarkGLRendering();

        // Apply RenderEffect
        ImageView render = findViewById(R.id.outputRender);
        render.setImageBitmap(mBitmapIn);
        applyGaussianBlurEffect(render, 5, 5);

        // Benchmark Java blur
        executorService.execute(() -> {
            applyGaussianBlurJava();
            runOnUiThread(() -> {
                ImageView outputJava = findViewById(R.id.outputJava);
                outputJava.setImageBitmap(mBitmapOutJava);
                timeViewJava.setText("Time Java: " + timeJava + " μs");
            });
        });

        // Benchmark RenderScript blur
        executorService.execute(() -> {
            applyGaussianBlurRS();
            runOnUiThread(() -> {
                ImageView outputRS = findViewById(R.id.outputRS);
                outputRS.setImageBitmap(mBitmapOutRS);
                timeViewRS.setText("Time RS: " + timeRS + " μs");
            });
        });
    }
    private void warmUp() {
        applyGaussianBlurJava();
        applyGaussianBlurRS();
    }
    private Bitmap loadBitmap(int resource) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeResource(getResources(), resource, options);
    }
    private void applyGaussianBlurJava() {
        long startTime = System.nanoTime();
        mBitmapOutJava = gaussianBlur.applyGaussianBlur(mBitmapIn);
        long endTime = System.nanoTime();
        timeJava = (endTime - startTime)/1000;
    }

    private void applyGaussianBlurEffect(ImageView imageView, float blurRadiusX, float blurRadiusY) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Create and apply the blur effect
            RenderEffect blurEffect = RenderEffect.createBlurEffect(blurRadiusX, blurRadiusY, Shader.TileMode.CLAMP);
            imageView.setRenderEffect(blurEffect);

            // Measure rendering time using Choreographer
            Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
                private long startTime = System.nanoTime();

                @Override
                public void doFrame(long frameTimeNanos) {
                    long endTime = System.nanoTime();
                    long duration = (endTime - startTime) / 1000; // Convert to microseconds
                    TextView timeRender = findViewById(R.id.timeRender);
                    timeRender.setText("Time Render: " + duration + " μs");
                }
            });
        }
    }

    private void applyGaussianBlurRS() {
        blurScript.setInput(inAllocation);
        long startTime = System.nanoTime();
        blurScript.forEach(outAllocation);

        // Copy the result to the output Bitmap
        outAllocation.copyTo(mBitmapOutRS);

        long endTime = System.nanoTime();
        timeRS = (endTime - startTime) / 1000;

        // Destroy the RenderScript context to release resources
        mRS.destroy();
    }
    private void benchmarkGLRendering() {
        // Get the aspect ratio of the bitmap
        float aspectRatio = (float) mBitmapIn.getWidth() / mBitmapIn.getHeight();

        // Post the frame callback to measure the rendering time
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            private long startTime = System.nanoTime();

            @Override
            public void doFrame(long frameTimeNanos) {
                // This method is called when the frame starts being drawn
                long endTime = System.nanoTime(); // Get the time when the frame starts rendering
                long duration = (endTime - startTime) / 1000; // Convert to microseconds

                // Update the UI with the rendering time
                TextView timeViewGL = findViewById(R.id.timeGL);
                timeViewGL.setText("Time GL: " + duration + " μs");
            }
        });

        // Set the renderer and trigger the render request
        glSurfaceView.setRenderer(new MyGLRenderer(this, mBitmapIn, 1, buffer, aspectRatio));
        glSurfaceView.requestRender();
    }
}