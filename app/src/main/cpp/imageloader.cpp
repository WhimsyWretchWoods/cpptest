#include <jni.h>
#include <android/log.h>

extern "C"
JNIEXPORT void JNICALL
Java_cpp_test_ImageLoader_nativeInit(JNIEnv* env, jobject thiz) {
    __android_log_print(ANDROID_LOG_INFO, "ImageLoader", "Native image loader initialized");
}
