package com.light.renderscripttest;

import android.graphics.Bitmap;
import android.graphics.Color;



public class GaussianBlur {

    private float[] kernel;  // Store the kernel for reuse
    private int radius;
    private float sigma;

    // Constructor to create the kernel
    public GaussianBlur(int radius, float sigma) {
        this.radius = radius;
        this.sigma = sigma;
        this.kernel = createGaussianKernel(radius, sigma);  // Precompute the kernel
    }

    // Apply Gaussian Blur with precomputed kernel
    public Bitmap applyGaussianBlur(Bitmap sentBitmap) {
        int width = sentBitmap.getWidth();
        int height = sentBitmap.getHeight();
        Bitmap blurredBitmap = Bitmap.createBitmap(width, height, sentBitmap.getConfig());

        // Get all pixels at once
        int[] pixels = new int[width * height];
        sentBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int[] horizontalBlurred = new int[width * height];

        // First pass: Horizontal blur
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float red = 0.0f;
                float green = 0.0f;
                float blue = 0.0f;

                // Apply Gaussian kernel in the horizontal direction
                for (int i = -radius; i <= radius; i++) {
                    int neighborX = x + i;
                    if (neighborX >= 0 && neighborX < width) {
                        int pixel = pixels[y * width + neighborX];
                        red += Color.red(pixel) * kernel[i + radius];
                        green += Color.green(pixel) * kernel[i + radius];
                        blue += Color.blue(pixel) * kernel[i + radius];
                    }
                }

                red = Math.max(0, Math.min(255, red));
                green = Math.max(0, Math.min(255, green));
                blue = Math.max(0, Math.min(255, blue));

                horizontalBlurred[y * width + x] = Color.argb(255, (int) red, (int) green, (int) blue);
            }
        }

        // Second pass: Vertical blur
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                float red = 0.0f;
                float green = 0.0f;
                float blue = 0.0f;

                // Apply Gaussian kernel in the vertical direction
                for (int j = -radius; j <= radius; j++) {
                    int neighborY = y + j;
                    if (neighborY >= 0 && neighborY < height) {
                        int pixel = horizontalBlurred[neighborY * width + x];
                        red += Color.red(pixel) * kernel[j + radius];
                        green += Color.green(pixel) * kernel[j + radius];
                        blue += Color.blue(pixel) * kernel[j + radius];
                    }
                }

                red = Math.max(0, Math.min(255, red));
                green = Math.max(0, Math.min(255, green));
                blue = Math.max(0, Math.min(255, blue));

                blurredBitmap.setPixel(x, y, Color.argb(255, (int) red, (int) green, (int) blue));
            }
        }

        return blurredBitmap;
    }

    // Create a Gaussian kernel for a given radius and sigma
    private float[] createGaussianKernel(int radius, float sigma) {
        int kernelSize = radius * 2 + 1;
        float[] kernel = new float[kernelSize];
        float sum = 0.0f;

        for (int i = -radius; i <= radius; i++) {
            kernel[i + radius] = (float) Math.exp(-(i * i) / (2 * sigma * sigma));
            sum += kernel[i + radius];
        }

        // Normalize kernel
        for (int i = 0; i < kernelSize; i++) {
            kernel[i] /= sum;
        }

        return kernel;
    }
}
