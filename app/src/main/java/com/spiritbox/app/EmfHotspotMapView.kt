package com.spiritbox.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Traça a trajetória do GPS com o campo em mG e marca hotspots maiores. */
class EmfHotspotMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Point(val lat: Double, val lng: Double, val mg: Float, val time: Long)

    private val points = ArrayList<Point>()
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x28, 0x28, 0x30)
        strokeWidth = 2f
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x10, 0x12, 0x16) }

    fun points(): ArrayList<Point> = points

    fun isEmpty(): Boolean = points.isEmpty()

    fun addPoint(lat: Double, lng: Double, mg: Float) {
        points.add(Point(lat, lng, mg, System.currentTimeMillis()))
        postInvalidateOnAnimation()
    }

    fun setPoints(list: List<Point>) {
        points.clear()
        points.addAll(list)
        postInvalidateOnAnimation()
    }

    fun clear() {
        points.clear()
        postInvalidateOnAnimation()
    }

    private fun normFor(mg: Float, minMg: Float, maxMg: Float): Float =
        if (maxMg <= minMg) 0.5f else ((mg - minMg) / (maxMg - minMg)).coerceIn(0f, 1f)

    private fun colorFor(mg: Float, minMg: Float, maxMg: Float): Int {
        val t = normFor(mg, minMg, maxMg)
        return Color.rgb(
            (0x20 + 0xC0 * t).roundToInt(),
            (0xB0 * (1f - t)).roundToInt(),
            0x20
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        canvas.drawLine(0f, h / 2f, w, h / 2f, gridPaint)
        canvas.drawLine(w / 2f, 0f, w / 2f, h, gridPaint)
        if (points.isEmpty()) return

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLng = Double.MAX_VALUE
        var maxLng = -Double.MAX_VALUE
        var minMg = Float.MAX_VALUE
        var maxMg = -Float.MAX_VALUE
        points.forEach {
            minLat = min(minLat, it.lat); maxLat = max(maxLat, it.lat)
            minLng = min(minLng, it.lng); maxLng = max(maxLng, it.lng)
            minMg = min(minMg, it.mg); maxMg = max(maxMg, it.mg)
        }
        val spanLat = (maxLat - minLat).coerceAtLeast(0.00001)
        val spanLng = (maxLng - minLng).coerceAtLeast(0.00001)
        val pad = 26f
        val sw = w - pad * 2f
        val sh = h - pad * 2f
        fun sx(lng: Double): Float = pad + ((lng - minLng) / spanLng * sw).toFloat()
        fun sy(lat: Double): Float = pad + (sh - ((lat - minLat) / spanLat * sh).toFloat())

        val n = points.size
        for (i in 1 until n) {
            val a = points[i - 1]
            val b = points[i]
            pathPaint.color = colorFor(b.mg, minMg, maxMg)
            canvas.drawLine(sx(a.lng), sy(a.lat), sx(b.lng), sy(b.lat), pathPaint)
        }
        for (p in points) {
            val t = normFor(p.mg, minMg, maxMg)
            val r = 6f + t * 16f
            dotPaint.color = colorFor(p.mg, minMg, maxMg)
            canvas.drawCircle(sx(p.lng), sy(p.lat), r, dotPaint)
            dotPaint.color = Color.argb(0xCC, 0xFF, 0xFF, 0xFF)
            canvas.drawCircle(sx(p.lng), sy(p.lat), r * 0.35f, dotPaint)
        }
    }
}