package com.example.codecmusicplayer.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.codecmusicplayer.R
import com.example.codecmusicplayer.engine.notification.NotificationAction
import com.example.codecmusicplayer.service.MusicPlaybackService
import com.example.codecmusicplayer.viewmodel.AudioViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: AudioViewModel
    private lateinit var playButton: Button

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) requestAudioPermission()
        }

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.loadAudio()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[AudioViewModel::class.java]

        playButton = findViewById(R.id.btnPlay)

        requestNotificationPermissionIfNeeded()
        observeAudio()

        playButton.setOnClickListener {
            val audioList = viewModel.audioList.value
            if (!audioList.isNullOrEmpty()) {
                val firstTrackUri = Uri.parse(audioList[0].uri)
                startPlaybackService(firstTrackUri)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestAudioPermission()
        }
    }

    private fun requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            audioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            audioPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun observeAudio() {
        viewModel.audioList.observe(this) { list ->
            android.util.Log.d("MainActivity", "Songs found: ${list.size}")
        }
    }

    private fun startPlaybackService(uri: Uri) {
        // Suppose you selected the first track from your list
        val firstTrackUri: Uri = viewModel.audioList.value?.firstOrNull()?.let {
            Uri.parse(it.uri)
        } ?: return

        val intent = Intent(this, MusicPlaybackService::class.java).apply {
            action = NotificationAction.ACTION_PLAY
            putExtra("TRACK_URI", firstTrackUri.toString())
        }
        startService(intent)

    }

}
