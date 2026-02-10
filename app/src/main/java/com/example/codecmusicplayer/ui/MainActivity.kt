package com.example.codecmusicplayer.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.codecmusicplayer.viewmodel.AudioViewModel
import com.example.codecmusicplayer.R

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: AudioViewModel

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                requestAudioPermission()
            }
        }

    private val audioPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                viewModel.loadAudio()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[AudioViewModel::class.java]

        requestNotificationPermissionIfNeeded()
        observeAudio()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            requestAudioPermission()
        }
    }

    private fun requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            audioPermissionLauncher.launch(
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            audioPermissionLauncher.launch(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    private fun observeAudio() {
        viewModel.audioList.observe(this) { list ->
            android.util.Log.d(
                "MainActivity",
                "Songs found: ${list.size}"
            )
        }
    }
}
