package com.spiritbox.app

import android.Manifest
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.spiritbox.app.radio.SweepEngine
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    companion object {
        private const val THEME_MODES = 3
    }

    private lateinit var tvFreq: TextView
    private lateinit var tvStatus: TextView
    private lateinit var meter: ProgressBar
    private lateinit var waterfall: WaterfallView
    private lateinit var btnToggle: MaterialButton
    private lateinit var etServer: EditText
    private lateinit var swFm: MaterialSwitch
    private lateinit var swAlerts: MaterialSwitch
    private lateinit var swNoise: MaterialSwitch
    private lateinit var spBand: Spinner
    private lateinit var etDwell: EditText
    private lateinit var etSettle: EditText
    private lateinit var etThreshold: EditText
    private lateinit var etGap: EditText
    private lateinit var btnManualCapture: MaterialButton
    private lateinit var btnHold: MaterialButton
    private lateinit var btnMute: MaterialButton
    private lateinit var btnBookmark: MaterialButton
    private lateinit var spFav: Spinner
    private lateinit var btnShare: MaterialButton
    private lateinit var btnTheme: MaterialButton
    private lateinit var btnNight: MaterialButton
    private lateinit var btnEmf: MaterialButton
    private lateinit var miniMap: MiniMapView
    private lateinit var redFilter: View
    private lateinit var listCaptures: ListView
    private var scrollRoot: NestedScrollView? = null

    private val monitor = ActivityMonitor()
    private val emfMeter = EmfMeter(this)
    private val emfHandler = Handler(Looper.getMainLooper())
    private val mapHandler = Handler(Looper.getMainLooper())
    private var emfRunning = false
    private var emfValue = 0f
    private var emfTrend = 0f
    private var emfGauge: EmfGaugeView? = null
    private var emfReadout: TextView? = null
    private var emfTrendView: EmfTrendView? = null
    private var emfDialog: Dialog? = null
    private var hotspotDialog: Dialog? = null
    private var recordingLocation = false
    private var lastLocation: Location? = null
    private var statusTaps = 0
    private var lastStatusTap = 0L

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            if (recordingLocation) pushHotspot(location)
        }
    }

    private val locationPerm =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (fine || coarse) {
                openEmfMapDialogCore()
            } else {
                Toast.makeText(this, R.string.emf_map_denied, Toast.LENGTH_LONG).show()
            }
        }

    private val captures = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>

    private class CaptureEntry(val freqKHz: Double, val level: Int, val time: Long)

    private val captureEntries = ArrayList<CaptureEntry>()
    private var lastFreq: Double = 0.0

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, R.string.notif_denied, Toast.LENGTH_LONG).show()
            }
        }

    private val listener = object : SpiritBoxEvents.Listener {
        override fun onFreq(freqKHz: Double) {
            tvFreq.text = formatFreq(freqKHz)
            lastFreq = freqKHz
            updateBookmarkButton()
        }

        override fun onStatus(text: String) {
            tvStatus.text = text
        }

        override fun onCapture(freqKHz: Double, level: Int) {
            adapter.add("${formatFreq(freqKHz)}  ·  nível $level%")
            captureEntries.add(CaptureEntry(freqKHz, level, System.currentTimeMillis()))
            waterfall.markCapture(freqKHz)
            monitor.onCapture(freqKHz, level)
            if (adapter.count > 200) {
                adapter.remove(adapter.getItem(0))
                if (captureEntries.isNotEmpty()) captureEntries.removeAt(0)
            }
            listCaptures.smoothScrollToPosition(adapter.count - 1)
            followCapturesIfNearBottom()
        }

        override fun onRms(rms: Double) {
            meter.progress = (rms * 100).toInt().coerceIn(0, 100)
            monitor.onRms(rms)
        }

        override fun onServiceStopped() {
            monitor.reset()
            miniMap.reset()
            miniMap.visibility = View.GONE
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
        applyThemeMode()
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
        swNoise = findViewById(R.id.swNoise)
        listCaptures = findViewById(R.id.listCaptures)
        scrollRoot = findViewById(R.id.scrollRoot)
        spBand = findViewById(R.id.spBand)
        etDwell = findViewById(R.id.etDwell)
        etSettle = findViewById(R.id.etSettle)
        etThreshold = findViewById(R.id.etThreshold)
        etGap = findViewById(R.id.etGap)
        btnManualCapture = findViewById(R.id.btnManualCapture)
        btnHold = findViewById(R.id.btnHold)
        btnMute = findViewById(R.id.btnMute)
        btnBookmark = findViewById(R.id.btnBookmark)
        spFav = findViewById(R.id.spFav)
        btnShare = findViewById(R.id.btnShare)
        btnTheme = findViewById(R.id.btnTheme)
        btnNight = findViewById(R.id.btnNight)
        btnEmf = findViewById(R.id.btnEmf)
        redFilter = findViewById(R.id.redFilter)
        miniMap = findViewById(R.id.miniMap)

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
                monitor.configureBand(range.startKHz.toDouble(), range.endKHz.toDouble())
                monitor.reset()
                miniMap.reset()
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

        swNoise.setOnCheckedChangeListener { _, checked ->
            Prefs.saveNoiseReduction(this, checked)
            if (SpiritBoxEvents.serviceRunning) {
                SpiritBoxService.setNoiseReduction(this, checked)
            }
        }

        btnShare.setOnClickListener { shareCaptures() }

        btnBookmark.setOnClickListener {
            if (lastFreq <= 0) {
                Toast.makeText(this, R.string.scan_waiting_freq, Toast.LENGTH_SHORT).show()
            } else {
                Prefs.toggleFavorite(this, lastFreq)
                updateBookmarkButton()
                loadFavorites()
            }
        }

        setupFavorites()

        waterfall.setOnLongClickListener {
            shareWaterfall()
            true
        }

        btnTheme.setOnClickListener {
            val next = (Prefs.themeMode(this) + 1) % THEME_MODES
            Prefs.saveThemeMode(this, next)
            applyThemeMode()
            updateThemeButton()
            recreate()
        }

        btnNight.setOnClickListener {
            val on = !Prefs.redNight(this)
            Prefs.saveRedNight(this, on)
            applyNightFilter()
        }

        btnEmf.setOnClickListener { openEmfDialog() }

        tvStatus.setOnClickListener {
            val now = System.currentTimeMillis()
            statusTaps = if (now - lastStatusTap < 2000) statusTaps + 1 else 1
            lastStatusTap = now
            if (statusTaps >= 7) {
                statusTaps = 0
                val on = !Prefs.motionUnlocked(this)
                Prefs.saveMotionUnlocked(this, on)
                Toast.makeText(
                    this,
                    getString(if (on) R.string.motion_unlocked else R.string.motion_locked),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        updateMuteButton()
        updateThemeButton()
        updateNightButton()
        applyNightFilter()

        spiritCheckUpdatesSilently()
    }

    /** Auto-check de atualização. Respeita o cooldown do Prefs; resultado vira diálogo. */
    private fun spiritCheckUpdatesSilently() {
        UpdateChecker.check(this) { info ->
            if (info == null) return@check
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.check_update_title)
                .setMessage(getString(R.string.check_update_msg, info.versionName))
                .setPositiveButton(R.string.check_update_btn) { _, _ ->
                    downloadAndInstallUpdate(info)
                }
                .setNegativeButton(R.string.check_update_later, null)
                .show()
        }
    }

    private fun downloadAndInstallUpdate(info: UpdateChecker.UpdateInfo) {
        UpdateChecker.downloadLatest(
            this,
            { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() },
            { file ->
                if (file == null) {
                    Toast.makeText(this, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                } else {
                    installApk(file)
                }
            }
        )
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.update_download_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun openEmfDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_emf)
        emfGauge = dialog.findViewById(R.id.emfGauge)
        emfReadout = dialog.findViewById(R.id.emfReadout)
        emfTrendView = dialog.findViewById(R.id.emfTrend)
        emfTrendView?.clear()
        dialog.findViewById<MaterialButton>(R.id.emfMapBtn).setOnClickListener {
            dialog.dismiss()
            openEmfMapDialog()
        }
        emfMeter.start()
        if (!emfMeter.available) {
            Toast.makeText(this, R.string.emf_no_sensor, Toast.LENGTH_LONG).show()
        }
        emfRunning = true
        emfHandler.post(emfRunnable)
        emfDialog = dialog
        dialog.setOnDismissListener {
            emfRunning = false
            emfHandler.removeCallbacks(emfRunnable)
            emfMeter.stop()
            emfDialog = null
        }
        dialog.show()
    }

    private fun openEmfMapDialog() {
        if (!Prefs.motionUnlocked(this)) {
            Toast.makeText(this, R.string.emf_map_locked, Toast.LENGTH_LONG).show()
            return
        }
        val hasFine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            locationPerm.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            openEmfMapDialogCore()
        }
    }

    private fun openEmfMapDialogCore() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_emf_hotspots)
        val map = dialog.findViewById<EmfHotspotMapView>(R.id.emfMap)
        val stats = dialog.findViewById<TextView>(R.id.emfMapStats)
        val btnRecord = dialog.findViewById<MaterialButton>(R.id.emfMapRecord)
        val btnClear = dialog.findViewById<MaterialButton>(R.id.emfMapClear)
        map.setPoints(loadHotspots())
        updateHotspotStats(stats, map)
        emfMeter.start()
        hotspotDialog = dialog
        recordingLocation = false
        btnRecord.text = getString(R.string.emf_map_record)

        fun setRecording(on: Boolean) {
            recordingLocation = on
            btnRecord.text = getString(if (on) R.string.emf_map_record_on else R.string.emf_map_record)
        }

        btnRecord.setOnClickListener {
            if (!recordingLocation && !isLocationEnabled()) {
                Toast.makeText(this, R.string.emf_map_gps_off, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (recordingLocation) {
                stopLocationUpdates()
                setRecording(false)
            } else {
                startLocationUpdates()
                setRecording(true)
            }
        }
        btnClear.setOnClickListener {
            map.clear()
            updateHotspotStats(stats, map)
        }
        dialog.setOnDismissListener {
            recordingLocation = false
            stopLocationUpdates()
            emfMeter.stop()
            saveHotspots(map.points())
            hotspotDialog = null
        }
        dialog.show()
    }

    private fun pushHotspot(location: Location) {
        val mG = emfMeter.milliGauss()
        val dialog = hotspotDialog ?: return
        if (mG == null) return
        val map = dialog.findViewById<EmfHotspotMapView>(R.id.emfMap)
        val stats = dialog.findViewById<TextView>(R.id.emfMapStats)
        map.addPoint(location.latitude, location.longitude, mG)
        updateHotspotStats(stats, map)
    }

    private fun updateHotspotStats(tv: TextView, map: EmfHotspotMapView) {
        val pts = map.points()
        val maxMg = pts.maxOfOrNull { it.mg } ?: 0f
        tv.text = getString(
            R.string.emf_map_stats, pts.size, String.format(Locale.US, "%.1f", maxMg)
        )
    }

    private fun locationManager(): LocationManager =
        getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private fun isLocationEnabled(): Boolean {
        val lm = locationManager()
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun startLocationUpdates() {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        val lm = locationManager()
        try {
            if (fine) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000L, 1f, locationListener
                )
            }
            lm.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 2000L, 1f, locationListener
            )
        } catch (_: Exception) {
        }
    }

    private fun stopLocationUpdates() {
        try {
            locationManager().removeUpdates(locationListener)
        } catch (_: Exception) {
        }
    }

    private fun hotspotFile(): File = File(filesDir, "emf_hotspots.json")

    private fun saveHotspots(list: List<EmfHotspotMapView.Point>) {
        try {
            val arr = JSONArray()
            list.forEach { p ->
                arr.put(
                    JSONObject().apply {
                        put("lat", p.lat)
                        put("lng", p.lng)
                        put("mg", p.mg.toDouble())
                        put("t", p.time)
                    }
                )
            }
            val obj = JSONObject().put("points", arr)
            FileOutputStream(hotspotFile()).write(obj.toString().toByteArray())
        } catch (_: Exception) {
        }
    }

    private fun loadHotspots(): ArrayList<EmfHotspotMapView.Point> {
        val out = ArrayList<EmfHotspotMapView.Point>()
        try {
            val arr = JSONObject(hotspotFile().readText()).getJSONArray("points")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    EmfHotspotMapView.Point(
                        o.getDouble("lat"),
                        o.getDouble("lng"),
                        o.getDouble("mg").toFloat(),
                        o.getLong("t")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out
    }

    private val emfRunnable = object : Runnable {
        override fun run() {
            if (!emfRunning) return
            val fresh = emfMeter.milliGauss()
            val value: Float = if (fresh != null) {
                fresh
            } else {
                val m = monitor.movement().toFloat()
                emfTrend = emfTrend + (m - emfTrend) * 0.2f
                val noise = ((Math.random() * 8) - 4).toFloat()
                (emfValue + emfTrend * 0.35f + noise).coerceIn(0f, 99.9f)
            }
            emfValue = value.coerceIn(0f, 99.9f)
            emfGauge?.setValue(emfValue / 100f)
            emfTrendView?.push(emfValue)
            val color = when {
                emfValue >= 70f -> Color.rgb(0xE0, 0x2C, 0x20)
                emfValue >= 40f -> Color.rgb(0xF0, 0xA0, 0x00)
                else -> Color.rgb(0x37, 0xB0, 0x50)
            }
            emfReadout?.setTextColor(color)
            emfReadout?.text = String.format(Locale.US, "%.1f mG", emfValue)
            emfHandler.postDelayed(this, 250)
        }
    }

    private val mapRunnable = object : Runnable {
        override fun run() {
            mapHandler.postDelayed(this, 600)
            if (!Prefs.motionUnlocked(this@MainActivity)) {
                if (miniMap.visibility == View.VISIBLE) miniMap.visibility = View.GONE
                return
            }
            val m = monitor.movement()
            miniMap.push(m)
            val shouldShow = m >= 30 && SpiritBoxEvents.serviceRunning
            if (shouldShow && miniMap.visibility != View.VISIBLE) {
                miniMap.visibility = View.VISIBLE
            } else if (!shouldShow && miniMap.visibility == View.VISIBLE) {
                miniMap.visibility = View.GONE
            }
        }
    }

    override fun onStart() {
        super.onStart()
        SpiritBoxEvents.addListener(listener)
        mapHandler.post(mapRunnable)
        updateToggle()
    }

    override fun onStop() {
        super.onStop()
        SpiritBoxEvents.removeListener(listener)
        mapHandler.removeCallbacks(mapRunnable)
        if (emfRunning) {
            emfRunning = false
            emfHandler.removeCallbacks(emfRunnable)
        }
        emfMeter.stop()
        stopLocationUpdates()
    }

    private fun loadPrefs() {
        etServer.setText(Prefs.server(this))
        swFm.isChecked = Prefs.fmMode(this)
        swAlerts.isChecked = Prefs.alerts(this)
        swNoise.isChecked = Prefs.noiseReduction(this)
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
        Prefs.saveNoiseReduction(this, swNoise.isChecked)
    }

    private fun followCapturesIfNearBottom() {
        val sv = scrollRoot ?: return
        val child = sv.getChildAt(0) ?: return
        val maxY = (child.height - sv.height).coerceAtLeast(0)
        if (sv.scrollY >= maxY - 120) {
            sv.post { sv.smoothScrollTo(0, maxY) }
        }
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

    private fun setupFavorites() {
        spFav.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position <= 0) return
                val favs = Prefs.favorites(this@MainActivity)
                val freq = favs.getOrNull(position - 1) ?: return
                spFav.setSelection(0)
                if (!SpiritBoxEvents.serviceRunning) {
                    Toast.makeText(this@MainActivity, R.string.scan_not_running, Toast.LENGTH_SHORT).show()
                    return
                }
                lastFreq = freq
                SpiritBoxService.tuneTo(this@MainActivity, freq)
                tvFreq.text = formatFreq(freq)
                updateBookmarkButton()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        loadFavorites()
    }

    private fun loadFavorites() {
        val favs = Prefs.favorites(this)
        val items = ArrayList<String>()
        items.add(getString(R.string.fav_prompt))
        items.addAll(favs.map { formatFreq(it) })
        spFav.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spFav.setSelection(0)
        updateBookmarkButton()
    }

    private fun updateBookmarkButton() {
        btnBookmark.text = if (Prefs.isFavorite(this, lastFreq)) "★" else getString(R.string.bookmark_off)
    }

    private fun shareWaterfall() {
        val bmp = try {
            waterfall.snapshotBitmap()
        } catch (_: Exception) {
            null
        } ?: return
        val file = File(cacheDir, "waterfall.png")
        try {
            file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.share_no_file, Toast.LENGTH_SHORT).show()
            return
        }
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(send, getString(R.string.share_waterfall_title)))
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

    private fun nightModeFor(mode: Int): Int = when (mode) {
        1 -> AppCompatDelegate.MODE_NIGHT_NO
        2 -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    private fun themeModeStringRes(mode: Int): Int = when (mode) {
        1 -> R.string.theme_light
        2 -> R.string.theme_dark
        else -> R.string.theme_system
    }

    private fun applyThemeMode() {
        AppCompatDelegate.setDefaultNightMode(nightModeFor(Prefs.themeMode(this)))
    }

    private fun updateThemeButton() {
        btnTheme.text = getString(
            R.string.btn_theme, getString(themeModeStringRes(Prefs.themeMode(this)))
        )
    }

    private fun updateNightButton() {
        btnNight.text = getString(
            if (Prefs.redNight(this)) R.string.btn_night_on else R.string.btn_night_off
        )
    }

    private fun applyNightFilter() {
        val on = Prefs.redNight(this)
        redFilter.visibility = if (on) View.VISIBLE else View.GONE
        window.attributes = window.attributes.apply {
            screenBrightness = if (on) {
                0.55f
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    private fun formatFreq(khz: Double): String {
        return if (khz >= 1000) {
            String.format(Locale.US, "%.3f MHz", khz / 1000)
        } else {
            "${khz.toInt()} kHz"
        }
    }
}