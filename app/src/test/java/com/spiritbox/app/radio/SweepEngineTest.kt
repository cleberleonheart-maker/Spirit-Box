package com.spiritbox.app.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SweepEngineTest {

    @Test
    fun nextFreq_advancesByStep() {
        assertEquals(525.0, SweepEngine.nextFreq(520.0, 5), 0.0)
        assertEquals(30000.0, SweepEngine.nextFreq(29995.0, 5), 0.0)
        assertEquals(137000.0, SweepEngine.nextFreq(136975.0, 25), 0.0)
    }

    @Test
    fun isWithinRange_inclusiveUpperBound() {
        assertTrue(SweepEngine.isWithinRange(1700.0, 1700))
        assertTrue(SweepEngine.isWithinRange(137000.0, 137000))
        assertTrue(SweepEngine.isWithinRange(520.0, 1700))
    }

    @Test
    fun isWithinRange_excludesBeyondUpperBound() {
        assertFalse(SweepEngine.isWithinRange(1705.0, 1700))
        assertFalse(SweepEngine.isWithinRange(137025.0, 137000))
    }

    @Test
    fun presets_haveValidRanges() {
        for (p in SweepEngine.PRESETS) {
            assertTrue("inicio menor que fim: ${p.name}", p.startKHz < p.endKHz)
            assertTrue(p.stepKHz > 0)
        }
    }

    @Test
    fun amPreset_coversExpectedFrequency() {
        val am = SweepEngine.PRESETS[0]
        assertEquals(520, am.startKHz)
        assertEquals(1700, am.endKHz)
        assertEquals(5, am.stepKHz)
    }

    @Test
    fun nextStreak_consecutivePassesInsideWindow_accumulate() {
        var passes = 0
        var last = -1L
        var t = 0L
        repeat(3) {
            passes = SweepEngine.nextStreak(passes, last, t)
            last = t
            t += 10_000
        }
        assertEquals(3, passes)
    }

    @Test
    fun nextStreak_passOlderThanWindow_restartsAtOne() {
        // Pico de 30 min atras nao pode somar com o de agora: sem isto, exigir 3
        // passagens virava "3 vezes em qualquer momento da sessao".
        val passes = SweepEngine.nextStreak(2, 0L, 30 * 60_000L)
        assertEquals(1, passes)
    }

    @Test
    fun nextStreak_firstPass_isOne() {
        assertEquals(1, SweepEngine.nextStreak(0, -1L, 5_000L))
    }

    @Test
    fun shouldCapture_needsPassesLevelAndCooldown() {
        val now = 600_000L
        assertTrue(SweepEngine.shouldCapture(3, 3, 0.5, 0.12, now, 0L))
        assertFalse("passagens insuficientes", SweepEngine.shouldCapture(2, 3, 0.5, 0.12, now, 0L))
        assertFalse("abaixo do limiar", SweepEngine.shouldCapture(3, 3, 0.05, 0.12, now, 0L))
        assertFalse(
            "dentro da janela de repeticao",
            SweepEngine.shouldCapture(3, 3, 0.5, 0.12, now, now - 30_000L)
        )
    }
}
