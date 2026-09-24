package com.spiritbox.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import com.spiritbox.app.radio.SweepEngine

class WaterfallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val COLS = 240
        private const val ROWS = 48
    }

    private var bandStartKHz = SweepEngine.PRESETS[0].startKHz.toDouble()
    private var bandEndKHz = SweepEngine.PRESETS[0].endKHz.toDouble()

    private var currentRow = FloatArray(COLS)
    private val marks = BooleanArray(COLS)
    private val pixels = IntArray(COLS * ROWS)
    private val bitmap: Bitmap = Bitmap.createBitmap(COLS, ROWS, Bitmap.Config.ARGB_8888)
    private val paint = Paint().apply { isFilterBitmap = false }
    private val markColor by lazy { context.getColor(R.color.capture_mark) }
    private val srcRect = Rect(0, 0, COLS, ROWS)
    private val dstRect = Rect()

    init {
        pixels.fill(bgColor())
    }

    fun configure(startKHz: Double, endKHz: Double) {
        bandStartKHz = startKHz
        bandEndKHz = endKHz
        clear()
    }

    fun clear() {
        pixels.fill(bgColor())
        currentRow.fill(0f)
        marks.fill(false)
        invalidate()
    }

    fun addSample(freqKHz: Double, rms: Double) {
        val span = bandEndKHz - bandStartKHz
        if (span <= 0) return
        val t = ((freqKHz - bandStartKHz) / span).toFloat().coerceIn(0f, 1f)
        val col = (t * (COLS - 1)).toInt().coerceIn(0, COLS - 1)
        val v = rms.toFloat()
        if (v > currentRow[col]) currentRow[col] = v
    }

    fun markCapture(freqKHz: Double) {
        val span = bandEndKHz - bandStartKHz
        if (span <= 0) return
        val t = ((freqKHz - bandStartKHz) / span).toFloat().coerceIn(0f, 1f)
        val col = (t * (COLS - 1)).toInt().coerceIn(0, COLS - 1)
        marks[col] = true
        invalidate()
    }

    fun nextCycle() {
        System.arraycopy(pixels, COLS, pixels, 0, pixels.size - COLS)
        val bottomStart = (ROWS - 1) * COLS
        for (c in 0 until COLS) {
            pixels[bottomStart + c] =
                if (marks[c]) markColor else colorFor(currentRow[c])
        }
        currentRow.fill(0f)
        invalidate()
    }

    /** Copia o waterfall atual para um Bitmap (com marcadores). */
    fun snapshotBitmap(): Bitmap {
        val out = Bitmap.createBitmap(COLS, ROWS, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, COLS, 0, 0, COLS, ROWS)
        for (c in 0 until COLS) {
            if (marks[c]) out.setPixel(c, ROWS - 1, markColor)
        }
        return out
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bitmap.setPixels(pixels, 0, COLS, 0, 0, COLS, ROWS)
        dstRect.set(0, 0, width, height)
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
    }

    private fun bgColor(): Int = Color.rgb(0x15, 0x1A, 0x24)

    private fun colorFor(level: Float): Int {
        val t = level.coerceIn(0f, 0.9f) / 0.9f
        if (t <= 0.01f) return bgColor()
        val r = (0x1A + ((0xFF - 0x1A) * t).toInt()).coerceIn(0, 255)
        val g = (0x06 + ((0x45 - 0x06) * t).toInt()).coerceIn(0, 255)
        val b = (0x08 + ((0x2A - 0x08) * t).toInt()).coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}