package com.example.stopgomusicplayer.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.stopgomusicplayer.data.PlaylistData
import com.example.stopgomusicplayer.data.Song
import com.example.stopgomusicplayer.playback.PlaybackEngine
import kotlin.math.abs

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    private val prefs = context.getSharedPreferences("stopandgo_prefs", Context.MODE_PRIVATE)
    private val engine: PlaybackEngine = PlaybackEngine.getInstance(context)

    // Player state
    var isPlaying by mutableIntStateOf(1) // 0=playing, 1=paused, -1=end-of-playlist
        private set
    var songName by mutableStateOf("")
        private set
    var songSubtitle by mutableStateOf("")
        private set
    var mode by mutableIntStateOf(prefs.getInt("stopAndGoKey", PlaybackEngine.MODE_STOP_AND_GO))
        private set
    var playlistName by mutableStateOf(prefs.getString("playlistName", "My first playlist") ?: "My first playlist")
        private set
    var showVolumeView by mutableStateOf(false)
    var showScrubbing by mutableStateOf(prefs.getBoolean("showSeekBar", true))
    var currentTime by mutableDoubleStateOf(0.0)
    var maxTime by mutableDoubleStateOf(0.0)
        private set
    var isCurrentlyScrubbing by mutableStateOf(false)
    var drmNotPlayable by mutableStateOf(false)
        private set
    var showPlaylist by mutableStateOf(false)
    var playlistSongs = mutableStateListOf<Song>()
        private set
    var currentPlayingSongIndex by mutableIntStateOf(0)
        private set
    var availablePlaylists = mutableStateListOf<String>()
        private set
    var playlistEditMode by mutableStateOf(false)

    // Scrub throttle
    private var ignoreNextUpdate = false
    private var lastSeekTime: Long = 0
    private val seekThrottleInterval: Long = 100

    val songTimeCurrent: String
        get() {
            if (maxTime <= 0) return ""
            val time = currentTime.toInt()
            return String.format("%d:%02d", time / 60, time % 60)
        }

    val songTimeSeparator: String
        get() = if (maxTime > 0) "-" else ""

    val songTimeMax: String
        get() {
            if (maxTime <= 0) return ""
            val time = maxTime.toInt()
            return String.format("%d:%02d", time / 60, time % 60)
        }

    // Settings
    var colorTheme by mutableStateOf(prefs.getString("colorTheme", "Red") ?: "Red")
    var darkMode by mutableStateOf(prefs.getBoolean("darkmode", false))
    var xfadeTime by mutableStateOf(prefs.getFloat("xfadetime", 12f).toDouble())
    var repeatAll by mutableStateOf(prefs.getBoolean("repeatall", false))
    var softPause by mutableStateOf(prefs.getBoolean("softPause", false))
    var playOnTap by mutableStateOf(prefs.getBoolean("playOnTap", false))
    var showSeekBar by mutableStateOf(prefs.getBoolean("showSeekBar", true))

    init {
        engine.onStateChanged = { syncFromEngine() }
        engine.startup()
        syncFromEngine()
        reloadPlaylistSongs()
    }

    private fun syncFromEngine() {
        mode = engine.stopAndGo
        currentPlayingSongIndex = engine.currentSongIndex

        // Determine play state
        isPlaying = when {
            engine.endOfPlaylist -> -1
            engine.isPlaying -> 0
            else -> 1
        }

        // Song info
        val song = engine.currentSong()
        if (!engine.endOfPlaylist && song != null) {
            val title = if (song.title.isNullOrEmpty()) "Song not on device!" else song.title
            songName = title ?: ""
            songSubtitle = "${song.artist ?: ""} - ${song.album ?: ""}"
            drmNotPlayable = song.uri == null && title != "Song not on device!"
        } else if (engine.endOfPlaylist) {
            songName = ""
            songSubtitle = engine.stringFin
            currentTime = 0.0
            maxTime = 0.0
        } else {
            songName = ""
            songSubtitle = engine.stringVide
            currentTime = 0.0
            maxTime = 0.0
        }

        // Time
        if (!isCurrentlyScrubbing && !ignoreNextUpdate) {
            val time = engine.updateTimeDisplay()
            if (!time.isNaN()) {
                currentTime = time
            }
        }
        maxTime = engine.getMaxTime()

        // Playlist name
        playlistName = prefs.getString("playlistName", "My first playlist") ?: "My first playlist"
    }

    fun reloadPlaylistSongs() {
        engine.reloadFromPlaylistData()
        val songs = engine.songList
        playlistSongs.clear()
        playlistSongs.addAll(songs)
        currentPlayingSongIndex = engine.currentSongIndex
    }

    fun reloadAvailablePlaylists() {
        val data = PlaylistData.getInstance(context)
        availablePlaylists.clear()
        availablePlaylists.addAll(data.loadPlaylistsList())
    }

    fun changeMode() { engine.changeMode() }
    fun previous() { engine.previous() }
    fun next() { engine.next() }
    fun playPause() { engine.playPause() }

    fun scrub(to: Double, force: Boolean = false) {
        if (force) {
            ignoreNextUpdate = true
            engine.scrubPrecise(to) { ignoreNextUpdate = false }
        } else if (isCurrentlyScrubbing) {
            val now = System.currentTimeMillis()
            if (now - lastSeekTime >= seekThrottleInterval) {
                lastSeekTime = now
                engine.scrub(to)
            }
        }
    }

    fun handleSongTap(at: Int) {
        if (playOnTap) {
            engine.jump(to = at, forcePlay = true)
        }
        reloadPlaylistSongs()
    }

    fun jumpToSong(at: Int, forcePlay: Boolean) {
        engine.jump(to = at, forcePlay = forcePlay)
        reloadPlaylistSongs()
    }

    fun moveSongToPlayNext(from: Int) {
        val current = engine.currentSongIndex
        val dest = current + 1
        engine.moveSong(from, dest)
        reloadPlaylistSongs()
    }

    fun deleteSong(at: Int) {
        engine.deleteSong(at = at)
        reloadPlaylistSongs()
    }

    fun moveSong(from: Int, to: Int) {
        engine.moveSong(from, to)
        reloadPlaylistSongs()
    }

    fun addFiles(uris: List<Uri>) {
        engine.addFilesFromDocumentPicker(uris)
        reloadPlaylistSongs()
    }

    fun switchToPlaylist(name: String) {
        engine.switchToPlaylist(name)
        reloadPlaylistSongs()
    }

    fun createNewPlaylist(name: String): String? {
        val result = engine.createNewPlaylist(name)
        if (result != null) {
            reloadPlaylistSongs()
            reloadAvailablePlaylists()
        }
        return result
    }

    fun deletePlaylist(name: String) {
        val data = PlaylistData.getInstance(context)
        data.removePlaylist(name)
        data.playlists = data.loadPlaylistsList()
        reloadAvailablePlaylists()
    }

    fun renamePlaylist(oldName: String, newName: String): Boolean {
        val data = PlaylistData.getInstance(context)
        val result = data.renamePlaylist(oldName, newName)
        if (result) {
            reloadAvailablePlaylists()
            playlistName = data.currentPlaylistName ?: playlistName
        }
        return result
    }

    fun updateSong(at: Int, title: String?, artist: String?, album: String?, notes: String?, artworkBitmap: Bitmap?) {
        engine.updateSong(at, title, artist, album, notes, artworkBitmap)
        reloadPlaylistSongs()
    }

    fun artworkFile(forSongAt: Int) = PlaylistData.getInstance(context).artworkFile(forSongAt)

    // Settings persistence
    fun setColorThemePref(theme: String) {
        colorTheme = theme
        prefs.edit().putString("colorTheme", theme).apply()
    }

    fun setDarkModePref(value: Boolean) {
        darkMode = value
        prefs.edit().putBoolean("darkmode", value).apply()
    }

    fun setXfadeTimePref(value: Double) {
        xfadeTime = value
        prefs.edit().putFloat("xfadetime", value.toFloat()).apply()
        engine.updateCrossfadeTiming()
    }

    fun setRepeatAllPref(value: Boolean) {
        repeatAll = value
        prefs.edit().putBoolean("repeatall", value).apply()
    }

    fun setSoftPausePref(value: Boolean) {
        softPause = value
        prefs.edit().putBoolean("softPause", value).apply()
    }

    fun setPlayOnTapPref(value: Boolean) {
        playOnTap = value
        prefs.edit().putBoolean("playOnTap", value).apply()
    }

    fun setShowSeekBarPref(value: Boolean) {
        showSeekBar = value
        showScrubbing = value
        prefs.edit().putBoolean("showSeekBar", value).apply()
    }

    fun setShowVolumePref(value: Boolean) {
        showVolumeView = value
        prefs.edit().putBoolean("volumeSlider", value).apply()
    }
}
