#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"
#include <jni.h>
#include <android/log.h>

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_cpp_test_ImageLoader_nativeDecodeImage(JNIEnv *env, jobject, jstring pathJ) {
    const char *path = env->GetStringUTFChars(pathJ, nullptr);
    int width, height, channels;
    unsigned char *data = stbi_load(path, &width, &height, &channels, 4); // force RGBA
    if (!data) {
        __android_log_print(ANDROID_LOG_ERROR, "ImageLoader", "Failed to decode %s", path);
        env->ReleaseStringUTFChars(pathJ, path);
        return nullptr;
    }
    size_t bufferSize = width * height * 4;
    jbyteArray result = env->NewByteArray(bufferSize + 8);
    if (!result) {
        stbi_image_free(data);
        env->ReleaseStringUTFChars(pathJ, path);
        return nullptr;
    }
    // store width/height as first 8 bytes (int32 each)
    env->SetByteArrayRegion(result, 0, 4, reinterpret_cast<jbyte*>(&width));
    env->SetByteArrayRegion(result, 4, 4, reinterpret_cast<jbyte*>(&height));
    env->SetByteArrayRegion(result, 8, bufferSize, reinterpret_cast<jbyte*>(data));
    stbi_image_free(data);
    env->ReleaseStringUTFChars(pathJ, path);
    return result;
}
