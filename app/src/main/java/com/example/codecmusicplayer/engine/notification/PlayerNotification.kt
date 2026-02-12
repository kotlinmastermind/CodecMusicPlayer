package com.example.codecmusicplayer.engine.notification

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import com.example.codecmusicplayer.R
import com.example.codecmusicplayer.engine.PlayerState
import com.example.codecmusicplayer.service.MusicPlaybackService



class PlayerNotification(
    private val context: Context,
    private val sessionToken: MediaSessionCompat.Token
) {

    companion object {
        const val CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 1001
    }

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun build(state: PlayerState): Notification {

        val isPlaying = state is PlayerState.Playing

        val playPauseAction = if (isPlaying) {
            buildAction(
                R.drawable.ic_pause,
                "Pause",
                NotificationAction.ACTION_PAUSE
            )
        } else {
            buildAction(
                R.drawable.ic_play,
                "Play",
                NotificationAction.ACTION_PLAY
            )
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music)
            .setContentTitle("Codec Music Player")
            .setContentText(mapStateText(state))
            .setOngoing(isPlaying)
            .addAction(playPauseAction)

            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )

            .build()
    }

    private fun mapStateText(state: PlayerState): String {
        return when (state) {
            is PlayerState.Idle -> "Idle"
            is PlayerState.Preparing -> "Preparing"
            is PlayerState.Playing -> "Playing"
            is PlayerState.Paused -> "Paused"
            is PlayerState.Stopped -> "Stopped"
            is PlayerState.Error -> "Error: ${state.reason}"
        }
    }

    private fun buildAction(
        icon: Int,
        title: String,
        action: String
    ): NotificationCompat.Action {

        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            this.action = action
        }

        val pendingIntent = PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Action(icon, title, pendingIntent)
    }
}
