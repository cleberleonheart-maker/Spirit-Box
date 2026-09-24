package com.spiritbox.app

import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.sqrt

/** Monitora a atividade do sinal para derivar um score de "movimento" 0..100. */
class ActivityMonitor {

    data class Capture(val freqKhz: Double, val level: Int)

    private val captures = ArrayDeque<Capture>()
    private val rmsRing = ArrayDeque<Double>()
    private var bandSpan = 1.0

    fun configureBand(startKhz: Double, endKhz: Double) {
        bandSpan = (endKhz - startKhz).coerceAtLeast(1.0)
    }

    fun onRms(level: Double) {
        synchronized(this) {
            rmsRing.addLast(level)
            while (rmsRing.size > 48) rmsRing.removeFirst()
        }
    }

    fun onCapture(freqKhz: Double, level: Int) {
        synchronized(this) {
            captures.addLast(Capture(freqKhz, level))
            while (captures.size > 32) captures.removeFirst()
        }
    }

    fun movement(): Int {
        synchronized(this) {
            if (captures.size < 3) {
                val r = rmsStd() ?: 0.0
                return (r * 18.0).coerceIn(0.0, 100.0).toInt()
            }
            val arr = captures.toList()
            val levels = arr.map { it.level.toDouble() }
            val lStd = std(levels)
            var driftSum = 0.0
            for (i in 1 until arr.size) {
                driftSum += abs(arr[i].freqKhz - arr[i - 1].freqKhz)
            }
            val drift = driftSum / (arr.size - 1) / bandSpan
            val r = rmsStd() ?: 0.0
            val v = (lStd * 3.0) + (drift * 90.0) + (r * 12.0)
            return v.coerceIn(0.0, 100.0).toInt()
        }
    }

    fun reset() {
        synchronized(this) {
            captures.clear()
            rmsRing.clear()
        }
    }

    private fun rmsStd(): Double? {
        if (rmsRing.size < 4) return null
        return std(rmsRing.toList())
    }

    private fun std(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.sum() / values.size
        val varSum = values.sumOf { (it - mean) * (it - mean) }
        return sqrt(varSum / values.size)
    }
}