package com.spiritbox.app

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioClipRecorderTest {

    @Test
    fun snapshot_returnsChronologicalOrder_afterWrap() {
        val rec = AudioClipRecorder(seconds = 1)
        val chunk = ShortArray(AudioClipRecorder.SAMPLE_RATE - 1000)
        chunk.fill(1)
        rec.add(chunk)
        val tail = ShortArray(2000)
        tail.fill(2)
        rec.add(tail)
        assertEquals(AudioClipRecorder.SAMPLE_RATE, rec.snapshot().size)
        val snap = rec.snapshot()
        assertEquals(1, snap[0].toInt())
        assertEquals(2, snap[snap.size - 1].toInt())
    }

    @Test
    fun snapshot_beforeWrap_startsAtZero() {
        val rec = AudioClipRecorder(seconds = 8)
        val chunk = ShortArray(1000)
        chunk.fill(7)
        rec.add(chunk)
        val snap = rec.snapshot()
        assertEquals(1000, snap.size)
        assertTrue(snap.all { it == 7.toShort() })
    }

    @Test
    fun writeWav_writesValidHeaderAndPcm() {
        val rec = AudioClipRecorder(seconds = 1)
        val samples = ShortArray(4) { (it * 1000).toShort() }
        val out = ByteArrayOutputStream()
        rec.writeWav(out, AudioClipRecorder.SAMPLE_RATE, samples)
        val bytes = out.toByteArray()
        assertEquals(44 + 8, bytes.size) // header + 4 shorts
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals("fmt ", String(bytes, 12, 4))
        assertEquals("data", String(bytes, 36, 4))
        assertEquals(1, bytes[20].toInt())                  // PCM
        assertEquals(1, bytes[22].toInt())                  // mono
        assertEquals(16, bytes[34].toInt())                 // bits (LE)
        assertEquals(8, bytes[40].toInt())                  // data size (LE low byte)
        assertEquals(0, bytes[41].toInt())                  // data size (LE)
        assertEquals(0, bytes[44].toInt() and 0xff)     // first sample = 0
        assertEquals(0, bytes[45].toInt() and 0xff)     // 1st sample high byte
        assertEquals(0xe8, bytes[46].toInt() and 0xff)  // 1000 = 0x03e8 -> LE
        assertEquals(3, bytes[47].toInt() and 0xff)
    }

    @Test
    fun hasData_onlyAfterFirstAdd() {
        val rec = AudioClipRecorder(seconds = 1)
        assertFalse(rec.hasData())
        rec.add(ShortArray(1))
        assertTrue(rec.hasData())
    }
}