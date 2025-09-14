package com.example.licznikusmiechow

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class SmileInterpreter(context: Context) {
    private val interpreter: Interpreter

    companion object {
        private const val INPUT_SIZE = 28
        private const val CHANNELS = 1
        private const val BYTES_PER_CHANNEL = 4 // float32
        private const val MODEL_FILE = "model.tflite" // ← podmieniasz plik w assets
    }

    init {
        interpreter = Interpreter(loadModelFile(context, MODEL_FILE))
    }

    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        val fd = context.assets.openFd(filename)
        FileInputStream(fd.fileDescriptor).use { fis ->
            val ch = fis.channel
            return ch.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

    /** Zwraca (pNot, pYes) */
    fun predictSmile(grayCrop: Bitmap): Pair<Float, Float> {
        val resized = Bitmap.createScaledBitmap(grayCrop, INPUT_SIZE, INPUT_SIZE, true)
        val input = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * CHANNELS * BYTES_PER_CHANNEL)
            .order(ByteOrder.nativeOrder())

        val px = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(px, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (v in px) {
            val r = (v shr 16 and 0xFF).toFloat()
            val g = (v shr 8 and 0xFF).toFloat()
            val b = (v and 0xFF).toFloat()
            val y = 0.299f * r + 0.587f * g + 0.114f * b
            input.putFloat(y / 255f)
        }

        val out = Array(1) { FloatArray(2) }
        interpreter.run(input, out)
        return out[0][0] to out[0][1]
    }
}