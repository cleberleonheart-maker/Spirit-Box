package com.spiritbox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchListTest {

    @Test
    fun `contains casa exatamente a frequencia marcada`() {
        assertTrue(WatchList.contains(listOf(1760.0), 1760.0))
    }

    @Test
    fun `contains casa dentro da tolerancia`() {
        assertTrue(WatchList.contains(listOf(1760.0), 1760.5))
        assertTrue(WatchList.contains(listOf(1760.0), 1759.5))
    }

    @Test
    fun `contains rejeita fora da tolerancia`() {
        assertFalse(WatchList.contains(listOf(1760.0), 1762.0))
    }

    @Test
    fun `lista vazia nunca casa`() {
        assertFalse(WatchList.contains(emptyList(), 1000.0))
    }

    @Test
    fun `casa se qualquer frequencia da lista bater`() {
        assertTrue(WatchList.contains(listOf(600.0, 1760.0, 7000.0), 7000.0))
    }

    @Test
    fun `tolerancia customizada e' respeitada`() {
        assertTrue(WatchList.contains(listOf(1760.0), 1765.0, toleranceKHz = 5.0))
        assertFalse(WatchList.contains(listOf(1760.0), 1765.0, toleranceKHz = 1.0))
    }

    @Test
    fun `label usa kHz abaixo de 1 MHz`() {
        assertEquals("760 kHz", WatchList.label(760.0))
    }

    @Test
    fun `label usa MHz a partir de 1 MHz`() {
        assertEquals("1.760 MHz", WatchList.label(1760.0))
    }
}
