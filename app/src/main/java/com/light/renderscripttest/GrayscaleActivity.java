package com.light.renderscripttest;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RenderEffect;
import android.os.Build;
import android.os.Bundle;
import android.view.Choreographer;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.renderscript.Allocation;
import androidx.renderscript.RenderScript;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GrayscaleActivity extends AppCompatActivity {
    private Bitmap mBitmapIn, mBitmapOutRS, mBitmapOutJava;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private MyGLSurfaceView glSurfaceView;
    public long timeJava, timeRS, timeGL;
    private RenderScript rs;
    private Allocation inAllocation, outAllocation;
    private ScriptC_grayscale script;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grayscale);

        Intent intent = getIntent();
        int size = intent.getIntExtra("size", 256);
        switch (size) {
            case 256:
                mBitmapIn = loadBitmap(R.drawable.i256x256);
                break;
            case 640:
                mBitmapIn = loadBitmap(R.drawable.i640x480);
                break;
            case 1024:
                mBitmapIn = loadBitmap(R.drawable.i1024x1024);
                break;
            case 1920:
                mBitmapIn = loadBitmap(R.drawable.i1920x1080);
                break;
        }

        int w = mBitmapIn.getWidth();
        int h = mBitmapIn.getHeight();
        // Display input image
        ImageView in = findViewById(R.id.inputImage);
        in.setImageBitmap(mBitmapIn);

        mBitmapOutRS = Bitmap.createBitmap(w, h, mBitmapIn.getConfig());
        mBitmapOutJava = Bitmap.createBitmap(w, h, mBitmapIn.getConfig());

        TextView timeViewJava = findViewById(R.id.timeJava);
        TextView timeViewRS = findViewById(R.id.timeRS);
        glSurfaceView = findViewById(R.id.outputGL);

        rs = RenderScript.create(this);
        inAllocation = Allocation.createFromBitmap(rs, mBitmapIn, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        outAllocation = Allocation.createTyped(rs, inAllocation.getType());
        script = new ScriptC_grayscale(rs);


        // Benchmark GLSurfaceView rendering
        benchmarkGLRendering();

        // Apply RenderEffect
        ImageView render = findViewById(R.id.outputRender);
        render.setImageBitmap(mBitmapIn);
        applyGrayscaleEffect(render);

        // Benchmark Java grayscale
        executorService.execute(() -> {
            applyGrayscaleJava();
            runOnUiThread(() -> {
                ImageView outputJava = findViewById(R.id.outputJava);
                outputJava.setImageBitmap(mBitmapOutJava);
                timeViewJava.setText("Time Java: " + timeJava + " μs");
            });
        });

        // Benchmark RenderScript grayscale
        executorService.execute(() -> {
            applyGrayscaleRS();
            runOnUiThread(() -> {
                ImageView outputRS = findViewById(R.id.outputRS);
                outputRS.setImageBitmap(mBitmapOutRS);
                timeViewRS.setText("Time RS: " + timeRS + " μs");
            });
        });
    }

    private void warmUp() {
        applyGrayscaleJava();
        applyGrayscaleRS();
    }

    private Bitmap loadBitmap(int resource) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeResource(getResources(), resource, options);
    }

    private void applyGrayscaleJava() {
        int width = mBitmapIn.getWidth();
        int height = mBitmapIn.getHeight();
        int[] pixels = new int[width * height];
        long startTime = System.nanoTime();
        mBitmapIn.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            pixels[i] = (0xFF << 24) | (gray << 16) | (gray << 8) | gray;
        }
        mBitmapOutJava.setPixels(pixels, 0, width, 0, 0, width, height);

        long endTime = System.nanoTime();
        timeJava = (endTime - startTime) / 1000;
    }

    private void applyGrayscaleEffect(ImageView imageView) {
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RenderEffect grayscaleEffect = RenderEffect.createColorFilterEffect(colorFilter);
            imageView.setRenderEffect(grayscaleEffect);

            // Measure time using Choreographer
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


    private void applyGrayscaleRS() {
        long startTime = System.nanoTime();
        script.forEach_root(inAllocation, outAllocation);
        outAllocation.copyTo(mBitmapOutRS);
        long endTime = System.nanoTime();

        timeRS = (endTime - startTime) / 1000;
        rs.destroy();
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
        glSurfaceView.setRenderer(new MyGLRenderer(this, mBitmapIn, 0, aspectRatio));
        glSurfaceView.requestRender();
    }

}
