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

    /** Alguns aparelhos nao expõem o sensor calibrado e entregam so o
     * TYPE_MAGNETIC_FIELD_UNCALIBRATED. Pedir apenas o calibrado fazia o app
     * concluir que nao havia magnetometro em aparelhos que tem, e o mapa
     * recusava todo ponto. Os dois trazem x, y e z em microteslas, entao o
     * modulo do campo serve igual. */
    private val calibrated = sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val mag = calibrated
        ?: sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
    private val magType = mag?.type ?: -1

    /** true quando so o sensor nao calibrado esta disponivel. */
    val uncalibratedOnly: Boolean get() = calibrated == null && mag != null

    var available = false
        private set

    private var fieldUv = 0.0
    private var baselineUv = 0.0
    private var lastSampleElapsed = 0L

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null || event.sensor.type != magType) return
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

    /** Ver [SensorLease]: o dismiss do Dialog chega atrasado pela fila do
     * Looper e chegava desregistrando o sensor que o mapa acabara de
     * registrar. */
    private val lease = SensorLease()
    private var registered = false

    fun start() {
        if (!lease.acquire()) return
        val m = mag
        val ok = m != null &&
            sm?.registerListener(listener, m, SensorManager.SENSOR_DELAY_NORMAL) == true
        registered = ok
        available = ok
        if (ok) {
            baselineUv = 0.0
            fieldUv = 0.0
            lastSampleElapsed = 0L
        }
    }

    fun stop() {
        if (!lease.release()) return
        if (registered) sm?.unregisterListener(listener)
        registered = false
        available = false
    }

    /** Solta tudo de uma vez, para quando a Activity vai embora e nenhum
     * dismiss pendente pode ser esperado. */
    fun stopAll() {
        lease.releaseAll()
        if (registered) sm?.unregisterListener(listener)
        registered = false
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
