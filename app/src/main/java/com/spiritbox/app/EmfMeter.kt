package com.spiritbox.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.sqrt

/** Leitor do magnetômetro. [milliGauss] devolve a variação do campo magnético em mG
 * relativa à linha de base do local; null se o sensor está ausente, ainda frio ou
 * parou de entregar amostras (evita gravar hotspot com leitura velha). */
class EmfMeter(context: Context) {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val mag = sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    var available = false
        private set

    private var fieldUv = 0.0
    private var baselineUv = 0.0
    private var lastSampleElapsed = 0L

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null || event.sensor.type != Sensor.TYPE_MAGNETIC_FIELD) return
            val x = event.values[0].toDouble()
            val y = event.values[1].toDouble()
            val z = event.values[2].toDouble()
            val microT = sqrt(x * x + y * y + z * z)
            fieldUv = microT
            lastSampleElapsed = SystemClock.elapsedRealtime()
            if (baselineUv <= 0.0) {
                baselineUv = microT
            } else {
                baselineUv += (microT - baselineUv) * 0.01
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        val m = mag
        available = m != null && sm?.registerListener(listener, m, SensorManager.SENSOR_DELAY_NORMAL) == true
        if (available) {
            baselineUv = 0.0
            fieldUv = 0.0
            lastSampleElapsed = 0L
        }
    }

    fun stop() {
        sm?.unregisterListener(listener)
        available = false
    }

    fun milliGauss(): Float? {
        if (!available || fieldUv <= 0.0) return null
        if (SystemClock.elapsedRealtime() - lastSampleElapsed > STALE_MS) return null
        return (abs(fieldUv - baselineUv) * 10.0).toFloat()
    }

    companion object {
        private const val STALE_MS = 2000L
    }
}
