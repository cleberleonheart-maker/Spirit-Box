package com.spiritbox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterfallViewTest {

    @Test
    fun axisStep_usesRoundNumbers() {
        // AM: 520-1700 kHz, 4 marcas -> passo de 500, nao 295.
        assertEquals(500.0, WaterfallView.axisStepKHz(1180.0), 0.001)
        // SW: 1710-30000 -> passo de 10000.
        assertEquals(10000.0, WaterfallView.axisStepKHz(28290.0), 0.001)
        // Militar: 225000-400000 -> passo de 50000 (4 marcas: 250/300/350/400 MHz).
        assertEquals(50000.0, WaterfallView.axisStepKHz(175000.0), 0.001)
    }

    @Test
    fun axisStep_degenerateSpan_isZero() {
        assertEquals(0.0, WaterfallView.axisStepKHz(0.0), 0.0)
        assertEquals(0.0, WaterfallView.axisStepKHz(-5.0), 0.0)
    }

    @Test
    fun axisTicks_stayInsideBand() {
        val ticks = WaterfallView.axisTicks(520.0, 1700.0)
        assertTrue("nenhuma marca", ticks.isNotEmpty())
        for (t in ticks) {
            assertTrue("marca abaixo da faixa: $t", t >= 520.0)
            assertTrue("marca acima da faixa: $t", t <= 1700.0)
        }
    }

    @Test
    fun axisTicks_militaryBand_areInMHz() {
        // 225000 nao cai em multiplo de 50000, entao a primeira marca e' 250 MHz.
        val ticks = WaterfallView.axisTicks(225000.0, 400000.0)
        assertEquals(250000.0, ticks[0], 0.001)
        assertEquals(400000.0, ticks[ticks.size - 1], 0.001)
    }

    @Test
    fun formatKHz_switchesToMHzAbove1000() {
        assertEquals("520 kHz", WaterfallView.formatKHz(520.0))
        assertEquals("999 kHz", WaterfallView.formatKHz(999.0))
        assertEquals("1,7 MHz", WaterfallView.formatKHz(1710.0))
        assertEquals("30,0 MHz", WaterfallView.formatKHz(30000.0))
        assertEquals("108 MHz", WaterfallView.formatKHz(108000.0))
        assertEquals("400 MHz", WaterfallView.formatKHz(400000.0))
    }

    @Test
    fun frequencyAt_snapsToBandStep() {
        // Sem o arredondamento o toque cairia entre duas frequencias e a varredura
        // travaria num valor que ela nunca visita.
        assertEquals(1000.0, WaterfallView.frequencyAt(520.0, 1700.0, 5.0, 0.407), 0.001)
        // FM: passo de 200 kHz, 87420 arredonda para 87400 (e nao 87500, que exigiria
        // arredondar para o multiplo mais proximo -- 87400 esta a 20, 87600 a 180).
        assertEquals(87400.0, WaterfallView.frequencyAt(87000.0, 108000.0, 200.0, 0.02), 0.001)
    }

    @Test
    fun frequencyAt_clampsToBandEdges() {
        assertEquals(520.0, WaterfallView.frequencyAt(520.0, 1700.0, 5.0, -0.5), 0.001)
        assertEquals(1700.0, WaterfallView.frequencyAt(520.0, 1700.0, 5.0, 1.5), 0.001)
    }

    @Test
    fun frequencyAt_withoutStep_isContinuous() {
        val f = WaterfallView.frequencyAt(520.0, 1700.0, 0.0, 0.5)
        assertEquals(1110.0, f, 0.001)
    }

    @Test
    fun apparentLevel_modulatedSignalReadsBrighterThanSteadyCarrier() {
        val carrier = WaterfallView.apparentLevel(0.4f, 0.38f)
        val voice = WaterfallView.apparentLevel(0.4f, 0.05f)
        assertTrue("voz $voice deve ser mais clara que a portadora $carrier", voice > carrier)
    }

    @Test
    fun apparentLevel_silence_isZero() {
        assertEquals(0f, WaterfallView.apparentLevel(0f, 0f), 0.0f)
        assertEquals(0f, WaterfallView.apparentLevel(0f, Float.MAX_VALUE), 0.0f)
    }
}
