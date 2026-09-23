package com.spiritbox.app.radio

import android.content.Context
import android.hardware.radio.RadioManager
import android.hardware.radio.RadioTuner
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.spiritbox.app.R

class FmTuner(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onAvailability: (Boolean) -> Unit
) {
    private val tag = "SpiritBox:FmTuner"
    private val radioManager: RadioManager? =
        context.getSystemService("radio") as? RadioManager

    private var tuner: RadioTuner? = null
    private var fmDescriptor: RadioManager.FmBandDescriptor? = null
    private val handler = Handler(Looper.getMainLooper())

    fun hasModule(): Boolean {
        val rm = radioManager ?: return false
        return try {
            val modules = rm.listModules()
            if (modules.isNullOrEmpty()) return false
            fmDescriptor = modules.firstNotNullOfOrNull { m ->
                m.bands.filterIsInstance<RadioManager.FmBandDescriptor>().firstOrNull()
            }
            fmDescriptor != null
        } catch (e: Exception) {
            Log.w(tag, "Falha ao listar módulos de rádio", e)
            false
        }
    }

    fun start(): Boolean {
        val rm = radioManager ?: return false
        if (fmDescriptor == null && !hasModule()) return false
        return try {
            val module = rm.listModules().first { it.bands.any { b -> b is RadioManager.FmBandDescriptor } }
            val callback = object : RadioTuner.Callback() {
                override fun onTuneFailed(reason: Int, programIndex: Int) {
                    onStatus(context.getString(R.string.fm_tune_failed, reason))
                }

                override fun onAntennaState(connected: Boolean) {
                    if (!connected) onStatus(context.getString(R.string.fm_antenna_removed))
                }

                override fun onError(status: Int) {
                    onStatus(context.getString(R.string.fm_tuner_error, status))
                }
            }
            tuner = rm.open(module, callback)
            onAvailability(true)
            onStatus(context.getString(R.string.fm_scanning))
            true
        } catch (e: Exception) {
            Log.w(tag, "Falha ao abrir sintonizador FM", e)
            onAvailability(false)
            onStatus(context.getString(R.string.fm_unavailable_device))
            false
        }
    }

    fun tune(freqKHz: Double) {
        val t = tuner ?: return
        val d = fmDescriptor ?: return
        try {
            val clamped = freqKHz.toInt().coerceIn(d.lowerLimit, d.upperLimit)
            val cfg = RadioManager.BandConfig(d)
            cfg.setFrequency(clamped)
            t.tune(cfg)
        } catch (e: Exception) {
            Log.w(tag, "Falha ao sintonizar ${freqKHz.toInt()} kHz", e)
            onStatus(context.getString(R.string.fm_tune_error, freqKHz.toInt()))
        }
    }

    fun signal(): Double {
        val t = tuner ?: return 0.0
        return try {
            (t.getSignalStrength() / 100.0).coerceIn(0.0, 1.0)
        } catch (e: Exception) {
            Log.w(tag, "Falha ao ler sinal FM", e)
            0.0
        }
    }

    fun stop() {
        try {
            tuner?.close()
        } catch (e: Exception) {
            Log.w(tag, "Falha ao fechar sintonizador FM", e)
        }
        tuner = null
    }
}