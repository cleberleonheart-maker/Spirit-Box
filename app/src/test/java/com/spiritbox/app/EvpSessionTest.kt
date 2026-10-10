package com.spiritbox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvpSessionTest {

    @Test
    fun `offset negativo e' zerado`() {
        val s = EvpSession()
        s.addMarker(-500, 1000.0, 10)
        assertEquals(0L, s.markers()[0].offsetMs)
    }

    @Test
    fun `offset por amostras`() {
        val s = EvpSession(sampleRate = 16000)
        assertEquals(0L, s.offsetForSamples(0))
        assertEquals(1000L, s.offsetForSamples(16000))
        assertEquals(500L, s.offsetForSamples(8000))
    }

    @Test
    fun `sessao vazia`() {
        val s = EvpSession()
        assertTrue(s.isEmpty())
        s.addMarker(100, 1760.0, 30)
        assertFalse(s.isEmpty())
    }

    @Test
    fun `json contem taxa e marcadores`() {
        val s = EvpSession(sampleRate = 16000)
        s.addMarker(1000, 1760.0, 30)
        s.addMarker(2000, 100000.0, 55)
        val json = s.toJson()
        assertTrue(json.contains("\"sample_rate\":16000"))
        assertTrue(json.contains("\"offset_ms\":1000"))
        assertTrue(json.contains("\"freq_khz\":1760"))
        assertTrue(json.contains("\"level\":55"))
        // Duas casas para o separador de milhar ficam preservadas em kHz inteiro.
        assertTrue(json.contains("\"markers\":[{") )
    }
}
