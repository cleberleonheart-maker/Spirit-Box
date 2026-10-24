package com.spiritbox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScanPresetCodecTest {

    @Test
    fun `exporta e parseia preset completo`() {
        val p = ScanPresetCodec.Preset(
            version = 1,
            mode = "sdr",
            server = "http://kiwisdr.com:8073/",
            rangeIndex = 2,
            dwell = 300,
            settle = 150,
            gap = 300,
            threshold = 12
        )
        val json = ScanPresetCodec.export(p)
        val res = ScanPresetCodec.parse(json)
        assertNotNull(res.preset)
        assertEquals(p.mode, res.preset!!.mode)
        assertEquals(p.server, res.preset!!.server)
        assertEquals(p.rangeIndex, res.preset!!.rangeIndex)
        assertEquals(p.dwell, res.preset!!.dwell)
        assertEquals(p.settle, res.preset!!.settle)
        assertEquals(p.gap, res.preset!!.gap)
        assertEquals(p.threshold, res.preset!!.threshold)
        assertNull(res.error)
    }

    @Test
    fun `rejeita json incompleto`() {
        assertNotNull(ScanPresetCodec.parse("{\"mode\":\"sdr\"}").error)
        assertNotNull(ScanPresetCodec.parse("").error)
        assertNotNull(ScanPresetCodec.parse("[]").error)
    }

    @Test
    fun `clampa valores fora da faixa`() {
        val json = """
            {"version":1,"mode":"fm","server":"http://x","rangeIndex":-1,"dwell":10,"settle":-5,"gap":5,"threshold":200}
        """.trimIndent()
        val p = ScanPresetCodec.parse(json).preset!!
        assertEquals(0, p.rangeIndex)
        assertEquals(50L, p.dwell)
        assertEquals(0L, p.settle)
        assertEquals(50L, p.gap)
        assertEquals(100, p.threshold)
    }
}
