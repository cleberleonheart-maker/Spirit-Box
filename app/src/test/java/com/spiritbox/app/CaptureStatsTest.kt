package com.spiritbox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureStatsTest {

    @Test
    fun `resumo conta total e frequencias distintas`() {
        val captures = listOf(
            Sighting(830.0, 20, 1000),
            Sighting(830.4, 25, 2000),
            Sighting(1760.0, 15, 3000)
        )
        val summary = CaptureStats.summarize(captures)
        assertEquals(3, summary.total)
        assertEquals(2, summary.distinct)
    }

    @Test
    fun `top vem por avistamentos e depois por nivel`() {
        val captures = listOf(
            Sighting(830.0, 20, 1000),
            Sighting(830.0, 30, 2000),
            Sighting(1760.0, 15, 3000),
            Sighting(1760.0, 18, 4000),
            Sighting(1760.0, 19, 5000),
            Sighting(25000.0, 40, 6000)
        )
        val top = CaptureStats.summarize(captures, limit = 2).top
        assertEquals(2, top.size)
        assertEquals(1760.0, top[0].freqKHz, 0.001)
        assertEquals(3, top[0].count)
        assertEquals(830.0, top[1].freqKHz, 0.001)
        assertEquals(30, top[1].maxLevel)
    }

    @Test
    fun `resumo vazio`() {
        val summary = CaptureStats.summarize(emptyList())
        assertEquals(0, summary.total)
        assertEquals(0, summary.distinct)
        assertEquals(0, summary.top.size)
    }

    @Test
    fun `pico pega o maior nivel e no empate o mais recente`() {
        val captures = listOf(
            Sighting(830.0, 20, 1000),
            Sighting(1760.0, 42, 2000),
            Sighting(7000.0, 42, 5000)
        )
        val peak = CaptureStats.peak(captures)
        assertEquals(7000.0, peak!!.freqKHz, 0.001)
        assertEquals(42, peak.level)
    }

    @Test
    fun `pico de lista vazia e nulo`() {
        assertNull(CaptureStats.peak(emptyList()))
    }
}
