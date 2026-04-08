package fr.yvz.stopandgo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.yvz.stopandgo.R
import fr.yvz.stopandgo.playback.PlaybackEngine
import fr.yvz.stopandgo.ui.theme.ColorThemes

/**
 * A pressable Box that dims on touch (like iOS SwiftUI Button behavior).
 * Shows a black overlay at ~0.3 alpha while pressed.
 */
@Composable
fun PressableBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val overlayAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.3f else 0f,
        animationSpec = tween(durationMillis = if (isPressed) 0 else 200),
        label = "pressOverlay"
    )

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .drawWithContent {
                drawContent()
                drawRect(Color.Black, alpha = overlayAlpha)
            },
        contentAlignment = contentAlignment,
        content = content
    )
}

@Composable
fun PlayerView(viewModel: PlayerViewModel, isTablet: Boolean = false) {
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
                PressableBox(
                    onClick = { viewModel.changeMode() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(theme.mode)
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
                        Image(
                            painter = painterResource(modeIconRes(viewModel.mode)),
                            contentDescription = modeText(viewModel.mode),
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.size(60.dp)
                        )
                    }
                }

                // Playlist button (or Edit Playlist toggle on tablet)
                PressableBox(
                    onClick = {
                        if (isTablet) {
                            viewModel.playlistEditMode = !viewModel.playlistEditMode
                        } else {
                            viewModel.showPlaylist = true
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(theme.menu)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isTablet) {
                                if (viewModel.playlistEditMode) "Done editing" else "Edit playlist"
                            } else {
                                viewModel.playlistName
                            },
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 12.dp, start = 10.dp, end = 10.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (isTablet) {
                            Icon(
                                imageVector = if (viewModel.playlistEditMode)
                                    Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = if (viewModel.playlistEditMode)
                                    "Done editing" else "Edit playlist",
                                tint = Color.White,
                                modifier = Modifier.size(50.dp)
                            )
                        } else {
                            Image(
                                painter = painterResource(R.drawable.ic_playlist),
                                contentDescription = "Playlist",
                                colorFilter = ColorFilter.tint(Color.White),
                                modifier = Modifier.size(60.dp)
                            )
                        }
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
                PressableBox(
                    onClick = { viewModel.previous() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(theme.previous)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_previous),
                        contentDescription = "Previous",
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(70.dp)
                    )
                }

                // Next button
                PressableBox(
                    onClick = { viewModel.next() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(theme.next)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_next),
                        contentDescription = "Next",
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(70.dp)
                    )
                }
            }

            // Bottom half: Play/Pause (full width, 50% height)
            PressableBox(
                onClick = { viewModel.playPause() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f)
                    .background(theme.play)
            ) {
                // Play/Pause/Restart icon centered
                Image(
                    painter = painterResource(playIconRes(viewModel.isPlaying)),
                    contentDescription = when (viewModel.isPlaying) {
                        0 -> "Pause"
                        -1 -> "Restart"
                        else -> "Play"
                    },
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier
                        .size(90.dp)
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

fun modeText(mode: Int): String = when (mode) {
    PlaybackEngine.MODE_STOP_AND_GO -> "Stop & Go"
    PlaybackEngine.MODE_CROSSFADE -> "Crossfade"
    PlaybackEngine.MODE_SHUFFLE -> "Shuffle"
    PlaybackEngine.MODE_REPEAT_ONE -> "Repeat 1"
    else -> "Normal"
}

fun modeIconRes(mode: Int): Int = when (mode) {
    PlaybackEngine.MODE_STOP_AND_GO -> R.drawable.ic_stop_go
    PlaybackEngine.MODE_CROSSFADE -> R.drawable.ic_crossfade
    PlaybackEngine.MODE_SHUFFLE -> R.drawable.ic_shuffle
    PlaybackEngine.MODE_REPEAT_ONE -> R.drawable.ic_repeat_one
    else -> R.drawable.ic_continuous
}

fun playIconRes(status: Int): Int = when (status) {
    -1 -> R.drawable.ic_repeat_one
    0 -> R.drawable.ic_pause
    else -> R.drawable.ic_play
}
