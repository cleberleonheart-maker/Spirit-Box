package com.spiritbox.app

import java.util.Locale

/** Estado mostrado no widget da tela inicial. Puro, para ser testado. */
object WidgetStatus {

    enum class State { STOPPED, PAUSED, SCANNING }

    fun state(running: Boolean, paused: Boolean): State = when {
        !running -> State.STOPPED
        paused -> State.PAUSED
        else -> State.SCANNING
    }

    /** Mesma formatação usada no app e na notificação. */
    fun formatFreq(khz: Double): String =
        if (khz >= 1000) {
            String.format(Locale.US, "%.3f MHz", khz / 1000)
        } else {
            "${khz.toInt()} kHz"
        }
}
