package com.spiritbox.app

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Serializa e lê a lista de favoritos como arquivo JSON. Pura, para ser testada.
 *
 * Formato exportado:
 *   {"format":"spiritbox-favorites","version":1,"favorites":[830,1760,25000]}
 *
 * A leitura tolera espaços e quebras de linha e também aceita uma lista simples de
 * números sem o campo "favorites" (`[830,1760]`), que é o que um arquivo editado à
 * mão costuma virar.
 */
object FavoritesCodec {

    const val FORMAT = "spiritbox-favorites"
    const val VERSION = 1

    private const val FIELD_FAVORITES = "\"favorites\""

    /** Casamento com a mesma tolerância de 1 kHz usada no histórico e na watch list. */
    const val TOLERANCE_KHZ = 1.0

    /** Resultado de uma importação: a lista nova (unida, sem duplicatas, ordenada)
     * e quantas frequências foram adicionadas. */
    data class MergeResult(val favorites: List<Double>, val added: Int)

    fun export(favorites: List<Double>): String {
        val sb = StringBuilder()
        sb.append("{\"format\":\"").append(FORMAT).append("\",")
            .append("\"version\":").append(VERSION).append(',')
            .append("\"favorites\":[")
        var first = true
        for (freq in favorites) {
            val f = normalize(freq) ?: continue
            if (!first) sb.append(',')
            first = false
            sb.append(formatNumber(f))
        }
        sb.append("]}")
        return sb.toString()
    }

    /** Número inteiro sem vírgula fica mais limpo para quem editar à mão. */
    private fun formatNumber(freq: Double): String =
        if (freq == freq.toLong().toDouble()) freq.toLong().toString() else freq.toString()

    /**
     * Interpreta o texto do arquivo. Retorna `null` se não parece um arquivo de
     * favoritos; caso contrário a lista de frequências em kHz (vazia quando o
     * arquivo é válido mas não traz nenhuma frequência útil).
     */
    fun parse(text: String): List<Double>? {
        val body = extractArray(text) ?: return null
        return body.split(',')
            .map { it.trim() }
            .mapNotNull { it.toDoubleOrNull() }
            .mapNotNull { normalize(it) }
    }

    /** Acha o conteúdo do primeiro par de colchetes: o do campo "favorites" ou,
     * se ele não existir, o primeiro array do documento. */
    private fun extractArray(text: String): String? {
        val fieldIndex = text.indexOf(FIELD_FAVORITES)
        val startFrom = if (fieldIndex >= 0) fieldIndex else 0
        val open = text.indexOf('[', startFrom)
        val close = if (open >= 0) text.indexOf(']', open) else -1
        if (open < 0 || close < 0) return null
        return text.substring(open + 1, close)
    }

    /** Une [new] à lista [existing] sem duplicatas (tolerância de 1 kHz), ordenada. */
    fun merge(new: List<Double>, existing: List<Double>): MergeResult {
        val out = existing.toMutableList()
        var added = 0
        for (freq in new) {
            val f = normalize(freq) ?: continue
            if (f <= 0) continue
            if (out.none { abs(it - f) < TOLERANCE_KHZ }) {
                out.add(f)
                added++
            }
        }
        return MergeResult(out.sorted(), added)
    }

    private fun normalize(freq: Double): Double? {
        if (freq.isNaN() || freq.isInfinite()) return null
        val f = (freq * 1000.0).roundToLong() / 1000.0
        return if (f <= 0) null else f
    }
}