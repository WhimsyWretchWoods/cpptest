#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"
#include <jni.h>
#include <android/log.h>

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
        reinterpret_cast<unsigned char*>(dataPtr),
        len,
        &width, &height, &channels, 4
    );
    env->ReleaseByteArrayElements(dataArray, dataPtr, 0);

    if (!img) {
        __android_log_print(ANDROID_LOG_ERROR, "ImageLoader", "failed to decode image from memory");
        return nullptr;
    }

    size_t bufferSize = width * height * 4;
    jbyteArray result = env->NewByteArray(bufferSize + 8);
    if (!result) {
        stbi_image_free(img);
        return nullptr;
    }
    env->SetByteArrayRegion(result, 0, 4, reinterpret_cast<jbyte*>(&width));
    env->SetByteArrayRegion(result, 4, 4, reinterpret_cast<jbyte*>(&height));
    env->SetByteArrayRegion(result, 8, bufferSize, reinterpret_cast<jbyte*>(img));
    stbi_image_free(img);
    return result;
}
