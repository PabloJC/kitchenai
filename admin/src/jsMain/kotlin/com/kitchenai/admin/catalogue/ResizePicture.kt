package com.kitchenai.admin.catalogue

import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.Image
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val MAX_SIDE = 1024
private const val WEBP_QUALITY = 0.85

/** The picked file, decoded by the browser and re-encoded as a WebP no larger than [MAX_SIDE]. */
suspend fun resizeToWebp(file: Blob): Blob {
    val url = URL.createObjectURL(file)
    try {
        val image = loadImage(url)
        val (width, height) = scaledToFit(image.naturalWidth, image.naturalHeight, MAX_SIDE)
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = width
        canvas.height = height
        (
            canvas.getContext(
                "2d",
            ) as CanvasRenderingContext2D
        ).drawImage(image, 0.0, 0.0, width.toDouble(), height.toDouble())
        return canvasToWebp(canvas)
    } finally {
        URL.revokeObjectURL(url)
    }
}

private suspend fun loadImage(url: String): Image =
    suspendCancellableCoroutine { continuation ->
        val image = Image()
        image.onload = { continuation.resume(image) }
        image.onerror = {
                _,
                _,
                _,
                _,
                _,
            ->
            continuation.resumeWithException(IllegalArgumentException("That file is not a picture"))
        }
        image.src = url
    }

private suspend fun canvasToWebp(canvas: HTMLCanvasElement): Blob =
    suspendCancellableCoroutine { continuation ->
        canvas.toBlob({ blob ->
            if (blob == null) {
                continuation.resumeWithException(IllegalStateException("The browser could not encode the picture"))
            } else {
                continuation.resume(blob)
            }
        }, "image/webp", WEBP_QUALITY)
    }
