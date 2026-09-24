package com.spiritbox.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

/** Mini mapa de movimento: colunas de calor varrendo a esquerda conforme o tempo. */
class MiniMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val samples = IntArray(MAX)
    private var cursor = 0
    private var count = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun push(movement: Int) {
        samples[cursor] = movement.coerceIn(0, 100)
        cursor = (cursor + 1) % MAX
        if (count < MAX) count++
        postInvalidateOnAnimation()
    }

    fun reset() {
        count = 0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (count == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val colW = w / MAX
        val drawn = count.coerceAtMost(MAX)
        val start = if (count < MAX) 0 else cursor
        for (i in 0 until drawn) {
            val v = samples[(start + i) % MAX]
            paint.color = color(v)
            canvas.drawRect(i * colW, 0f, (i + 1) * colW, h, paint)
        }
    }

    private fun color(v: Int): Int {
        val t = v / 100f
        val a = (0x30 + (0xE0 - 0x30) * t).roundToInt()
        val r = (0x60 + (0xFF - 0x60) * t).roundToInt()
        val g = (0x08 + (0x80 - 0x08) * t).toInt()
        val b = (0x06 + (0x10 - 0x06) * t).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    companion object {
        private const val MAX = 96
    }
}