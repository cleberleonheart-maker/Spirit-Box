package com.spiritbox.app.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSdrClientTest {

    @Test
    fun buildTuneCommand_am() {
        val cmd = WebSdrClient.buildTuneCommand(520.0, 0, -4.5, 4.5, 1)
        assertEquals("GET /~~param?f=520&band=0&lo=-4.5&hi=4.5&mode=1&name=", cmd)
    }

    @Test
    fun buildTuneCommand_trimTrailingZeros() {
        assertEquals(
            "GET /~~param?f=87&band=1&lo=-4.5&hi=4.5&mode=2&name=",
            WebSdrClient.buildTuneCommand(87.000, 1, -4.5, 4.5, 2)
        )
    }

    @Test
    fun buildTuneCommand_keepRelevantDecimals() {
        assertEquals(
            "GET /~~param?f=118037.5&band=1&lo=-4.5&hi=4.5&mode=0&name=",
            WebSdrClient.buildTuneCommand(118037.500, 1, -4.5, 4.5, 0)
        )
    }

    @Test
    fun buildTuneCommand_largeFrequency() {
        val cmd = WebSdrClient.buildTuneCommand(108000.0, 1, -4.5, 4.5, 2)
        assertTrue(cmd.startsWith("GET /~~param?f=108000&"))
        assertFalse(cmd.contains(".0&band"))
    }

    @Test
    fun buildTuneCommand_alwaysHasMandatoryFields() {
        val cmd = WebSdrClient.buildTuneCommand(9500.0, 1, -4.5, 4.5, 1)
        assertTrue(cmd.contains("&band="))
        assertTrue(cmd.contains("&lo="))
        assertTrue(cmd.contains("&hi="))
        assertTrue(cmd.contains("&mode="))
        assertTrue(cmd.endsWith("&name="))
    }
}