package com.spiritbox.app

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Cabeçalho WAV mono 16-bit PCM, compartilhado pelos clipes e pela gravação EVP. */
internal object WavUtil {
    fun header(dataSize: Int, sampleRate: Int): ByteArray {
        val bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray())
        bb.putInt(36 + dataSize)
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(16)             // fmt chunk size
        bb.putShort(1)            // PCM
        bb.putShort(1)            // mono
        bb.putInt(sampleRate)
        bb.putInt(sampleRate * 2) // byte rate
        bb.putShort(2)            // block align
        bb.putShort(16)           // bits per sample
        bb.put("data".toByteArray())
        bb.putInt(dataSize)
        return bb.array()
    }
}
