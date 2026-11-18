package com.example.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.data.local.KalyeDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.model.GeoUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

data class DailyProgressSummary(
    val distanceLabel: String,
    val elevationLabel: String,
    val spendLabel: String,
    val rideCount: Int,
    val hasActiveRide: Boolean
)

class KalyeDailyWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val summary = withContext(Dispatchers.IO) {
            loadDailySummary(context)
        }
        provideContent {
            GlanceTheme {
                DailyWidgetContent(summary)
            }
        }
    }

    private suspend fun loadDailySummary(context: Context): DailyProgressSummary {
        val prefs = PreferencesRepository(context).preferencesFlow.first()
        val uid = try {
            Firebase.auth.currentUser?.uid
        } catch (_: Exception) {
            null
        }
        if (uid.isNullOrBlank()) {
            return DailyProgressSummary(
                distanceLabel = "0.00 ${prefs.distanceUnit.shortLabel}",
                elevationLabel = "0 m",
                spendLabel = GeoUtils.formatCurrency(0.0, prefs.currencyCode),
                rideCount = 0,
                hasActiveRide = false
            )
        }
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dao = KalyeDatabase.getInstance(context).kalyeDao()
        val todayTrips = dao.getTodayTripsSnapshot(uid, cal.timeInMillis)
        val totalDistance = todayTrips.sumOf { it.distanceMeters }
        val totalGain = todayTrips.sumOf { it.elevationGainMeters }
        val totalSpend = todayTrips.sumOf { it.totalFuelCost + it.totalFerryCost + it.totalOtherCost }
        val hasActive = todayTrips.any { it.activeFlag }
        return DailyProgressSummary(
            distanceLabel = GeoUtils.formatDistance(totalDistance, prefs.distanceUnit),
            elevationLabel = "+${GeoUtils.formatElevation(totalGain, prefs.distanceUnit)}",
            spendLabel = GeoUtils.formatCurrency(totalSpend, prefs.currencyCode),
            rideCount = todayTrips.size,
            hasActiveRide = hasActive
        )
    }

    @Composable
    private fun DailyWidgetContent(summary: DailyProgressSummary) {
        val bgColor = ColorProvider(Color(0xFF131815))
        val surfaceColor = ColorProvider(Color(0xFF1E2520))
        val accentColor = ColorProvider(Color(0xFFE86F48))
        val pineColor = ColorProvider(Color(0xFF5BB28A))
        val textPrimary = ColorProvider(Color(0xFFF1EFE9))
        val textMuted = ColorProvider(Color(0xFFA9AEA4))

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bgColor)
                .cornerRadius(20.dp)
                .clickable(actionStartActivity<MainActivity>())
                .padding(14.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = "KALYE • TODAY",
                    style = TextStyle(
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = if (summary.hasActiveRide) "● RECORDING" else "${summary.rideCount} RIDES",
                    style = TextStyle(
                        color = if (summary.hasActiveRide) pineColor else textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            Text(
                text = summary.distanceLabel,
                style = TextStyle(
                    color = textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(surfaceColor)
                    .cornerRadius(12.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "CLIMB",
                        style = TextStyle(color = textMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = summary.elevationLabel,
                        style = TextStyle(color = pineColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = GlanceModifier.width(8.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "SPEND",
                        style = TextStyle(color = textMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = summary.spendLabel,
                        style = TextStyle(color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }

    companion object {
        suspend fun refreshAll(context: Context) {
            try {
                KalyeDailyWidget().updateAll(context.applicationContext)
            } catch (_: Exception) {
                // Ignore if no widget placed
            }
        }
    }
}

class KalyeDailyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KalyeDailyWidget()
}
