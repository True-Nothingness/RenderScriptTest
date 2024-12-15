package com.light.renderscripttest;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
        mBitmapOutRS = Bitmap.createBitmap(w, h, mBitmapIn.getConfig());
        mBitmapOutJava = Bitmap.createBitmap(w, h, mBitmapIn.getConfig());
        TextView timeViewJava = findViewById(R.id.timeJava);
        TextView timeViewRS = findViewById(R.id.timeRS);
        glSurfaceView = findViewById(R.id.outputGL);
        long startTime = System.nanoTime();
        glSurfaceView.setRenderer(new MyGLRenderer(this, mBitmapIn, 1, buffer));
        long endTime = System.nanoTime();
        timeGL = (endTime - startTime)/1000;
        TextView timeViewGL = findViewById(R.id.timeGL);
        timeViewGL.setText("Time GL: " + timeGL + " μs");
        ImageView in = findViewById(R.id.inputImage);
        in.setImageBitmap(mBitmapIn);
        ImageView render = findViewById(R.id.outputRender);
        render.setImageBitmap(mBitmapIn);
        applyGaussianBlurEffect(render, 5, 5);
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                // Apply Java grayscale
                applyGaussianBlurJava();

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
                applyGaussianBlurRS();

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
    private void applyGaussianBlurJava() {
        long startTime = System.nanoTime();
        mBitmapOutJava = GaussianBlur.applyGaussianBlur(mBitmapIn, javaRadius, javaSigma);
        long endTime = System.nanoTime();
        timeJava = (endTime - startTime)/1000;
    }

    private void applyGaussianBlurEffect(ImageView imageView, float blurRadiusX, float blurRadiusY) {
        long startTime = System.nanoTime();
        RenderEffect blurEffect = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurEffect = RenderEffect.createBlurEffect(blurRadiusX, blurRadiusY, Shader.TileMode.CLAMP);
            imageView.setRenderEffect(blurEffect);
        }
        long endTime = System.nanoTime();
        long duration = (endTime - startTime)/1000;
        TextView timeRender = findViewById(R.id.timeRender);
        timeRender.setText("Time Render: " + duration + " μs");
    }
    private void applyGaussianBlurRS() {
        long startTime = System.nanoTime();
        // Initialize RenderScript
        RenderScript mRS = RenderScript.create(this);

        // Create input and output allocations
        Allocation inAllocation = Allocation.createFromBitmap(mRS, mBitmapIn, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        Allocation outAllocation = Allocation.createTyped(mRS, inAllocation.getType());

        // Create an intrinsic blur script
        ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(mRS, Element.U8_4(mRS));
        blurScript.setRadius(rsRadius); // Set desired blur radius
        blurScript.setInput(inAllocation);
        blurScript.forEach(outAllocation);

        // Copy the result to the output Bitmap
        outAllocation.copyTo(mBitmapOutRS);

        long endTime = System.nanoTime();
        timeRS = (endTime - startTime) / 1000;

        // Destroy the RenderScript context to release resources
        mRS.destroy();
    }

}