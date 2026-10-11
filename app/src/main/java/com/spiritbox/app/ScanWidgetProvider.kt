package com.spiritbox.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Widget da tela inicial: mostra a frequência atual e um botão play/pause que
 * pausa ou retoma a varredura sem abrir o app. O botão reusa a ação ACTION_PAUSE
 * do serviço (mesma da notificação) e o corpo do widget abre o MainActivity.
 */
class ScanWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val running = Prefs.activeScan(context) != null
        val frozen = Prefs.paused(context) || SpiritBoxEvents.holdActive
        val views = build(context, running, frozen, 0.0)
        for (id in ids) manager.updateAppWidget(id, views)
    }

    companion object {
        /** Atualiza todos os widgets quando o serviço muda de estado ou de frequência. */
        fun refresh(context: Context, running: Boolean, paused: Boolean, freqKHz: Double) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ScanWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            val views = build(context, running, paused, freqKHz)
            for (id in ids) manager.updateAppWidget(id, views)
        }

        private fun build(
            context: Context,
            running: Boolean,
            paused: Boolean,
            freqKHz: Double
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_scan)
            val state = WidgetStatus.state(running, paused)
            views.setTextViewText(
                R.id.widgetFreq,
                if (freqKHz > 0) WidgetStatus.formatFreq(freqKHz) else "—"
            )
            views.setTextViewText(
                R.id.widgetStatus,
                context.getString(
                    when (state) {
                        WidgetStatus.State.STOPPED -> R.string.widget_status_stopped
                        WidgetStatus.State.PAUSED -> R.string.notif_paused
                        WidgetStatus.State.SCANNING -> R.string.notif_text_scanning
                    }
                )
            )
            val showPlay = state != WidgetStatus.State.SCANNING
            views.setImageViewResource(
                R.id.widgetPlayPause,
                if (showPlay) R.drawable.ic_play else R.drawable.ic_pause
            )
            views.setContentDescription(
                R.id.widgetPlayPause,
                context.getString(if (showPlay) R.string.btn_play else R.string.btn_pause)
            )
            views.setOnClickPendingIntent(R.id.widgetPlayPause, pauseIntent(context))
            views.setOnClickPendingIntent(R.id.widgetRoot, openIntent(context))
            return views
        }

        private fun pauseIntent(context: Context): PendingIntent {
            val i = Intent(context, SpiritBoxService::class.java)
                .setAction(SpiritBoxService.ACTION_PAUSE)
            return PendingIntent.getService(
                context, 20, i,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        private fun openIntent(context: Context): PendingIntent {
            val i = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context, 21, i,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
