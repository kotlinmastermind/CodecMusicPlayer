package com.example.codecmusicplayer.service

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.codecmusicplayer.engine.PlayerCommand
import com.example.codecmusicplayer.engine.PlayerEngine
import com.example.codecmusicplayer.engine.PlayerState
import com.example.codecmusicplayer.engine.notification.NotificationAction
import com.example.codecmusicplayer.engine.notification.PlayerNotification
import kotlinx.coroutines.*

class MusicPlaybackService : Service() {

    private lateinit var engine: PlayerEngine
    private lateinit var notification: PlayerNotification

    // 🔹 Service-owned coroutine scope
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onCreate() {
        super.onCreate()
        Log.e("SERVICE_STAGE8", "MusicPlaybackService CREATED")

        // Initialize PlayerEngine and Notification
        engine = PlayerEngine(this)
        notification = PlayerNotification(this)
        notification.createChannel()

        // 🔹 Observe PlayerEngine state and update notification live
        serviceScope.launch {
            engine.state.collect { state ->
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {

            NotificationAction.ACTION_PLAY -> {
                Log.e("SERVICE_STAGE8", "ACTION_PLAY received")
                engine.handleCommand(PlayerCommand.Resume)
            }

            NotificationAction.ACTION_PAUSE -> {
                Log.e("SERVICE_STAGE8", "ACTION_PAUSE received")
                engine.handleCommand(PlayerCommand.Pause)
            }

            NotificationAction.ACTION_STOP -> {
                Log.e("SERVICE_STAGE8", "ACTION_STOP received")
                engine.handleCommand(PlayerCommand.Stop)
                stopForegroundService()
            }

            null -> {
                Log.e("SERVICE_STAGE8", "No action: start default playback if needed")
                // Optional: engine.handleCommand(PlayerCommand.Play(defaultUri))
            }
        }

        return START_STICKY
    }

    // 🔹 Update or create foreground notification based on PlayerState
    private fun updateNotification(state: PlayerState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                PlayerNotification.NOTIFICATION_ID,
                notification.build(state)
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(
                PlayerNotification.NOTIFICATION_ID,
                notification.build(state)
            )
        }
    }

    private fun stopForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        serviceJob.cancel() // 🔥 Prevent coroutine leaks
        super.onDestroy()
        Log.e("SERVICE_STAGE8", "Service DESTROYED")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
