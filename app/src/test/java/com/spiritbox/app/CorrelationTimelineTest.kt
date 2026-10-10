package com.spiritbox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrelationTimelineTest {

    @Test
    fun `excursao unica vira um pico no maior valor`() {
        val t = CorrelationTimeline()
        t.addEmf(0, 2f)
        t.addEmf(1000, 18f)
        t.addEmf(2000, 30f)
        t.addEmf(3000, 12f)
        t.addEmf(4000, 3f)
        val peaks = t.peaks(thresholdMg = 15f)
        assertEquals(1, peaks.size)
        assertEquals(2000L, peaks[0].t)
        assertEquals(30f, peaks[0].mg, 0.001f)
    }

    @Test
    fun `pico ainda acima do limiar no fim e' emitido`() {
        val t = CorrelationTimeline()
        t.addEmf(0, 5f)
        t.addEmf(1000, 20f)
        t.addEmf(2000, 25f)
        val peaks = t.peaks(thresholdMg = 15f)
        assertEquals(1, peaks.size)
        assertEquals(2000L, peaks[0].t)
    }

    @Test
    fun `duas excursões separadas viram dois picos`() {
        val t = CorrelationTimeline()
        t.addEmf(0, 20f)
        t.addEmf(1000, 2f)
        t.addEmf(2000, 22f)
        val peaks = t.peaks(thresholdMg = 15f)
        assertEquals(2, peaks.size)
    }

    @Test
    fun `captura dentro da janela vira evento combinado`() {
        val t = CorrelationTimeline()
        t.addEmf(10_000, 20f)
        t.addRadio(12_000, 1760.0, 40)
        val combined = t.combined(windowMs = 5000L, thresholdMg = 15f)
        assertEquals(1, combined.size)
        assertEquals(1760.0, combined[0].radio.freqKHz, 0.001)
        assertEquals(10_000L, combined[0].peak.t)
    }

    @Test
    fun `captura fora da janela nao combina`() {
        val t = CorrelationTimeline()
        t.addEmf(10_000, 20f)
        t.addRadio(20_000, 1760.0, 40)
        assertTrue(t.combined(windowMs = 5000L, thresholdMg = 15f).isEmpty())
    }

    @Test
    fun `escolhe o pico mais proximo no tempo`() {
        val t = CorrelationTimeline()
        t.addEmf(10_000, 20f)
        t.addEmf(10_400, 22f)
        t.addRadio(10_500, 1760.0, 40)
        val c = t.combined(windowMs = 5000L, thresholdMg = 15f)
        // As duas excursões estão no mesmo trecho? Não: o segundo sample segue acima
        // do limiar, então é a mesma excursão e o pico é o maior (10_400).
        assertEquals(1, c.size)
        assertEquals(10_400L, c[0].peak.t)
    }

    @Test
    fun `clear zera tudo`() {
        val t = CorrelationTimeline()
        t.addEmf(1000, 20f)
        t.addRadio(1000, 1000.0, 10)
        t.clear()
        assertTrue(t.emfSamples().isEmpty())
        assertTrue(t.radioEvents().isEmpty())
        assertTrue(t.peaks().isEmpty())
    }

    @Test
    fun `capacidade limita o historico`() {
        val t = CorrelationTimeline(capacity = 3)
        repeat(10) { t.addEmf(it.toLong(), 1f) }
        assertEquals(3, t.emfSamples().size)
        assertEquals(9L, t.emfSamples().last().t)
    }
}
