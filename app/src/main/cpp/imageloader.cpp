#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"
#include <jni.h>
#include <android/log.h>
#include <vector>
#include <cstring> // For memcpy

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
    if (dataArray == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "ImageLoader", "Input dataArray is null.");
        return nullptr;
    }

    jsize len = env->GetArrayLength(dataArray);
    // Use GetByteArrayElements for raw access, ensure it's released
    jbyte* dataPtr = env->GetByteArrayElements(dataArray, nullptr);
    if (dataPtr == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "ImageLoader", "Failed to get ByteArrayElements.");
        return nullptr;
    }

    int originalWidth, originalHeight, channels;
    // Load image always as RGBA (4 channels)
    unsigned char* img_raw = stbi_load_from_memory(
        reinterpret_cast<const unsigned char*>(dataPtr),
        len,
        &originalWidth, &originalHeight, &channels, 4
    );

    // Release array elements ASAP
    env->ReleaseByteArrayElements(dataArray, dataPtr, 0);

    if (!img_raw) {
        __android_log_print(ANDROID_LOG_ERROR, "ImageLoader", "Failed to decode image from memory: %s", stbi_failure_reason());
        return nullptr;
    }

    int finalWidth = originalWidth;
    int finalHeight = originalHeight;
    unsigned char* pixels_to_return = img_raw; // Points to img_raw or newly allocated downsampled buffer
    size_t finalPixelBufferSize;

    if (sampleSize > 1) {
        finalWidth = originalWidth / sampleSize;
        finalHeight = originalHeight / sampleSize;

        if (finalWidth == 0) finalWidth = 1;
        if (finalHeight == 0) finalHeight = 1;

        finalPixelBufferSize = finalWidth * finalHeight * 4; // 4 bytes per pixel (RGBA)
        pixels_to_return = (unsigned char*)malloc(finalPixelBufferSize);

        if (!pixels_to_return) {
            __android_log_print(ANDROID_LOG_ERROR, "ImageLoader", "Failed to allocate memory for downsampled image.");
            stbi_image_free(img_raw); // Free the original
            return nullptr;
        }

        // Manual nearest-neighbor downsampling
        for (int y = 0; y < finalHeight; ++y) {
            for (int x = 0; x < finalWidth; ++x) {
                int srcX = x * sampleSize;
                int srcY = y * sampleSize;

                // Clamp source coordinates to ensure they are within bounds of the original image
                if (srcX >= originalWidth) srcX = originalWidth - 1;
                if (srcY >= originalHeight) srcY = originalHeight - 1;

                size_t src_offset = (srcY * originalWidth + srcX) * 4; // RGBA
                size_t dest_offset = (y * finalWidth + x) * 4;       // RGBA

                // Copy 4 bytes (RGBA)
                memcpy(&pixels_to_return[dest_offset], &img_raw[src_offset], 4);
            }
        }
        stbi_image_free(img_raw); // Free the original image after downsampling
    } else {
        // No downsampling, use original dimensions and raw pixels
        finalPixelBufferSize = originalWidth * originalHeight * 4;
        // pixels_to_return already points to img_raw
    }

    // Prepare the final result byte array (width + height + pixel data)
    size_t totalResultBufferSize = finalPixelBufferSize + 8; // 4 bytes for width, 4 for height
    jbyteArray result = env->NewByteArray(totalResultBufferSize);
    if (!result) {
        __android_log_print(ANDROID_LOG_ERROR, "ImageLoader", "Failed to allocate result byte array.");
        if (pixels_to_return != img_raw) { // If downsampled, free malloc'd memory
            free(pixels_to_return);
        } else { // If not downsampled, free stbi_image_free'd memory
            stbi_image_free(pixels_to_return); // This handles the img_raw case
        }
        return nullptr;
    }

    // Copy width and height into the first 8 bytes
    env->SetByteArrayRegion(result, 0, 4, reinterpret_cast<jbyte*>(&finalWidth));
    env->SetByteArrayRegion(result, 4, 4, reinterpret_cast<jbyte*>(&finalHeight));
    // Copy pixel data
    env->SetByteArrayRegion(result, 8, finalPixelBufferSize, reinterpret_cast<jbyte*>(pixels_to_return));

    // Clean up memory based on who allocated it
    if (pixels_to_return != img_raw) {
        free(pixels_to_return); // Free memory allocated by malloc
    } else {
        stbi_image_free(pixels_to_return); // Free memory allocated by stbi_load_from_memory
    }

    return result;
}
