package com.example.licznikusmiechow

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Box(val rect: RectF, val label: String, val smiling: Boolean)

    private val boxes = mutableListOf<Box>()
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 42f
    }
    private val textBg = Paint().apply { color = 0x80000000.toInt() }

    fun setBoxes(list: List<Box>) {
        synchronized(boxes) { boxes.clear(); boxes.addAll(list) }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        synchronized(boxes) {
            for (b in boxes) {
                boxPaint.color = if (b.smiling) Color.GREEN else Color.RED
                canvas.drawRect(b.rect, boxPaint)

                val pad = 8f
                val tw = textPaint.measureText(b.label)
                val th = textPaint.fontMetrics.run { bottom - top }
                val left = b.rect.left
                val top = (b.rect.top - th - 12f).coerceAtLeast(0f)

                canvas.drawRect(left - pad, top - pad, left + tw + pad, top + th + pad, textBg)
                canvas.drawText(b.label, left, top + th * 0.8f, textPaint)
            }
        }
    }
}
