package com.spiritbox.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

/** Medidor semicircular animado para o módulo EMF. Valor normalizado 0..1. */
class EmfGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var value = 0f
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x2A, 0x2A, 0x30)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x55, 0x55, 0x5E)
        strokeWidth = 2f
    }
    private val arc = RectF()

    fun setValue(v: Float) {
        value = v.coerceIn(0f, 1f)
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, hw: Int) {
        super.onSizeChanged(w, h, ow, hw)
        val stroke = h * 0.09f
        bgPaint.strokeWidth = stroke
        fgPaint.strokeWidth = stroke
        val inset = stroke
        arc.set(inset * 1.6f, stroke, w - inset * 1.6f, h * 1.75f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawArc(arc, 180f, 180f, false, bgPaint)
        for (i in 0..10) {
            val ang = 180 + (180f * i / 10f)
            val rad = Math.toRadians(ang.toDouble())
            val cx = arc.centerX()
            val cy = arc.centerY()
            val outR = arc.width() / 2f
            val r0 = outR - 3f
            val r1 = outR + 3f
            canvas.drawLine(
                (cx + r0 * kotlin.math.cos(rad)).toFloat(),
                (cy + r0 * kotlin.math.sin(rad)).toFloat(),
                (cx + r1 * kotlin.math.cos(rad)).toFloat(),
                (cy + r1 * kotlin.math.sin(rad)).toFloat(),
                tickPaint
            )
        }
        if (value > 0.01f) {
            val r = (0x30 + 0xCF * value.coerceIn(0f, 1f)).roundToInt()
            val g = (0xA0 * (1f - value)).roundToInt().coerceIn(0, 255)
            fgPaint.color = Color.rgb(r.coerceAtMost(255), g, 0x18)
            canvas.drawArc(arc, 180f, 180f * value.coerceIn(0f, 1f), false, fgPaint)
        }
    }
}