package com.spiritbox.app

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.spiritbox.app.radio.SweepEngine

@RequiresApi(Build.VERSION_CODES.N)
class ScanTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (SpiritBoxEvents.serviceRunning) {
            SpiritBoxService.stop(this)
        } else {
            val server = Prefs.server(this)
            val rangeIndex = Prefs.rangeIndex(this).coerceIn(0, SweepEngine.PRESETS.size - 1)
            val range = SweepEngine.PRESETS[rangeIndex]
            val dwell = Prefs.dwell(this)
            val settle = Prefs.settle(this)
            val threshold = Prefs.thresholdPct(this)
            val gap = Prefs.gap(this)
            val mode = if (Prefs.fmMode(this)) SpiritBoxService.MODE_FM else SpiritBoxService.MODE_SDR
            SpiritBoxService.start(this, mode, server, range, dwell, settle, threshold, gap)
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        if (SpiritBoxEvents.serviceRunning) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = getString(R.string.app_name)
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.app_name)
        }
        tile.updateTile()
    }
}
