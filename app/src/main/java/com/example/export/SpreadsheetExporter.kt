package com.example.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.DistanceUnit
import com.example.data.model.GeoUtils
import com.example.data.model.TripStop
import com.example.data.model.TripSummary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SpreadsheetExporter {
    private fun dateTimeFormat(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun buildTripLedgerCsv(
        trip: TripSummary,
        stops: List<TripStop>,
        unit: DistanceUnit
    ): String {
        val fmt = dateTimeFormat()
        val sb = StringBuilder()
        sb.appendLine("KALYE EXPEDITION LOG")
        sb.appendLine("Trip Title,Category,Start Time,End Time,Duration,Distance (${unit.shortLabel}),Elevation Gain (${if (unit == DistanceUnit.KM) "m" else "ft"}),Elevation Loss,Max Altitude,Fuel Liters,Fuel Cost,Ferry Fees,Other Costs,Total Cost,Currency,Notes")

        val distNum = GeoUtils.distanceNumber(trip.distanceMeters, unit)
        val gainVal = if (unit == DistanceUnit.KM) trip.elevationGainMeters else trip.elevationGainMeters * 3.28084
        val lossVal = if (unit == DistanceUnit.KM) trip.elevationLossMeters else trip.elevationLossMeters * 3.28084
        val maxAlt = if (unit == DistanceUnit.KM) trip.maxElevationMeters else trip.maxElevationMeters * 3.28084

        sb.appendLine(
            listOf(
                trip.title,
                trip.categoryTag,
                fmt.format(Date(trip.startTimeMillis)),
                fmt.format(Date(trip.endTimeMillis)),
                GeoUtils.formatDuration(trip.durationMillis),
                distNum,
                String.format(Locale.US, "%.1f", gainVal),
                String.format(Locale.US, "%.1f", lossVal),
                String.format(Locale.US, "%.1f", maxAlt),
                String.format(Locale.US, "%.2f", trip.totalFuelLiters),
                String.format(Locale.US, "%.2f", trip.totalFuelCost),
                String.format(Locale.US, "%.2f", trip.totalFerryCost),
                String.format(Locale.US, "%.2f", trip.totalOtherCost),
                String.format(Locale.US, "%.2f", trip.totalCost),
                trip.currencyCode,
                trip.notes
            ).joinToString(",") { csvEscape(it) }
        )

        sb.appendLine()
        sb.appendLine("ITEMIZED STOPS, FUEL & FERRY EXPENSES")
        sb.appendLine("Timestamp,Type,Location / Title,Note,Latitude,Longitude,Elevation (m),Fuel Volume (L),Price / L,Amount,Currency")
        stops.forEach { stop ->
            sb.appendLine(
                listOf(
                    fmt.format(Date(stop.timestampMillis)),
                    stop.stopType.label,
                    stop.title,
                    stop.note,
                    String.format(Locale.US, "%.5f", stop.latitude),
                    String.format(Locale.US, "%.5f", stop.longitude),
                    String.format(Locale.US, "%.1f", stop.elevationMeters),
                    String.format(Locale.US, "%.2f", stop.fuelLiters),
                    String.format(Locale.US, "%.2f", stop.pricePerLiter),
                    String.format(Locale.US, "%.2f", stop.costAmount),
                    stop.currencyCode
                ).joinToString(",") { csvEscape(it) }
            )
        }
        return sb.toString()
    }

    fun buildAllTripsCsv(
        trips: List<TripSummary>,
        allStops: List<TripStop>,
        unit: DistanceUnit
    ): String {
        val fmt = dateTimeFormat()
        val sb = StringBuilder()
        sb.appendLine("Trip ID,Trip Title,Category,Date,Duration,Distance (${unit.shortLabel}),Elevation Gain (m),Fuel Liters,Fuel Cost,Ferry Cost,Other Cost,Total Cost,Currency,Stops Count")
        trips.forEach { trip ->
            val stopCount = allStops.count { it.tripId == trip.id }
            sb.appendLine(
                listOf(
                    trip.id,
                    trip.title,
                    trip.categoryTag,
                    fmt.format(Date(trip.startTimeMillis)),
                    GeoUtils.formatDuration(trip.durationMillis),
                    GeoUtils.distanceNumber(trip.distanceMeters, unit),
                    String.format(Locale.US, "%.1f", trip.elevationGainMeters),
                    String.format(Locale.US, "%.2f", trip.totalFuelLiters),
                    String.format(Locale.US, "%.2f", trip.totalFuelCost),
                    String.format(Locale.US, "%.2f", trip.totalFerryCost),
                    String.format(Locale.US, "%.2f", trip.totalOtherCost),
                    String.format(Locale.US, "%.2f", trip.totalCost),
                    trip.currencyCode,
                    stopCount.toString()
                ).joinToString(",") { csvEscape(it) }
            )
        }
        return sb.toString()
    }

    fun exportAndShareCsv(
        context: Context,
        filenameBase: String,
        csvContent: String
    ): Uri {
        val dir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val safeName = filenameBase.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(32).ifBlank { "kalye_log" }
        val file = File(dir, "${safeName}.csv")
        file.writeText(csvContent, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Kalye Overland Spreadsheet ($safeName)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export Spreadsheet (CSV)"))
        return uri
    }

    private fun csvEscape(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n')
        return if (needsQuotes) "\"${value.replace("\"", "\"\"")}\"" else value
    }
}
