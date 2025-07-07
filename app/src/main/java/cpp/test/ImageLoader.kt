package cpp.test

object ImageLoader {
    init {
        System.loadLibrary("imageloader")
    }

    @JvmStatic
    external fun nativeInit()

    @JvmStatic
    external fun nativeDecodeImageFromBytes(
        data: ByteArray,
        sampleSize: Int,
        reqWidth: Int,
        reqHeight: Int
    ): ByteArray?
}
