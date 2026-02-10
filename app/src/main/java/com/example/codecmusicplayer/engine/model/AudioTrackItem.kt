package com.example.codecmusicplayer.engine.model

import android.net.Uri

data class AudioTrackItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val mimeType: String
)
