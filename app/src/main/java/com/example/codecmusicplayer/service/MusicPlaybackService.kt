package com.example.codecmusicplayer.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.codecmusicplayer.engine.PlayerCommand
import com.example.codecmusicplayer.engine.PlayerEngine
import com.example.codecmusicplayer.engine.PlayerState
import com.example.codecmusicplayer.engine.notification.NotificationAction
import com.example.codecmusicplayer.engine.notification.PlayerNotification
import kotlinx.coroutines.*

import android.net.Uri
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat


class MusicPlaybackService : Service() {

    private lateinit var engine: PlayerEngine
    private lateinit var notification: PlayerNotification

    // 🔹 Service-owned coroutine scope
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var mediaSession: MediaSessionCompat

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null




    override fun onCreate() {
        super.onCreate()

        Log.e("SERVICE_STAGE8", "MusicPlaybackService CREATED")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initAudioFocus()

        initMediaSession()


        // Initialize PlayerEngine and Notification
        engine = PlayerEngine(this)
        notification = PlayerNotification(this,mediaSession.sessionToken)
        notification.createChannel()


        mediaSession = MediaSessionCompat(this, "CodecMusicSession").apply {

            setCallback(object : MediaSessionCompat.Callback() {

                override fun onPlay() {
                    requestAudioFocus()
                    engine.handleCommand(PlayerCommand.Resume)
                }

                override fun onPause() {
                    engine.handleCommand(PlayerCommand.Pause)
                }

                override fun onStop() {
                    engine.handleCommand(PlayerCommand.Stop)
                    abandonAudioFocus()
                    stopSelf()
                }
            })

            isActive = true
        }


        // 🔹 Observe PlayerEngine state and update notification live
        serviceScope.launch {
            engine.state.collect { state ->

                // 1️⃣ Update notification
                updateNotification(state)

                // 2️⃣ Update MediaSession playback state
                when (state) {

                    PlayerState.Playing ->
                        updatePlaybackState(true)

                    PlayerState.Paused ->
                        updatePlaybackState(false)

                    PlayerState.Stopped ->
                        updatePlaybackState(false)

                    PlayerState.Idle ->
                        updatePlaybackState(false)

                    PlayerState.Preparing ->
                        updatePlaybackState(false)

                    is PlayerState.Error ->
                        updatePlaybackState(false)
                }
            }
        }

    }

    private fun initAudioFocus() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener { focusChange ->
                    handleFocusChange(focusChange)
                }
                .build()
        }
    }

    private fun handleFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                engine.handleCommand(PlayerCommand.Stop)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                engine.handleCommand(PlayerCommand.Pause)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                engine.handleCommand(PlayerCommand.Resume)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // Get URI string from intent extras
        val trackUriString: String? = intent?.getStringExtra("TRACK_URI")

        // Parse string to Uri safely
        val trackUri: Uri? = trackUriString?.let { Uri.parse(it) }

        when (intent?.action) {
            NotificationAction.ACTION_PLAY -> {
                if (trackUri != null) {

                    if (requestAudioFocus()) {
                        engine.handleCommand(PlayerCommand.Play(trackUri))
                    }

                } else {
                    android.util.Log.e("SERVICE_STAGE8", "No URI provided for playback")
                }
            }

            NotificationAction.ACTION_PAUSE -> engine.handleCommand(PlayerCommand.Pause)
            NotificationAction.ACTION_STOP -> {

                engine.handleCommand(PlayerCommand.Stop)
                abandonAudioFocus()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                else
                    stopForeground(true)

                stopSelf()
            }


            else -> {
                if (trackUri != null) {
                    engine.handleCommand(PlayerCommand.Play(trackUri))
                } else {
                    android.util.Log.e("SERVICE_STAGE8", "No action: start default playback if needed")
                }
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

    private fun requestAudioFocus(): Boolean {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(audioFocusRequest!!) ==
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            audioManager.requestAudioFocus(
                { focusChange -> handleFocusChange(focusChange) },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            audioManager.abandonAudioFocus(null)
        }
    }



    private fun updatePlaybackState(isPlaying: Boolean) {

        val state = if (isPlaying)
            PlaybackStateCompat.STATE_PLAYING
        else
            PlaybackStateCompat.STATE_PAUSED

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    private fun initMediaSession() {

        mediaSession = MediaSessionCompat(this, "CodecMusicSession").apply {

            setCallback(object : MediaSessionCompat.Callback() {

                override fun onPlay() {
                    requestAudioFocus()
                    engine.handleCommand(PlayerCommand.Resume)
                }

                override fun onPause() {
                    engine.handleCommand(PlayerCommand.Pause)
                }

                override fun onStop() {
                    engine.handleCommand(PlayerCommand.Stop)
                    abandonAudioFocus()
                    stopSelf()
                }
            })

            isActive = true
        }
    }


    override fun onDestroy() {
        serviceJob.cancel() // 🔥 Prevent coroutine leaks
        super.onDestroy()
        Log.e("SERVICE_STAGE8", "Service DESTROYED")
    }

    override fun onBind(intent: Intent?): IBinder? = null








}
