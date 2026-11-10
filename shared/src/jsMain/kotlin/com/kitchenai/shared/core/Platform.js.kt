package com.kitchenai.shared.core

private class BrowserPlatform : Platform {
    override val name: String = "Browser"
}

actual fun currentPlatform(): Platform = BrowserPlatform()
