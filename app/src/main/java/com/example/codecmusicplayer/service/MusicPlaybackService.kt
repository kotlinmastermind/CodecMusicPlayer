package com.example.codecmusicplayer.service

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.example.codecmusicplayer.engine.PlayerCommand
import com.example.codecmusicplayer.engine.PlayerEngine
import com.example.codecmusicplayer.engine.notification.NotificationAction
import com.example.codecmusicplayer.engine.notification.PlayerNotification
import kotlinx.coroutines.*

class MusicPlaybackService : Service() {

    private lateinit var engine: PlayerEngine
    private lateinit var notification: PlayerNotification

    // 🔥 Service-owned coroutine scope (NO lifecycleScope)
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onCreate() {
        super.onCreate()
        android.util.Log.e("SERVICE_TEST", "MusicPlaybackService CREATED")
        engine = PlayerEngine(this)

        notification = PlayerNotification(this)
        notification.createChannel()

        // 🔥 Engine → Notification (reactive)
        serviceScope.launch {
            engine.state.collect { state ->
                startForeground(
                    PlayerNotification.NOTIFICATION_ID,
                    notification.build(state)
                )
            }
        }

        val testUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        android.util.Log.e("SERVICE_TEST", "Sending PLAY command")
        engine.handleCommand(PlayerCommand.Play(testUri))


    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            NotificationAction.ACTION_PLAY ->
                engine.handleCommand(PlayerCommand.Resume)

            NotificationAction.ACTION_PAUSE ->
                engine.handleCommand(PlayerCommand.Pause)

            NotificationAction.ACTION_STOP -> {
                engine.handleCommand(PlayerCommand.Stop)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }

                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        serviceJob.cancel() // 🔥 Prevent leaks
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
