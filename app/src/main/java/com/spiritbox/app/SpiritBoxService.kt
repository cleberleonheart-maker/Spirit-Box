package com.spiritbox.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Log
import com.spiritbox.app.radio.FmSweepTuner
import com.spiritbox.app.radio.FmTuner
import com.spiritbox.app.radio.SweepEngine
import com.spiritbox.app.radio.WebSdrClient
import com.spiritbox.app.radio.WebSdrSweepTuner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SpiritBoxService : Service() {

    companion object {
        const val MODE_SDR = "sdr"
        const val MODE_FM = "fm"
        const val DEFAULT_SERVER = "websdr.ewi.utwente.nl:8901"

        private const val CHANNEL_ID = "spiritbox_scan"
        private const val NOTIF_ID = 1
        private const val TAG = "SpiritBox"
        private const val ACTION_START = "com.spiritbox.app.START"
        private const val ACTION_STOP = "com.spiritbox.app.STOP"
        private const val ACTION_CAPTURE = "com.spiritbox.app.CAPTURE"
        private const val ACTION_HOLD = "com.spiritbox.app.HOLD"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_SERVER = "server"
        private const val EXTRA_RANGE_INDEX = "rangeIndex"
        private const val EXTRA_DWELL = "dwell"
        private const val EXTRA_SETTLE = "settle"
        private const val EXTRA_THRESHOLD = "threshold"
        private const val EXTRA_GAP = "gap"
        private const val AUDIO_RATE = 11025
        private const val WAKELOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L
        private const val NOTIF_UPDATE_INTERVAL_MS = 400L

        private val FALLBACK_SERVERS = listOf(
            "websdr.ewi.utwente.nl:8901",
            "sdr.oh2gcs.fi:443",
            "remote.weixun-ham.com:8801"
        )

        fun start(
            context: Context,
            mode: String,
            server: String,
            range: SweepEngine.BandRange = SweepEngine.PRESETS[0],
            dwellMs: Long = 300L,
            settleMs: Long = 150L,
            thresholdPct: Int = 12,
            gapMs: Long = 300L
        ) {
            val i = Intent(context, SpiritBoxService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_SERVER, server)
                putExtra(EXTRA_RANGE_INDEX, SweepEngine.PRESETS.indexOf(range).coerceAtLeast(0))
                putExtra(EXTRA_DWELL, dwellMs)
                putExtra(EXTRA_SETTLE, settleMs)
                putExtra(EXTRA_THRESHOLD, thresholdPct)
                putExtra(EXTRA_GAP, gapMs)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun saveManualCapture(context: Context) {
            context.startService(
                Intent(context, SpiritBoxService::class.java).apply {
                    action = ACTION_CAPTURE
                }
            )
        }

        fun toggleHold(context: Context) {
            context.startService(
                Intent(context, SpiritBoxService::class.java).apply {
                    action = ACTION_HOLD
                }
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, SpiritBoxService::class.java).apply {
                    action = ACTION_STOP
                }
            )
        }
    }

    private var engine: SweepEngine? = null
    private var activeMode: String = MODE_SDR
    private var activeServer: String = DEFAULT_SERVER
    private var activeBand: String = ""

    @Volatile
    private var audioTrack: AudioTrack? = null
    private var sdrTuner: WebSdrSweepTuner? = null
    private var client: WebSdrClient? = null
    private var fm: FmTuner? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var captureLog: File? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var lastFreq: Double = 0.0

    @Volatile
    private var lastRms: Double = 0.0

    private var lastNotifUpdateMs = 0L

    private val eventsListener = object : SpiritBoxEvents.Listener {
        override fun onFreq(freqKHz: Double) {
            lastFreq = freqKHz
            updateNotification()
        }

        override fun onRms(rms: Double) {
            lastRms = rms
        }
    }

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { state ->
        when (state) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                try {
                    audioTrack?.pause()
                } catch (_: Exception) {
                }
            }
            AudioManager.AUDIOFOCUS_GAIN, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> {
                try {
                    audioTrack?.play()
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        captureLog = File(filesDir, "capturas.csv")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_SDR
                val server = intent.getStringExtra(EXTRA_SERVER) ?: DEFAULT_SERVER
                val rangeIndex = intent.getIntExtra(EXTRA_RANGE_INDEX, 0)
                val dwell = intent.getLongExtra(EXTRA_DWELL, 300L)
                val settle = intent.getLongExtra(EXTRA_SETTLE, 150L)
                val threshold = intent.getIntExtra(EXTRA_THRESHOLD, 12)
                val gap = intent.getLongExtra(EXTRA_GAP, 300L)
                Prefs.saveActiveScan(
                    this,
                    Prefs.ScanState(mode, server, rangeIndex, dwell, threshold, settle, gap)
                )
                val notif = buildNotification(getString(R.string.notif_text_scanning))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIF_ID, notif)
                }
                startScan(mode, server, rangeIndex, dwell, settle, threshold, gap)
            }
            ACTION_CAPTURE -> {
                if (engine == null) {
                    SpiritBoxEvents.pushStatus(getString(R.string.scan_not_running))
                    stopSelf()
                } else {
                    val freq = lastFreq
                    val level = (lastRms * 100).toInt().coerceIn(0, 100)
                    if (freq <= 0) {
                        SpiritBoxEvents.pushStatus(getString(R.string.scan_waiting_freq))
                    } else {
                        logCapture(freq, level)
                        SpiritBoxEvents.pushCapture(freq, level)
                        SpiritBoxEvents.pushStatus(getString(R.string.manual_capture_saved))
                    }
                }
            }
            ACTION_HOLD -> {
                val e = engine
                if (e == null) {
                    SpiritBoxEvents.pushStatus(getString(R.string.scan_not_running))
                    stopSelf()
                } else {
                    val h = !e.hold
                    e.hold = h
                    SpiritBoxEvents.holdActive = h
                    SpiritBoxEvents.pushHoldChanged(h)
                    SpiritBoxEvents.pushStatus(
                        getString(if (h) R.string.hold_engaged else R.string.hold_released)
                    )
                }
            }
            ACTION_STOP -> {
                Prefs.clearActiveScan(this)
                SpiritBoxEvents.holdActive = false
                stopScan()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
            null -> {
                val saved = Prefs.activeScan(this)
                if (saved != null && engine == null) {
                    val notif = buildNotification(getString(R.string.notif_text_scanning))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                    } else {
                        startForeground(NOTIF_ID, notif)
                    }
                    SpiritBoxEvents.pushStatus(getString(R.string.service_resumed))
                    startScan(saved.mode, saved.server, saved.rangeIndex, saved.dwell, saved.settle, saved.thresholdPct, saved.gap)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopScan()
        super.onDestroy()
    }

    private fun startScan(mode: String, server: String, rangeIndex: Int, dwell: Long, settle: Long, threshold: Int, gap: Long) {
        if (engine != null) return
        lastFreq = 0.0
        lastRms = 0.0
        lastNotifUpdateMs = 0L
        activeMode = mode
        activeServer = server
        val range = SweepEngine.PRESETS.getOrNull(rangeIndex) ?: SweepEngine.PRESETS[0]
        activeBand = getString(SweepEngine.bandId(range))
        SpiritBoxEvents.serviceRunning = true
        SpiritBoxEvents.addListener(eventsListener)
        SpiritBoxEvents.pushStatus(getString(R.string.status_starting))
        acquireWakeLock()
        requestAudioFocus()

        if (mode == MODE_FM) {
            val f = FmTuner(this, { SpiritBoxEvents.pushStatus(it) }) { available ->
                SpiritBoxEvents.pushStatus(
                if (available) getString(R.string.status_fm_available)
                else getString(R.string.status_fm_unavailable)
            )
            }
            fm = f
            engine = SweepEngine(this, FmSweepTuner(f)) { freq, level -> logCapture(freq, level) }.apply {
                configureRange(range)
                setDwell(dwell)
                setSettle(settle)
                setSweepGap(gap)
                captureThreshold = (threshold / 100.0).coerceIn(0.0, 1.0)
            }
            engine?.start()
        } else {
            val at = createAudioTrack()
            audioTrack = at
            val c = WebSdrClient(
                this,
                at,
                { SpiritBoxEvents.pushStatus(it) },
                { rms ->
                    sdrTuner?.setRms(rms)
                    SpiritBoxEvents.pushRms(rms)
                },
                onGiveUp = { onWebSdrGiveUp() }
            )
            client = c
            val tuner = WebSdrSweepTuner(c)
            sdrTuner = tuner
            engine = SweepEngine(this, tuner) { freq, level -> logCapture(freq, level) }.apply {
                configureRange(range)
                setDwell(dwell)
                setSettle(settle)
                setSweepGap(gap)
                captureThreshold = (threshold / 100.0).coerceIn(0.0, 1.0)
            }
            engine?.start()
            val servers = buildServerList(server)
            c.start(servers)
        }
    }

    private fun buildServerList(preferred: String): List<String> {
        val list = mutableListOf(preferred)
        for (s in FALLBACK_SERVERS) {
            if (!list.contains(s)) list.add(s)
        }
        return list
    }

    private fun stopScan() {
        if (engine == null && client == null && fm == null) {
            SpiritBoxEvents.holdActive = false
            return
        }
        SpiritBoxEvents.serviceRunning = false
        SpiritBoxEvents.holdActive = false
        Prefs.clearActiveScan(this)
        SpiritBoxEvents.removeListener(eventsListener)
        engine?.stop()
        engine = null
        client?.close()
        client = null
        sdrTuner = null
        fm?.stop()
        fm = null
        // O AudioTrack do modo SDR é liberado pelo próprio thread de escrita
        // (AudioWriterThread) durante client.close(), evitando uso-após-release.
        audioTrack = null
        releaseWakeLock()
        abandonAudioFocus()
        SpiritBoxEvents.pushStatus(getString(R.string.status_stopped))
        SpiritBoxEvents.pushServiceStopped()
    }

    private fun onWebSdrGiveUp() {
        Handler(Looper.getMainLooper()).post {
            if (engine == null && client == null && fm == null) return@post
            Prefs.clearActiveScan(this)
            stopScan()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:scan").apply {
            setReferenceCounted(false)
            val timeout = WAKELOCK_TIMEOUT_MS
            if (timeout > 0) acquire(timeout) else acquire()
        }
    }

    private fun releaseWakeLock() {
        val wl = wakeLock ?: return
        try {
            if (wl.isHeld) wl.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun updateNotification() {
        val now = System.currentTimeMillis()
        if (engine == null || now - lastNotifUpdateMs < NOTIF_UPDATE_INTERVAL_MS) return
        lastNotifUpdateMs = now
        val freq = lastFreq
        val text = if (freq > 0) {
            getString(R.string.notif_scanning_freq, formatFreq(freq))
        } else {
            getString(R.string.notif_text_scanning)
        }
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ID, buildNotification(text))
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao atualizar notificação", e)
        }
    }

    private fun formatFreq(khz: Double): String {
        return if (khz >= 1000) {
            String.format(Locale.US, "%.3f MHz", khz / 1000)
        } else {
            "${khz.toInt()} kHz"
        }
    }

    private fun createAudioTrack(): AudioTrack {
        val rate = AUDIO_RATE
        val minBuf = AudioTrack.getMinBufferSize(
            rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, rate * 2))
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    }

    private fun requestAudioFocus() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioFocusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        val req = audioFocusRequest
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && req != null) {
            am.abandonAudioFocusRequest(req)
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusListener)
        }
        audioManager = null
        audioFocusRequest = null
    }

    private fun logCapture(freqKHz: Double, level: Int) {
        val file = captureLog ?: return
        try {
            val f = if (freqKHz >= 1000) {
                String.format(Locale.US, "%.3f", freqKHz / 1000) + " MHz"
            } else {
                "${freqKHz.toInt()} kHz"
            }
            val tsFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val ts = tsFormat.format(Date())
            if (!file.exists()) {
                file.appendText("data_iso,frequencia,nivel,modo,banda,servidor\n")
            }
            file.appendText(
                "${csvEscape(ts)},${csvEscape(f)},${csvEscape("$level%")}," +
                    "${csvEscape(activeMode)},${csvEscape(activeBand)},${csvEscape(activeServer)}\n"
            )
            mirrorToDownloads(file)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao gravar captura", e)
        }
        if (Prefs.alerts(this)) playCaptureAlert()
    }

    private fun playCaptureAlert() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            Handler(Looper.getMainLooper()).postDelayed({ tg.release() }, 250)
        } catch (e: Exception) {
            Log.w(TAG, "Falha no alerta sonoro", e)
        }
        try {
            val vibe = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibe.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibe.vibrate(120)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha na vibração", e)
        }
    }

    private fun mirrorToDownloads(src: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val fileName = "spiritbox_capturas.csv"
            val resolver = contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val existing = resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(fileName),
                null
            )
            val uri = if (existing != null && existing.moveToFirst()) {
                android.content.ContentUris.withAppendedId(collection, existing.getLong(0))
            } else {
                resolver.insert(
                    collection,
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                )
            }
            existing?.close()
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { os ->
                    src.inputStream().use { it.copyTo(os) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao espelhar CSV em Downloads", e)
        }
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, SpiritBoxService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val savePi = PendingIntent.getService(
            this, 1,
            Intent(this, SpiritBoxService::class.java).apply { action = ACTION_CAPTURE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(drawableIcon())
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(null, getString(R.string.action_save), savePi).build()
            )
            .addAction(
                Notification.Action.Builder(null, getString(R.string.action_stop), stopPi).build()
            )
            .build()
    }

    private fun drawableIcon(): Int = R.drawable.ic_launcher_foreground
}