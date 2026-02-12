package com.example.codecmusicplayer.engine

import android.content.Context
import android.media.*
import android.net.Uri
import android.util.Log
import com.example.codecmusicplayer.engine.extractor.AudioExtractor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer

class PlayerEngine(
    private val context: Context
) {

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val state: StateFlow<PlayerState> = _state

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var extractor: AudioExtractor? = null
    private var codec: MediaCodec? = null
    private var audioTrack: AudioTrack? = null


    @Volatile private var isPlaying = false


    private var playing = false
    private var paused = false

    fun handleCommand(command: PlayerCommand) {
        when (command) {
            is PlayerCommand.Play -> prepareAndPlay(command.uri)
            PlayerCommand.Pause -> paused = true.also { _state.value = PlayerState.Paused }
            PlayerCommand.Resume -> paused = false.also { _state.value = PlayerState.Playing }
            PlayerCommand.Stop -> stop()
            is PlayerCommand.Seek -> {}
        }
    }

    private fun prepareAndPlay(uri: Uri) {
        stop()

        engineScope.launch {
            try {
                _state.value = PlayerState.Preparing

                extractor = AudioExtractor(context).apply { prepare(uri) }
                val format = extractor!!.getAudioFormat()
                val mime = format.getString(MediaFormat.KEY_MIME)!!

                codec = MediaCodec.createDecoderByType(mime).apply {
                    configure(format, null, null, 0)
                    start()
                }

                audioTrack = buildAudioTrack(format)
                audioTrack?.play()

                playing = true
                paused = false

                _state.value = PlayerState.Playing

                decodeLoop()

            } catch (e: Exception) {
                _state.value = PlayerState.Error(e.message ?: "Unknown error")
            }
        }
    }


    private suspend fun decodeLoop() {

        val bufferInfo = MediaCodec.BufferInfo()

        while (playing) {

            if (paused) {
                delay(50)
                continue
            }

            // ========= INPUT =========
            val inputIndex = codec?.dequeueInputBuffer(10000) ?: -1

            if (inputIndex >= 0) {

                val inputBuffer = codec!!.getInputBuffer(inputIndex)!!
                inputBuffer.clear()

                val sampleSize = extractor!!.readSampleData(inputBuffer)

                if (sampleSize < 0) {
                    codec!!.queueInputBuffer(
                        inputIndex,
                        0,
                        0,
                        0L,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    playing = false
                } else {
                    codec!!.queueInputBuffer(
                        inputIndex,
                        0,
                        sampleSize,
                        extractor!!.sampleTimeUs(),
                        0
                    )
                    extractor!!.advance()
                }
            }

            // ========= OUTPUT =========
            val outputIndex = codec?.dequeueOutputBuffer(bufferInfo, 10000) ?: -1

            if (outputIndex >= 0) {

                val outputBuffer = codec!!.getOutputBuffer(outputIndex)

                if (outputBuffer != null && bufferInfo.size > 0) {

                    val pcmData = ByteArray(bufferInfo.size)
                    outputBuffer.get(pcmData)

                    audioTrack?.write(pcmData, 0, pcmData.size)
                }

                codec!!.releaseOutputBuffer(outputIndex, false)
            }
        }

        stop()
    }



    private fun startOutputLoop() {

        val track = audioTrack ?: return

        val bufferInfo = MediaCodec.BufferInfo()

        track.play()

        while (isPlaying) {
            val outputIndex = codec!!.dequeueOutputBuffer(bufferInfo, 10_000)

            if (outputIndex >= 0) {
                val outputBuffer = codec!!.getOutputBuffer(outputIndex)

                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                    val pcmData = ByteArray(bufferInfo.size)
                    outputBuffer.get(pcmData)

                    track.write(pcmData, 0, pcmData.size)
                }

                codec!!.releaseOutputBuffer(outputIndex, false)
            }
        }
    }


    private fun buildAudioTrack(format: MediaFormat): AudioTrack {
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val channelConfig =
            if (channelCount == 1)
                AudioFormat.CHANNEL_OUT_MONO
            else
                AudioFormat.CHANNEL_OUT_STEREO

        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        )

        return AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer,
            AudioTrack.MODE_STREAM
        )
    }

    private fun stop() {
        playing = false
        codec?.stop()
        codec?.release()
        extractor?.release()
        audioTrack?.stop()
        audioTrack?.release()

        codec = null
        extractor = null
        audioTrack = null

        _state.value = PlayerState.Stopped
    }
}
