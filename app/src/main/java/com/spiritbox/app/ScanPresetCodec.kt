package com.spiritbox.app

import org.json.JSONObject

/** Codifica/decodifica presets de varredura em JSON. */
object ScanPresetCodec {

    data class Preset(
        val version: Int = 1,
        val mode: String,
        val server: String,
        val rangeIndex: Int,
        val dwell: Long,
        val settle: Long,
        val gap: Long,
        val threshold: Int
    )

    data class ParseResult(val preset: Preset?, val error: String? = null)

    fun export(p: Preset): String {
        val o = JSONObject()
        o.put("version", p.version)
        o.put("mode", p.mode)
        o.put("server", p.server)
        o.put("rangeIndex", p.rangeIndex)
        o.put("dwell", p.dwell)
        o.put("settle", p.settle)
        o.put("gap", p.gap)
        o.put("threshold", p.threshold)
        return o.toString(2)
    }

    fun parse(text: String): ParseResult {
        if (text.isBlank()) return ParseResult(null, "empty")
        return try {
            val o = JSONObject(text.trim())
            val mode = o.optString("mode").ifBlank { return ParseResult(null, "bad_mode") }
            val server = o.optString("server").ifBlank { return ParseResult(null, "bad_server") }
            if (!o.has("rangeIndex")) return ParseResult(null, "bad_range")
            if (!o.has("dwell")) return ParseResult(null, "bad_dwell")
            if (!o.has("settle")) return ParseResult(null, "bad_settle")
            if (!o.has("gap")) return ParseResult(null, "bad_gap")
            if (!o.has("threshold")) return ParseResult(null, "bad_threshold")
            val preset = Preset(
                version = o.optInt("version", 1),
                mode = mode,
                server = server,
                rangeIndex = o.getInt("rangeIndex").coerceAtLeast(0),
                dwell = o.getLong("dwell").coerceAtLeast(50),
                settle = o.getLong("settle").coerceAtLeast(0),
                gap = o.getLong("gap").coerceAtLeast(50),
                threshold = o.getInt("threshold").coerceIn(0, 100)
            )
            ParseResult(preset)
        } catch (e: Exception) {
            ParseResult(null, "parse")
        }
    }
}
