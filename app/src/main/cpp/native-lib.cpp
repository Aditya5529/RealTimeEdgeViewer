#include <jni.h>
#include <android/log.h>

extern "C"
JNIEXPORT void JNICALL
Java_com_example_myapplication_MainActivity_nativeProcessFrame(
        JNIEnv* env, jobject , jobject buffer, jint width, jint height) {
    (void)env;
    (void)buffer;
    (void)width;
    (void)height;
    __android_log_print(ANDROID_LOG_INFO, "NativeLib", "Frame received: %dx%d", width, height);
}
