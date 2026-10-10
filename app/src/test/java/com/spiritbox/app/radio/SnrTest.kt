package com.spiritbox.app.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class SnrTest {

    @Test
    fun `dez vezes o piso da 20 dB`() {
        assertEquals(20, Snr.db(1.0, 0.1))
    }

    @Test
    fun `cem vezes o piso da 40 dB`() {
        assertEquals(40, Snr.db(1.0, 0.01))
    }

    @Test
    fun `tres vezes o piso da cerca de 9 dB`() {
        assertEquals(9, Snr.db(0.3, 0.1))
    }

    @Test
    fun `igual ao piso da 0 dB`() {
        assertEquals(0, Snr.db(0.1, 0.1))
    }

    @Test
    fun `abaixo do piso fica em 0 dB`() {
        assertEquals(0, Snr.db(0.05, 0.1))
    }

    @Test
    fun `pico zero da 0 dB`() {
        assertEquals(0, Snr.db(0.0, 0.1))
    }

    @Test
    fun `referencia zero da 0 dB`() {
        assertEquals(0, Snr.db(0.5, 0.0))
    }

    @Test
    fun `valores extremos sao presos ao maximo`() {
        assertEquals(Snr.MAX_DB, Snr.db(1.0, 1e-9))
    }
}
