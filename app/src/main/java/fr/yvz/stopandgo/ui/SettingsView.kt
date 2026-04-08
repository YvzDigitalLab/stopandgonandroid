package fr.yvz.stopandgo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.yvz.stopandgo.ui.theme.ColorThemes

@Composable
fun SettingsView(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .padding(top = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Text(
                        text = "Settings",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Crossfade section
                SectionHeader("Crossfade")
                Column {
                    val formatted = if (viewModel.xfadeTime == viewModel.xfadeTime.toInt().toDouble()) {
                        "${viewModel.xfadeTime.toInt()}"
                    } else {
                        String.format("%.1f", viewModel.xfadeTime)
                    }
                    Text("Crossfade duration: $formatted s")
                    Slider(
                        value = viewModel.xfadeTime.toFloat(),
                        onValueChange = { viewModel.setXfadeTimePref(it.toDouble()) },
                        valueRange = 1f..23f,
                        steps = 43, // 0.5 step: (23-1)/0.5 - 1 = 43
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Gray,
                            activeTrackColor = Color.Gray
                        )
                    )
                }

                // Playback section
                SectionHeader("Playback")
                SettingsToggle("Repeat playlists", viewModel.repeatAll) { viewModel.setRepeatAllPref(it) }
                SettingsToggle("Soft pause", viewModel.softPause) { viewModel.setSoftPausePref(it) }
                SettingsToggle("Tap song in playlist to play", viewModel.playOnTap) { viewModel.setPlayOnTapPref(it) }

                // Display section
                SectionHeader("Display")
                SettingsToggle("Dark side of the Moon", viewModel.darkMode) { viewModel.setDarkModePref(it) }
                SettingsToggle("Disable Auto-Lock", viewModel.autoLock) { viewModel.setAutoLockPref(it) }
                SettingsToggle("Show seek bar", viewModel.showSeekBar) { viewModel.setShowSeekBarPref(it) }
                SettingsToggle("Show volume control", viewModel.showVolumeView) { viewModel.setShowVolumePref(it) }

                // Theme section
                SectionHeader("Theme")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    ColorThemes.allThemeNames.forEach { themeName ->
                        ThemeCircle(
                            themeName = themeName,
                            isSelected = viewModel.colorTheme == themeName,
                            onClick = { viewModel.setColorThemePref(themeName) }
                        )
                    }
                }

                // Acknowledgements
                SectionHeader("Acknowledgements")
                Text(
                    text = "Many thanks to Ric Zito for the UI refresh",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 16.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF4CAF50))
        )
    }
}

@Composable
fun ThemeCircle(
    themeName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val theme = ColorThemes.forName(themeName)
    val colors = listOf(theme.menu, theme.mode, theme.previous, theme.play)

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable { onClick() }
            .then(
                if (isSelected)
                    Modifier
                        .border(3.dp, Color.White, CircleShape)
                        .shadow(4.dp, CircleShape)
                else Modifier
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = size.width

            // top-right (lightest)
            drawQuarterArc(colors[0], -90f, centerX, centerY, radius)
            // bottom-right
            drawQuarterArc(colors[1], 0f, centerX, centerY, radius)
            // bottom-left
            drawQuarterArc(colors[2], 90f, centerX, centerY, radius)
            // top-left (darkest)
            drawQuarterArc(colors[3], 180f, centerX, centerY, radius)
        }
    }
}

private fun DrawScope.drawQuarterArc(
    color: Color,
    startAngle: Float,
    centerX: Float,
    centerY: Float,
    radius: Float
) {
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = 90f,
        useCenter = true,
        topLeft = Offset(centerX - radius, centerY - radius),
        size = Size(radius * 2, radius * 2)
    )
}
