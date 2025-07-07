#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"
#include <jni.h>
#include <android/log.h>

extern "C"
JNIEXPORT void JNICALL
Java_cpp_test_ImageLoader_nativeInit(
    JNIEnv *env, jobject thiz
) {
    __android_log_print(ANDROID_LOG_INFO, "ImageLoader", "nativeInit called.");
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_cpp_test_ImageLoader_nativeDecodeImageFromBytes(
        JNIEnv *env, jobject,
        jbyteArray dataArray
) {
    jsize len = env->GetArrayLength(dataArray);
    jbyte* dataPtr = env->GetByteArrayElements(dataArray, nullptr);

    int width, height, channels;
    unsigned char* img = stbi_load_from_memory(
        reinterpret_cast<const unsigned char*>(dataPtr), // Added const for safety
        len,
        &width, &height, &channels, 4
    );
    env->ReleaseByteArrayElements(dataArray, dataPtr, 0);

    if (!img) {
        __android_log_print(ANDROID_LOG_ERROR, "ImageLoader", "failed to decode image from memory: %s", stbi_failure_reason());
        return nullptr;
    }

    size_t bufferSize = width * height * 4;
    jbyteArray result = env->NewByteArray(bufferSize + 8);
    if (!result) {
        stbi_image_free(img);
        __android_log_print(ANDROID_LOG_ERROR, "ImageLoader", "Failed to allocate new byte array for result.");
        return nullptr;
    }
    
    // Ensure data is treated as unsigned bytes before casting to jbyte
    // This assumes your host system is little-endian, consistent with Java ByteBuffer
    jbyte* widthBytes = reinterpret_cast<jbyte*>(&width);
    jbyte* heightBytes = reinterpret_cast<jbyte*>(&height);

    env->SetByteArrayRegion(result, 0, 4, widthBytes);
    env->SetByteArrayRegion(result, 4, 4, heightBytes);
    env->SetByteArrayRegion(result, 8, bufferSize, reinterpret_cast<jbyte*>(img)); // img is already RGBA

    stbi_image_free(img);
    return result;
}
