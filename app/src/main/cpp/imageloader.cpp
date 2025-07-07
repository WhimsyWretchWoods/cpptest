#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"
#include <jni.h>
#include <android/log.h>
#include <vector>

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
        jbyteArray dataArray,
        jint sampleSize
) {
    jsize len = env->GetArrayLength(dataArray);
    jbyte* dataPtr = env->GetByteArrayElements(dataArray, nullptr);

    int width, height, channels;
    unsigned char* img_raw = stbi_load_from_memory(
        reinterpret_cast<const unsigned char*>(dataPtr),
        len,
        &width, &height, &channels, 4
    );
    env->ReleaseByteArrayElements(dataArray, dataPtr, 0);

    if (!img_raw) {
        __android_log_print(ANDROID_LOG_ERROR, "ImageLoader", "failed to decode image from memory: %s", stbi_failure_reason());
        return nullptr;
    }

    int outputWidth = width;
    int outputHeight = height;
    unsigned char* img_final = img_raw;

    if (sampleSize > 1) {
        outputWidth = width / sampleSize;
        outputHeight = height / sampleSize;

        if (outputWidth == 0) outputWidth = 1;
        if (outputHeight == 0) outputHeight = 1;

        size_t finalBufferSize = outputWidth * outputHeight * 4;
        std::vector<unsigned char> temp_buffer(finalBufferSize);

        for (int y = 0; y < outputHeight; ++y) {
            for (int x = 0; x < outputWidth; ++x) {
                int srcX = x * sampleSize;
                int srcY = y * sampleSize;

                if (srcX >= width) srcX = width - 1;
                if (srcY >= height) srcY = height - 1;

                size_t src_offset = (srcY * width + srcX) * 4;
                size_t dest_offset = (y * outputWidth + x) * 4;

                if (src_offset + 3 < (size_t)width * height * 4 && dest_offset + 3 < finalBufferSize) {
                    temp_buffer[dest_offset + 0] = img_raw[src_offset + 0];
                    temp_buffer[dest_offset + 1] = img_raw[src_offset + 1];
                    temp_buffer[dest_offset + 2] = img_raw[src_offset + 2];
                    temp_buffer[dest_offset + 3] = img_raw[src_offset + 3];
                }
            }
        }
        stbi_image_free(img_raw);
        
        unsigned char* heap_buffer = (unsigned char*)malloc(finalBufferSize);
        if (!heap_buffer) {
             __android_log_print(ANDROID_LOG_ERROR, "ImageLoader", "Failed to allocate heap_buffer for downsampled image.");
             return nullptr;
        }
        memcpy(heap_buffer, temp_buffer.data(), finalBufferSize);
        img_final = heap_buffer;
        finalBufferSize = finalBufferSize;
    } else {
        width = outputWidth;
        height = outputHeight;
    }

    size_t bufferSize = width * height * 4;
    jbyteArray result = env->NewByteArray(bufferSize + 8);
    if (!result) {
        if (img_raw != img_final) {
            free(img_final);
        } else {
            stbi_image_free(img_final);
        }
        __android_log_print(ANDROID_LOG_ERROR, "ImageLoader", "Failed to allocate new byte array for result (OOM).");
        return nullptr;
    }
    
    jbyte* widthBytes = reinterpret_cast<jbyte*>(&width);
    jbyte* heightBytes = reinterpret_cast<jbyte*>(&height);

    env->SetByteArrayRegion(result, 0, 4, widthBytes);
    env->SetByteArrayRegion(result, 4, 4, heightBytes);
    env->SetByteArrayRegion(result, 8, bufferSize, reinterpret_cast<jbyte*>(img_final));

    if (img_raw != img_final) {
        free(img_final);
    } else {
        stbi_image_free(img_final);
    }
    
    return result;
}
