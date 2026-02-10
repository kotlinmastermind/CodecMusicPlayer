package com.example.codecmusicplayer.viewmodel

import android.app.Application
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

data class AudioItem(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: String
)

class AudioViewModel(application: Application) : AndroidViewModel(application) {

    private val _audioList = MutableLiveData<List<AudioItem>>()
    val audioList: LiveData<List<AudioItem>> = _audioList

    fun loadAudio() {
        Log.e("AudioVM", "loadAudio() CALLED")

        val resolver = getApplication<Application>().contentResolver

        val cursor = resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            null,
            null,
            null,
            null
        )

        Log.e(
            "AudioVM",
            "Cursor null? ${cursor == null}, count = ${cursor?.count}"
        )

        cursor?.use {
            val list = mutableListOf<AudioItem>()

            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                val title = it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                val artist = it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                val uri = "${MediaStore.Audio.Media.EXTERNAL_CONTENT_URI}/$id"

                list.add(AudioItem(id, title, artist, uri))
            }

            _audioList.postValue(list)

        }
    }

}
