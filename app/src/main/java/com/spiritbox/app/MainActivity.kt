package com.spiritbox.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.spiritbox.app.radio.SweepEngine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvFreq: TextView
    private lateinit var tvStatus: TextView
    private lateinit var meter: ProgressBar
    private lateinit var waterfall: WaterfallView
    private lateinit var btnToggle: MaterialButton
    private lateinit var etServer: EditText
    private lateinit var swFm: Switch
    private lateinit var swAlerts: Switch
    private lateinit var spBand: Spinner
    private lateinit var etDwell: EditText
    private lateinit var etSettle: EditText
    private lateinit var etThreshold: EditText
    private lateinit var etGap: EditText
    private lateinit var btnManualCapture: MaterialButton
    private lateinit var btnHold: MaterialButton
    private lateinit var btnMute: MaterialButton
    private lateinit var btnShare: MaterialButton
    private lateinit var listCaptures: ListView

    private val captures = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>

    private class CaptureEntry(val freqKHz: Double, val level: Int, val time: Long)

    private val captureEntries = ArrayList<CaptureEntry>()

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, R.string.notif_denied, Toast.LENGTH_LONG).show()
            }
        }

    private val listener = object : SpiritBoxEvents.Listener {
        override fun onFreq(freqKHz: Double) {
            tvFreq.text = formatFreq(freqKHz)
        }

        override fun onStatus(text: String) {
            tvStatus.text = text
        }

        override fun onCapture(freqKHz: Double, level: Int) {
            adapter.add("${formatFreq(freqKHz)}  ·  nível $level%")
            captureEntries.add(CaptureEntry(freqKHz, level, System.currentTimeMillis()))
            if (adapter.count > 200) {
                adapter.remove(adapter.getItem(0))
                if (captureEntries.isNotEmpty()) captureEntries.removeAt(0)
            }
            listCaptures.smoothScrollToPosition(adapter.count - 1)
        }

        override fun onRms(rms: Double) {
            meter.progress = (rms * 100).toInt().coerceIn(0, 100)
        }

        override fun onServiceStopped() {
            updateToggle()
        }

        override fun onHoldChanged(hold: Boolean) {
            updateHoldButton(hold)
        }

        override fun onWaterfall(freqKHz: Double, rms: Double) {
            waterfall.addSample(freqKHz, rms)
        }

        override fun onSweepCycle() {
            waterfall.nextCycle()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvFreq = findViewById(R.id.tvFreq)
        tvStatus = findViewById(R.id.tvStatus)
        meter = findViewById(R.id.meter)
        waterfall = findViewById(R.id.waterfall)
        btnToggle = findViewById(R.id.btnToggle)
        etServer = findViewById(R.id.etServer)
        swFm = findViewById(R.id.swFm)
        swAlerts = findViewById(R.id.swAlerts)
        listCaptures = findViewById(R.id.listCaptures)
        spBand = findViewById(R.id.spBand)
        etDwell = findViewById(R.id.etDwell)
        etSettle = findViewById(R.id.etSettle)
        etThreshold = findViewById(R.id.etThreshold)
        etGap = findViewById(R.id.etGap)
        btnManualCapture = findViewById(R.id.btnManualCapture)
        btnHold = findViewById(R.id.btnHold)
        btnMute = findViewById(R.id.btnMute)
        btnShare = findViewById(R.id.btnShare)

        spBand.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            SweepEngine.PRESETS.map { getString(SweepEngine.bandId(it)) }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        loadPrefs()

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, captures)
        listCaptures.adapter = adapter
        listCaptures.setOnItemClickListener { _, _, position, _ ->
            captureEntries.getOrNull(position)?.let { showCaptureDetail(it) }
        }
        loadPersistedCaptures()

        updateToggle()

        btnToggle.setOnClickListener {
            if (SpiritBoxEvents.serviceRunning) {
                SpiritBoxService.stop(this)
            } else {
                requestPermissionsIfNeeded()
                savePrefs()
                val host = etServer.text.toString().trim()
                val server = if (host.isEmpty()) SpiritBoxService.DEFAULT_SERVER else host
                val mode = if (swFm.isChecked) SpiritBoxService.MODE_FM else SpiritBoxService.MODE_SDR
                val range = SweepEngine.PRESETS[spBand.selectedItemPosition.coerceIn(
                    0, SweepEngine.PRESETS.size - 1
                )]
                val dwell = etDwell.text.toString().toLongOrNull() ?: 300L
                val settle = etSettle.text.toString().toLongOrNull() ?: 150L
                val threshold = etThreshold.text.toString().toIntOrNull() ?: 12
                val gap = etGap.text.toString().toLongOrNull() ?: 300L
                waterfall.configure(range.startKHz.toDouble(), range.endKHz.toDouble())
                SpiritBoxService.start(this, mode, server, range, dwell, settle, threshold, gap)
            }
        }

        btnManualCapture.setOnClickListener {
            if (!SpiritBoxEvents.serviceRunning) {
                Toast.makeText(this, R.string.scan_not_running, Toast.LENGTH_SHORT).show()
            } else {
                SpiritBoxService.saveManualCapture(this)
            }
        }

        btnHold.setOnClickListener {
            if (!SpiritBoxEvents.serviceRunning) {
                Toast.makeText(this, R.string.scan_not_running, Toast.LENGTH_SHORT).show()
            } else {
                SpiritBoxService.toggleHold(this)
            }
        }

        btnMute.setOnClickListener {
            val muted = !Prefs.muted(this)
            Prefs.saveMuted(this, muted)
            updateMuteButton()
            if (SpiritBoxEvents.serviceRunning) {
                SpiritBoxService.setMuted(this, muted)
            }
        }
        updateMuteButton()

        btnShare.setOnClickListener { shareCaptures() }
    }

    override fun onStart() {
        super.onStart()
        SpiritBoxEvents.addListener(listener)
        updateToggle()
    }

    override fun onStop() {
        super.onStop()
        SpiritBoxEvents.removeListener(listener)
    }

    private fun loadPrefs() {
        etServer.setText(Prefs.server(this))
        swFm.isChecked = Prefs.fmMode(this)
        swAlerts.isChecked = Prefs.alerts(this)
        spBand.setSelection(Prefs.rangeIndex(this).coerceIn(0, SweepEngine.PRESETS.size - 1))
        etDwell.setText(Prefs.dwell(this).toString())
        etSettle.setText(Prefs.settle(this).toString())
        etThreshold.setText(Prefs.thresholdPct(this).toString())
        etGap.setText(Prefs.gap(this).toString())
        updateHoldButton(false)
    }

    private fun savePrefs() {
        val host = etServer.text.toString().trim()
        if (host.isNotEmpty()) Prefs.saveServer(this, host)
        Prefs.saveFmMode(this, swFm.isChecked)
        Prefs.saveRangeIndex(this, spBand.selectedItemPosition)
        etDwell.text.toString().toLongOrNull()?.let { Prefs.saveDwell(this, it) }
        etSettle.text.toString().toLongOrNull()?.let { Prefs.saveSettle(this, it) }
        etThreshold.text.toString().toIntOrNull()?.let { Prefs.saveThreshold(this, it) }
        etGap.text.toString().toLongOrNull()?.let { Prefs.saveGap(this, it) }
        Prefs.saveAlerts(this, swAlerts.isChecked)
    }

    private fun loadPersistedCaptures() {
        val file = File(filesDir, "capturas.csv")
        if (!file.exists()) return
        try {
            val lines = file.readLines()
            for (i in 1 until lines.size) { // pula cabeçalho
                val cols = parseCsvLine(lines[i])
                if (cols.size < 3) continue
                val time = parseIsoUtc(cols[0]) ?: continue
                val freqKHz = parseFreqToKHz(cols[1]) ?: continue
                val level = parseLevel(cols[2]) ?: continue
                adapter.add("${formatFreq(freqKHz)}  ·  nível $level%")
                captureEntries.add(CaptureEntry(freqKHz, level, time))
            }
            while (adapter.count > 200) {
                adapter.remove(adapter.getItem(0))
                if (captureEntries.isNotEmpty()) captureEntries.removeAt(0)
            }
            if (adapter.count > 0) {
                listCaptures.smoothScrollToPosition(adapter.count - 1)
            }
        } catch (e: Exception) {
            // CSV corrompido ou ilegível: segue sem histórico
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    sb.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        result.add(sb.toString())
                        sb.setLength(0)
                    }
                    else -> sb.append(ch)
                }
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    private fun parseIsoUtc(text: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse(text)?.time
    } catch (_: Exception) {
        null
    }

    private fun parseFreqToKHz(text: String): Double? {
        val t = text.trim()
        return when {
            t.endsWith("MHz", ignoreCase = true) ->
                t.dropLast(3).trim().toDoubleOrNull()?.times(1000.0)
            t.endsWith("kHz", ignoreCase = true) ->
                t.dropLast(3).trim().toDoubleOrNull()
            else -> t.toDoubleOrNull()
        }
    }

    private fun parseLevel(text: String): Int? =
        text.removeSuffix("%").trim().toIntOrNull()?.coerceIn(0, 100)

    private fun showCaptureDetail(entry: CaptureEntry) {
        val time = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            .format(Date(entry.time))
        val freq = formatFreq(entry.freqKHz)
        val message = StringBuilder()
            .append(getString(R.string.capture_detail_freq, freq)).append('\n')
            .append(getString(R.string.capture_detail_level, entry.level)).append('\n')
            .append(getString(R.string.capture_detail_time, time))
            .toString()
        AlertDialog.Builder(this)
            .setTitle(R.string.capture_detail_title)
            .setMessage(message)
            .setPositiveButton(R.string.btn_copy_freq) { _, _ ->
                copyToClipboard(freq)
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("freq", text))
    }

    private fun shareCaptures() {
        val file = File(filesDir, "capturas.csv")
        if (!file.exists()) {
            Toast.makeText(this, R.string.share_no_file, Toast.LENGTH_SHORT).show()
            return
        }
        val uri: Uri = FileProvider.getUriForFile(
            this, "$packageName.fileprovider", file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(send, getString(R.string.share_title)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.share_no_file, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestIgnoreBatteryOptimizations()
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
        }
    }

    private fun updateToggle() {
        btnToggle.text = getString(
            if (SpiritBoxEvents.serviceRunning) R.string.btn_stop else R.string.btn_start
        )
        updateHoldButton(SpiritBoxEvents.holdActive)
    }

    private fun updateHoldButton(hold: Boolean) {
        btnHold.text = getString(if (hold) R.string.btn_release else R.string.btn_hold)
    }

    private fun updateMuteButton() {
        val muted = Prefs.muted(this)
        btnMute.text = getString(if (muted) R.string.btn_unmute else R.string.btn_mute)
    }

    private fun formatFreq(khz: Double): String {
        return if (khz >= 1000) {
            String.format(Locale.US, "%.3f MHz", khz / 1000)
        } else {
            "${khz.toInt()} kHz"
        }
    }
}