#define STB_IMAGE_IMPLEMENTATION
#define STB_IMAGE_RESIZE_IMPLEMENTATION
#include "stb_image.h"
#include "stb_image_resize2.h"
#include <jni.h>
#include <android/log.h>
#include <vector>
#include <algorithm>

extern "C" JNIEXPORT void JNICALL
Java_cpp_test_ImageLoader_nativeInit(JNIEnv* env, jobject thiz) {
    stbi_set_flip_vertically_on_load(0);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_cpp_test_ImageLoader_nativeDecodeImageFromBytes(
    JNIEnv* env,
    jobject,
    jbyteArray dataArray,
    jint sampleSize,
    jint reqWidth,
    jint reqHeight
) {
    if (dataArray == nullptr) {
        return nullptr;
    }

    jsize len = env->GetArrayLength(dataArray);
    jbyte* dataPtr = env->GetByteArrayElements(dataArray, nullptr);
    if (dataPtr == nullptr) {
        return nullptr;
    }

    int width, height, channels;
    unsigned char* img_raw = stbi_load_from_memory(
        reinterpret_cast<const unsigned char*>(dataPtr),
        len,
        &width, &height, &channels, STBI_rgb_alpha
    );

    env->ReleaseByteArrayElements(dataArray, dataPtr, JNI_ABORT);

    if (!img_raw) {
        return nullptr;
    }

    // Calculate target dimensions maintaining aspect ratio
    float aspectRatio = static_cast<float>(width) / height;
    int targetWidth, targetHeight;
    
    if (reqWidth > 0 && reqHeight > 0) {
        targetWidth = reqWidth;
        targetHeight = reqHeight;
    } else if (sampleSize > 1) {
        targetWidth = width / sampleSize;
        targetHeight = height / sampleSize;
    } else {
        targetWidth = width;
        targetHeight = height;
    }

    // Ensure minimum size of 1
    targetWidth = std::max(1, targetWidth);
    targetHeight = std::max(1, targetHeight);

    // Use stbir for high-quality resizing
    unsigned char* resized_img = nullptr;
    if (targetWidth != width || targetHeight != height) {
        resized_img = static_cast<unsigned char*>(malloc(targetWidth * targetHeight * 4));
        if (resized_img) {
            stbir_resize_uint8(
                img_raw, width, height, 0,
                resized_img, targetWidth, targetHeight, 0,
                4
            );
        }
        stbi_image_free(img_raw);
        img_raw = resized_img;
        if (!img_raw) {
            return nullptr;
        }
        width = targetWidth;
        height = targetHeight;
    }

    // Prepare result buffer (width + height + pixels)
    const size_t pixelBufferSize = width * height * 4;
    const size_t totalSize = 8 + pixelBufferSize;
    jbyteArray result = env->NewByteArray(totalSize);
    if (!result) {
        stbi_image_free(img_raw);
        return nullptr;
    }

    // Copy width and height (little-endian)
    env->SetByteArrayRegion(result, 0, 4, reinterpret_cast<jbyte*>(&width));
    env->SetByteArrayRegion(result, 4, 4, reinterpret_cast<jbyte*>(&height));
    // Copy pixel data
    env->SetByteArrayRegion(result, 8, pixelBufferSize, reinterpret_cast<jbyte*>(img_raw));

    stbi_image_free(img_raw);
    return result;
}
