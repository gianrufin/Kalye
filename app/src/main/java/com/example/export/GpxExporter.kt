package com.example.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.RoutePoint
import com.example.data.model.TripStop
import com.example.data.model.TripSummary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxExporter {
    private fun isoFormat(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    fun buildGpxXml(
        trip: TripSummary,
        points: List<RoutePoint>,
        stops: List<TripStop>
    ): String {
        val fmt = isoFormat()
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="Kalye Overland Tracker" xmlns="http://www.topografix.com/GPX/1/1">""")
        sb.appendLine("  <metadata>")
        sb.appendLine("    <name>${escapeXml(trip.title)}</name>")
        sb.appendLine("    <desc>${escapeXml("${trip.categoryTag} | ${trip.notes}".trim())}</desc>")
        sb.appendLine("    <time>${fmt.format(Date(trip.startTimeMillis))}</time>")
        sb.appendLine("  </metadata>")

        stops.forEach { stop ->
            sb.appendLine("""  <wpt lat="${stop.latitude}" lon="${stop.longitude}">""")
            sb.appendLine("    <ele>${String.format(Locale.US, "%.1f", stop.elevationMeters)}</ele>")
            sb.appendLine("    <time>${fmt.format(Date(stop.timestampMillis))}</time>")
            sb.appendLine("    <name>${escapeXml(stop.title)}</name>")
            sb.appendLine("    <type>${escapeXml(stop.stopType.label)}</type>")
            if (stop.note.isNotBlank() || stop.costAmount > 0) {
                val desc = "${stop.note} Cost: ${stop.costAmount} ${stop.currencyCode}".trim()
                sb.appendLine("    <desc>${escapeXml(desc)}</desc>")
            }
            sb.appendLine("  </wpt>")
        }

        sb.appendLine("  <trk>")
        sb.appendLine("    <name>${escapeXml(trip.title)}</name>")
        sb.appendLine("    <type>${escapeXml(trip.categoryTag)}</type>")
        sb.appendLine("    <trkseg>")
        points.forEach { pt ->
            sb.appendLine("""      <trkpt lat="${pt.latitude}" lon="${pt.longitude}">""")
            sb.appendLine("        <ele>${String.format(Locale.US, "%.1f", pt.altitudeMeters)}</ele>")
            sb.appendLine("        <time>${fmt.format(Date(pt.timestampMillis))}</time>")
            sb.appendLine("      </trkpt>")
        }
        sb.appendLine("    </trkseg>")
        sb.appendLine("  </trk>")
        sb.appendLine("</gpx>")
        return sb.toString()
    }

    fun exportAndShare(
        context: Context,
        trip: TripSummary,
        points: List<RoutePoint>,
        stops: List<TripStop>
    ): Uri {
        val dir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val safeName = trip.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(32).ifBlank { "kalye_ride" }
        val file = File(dir, "${safeName}.gpx")
        file.writeText(buildGpxXml(trip, points, stops), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${trip.title} (GPX Track)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export GPX Track"))
        return uri
    }

    private fun escapeXml(input: String): String =
        input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
