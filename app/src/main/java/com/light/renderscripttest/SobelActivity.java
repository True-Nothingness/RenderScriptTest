package com.light.renderscripttest;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Choreographer;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.renderscript.Allocation;
import androidx.renderscript.RenderScript;
import androidx.renderscript.Script;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SobelActivity extends AppCompatActivity {
    private Bitmap mBitmapIn, mBitmapOutRS, mBitmapOutJava;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private MyGLSurfaceView glSurfaceView;
    public long timeJava, timeRS, timeGL;
    private RenderScript rs;
    private Allocation inAllocation, outAllocation;
    private ScriptC_sobel script;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sobel);
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
        ImageView in = findViewById(R.id.inputImage);
        in.setImageBitmap(mBitmapIn);
        mBitmapOutRS = Bitmap.createBitmap(w, h, mBitmapIn.getConfig());
        mBitmapOutJava = Bitmap.createBitmap(w, h, mBitmapIn.getConfig());

        TextView timeViewJava = findViewById(R.id.timeJava);
        TextView timeViewRS = findViewById(R.id.timeRS);
        glSurfaceView = findViewById(R.id.outputGL);
        // Initialize RenderScript
        rs = RenderScript.create(this);

        // Create input and output allocations
        inAllocation = Allocation.createFromBitmap(rs, mBitmapIn, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        outAllocation = Allocation.createTyped(rs, inAllocation.getType());

        // Load the Sobel RenderScript
        script = new ScriptC_sobel(rs);


        // Benchmark GLSurfaceView rendering
        benchmarkGLRendering();

        // Benchmark Java sobel
        executorService.execute(() -> {
            applySobelJava();
            runOnUiThread(() -> {
                ImageView outputJava = findViewById(R.id.outputJava);
                outputJava.setImageBitmap(mBitmapOutJava);
                timeViewJava.setText("Time Java: " + timeJava + " μs");
            });
        });

        // Benchmark RenderScript sobel
        executorService.execute(() -> {
            applySobelRS();
            runOnUiThread(() -> {
                ImageView outputRS = findViewById(R.id.outputRS);
                outputRS.setImageBitmap(mBitmapOutRS);
                timeViewRS.setText("Time RS: " + timeRS + " μs");
            });
        });
    }
    private Bitmap loadBitmap(int resource) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeResource(getResources(), resource, options);
    }
    private void warmUp() {
        applySobelJava();
        applySobelRS();
    }
    private void applySobelJava() {
        int width = mBitmapIn.getWidth();
        int height = mBitmapIn.getHeight();

        // Sobel Kernels for X and Y
        int[] sobelX = {
                -1, 0, 1,
                -2, 0, 2,
                -1, 0, 1
        };

        int[] sobelY = {
                -1, -2, -1,
                0, 0, 0,
                1, 2, 1
        };

        // Create a pixel array to hold the input and output pixels
        int[] pixelsIn = new int[width * height];
        int[] pixelsOut = new int[width * height];

        // Get all the input pixels at once
        mBitmapIn.getPixels(pixelsIn, 0, width, 0, 0, width, height);

        long startTime = System.nanoTime();

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int gx = 0, gy = 0;

                // Apply Sobel X and Sobel Y
                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        // Get the pixel color (grayscale) from the input array
                        int pixel = pixelsIn[(y + ky) * width + (x + kx)];
                        int gray = (int) (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel));

                        gx += sobelX[(ky + 1) * 3 + (kx + 1)] * gray;
                        gy += sobelY[(ky + 1) * 3 + (kx + 1)] * gray;
                    }
                }

                // Calculate the gradient magnitude
                int magnitude = (int) Math.min(255, Math.sqrt(gx * gx + gy * gy));

                // Set the edge pixel color (grayscale) in the output array
                pixelsOut[y * width + x] = Color.rgb(magnitude, magnitude, magnitude);
            }
        }

        // Set the output pixels all at once
        mBitmapOutJava.setPixels(pixelsOut, 0, width, 0, 0, width, height);

        long endTime = System.nanoTime();
        timeJava = (endTime - startTime) / 1000;
    }


    private void applySobelRS() {
        // Bind the input allocation to the script
        script.set_gIn(inAllocation);
        script.set_gOut(outAllocation);

        // Set launch options (no special parameters needed here)
        Script.LaunchOptions launchOptions = new Script.LaunchOptions();
        launchOptions.setX(0, inAllocation.getType().getX() - 1);
        launchOptions.setY(0, inAllocation.getType().getY() - 1);

        long startTime = System.nanoTime();

        // Execute the Sobel operation
        script.forEach_root(outAllocation, launchOptions);

        // Copy the result to the output Bitmap
        outAllocation.copyTo(mBitmapOutRS);

        long endTime = System.nanoTime();
        timeRS = (endTime - startTime)/1000;


        // Destroy the RenderScript context to release resources
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
        glSurfaceView.setRenderer(new MyGLRenderer(this, mBitmapIn, 2, aspectRatio));
        glSurfaceView.requestRender();
    }
}