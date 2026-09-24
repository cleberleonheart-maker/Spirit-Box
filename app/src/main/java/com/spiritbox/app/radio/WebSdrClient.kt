package com.spiritbox.app.radio

import android.content.Context
import android.media.AudioTrack
import android.util.Log
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketFrame
import com.spiritbox.app.R
import java.net.URI
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

class WebSdrClient(
    private val context: Context,
    private val audioTrack: AudioTrack,
    private val onStatus: (String) -> Unit,
    private val onRms: (Double) -> Unit,
    private val pcmSink: (ShortArray) -> Unit = {}
) {
    @Volatile
    private var ws: WebSocket? = null
    private val id = (0 until 12).joinToString("") { ((0..15).random()).toString(16) }

    @Volatile
    var connected = false
        private set

    private val closed = AtomicBoolean(false)
    private val reconnecting = AtomicBoolean(false)
    private val servers = mutableListOf<String>()
    private var serverIndex = 0
    private val retries = AtomicInteger(0)
    private val lastDataMs = AtomicLong(System.currentTimeMillis())

    private var audioWriter: AudioWriterThread? = null

    fun start(serverList: List<String>) {
        synchronized(servers) {
            servers.clear()
            servers.addAll(serverList.map { it.trim() }.filter { it.isNotEmpty() })
            if (servers.isEmpty()) servers.add(DEFAULT_SERVER)
            serverIndex = 0
        }
        retries.set(0)
        reconnecting.set(false)
        closed.set(false)
        audioWriter?.stop()
        audioWriter = AudioWriterThread(audioTrack).also { it.start() }
        Thread {
            while (!closed.get()) {
                perhapsForceReconnect()
                try {
                    Thread.sleep(WATCHDOG_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.apply { isDaemon = true }.start()
        Thread {
            val host = synchronized(servers) {
                servers.getOrNull(serverIndex) ?: servers.firstOrNull()
            }
            connectTo(host ?: return@Thread)
        }.apply { isDaemon = true }.start()
    }

    private fun connectTo(hostPort: String) {
        if (closed.get()) return
        val secure = hostPort.endsWith(":443")
        val scheme = if (secure) "wss" else "ws"
        val attempt = (retries.get() + 1).coerceAtMost(99)
        onStatus(context.getString(R.string.ws_connecting, hostPort, attempt))
        val socket = try {
            WebSocketFactory()
                .setConnectionTimeout(10000)
                .createSocket(URI("$scheme://$hostPort/~~stream"))
        } catch (e: Exception) {
            Log.w(TAG, "Endereço inválido: $hostPort", e)
            onStatus(context.getString(R.string.ws_invalid_address, hostPort))
            scheduleReconnect()
            return
        }
        socket.addHeader("Accept", "*/*")
        socket.addHeader("Accept-Encoding", "gzip, deflate")
        socket.addHeader("Cache-Control", "no-cache")
        socket.addHeader("Connection", "keep-alive, Upgrade")
        socket.addHeader("Cookie", "ID=$id; view=2; usejava=nn")
        socket.addHeader("Host", hostPort)
        socket.addHeader("Origin", if (secure) "https://$hostPort" else "http://$hostPort")
        socket.addHeader("Pragma", "no-cache")
        socket.addHeader("Sec-WebSocket-Version", "13")
        socket.addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0 Safari/605.1.15")
        socket.addListener(object : WebSocketAdapter() {
            override fun onConnected(
                websocket: WebSocket?,
                headers: MutableMap<String, MutableList<String>>?
            ) {
                if (ws !== socket || closed.get()) return
                connected = true
                retries.set(0)
                lastDataMs.set(System.currentTimeMillis())
                synchronized(servers) {
                    serverIndex = servers.indexOf(hostPort).coerceAtLeast(0)
                }
                onStatus(context.getString(R.string.ws_connected, hostPort))
                audioTrack.play()
                sendAudioParams()
            }

            override fun onBinaryMessage(websocket: WebSocket?, bytes: ByteArray) {
                if (ws !== socket || closed.get()) return
                val samples = AlawDecoder.decode(bytes)
                lastDataMs.set(System.currentTimeMillis())
                onRms(rms(samples))
                pcmSink(samples)
                audioWriter?.offer(samples)
            }

            override fun onTextMessage(websocket: WebSocket?, message: String) {
                if (ws !== socket || closed.get()) return
                lastDataMs.set(System.currentTimeMillis())
                val m = message.trim()
                if (m.isNotEmpty() && m != "*") onStatus(m.take(90))
            }

            override fun onDisconnected(
                websocket: WebSocket?,
                serverCloseFrame: WebSocketFrame?,
                clientCloseFrame: WebSocketFrame?,
                closedByServer: Boolean
            ) {
                if (ws !== socket) return
                connected = false
                audioTrack.pause()
                onStatus(context.getString(
                    R.string.ws_closed,
                    serverCloseFrame?.closeReason ?: clientCloseFrame?.closeReason ?: "fim"
                ))
                scheduleReconnect()
            }

            override fun onConnectError(websocket: WebSocket?, exception: WebSocketException?) {
                if (ws !== socket || closed.get()) return
                connected = false
                audioTrack.pause()
                onStatus(context.getString(R.string.ws_connect_error, exception?.message ?: ""))
                scheduleReconnect()
            }

            override fun onError(websocket: WebSocket?, exception: WebSocketException?) {
                if (ws !== socket || closed.get()) return
                onStatus(context.getString(R.string.ws_error, exception?.message ?: ""))
            }
        })
        ws = socket
        try {
            socket.connect()
        } catch (e: Exception) {
            // onConnectError/onDisconnected handles the retry path
        }
    }

    private fun scheduleReconnect() {
        if (closed.get()) return
        if (!reconnecting.compareAndSet(false, true)) return
        val delay = (1000L * (retries.get() + 1)).coerceAtMost(10000L)
        retries.incrementAndGet()
        Thread {
            Thread.sleep(delay)
            if (closed.get()) return@Thread
            reconnecting.set(false)
            val host = synchronized(servers) {
                if (servers.isEmpty()) {
                    null
                } else {
                    serverIndex = (serverIndex + 1) % servers.size
                    servers[serverIndex]
                }
            }
            connectTo(host ?: return@Thread)
        }.apply { isDaemon = true }.start()
    }

    private fun perhapsForceReconnect() {
        if (closed.get() || !connected) return
        val idle = System.currentTimeMillis() - lastDataMs.get()
        if (idle > IDLE_TIMEOUT_MS) {
            onStatus(context.getString(R.string.ws_no_data, idle / 1000))
            connected = false
            val s = ws
            ws = null
            try {
                s?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao desconectar (watchdog)", e)
            }
            scheduleReconnect()
        }
    }

    fun sendTune(freqKHz: Double, band: Int, lo: Double, hi: Double, mode: Int) {
        val s = ws ?: return
        if (!connected) return
        try {
            s.sendText(buildTuneCommand(freqKHz, band, lo, hi, mode))
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao enviar sintonia", e)
            connected = false
            scheduleReconnect()
        }
    }

    fun sendAudioParams() {
        val s = ws ?: return
        if (!connected) return
        try {
            s.sendText("GET /~~param?gain=10000")
            s.sendText("GET /~~param?agchang=0")
            s.sendText("GET /~~param?squelch=0")
            sendNoiseFilter()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao enviar parâmetros de áudio", e)
            connected = false
            scheduleReconnect()
        }
    }

    @Volatile
    var noiseReduction: Boolean = false

    fun applyNoiseFilter(enabled: Boolean) {
        noiseReduction = enabled
        val s = ws ?: return
        if (!connected) return
        try {
            sendNoiseFilter()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao enviar filtro de ruído", e)
            connected = false
            scheduleReconnect()
        }
    }

    private fun sendNoiseFilter() {
        val s = ws
        if (s == null || !connected) return
        val v = if (noiseReduction) 1 else 0
        s.sendText("GET /~~param?autonotch=$v")
        s.sendText("GET /~~param?noisered=$v")
    }

    fun close() {
        closed.set(true)
        audioWriter?.stop()
        audioWriter = null
        val s = ws
        ws = null
        try {
            s?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao desconectar (close)", e)
        }
        connected = false
    }

    companion object {
        const val DEFAULT_SERVER = "websdr.ewi.utwente.nl:8901"
        private const val TAG = "SpiritBox"
        private const val JOIN_TIMEOUT_MS = 1000L
        private const val IDLE_TIMEOUT_MS = 30000L
        private const val WATCHDOG_INTERVAL_MS = 5000L

        fun buildTuneCommand(
            freqKHz: Double,
            band: Int,
            lo: Double,
            hi: Double,
            mode: Int
        ): String {
            val f = String.format(Locale.US, "%.3f", freqKHz)
                .trimEnd('0').trimEnd('.')
            return "GET /~~param?f=$f&band=$band&lo=$lo&hi=$hi&mode=$mode&name="
        }
    }

    private class AudioWriterThread(private val track: AudioTrack) {
        private val queue: BlockingQueue<ShortArray> = ArrayBlockingQueue(8)
        private val running = AtomicBoolean(true)
        private var thread: Thread? = null

        fun start() {
            thread = Thread {
                try {
                    while (running.get()) {
                        val samples = try {
                            queue.poll(200, TimeUnit.MILLISECONDS)
                        } catch (_: InterruptedException) {
                            null
                        } ?: continue
                        try {
                            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                        } catch (e: Exception) {
                            Log.w(TAG, "Falha na escrita de áudio", e)
                        }
                    }
                } finally {
                    releaseTrackFromWriter()
                }
            }.apply { isDaemon = true }
            thread?.start()
        }

        fun offer(samples: ShortArray) {
            if (!queue.offer(samples)) {
                queue.poll()
                queue.offer(samples)
            }
        }

        fun stop() {
            running.set(false)
            val t = thread ?: return
            t.interrupt()
            queue.clear()
            try {
                track.flush()
            } catch (_: Exception) {
            }
            try {
                t.join(WebSdrClient.JOIN_TIMEOUT_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            thread = null
        }

        private fun releaseTrackFromWriter() {
            try {
                track.pause()
            } catch (_: Exception) {
            }
            try {
                track.flush()
            } catch (_: Exception) {
            }
            try {
                track.stop()
            } catch (_: Exception) {
            }
            try {
                track.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun rms(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (s in samples) {
            val v = s / 32768.0
            sum += v * v
        }
        return sqrt(sum / samples.size)
    }
}