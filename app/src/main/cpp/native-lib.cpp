#include <jni.h>
#include <android/log.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "NativeLib", __VA_ARGS__)

extern "C"
JNIEXPORT void JNICALL
Java_com_example_myapplication_MainActivity_nativeProcessFrame(
        JNIEnv* env, jobject /*this*/,
        jobject yPlane, jint yRowStride, jint yPixelStride,
        jobject outRgba, jint width, jint height) {

    auto* y_ptr   = static_cast<unsigned char*>(env->GetDirectBufferAddress(yPlane));
    auto* out_ptr = static_cast<unsigned char*>(env->GetDirectBufferAddress(outRgba));
    if (!y_ptr || !out_ptr) { LOGI("null buffer(s)"); return; }

    // ✅ Cache Mats and dimensions to prevent reallocation every frame
    static int cachedW = 0, cachedH = 0;
    static cv::Mat yCompact, yGather, edges, rgba;

    if (cachedW != width || cachedH != height) {
        cachedW = width;
        cachedH = height;
        yCompact.release();
        yGather.release();
        edges.release();
        rgba.release();
        LOGI("Reallocated Mats for %dx%d", width, height);
    }

    // View over Y plane (stride-aware)
    cv::Mat yStrided(height, width, CV_8UC1, y_ptr, static_cast<size_t>(yRowStride));

    // Compact into contiguous WxH if pixelStride != 1
    if (yPixelStride == 1) {
        yCompact = yStrided; // shallow copy, no data duplication
    } else {
        if (yGather.empty()) yGather.create(height, width, CV_8UC1);
        for (int r = 0; r < height; ++r) {
            const unsigned char* srcRow = y_ptr + r * yRowStride;
            auto* dstRow = yGather.ptr<unsigned char>(r);
            for (int c = 0; c < width; ++c)
                dstRow[c] = srcRow[c * yPixelStride];
        }
        yCompact = yGather;
    }

    // Allocate reusable Mats
    if (edges.empty()) edges.create(height, width, CV_8UC1);
    if (rgba.empty())  rgba = cv::Mat(height, width, CV_8UC4, out_ptr);  // wrap output buffer

    // ✅ Processing (lightweight pipeline)
    cv::GaussianBlur(yCompact, edges, cv::Size(3,3), 0, 0, cv::BORDER_REPLICATE);
    cv::Canny(edges, edges, 80, 160);
    cv::cvtColor(edges, rgba, cv::COLOR_GRAY2RGBA); // fills out_ptr directly
}
