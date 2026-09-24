package com.spiritbox.app.radio

import android.content.Context
import android.util.Log
import com.spiritbox.app.R
import com.spiritbox.app.SpiritBoxEvents
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class SweepEngine(
    private val context: Context,
    private val tuner: SweepTuner,
    private val onCaptureHook: ((freqKHz: Double, level: Int) -> Unit)? = null
) {

    data class BandRange(
        val name: String,
        val startKHz: Int,
        val endKHz: Int,
        val stepKHz: Int,
        val modulation: Int,
        val band: Int,
        val loKHz: Double,
        val hiKHz: Double
    )

    interface SweepTuner {
        fun start()
        fun stop()
        fun tune(freqKHz: Double, r: BandRange)
        val currentRms: Double
    }

    companion object {
        val PRESETS = listOf(
            BandRange("Onda média (AM)", 520, 1700, 5, 1, 0, -4.5, 4.5),
            BandRange("Onda curta (SW)", 1710, 30000, 5, 0, 1, -4.5, 4.5),
            BandRange("Faixa do cidadão (CB)", 26500, 28000, 5, 1, 0, -4.5, 4.5),
            BandRange("VHF aeronáutica", 118000, 137000, 25, 0, 1, -4.5, 4.5),
            BandRange("FM 87-108 MHz", 87000, 108000, 200, 2, 1, -4.5, 4.5),
            BandRange("METEO 162 MHz", 162400, 162550, 25, 2, 1, -4.5, 4.5),
            BandRange("VHF marítimo", 156000, 163000, 25, 2, 1, -4.5, 4.5),
            BandRange("Militar 225-400 MHz", 225000, 400000, 25, 0, 1, -4.5, 4.5)
        )

        private const val TAG = "SpiritBox"
        private const val TICK_MS = 25L

        fun nextFreq(freqKHz: Double, stepKHz: Int): Double = freqKHz + stepKHz

        fun isWithinRange(freqKHz: Double, endKHz: Int): Boolean = freqKHz <= endKHz

        fun bandId(r: BandRange): Int {
            val idx = PRESETS.indexOf(r).coerceAtLeast(0)
            return if (idx < PRESET_BAND_IDS.size) PRESET_BAND_IDS[idx] else PRESET_BAND_IDS.first()
        }

        private val PRESET_BAND_IDS = listOf(
            com.spiritbox.app.R.string.band_mw,
            com.spiritbox.app.R.string.band_sw,
            com.spiritbox.app.R.string.band_cb,
            com.spiritbox.app.R.string.band_aviation,
            com.spiritbox.app.R.string.band_fm,
            com.spiritbox.app.R.string.band_meteo,
            com.spiritbox.app.R.string.band_marine,
            com.spiritbox.app.R.string.band_military
        )
    }

    private enum class Phase { TUNE, SETTLE, HOLD, GAP }

    @Volatile
    private var running = false

    @Volatile
    var hold = false
        set(value) {
            field = value
            if (value) {
                captureStreak.clear()
                lastCaptureByFreq.remove(currentFreq.toLong())
            }
        }

    @Volatile
    var requiredPasses: Int = 1

    @Volatile
    var sweepGapMs: Long = 300

    @Volatile
    var range: BandRange = PRESETS[0]

    @Volatile
    var dwellMs: Long = 300

    @Volatile
    var settleMs: Long = 150

    @Volatile
    var captureThreshold: Double = 0.12

    @Volatile
    var currentFreq: Double = 0.0
        private set

    private var scheduler: ScheduledExecutorService? = null
    private var phase = Phase.TUNE
    private var phaseUntil = 0L
    private var peak = 0.0
    private var lastRmsPushMs = 0L
    private val captureStreak = HashMap<Long, Int>()
    private val lastCaptureByFreq = HashMap<Long, Long>()

    fun configureRange(r: BandRange) {
        range = r
    }

    fun setDwell(ms: Long) {
        dwellMs = ms.coerceIn(20L, 5000L)
    }

    fun setSettle(ms: Long) {
        settleMs = ms.coerceIn(0L, 2000L)
    }

    fun setSweepGap(ms: Long) {
        sweepGapMs = ms.coerceIn(0L, 10000L)
    }

    fun start() {
        if (running) return
        running = true
        phase = Phase.TUNE
        peak = 0.0
        currentFreq = range.startKHz.toDouble()
        tuner.start()
        SpiritBoxEvents.pushStatus(context.getString(R.string.status_sweep_started))
        val ex = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "SweepEngine-Ticker").apply { isDaemon = true }
        }
        scheduler = ex
        ex.scheduleWithFixedDelay({ tick() }, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS)
    }

    /** Prende a varredura em [freqKHz] (usado por favoritos). */
    fun stayOn(freqKHz: Double) {
        if (!running || freqKHz <= 0) return
        currentFreq = freqKHz
        hold = true
        phase = Phase.TUNE
    }

    fun stop() {
        if (!running) return
        running = false
        scheduler?.shutdownNow()
        scheduler = null
        tuner.stop()
        SpiritBoxEvents.pushStatus(context.getString(R.string.status_stopped))
    }

    private fun tick() {
        if (!running) return
        try {
            step()
        } catch (e: Exception) {
            Log.w(TAG, "Erro no tick de varredura", e)
        }
    }

    private fun step() {
        val r = range
        when (phase) {
            Phase.TUNE -> {
                val freqNow = currentFreq
                tuner.tune(freqNow, r)
                SpiritBoxEvents.pushFreq(freqNow)
                phase = Phase.SETTLE
                phaseUntil = System.currentTimeMillis() + settleMs
            }
            Phase.SETTLE -> {
                if (System.currentTimeMillis() >= phaseUntil) {
                    peak = 0.0
                    phase = Phase.HOLD
                    phaseUntil = System.currentTimeMillis() + dwellMs
                }
            }
            Phase.HOLD -> {
                val v = tuner.currentRms
                if (v > peak) peak = v
                maybePushMeter()
                if (System.currentTimeMillis() >= phaseUntil) {
                    val freqEnd = currentFreq
                    if (peak >= captureThreshold) {
                        val key = freqEnd.toLong()
                        captureStreak[key] = (captureStreak[key] ?: 0) + 1
                    } else {
                        captureStreak.remove(freqEnd.toLong())
                    }
                    val passes = captureStreak[freqEnd.toLong()] ?: 0
                    val now = System.currentTimeMillis()
                    val trackTime = lastCaptureByFreq[freqEnd.toLong()] ?: 0L
                    if (passes >= requiredPasses && peak >= captureThreshold &&
                        now - trackTime > 60000
                    ) {
                        lastCaptureByFreq[freqEnd.toLong()] = now
                        val level = (peak * 100).toInt().coerceIn(0, 100)
                        SpiritBoxEvents.pushCapture(freqEnd, level)
                        try {
                            onCaptureHook?.invoke(freqEnd, level)
                        } catch (e: Exception) {
                            Log.w(TAG, "Falha no hook de captura", e)
                        }
                    }
                    SpiritBoxEvents.pushSweepCycle()
                    if (hold) {
                        phase = Phase.TUNE
                    } else {
                        val next = nextFreq(freqEnd, r.stepKHz)
                        if (isWithinRange(next, r.endKHz)) {
                            currentFreq = next
                            phase = Phase.TUNE
                        } else if (sweepGapMs > 0) {
                            phase = Phase.GAP
                            phaseUntil = System.currentTimeMillis() + sweepGapMs
                        } else {
                            currentFreq = r.startKHz.toDouble()
                            phase = Phase.TUNE
                        }
                    }
                }
            }
            Phase.GAP -> {
                if (System.currentTimeMillis() >= phaseUntil) {
                    currentFreq = r.startKHz.toDouble()
                    phase = Phase.TUNE
                }
            }
        }
    }

    private fun maybePushMeter() {
        val now = System.currentTimeMillis()
        if (now - lastRmsPushMs >= 120) {
            lastRmsPushMs = now
            val v = tuner.currentRms
            SpiritBoxEvents.pushRms(v)
            SpiritBoxEvents.pushWaterfall(currentFreq, v)
        }
    }
}

class WebSdrSweepTuner(private val client: WebSdrClient) : SweepEngine.SweepTuner {
    @Volatile
    private var current = 0.0

    fun setRms(v: Double) {
        current = v
    }

    override fun start() {
    }

    override fun stop() {
    }

    override fun tune(freqKHz: Double, r: SweepEngine.BandRange) {
        client.sendTune(freqKHz, r.band, r.loKHz, r.hiKHz, r.modulation)
    }

    override val currentRms: Double
        get() = current
}

class FmSweepTuner(private val fm: FmTuner) :
    SweepEngine.SweepTuner {
    override fun start() {
        fm.start()
    }

    override fun stop() {
        fm.stop()
    }

    override fun tune(freqKHz: Double, r: SweepEngine.BandRange) {
        fm.tune(freqKHz)
    }

    override val currentRms: Double
        get() = fm.signal()
}