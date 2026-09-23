package com.spiritbox.app

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "spiritbox_prefs"

    private const val KEY_SERVER = "server"
    private const val KEY_RANGE = "rangeIndex"
    private const val KEY_DWELL = "dwell"
    private const val KEY_FM = "fmMode"
    private const val KEY_THRESHOLD = "thresholdPct"
    private const val KEY_SETTLE = "settle"
    private const val KEY_GAP = "gap"
    private const val KEY_ALERTS = "alerts"

    private const val KEY_SCAN_ACTIVE = "scanActive"
    private const val KEY_SCAN_MODE = "scanMode"
    private const val KEY_SCAN_SERVER = "scanServer"
    private const val KEY_SCAN_RANGE = "scanRange"
    private const val KEY_SCAN_DWELL = "scanDwell"
    private const val KEY_SCAN_THRESHOLD = "scanThreshold"
    private const val KEY_SCAN_SETTLE = "scanSettle"
    private const val KEY_SCAN_GAP = "scanGap"

    private fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun server(c: Context): String =
        prefs(c).getString(KEY_SERVER, SpiritBoxService.DEFAULT_SERVER)
            ?: SpiritBoxService.DEFAULT_SERVER

    fun saveServer(c: Context, v: String) =
        prefs(c).edit().putString(KEY_SERVER, v).apply()

    fun rangeIndex(c: Context): Int = prefs(c).getInt(KEY_RANGE, 1)

    fun saveRangeIndex(c: Context, v: Int) =
        prefs(c).edit().putInt(KEY_RANGE, v).apply()

    fun dwell(c: Context): Long = prefs(c).getLong(KEY_DWELL, 300L)

    fun saveDwell(c: Context, v: Long) =
        prefs(c).edit().putLong(KEY_DWELL, v).apply()

    fun fmMode(c: Context): Boolean = prefs(c).getBoolean(KEY_FM, false)

    fun saveFmMode(c: Context, v: Boolean) =
        prefs(c).edit().putBoolean(KEY_FM, v).apply()

    fun thresholdPct(c: Context): Int = prefs(c).getInt(KEY_THRESHOLD, 12)

    fun saveThreshold(c: Context, v: Int) =
        prefs(c).edit().putInt(KEY_THRESHOLD, v).apply()

    fun settle(c: Context): Long = prefs(c).getLong(KEY_SETTLE, 150L)

    fun saveSettle(c: Context, v: Long) =
        prefs(c).edit().putLong(KEY_SETTLE, v).apply()

    fun gap(c: Context): Long = prefs(c).getLong(KEY_GAP, 300L)

    fun saveGap(c: Context, v: Long) =
        prefs(c).edit().putLong(KEY_GAP, v).apply()

    fun alerts(c: Context): Boolean = prefs(c).getBoolean(KEY_ALERTS, false)

    fun saveAlerts(c: Context, v: Boolean) =
        prefs(c).edit().putBoolean(KEY_ALERTS, v).apply()

    data class ScanState(
        val mode: String,
        val server: String,
        val rangeIndex: Int,
        val dwell: Long,
        val thresholdPct: Int,
        val settle: Long,
        val gap: Long
    )

    fun saveActiveScan(c: Context, s: ScanState) =
        prefs(c).edit()
            .putBoolean(KEY_SCAN_ACTIVE, true)
            .putString(KEY_SCAN_MODE, s.mode)
            .putString(KEY_SCAN_SERVER, s.server)
            .putInt(KEY_SCAN_RANGE, s.rangeIndex)
            .putLong(KEY_SCAN_DWELL, s.dwell)
            .putInt(KEY_SCAN_THRESHOLD, s.thresholdPct)
            .putLong(KEY_SCAN_SETTLE, s.settle)
            .putLong(KEY_SCAN_GAP, s.gap)
            .apply()

    fun clearActiveScan(c: Context) =
        prefs(c).edit().putBoolean(KEY_SCAN_ACTIVE, false).apply()

    fun activeScan(c: Context): ScanState? {
        val p = prefs(c)
        if (!p.getBoolean(KEY_SCAN_ACTIVE, false)) return null
        return ScanState(
            p.getString(KEY_SCAN_MODE, SpiritBoxService.MODE_SDR)
                ?: SpiritBoxService.MODE_SDR,
            p.getString(KEY_SCAN_SERVER, SpiritBoxService.DEFAULT_SERVER)
                ?: SpiritBoxService.DEFAULT_SERVER,
            p.getInt(KEY_SCAN_RANGE, 0).coerceAtLeast(0),
            p.getLong(KEY_SCAN_DWELL, 300L),
            p.getInt(KEY_SCAN_THRESHOLD, 12),
            p.getLong(KEY_SCAN_SETTLE, 150L),
            p.getLong(KEY_SCAN_GAP, 300L)
        )
    }
}