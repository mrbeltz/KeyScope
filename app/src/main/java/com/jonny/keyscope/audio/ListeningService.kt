package com.jonny.keyscope.audio

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
import androidx.core.content.ContextCompat
import com.jonny.keyscope.MainActivity
import com.jonny.keyscope.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the analysis alive while you switch apps or fold the phone shut. The service does no DSP
 * itself; it just holds the process up and owns the ongoing notification.
 */
class ListeningService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engineWatcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            KeyScopeEngine.stop()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
        )
        KeyScopeEngine.start(this)

        // The engine releases the mic itself once a key locks. Watch for that so the notification
        // goes away with it rather than sitting there claiming to be recording.
        engineWatcher?.cancel()
        engineWatcher = scope.launch {
            KeyScopeEngine.state
                .map { it.listening }
                .distinctUntilChanged()
                .drop(1) // the current value is already true; react to the change away from it
                .collect { listening ->
                    if (!listening) {
                        ServiceCompat.stopForeground(
                            this@ListeningService, ServiceCompat.STOP_FOREGROUND_REMOVE
                        )
                        stopSelf()
                    }
                }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        engineWatcher?.cancel()
        scope.cancel()
        KeyScopeEngine.stop()
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentTitle(getString(R.string.app_name))
        .setContentText("Listening for a key")
        .setOngoing(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            0,
            "Stop",
            PendingIntent.getService(
                this,
                1,
                Intent(this, ListeningService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    companion object {
        private const val CHANNEL_ID = "keyscope.listening"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.jonny.keyscope.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ListeningService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ListeningService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
