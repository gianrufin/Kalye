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

    @Test
    fun `share card filename includes distinct date and time`() {
        val t1 = 1700000000000L
        val t2 = 1700000065000L
        val f1 = com.example.export.ShareCardRenderer.buildExportFilename(t1)
        val f2 = com.example.export.ShareCardRenderer.buildExportFilename(t2)
        assertTrue(Regex("^kalye_\\d{8}_\\d{6}\\.png$").matches(f1))
        assertTrue(f1 != f2)
    }

    @Test
    fun `theme mode only exposes light and dark and maps legacy system to light`() {
        val entries = com.example.data.model.ThemeMode.entries
        assertEquals(2, entries.size)
        assertEquals(com.example.data.model.ThemeMode.LIGHT, com.example.data.model.ThemeMode.safeFrom("SYSTEM"))
        assertEquals(com.example.data.model.ThemeMode.DARK, com.example.data.model.ThemeMode.safeFrom("DARK"))
    }

    @Test
    fun `moving time formats hh_mm_ss and mm_ss cleanly`() {
        assertEquals("08:05", GeoUtils.formatMovingTime(485_000L))
        assertEquals("2:11:26", GeoUtils.formatMovingTime(7_886_000L))
    }

    @Test
    fun `vehicle garage defaults start empty without preset cars`() {
        val presets = com.example.data.model.VehicleGarageDefaults.presets
        assertTrue(presets.isEmpty())
    }

    @Test
    fun `map layer type collapses legacy topo into minimal street`() {
        assertEquals(com.example.data.model.MapLayerType.STREET, com.example.data.model.MapLayerType.safeFrom("TOPO"))
        assertEquals(com.example.data.model.MapLayerType.STREET, com.example.data.model.MapLayerType.safeFrom("STREET"))
        assertEquals(com.example.data.model.MapLayerType.OFFLINE, com.example.data.model.MapLayerType.safeFrom("OFFLINE"))
    }

    @Test
    fun `auto pause evaluator pauses after 25s stationary and resumes on movement`() {
        val t0 = 1_000_000L
        val moving = com.example.data.model.AutoPauseEvaluator.evaluate(
            currentMovingMillis = 10_000L,
            wasAutoPaused = false,
            lastMovementTimeMillis = t0 - 2_000L,
            lastSampleTimeMillis = t0 - 2_000L,
            deltaDistanceMeters = 18.0,
            speedMps = 9.0f,
            sampleTimeMillis = t0
        )
        assertEquals(false, moving.isAutoPaused)
        assertEquals(12_000L, moving.movingDurationMillis)

        // Stationary for 26s -> triggers auto-pause and stops accumulating moving duration
        val stopped = com.example.data.model.AutoPauseEvaluator.evaluate(
            currentMovingMillis = 12_000L,
            wasAutoPaused = false,
            lastMovementTimeMillis = t0,
            lastSampleTimeMillis = t0 + 24_000L,
            deltaDistanceMeters = 0.4,
            speedMps = 0.2f,
            sampleTimeMillis = t0 + 26_000L
        )
        assertEquals(true, stopped.isAutoPaused)
        assertEquals(12_000L, stopped.movingDurationMillis)

        // Speed >= 1.2 m/s resumes tracking
        val resumed = com.example.data.model.AutoPauseEvaluator.evaluate(
            currentMovingMillis = 12_000L,
            wasAutoPaused = true,
            lastMovementTimeMillis = t0,
            lastSampleTimeMillis = t0 + 26_000L,
            deltaDistanceMeters = 5.0,
            speedMps = 2.5f,
            sampleTimeMillis = t0 + 28_000L
        )
        assertEquals(false, resumed.isAutoPaused)
        assertEquals(14_000L, resumed.movingDurationMillis)
    }

    @Test
    fun `odometer fuel efficiency and cost per distance format accurately`() {
        assertEquals("12,450 km", GeoUtils.formatOdometer(12450.0, DistanceUnit.KM))
        assertEquals("12.5 km/L", GeoUtils.formatFuelEfficiency(125_000.0, 10.0, DistanceUnit.KM))
        assertEquals("29.4 mpg", GeoUtils.formatFuelEfficiency(125_000.0, 10.0, DistanceUnit.MI))
        assertEquals("₱6.00/km", GeoUtils.formatCostPerDistance(600.0, 100_000.0, "PHP", DistanceUnit.KM))

        val tripWithMoving = TripSummary(
            id = "t_mov",
            userId = "u1",
            title = "City Loop",
            categoryTag = "Overland",
            notes = "",
            startTimeMillis = 1000L,
            endTimeMillis = 61_000L,
            distanceMeters = 5000.0,
            elevationGainMeters = 20.0,
            elevationLossMeters = 10.0,
            maxElevationMeters = 100.0,
            minElevationMeters = 80.0,
            maxSpeedKmh = 40.0,
            totalFuelCost = 0.0,
            totalFerryCost = 0.0,
            totalOtherCost = 0.0,
            totalFuelLiters = 0.0,
            currencyCode = "PHP",
            routePolyline = "",
            pointCount = 10,
            movingDurationMillis = 35_000L
        )
        assertEquals(35_000L, tripWithMoving.effectiveMovingMillis)
    }

    @Test
    fun `semver parsing and comparison order releases accurately`() {
        val v101 = com.example.update.SemVer.parse("v1.0.1")!!
        val v102 = com.example.update.SemVer.parse("1.0.2")!!
        val v1010 = com.example.update.SemVer.parse("v1.0.10")!!
        val v110 = com.example.update.SemVer.parse("1.1.0-rc1")!!

        assertTrue(v102 > v101)
        assertTrue(v1010 > v102)
        assertTrue(v110 > v1010)
        assertEquals("1.0.1", v101.toString())
    }

    @Test
    fun `app update checker parses github release JSON and extracts APK asset`() {
        val checker = com.example.update.AppUpdateChecker(
            repoOwner = "gianrufin",
            repoName = "Kalye",
            currentVersionProvider = { "1.0.1" }
        )
        val payload = """
            {
              "tag_name": "v1.0.2",
              "name": "Kalye v1.0.2",
              "body": "Added stealth HUD and auto-pause.",
              "draft": false,
              "prerelease": false,
              "published_at": "2026-11-19T12:00:00Z",
              "html_url": "https://github.com/gianrufin/Kalye/releases/tag/v1.0.2",
              "assets": [
                {
                  "name": "kalye-v1.0.2.apk",
                  "size": 15728640,
                  "browser_download_url": "https://github.com/gianrufin/Kalye/releases/download/v1.0.2/kalye-v1.0.2.apk"
                }
              ]
            }
        """.trimIndent()

        val parsed = checker.parseSingleReleaseJson(payload)!!
        assertEquals("v1.0.2", parsed.tagName)
        assertEquals("1.0.2", parsed.versionName)
        assertEquals("kalye-v1.0.2.apk", parsed.apkAssetName)
        assertEquals(
            "https://github.com/gianrufin/Kalye/releases/download/v1.0.2/kalye-v1.0.2.apk",
            parsed.apkDownloadUrl
        )
        assertTrue(com.example.update.SemVer.parse(parsed.versionName)!! > com.example.update.SemVer.parse("1.0.1")!!)
    }

    @Test
    fun `free map tile URLs require no API keys and return OSM endpoints`() {
        val urls = com.example.maps.OfflineTileCacheManager.buildFreeTileUrls(z = 12, x = 100, y = 200)
        assertTrue(urls.isNotEmpty())
        assertTrue(urls.first().startsWith("https://tile.openstreetmap.org/12/100/200.png"))
        assertTrue(urls.none { it.contains("api_key", ignoreCase = true) || it.contains("key=", ignoreCase = true) })
    }

    @Test
    fun `live moving timer ticks per second when active and freezes when paused`() {
        val baseTrip = TripSummary(
            id = "t_live",
            userId = "u1",
            title = "Cordillera Pass",
            categoryTag = "Overland",
            notes = "",
            startTimeMillis = 10_000L,
            endTimeMillis = 20_000L,
            distanceMeters = 500.0,
            elevationGainMeters = 10.0,
            elevationLossMeters = 0.0,
            maxElevationMeters = 100.0,
            minElevationMeters = 90.0,
            maxSpeedKmh = 35.0,
            totalFuelCost = 0.0,
            totalFerryCost = 0.0,
            totalOtherCost = 0.0,
            totalFuelLiters = 0.0,
            currencyCode = "PHP",
            routePolyline = "",
            pointCount = 3,
            movingDurationMillis = 10_000L,
            isActive = true,
            isPaused = false,
            isAutoPaused = false
        )

        assertEquals(13_000L, GeoUtils.computeLiveMovingMillis(baseTrip, nowMillis = 23_000L))
        assertEquals(17_000L, GeoUtils.computeLiveMovingMillis(baseTrip, nowMillis = 27_000L))

        val paused = baseTrip.copy(isPaused = true)
        assertEquals(10_000L, GeoUtils.computeLiveMovingMillis(paused, nowMillis = 27_000L))

        val autoPaused = baseTrip.copy(isAutoPaused = true)
        assertEquals(10_000L, GeoUtils.computeLiveMovingMillis(autoPaused, nowMillis = 27_000L))
    }

    @Test
    fun `altitude stabilizer rejects spikes and hysteresis eliminates flat-road noise`() {
        val stabilizer = com.example.data.model.AltitudeStabilizer()
        val s1 = stabilizer.process(100.0, verticalAccuracyMeters = 4f, timestampMillis = 1_000L)!!
        val s2 = stabilizer.process(101.0, verticalAccuracyMeters = 4f, timestampMillis = 3_000L)!!
        // Isolated 45m GPS spike with coarse vertical accuracy must be rejected
        val spikeRejected = stabilizer.process(146.0, verticalAccuracyMeters = 18f, timestampMillis = 5_000L)!!
        assertEquals(s2, spikeRejected, 0.001)
        assertTrue(s1 in 99.0..102.0)

        // Small jitter (< 2.5m) on flat terrain shouldn't add phantom climb, while real 12m climb accumulates
        val flatJitter = listOf(
            RoutePoint(14.5, 121.0, 50.0),
            RoutePoint(14.501, 121.001, 51.4),
            RoutePoint(14.502, 121.002, 49.8),
            RoutePoint(14.503, 121.003, 51.2),
            RoutePoint(14.504, 121.004, 50.1)
        )
        val flatStats = GeoUtils.computeElevationStats(flatJitter)
        assertEquals(0.0, flatStats.gainMeters, 0.01)

        val climb = listOf(
            RoutePoint(14.5, 121.0, 100.0),
            RoutePoint(14.501, 121.001, 106.0),
            RoutePoint(14.502, 121.002, 114.0),
            RoutePoint(14.503, 121.003, 111.0)
        )
        val climbStats = GeoUtils.computeElevationStats(climb)
        assertEquals(14.0, climbStats.gainMeters, 0.01)
        assertEquals(3.0, climbStats.lossMeters, 0.01)
        assertEquals(100.0, climbStats.minMeters, 0.01)
        assertEquals(114.0, climbStats.maxMeters, 0.01)
    }

    @Test
    fun `vehicle body styles include expanded classes and rig avatar models parse safely`() {
        val styles = com.example.data.model.VehicleBodyStyle.entries
        assertEquals(8, styles.size)
        assertTrue(styles.contains(com.example.data.model.VehicleBodyStyle.CROSSOVER))
        assertTrue(styles.contains(com.example.data.model.VehicleBodyStyle.HATCHBACK))
        assertTrue(styles.contains(com.example.data.model.VehicleBodyStyle.COUPE))
        assertTrue(styles.contains(com.example.data.model.VehicleBodyStyle.PICKUP))
        assertTrue(styles.contains(com.example.data.model.VehicleBodyStyle.SUV_4X4))
        assertTrue(styles.contains(com.example.data.model.VehicleBodyStyle.SEDAN))
        assertTrue(styles.contains(com.example.data.model.VehicleBodyStyle.VAN))
        assertTrue(styles.contains(com.example.data.model.VehicleBodyStyle.MOTO))

        assertEquals(
            com.example.data.model.VehicleRigGear.EXPEDITION,
            com.example.data.model.VehicleRigGear.safeFrom("EXPEDITION")
        )
        assertEquals(
            com.example.data.model.RigAvatarBackdrop.ALPINE_PINE,
            com.example.data.model.RigAvatarBackdrop.safeFrom("UNKNOWN_SCENE")
        )
    }

    @Test
    fun `roadmap share card computes full-route viewport covering all points and stops`() {
        assertTrue(com.example.data.model.ShareCardLayout.entries.contains(com.example.data.model.ShareCardLayout.ROADMAP_CARD))
        val pts = listOf(
            RoutePoint(16.4023, 120.5960, 1465.0),
            RoutePoint(16.4940, 120.6710, 2110.0),
            RoutePoint(16.5980, 120.7380, 1615.0)
        )
        val stop = TripStop(
            id = "s1",
            userId = "u1",
            tripId = "t1",
            stopType = StopType.FUEL,
            title = "Atok",
            note = "",
            latitude = 16.4940,
            longitude = 120.6710,
            elevationMeters = 2110.0,
            costAmount = 1000.0,
            fuelLiters = 15.0,
            pricePerLiter = 66.6,
            currencyCode = "PHP",
            timestampMillis = 1000L
        )
        val viewport = com.example.export.ShareCardRenderer.computeRoadmapViewport(
            points = pts,
            stops = listOf(stop),
            width = 1080,
            height = 1350
        )
        assertTrue(viewport.intZoom in 4..15)
        assertTrue(viewport.visibleTiles.isNotEmpty())
        pts.forEach { p ->
            val (px, py) = viewport.project(p.latitude, p.longitude)
            assertTrue(px in 40f..1040f)
            assertTrue(py in 120f..980f)
        }
    }
}
