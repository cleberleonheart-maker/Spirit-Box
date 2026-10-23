package com.spiritbox.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Gera o trajeto de hotspots magnéticos como GPX 1.1 (trk/trkpt). */
object GpxExport {

    data class Point(val lat: Double, val lng: Double, val mg: Float, val time: Long)

    fun build(points: List<Point>, name: String = "SpiritBox EMF"): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"SpiritBox\" ")
            .append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <trk>\n")
        sb.append("    <name>").append(escape(name)).append("</name>\n")
        sb.append("    <trkseg>\n")
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        for (p in points) {
            sb.append("      <trkpt lat=\"").append(fmtNum(p.lat))
                .append("\" lon=\"").append(fmtNum(p.lng)).append("\">\n")
            sb.append("        <time>").append(fmt.format(Date(p.time))).append("</time>\n")
            sb.append("        <extensions><mg>").append(fmtNum(p.mg.toDouble()))
                .append("</mg></extensions>\n")
            sb.append("      </trkpt>\n")
        }
        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")
        return sb.toString()
    }

    /** Coordenadas com 6 casas (~10 cm) e sem zeros a direita. */
    fun fmtNum(v: Double): String =
        String.format(Locale.US, "%.6f", v).trimEnd('0').trimEnd('.')

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
