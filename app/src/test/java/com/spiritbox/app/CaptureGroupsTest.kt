package com.spiritbox.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureGroupsTest {

    private fun s(freq: Double, level: Int, time: Long) = Sighting(freq, level, time)

    @Test
    fun `lista vazia gera lista vazia`() {
        assertEquals(0, CaptureGroups.group(emptyList()).size)
    }

    @Test
    fun `captura unica vira grupo de um`() {
        val groups = CaptureGroups.group(listOf(s(1760.0, 20, 1000)))
        assertEquals(1, groups.size)
        assertEquals(1760.0, groups[0].freqKHz, 0.0001)
        assertEquals(1, groups[0].count)
        assertEquals(20, groups[0].maxLevel)
        assertEquals(1000, groups[0].firstTime)
        assertEquals(1000, groups[0].lastTime)
    }

    @Test
    fun `mesma frequencia soma avistamentos e pega o maior nivel`() {
        val groups = CaptureGroups.group(
            listOf(s(1760.0, 10, 1000), s(1760.0, 42, 2000), s(1760.0, 30, 3000))
        )
        assertEquals(1, groups.size)
        assertEquals(3, groups[0].count)
        assertEquals(42, groups[0].maxLevel)
        assertEquals(1000, groups[0].firstTime)
        assertEquals(3000, groups[0].lastTime)
    }

    @Test
    fun `frequencias dentro da tolerancia agrupam`() {
        val groups = CaptureGroups.group(listOf(s(1760.0, 10, 1000), s(1760.5, 12, 2000)))
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].count)
    }

    @Test
    fun `frequencias fora da tolerancia nao agrupam`() {
        val groups = CaptureGroups.group(listOf(s(1760.0, 10, 1000), s(1762.0, 12, 2000)))
        assertEquals(2, groups.size)
    }

    @Test
    fun `grupos saem ordenados pela ultima captura`() {
        val groups = CaptureGroups.group(
            listOf(
                s(1000.0, 10, 5000),
                s(2000.0, 10, 1000),
                s(3000.0, 10, 9000)
            )
        )
        assertEquals(listOf(2000.0, 1000.0, 3000.0), groups.map { it.freqKHz })
    }
}
