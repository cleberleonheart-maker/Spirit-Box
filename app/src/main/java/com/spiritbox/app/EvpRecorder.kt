package com.spiritbox.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong

/** Grava o microfone em paralelo à varredura para o modo EVP. O PCM 16-bit mono é
 * escrito num arquivo temporário; as capturas de rádio são registradas como
 * marcadores (offset em ms). No fim o serviço converte para WAV + JSON. */
class EvpRecorder(
    private val context: Context,
    private val sampleRate: Int = EvpSession.DEFAULT_SAMPLE_RATE
) {

    class Result(val pcmFile: File, val durationMs: Long, val session: EvpSession)

    private val session = EvpSession(sampleRate)
    private val sampleCount = AtomicLong(0)

    @Volatile
    private var running = false
    private var started = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null
    private var out: FileOutputStream? = null
    private var pcmFile: File? = null

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (started) return true
        if (!hasPermission()) return false
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false
        val ar = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, sampleRate)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao criar AudioRecord", e)
            return false
        }
        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            ar.release()
            return false
        }
        val file = File(context.cacheDir, "evp_${System.currentTimeMillis()}.pcm")
        val os = try {
            FileOutputStream(file)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao criar arquivo EVP", e)
            ar.release()
            return false
        }
        pcmFile = file
        out = os
        record = ar
        started = true
        running = true
        ar.startRecording()
        thread = Thread({ loop(ar, os) }, "EvpRecorder").apply {
            isDaemon = true
            start()
        }
        return true
    }

    /** Offset atual da gravação, em ms, a partir das amostras já gravadas. */
    fun elapsedMs(): Long = session.offsetForSamples(sampleCount.get())

    /** Registra um marcador de captura de rádio no instante atual. */
    fun addMarker(freqKHz: Double, level: Int) {
        if (!started) return
        session.addMarker(elapsedMs(), freqKHz, level)
    }

    /** Para a gravação. Devolve o arquivo PCM e a sessão, ou null se nada foi
     * gravado (permissão negada/sensor indisponível). */
    fun stop(): Result? {
        if (!started) return null
        running = false
        try {
            record?.stop()
        } catch (_: Exception) {
        }
        thread?.join(600)
        try {
            out?.flush()
            out?.close()
        } catch (_: Exception) {
        }
        try {
            record?.release()
        } catch (_: Exception) {
        }
        started = false
        val file = pcmFile
        val duration = session.offsetForSamples(sampleCount.get())
        pcmFile = null
        record = null
        out = null
        thread = null
        return if (file != null && file.exists() && file.length() > 0) {
            Result(file, duration, session)
        } else {
            file?.delete()
            null
        }
    }

    private fun loop(ar: AudioRecord, os: FileOutputStream) {
        val buf = ShortArray(1024)
        val bytes = ByteArray(buf.size * 2)
        try {
            while (running) {
                val n = ar.read(buf, 0, buf.size)
                if (n < 0) break
                if (n == 0) continue
                var bi = 0
                for (i in 0 until n) {
                    val v = buf[i].toInt()
                    bytes[bi++] = (v and 0xFF).toByte()
                    bytes[bi++] = ((v shr 8) and 0xFF).toByte()
                }
                os.write(bytes, 0, bi)
                sampleCount.addAndGet(n.toLong())
            }
            os.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Falha na gravação EVP", e)
        }
    }

    companion object {
        private const val TAG = "SpiritBox"
    }
}
