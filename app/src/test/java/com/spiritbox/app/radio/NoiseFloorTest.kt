package com.spiritbox.app.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoiseFloorTest {

    @Test
    fun emptyFloor_isZero() {
        val f = NoiseFloor()
        assertEquals(0, f.size)
        assertEquals(0f, f.percentile(0.2), 0.0f)
    }

    @Test
    fun percentile_picksLowTail() {
        val f = NoiseFloor(capacity = 100)
        // 90 amostras de ruido baixo e 10 de sinal forte: o percentil 20 tem de
        // ficar no ruido, e nao na media.
        repeat(90) { f.add(0.02f + it * 0.0001f) }
        repeat(10) { f.add(0.8f) }
        val p20 = f.percentile(NoiseFloor.PERCENTILE)
        assertTrue("p20=$p20 saiu do ruido", p20 < 0.05f)
    }

    @Test
    fun percentile_medianSplitsDistribution() {
        val f = NoiseFloor(capacity = 100)
        for (i in 0 until 50) f.add(0.0f)
        for (i in 0 until 50) f.add(1.0f)
        assertEquals(0.0f, f.percentile(0.0), 0.001f)
        assertEquals(1.0f, f.percentile(1.0), 0.001f)
        assertEquals(0.5f, f.percentile(0.5), 0.02f)
    }

    @Test
    fun capacity_wrapsAndKeepsSize() {
        val f = NoiseFloor(capacity = 4)
        repeat(10) { f.add(it.toFloat()) }
        assertEquals(4, f.size)
        // So as 4 ultimas: 6, 7, 8, 9 -> piso = 6.
        assertEquals(6f, f.percentile(0.0), 0.001f)
        assertEquals(9f, f.percentile(1.0), 0.001f)
    }

    @Test
    fun clear_resetsEverything() {
        val f = NoiseFloor(capacity = 8)
        repeat(5) { f.add(0.5f) }
        f.clear()
        assertEquals(0, f.size)
        assertEquals(0f, f.percentile(0.5), 0.0f)
    }

    @Test
    fun percentile_outOfRange_pclamps() {
        val f = NoiseFloor(capacity = 4)
        repeat(4) { f.add(it.toFloat()) }
        assertEquals(0f, f.percentile(-3.0), 0.001f)
        assertEquals(3f, f.percentile(9.0), 0.001f)
    }

    @Test
    fun zeroCapacity_doesNotCrash() {
        val f = NoiseFloor(capacity = 0)
        f.add(0.5f)
        assertEquals(0, f.size)
        assertEquals(0f, f.percentile(0.5), 0.0f)
    }
}
