package com.spiritbox.app

import java.text.Normalizer

/**
 * Busca no histórico de capturas: lógica pura, sem Android, testável.
 *
 * Regras:
 * - Consulta vazia deixa tudo visível.
 * - Vários tokens (separados por espaço) precisam **todos** aparecer.
 * - Acento não atrapalha: "nivel" acha "nível".
 * - Token só com números ignora separadores: "1760" acha "1.760 MHz" e
 *   "2026" acha a data "2026-10-06".
 */
object CaptureFilter {

    fun matches(haystack: String, query: String): Boolean {
        val h = fold(haystack)
        return tokens(query).all { tokenMatches(h, it) }
    }

    private fun tokens(query: String): List<String> =
        query.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .map { fold(it) }

    private fun tokenMatches(haystack: String, token: String): Boolean {
        if (haystack.contains(token, ignoreCase = true)) return true
        if (token.any { it.isDigit() } && token.none { it.isLetter() }) {
            val alvo = token.filter { it.isDigit() }
            if (alvo.isNotEmpty() && haystack.filter { it.isDigit() }.contains(alvo)) return true
        }
        return false
    }

    /** Remove acentos/diacríticos: "nível" vira "nivel". */
    private fun fold(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
