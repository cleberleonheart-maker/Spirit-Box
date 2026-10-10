package com.spiritbox.app.radio

import kotlin.math.log10

/**
 * Relacao sinal/ruido (SNR) da captura, em dB acima da referencia (o piso de ruido
 * medido da faixa). Pura, para ser testada.
 *
 * [peak] e [reference] sao amplitudes RMS lineares (mesma unidade), entao o fator e
 * 20*log10 — e nao 10*log10, que valeria se ja fossem potencia. Resultado inteiro,
 * preso a [MIN_DB]..[MAX_DB]: abaixo de 0 dB a captura nem superou o piso, e acima de
 * [MAX_DB] o numero deixa de ser util para o usuario.
 */
object Snr {

    const val MIN_DB = 0
    const val MAX_DB = 99

    fun db(peak: Double, reference: Double): Int {
        if (peak <= 0.0 || reference <= 0.0) return MIN_DB
        val ratio = peak / reference
        if (ratio <= 0.0) return MIN_DB
        val db = 20.0 * log10(ratio)
        if (db <= MIN_DB) return MIN_DB
        if (db >= MAX_DB) return MAX_DB
        return db.toInt()
    }
}
