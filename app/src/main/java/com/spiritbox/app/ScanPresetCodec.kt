package com.spiritbox.app

/** Codifica/decodifica presets de varredura em JSON (sem org.json). */
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
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"version\": ").append(p.version).append(",\n")
        sb.append("  \"mode\": \"").append(escape(p.mode)).append("\",\n")
        sb.append("  \"server\": \"").append(escape(p.server)).append("\",\n")
        sb.append("  \"rangeIndex\": ").append(p.rangeIndex).append(",\n")
        sb.append("  \"dwell\": ").append(p.dwell).append(",\n")
        sb.append("  \"settle\": ").append(p.settle).append(",\n")
        sb.append("  \"gap\": ").append(p.gap).append(",\n")
        sb.append("  \"threshold\": ").append(p.threshold).append("\n")
        sb.append("}\n")
        return sb.toString()
    }

    fun parse(text: String): ParseResult {
        if (text.isBlank()) return ParseResult(null, "empty")
        val t = text.trim()
        if (!t.startsWith("{") || !t.endsWith("}")) return ParseResult(null, "bad")
        val map = HashMap<String, String>()
        var i = 1
        val len = t.length - 1
        while (i < len) {
            skipWs(t, i).also { i = it }
            if (i >= len || t[i] == ',') { if (t[i] == ',') i++; continue }
            if (t[i] != '"') { i++; continue }
            val keyRes = readString(t, i)
            val key = keyRes.first
            i = keyRes.second
            i = skipWs(t, i)
            if (i >= len || t[i] != ':') { i++; continue }
            i++
            i = skipWs(t, i)
            if (i >= len) break
            if (t[i] == '"') {
                val vRes = readString(t, i)
                map[key] = vRes.first
                i = vRes.second
            } else if (t[i] == '-' || (t[i] >= '0' && t[i] <= '9')) {
                var j = i
                if (t[j] == '-') j++
                while (j < len && t[j] >= '0' && t[j] <= '9') j++
                map[key] = t.substring(i, j)
                i = j
            } else {
                i++
            }
            i = skipWs(t, i)
            if (i < len && t[i] == ',') i++
        }
        val mode = map["mode"]?.trim().orEmpty()
        val server = map["server"]?.trim().orEmpty()
        if (mode.isBlank()) return ParseResult(null, "bad_mode")
        if (server.isBlank()) return ParseResult(null, "bad_server")
        val rangeIdx = map["rangeIndex"]?.toIntOrNull()
        val dwell = map["dwell"]?.toLongOrNull()
        val settle = map["settle"]?.toLongOrNull()
        val gap = map["gap"]?.toLongOrNull()
        val threshold = map["threshold"]?.toIntOrNull()
        if (rangeIdx == null || dwell == null || settle == null || gap == null || threshold == null) {
            return ParseResult(null, "missing")
        }
        val preset = Preset(
            version = map["version"]?.toIntOrNull() ?: 1,
            mode = mode,
            server = server,
            rangeIndex = rangeIdx.coerceAtLeast(0),
            dwell = if (dwell >= 50) dwell else 50L,
            settle = if (settle >= 0) settle else 0L,
            gap = if (gap >= 50) gap else 50L,
            threshold = threshold.coerceIn(0, 100)
        )
        return ParseResult(preset)
    }

    private fun skipWs(s: String, i: Int): Int {
        var j = i
        while (j < s.length) {
            val c = s[j]
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') j++ else break
        }
        return j
    }

    private fun readString(s: String, i: Int): Pair<String, Int> {
        if (i >= s.length || s[i] != '"') return Pair("", i + 1)
        val sb = StringBuilder()
        var j = i + 1
        while (j < s.length) {
            val c = s[j]
            if (c == '\\' && j + 1 < s.length) {
                val n = s[j + 1]
                when (n) {
                    '"', '\\', '/' -> sb.append(n)
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    else -> sb.append(n)
                }
                j += 2
                continue
            }
            if (c == '"') return Pair(sb.toString(), j + 1)
            sb.append(c)
            j++
        }
        return Pair(sb.toString(), j)
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
