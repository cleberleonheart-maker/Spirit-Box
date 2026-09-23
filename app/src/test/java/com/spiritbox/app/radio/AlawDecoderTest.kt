package com.spiritbox.app.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class AlawDecoderTest {

    @Test
    fun decode_preservesInputLength() {
        val input = byteArrayOf(0x55, 0xAA.toByte(), 0x00, -1)
        assertEquals(input.size, AlawDecoder.decode(input).size)
    }

    @Test
    fun decode_0xff_yields848() {
        val out = AlawDecoder.decode(byteArrayOf(0xFF.toByte()))
        assertEquals(848, out[0].toInt())
    }

    @Test
    fun decode_0x00_yieldsMinus5504() {
        val out = AlawDecoder.decode(byteArrayOf(0x00))
        assertEquals(-5504, out[0].toInt())
    }

    @Test
    fun decode_emptyInput_yieldsEmptyOutput() {
        assertEquals(0, AlawDecoder.decode(ByteArray(0)).size)
    }
}