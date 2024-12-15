package com.light.renderscripttest;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RenderEffect;
import android.opengl.GLES32;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
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
        mBitmapOutRS = Bitmap.createBitmap(w, h, mBitmapIn.getConfig());
        mBitmapOutJava = Bitmap.createBitmap(w, h, mBitmapIn.getConfig());
        TextView timeViewJava = findViewById(R.id.timeJava);
        TextView timeViewRS = findViewById(R.id.timeRS);
        glSurfaceView = findViewById(R.id.outputGL);
        long startTime = System.nanoTime();
        glSurfaceView.setRenderer(new MyGLRenderer(this, mBitmapIn, 0));
        long endTime = System.nanoTime();
        timeGL = (endTime - startTime)/1000;
        TextView timeViewGL = findViewById(R.id.timeGL);
        timeViewGL.setText("Time GL: " + timeGL + " μs");
        ImageView in = findViewById(R.id.inputImage);
        in.setImageBitmap(mBitmapIn);
        ImageView render = findViewById(R.id.outputRender);
        render.setImageBitmap(mBitmapIn);
        applyGrayscaleEffect(render);
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                // Apply Java grayscale
                applyGrayscaleJava();

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
                applyGrayscaleRS();

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
    private void applyGrayscaleJava() {
        long startTime = System.nanoTime();

        int width = mBitmapIn.getWidth();
        int height = mBitmapIn.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = mBitmapIn.getPixel(x, y);

                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                int grayPixel = (0xFF << 24) | (gray << 16) | (gray << 8) | gray;

                mBitmapOutJava.setPixel(x, y, grayPixel);
            }
        }
        long endTime = System.nanoTime();
        timeJava = (endTime - startTime)/1000;
    }

    private void applyGrayscaleEffect(ImageView imageView) {
        long startTime = System.nanoTime();

        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0); // Set saturation to 0 for grayscale
        ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);

        RenderEffect grayscaleEffect = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            grayscaleEffect = RenderEffect.createColorFilterEffect(colorFilter);
            imageView.setRenderEffect(grayscaleEffect);
        }

        long endTime = System.nanoTime();
        long duration = (endTime - startTime)/1000;
        TextView timeRender = findViewById(R.id.timeRender);
        timeRender.setText("Time Render: " + duration + " μs");
    }

    private void applyGrayscaleRS() {
        // Initialize RenderScript
        long startTime = System.nanoTime();

        RenderScript rs = RenderScript.create(this);

        // Create input and output allocations
        Allocation inAllocation = Allocation.createFromBitmap(rs, mBitmapIn, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        Allocation outAllocation = Allocation.createTyped(rs, inAllocation.getType());

        // Load the grayscale RenderScript
        ScriptC_grayscale script = new ScriptC_grayscale(rs);

        // Set input allocation in the script
        script.forEach_root(inAllocation, outAllocation);

        // Copy the result to the output Bitmap
        outAllocation.copyTo(mBitmapOutRS);

        long endTime = System.nanoTime();
        timeRS = (endTime - startTime)/1000;

        // Destroy the RenderScript context to release resources
        rs.destroy();
    }
}