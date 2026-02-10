package com.example.codecmusicplayer.engine

import android.content.Context
import android.net.Uri
import com.example.codecmusicplayer.engine.extractor.AudioExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerEngine(
    private val context: Context
) {
    init {
        android.util.Log.e("ENGINE_TEST", "PlayerEngine CREATED")
    }


    // 🔥 Observable state (Engine is the boss)
    private val _state =
        MutableStateFlow<PlayerState>(PlayerState.Idle)

    val state: StateFlow<PlayerState> = _state


    // for media extractor testing

    init {
        android.util.Log.d("TEST", "PlayerEngine created")
    }






    fun handleCommand(command: PlayerCommand) {
        when (command) {

            is PlayerCommand.Play -> prepare(command.uri)

            PlayerCommand.Pause -> pause()

            PlayerCommand.Resume -> resume()

            PlayerCommand.Stop -> stop()

            is PlayerCommand.Seek -> seek(command.positionMs)
        }
    }

    private fun prepare(uri: Uri) {

        android.util.Log.e("PREPARE_TEST", "prepare() CALLED with uri=$uri")


        _state.value = PlayerState.Preparing

        CoroutineScope(Dispatchers.IO).launch {

            val extractor = AudioExtractor(context)
            extractor.prepare(uri)

            val buffer = ByteArray(1024 * 1024)

            while (true) {
                val size = extractor.readSampleData(buffer)
                if (size < 0) break

                android.util.Log.d(
                    "Extractor",
                    "Frame size=$size timeUs=${extractor.sampleTimeUs()}"
                )

                extractor.advance()
            }

            extractor.release()

            withContext(Dispatchers.Main) {
                _state.value = PlayerState.Playing
            }
        }
    }

    private fun pause() {
        if (_state.value is PlayerState.Playing) {
            _state.value = PlayerState.Paused
        }
    }

    private fun resume() {
        if (_state.value is PlayerState.Paused) {
            _state.value = PlayerState.Playing
        }
    }

    private fun stop() {
        _state.value = PlayerState.Stopped
    }

    private fun seek(positionMs: Long) {
        // Stage 12
    }


}
