package com.spiritbox.app

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Buffer circular dos últimos [seconds] de PCM 16-bit mono (11025 Hz);
 * alimentado pelo stream decodificado do WebSDR. Usado para salvar
 * clipes ao detectar uma captura.
 */
class AudioClipRecorder(private val seconds: Int) {

    private val capacity = SAMPLE_RATE * seconds
    private val buffer = ShortArray(capacity)
    private var writePos = 0
    private var filled = 0

    @Synchronized
    fun add(samples: ShortArray) {
        if (samples.isEmpty()) return
        for (s in samples) {
            buffer[writePos] = s
            writePos = (writePos + 1) % capacity
            if (filled < capacity) filled++
        }
    }

    @Synchronized
    fun hasData(): Boolean = filled > 0

    /** Retorna o conteúdo em ordem cronológica (até [capacity] amostras). */
    @Synchronized
    fun snapshot(): ShortArray {
        if (filled == 0) return ShortArray(0)
        val out = ShortArray(filled)
        val start = if (filled < capacity) 0 else writePos
        for (i in 0 until filled) {
            out[i] = buffer[(start + i) % capacity]
        }
        return out
    }

    /** Escreve [samples] como WAV mono 16-bit ([sampleRate]) em [out]. */
    fun writeWav(out: OutputStream, sampleRate: Int, samples: ShortArray) {
        val dataSize = samples.size * 2
        out.write(WavUtil.header(dataSize, sampleRate))
        val bb = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) bb.putShort(s)
        out.write(bb.array())
        out.flush()
    }

    companion object {
        const val SAMPLE_RATE = 11025
    }
}