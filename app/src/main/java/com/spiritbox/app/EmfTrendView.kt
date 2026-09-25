package com.spiritbox.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/** Mini gráfico de linha com a tendência do campo (mG) dos últimos ~60 s. */
class EmfTrendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val capacity = 240
    private val samples = FloatArray(capacity)
    private var count = 0
    private var head = 0

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x33, 0x33, 0x3C)
        strokeWidth = 1f
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x14, 0x14, 0x18) }
    private val segPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    fun push(v: Float) {
        samples[head] = v.coerceIn(0f, 99.9f)
        head = (head + 1) % capacity
        if (count < capacity) count++
        postInvalidateOnAnimation()
    }

    fun clear() {
        count = 0
        head = 0
        postInvalidateOnAnimation()
    }

    private fun colorFor(v: Float): Int = when {
        v >= 70f -> Color.rgb(0xE0, 0x2C, 0x20)
        v >= 40f -> Color.rgb(0xF0, 0xA0, 0x00)
        else -> Color.rgb(0x37, 0xB0, 0x50)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        canvas.drawLine(0f, h * 0.25f, w, h * 0.25f, gridPaint)
        canvas.drawLine(0f, h * 0.5f, w, h * 0.5f, gridPaint)
        canvas.drawLine(0f, h * 0.75f, w, h * 0.75f, gridPaint)
        if (count < 2) return

        var maxV = 1f
        for (i in 0 until count) {
            maxV = max(maxV, samples[(head - count + i + capacity) % capacity])
        }
        maxV = maxV.coerceAtLeast(1f)

        val n = count
        for (i in 1 until n) {
            val i0 = (head - n + i - 1 + capacity) % capacity
            val i1 = (head - n + i + capacity) % capacity
            val x0 = w * (i - 1) / (n - 1)
            val x1 = w * i / (n - 1)
            val y0 = h - (h * samples[i0] / maxV)
            val y1 = h - (h * samples[i1] / maxV)
            segPaint.color = colorFor(samples[i1])
            canvas.drawLine(x0, y0, x1, y1, segPaint)
        }
    }
}