package com.spiritbox.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStatusTest {

    @Test
    fun `parado quando a varredura nao esta rodando`() {
        assertEquals(WidgetStatus.State.STOPPED, WidgetStatus.state(running = false, paused = false))
        assertEquals(WidgetStatus.State.STOPPED, WidgetStatus.state(running = false, paused = true))
    }

    @Test
    fun `pausado quando rodando e pausado`() {
        assertEquals(WidgetStatus.State.PAUSED, WidgetStatus.state(running = true, paused = true))
    }

    @Test
    fun `varrendo quando rodando e ativo`() {
        assertEquals(WidgetStatus.State.SCANNING, WidgetStatus.state(running = true, paused = false))
    }

    @Test
    fun `formata khz e mhz`() {
        assertEquals("830 kHz", WidgetStatus.formatFreq(830.0))
        assertEquals("1.760 MHz", WidgetStatus.formatFreq(1760.0))
    }
}
