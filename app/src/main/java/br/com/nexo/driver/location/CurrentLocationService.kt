package br.com.nexo.driver.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Explicit, user-visible foreground service for the optional GPS feature. It is intentionally
 * non-sticky and `stopWithTask`; Driver Inteligente does not request background-location access.
 */
class CurrentLocationService : Service() {
    private lateinit var tracker: CurrentLocationTracker
    private val monitorHandler = Handler(Looper.getMainLooper())
    private var terminalState: CurrentLocationState? = null
    private val monitor = object : Runnable {
        override fun run() {
            when (val state = tracker.currentState()) {
                CurrentLocationState.PermissionMissing,
                CurrentLocationState.ProviderUnavailable,
                -> stopTracking(state)
                else -> monitorHandler.postDelayed(this, MONITOR_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        tracker = CurrentLocationTracker(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }
        return runCatching {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            when (val state = tracker.start()) {
                CurrentLocationState.PermissionMissing,
                CurrentLocationState.ProviderUnavailable,
                -> stopTracking(state)
                else -> monitorHandler.postDelayed(monitor, MONITOR_INTERVAL_MS)
            }
            START_NOT_STICKY
        }.getOrElse { failure ->
            Log.w(TAG, "Location foreground service could not start: ${failure.javaClass.simpleName}")
            CurrentLocationStateRepository.update(CurrentLocationState.PermissionMissing)
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf(startId)
            START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        monitorHandler.removeCallbacks(monitor)
        tracker.close()
        terminalState?.let(CurrentLocationStateRepository::update)
        super.onDestroy()
    }

    private fun stopTracking(finalState: CurrentLocationState? = null) {
        terminalState = finalState
        monitorHandler.removeCallbacks(monitor)
        tracker.close()
        finalState?.let(CurrentLocationStateRepository::update)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(): Notification {
        val stopIntent = Intent(this, CurrentLocationService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(br.com.nexo.driver.R.drawable.ic_driver_inteligente)
            .setContentTitle(getString(br.com.nexo.driver.R.string.location_notification_title))
            .setContentText(getString(br.com.nexo.driver.R.string.location_notification_text))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(br.com.nexo.driver.R.string.location_notification_stop), stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(br.com.nexo.driver.R.string.location_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(br.com.nexo.driver.R.string.location_notification_channel_description)
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "driver_location"
        private const val NOTIFICATION_ID = 20260715
        private const val STOP_REQUEST_CODE = 77
        private const val ACTION_STOP = "br.com.nexo.driver.location.STOP"
        private const val TAG = "CurrentLocationService"
        private const val MONITOR_INTERVAL_MS = 2_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CurrentLocationService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CurrentLocationService::class.java).setAction(ACTION_STOP))
        }
    }
}
