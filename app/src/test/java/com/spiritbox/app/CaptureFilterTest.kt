package com.spiritbox.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureFilterTest {

    private val linha =
        "1.760 MHz  ·  nível 31%  ·  2026-10-06 19:36  ·  SW  ·  websdr.ewi.utwente.nl:8901"

    @Test
    fun `consulta vazia mostra tudo`() {
        assertTrue(CaptureFilter.matches(linha, ""))
        assertTrue(CaptureFilter.matches(linha, "   "))
    }

    @Test
    fun `busca por texto ignora acento e maiuscula`() {
        assertTrue(CaptureFilter.matches(linha, "nivel"))
        assertTrue(CaptureFilter.matches(linha, "NÍVEL"))
        assertTrue(CaptureFilter.matches(linha, "sw"))
        assertFalse(CaptureFilter.matches(linha, "banana"))
    }

    @Test
    fun `numero sem separador acha a frequencia formatada`() {
        assertTrue(CaptureFilter.matches(linha, "1760"))
        assertTrue(CaptureFilter.matches(linha, "1.760"))
        assertFalse(CaptureFilter.matches(linha, "9999"))
    }

    @Test
    fun `busca por data e servidor`() {
        assertTrue(CaptureFilter.matches(linha, "2026-10"))
        assertTrue(CaptureFilter.matches(linha, "websdr"))
        assertFalse(CaptureFilter.matches(linha, "websdr 9999"))
    }

    @Test
    fun `todos os tokens precisam bater`() {
        assertTrue(CaptureFilter.matches(linha, "sw 31"))
        assertFalse(CaptureFilter.matches(linha, "sw banana"))
    }
}
