package com.example

import com.example.data.model.DistanceUnit
import com.example.data.model.GeoUtils
import com.example.data.model.RoutePoint
import com.example.data.model.StopType
import com.example.data.model.TripStop
import com.example.data.model.TripSummary
import com.example.export.GpxExporter
import com.example.export.SpreadsheetExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KalyeUnitTest {
    @Test
    fun `haversine and polyline roundtrip work accurately`() {
        val pts = listOf(
            RoutePoint(16.4023, 120.5960, 1500.0, timestampMillis = 1000L),
            RoutePoint(16.4123, 120.6060, 1620.0, timestampMillis = 2000L)
        )
        val dist = GeoUtils.haversineMeters(pts[0].latitude, pts[0].longitude, pts[1].latitude, pts[1].longitude)
        assertTrue(dist > 1400.0 && dist < 1700.0)

        val encoded = GeoUtils.encodeRoutePoints(pts)
        val decoded = GeoUtils.decodeRoutePoints(encoded)
        assertEquals(2, decoded.size)
        assertEquals(16.4023, decoded[0].latitude, 0.001)
        assertEquals(1620.0, decoded[1].altitudeMeters, 0.5)
    }

    @Test
    fun `gpx exporter produces valid GPX 1_1 XML with waypoints and trackpoints`() {
        val trip = TripSummary(
            id = "t1",
            userId = "u1",
            title = "Cordillera Ridge",
            categoryTag = "Overland",
            notes = "Clear skies",
            startTimeMillis = 1700000000000L,
            endTimeMillis = 1700003600000L,
            distanceMeters = 12500.0,
            elevationGainMeters = 450.0,
            elevationLossMeters = 120.0,
            maxElevationMeters = 1900.0,
            minElevationMeters = 1450.0,
            maxSpeedKmh = 64.0,
            totalFuelCost = 1200.0,
            totalFerryCost = 0.0,
            totalOtherCost = 0.0,
            totalFuelLiters = 18.0,
            currencyCode = "PHP",
            routePolyline = "",
            pointCount = 2
        )
        val pts = listOf(
            RoutePoint(16.4023, 120.5960, 1500.0, timestampMillis = 1700000000000L),
            RoutePoint(16.4500, 120.6200, 1850.0, timestampMillis = 1700003600000L)
        )
        val stops = listOf(
            TripStop(
                id = "s1",
                userId = "u1",
                tripId = "t1",
                stopType = StopType.FUEL,
                title = "Atok Fuel",
                note = "Diesel",
                latitude = 16.42,
                longitude = 120.61,
                elevationMeters = 1700.0,
                costAmount = 1200.0,
                fuelLiters = 18.0,
                pricePerLiter = 66.67,
                currencyCode = "PHP",
                timestampMillis = 1700001800000L
            )
        )

        val gpx = GpxExporter.buildGpxXml(trip, pts, stops)
        assertTrue(gpx.contains("""<gpx version="1.1""""))
        assertTrue(gpx.contains("<wpt lat=\"16.42\" lon=\"120.61\">"))
        assertTrue(gpx.contains("<trkpt lat=\"16.45\" lon=\"120.62\">"))
    }

    @Test
    fun `spreadsheet exporter escapes commas and computes csv ledger`() {
        val trip = TripSummary(
            id = "t1",
            userId = "u1",
            title = "Batangas, Calapan Ferry Run",
            categoryTag = "Coastal Ferry",
            notes = "Calm sea",
            startTimeMillis = 1700000000000L,
            endTimeMillis = 1700007200000L,
            distanceMeters = 52000.0,
            elevationGainMeters = 200.0,
            elevationLossMeters = 180.0,
            maxElevationMeters = 210.0,
            minElevationMeters = 5.0,
            maxSpeedKmh = 70.0,
            totalFuelCost = 1000.0,
            totalFerryCost = 2800.0,
            totalOtherCost = 0.0,
            totalFuelLiters = 15.0,
            currencyCode = "PHP",
            routePolyline = "",
            pointCount = 0
        )
        val csv = SpreadsheetExporter.buildTripLedgerCsv(trip, emptyList(), DistanceUnit.KM)
        assertTrue(csv.contains("\"Batangas, Calapan Ferry Run\""))
        assertTrue(csv.contains("2800.00"))
    }
}
