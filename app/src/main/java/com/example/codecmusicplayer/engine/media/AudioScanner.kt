package com.example.codecmusicplayer.engine.media

import android.content.Context
import android.provider.MediaStore
import com.example.codecmusicplayer.engine.model.AudioTrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioScanner(
    private val context: Context
) {

    suspend fun scanDeviceAudio(): List<AudioTrackItem> =
        withContext(Dispatchers.IO) {

            val audioList = mutableListOf<AudioTrackItem>()

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.MIME_TYPE
            )

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)

                    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        .buildUpon()
                        .appendPath(id.toString())
                        .build()

                    audioList.add(
                        AudioTrackItem(
                            id = id,
                            uri = uri,
                            title = it.getString(titleCol) ?: "Unknown",
                            artist = it.getString(artistCol) ?: "Unknown",
                            durationMs = it.getLong(durationCol),
                            mimeType = it.getString(mimeCol) ?: ""
                        )
                    )
                }
            }

            audioList
        }
}
