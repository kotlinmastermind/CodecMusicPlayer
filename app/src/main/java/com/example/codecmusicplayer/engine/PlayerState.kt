package com.example.codecmusicplayer.engine

sealed class PlayerState {
    object Idle : PlayerState()
    object Preparing : PlayerState()
    object Playing : PlayerState()
    object Paused : PlayerState()
    object Stopped : PlayerState()
    data class Error(val reason: String) : PlayerState()
}

