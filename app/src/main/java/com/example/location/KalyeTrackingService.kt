package com.example.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.KalyeDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.model.GeoUtils
import com.example.data.remote.KalyeFirestoreDataSource
import com.example.data.repository.KalyeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class KalyeTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null
    private lateinit var repository: KalyeRepository
    private lateinit var locationTracker: KalyeLocationTracker

    override fun onCreate() {
        super.onCreate()
        repository = KalyeRepository(this, KalyeFirestoreDataSource(this))
        locationTracker = KalyeLocationTracker(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val tripId = intent.getStringExtra(EXTRA_TRIP_ID)
                if (!tripId.isNullOrBlank()) {
                    startForegroundTracking(tripId)
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundTracking(tripId: String) {
        val notification = buildNotification("Recording GPS route & elevation…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            locationTracker.observeHighAccuracyLocation().collect { telemetry ->
                val point = telemetry.point ?: return@collect
                if (point.accuracyMeters <= 45f) {
                    repository.appendTrackPoint(tripId, point)
                    val trip = KalyeDatabase.getInstance(this@KalyeTrackingService)
                        .kalyeDao().getTripById(tripId)
                    val prefs = PreferencesRepository(this@KalyeTrackingService).preferencesFlow.first()
                    if (trip != null) {
                        val status = when {
                            trip.pausedFlag -> "Paused"
                            trip.autoPausedFlag -> "Auto-Paused"
                            else -> "Recording"
                        }
                        val dist = GeoUtils.formatDistance(trip.distanceMeters, prefs.distanceUnit)
                        val moving = GeoUtils.formatMovingTime(
                            GeoUtils.computeLiveMovingMillis(trip.toDomain())
                        )
                        val mgr = getSystemService(NotificationManager::class.java)
                        mgr.notify(NOTIFICATION_ID, buildNotification("$status • $dist • $moving"))
                    }
                }
            }
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kalye Expedition Active")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingOpen)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tracking_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.tracking_channel_description)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        trackingJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "kalye_tracking_channel"
        private const val NOTIFICATION_ID = 4091
        const val ACTION_START = "com.example.action.START_TRACKING"
        const val ACTION_STOP = "com.example.action.STOP_TRACKING"
        const val EXTRA_TRIP_ID = "extra_trip_id"

        fun start(context: Context, tripId: String) {
            val intent = Intent(context, KalyeTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRIP_ID, tripId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, KalyeTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
