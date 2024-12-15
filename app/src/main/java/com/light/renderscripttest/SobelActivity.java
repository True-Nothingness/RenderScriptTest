package com.light.renderscripttest;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
        mBitmapOutRS = Bitmap.createBitmap(w, h, mBitmapIn.getConfig());
        mBitmapOutJava = Bitmap.createBitmap(w, h, mBitmapIn.getConfig());
        TextView timeViewJava = findViewById(R.id.timeJava);
        TextView timeViewRS = findViewById(R.id.timeRS);
        glSurfaceView = findViewById(R.id.outputGL);
        long startTime = System.nanoTime();
        glSurfaceView.setRenderer(new MyGLRenderer(this, mBitmapIn, 2));
        long endTime = System.nanoTime();
        timeGL = (endTime - startTime)/1000;
        TextView timeViewGL = findViewById(R.id.timeGL);
        timeViewGL.setText("Time GL: " + timeGL + " μs");
        ImageView in = findViewById(R.id.inputImage);
        in.setImageBitmap(mBitmapIn);
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                // Apply Java grayscale
                applySobelJava();

                // Once done, call the UI update method to set the result
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ImageView outputJava = findViewById(R.id.outputJava);
                        outputJava.setImageBitmap(mBitmapOutJava);
                        timeViewJava.setText("Time Java: " + timeJava + " μs");
                    }
                });
            }
        });
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                // Apply RenderScript grayscale
                applySobelRS();

                // Once done, call the UI update method to set the result
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ImageView outputRS = findViewById(R.id.outputRS);
                        outputRS.setImageBitmap(mBitmapOutRS);
                        timeViewRS.setText("Time RS: " + timeRS + " μs");
                    }
                });
            }
        });
    }
    private Bitmap loadBitmap(int resource) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeResource(getResources(), resource, options);
    }
    private void applySobelJava() {
        long startTime = System.nanoTime();
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

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int gx = 0, gy = 0;

                // Apply Sobel X and Sobel Y
                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int pixel = mBitmapIn.getPixel(x + kx, y + ky);
                        int gray = (int) (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel));

                        gx += sobelX[(ky + 1) * 3 + (kx + 1)] * gray;
                        gy += sobelY[(ky + 1) * 3 + (kx + 1)] * gray;
                    }
                }

                // Calculate the gradient magnitude
                int magnitude = (int) Math.min(255, Math.sqrt(gx * gx + gy * gy));

                // Set the edge pixel color (grayscale)
                mBitmapOutJava.setPixel(x, y, Color.rgb(magnitude, magnitude, magnitude));
            }
        }
        long endTime = System.nanoTime();
        timeJava = (endTime - startTime)/1000;
    }

    private void applySobelRS() {
        long startTime = System.nanoTime();
        // Initialize RenderScript
        RenderScript rs = RenderScript.create(this);

        // Create input and output allocations
        Allocation inAllocation = Allocation.createFromBitmap(rs, mBitmapIn, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        Allocation outAllocation = Allocation.createTyped(rs, inAllocation.getType());

        // Load the Sobel RenderScript
        ScriptC_sobel script = new ScriptC_sobel(rs);

        // Bind the input allocation to the script
        script.set_gIn(inAllocation);
        script.set_gOut(outAllocation);

        // Set launch options (no special parameters needed here)
        Script.LaunchOptions launchOptions = new Script.LaunchOptions();
        launchOptions.setX(0, inAllocation.getType().getX() - 1);
        launchOptions.setY(0, inAllocation.getType().getY() - 1);

        // Execute the Sobel operation
        script.forEach_root(outAllocation, launchOptions);

        // Copy the result to the output Bitmap
        outAllocation.copyTo(mBitmapOutRS);

        long endTime = System.nanoTime();
        timeRS = (endTime - startTime)/1000;


        // Destroy the RenderScript context to release resources
        rs.destroy();
    }
}