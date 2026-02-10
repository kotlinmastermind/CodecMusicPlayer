package com.example.codecmusicplayer.engine

import android.net.Uri
sealed class PlayerCommand {
    data class Play(val uri: Uri) : PlayerCommand()
    object Pause : PlayerCommand()
    object Resume : PlayerCommand()
    object Stop : PlayerCommand()
    data class Seek(val positionMs: Long) : PlayerCommand()
}
