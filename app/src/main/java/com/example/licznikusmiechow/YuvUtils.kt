package com.example.licznikusmiechow

import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

object YuvUtils {

    // Konwersja YUV_420_888 (CameraX) -> NV21 (VU interleaved)
    fun yuv420ToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2

        val out = ByteArray(ySize + uvSize)

        // --- Y ---
        val yPlane = image.planes[0]
        copyPlane(
            yPlane.buffer, yPlane.rowStride, yPlane.pixelStride,
            width, height, out, 0
        )

        // --- UV (VU dla NV21) ---
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val chromaHeight = height / 2
        val chromaWidth = width / 2

        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()

        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        var pos = ySize
        for (row in 0 until chromaHeight) {
            var uRowPos = row * uRowStride
            var vRowPos = row * vRowStride
            for (col in 0 until chromaWidth) {
                // NV21 = V potem U
                out[pos++] = vBuffer.get(vRowPos)
                out[pos++] = uBuffer.get(uRowPos)
                uRowPos += uPixelStride
                vRowPos += vPixelStride
            }
        }

        return out
    }

    private fun copyPlane(
        buffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        out: ByteArray,
        offset: Int
    ) {
        val src = buffer.duplicate()
        var outPos = offset
        if (pixelStride == 1 && rowStride == width) {
            // szybka ścieżka: cały blok na raz
            src.get(out, outPos, width * height)
            return
        }
        // ogólna ścieżka: linia po linii
        for (row in 0 until height) {
            var srcPos = row * rowStride
            if (pixelStride == 1) {
                src.position(srcPos)
                src.get(out, outPos, width)
                outPos += width
            } else {
                for (col in 0 until width) {
                    out[outPos++] = src.get(srcPos)
                    srcPos += pixelStride
                }
            }
        }
    }
}
