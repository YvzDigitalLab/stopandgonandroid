package fr.yvz.stopandgo.data

import android.net.Uri
import java.util.UUID

data class SongDataContainer(
    val title: String? = null,
    val duration: Double? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkFileName: String? = null,
    val notes: String? = null,
    val localAudioPath: String? = null
)

data class PlaylistDataContainer(
    val createdTimestamp: String? = null,
    val modifiedTimestamp: String? = null,
    val fileFormatVersion: Int? = null,
    val appVersion: Float? = null,
    val phoneModel: String? = null,
    val osVersion: String? = null,
    val songList: List<SongDataContainer>? = null
)

data class Song(
    val id: String = UUID.randomUUID().toString(),
    val title: String? = null,
    val duration: Double = 0.0,
    val uri: Uri? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkFileName: String? = null,
    val notes: String? = null,
    val isImportedFile: Boolean = true,
    val isTransient: Boolean = false
)
