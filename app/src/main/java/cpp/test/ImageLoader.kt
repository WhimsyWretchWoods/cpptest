package cpp.test

object ImageLoader {
    init {
        System.loadLibrary("imageloader")
    }

    @JvmStatic
    external fun nativeInit()
}
