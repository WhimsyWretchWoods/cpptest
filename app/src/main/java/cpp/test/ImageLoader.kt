package cpp.test

object ImageLoader {
    init {
        System.loadLibrary("imageloader")
    }

    @JvmStatic
    external fun nativeInit()

    @JvmStatic
    external fun nativeDecodeImage(path: String): ByteArray?
}
