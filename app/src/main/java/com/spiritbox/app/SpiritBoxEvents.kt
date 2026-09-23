package com.spiritbox.app

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

object SpiritBoxEvents {
    interface Listener {
        fun onFreq(freqKHz: Double) {}
        fun onStatus(text: String) {}
        fun onCapture(freqKHz: Double, level: Int) {}
        fun onRms(rms: Double) {}
        fun onServiceStopped() {}
        fun onHoldChanged(hold: Boolean) {}
        fun onWaterfall(freqKHz: Double, rms: Double) {}
        fun onSweepCycle() {}
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    @Volatile
    var serviceRunning = false

    @Volatile
    var holdActive = false

    val main: Handler = Handler(Looper.getMainLooper())

    fun addListener(listener: Listener) = listeners.addIfAbsent(listener)

    fun removeListener(listener: Listener) = listeners.remove(listener)

    fun pushFreq(freqKHz: Double) {
        main.post { listeners.forEach { it.onFreq(freqKHz) } }
    }

    fun pushStatus(text: String) {
        main.post { listeners.forEach { it.onStatus(text) } }
    }

    fun pushCapture(freqKHz: Double, level: Int) {
        main.post { listeners.forEach { it.onCapture(freqKHz, level) } }
    }

    fun pushRms(rms: Double) {
        main.post { listeners.forEach { it.onRms(rms) } }
    }

    fun pushServiceStopped() {
        main.post { listeners.forEach { it.onServiceStopped() } }
    }

    fun pushHoldChanged(hold: Boolean) {
        main.post { listeners.forEach { it.onHoldChanged(hold) } }
    }

    fun pushWaterfall(freqKHz: Double, rms: Double) {
        main.post { listeners.forEach { it.onWaterfall(freqKHz, rms) } }
    }

    fun pushSweepCycle() {
        main.post { listeners.forEach { it.onSweepCycle() } }
    }
}