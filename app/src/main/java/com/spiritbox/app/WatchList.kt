package com.spiritbox.app

/** Lista de observação (watch list): decide se uma frequência capturada casa com
 * alguma frequência marcada, dentro de uma tolerância em kHz. Pura, para ser
 * testada sem Android. Reusa os favoritos como a lista de frequências observadas. */
object WatchList {

    /** Quão perto (em kHz) uma captura precisa estar para casar com uma marcada. */
    const val TOLERANCE_KHZ = 1.0

    /** Verdadeiro se [freqKHz] cai dentro da tolerância de alguma frequência em
     * [watched]. Lista vazia nunca casa. */
    fun contains(
        watched: List<Double>,
        freqKHz: Double,
        toleranceKHz: Double = TOLERANCE_KHZ
    ): Boolean = watched.any { kotlin.math.abs(it - freqKHz) <= toleranceKHz }

    /** Rótulo curto da frequência: kHz inteiro abaixo de 1 MHz, senão MHz com 3
     * casas (ex.: "1760 kHz" e "1.760 MHz" descrevem a mesma faixa). */
    fun label(freqKHz: Double): String = if (freqKHz >= 1000) {
        String.format(java.util.Locale.US, "%.3f MHz", freqKHz / 1000)
    } else {
        "${freqKHz.toInt()} kHz"
    }
}
