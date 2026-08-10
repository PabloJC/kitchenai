package com.kitchenai.shared.core

private class AndroidPlatform : Platform {
    override val name: String = "Android ${android.os.Build.VERSION.SDK_INT}"
}

actual fun currentPlatform(): Platform = AndroidPlatform()
