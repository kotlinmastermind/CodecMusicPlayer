package com.example.codecmusicplayer.engine.extractor

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log

class AudioExtractor(
    private val context: Context
) {

    private val extractor = MediaExtractor()
    private var audioTrackIndex = -1
    private lateinit var audioFormat: MediaFormat

    /**
     * Prepare extractor with audio file
     */
    fun prepare(uri: Uri) {
        extractor.setDataSource(context, uri, null)
        Log.e("EXTRACTOR_TEST", "MediaExtractor prepared successfully")

        selectAudioTrack()
    }

    /**
     * Find & select audio track
     */
    private fun selectAudioTrack() {
        Log.e("EXTRACTOR_TEST", "Starting read loop")

        for (i in 0 until extractor.trackCount) {

            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                audioFormat = format
                extractor.selectTrack(i)
                return
            }
        }

        throw IllegalStateException("No audio track found")
    }

    /**
     * Read encoded frame into buffer
     */
    fun readSampleData(buffer: ByteArray): Int {
        return extractor.readSampleData(
            java.nio.ByteBuffer.wrap(buffer),
            0
        )
    }

    /**
     * Advance to next frame
     */
    fun advance(): Boolean {
        return extractor.advance()
    }

    /**
     * Sample timestamp (microseconds)
     */
    fun sampleTimeUs(): Long {
        return extractor.sampleTime
    }

    /**
     * Sample flags (sync frame etc.)
     */
    fun sampleFlags(): Int {
        return extractor.sampleFlags
    }

    fun getAudioFormat(): MediaFormat = audioFormat

    fun release() {
        extractor.release()
    }
}
