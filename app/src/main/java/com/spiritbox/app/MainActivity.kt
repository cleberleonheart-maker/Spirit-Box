package com.spiritbox.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
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
import android.util.Log
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
    private lateinit var tvVersion: TextView
    private var scrollRoot: NestedScrollView? = null

    private val monitor = ActivityMonitor()
    private val emfMeterLazy = lazy { EmfMeter(this) }
    private val emfMeter by emfMeterLazy
    private val emfHandler = Handler(Looper.getMainLooper())
    private val mapHandler = Handler(Looper.getMainLooper())
    private var emfRunning = false
    private var pendingBatteryPrompt = false
    private var emfValue = 0f
    private var emfTrend = 0f
    private var emfGauge: EmfGaugeView? = null
    private var emfReadout: TextView? = null
    private var emfTrendView: EmfTrendView? = null
    private var emfDialog: Dialog? = null
    private var hotspotDialog: Dialog? = null
    private var recordingLocation = false
    private var lastLocation: Location? = null

    /** onStop desliga sensor, localizacao e o loop do medidor. Marca aqui para
     * o onResume saber que precisa religar, e nao religar em toda volta. */
    private var emfTornDown = false

    /** Runnables do mapa elevatedos a campo: antes eram locais, entao o onStop
     * nao conseguia cancelar e o statusTick continuava se repostando a cada
     * segundo, segurando a Activity e o Dialog. */
    private var mapStatusTick: Runnable? = null
    private var mapMap: EmfHotspotMapView? = null
    private var mapStatusView: TextView? = null
    private var statusTaps = 0
    private var lastStatusTap = 0L

    /** Ultimo fix de localizacao recebido desde o ultimo PARAR. Sem isso o mapa
     * ficava vazio por qualquer um dos quatro motivos e nenhum era distinguivel. */
    @Volatile
    private var locationFixSeen = false

    /** Erro thrown ao registrar GPS ou rede; a mensagem vai para o status. */
    @Volatile
    private var locationProviderError: String? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            locationFixSeen = true
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
        tvVersion = findViewById(R.id.tvVersion)
        tvVersion.text = getString(R.string.app_version, BuildConfig.VERSION_NAME)
        findViewById<MaterialButton>(R.id.btnCheckUpdate)
            .setOnClickListener { checkUpdateOnDemand(it as MaterialButton) }
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
                waterfall.configure(
                    range.startKHz.toDouble(),
                    range.endKHz.toDouble(),
                    range.stepKHz.toDouble()
                )

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

        waterfall.onTapped = { freq ->
            if (!SpiritBoxEvents.serviceRunning) {
                Toast.makeText(this, R.string.scan_not_running, Toast.LENGTH_SHORT).show()
            } else {
                SpiritBoxService.tuneTo(this, freq)
                lastFreq = freq
                updateBookmarkButton()
                Toast.makeText(
                    this,
                    getString(R.string.waterfall_tuned, WaterfallView.formatKHz(freq)),
                    Toast.LENGTH_SHORT
                ).show()
            }
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
        UpdateChecker.check(this) { result ->
            if (result !is UpdateChecker.Result.Available) return@check
            showUpdateDialog(result.info)
        }
    }

    /** Consulta a pedido, sem cooldown. Antes nao havia nenhuma forma de forcar
     * a verificacao, e o automatico era limitado a uma vez por dia. */
    private fun checkUpdateOnDemand(button: MaterialButton) {
        button.isEnabled = false
        button.text = getString(R.string.check_update_checking)
        UpdateChecker.check(this, force = true) { result ->
            // A resposta chega ate 20 s depois (timeouts da API) e pode cair com
            // a Activity ja fechada; show() sem guarda estoura BadTokenException.
            if (isFinishing || isDestroyed) return@check
            button.isEnabled = true
            button.text = getString(R.string.check_update_now)
            when (result) {
                is UpdateChecker.Result.Available -> showUpdateDialog(result.info)
                UpdateChecker.Result.UpToDate -> Toast.makeText(
                    this, R.string.check_update_uptodate, Toast.LENGTH_LONG
                ).show()
                UpdateChecker.Result.Unavailable -> Toast.makeText(
                    this, R.string.check_update_error, Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showUpdateDialog(info: UpdateChecker.UpdateInfo) {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.check_update_title)
            .setMessage(
                getString(R.string.check_update_msg, info.versionName) +
                    "\n" + getString(R.string.app_version, BuildConfig.VERSION_NAME)
            )
            .setPositiveButton(R.string.check_update_btn) { _, _ ->
                downloadAndInstallUpdate(info)
            }
            .setNegativeButton(R.string.check_update_later, null)
            .show()
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
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: SecurityException) {
            Log.w("SpiritBox", "Instalação do APK bloqueada", e)
            Toast.makeText(this, R.string.update_install_denied, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.w("SpiritBox", "Falha ao abrir o instalador", e)
            Toast.makeText(this, R.string.update_install_failed, Toast.LENGTH_LONG).show()
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
        warnBatteryIfRestricted()

        val status = dialog.findViewById<TextView>(R.id.emfMapStatus)
        mapMap = map
        mapStatusView = status

        /** O mapa vazio era indistinguivel entre quatro causas: sem permissao,
         * sem provedor registrado, sem fix e sem leitura do sensor. Cada uma
         * agora tem uma linha propria, atualizada ao vivo enquanto grava. */
        fun refreshStatus() {
            val dialogUp = hotspotDialog
            if (dialogUp == null) return
            val text = when {
                !recordingLocation -> getString(R.string.emf_status_idle)
                !hasLocationPermission() ->
                    getString(R.string.emf_status_no_permission)
                !isLocationEnabled() -> getString(R.string.emf_status_gps_off)
                locationProviderError != null -> getString(
                    R.string.emf_status_provider_failed, locationProviderError!!
                )
                !locationFixSeen ->
                    getString(R.string.emf_status_waiting_fix)
                !emfMeter.available -> getString(R.string.emf_status_no_magnetometer)
                emfMeter.milliGauss() == null ->
                    getString(R.string.emf_status_sensor_blocked)
                emfMeter.uncalibratedOnly -> getString(R.string.emf_status_uncalibrated)
                else -> getString(R.string.emf_status_recording, map.points().size)
            }
            status.text = text
            status.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }

        val statusTick = object : Runnable {
            override fun run() {
                refreshStatus()
                if (recordingLocation && !emfTornDown) emfHandler.postDelayed(this, 1000L)
            }
        }
        mapStatusTick = statusTick

        fun setRecording(on: Boolean) {
            recordingLocation = on
            emfHandler.removeCallbacks(noSensorCheck)
            emfHandler.removeCallbacks(statusTick)
            if (on) {
                hotspotSaveFailed = false
                recordStartPoints = map.points().size
                locationFixSeen = false
                locationProviderError = null
                emfHandler.postDelayed(noSensorCheck, 6000L)
                emfHandler.post(statusTick)
            }
            btnRecord.text = getString(if (on) R.string.emf_map_record_on else R.string.emf_map_record)
            refreshStatus()
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
            hotspotIo.execute {
                try {
                    hotspotFile().delete()
                } catch (e: Exception) {
                    Log.w("SpiritBox", "Falha ao limpar hotspots", e)
                }
            }
        }
        dialog.setOnDismissListener {
            recordingLocation = false
            emfHandler.removeCallbacks(noSensorCheck)
            emfHandler.removeCallbacks(statusTick)
            stopLocationUpdates()
            emfMeter.stop()
            hotspotDialog = null
        }
        dialog.show()
        refreshStatus()
    }

    private fun pushHotspot(location: Location) {
        val dialog = hotspotDialog ?: return
        val mG = emfMeter.milliGauss()
        if (mG == null) return
        val map = dialog.findViewById<EmfHotspotMapView>(R.id.emfMap)
        val stats = dialog.findViewById<TextView>(R.id.emfMapStats)
        map.addPoint(location.latitude, location.longitude, mG)
        updateHotspotStats(stats, map)
        appendHotspot(map.points().last())
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

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
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
        val errors = ArrayList<String>()

        // Cada provedor e registrado no seu proprio try: uma excecao no GPS
        // pulava o registro da rede, e o mapa ficava vazio para sempre sem
        // nenhuma pista do motivo.
        if (fine) {
            try {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000L, 1f, locationListener
                )
            } catch (e: Exception) {
                errors.add("GPS: ${e.javaClass.simpleName}")
                Log.w("SpiritBox", "Falha ao registrar GPS_PROVIDER", e)
            }
        }
        try {
            lm.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 2000L, 1f, locationListener
            )
        } catch (e: Exception) {
            errors.add("rede: ${e.javaClass.simpleName}")
            Log.w("SpiritBox", "Falha ao registrar NETWORK_PROVIDER", e)
        }
        locationProviderError = if (errors.isEmpty()) null else errors.joinToString("; ")
    }

    private fun stopLocationUpdates() {
        try {
            locationManager().removeUpdates(locationListener)
        } catch (_: Exception) {
        }
    }

    private fun hotspotFile(): File = File(filesDir, "emf_hotspots.jsonl")

    private fun legacyHotspotFile(): File = File(filesDir, "emf_hotspots.json")

    /** Gravação dos hotspots fora da main thread. Um ponto por vez, em append: o
     * arquivo antigo era reescrito inteiro a cada 3s, na main thread, com o custo
     * crescendo junto com o trajeto. */
    private val hotspotIo: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var hotspotSaveFailed = false

    private var recordStartPoints = 0

    /** Sem amostra o magnetômetro nao entrega nada, e o ponto era descartado em
     * silencio -- era o sintoma do Doze. Como o sensor precisa de algumas leituras
     * para fixar a linha de base, a espera evita acusar o aquecimento de falha. */
    private val noSensorCheck = object : Runnable {
        override fun run() {
            if (!recordingLocation) return
            // Com o app em background o onStop ja desregistrou o sensor; acusar
            // o magnetometro aqui seria culpa do proprio app.
            if (emfTornDown) return
            if (!emfMeterLazy.isInitialized() || !emfMeter.available) return
            val map = mapMap ?: return
            if (map.points().size > recordStartPoints) return
            Toast.makeText(this@MainActivity, R.string.emf_map_no_sensor, Toast.LENGTH_LONG).show()
        }
    }

    private fun pointJson(p: EmfHotspotMapView.Point): String =
        JSONObject()
            .put("lat", p.lat)
            .put("lng", p.lng)
            .put("mg", p.mg.toDouble())
            .put("t", p.time)
            .toString()

    private fun appendHotspot(p: EmfHotspotMapView.Point) {
        hotspotIo.execute {
            try {
                FileOutputStream(hotspotFile(), true).use {
                    it.write((pointJson(p) + "\n").toByteArray())
                }
            } catch (e: Exception) {
                Log.w("SpiritBox", "Falha ao gravar hotspot", e)
                if (!hotspotSaveFailed) {
                    hotspotSaveFailed = true
                    emfHandler.post {
                        Toast.makeText(this, R.string.emf_map_save_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    /** Ate a v1.8.1 o traco inteiro vivia num unico JSON reescrito a cada 3s. O
     * formato agora e um ponto por linha, onde cada linha e independente: um
     * processo morto no meio da caminhada deixa so a ultima linha de fora e o
     * resto do traco continua legivel. */
    private fun loadHotspots(): ArrayList<EmfHotspotMapView.Point> {
        migrateLegacyHotspots()
        val out = ArrayList<EmfHotspotMapView.Point>()
        var ilegiveis = 0
        try {
            hotspotFile().forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                try {
                    val o = JSONObject(line)
                    out.add(
                        EmfHotspotMapView.Point(
                            o.getDouble("lat"),
                            o.getDouble("lng"),
                            o.getDouble("mg").toFloat(),
                            o.getLong("t")
                        )
                    )
                } catch (_: Exception) {
                    ilegiveis++
                }
            }
        } catch (e: Exception) {
            Log.w("SpiritBox", "Falha ao ler hotspots", e)
        }
        if (ilegiveis > 0) Log.w("SpiritBox", "$ilegiveis linha(s) de hotspot ilegivel(is), ignorada(s)")
        return out
    }

    /** Converte o emf_hotspots.json antigo uma unica vez, para nao perder o traco
     * de quem ja gravou na v1.8.1. */
    private fun migrateLegacyHotspots() {
        val legacy = legacyHotspotFile()
        if (!legacy.exists()) return
        val antigos = ArrayList<EmfHotspotMapView.Point>()
        try {
            val arr = JSONObject(legacy.readText()).getJSONArray("points")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                antigos.add(
                    EmfHotspotMapView.Point(
                        o.getDouble("lat"),
                        o.getDouble("lng"),
                        o.getDouble("mg").toFloat(),
                        o.getLong("t")
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("SpiritBox", "Nao consegui ler o emf_hotspots.json antigo", e)
        }
        if (antigos.isNotEmpty()) {
            try {
                FileOutputStream(hotspotFile(), true).use { out ->
                    antigos.forEach { out.write((pointJson(it) + "\n").toByteArray()) }
                }
            } catch (e: Exception) {
                Log.w("SpiritBox", "Falha ao migrar hotspots", e)
            }
        }
        legacy.delete()
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
        // Os dois runnables do mapa se repostam sozinhos; sem remover aqui eles
        // continuavam de tempos em tempos segurando a Activity depois de
        // destruida.
        mapStatusTick?.let { emfHandler.removeCallbacks(it) }
        emfHandler.removeCallbacks(noSensorCheck)
        val mapUp = hotspotDialog?.isShowing == true
        val meterUp = emfDialog?.isShowing == true
        if (emfMeterLazy.isInitialized()) emfMeter.stopAll()
        // O teardown acima e incondicional, entao a flag precisa considerar o
        // mapa tambem: senao o onResume nao religaria o sensor e ele ficaria
        // morto ate o dialogo ser fechado e reaberto.
        emfTornDown = mapUp || meterUp || recordingLocation
        stopLocationUpdates()
        // Nao ha mais nada a gravar aqui: cada ponto ja foi para o arquivo em
        // append no pushHotspot, entao o onStop nao corre o risco de perder o
        // traco -- e nem deve reapender o ultimo ponto, que criaria duplicata.
    }

    override fun onDestroy() {
        super.onDestroy()
        // shutdown(), e nao shutdownNow(): os pontos que ja entraram na fila ainda
        // precisam ser gravados -- e um append de ~60 bytes, nao ha fila relevante.
        hotspotIo.shutdown()
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

    private fun batteryExempt(): Boolean {
        val pm = getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun warnBatteryIfRestricted() {
        if (batteryExempt()) return
        Toast.makeText(this, R.string.battery_not_exempt, Toast.LENGTH_LONG).show()
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (batteryExempt()) return
        if (openMiuiAutostart()) {
            pendingBatteryPrompt = true
        } else {
            openDozeSettings()
        }
        warnBatteryIfRestricted()
    }

    /** Autostart do MIUI/HyperOS não concede isenção de Doze; deixamos marcado para
     * oferecer a tela padrão quando o usuário voltar. */
    private fun openMiuiAutostart(): Boolean {
        val vendor = Build.MANUFACTURER.lowercase(Locale.US)
        val isMiui = vendor.contains("xiaomi") || vendor.contains("redmi") || vendor.contains("poco")
        if (!isMiui) return false
        val miui = Intent()
            .setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            )
            .putExtra("packageName", packageName)
        return runCatching { startActivity(miui) }.isSuccess
    }

    /** Isenção de Doze é o que mantém a varredura e o magnetômetro vivos com a tela
     * apagada. O app é distribuído por GitHub Releases (fora da Play Store), então a
     * restrição de política do lint não se aplica. */
    @SuppressLint("BatteryLife")
    private fun openDozeSettings() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingBatteryPrompt) {
            pendingBatteryPrompt = false
            if (!batteryExempt()) openDozeSettings()
        }
        if (!emfTornDown) return
        emfTornDown = false
        val mapUp = hotspotDialog?.isShowing == true
        val meterUp = emfDialog?.isShowing == true
        if (emfMeterLazy.isInitialized() && (mapUp || meterUp || recordingLocation)) {
            emfMeter.start()
        }
        if (meterUp && !emfRunning) {
            emfRunning = true
            emfHandler.post(emfRunnable)
        }
        if (recordingLocation) {
            locationFixSeen = false
            locationProviderError = null
            startLocationUpdates()
            emfHandler.postDelayed(noSensorCheck, 6000L)
        }
        if (mapUp) {
            mapStatusTick?.let {
                emfHandler.removeCallbacks(it)
                emfHandler.post(it)
            }
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