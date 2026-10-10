package com.spiritbox.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/** Timeline da janela mais recente: linha do campo (mG), traços das capturas de
 * rádio e destaque dos eventos combinados (pico do magnetômetro coincidente com uma
 * captura). Desenhada a partir de instantâneos, sem depender da thread do sensor. */
class EmfCorrelationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val windowMs = CorrelationTimeline.WINDOW_MS

    private var samples: List<CorrelationTimeline.Sample> = emptyList()
    private var radio: List<CorrelationTimeline.RadioEvent> = emptyList()
    private var peaks: List<CorrelationTimeline.Peak> = emptyList()
    private var combined: List<CorrelationTimeline.Combined> = emptyList()
    private var hasSensor = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x14, 0x14, 0x18) }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x33, 0x33, 0x3C)
        strokeWidth = 1f
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x37, 0xB0, 0x50)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val radioPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x42, 0x9A, 0xF0)
        strokeWidth = 2f
    }
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xE0, 0x2C, 0x20)
    }
    private val combinedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0x66, 0xF0, 0xA0, 0x00)
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x88, 0x88, 0x90)
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }

    fun show(
        samples: List<CorrelationTimeline.Sample>,
        radio: List<CorrelationTimeline.RadioEvent>,
        peaks: List<CorrelationTimeline.Peak>,
        combined: List<CorrelationTimeline.Combined>,
        hasSensor: Boolean
    ) {
        this.samples = samples
        this.radio = radio
        this.peaks = peaks
        this.combined = combined
        this.hasSensor = hasSensor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        canvas.drawLine(0f, h * 0.5f, w, h * 0.5f, gridPaint)

        if (!hasSensor) {
            canvas.drawText(
                context.getString(R.string.emf_corr_no_sensor),
                w / 2f,
                h / 2f + textPaint.textSize / 3f,
                textPaint
            )
            return
        }

        var now = 0L
        for (s in samples) if (s.t > now) now = s.t
        for (r in radio) if (r.t > now) now = r.t
        if (now == 0L) return
        val start = now - windowMs

        fun xFor(t: Long): Float = ((t - start).toFloat() / windowMs) * w

        // Destaque dos eventos combinados: banda entre a captura e o pico.
        for (c in combined) {
            val x0 = xFor(c.peak.t)
            val x1 = xFor(c.radio.t)
            canvas.drawRect(
                minOf(x0, x1) - 2f, 0f,
                maxOf(x0, x1) + 2f, h,
                combinedPaint
            )
        }

        // Linha do campo.
        var maxMg = 1f
        for (s in samples) if (s.t >= start) maxMg = max(maxMg, s.mg)
        var prevX = Float.NaN
        var prevY = Float.NaN
        for (s in samples) {
            if (s.t < start) continue
            val x = xFor(s.t)
            val y = h - (h * (s.mg.coerceAtLeast(0f) / maxMg))
            if (!prevX.isNaN()) canvas.drawLine(prevX, prevY, x, y, linePaint)
            prevX = x
            prevY = y
        }

        // Picos do campo.
        for (p in peaks) {
            if (p.t < start) continue
            val x = xFor(p.t)
            val y = h - (h * (p.mg.coerceAtLeast(0f) / maxMg))
            canvas.drawCircle(x, y, 4f, peakPaint)
        }

        // Capturas de rádio.
        for (r in radio) {
            if (r.t < start) continue
            val x = xFor(r.t)
            canvas.drawLine(x, 0f, x, h, radioPaint)
        }
    }
}
