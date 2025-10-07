#include <jni.h>
#include <android/log.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "NativeLib", __VA_ARGS__)

extern "C"
JNIEXPORT void JNICALL
Java_com_example_myapplication_MainActivity_nativeProcessFrame(
        JNIEnv* env, jobject /*thiz*/, jobject buffer, jint width, jint height) {

    // Plane-0 from YUV_420_888 = full-res luma; treat as grayscale
    auto* y_ptr = static_cast<unsigned char*>(env->GetDirectBufferAddress(buffer));
    if (!y_ptr) { LOGI("Null Y plane"); return; }

    cv::Mat y(height, width, CV_8UC1, y_ptr);        // grayscale source
    cv::Mat edges;
    // Optionally blur to reduce noise
    cv::GaussianBlur(y, edges, cv::Size(3,3), 0);
    cv::Canny(edges, edges, 80, 160);

    // (Demo) count non-zero edge pixels
    int edgeCount = cv::countNonZero(edges);
    LOGI("Processed frame %dx%d, edges=%d", width, height, edgeCount);

    // If you later need RGBA for GL upload:
    // cv::Mat rgba; cv::cvtColor(y, rgba, cv::COLOR_GRAY2RGBA);
    // ... copy to a direct buffer to render on the Java side.
}
