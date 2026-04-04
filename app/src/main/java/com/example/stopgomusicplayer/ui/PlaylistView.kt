package com.example.stopgomusicplayer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stopgomusicplayer.data.Song
import com.example.stopgomusicplayer.playback.PlaybackEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistView(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    var showSelectPlaylist by remember { mutableStateOf(false) }
    var editingSongIndex by remember { mutableStateOf<Int?>(null) }
    var songActionIndex by remember { mutableStateOf<Int?>(null) }
    var isEditMode by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addFiles(uris)
        }
    }

    val listState = rememberLazyListState()

    // Scroll to current song on appear
    LaunchedEffect(viewModel.currentPlayingSongIndex) {
        if (viewModel.playlistSongs.isNotEmpty() && viewModel.currentPlayingSongIndex in viewModel.playlistSongs.indices) {
            listState.animateScrollToItem(viewModel.currentPlayingSongIndex)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.reloadPlaylistSongs()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main playlist content
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 15.dp)
                        .padding(bottom = 5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                        TextButton(onClick = { isEditMode = !isEditMode }) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = if (isEditMode) "Done" else "Edit",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isEditMode) "Done" else "Edit",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(
                        text = viewModel.playlistName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Song list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                itemsIndexed(
                    items = viewModel.playlistSongs,
                    key = { _, song -> song.id }
                ) { index, song ->
                    SongRow(
                        song = song,
                        isCurrent = index == viewModel.currentPlayingSongIndex,
                        isEditing = isEditMode,
                        onTap = {
                            if (!isEditMode) {
                                if (viewModel.playOnTap) {
                                    viewModel.handleSongTap(at = index)
                                } else {
                                    songActionIndex = index
                                }
                            }
                        },
                        onMoreTapped = { editingSongIndex = index },
                        onDelete = { viewModel.deleteSong(at = index) }
                    )
                }
            }

            // Bottom bar
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        BottomBarButton(
                            icon = Icons.Default.Add,
                            label = "Import File",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                filePickerLauncher.launch(arrayOf("audio/*"))
                            }
                        )
                        BottomBarButton(
                            icon = Icons.Default.LibraryMusic,
                            label = "Playlists",
                            modifier = Modifier.weight(1f),
                            onClick = { showSelectPlaylist = true }
                        )
                        BottomBarButton(
                            icon = Icons.Default.Settings,
                            label = "Settings",
                            modifier = Modifier.weight(1f),
                            onClick = { showSettings = true }
                        )
                    }
                }
            }
        }

        // Song action dialog
        if (songActionIndex != null) {
            val index = songActionIndex!!
            val song = if (index in viewModel.playlistSongs.indices) viewModel.playlistSongs[index] else null
            if (song != null) {
                AlertDialog(
                    onDismissRequest = { songActionIndex = null },
                    title = { Text(song.title ?: "") },
                    text = null,
                    confirmButton = {},
                    dismissButton = {
                        Column {
                            TextButton(onClick = {
                                viewModel.jumpToSong(at = index, forcePlay = true)
                                songActionIndex = null
                            }) { Text("Play") }
                            TextButton(onClick = {
                                viewModel.jumpToSong(at = index, forcePlay = false)
                                songActionIndex = null
                            }) { Text("Prepare") }
                            if (index != viewModel.currentPlayingSongIndex &&
                                index != viewModel.currentPlayingSongIndex + 1 &&
                                viewModel.mode != PlaybackEngine.MODE_SHUFFLE
                            ) {
                                TextButton(onClick = {
                                    viewModel.moveSongToPlayNext(from = index)
                                    songActionIndex = null
                                }) { Text("Play Next") }
                            }
                            TextButton(onClick = { songActionIndex = null }) { Text("Cancel") }
                        }
                    }
                )
            }
        }

        // Full-screen overlays on top of playlist
        if (showSettings) {
            Surface(modifier = Modifier.fillMaxSize()) {
                SettingsView(viewModel = viewModel, onDismiss = { showSettings = false })
            }
        }

        if (showSelectPlaylist) {
            Surface(modifier = Modifier.fillMaxSize()) {
                SelectPlaylistView(viewModel = viewModel, onDismiss = { showSelectPlaylist = false })
            }
        }

        if (editingSongIndex != null) {
            Surface(modifier = Modifier.fillMaxSize()) {
                EditSongView(
                    viewModel = viewModel,
                    songIndex = editingSongIndex!!,
                    onDismiss = { editingSongIndex = null }
                )
            }
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isEditing: Boolean,
    onTap: () -> Unit,
    onMoreTapped: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (song.uri == null && !song.title.isNullOrEmpty()) {
                Text(
                    text = song.title ?: "",
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = if (song.isImportedFile) "File missing — delete and import again"
                    else "Not available — download or check DRM",
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                Text(
                    text = song.title ?: "Song not on device!",
                    fontSize = 14.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "${song.artist ?: ""} - ${song.album ?: ""}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        if (isEditing) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        } else {
            IconButton(onClick = onMoreTapped) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
fun BottomBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable { onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}
