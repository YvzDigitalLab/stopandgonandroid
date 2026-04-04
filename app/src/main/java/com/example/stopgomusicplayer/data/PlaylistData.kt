package com.example.stopgomusicplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class PlaylistData private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: PlaylistData? = null

        fun getInstance(context: Context): PlaylistData {
            return instance ?: synchronized(this) {
                instance ?: PlaylistData(context.applicationContext).also { instance = it }
            }
        }
    }

    private val playlistsFolderName = "playlists"
    private val playlistFileName = "content.json"
    private val gson: Gson = GsonBuilder().create()

    val playlistsFolder: File = File(context.filesDir, playlistsFolderName)
    var currentPlaylistName: String?
        get() = prefs.getString("playlistName", null)
        set(value) { prefs.edit().putString("playlistName", value).apply() }

    private var currentPlaylist: PlaylistDataContainer? = null
    var playlists: List<String> = emptyList()

    private val prefs = context.getSharedPreferences("stopandgo_prefs", Context.MODE_PRIVATE)

    init {
        createFolderIfNeeded()
        playlists = loadPlaylistsList()

        val name = currentPlaylistName
        if (name == null) {
            val firstPlaylistName = createPlaylist("My first playlist")
            if (firstPlaylistName != null) {
                currentPlaylistName = firstPlaylistName
                currentPlaylist = loadPlaylist(firstPlaylistName)
                playlists = loadPlaylistsList()
            }
        } else {
            currentPlaylist = loadPlaylist(name)
        }
    }

    private fun createFolderIfNeeded() {
        if (!playlistsFolder.exists()) {
            playlistsFolder.mkdirs()
        }
    }

    fun loadPlaylistsList(): List<String> {
        return playlistsFolder.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    fun loadPlaylist(name: String): PlaylistDataContainer? {
        val file = File(playlistsFolder, "$name/$playlistFileName")
        return try {
            val json = file.readText(Charsets.UTF_8)
            gson.fromJson(json, PlaylistDataContainer::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun removePlaylist(name: String) {
        if (name.isNotEmpty()) {
            File(playlistsFolder, name).deleteRecursively()
        }
    }

    fun createPlaylist(name: String): String? {
        var finalName = removeForbiddenCharacters(name)
        if (finalName.isEmpty()) return null

        finalName = deduplicateName(finalName)

        val dir = File(playlistsFolder, finalName)
        if (!dir.mkdirs() && !dir.exists()) return null

        val playlistData = PlaylistDataContainer()
        val saved = savePlaylistData(finalName, playlistData)
        if (!saved) {
            removePlaylist(finalName)
            return null
        }
        return finalName
    }

    private fun savePlaylistData(playlist: String, data: PlaylistDataContainer): Boolean {
        val file = File(playlistsFolder, "$playlist/$playlistFileName")
        return try {
            val json = gson.toJson(data)
            file.writeText(json, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun removeForbiddenCharacters(fileName: String): String {
        return fileName.replace(Regex("[:/\\n\\r\\\\]"), "")
    }

    private fun deduplicateName(newName: String): String {
        val existing = loadPlaylistsList()
        if (existing.isEmpty()) return newName

        var nameKey = newName
        while (existing.contains(nameKey)) {
            val lastSpace = nameKey.lastIndexOf(' ')
            if (lastSpace >= 0) {
                val suffix = nameKey.substring(lastSpace + 1)
                val num = suffix.toIntOrNull()
                if (num != null) {
                    nameKey = nameKey.substring(0, lastSpace) + " ${num + 1}"
                    continue
                }
            }
            nameKey = "$nameKey 2"
        }
        return nameKey
    }

    fun addFileFromUri(sourceUri: Uri): Boolean {
        val name = currentPlaylistName ?: return false
        if (currentPlaylist == null) return false

        val playlistFolder = File(playlistsFolder, name)
        val audioFolder = File(playlistFolder, "audio")
        audioFolder.mkdirs()

        // Copy file to app sandbox
        val ext = getFileExtension(sourceUri) ?: "m4a"
        val localFileName = "${UUID.randomUUID()}.$ext"
        val localFile = File(audioFolder, localFileName)

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return false
        } catch (e: Exception) {
            return false
        }

        // Extract metadata
        val retriever = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var duration = 0.0
        var artworkFileName: String? = null

        try {
            retriever.setDataSource(localFile.absolutePath)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration = (durationMs?.toLongOrNull() ?: 0L) / 1000.0

            // Extract artwork
            val artworkBytes = retriever.embeddedPicture
            if (artworkBytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(artworkBytes, 0, artworkBytes.size)
                if (bitmap != null) {
                    val resized = resizeImage(bitmap, 320, 320)
                    artworkFileName = writeImageFile(resized, playlistFolder, "${UUID.randomUUID()}.jpg")
                    resized.recycle()
                    if (bitmap !== resized) bitmap.recycle()
                }
            }
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        if (title.isNullOrEmpty()) {
            title = sourceUri.lastPathSegment?.substringBeforeLast('.') ?: "Unknown"
        }

        val relativePath = "playlists/$name/audio/$localFileName"
        val song = SongDataContainer(
            title = title,
            duration = duration,
            artist = artist,
            album = album,
            artworkFileName = artworkFileName,
            localAudioPath = relativePath
        )

        val updatedList = (currentPlaylist?.songList ?: emptyList()) + song
        currentPlaylist = currentPlaylist?.copy(songList = updatedList)
            ?: PlaylistDataContainer(songList = listOf(song))
        return true
    }

    private fun getFileExtension(uri: Uri): String? {
        val path = uri.lastPathSegment ?: return null
        val dot = path.lastIndexOf('.')
        return if (dot >= 0) path.substring(dot + 1) else null
    }

    private fun resizeImage(source: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()
        val fillScale = maxOf(targetW / srcW, targetH / srcH)
        val cropW = (targetW / fillScale).toInt()
        val cropH = (targetH / fillScale).toInt()
        val cropX = ((srcW - cropW) / 2).toInt()
        val cropY = ((srcH - cropH) / 2).toInt()

        val cropped = Bitmap.createBitmap(source, cropX, cropY, cropW, cropH)
        val scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
        if (cropped !== source && cropped !== scaled) cropped.recycle()
        return scaled
    }

    private fun writeImageFile(bitmap: Bitmap, folder: File, fileName: String): String? {
        return try {
            val file = File(folder, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }
            fileName
        } catch (e: Exception) {
            null
        }
    }

    fun saveCurrentPlaylist(): Boolean {
        val name = currentPlaylistName ?: return false
        val data = currentPlaylist ?: return false
        return savePlaylistData(name, data)
    }

    fun switchToPlaylist(name: String) {
        currentPlaylistName = name
        currentPlaylist = loadPlaylist(name)
    }

    fun removeSong(at: Int) {
        val list = currentPlaylist?.songList?.toMutableList() ?: return
        if (at < 0 || at >= list.size) return

        val name = currentPlaylistName ?: return
        // Delete artwork file
        list[at].artworkFileName?.let {
            File(playlistsFolder, "$name/$it").delete()
        }
        // Delete imported audio file
        list[at].localAudioPath?.let {
            File(context.filesDir, it).delete()
        }

        list.removeAt(at)
        currentPlaylist = currentPlaylist?.copy(songList = list)
    }

    fun moveSong(from: Int, to: Int) {
        val list = currentPlaylist?.songList?.toMutableList() ?: return
        if (from < 0 || from >= list.size || to < 0 || to > list.size) return
        val item = list.removeAt(from)
        val adjustedDest = if (to > from) to - 1 else to
        list.add(adjustedDest, item)
        currentPlaylist = currentPlaylist?.copy(songList = list)
    }

    fun renamePlaylist(oldName: String, newName: String): Boolean {
        val sanitized = removeForbiddenCharacters(newName)
        if (sanitized.isEmpty()) return false
        val finalName = deduplicateName(sanitized)
        val oldDir = File(playlistsFolder, oldName)
        val newDir = File(playlistsFolder, finalName)
        return try {
            oldDir.renameTo(newDir).also {
                if (it && currentPlaylistName == oldName) {
                    currentPlaylistName = finalName
                }
                playlists = loadPlaylistsList()
            }
        } catch (e: Exception) {
            false
        }
    }

    fun updateSong(at: Int, title: String?, artist: String?, album: String?, notes: String?, artworkBitmap: Bitmap?) {
        val list = currentPlaylist?.songList?.toMutableList() ?: return
        if (at < 0 || at >= list.size) return

        var artworkFileName = list[at].artworkFileName
        if (artworkBitmap != null) {
            val name = currentPlaylistName ?: return
            // Delete old artwork
            artworkFileName?.let {
                File(playlistsFolder, "$name/$it").delete()
            }
            val resized = resizeImage(artworkBitmap, 320, 320)
            artworkFileName = writeImageFile(resized, File(playlistsFolder, name), "${UUID.randomUUID()}.jpg")
            resized.recycle()
        }

        list[at] = list[at].copy(
            title = title,
            artist = artist,
            album = album,
            notes = notes,
            artworkFileName = artworkFileName
        )
        currentPlaylist = currentPlaylist?.copy(songList = list)
        saveCurrentPlaylist()
    }

    fun clearSongs() {
        currentPlaylist = currentPlaylist?.copy(songList = emptyList())
    }

    fun getSongs(): List<Song> {
        return currentPlaylist?.songList?.map { container ->
            val uri = if (container.localAudioPath != null) {
                val file = File(context.filesDir, container.localAudioPath)
                if (file.exists()) Uri.fromFile(file) else null
            } else {
                null
            }
            Song(
                title = container.title,
                duration = container.duration ?: 0.0,
                uri = uri,
                artist = container.artist,
                album = container.album,
                artworkFileName = container.artworkFileName,
                notes = container.notes,
                isImportedFile = container.localAudioPath != null
            )
        } ?: emptyList()
    }

    fun artworkFile(forSongAt: Int): File? {
        val list = currentPlaylist?.songList ?: return null
        if (forSongAt < 0 || forSongAt >= list.size) return null
        val artworkName = list[forSongAt].artworkFileName ?: return null
        val name = currentPlaylistName ?: return null
        val file = File(playlistsFolder, "$name/$artworkName")
        return if (file.exists()) file else null
    }
}
