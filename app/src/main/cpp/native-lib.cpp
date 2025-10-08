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

    // Build a view over Y with the actual row stride (no copy).
    // Most devices have yPixelStride == 1; if not, compact first.
    cv::Mat yStrided(height, width, CV_8UC1, y_ptr, static_cast<size_t>(yRowStride));
    cv::Mat y; // compact WxH
    if (yPixelStride == 1) {
        y = yStrided;                    // header only, no copy
    } else {
        // Rare, but handle gracefully: gather every pixelStride byte.
        y.create(height, width, CV_8UC1);
        for (int r = 0; r < height; ++r) {
            const unsigned char* srcRow = y_ptr + r * yRowStride;
            auto* dstRow = y.ptr<unsigned char>(r);
            for (int c = 0; c < width; ++c) dstRow[c] = srcRow[c * yPixelStride];
        }
    }

    // Edge detection -> write directly into outRgba buffer
    cv::Mat edges;
    cv::GaussianBlur(y, edges, cv::Size(3,3), 0);
    cv::Canny(edges, edges, 80, 160);

    cv::Mat rgba(height, width, CV_8UC4, out_ptr);  // wraps output buffer
    cv::cvtColor(edges, rgba, cv::COLOR_GRAY2RGBA); // fills out_ptr in-place
}
