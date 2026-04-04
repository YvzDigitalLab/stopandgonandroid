package com.example.stopgomusicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stopgomusicplayer.playback.PlaybackEngine
import com.example.stopgomusicplayer.ui.theme.ColorThemes

@Composable
fun PlayerView(viewModel: PlayerViewModel) {
    val theme = ColorThemes.forName(viewModel.colorTheme)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top row: Mode + Playlist (each 50% width, 25% height)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Mode button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(theme.mode)
                        .clickable { viewModel.changeMode() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = modeText(viewModel.mode),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 12.dp, start = 10.dp, end = 10.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ModeIcon(
                            mode = viewModel.mode,
                            modifier = Modifier.size(60.dp)
                        )
                    }
                }

                // Playlist button (phone: shows playlist name, opens playlist)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(theme.menu)
                        .clickable { viewModel.showPlaylist = true },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = viewModel.playlistName,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 12.dp, start = 10.dp, end = 10.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = "Playlist",
                            tint = Color.White,
                            modifier = Modifier.size(60.dp)
                        )
                    }
                }
            }

            // Middle row: Previous + Next (each 50% width, 25% height)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Previous button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(theme.previous)
                        .clickable { viewModel.previous() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(70.dp)
                    )
                }

                // Next button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(theme.next)
                        .clickable { viewModel.next() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(70.dp)
                    )
                }
            }

            // Bottom half: Play/Pause (full width, 50% height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f)
                    .background(theme.play)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { viewModel.playPause() }
            ) {
                // Play icon centered
                Icon(
                    imageVector = playIcon(viewModel.isPlaying),
                    contentDescription = when (viewModel.isPlaying) {
                        0 -> "Pause"
                        -1 -> "Restart"
                        else -> "Play"
                    },
                    tint = Color.White,
                    modifier = Modifier
                        .size(200.dp)
                        .align(Alignment.Center)
                )

                // Song info at top
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(start = 30.dp, end = 30.dp, top = 40.dp)
                ) {
                    Text(
                        text = viewModel.songName,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (viewModel.songSubtitle.isNotEmpty()) {
                        Text(
                            text = viewModel.songSubtitle,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // DRM warning
                if (viewModel.drmNotPlayable) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Song not available — download it or check DRM",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }

                // Time display at bottom-right
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 30.dp,
                            bottom = if (viewModel.showVolumeView) 120.dp else 30.dp
                        ),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "${viewModel.songTimeCurrent} ${viewModel.songTimeSeparator} ${viewModel.songTimeMax}",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Volume slider overlay
                if (viewModel.showVolumeView) {
                    VolumeSliderOverlay(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(theme.play)
                    )
                }
            }
        }

        // Seek bar overlay (centered vertically)
        if (viewModel.showScrubbing && viewModel.maxTime > 0) {
            TimeSlider(
                currentValue = viewModel.currentTime.toFloat(),
                maxValue = viewModel.maxTime.toFloat(),
                onValueChange = { newValue ->
                    viewModel.currentTime = newValue.toDouble()
                    if (viewModel.isCurrentlyScrubbing) {
                        viewModel.scrub(to = newValue.toDouble())
                    }
                },
                onValueChangeStarted = {
                    viewModel.isCurrentlyScrubbing = true
                },
                onValueChangeFinished = {
                    viewModel.isCurrentlyScrubbing = false
                    viewModel.scrub(to = viewModel.currentTime, force = true)
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun TimeSlider(
    currentValue: Float,
    maxValue: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeStarted: () -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Slider(
        value = currentValue.coerceIn(0f, maxValue.coerceAtLeast(0.01f)),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = 0f..maxValue.coerceAtLeast(0.01f),
        modifier = modifier.height(40.dp),
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
        )
    )
}

@Composable
fun VolumeSliderOverlay(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) }
    var currentVolume by remember {
        mutableIntStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
    }

    Box(modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.Black,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .height(50.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RemoveCircleOutline,
                    contentDescription = "Volume low",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
                Slider(
                    value = currentVolume.toFloat() / maxVolume.toFloat(),
                    onValueChange = { fraction ->
                        val newVol = (fraction * maxVolume).toInt().coerceIn(0, maxVolume)
                        currentVolume = newVol
                        audioManager.setStreamVolume(
                            android.media.AudioManager.STREAM_MUSIC,
                            newVol,
                            0
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Icon(
                    imageVector = Icons.Default.AddCircleOutline,
                    contentDescription = "Volume high",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
fun ModeIcon(mode: Int, modifier: Modifier = Modifier) {
    val icon = when (mode) {
        PlaybackEngine.MODE_STOP_AND_GO -> Icons.Default.PanTool
        PlaybackEngine.MODE_CROSSFADE -> Icons.Default.SwapHoriz
        PlaybackEngine.MODE_SHUFFLE -> Icons.Default.Shuffle
        PlaybackEngine.MODE_REPEAT_ONE -> Icons.Default.RepeatOne
        else -> Icons.Default.AllInclusive // Continuous
    }
    Icon(
        imageVector = icon,
        contentDescription = modeText(mode),
        tint = Color.White,
        modifier = modifier
    )
}

fun modeText(mode: Int): String = when (mode) {
    PlaybackEngine.MODE_STOP_AND_GO -> "Stop & Go"
    PlaybackEngine.MODE_CROSSFADE -> "Crossfade"
    PlaybackEngine.MODE_SHUFFLE -> "Shuffle"
    PlaybackEngine.MODE_REPEAT_ONE -> "Repeat 1"
    else -> "Normal"
}

fun playIcon(status: Int) = when (status) {
    -1 -> Icons.Default.RepeatOne
    0 -> Icons.Default.Pause
    else -> Icons.Default.PlayArrow
}
