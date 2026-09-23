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
}