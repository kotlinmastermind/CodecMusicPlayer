package com.example.codecmusicplayer.engine

import android.content.Context
import android.media.*
import android.net.Uri
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

    private val engineJob = SupervisorJob()
    private val engineScope = CoroutineScope(Dispatchers.IO + engineJob)

    private val outputChannel = Channel<ByteArray>(Channel.UNLIMITED)

    private var decoderJob: Job? = null
    private var audioTrackJob: Job? = null

    private var audioTrack: AudioTrack? = null
    private var paused = false

    fun handleCommand(command: PlayerCommand) {
        when (command) {
            is PlayerCommand.Play -> startPlaying(command.uri)
            PlayerCommand.Pause -> pause()
            PlayerCommand.Resume -> resume()
            PlayerCommand.Stop -> stop()
            is PlayerCommand.Seek -> seek(command.positionMs)
        }
    }

    // 🔹 Stage-8: Start decoder + AudioTrack
    private fun startPlaying(uri: Uri) {
        _state.value = PlayerState.Preparing

        decoderJob?.cancel()
        audioTrackJob?.cancel()
        paused = false

        decoderJob = engineScope.launch {
            val extractor = AudioExtractor(context)
            extractor.prepare(uri)

            val format = extractor.getAudioFormat()
            val mime = format.getString(MediaFormat.KEY_MIME)!!

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val inputBuffers = codec.inputBuffers
            val outputBuffers = codec.outputBuffers

            var isExtractorDone = false
            var isDecoderDone = false

            while (!isDecoderDone && isActive) {
                // 1️⃣ Feed input buffer
                if (!isExtractorDone) {
                    val inputIndex = codec.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuffer = inputBuffers[inputIndex]
                        inputBuffer.clear()

                        val sampleSize = extractor.readSampleData(inputBuffer.array())
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isExtractorDone = true
                        } else {
                            val sampleTime = extractor.sampleTimeUs()
                            codec.queueInputBuffer(
                                inputIndex, 0, sampleSize, sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                // 2️⃣ Read output buffer
                val bufferInfo = MediaCodec.BufferInfo()
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputIndex >= 0) {
                    val outputBuffer = outputBuffers[outputIndex]
                    val chunk = ByteArray(bufferInfo.size)
                    outputBuffer.get(chunk)
                    outputBuffer.clear()

                    outputChannel.send(chunk)

                    codec.releaseOutputBuffer(outputIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        isDecoderDone = true
                        outputChannel.close()
                    }
                }
            }

            codec.stop()
            codec.release()
            extractor.release()
        }

        // 3️⃣ Start AudioTrack consumer with pause/resume
        audioTrackJob = engineScope.launch {
            val extractor = AudioExtractor(context)
            extractor.prepare(uri)
            val format = extractor.getAudioFormat()

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val channelConfig = if (channelCount == 1)
                AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO

            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize,
                AudioTrack.MODE_STREAM
            )
            audioTrack?.play()

            for (pcmChunk in outputChannel) {
                // Pause/resume support
                while (paused) {
                    delay(50)
                }
                audioTrack?.write(pcmChunk, 0, pcmChunk.size)
            }

            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        }

        engineScope.launch(Dispatchers.Main) {
            _state.value = PlayerState.Playing
        }
    }

    private fun pause() {
        if (_state.value is PlayerState.Playing) {
            paused = true
            _state.value = PlayerState.Paused
        }
    }

    private fun resume() {
        if (_state.value is PlayerState.Paused) {
            paused = false
            _state.value = PlayerState.Playing
        }
    }

    private fun stop() {
        decoderJob?.cancel()
        audioTrackJob?.cancel()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        engineJob.cancel()
        _state.value = PlayerState.Stopped
    }

    private fun seek(positionMs: Long) {
        // Stage-12: implement reset extractor + codec
    }
}
