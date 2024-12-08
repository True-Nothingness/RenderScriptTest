#pragma version(1)
#pragma rs java_package_name(com.light.renderscripttest)

const static float3 gMonoMult = {0.299f, 0.587f, 0.114f};

uchar4 RS_KERNEL root(uchar4 in, uint32_t x, uint32_t y) {
    float4 f4 = rsUnpackColor8888(in);
    float mono = dot(f4.rgb, gMonoMult);
    return rsPackColorTo8888(mono, mono, mono, f4.a);
}
