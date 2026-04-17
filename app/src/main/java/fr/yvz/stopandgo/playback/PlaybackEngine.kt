package fr.yvz.stopandgo.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import fr.yvz.stopandgo.data.PlaylistData
import fr.yvz.stopandgo.data.Song

class PlaybackEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PlaybackEngine"

        @Volatile
        private var instance: PlaybackEngine? = null

        fun getInstance(context: Context): PlaybackEngine {
            return instance ?: synchronized(this) {
                instance ?: PlaybackEngine(context.applicationContext).also { instance = it }
            }
        }

        const val MODE_CONTINUOUS = 0
        const val MODE_STOP_AND_GO = 1
        const val MODE_CROSSFADE = 2
        const val MODE_SHUFFLE = 3
        const val MODE_REPEAT_ONE = 4
    }

    private var songs: List<Song> = emptyList()
    val songList: List<Song> get() = songs
    val songCount: Int get() = songs.size
    var currentSongIndex: Int = 0
        private set
    var endOfPlaylist: Boolean = false
        private set
    var shuffleArray: MutableList<Int> = mutableListOf()
    var shuffleHasStartedPlayback: Boolean = false

    var isPlaying: Boolean = false
        private set
    var playListExists: Boolean = false
        private set
    var stopAndGo: Int = MODE_STOP_AND_GO
        private set
    var trackLength: Double = 0.0
        private set

    private var player: MediaPlayer? = null
    private var fadingOutPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var periodicTimeRunnable: Runnable? = null
    private var crossfadeBoundaryRunnable: Runnable? = null

    // Generation counter to detect stale completion callbacks
    private var playerGeneration: Int = 0

    private var fadeHandler = Handler(Looper.getMainLooper())
    private var fadeRunnable: Runnable? = null
    private var savedVolume: Float = 1.0f
    private var isFading: Boolean = false
    private var currentFadeVolume: Float = 1.0f

    // Crossfade fade-out runnable
    private var crossfadeFadeRunnable: Runnable? = null

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var mediaSession: MediaSession? = null

    // Transient song: when switching playlists while playing, the current song
    // is prepended at index 0 of `songs` as a non-persistent, faded, non-interactive
    // row that keeps playing until it finishes or the user moves off it.
    private var hasTransientSong: Boolean = false
    private var transientArtworkFile: java.io.File? = null
    private val songIndexOffset: Int get() = if (hasTransientSong) 1 else 0

    var stringFin: String = "End of playlist"
    var stringVide: String = "Playlist is currently empty"

    private val prefs = context.getSharedPreferences("stopandgo_prefs", Context.MODE_PRIVATE)

    var onStateChanged: (() -> Unit)? = null

    val currentTime: Double
        get() {
            val p = player ?: return 0.0
            return try {
                p.currentPosition.toDouble() / 1000.0
            } catch (_: Exception) { 0.0 }
        }

    fun startup() {
        stopAndGo = prefs.getInt("stopAndGoKey", MODE_STOP_AND_GO)
        isPlaying = false
        endOfPlaylist = false
        currentSongIndex = 0
        trackLength = 0.0

        reloadFromPlaylistData()

        if (songCount > 0) {
            currentSongIndex = 0
            playListExists = true
            loadPlayers()

            if (stopAndGo == MODE_SHUFFLE) {
                generateShuffleArray()
                firstSongInShuffleArray()
            }
        }

        startMonitorTimer()
        setupAudioSession()
        setupMediaSession()

        if (prefs.getString("playlistName", null) == null) {
            prefs.edit().putString("playlistName", "My first playlist").apply()
        }

        val xfadetime = prefs.getFloat("xfadetime", 0f)
        if (xfadetime > 16f || xfadetime < 1f) {
            prefs.edit()
                .putBoolean("autolock", true)
                .putBoolean("repeatall", false)
                .putFloat("xfadetime", 12f)
                .putBoolean("darkmode", false)
                .apply()
        }

        onStateChanged?.invoke()
    }

    fun currentSong(): Song? {
        return if (currentSongIndex in songs.indices) songs[currentSongIndex] else null
    }

    // MARK: - Transport Controls

    fun playPause() { togglePlayPause(userInitiated = true) }
    fun next() { skipToNext() }
    fun previous() { skipToPrevious() }

    fun scrub(to: Double) {
        try { player?.seekTo((to * 1000).toInt()) } catch (_: Exception) {}
    }

    fun scrubPrecise(to: Double, completion: () -> Unit) {
        try {
            val boundary = trackLength
            player?.seekTo((to * 1000).toInt())
            handler.postDelayed({
                completion()
                val mode = stopAndGo
                if ((mode == MODE_CROSSFADE || mode == MODE_SHUFFLE) && boundary > 0 && to >= boundary) {
                    handleCrossfadeBoundary()
                }
            }, 100)
        } catch (_: Exception) {
            completion()
        }
    }

    fun changeMode() { cycleMode() }

    fun clearPlayerQueue() {
        cleanFade()
        isPlaying = false
        playListExists = false
        releaseAllPlayers()
        currentSongIndex = 0
        onStateChanged?.invoke()
    }

    // MARK: - Shuffle Array

    fun generateShuffleArray() {
        val count = songs.size
        if (count == 0) { shuffleArray.clear(); return }

        if (shuffleArray.isEmpty()) {
            shuffleArray = (0 until count).toMutableList()
            shuffleArray.shuffle()
        } else if (shuffleArray.size > count) {
            shuffleArray.removeAll { it >= count }
        } else if (shuffleArray.size < count) {
            for (i in shuffleArray.size until count) { shuffleArray.add(i) }
            val currentPos = shuffleArray.indexOf(currentSongIndex).coerceAtLeast(0)
            val startShuffle = currentPos + 1
            if (startShuffle < count - 1) {
                shuffleArray.subList(startShuffle, count).shuffle()
            }
        }
    }

    fun firstSongInShuffleArray() {
        val idx = shuffleArray.indexOf(currentSongIndex)
        if (idx >= 0) {
            shuffleArray[idx] = shuffleArray[0]
            shuffleArray[0] = currentSongIndex
        }
    }

    private fun rebuildShuffleArray(jumpingTo: Int) {
        val count = songs.size
        if (count <= 1) return
        if (!shuffleHasStartedPlayback) {
            val others = (0 until count).filter { it != jumpingTo }.shuffled()
            shuffleArray = (mutableListOf(jumpingTo) + others).toMutableList()
        } else {
            val currentPos = shuffleArray.indexOf(currentSongIndex).coerceAtLeast(0)
            val played = shuffleArray.subList(0, currentPos + 1).filter { it != jumpingTo }
            val unplayed = shuffleArray.subList(currentPos + 1, shuffleArray.size)
                .filter { it != jumpingTo }.shuffled()
            shuffleArray = (played + listOf(jumpingTo) + unplayed).toMutableList()
        }
    }

    // MARK: - Song Navigation

    private fun incrementIndex(allowRepeatOne: Boolean) {
        val mode = stopAndGo
        val repeatAll = prefs.getBoolean("repeatall", false)
        val totalSongs = songs.size

        if (allowRepeatOne && mode == MODE_REPEAT_ONE) {
            // index stays the same
        } else if (mode == MODE_SHUFFLE) {
            val pos = shuffleArray.indexOf(currentSongIndex)
            if (pos >= 0) {
                val nextPos = pos + 1
                if (nextPos >= totalSongs) {
                    shuffleArray.clear()
                    generateShuffleArray()
                    shuffleHasStartedPlayback = false
                    currentSongIndex = if (repeatAll) shuffleArray.firstOrNull() ?: 0 else totalSongs
                } else {
                    currentSongIndex = shuffleArray[nextPos]
                }
            } else {
                currentSongIndex++
            }
        } else {
            currentSongIndex++
        }

        if (repeatAll && currentSongIndex >= totalSongs) {
            currentSongIndex = if (mode == MODE_SHUFFLE) shuffleArray.firstOrNull() ?: 0 else 0
        }
    }

    private fun advanceToNextSong() {
        val totalSongs = songs.size
        incrementIndex(allowRepeatOne = true)
        loadPlayers()

        if (currentSongIndex >= totalSongs) {
            isPlaying = false
            endOfPlaylist = true
        }
    }

    private fun skipToNext() {
        if (!playListExists || endOfPlaylist) return

        if (hasTransientSong && currentSongIndex == 0) {
            cleanFade()
            val wasPlaying = isPlaying
            val mode = stopAndGo
            if (wasPlaying && (mode == MODE_CROSSFADE || mode == MODE_SHUFFLE)) {
                crossfadeFromTransient()
            } else {
                leaveTransientAndStart(wasPlaying = wasPlaying)
            }
            return
        }

        cleanFade()
        val mode = stopAndGo
        val wasPlaying = isPlaying

        // Pause + detach old player before anything else
        detachCurrentPlayer()

        incrementIndex(allowRepeatOne = false)

        if (currentSongIndex >= songs.size) {
            endOfPlaylist = true
            isPlaying = false
            loadPlayers()
            onStateChanged?.invoke()
            updateMediaSessionState()
            return
        }

        loadPlayers()

        // Clean up the old player (now fadingOutPlayer)
        if (mode == MODE_CROSSFADE || mode == MODE_SHUFFLE) {
            fadeOutFadingPlayer()
        } else {
            safeRelease(fadingOutPlayer)
            fadingOutPlayer = null
        }

        if (wasPlaying) {
            startPlayback()
        }
        onStateChanged?.invoke()
        updateMediaSessionState()
    }

    private fun decrementIndex() {
        if (stopAndGo == MODE_SHUFFLE) {
            val pos = shuffleArray.indexOf(currentSongIndex)
            if (pos >= 0) {
                if (pos - 1 < 0) {
                    shuffleArray.clear()
                    generateShuffleArray()
                    shuffleHasStartedPlayback = false
                    currentSongIndex = shuffleArray.firstOrNull() ?: 0
                } else {
                    currentSongIndex = shuffleArray[pos - 1]
                }
            } else {
                currentSongIndex--
            }
        } else {
            currentSongIndex--
        }
        if (currentSongIndex < 0) currentSongIndex = 0
    }

    private fun skipToPrevious() {
        if (!playListExists) return

        cleanFade()

        if (hasTransientSong && currentSongIndex == 0) {
            leaveTransientAndStart(wasPlaying = isPlaying)
            return
        }

        if (endOfPlaylist) {
            val lastIndex = songs.size - 1
            if (lastIndex >= 0) jump(to = lastIndex, forcePlay = false)
            return
        }

        val mode = stopAndGo
        val wasPlaying = isPlaying

        detachCurrentPlayer()
        decrementIndex()
        loadPlayers()

        if (wasPlaying) {
            if (mode == MODE_CROSSFADE || mode == MODE_SHUFFLE) {
                fadeOutFadingPlayer()
            } else {
                safeRelease(fadingOutPlayer)
                fadingOutPlayer = null
            }
            startPlayback()
        }
        onStateChanged?.invoke()
        updateMediaSessionState()
    }

    fun jump(to: Int, forcePlay: Boolean) {
        if (to >= songs.size) return
        if (hasTransientSong && to == 0) return // transient is non-interactive

        cleanFade()

        val mode = stopAndGo
        val wasPlaying = isPlaying

        if (hasTransientSong) {
            // Drop the transient and jump within the new playlist.
            val realIndex = (to - 1).coerceAtLeast(0)
            clearTransientSong()
            endOfPlaylist = false
            if (songs.isEmpty()) {
                playListExists = false
                isPlaying = false
                onStateChanged?.invoke()
                updateMediaSessionState()
                return
            }
            playListExists = true
            currentSongIndex = realIndex.coerceAtMost(songs.size - 1)
            if (mode == MODE_SHUFFLE && songs.size > 1) {
                shuffleArray.clear()
                shuffleHasStartedPlayback = false
                rebuildShuffleArray(jumpingTo = currentSongIndex)
            }
            loadPlayers()
            when {
                forcePlay -> startPlayback()
                wasPlaying -> {
                    isPlaying = false
                    try { player?.pause() } catch (_: Exception) {}
                }
            }
            onStateChanged?.invoke()
            updateMediaSessionState()
            return
        }

        if (mode == MODE_SHUFFLE && songs.size > 1) {
            rebuildShuffleArray(jumpingTo = to)
        }

        detachCurrentPlayer()
        currentSongIndex = to
        endOfPlaylist = false
        loadPlayers()

        if (wasPlaying || forcePlay) {
            if (mode == MODE_CROSSFADE || mode == MODE_SHUFFLE) {
                fadeOutFadingPlayer()
            } else {
                safeRelease(fadingOutPlayer)
                fadingOutPlayer = null
            }
        }

        when {
            forcePlay -> startPlayback()
            wasPlaying && !forcePlay -> {
                isPlaying = false
                try { player?.pause() } catch (_: Exception) {}
            }
        }
        onStateChanged?.invoke()
        updateMediaSessionState()
    }

    private fun restartFromBeginning() {
        val mode = stopAndGo
        endOfPlaylist = false
        currentSongIndex = if (mode == MODE_SHUFFLE) shuffleArray.firstOrNull() ?: 0 else 0

        loadPlayers()

        if (mode != MODE_STOP_AND_GO) {
            startPlayback()
        } else {
            isPlaying = false
        }
        onStateChanged?.invoke()
    }

    // MARK: - End-of-Track Handlers

    private fun handleTrackEnded(generation: Int) {
        // Ignore stale callbacks from old players
        if (generation != playerGeneration) {
            Log.d(TAG, "handleTrackEnded: STALE callback (gen=$generation, current=$playerGeneration), ignoring")
            return
        }

        if (hasTransientSong && currentSongIndex == 0) {
            Log.d(TAG, "handleTrackEnded: transient finished, advancing into new playlist")
            handleTransientEnded()
            return
        }

        Log.d(TAG, "handleTrackEnded: index=$currentSongIndex, mode=$stopAndGo")
        advanceToNextSong()

        if (endOfPlaylist) {
            onStateChanged?.invoke()
            updateMediaSessionState()
            return
        }

        if (stopAndGo == MODE_STOP_AND_GO) {
            isPlaying = false
        } else {
            startPlayback()
        }
        onStateChanged?.invoke()
        updateMediaSessionState()
    }

    private fun handleCrossfadeBoundary() {
        crossfadeBoundaryRunnable?.let { handler.removeCallbacks(it) }
        crossfadeBoundaryRunnable = null

        if (hasTransientSong && currentSongIndex == 0) {
            Log.d(TAG, "handleCrossfadeBoundary: crossfading from transient")
            crossfadeFromTransient()
            return
        }

        Log.d(TAG, "handleCrossfadeBoundary: crossfading at index=$currentSongIndex")

        advanceToNextSong()

        if (endOfPlaylist) {
            onStateChanged?.invoke()
            updateMediaSessionState()
            return
        }

        // Fade out the old player (now fadingOutPlayer) matching iOS's volume ramp
        fadeOutFadingPlayer()
        startPlayback()
        onStateChanged?.invoke()
        updateMediaSessionState()
    }

    // MARK: - Player Management

    /**
     * Detaches the completion listener from the current player to prevent
     * stale callbacks. In non-crossfade modes, also pauses playback.
     * In crossfade/shuffle modes, the player keeps playing so it can
     * be faded out while the next song starts (true crossfade).
     */
    private fun detachCurrentPlayer() {
        player?.let { p ->
            try { p.setOnCompletionListener(null) } catch (_: Exception) {}
            val mode = stopAndGo
            if (mode != MODE_CROSSFADE && mode != MODE_SHUFFLE) {
                try { p.pause() } catch (_: Exception) {}
            }
            // In crossfade/shuffle: player keeps playing — it will be
            // shifted to fadingOutPlayer by loadPlayers() and then
            // fadeOutFadingPlayer() will gradually lower its volume.
        }
    }

    /**
     * Creates a new MediaPlayer for the current song index.
     * Shifts the current player to fadingOutPlayer for crossfade.
     */
    private fun loadPlayers() {
        Log.d(TAG, "loadPlayers: index=$currentSongIndex, songs=${songs.size}")
        stopMonitorTimer()
        removeCrossfadeBoundary()

        // Bump generation so any pending completion callbacks from old players are ignored
        playerGeneration++

        if (!playListExists) {
            releaseAllPlayers()
            startMonitorTimer()
            return
        }

        // Shift current player to fading-out slot
        if (fadingOutPlayer != null && fadingOutPlayer !== player) {
            safeRelease(fadingOutPlayer)
        }
        fadingOutPlayer = player
        player = null

        if (currentSongIndex >= songs.size) {
            startMonitorTimer()
            return
        }

        val song = songs[currentSongIndex]
        val uri = song.uri
        Log.d(TAG, "loadPlayers: song='${song.title}', uri=$uri")

        if (uri == null) {
            Log.w(TAG, "loadPlayers: null URI, skipping")
            // Don't restore fadingOutPlayer — it's the old song's player
            startMonitorTimer()
            skipToNext()
            return
        }

        // Create new MediaPlayer
        val gen = playerGeneration
        try {
            val newPlayer = MediaPlayer()
            newPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )

            // Use file path directly for file:// URIs
            val path = if (uri.scheme == "file") uri.path else null
            if (path != null) {
                newPlayer.setDataSource(path)
            } else {
                newPlayer.setDataSource(context, uri)
            }

            newPlayer.prepare()

            // Update song duration from actual player if metadata had 0
            val actualDuration = newPlayer.duration / 1000.0
            if (song.duration <= 0 && actualDuration > 0) {
                val updatedSongs = songs.toMutableList()
                updatedSongs[currentSongIndex] = song.copy(duration = actualDuration)
                songs = updatedSongs
            }

            // Completion listener uses generation to detect staleness
            newPlayer.setOnCompletionListener {
                handler.post {
                    val currentMode = stopAndGo
                    if (currentMode != MODE_CROSSFADE && currentMode != MODE_SHUFFLE) {
                        handleTrackEnded(gen)
                    }
                }
            }

            player = newPlayer
            Log.d(TAG, "loadPlayers: OK, duration=${newPlayer.duration}ms, gen=$gen")

            if (stopAndGo == MODE_CROSSFADE || stopAndGo == MODE_SHUFFLE) {
                configureBoundaryObserver()
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadPlayers: FAILED", e)
            // Don't assign fadingOutPlayer to player — it may be released
            player = null
            startMonitorTimer()
            return
        }

        startMonitorTimer()
        onStateChanged?.invoke()
    }

    private fun safeRelease(mp: MediaPlayer?) {
        if (mp == null) return
        try { mp.setOnCompletionListener(null) } catch (_: Exception) {}
        try { mp.reset() } catch (_: Exception) {}
        try { mp.release() } catch (_: Exception) {}
    }

    private fun releaseAllPlayers() {
        safeRelease(player)
        player = null
        safeRelease(fadingOutPlayer)
        fadingOutPlayer = null
    }

    private fun removeCrossfadeBoundary() {
        crossfadeBoundaryRunnable?.let { handler.removeCallbacks(it) }
        crossfadeBoundaryRunnable = null
    }

    // MARK: - Boundary Observer (crossfade)

    private fun configureBoundaryObserver() {
        removeCrossfadeBoundary()

        val xfadetime = prefs.getFloat("xfadetime", 12f).toDouble()
        val duration = if (currentSongIndex < songs.size) songs[currentSongIndex].duration else 0.0

        var tl = duration - xfadetime
        if (tl < xfadetime) tl = duration / 1.5
        trackLength = tl

        if (trackLength <= 0) return

        val checkInterval = 200L
        val runnable = object : Runnable {
            override fun run() {
                if (currentTime >= trackLength && trackLength > 0 && isPlaying) {
                    handleCrossfadeBoundary()
                } else {
                    handler.postDelayed(this, checkInterval)
                }
            }
        }
        crossfadeBoundaryRunnable = runnable
        handler.postDelayed(runnable, checkInterval)
    }

    // MARK: - Playback Start

    private fun startPlayback() {
        val p = player
        if (p == null) {
            Log.w(TAG, "startPlayback: player is null!")
            isPlaying = false
            onStateChanged?.invoke()
            return
        }
        try {
            p.start()
            isPlaying = true
            Log.d(TAG, "startPlayback: OK, index=$currentSongIndex")
            if (stopAndGo == MODE_SHUFFLE) {
                shuffleHasStartedPlayback = true
            }
            requestAudioFocus()
            updateMediaSessionState()
        } catch (e: Exception) {
            Log.e(TAG, "startPlayback: FAILED", e)
            isPlaying = false
        }
        onStateChanged?.invoke()
    }

    // MARK: - Crossfade Fade-Out

    /**
     * Fades out fadingOutPlayer from 1.0→0.0 over min(xfadeTime, 6) seconds.
     * Matches iOS's AVAudioMix linear volume ramp.
     */
    private fun fadeOutFadingPlayer() {
        val fp = fadingOutPlayer ?: return

        crossfadeFadeRunnable?.let { handler.removeCallbacks(it) }

        val xfadeTime = prefs.getFloat("xfadetime", 12f).toDouble()
        val fadeDurationMs = (minOf(xfadeTime, 6.0) * 1000).toLong()
        val fadeSteps = 30
        val fadeInterval = (fadeDurationMs / fadeSteps).coerceAtLeast(50)
        var step = 0

        val runnable = object : Runnable {
            override fun run() {
                step++
                val volume = (1.0f - step.toFloat() / fadeSteps).coerceAtLeast(0f)
                try { fp.setVolume(volume, volume) } catch (_: Exception) {}
                if (step >= fadeSteps) {
                    safeRelease(fp)
                    if (fadingOutPlayer === fp) fadingOutPlayer = null
                    crossfadeFadeRunnable = null
                } else {
                    handler.postDelayed(this, fadeInterval)
                }
            }
        }
        crossfadeFadeRunnable = runnable
        handler.postDelayed(runnable, fadeInterval)
    }

    // MARK: - Soft Pause Fade

    private fun softPause() {
        val p = player ?: return
        savedVolume = currentFadeVolume
        val step = savedVolume / 10.0f
        isFading = true
        fadeRunnable?.let { fadeHandler.removeCallbacks(it) }

        var vol = savedVolume
        fadeRunnable = object : Runnable {
            override fun run() {
                vol -= step
                if (vol <= 0) {
                    try { p.setVolume(0f, 0f); p.pause(); p.setVolume(savedVolume, savedVolume) } catch (_: Exception) {}
                    currentFadeVolume = savedVolume
                    isFading = false
                    fadeRunnable = null
                } else {
                    try { p.setVolume(vol, vol) } catch (_: Exception) {}
                    currentFadeVolume = vol
                    fadeHandler.postDelayed(this, 100)
                }
            }
        }
        fadeHandler.postDelayed(fadeRunnable!!, 100)
    }

    private fun softResume() {
        val p = player ?: return
        val targetVol = if (savedVolume > 0 && savedVolume <= 1.0f) savedVolume else 1.0f
        try { p.setVolume(0f, 0f) } catch (_: Exception) {}
        currentFadeVolume = 0f
        isFading = true
        fadeRunnable?.let { fadeHandler.removeCallbacks(it) }

        startPlayback()

        val step = targetVol / 10.0f
        var vol = 0f
        fadeRunnable = object : Runnable {
            override fun run() {
                vol += step
                if (vol >= targetVol) {
                    try { p.setVolume(targetVol, targetVol) } catch (_: Exception) {}
                    currentFadeVolume = targetVol
                    isFading = false
                    fadeRunnable = null
                } else {
                    try { p.setVolume(vol, vol) } catch (_: Exception) {}
                    currentFadeVolume = vol
                    fadeHandler.postDelayed(this, 100)
                }
            }
        }
        fadeHandler.postDelayed(fadeRunnable!!, 100)
    }

    fun cleanFade() {
        fadeRunnable?.let { fadeHandler.removeCallbacks(it) }
        fadeRunnable = null
        if (isFading) {
            val restoreVol = if (savedVolume > 0 && savedVolume <= 1.0f) savedVolume else 1.0f
            try { player?.setVolume(restoreVol, restoreVol) } catch (_: Exception) {}
            currentFadeVolume = restoreVol
            isFading = false
        }
        crossfadeFadeRunnable?.let { handler.removeCallbacks(it) }
        crossfadeFadeRunnable = null
    }

    // MARK: - Toggle Play/Pause

    private fun togglePlayPause(userInitiated: Boolean) {
        Log.d(TAG, "togglePlayPause: isPlaying=$isPlaying, exists=$playListExists, eop=$endOfPlaylist, player=${player != null}")

        if (!playListExists) return

        if (endOfPlaylist) {
            restartFromBeginning()
            updateMediaSessionState()
            return
        }

        val softPauseEnabled = prefs.getBoolean("softPause", false)

        if (!isPlaying) {
            // If player was lost, recreate it
            if (player == null) {
                Log.w(TAG, "togglePlayPause: player null, reloading")
                loadPlayers()
            }
            if (userInitiated && softPauseEnabled) {
                softResume()
            } else {
                startPlayback()
            }
        } else {
            isPlaying = false
            if (userInitiated && softPauseEnabled) {
                softPause()
            } else {
                try { player?.pause() } catch (_: Exception) {}
            }
            onStateChanged?.invoke()
        }
        updateMediaSessionState()
    }

    // MARK: - Mode Cycling

    private fun cycleMode() {
        val oldMode = stopAndGo
        val newMode = when (oldMode) {
            MODE_STOP_AND_GO -> MODE_CONTINUOUS
            MODE_CONTINUOUS -> MODE_REPEAT_ONE
            MODE_REPEAT_ONE -> MODE_CROSSFADE
            MODE_CROSSFADE -> MODE_SHUFFLE
            else -> MODE_STOP_AND_GO
        }

        val enteringCrossfade = (newMode == MODE_CROSSFADE || newMode == MODE_SHUFFLE) &&
                (oldMode != MODE_CROSSFADE && oldMode != MODE_SHUFFLE)
        val leavingCrossfade = (newMode != MODE_CROSSFADE && newMode != MODE_SHUFFLE) &&
                (oldMode == MODE_CROSSFADE || oldMode == MODE_SHUFFLE)

        stopAndGo = newMode

        if (enteringCrossfade) reconfigureForCrossfadeMode()

        if (newMode == MODE_SHUFFLE && oldMode == MODE_CROSSFADE) {
            val totalSongs = songs.size
            if (totalSongs > 0 && !isPlaying) {
                generateShuffleArray()
                shuffleHasStartedPlayback = false
                val firstShuffled = shuffleArray.firstOrNull() ?: 0
                currentSongIndex = firstShuffled
                jump(to = firstShuffled, forcePlay = false)
            } else if (totalSongs > 1) {
                val others = (0 until totalSongs).filter { it != currentSongIndex }.shuffled()
                shuffleArray = (mutableListOf(currentSongIndex) + others).toMutableList()
                shuffleHasStartedPlayback = true
            } else {
                shuffleArray = if (totalSongs == 1) mutableListOf(0) else mutableListOf()
                shuffleHasStartedPlayback = isPlaying
            }
        }

        if (leavingCrossfade) {
            reconfigureForNonCrossfadeMode()
            cleanFade()
            safeRelease(fadingOutPlayer)
            fadingOutPlayer = null
            shuffleArray.clear()
            shuffleHasStartedPlayback = false
        }

        prefs.edit().putInt("stopAndGoKey", newMode).apply()
        onStateChanged?.invoke()
    }

    private fun reconfigureForCrossfadeMode() {
        if (endOfPlaylist || !playListExists || currentSongIndex >= songs.size) return
        configureBoundaryObserver()
    }

    private fun reconfigureForNonCrossfadeMode() {
        removeCrossfadeBoundary()
    }

    fun updateCrossfadeTiming() {
        if (stopAndGo != MODE_CROSSFADE && stopAndGo != MODE_SHUFFLE) return
        if (endOfPlaylist || !playListExists || currentSongIndex >= songs.size) return
        configureBoundaryObserver()
    }

    // MARK: - Time Display

    fun updateTimeDisplay(): Double {
        if (!endOfPlaylist && currentSongIndex < songs.size && songs[currentSongIndex].uri != null) {
            return currentTime
        }
        return 0.0
    }

    fun getMaxTime(): Double {
        return if (currentSongIndex < songs.size) songs[currentSongIndex].duration else 0.0
    }

    private fun startMonitorTimer() {
        stopMonitorTimer()
        periodicTimeRunnable = object : Runnable {
            override fun run() {
                onStateChanged?.invoke()
                handler.postDelayed(this, 500)
            }
        }
        handler.postDelayed(periodicTimeRunnable!!, 500)
    }

    private fun stopMonitorTimer() {
        periodicTimeRunnable?.let { handler.removeCallbacks(it) }
        periodicTimeRunnable = null
    }

    fun reloadFromPlaylistData() {
        val realSongs = PlaylistData.getInstance(context).getSongs()
        songs = if (hasTransientSong && songs.isNotEmpty()) {
            listOf(songs[0]) + realSongs
        } else {
            realSongs
        }
    }

    // MARK: - Audio Focus

    private var hasAudioFocus = false

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d(TAG, "onAudioFocusChange: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss — another app took focus
                hasAudioFocus = false
                if (isPlaying) togglePlayPause(userInitiated = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporary loss — pause but keep focus request
                if (isPlaying) togglePlayPause(userInitiated = false)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Regained focus after transient loss
                hasAudioFocus = true
            }
        }
    }

    private fun setupAudioSession() {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Build the focus request once with a single, stable listener
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            .setOnAudioFocusChangeListener(audioFocusListener, handler)
            .build()
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return // Already have focus — don't re-request
        val am = audioManager ?: return
        val req = audioFocusRequest ?: return
        val result = am.requestAudioFocus(req)
        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        Log.d(TAG, "requestAudioFocus: result=$result, granted=$hasAudioFocus")
    }

    // MARK: - Media Session

    val notificationChannelId = "stopandgo_playback"
    val notificationId = 1001

    private fun setupMediaSession() {
        mediaSession = MediaSession(context, "StopAndGoPlayer").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { if (!isPlaying) togglePlayPause(userInitiated = true) }
                override fun onPause() { if (isPlaying) togglePlayPause(userInitiated = true) }
                override fun onSkipToNext() { skipToNext() }
                override fun onSkipToPrevious() { skipToPrevious() }
            })
            isActive = true
        }
        createNotificationChannel()
        updateMediaSessionState()
    }

    private fun createNotificationChannel() {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        val channel = android.app.NotificationChannel(
            notificationChannelId,
            "Playback",
            android.app.NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Media playback controls"
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    /** Builds the playback notification. Used by both the engine and PlaybackService. */
    fun buildPlaybackNotification(): android.app.Notification? {
        val session = mediaSession ?: return null
        val song = currentSong() ?: return null

        val artworkBitmap: android.graphics.Bitmap? = try {
            val file = if (hasTransientSong && currentSongIndex == 0) transientArtworkFile
                       else PlaylistData.getInstance(context).artworkFile(forSongAt = currentSongIndex)
            file?.let { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }
        } catch (_: Exception) { null }

        val contentIntent = android.app.PendingIntent.getActivity(
            context, 0,
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        return android.app.Notification.Builder(context, notificationChannelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(song.title ?: "")
            .setContentText(song.artist ?: "")
            .setSubText(song.album ?: "")
            .setLargeIcon(artworkBitmap)
            .setContentIntent(contentIntent)
            .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setStyle(
                android.app.Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(
                android.app.Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_media_previous),
                    "Previous",
                    buildMediaActionIntent("prev")
                ).build()
            )
            .addAction(
                android.app.Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(
                        context,
                        if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                    ),
                    if (isPlaying) "Pause" else "Play",
                    buildMediaActionIntent("playpause")
                ).build()
            )
            .addAction(
                android.app.Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_media_next),
                    "Next",
                    buildMediaActionIntent("next")
                ).build()
            )
            .build()
    }

    private fun postOrUpdateNotification() {
        val notification = buildPlaybackNotification() ?: return
        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return

        // Start the foreground service when playing so the OS keeps the process alive.
        // Stop it when paused so the user can swipe the notification away.
        try {
            val serviceIntent = android.content.Intent(context, PlaybackService::class.java)
            if (isPlaying) {
                androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                // Demote the foreground notification (still posted, but cancellable)
                context.stopService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start/stop PlaybackService: ${e.message}")
        }

        try {
            nm.notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted: ${e.message}")
        }
    }

    private fun buildMediaActionIntent(action: String): android.app.PendingIntent {
        val intent = android.content.Intent("fr.yvz.stopandgo.MEDIA_ACTION").apply {
            setPackage(context.packageName)
            putExtra("action", action)
        }
        return android.app.PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private val mediaActionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.getStringExtra("action")) {
                "playpause" -> togglePlayPause(userInitiated = true)
                "next" -> skipToNext()
                "prev" -> skipToPrevious()
            }
        }
    }

    init {
        // Register broadcast receiver for notification actions
        try {
            context.registerReceiver(
                mediaActionReceiver,
                android.content.IntentFilter("fr.yvz.stopandgo.MEDIA_ACTION"),
                android.content.Context.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {}
    }

    private fun updateMediaSessionState() {
        val state = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_PLAY_PAUSE
            )
            .setState(
                if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                (currentTime * 1000).toLong(), 1.0f
            )
            .build()
        mediaSession?.setPlaybackState(state)

        currentSong()?.let { song ->
            val builder = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, song.title ?: "")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, song.artist ?: "")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, song.album ?: "")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, (song.duration * 1000).toLong())

            // Load artwork bitmap from playlist folder, if any
            val artworkFile = if (hasTransientSong && currentSongIndex == 0) transientArtworkFile
                              else PlaylistData.getInstance(context).artworkFile(forSongAt = currentSongIndex)
            if (artworkFile != null) {
                try {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(artworkFile.absolutePath)
                    if (bitmap != null) {
                        builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
                        builder.putBitmap(MediaMetadata.METADATA_KEY_ART, bitmap)
                    }
                } catch (_: Exception) {}
            }

            mediaSession?.setMetadata(builder.build())
        }

        // Update the lock screen / notification panel media controls
        if (playListExists) {
            postOrUpdateNotification()
        }
    }

    // MARK: - Playlist Editing

    fun deleteSong(at: Int) {
        if (hasTransientSong && at == 0) return // transient cannot be deleted

        val wasPlaying = isPlaying
        val playlistData = PlaylistData.getInstance(context)
        val offset = songIndexOffset
        val dataIndex = at - offset
        val realSongCount = songs.size - offset

        // If deleting the only real song and no transient is active, tear down
        // the player entirely. When transient is active, keep playing.
        if (realSongCount == 1 && !hasTransientSong) {
            playlistData.removeSong(at = 0)
            playlistData.saveCurrentPlaylist()
            clearPlayerQueue()
            reloadFromPlaylistData()
            onStateChanged?.invoke()
            return
        }

        val deletingCurrentSong = (at == currentSongIndex)

        if (at < currentSongIndex) {
            currentSongIndex--
        } else if (at == currentSongIndex && currentSongIndex >= songs.size - 1) {
            currentSongIndex = (songs.size - 2).coerceAtLeast(offset)
        }

        playlistData.removeSong(at = dataIndex)
        playlistData.saveCurrentPlaylist()
        reloadFromPlaylistData()

        if (deletingCurrentSong && !hasTransientSong) {
            loadPlayers()
            if (wasPlaying) startPlayback()
        }
        onStateChanged?.invoke()
    }

    fun moveSong(from: Int, to: Int) {
        val offset = songIndexOffset
        // Transient slot (index 0) can't be moved, and nothing can be
        // inserted above it.
        if (hasTransientSong && (from == 0 || to == 0)) return

        val adjustedDest = if (to > from) to - 1 else to
        if (currentSongIndex == from) {
            currentSongIndex = adjustedDest
        } else if (from < currentSongIndex && adjustedDest >= currentSongIndex) {
            currentSongIndex--
        } else if (from > currentSongIndex && adjustedDest <= currentSongIndex) {
            currentSongIndex++
        }

        val playlistData = PlaylistData.getInstance(context)
        playlistData.moveSong(from - offset, to - offset)
        playlistData.saveCurrentPlaylist()
        reloadFromPlaylistData()
        onStateChanged?.invoke()
    }

    fun updateSong(at: Int, title: String?, artist: String?, album: String?, notes: String?, artworkBitmap: android.graphics.Bitmap?) {
        if (hasTransientSong && at == 0) return // transient is not editable
        val dataIndex = at - songIndexOffset
        PlaylistData.getInstance(context).updateSong(dataIndex, title, artist, album, notes, artworkBitmap)
        reloadFromPlaylistData()
        onStateChanged?.invoke()
    }

    fun addFilesFromDocumentPicker(uris: List<Uri>) {
        val realWasEmpty = (songs.size - songIndexOffset) <= 0
        val playlistData = PlaylistData.getInstance(context)
        for (uri in uris) {
            playlistData.addFileFromUri(uri)
        }
        playlistData.saveCurrentPlaylist()
        reloadFromPlaylistData()

        if (hasTransientSong) {
            // Keep transient playing; don't touch the player.
            onStateChanged?.invoke()
            return
        }

        if (realWasEmpty && songs.isNotEmpty()) {
            playListExists = true
            currentSongIndex = 0
            endOfPlaylist = false
        }
        loadPlayers()
        onStateChanged?.invoke()
    }

    // MARK: - Playlist switching

    fun switchToPlaylist(name: String) {
        val playlistData = PlaylistData.getInstance(context)
        if (playlistData.currentPlaylistName == name) return

        val currentSnapshot = currentSong()
        val canKeepPlaying = isPlaying && player != null && !endOfPlaylist &&
                currentSnapshot != null && currentSnapshot.uri != null

        if (canKeepPlaying) {
            // Keep the current MediaPlayer running; prepend the playing song as
            // a transient row at index 0 of the new playlist.
            if (!hasTransientSong) {
                transientArtworkFile = playlistData.artworkFile(forSongAt = currentSongIndex)
            }
            val baseId = currentSnapshot!!.id.removeSuffix("|transient")
            val transient = currentSnapshot.copy(
                id = "$baseId|transient",
                isTransient = true
            )

            playlistData.switchToPlaylist(name)
            val realSongs = playlistData.getSongs()
            songs = listOf(transient) + realSongs
            hasTransientSong = true
            currentSongIndex = 0
            endOfPlaylist = false
            playListExists = true

            // Shuffle array is for the underlying real playlist; build after
            // the transient is removed.
            if (stopAndGo == MODE_SHUFFLE) {
                shuffleArray.clear()
                shuffleHasStartedPlayback = false
            }

            // In crossfade/shuffle, schedule the boundary observer so the
            // transient crossfades into the new playlist near its end.
            if (stopAndGo == MODE_CROSSFADE || stopAndGo == MODE_SHUFFLE) {
                configureBoundaryObserver()
            }

            onStateChanged?.invoke()
            updateMediaSessionState()
            return
        }

        // Not playing — original behavior.
        detachCurrentPlayer()
        isPlaying = false
        hasTransientSong = false
        transientArtworkFile = null

        playlistData.switchToPlaylist(name)
        reloadFromPlaylistData()

        currentSongIndex = 0
        endOfPlaylist = false

        if (songs.isNotEmpty()) {
            playListExists = true
            if (stopAndGo == MODE_SHUFFLE) {
                shuffleArray.clear()
                generateShuffleArray()
                shuffleHasStartedPlayback = false
                currentSongIndex = shuffleArray.firstOrNull() ?: 0
            }
            loadPlayers()
        } else {
            playListExists = false
        }

        onStateChanged?.invoke()
        updateMediaSessionState()
    }

    /**
     * Releases the transient player and repopulates `songs` with the new
     * playlist's real songs. Leaves currentSongIndex/playback state to the caller.
     */
    private fun clearTransientSong() {
        safeRelease(player)
        player = null
        safeRelease(fadingOutPlayer)
        fadingOutPlayer = null
        hasTransientSong = false
        transientArtworkFile = null
        reloadFromPlaylistData()
        removeCrossfadeBoundary()
    }

    private fun leaveTransientAndStart(wasPlaying: Boolean) {
        clearTransientSong()
        currentSongIndex = 0
        endOfPlaylist = false
        if (songs.isEmpty()) {
            playListExists = false
            isPlaying = false
            onStateChanged?.invoke()
            updateMediaSessionState()
            return
        }
        playListExists = true
        if (stopAndGo == MODE_SHUFFLE) {
            shuffleArray.clear()
            generateShuffleArray()
            shuffleHasStartedPlayback = false
            currentSongIndex = shuffleArray.firstOrNull() ?: 0
        }
        loadPlayers()
        if (wasPlaying) startPlayback() else isPlaying = false
        onStateChanged?.invoke()
        updateMediaSessionState()
    }

    /**
     * Transient's MediaPlayer completed naturally (non-crossfade modes).
     * In STOP_AND_GO we pause at the new playlist's first song; in other
     * non-crossfade modes we keep playing.
     */
    private fun handleTransientEnded() {
        clearTransientSong()
        currentSongIndex = 0
        endOfPlaylist = false
        if (songs.isEmpty()) {
            playListExists = false
            isPlaying = false
            onStateChanged?.invoke()
            updateMediaSessionState()
            return
        }
        playListExists = true
        if (stopAndGo == MODE_SHUFFLE) {
            // Shuffle uses boundary observer, not this path — defensive only.
            shuffleArray.clear()
            generateShuffleArray()
            shuffleHasStartedPlayback = false
            currentSongIndex = shuffleArray.firstOrNull() ?: 0
        }
        loadPlayers()
        if (stopAndGo == MODE_STOP_AND_GO) {
            isPlaying = false
        } else {
            startPlayback()
        }
        onStateChanged?.invoke()
        updateMediaSessionState()
    }

    /**
     * Transient crosses the crossfade boundary — fade it out while the new
     * playlist's first (or shuffle-start) song fades in.
     */
    private fun crossfadeFromTransient() {
        // Detach transient player so its completion doesn't fire.
        val transientPlayer = player
        try { transientPlayer?.setOnCompletionListener(null) } catch (_: Exception) {}

        hasTransientSong = false
        transientArtworkFile = null
        reloadFromPlaylistData()
        currentSongIndex = 0
        endOfPlaylist = false

        if (songs.isEmpty()) {
            // No real songs — fade the transient out into silence.
            safeRelease(fadingOutPlayer)
            fadingOutPlayer = transientPlayer
            player = null
            fadeOutFadingPlayer()
            playListExists = false
            isPlaying = false
            onStateChanged?.invoke()
            updateMediaSessionState()
            return
        }

        playListExists = true
        if (stopAndGo == MODE_SHUFFLE) {
            shuffleArray.clear()
            generateShuffleArray()
            shuffleHasStartedPlayback = false
            currentSongIndex = shuffleArray.firstOrNull() ?: 0
        }
        // loadPlayers() will shift the current player (the transient) into
        // fadingOutPlayer and create a new player for the real song.
        loadPlayers()
        fadeOutFadingPlayer()
        startPlayback()
        onStateChanged?.invoke()
        updateMediaSessionState()
    }

    fun createNewPlaylist(name: String): String? {
        val playlistData = PlaylistData.getInstance(context)
        val finalName = playlistData.createPlaylist(name) ?: return null
        playlistData.playlists = playlistData.loadPlaylistsList()
        switchToPlaylist(finalName)
        return finalName
    }
}
