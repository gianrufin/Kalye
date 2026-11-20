package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Kalye", appName)
  }

  @Test
  fun `render roadmap share card bitmap across all map themes`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val trip = com.example.data.model.TripSummary(
      id = "t_map",
      userId = "u1",
      title = "Cordillera Overland",
      categoryTag = "Overland",
      notes = "",
      startTimeMillis = 1_760_000_000_000L,
      endTimeMillis = 1_760_012_745_000L,
      distanceMeters = 124_800.0,
      elevationGainMeters = 1485.0,
      elevationLossMeters = 1335.0,
      maxElevationMeters = 2248.0,
      minElevationMeters = 1465.0,
      maxSpeedKmh = 78.4,
      totalFuelCost = 2480.0,
      totalFerryCost = 0.0,
      totalOtherCost = 0.0,
      totalFuelLiters = 38.2,
      currencyCode = "PHP",
      routePolyline = "",
      pointCount = 3,
      vehicleId = "v1",
      vehicleName = "Olive 4x4 SUV",
      movingDurationMillis = 12_745_000L
    )
    val pts = listOf(
      com.example.data.model.RoutePoint(16.4023, 120.5960, 1465.0),
      com.example.data.model.RoutePoint(16.4940, 120.6710, 2110.0),
      com.example.data.model.RoutePoint(16.5980, 120.7380, 1615.0)
    )
    com.example.data.model.ShareCardInkStyle.entries.forEach { ink ->
      val bmp = com.example.export.ShareCardRenderer.renderToBitmap(
        context = context,
        trip = trip,
        points = pts,
        config = com.example.export.ShareCardConfig(
          layout = com.example.data.model.ShareCardLayout.ROADMAP_CARD,
          inkStyle = ink,
          distanceUnit = com.example.data.model.DistanceUnit.KM
        ),
        width = 720,
        height = 900
      )
      assertEquals(720, bmp.width)
      assertEquals(900, bmp.height)
    }
  }
}
