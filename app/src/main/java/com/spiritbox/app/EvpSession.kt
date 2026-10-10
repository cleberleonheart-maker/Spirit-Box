package com.spiritbox.app

import java.util.Locale

/** Marcadores de uma sessão EVP: cada captura de rádio vira um ponto no tempo,
 * relativo ao início da gravação do microfone. Puro, para poder ser testado — o
 * JSON é montado à mão para não depender do org.json do Android. */
class EvpSession(val sampleRate: Int = DEFAULT_SAMPLE_RATE) {

    data class Marker(val offsetMs: Long, val freqKHz: Double, val level: Int)

    private val markers = ArrayList<Marker>()

    @Synchronized
    fun addMarker(offsetMs: Long, freqKHz: Double, level: Int) {
        markers.add(Marker(offsetMs.coerceAtLeast(0L), freqKHz, level))
    }

    @Synchronized
    fun markers(): List<Marker> = ArrayList(markers)

    @Synchronized
    fun isEmpty(): Boolean = markers.isEmpty()

    /** Instante do marcador a partir de uma contagem de amostras gravadas. */
    fun offsetForSamples(samples: Long): Long =
        if (sampleRate <= 0) 0L else samples * 1000L / sampleRate

    @Synchronized
    fun toJson(): String {
        val sb = StringBuilder()
        sb.append("{\"sample_rate\":").append(sampleRate).append(",\"markers\":[")
        for ((i, m) in markers.withIndex()) {
            if (i > 0) sb.append(',')
            sb.append("{\"offset_ms\":").append(m.offsetMs)
            sb.append(",\"freq_khz\":").append(formatDouble(m.freqKHz))
            sb.append(",\"level\":").append(m.level).append('}')
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun formatDouble(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(Locale.US, "%.3f", v)

    companion object {
        const val DEFAULT_SAMPLE_RATE = 16000
    }
}
