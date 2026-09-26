package com.spiritbox.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.spiritbox.app.radio.SweepEngine
import java.util.Locale
import kotlin.math.roundToInt

class WaterfallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val COLS = 240
        private const val ROWS = 48

        /** Ampliacao nearest-neighbor do PNG exportado: 240x48 -> 1440x288. */
        private const val EXPORT_SCALE = 6

        private const val AXIS_HEIGHT_DP = 15f
        private const val AXIS_TEXT_SP = 9f
        private const val AXIS_TARGET_TICKS = 4

        /** O waterfall e' sempre escuro (fundo fixo, independente do tema), entao o
         * eixo nao pode usar @color/text_secondary, que e' escuro no tema claro. */
        private const val AXIS_COLOR = 0xFF8A93A5.toInt()

        /** Passo "redondo" (1, 2 ou 5 x 10^n) para as marcas do eixo. */
        fun axisStepKHz(spanKHz: Double, targetTicks: Int = AXIS_TARGET_TICKS): Double {
            if (spanKHz <= 0 || targetTicks <= 0) return 0.0
            val raw = spanKHz / targetTicks
            var mag = 1.0
            while (mag * 10.0 <= raw) mag *= 10.0
            val norm = raw / mag
            val mult = when {
                norm <= 1.0 -> 1.0
                norm <= 2.0 -> 2.0
                norm <= 5.0 -> 5.0
                else -> 10.0
            }
            return mult * mag
        }

        /** Frequencias (kHz) onde o eixo ganha uma marca, dentro da faixa. */
        fun axisTicks(startKHz: Double, endKHz: Double, targetTicks: Int = AXIS_TARGET_TICKS):
            DoubleArray {
            val step = axisStepKHz(endKHz - startKHz, targetTicks)
            if (step <= 0.0) return DoubleArray(0)
            val out = ArrayList<Double>()
            var f = Math.ceil(startKHz / step) * step
            while (f <= endKHz + step * 0.001) {
                out.add(f)
                f += step
            }
            return out.toDoubleArray()
        }

        /**
         * Rótulo do eixo: kHz ate 999, depois MHz — 225000 kHz e 400000 kHz nao
         * cabem embaixo de uma coluna, e "225 MHz" le melhor que "225000".
         */
        fun formatKHz(freqKHz: Double): String {
            if (freqKHz < 1000.0) return "${freqKHz.toInt()} kHz"
            val mhz = freqKHz / 1000.0
            val txt = if (mhz >= 100.0) {
                mhz.toInt().toString()
            } else {
                String.format(Locale.US, "%.1f", mhz).replace('.', ',')
            }
            return "$txt MHz"
        }

        /**
         * Frequencia de uma posicao horizontal do waterfall ([fraction] de 0 a 1),
         * arredondada para o passo da faixa. Sem o arredondamento o toque cairia
         * entre duas frequencias e a varredura seguiria num valor que ela nunca
         * visita.
         */
        fun frequencyAt(
            startKHz: Double,
            endKHz: Double,
            stepKHz: Double,
            fraction: Double
        ): Double {
            val span = endKHz - startKHz
            if (span <= 0) return startKHz
            val t = fraction.coerceIn(0.0, 1.0)
            val raw = startKHz + t * span
            if (stepKHz <= 0) return raw.coerceIn(startKHz, endKHz)
            val snapped = startKHz + Math.round((raw - startKHz) / stepKHz) * stepKHz
            return snapped.coerceIn(startKHz, endKHz)
        }

        /**
         * Nivel pintado de uma coluna. O pico manda na escala, mas um sinal
         * modulado (voz, dados) dentro do mesmo dwell clareia a coluna em relacao a
         * uma portadora continua: [max] e [min] sao o maior e o menor nivel vistos
         * naquela frequencia. Sem isto, dwell de 300 ms vira um unico pixel e uma
         * portadora CW fica indistinguivel de uma emissora de voz.
         */
        fun apparentLevel(maxLevel: Float, minLevel: Float): Float {
            if (maxLevel <= 0f) return 0f
            val spread = ((maxLevel - minLevel) / maxLevel).coerceIn(0f, 1f)
            val gain = 0.75f + 0.25f * spread
            return maxLevel * gain
        }
    }

    private var bandStartKHz = SweepEngine.PRESETS[0].startKHz.toDouble()
    private var bandEndKHz = SweepEngine.PRESETS[0].endKHz.toDouble()
    private var bandStepKHz = SweepEngine.PRESETS[0].stepKHz.toDouble()

    private var currentMax = FloatArray(COLS)
    private var currentMin = FloatArray(COLS)
    private val marks = BooleanArray(COLS)
    private val pixels = IntArray(COLS * ROWS)
    private val bitmap: Bitmap = Bitmap.createBitmap(COLS, ROWS, Bitmap.Config.ARGB_8888)
    private val paint = Paint().apply { isFilterBitmap = false }
    private val markColor by lazy { context.getColor(R.color.capture_mark) }
    private val srcRect = Rect(0, 0, COLS, ROWS)
    private val dstRect = Rect()

    private val density = resources.displayMetrics.density
    private val axisHeightPx = (AXIS_HEIGHT_DP * density).roundToInt()
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AXIS_COLOR
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, AXIS_TEXT_SP, resources.displayMetrics
        )
    }
    private val axisTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AXIS_COLOR
        strokeWidth = density
    }

    /** Chamado quando o usuario toca numa coluna: [freqKHz] ja vem arredondado para
     * o passo da faixa. Null enquanto ninguem escucha (exportacao, testes). */
    var onTapped: ((freqKHz: Double) -> Unit)? = null

    private val gestures = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (width <= 0) return true
                onTapped?.invoke(frequencyAtX(e.x))
                return true
            }

            // O long-press ja e' usado para exportar o PNG. Como o toque passa por
            // este detector, o listener do Activity so dispara se o proprio detector
            // repassar o evento.
            override fun onLongPress(e: MotionEvent) {
                performLongClick()
            }
        }
    )

    init {
        pixels.fill(bgColor())
    }

    fun configure(startKHz: Double, endKHz: Double, stepKHz: Double = 0.0) {
        bandStartKHz = startKHz
        bandEndKHz = endKHz
        bandStepKHz = stepKHz
        clear()
    }

    fun clear() {
        pixels.fill(bgColor())
        currentMax.fill(0f)
        currentMin.fill(Float.MAX_VALUE)
        marks.fill(false)
        invalidate()
    }

    fun addSample(freqKHz: Double, rms: Double) {
        val span = bandEndKHz - bandStartKHz
        if (span <= 0) return
        val t = ((freqKHz - bandStartKHz) / span).toFloat().coerceIn(0f, 1f)
        val col = (t * (COLS - 1)).toInt().coerceIn(0, COLS - 1)
        val v = rms.toFloat()
        if (v > currentMax[col]) currentMax[col] = v
        if (v < currentMin[col]) currentMin[col] = v
    }

    fun markCapture(freqKHz: Double) {
        marks[columnFor(freqKHz)] = true
        invalidate()
    }

    fun nextCycle() {
        System.arraycopy(pixels, COLS, pixels, 0, pixels.size - COLS)
        val bottomStart = (ROWS - 1) * COLS
        for (c in 0 until COLS) {
            val seen = currentMax[c] > 0f
            val min = if (seen) currentMin[c].coerceAtMost(currentMax[c]) else 0f
            pixels[bottomStart + c] =
                if (marks[c]) markColor else colorFor(apparentLevel(currentMax[c], min))
        }
        currentMax.fill(0f)
        currentMin.fill(Float.MAX_VALUE)
        invalidate()
    }

    /** Copia o waterfall atual para um Bitmap (com marcadores e eixo), ampliado para
     * ter tamanho de leitura. A 1:1 a imagem sai com 240x48 px e qualquer
     * visualizador abre uma tira de meio pixel de altura. */
    fun snapshotBitmap(): Bitmap {
        val raw = Bitmap.createBitmap(COLS, ROWS, Bitmap.Config.ARGB_8888)
        raw.setPixels(pixels, 0, COLS, 0, 0, COLS, ROWS)
        for (c in 0 until COLS) {
            if (marks[c]) raw.setPixel(c, ROWS - 1, markColor)
        }
        val out = Bitmap.createScaledBitmap(
            raw, COLS * EXPORT_SCALE, ROWS * EXPORT_SCALE, false
        )
        // O eixo e' desenhado depois da ampliacao: escalado junto, o texto sairia
        // serrilhado de 6x.
        drawAxis(Canvas(out), COLS * EXPORT_SCALE, ROWS * EXPORT_SCALE, EXPORT_SCALE.toFloat())
        return out
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bitmap.setPixels(pixels, 0, COLS, 0, 0, COLS, ROWS)
        val graphH = (height - axisHeightPx).coerceAtLeast(0)
        dstRect.set(0, 0, width, graphH)
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
        if (axisHeightPx > 0 && width > 0) {
            drawAxis(canvas, width, graphH, 1f)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestures.onTouchEvent(event)
        return handled || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    private fun frequencyAtX(x: Float): Double {
        val span = bandEndKHz - bandStartKHz
        if (span <= 0 || width <= 0) return bandStartKHz
        return frequencyAt(bandStartKHz, bandEndKHz, bandStepKHz, (x / width).toDouble())
    }

    private fun columnFor(freqKHz: Double): Int {
        val span = bandEndKHz - bandStartKHz
        if (span <= 0) return 0
        val t = ((freqKHz - bandStartKHz) / span).toFloat().coerceIn(0f, 1f)
        return (t * (COLS - 1)).toInt().coerceIn(0, COLS - 1)
    }

    private fun drawAxis(canvas: Canvas, w: Int, graphH: Int, scale: Float) {
        val span = bandEndKHz - bandStartKHz
        if (span <= 0 || w <= 0) return
        val top = graphH + (2f * scale)
        val textH = axisPaint.textSize
        for (tick in axisTicks(bandStartKHz, bandEndKHz)) {
            val x = (columnFor(tick).toFloat() + 0.5f) / COLS * w
            canvas.drawLine(x, top - scale, x, top, axisTickPaint)
            val label = formatKHz(tick)
            val tw = axisPaint.measureText(label)
            // Rotulo centralizado na marca, mas sem sair do desenho.
            val lx = (x - tw / 2f).coerceIn(0f, (w - tw).coerceAtLeast(0f))
            canvas.drawText(label, lx, top + textH * 0.85f, axisPaint)
        }
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