#pragma version(1)
#pragma rs java_package_name(com.light.renderscripttest)

rs_allocation gIn;  // Input allocation
rs_allocation gOut; // Output allocation

// Sobel kernel for X and Y directions (flattened)
static const float SobelX[9] = {
    -1, 0, 1,
    -2, 0, 2,
    -1, 0, 1
};

static const float SobelY[9] = {
    -1, -2, -1,
     0,  0,  0,
     1,  2,  1
};

uchar4 RS_KERNEL root(uint32_t x, uint32_t y) {
    int w = rsAllocationGetDimX(gIn);
    int h = rsAllocationGetDimY(gIn);

    float gx = 0.0f;
    float gy = 0.0f;

    // Iterate through the 3x3 kernel
    for (int ky = -1; ky <= 1; ky++) {
        for (int kx = -1; kx <= 1; kx++) {
            int px = clamp((int)x + kx, 0, w - 1);
            int py = clamp((int)y + ky, 0, h - 1);

            uchar4 pixel = rsGetElementAt_uchar4(gIn, px, py);
            float gray = 0.299f * pixel.r + 0.587f * pixel.g + 0.114f * pixel.b;

            int kernelIndex = (ky + 1) * 3 + (kx + 1);
            gx += SobelX[kernelIndex] * gray;
            gy += SobelY[kernelIndex] * gray;
        }
    }

    // Calculate the gradient magnitude
    float magnitude = sqrt(gx * gx + gy * gy);

    // Clamp to the range [0, 255] and convert to uchar
    uchar edge = (uchar)clamp(magnitude, 0.0f, 255.0f);

    float4 edgeColor = (float4){edge / 255.0f, edge / 255.0f, edge / 255.0f, 1.0f};
    return rsPackColorTo8888(edgeColor);
}
