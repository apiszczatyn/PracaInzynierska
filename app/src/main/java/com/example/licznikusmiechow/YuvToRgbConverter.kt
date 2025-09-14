@file:Suppress("DEPRECATION")
package com.example.licznikusmiechow

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.*
import androidx.camera.core.ImageProxy

class YuvToRgbConverter(context: Context) {
    private val rs = RenderScript.create(context)
    private val script = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))

    private var yuvType: Type? = null
    private var rgbaType: Type? = null
    private var inputAlloc: Allocation? = null
    private var outputAlloc: Allocation? = null

    fun yuvToRgb(image: ImageProxy, output: Bitmap) {
        // 1) Zbuduj POPRAWNE NV21
        val nv21 = YuvUtils.yuv420ToNv21(image)

        // 2) Input allocation (U8 o rozmiarze bufora)
        if (yuvType == null || yuvType?.count != nv21.size) {
            yuvType = Type.Builder(rs, Element.U8(rs)).setX(nv21.size).create()
            inputAlloc = Allocation.createTyped(rs, yuvType, Allocation.USAGE_SCRIPT)
        }

        // 3) Output allocation (RGBA_8888 wielkości bitmapy)
        if (rgbaType == null || rgbaType?.x != output.width || rgbaType?.y != output.height) {
            rgbaType = Type.Builder(rs, Element.RGBA_8888(rs))
                .setX(output.width).setY(output.height).create()
            outputAlloc = Allocation.createTyped(rs, rgbaType, Allocation.USAGE_SCRIPT)
        }

        // 4) Konwersja
        inputAlloc!!.copyFrom(nv21)
        script.setInput(inputAlloc)
        script.forEach(outputAlloc)
        outputAlloc!!.copyTo(output)
    }
}
