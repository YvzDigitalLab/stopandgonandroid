package fr.yvz.stopandgo.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
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
import fr.yvz.stopandgo.data.Song
import fr.yvz.stopandgo.playback.PlaybackEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistView(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    isTablet: Boolean = false
) {
    var showSettings by remember { mutableStateOf(false) }
    var showSelectPlaylist by remember { mutableStateOf(false) }
    var editingSongIndex by remember { mutableStateOf<Int?>(null) }
    var songActionIndex by remember { mutableStateOf<Int?>(null) }
    var showUpgrade by remember { mutableStateOf(false) }

    // On tablet, edit mode is driven by viewModel.playlistEditMode (toggled from PlayerView).
    // On phone, it's local to this view.
    var localEditMode by remember { mutableStateOf(false) }
    val isEditMode = if (isTablet) viewModel.playlistEditMode else localEditMode
    val toggleEditMode = {
        if (isTablet) viewModel.playlistEditMode = !viewModel.playlistEditMode
        else localEditMode = !localEditMode
    }

    // Handle the system back button:
    //  - dismiss any open sub-screen first (settings / select playlist / edit song)
    //  - then exit edit mode
    //  - then dismiss the playlist itself (phone only — on tablet it's permanent)
    val hasOverlay = editingSongIndex != null || showSettings || showSelectPlaylist ||
                     songActionIndex != null || showUpgrade || isEditMode
    BackHandler(enabled = hasOverlay || !isTablet) {
        when {
            showUpgrade -> showUpgrade = false
            editingSongIndex != null -> editingSongIndex = null
            showSettings -> showSettings = false
            showSelectPlaylist -> showSelectPlaylist = false
            songActionIndex != null -> songActionIndex = null
            isEditMode -> {
                if (isTablet) viewModel.playlistEditMode = false
                else localEditMode = false
            }
            !isTablet -> onDismiss()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val uris = mutableListOf<Uri>()
            data?.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    uris.add(clip.getItemAt(i).uri)
                }
            }
            if (uris.isEmpty()) {
                data?.data?.let { uris.add(it) }
            }
            if (uris.isNotEmpty()) {
                val didHitLimit = viewModel.addFiles(uris)
                if (didHitLimit) showUpgrade = true
            }
        }
    }

    val listState = rememberLazyListState()

    // Reorderable LazyColumn state — handles drag, auto-scroll, and animations
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        // Library uses absolute list indices; viewModel.moveSong takes (from, to)
        // where 'to' is the insertion index BEFORE removal — convert.
        val fromIdx = from.index
        val toIdx = to.index
        // Transient row (if any) lives at index 0 and must not be moved or
        // displaced.
        val hasTransient = viewModel.playlistSongs.firstOrNull()?.isTransient == true
        if (hasTransient && (fromIdx == 0 || toIdx == 0)) return@rememberReorderableLazyListState
        val insertionIdx = if (toIdx > fromIdx) toIdx + 1 else toIdx
        viewModel.moveSong(from = fromIdx, to = insertionIdx)
    }

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
                    if (!isTablet) {
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
                            TextButton(onClick = { toggleEditMode() }) {
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
                    }
                    Text(
                        text = viewModel.playlistName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (isTablet) Modifier.fillMaxWidth() else Modifier,
                        textAlign = if (isTablet) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start
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
                    ReorderableItem(reorderableState, key = song.id) { isDragging ->
                        SongRow(
                            song = song,
                            isCurrent = index == viewModel.currentPlayingSongIndex,
                            isEditing = isEditMode,
                            isDragging = isDragging,
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
                            onDelete = { viewModel.deleteSong(at = index) },
                            // Bind the long-press drag gesture from the library
                            // to the drag handle on this ReorderableItemScope.
                            dragHandleModifier = with(this) {
                                Modifier.draggableHandle()
                            }
                        )
                    }
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
                                if (!viewModel.isPremium &&
                                    viewModel.playlistSongs.size >= viewModel.freeTrackLimit
                                ) {
                                    showUpgrade = true
                                } else {
                                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                        type = "audio/*"
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                    }
                                    filePickerLauncher.launch(
                                        Intent.createChooser(intent, "Select audio files")
                                    )
                                }
                            }
                        )
                        BottomBarButton(
                            icon = Icons.Default.LibraryMusic,
                            label = "Playlists",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (viewModel.isPremium) {
                                    showSelectPlaylist = true
                                } else {
                                    showUpgrade = true
                                }
                            }
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
                                if (!isTablet) onDismiss()
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

        if (showUpgrade) {
            Surface(modifier = Modifier.fillMaxSize()) {
                UpgradeView(viewModel = viewModel, onDismiss = { showUpgrade = false })
            }
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isEditing: Boolean,
    isDragging: Boolean = false,
    onTap: () -> Unit,
    onMoreTapped: () -> Unit,
    onDelete: () -> Unit,
    dragHandleModifier: Modifier = Modifier
) {
    val transient = song.isTransient
    val contentAlpha = if (transient) 0.4f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
            )
            .then(
                if (transient) Modifier
                else Modifier.clickable(enabled = !isDragging) { onTap() }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isEditing && !transient) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            if (song.uri == null && !song.title.isNullOrEmpty()) {
                Text(
                    text = song.title ?: "",
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f * contentAlpha)
                )
                Text(
                    text = if (song.isImportedFile) "File missing — delete and import again"
                    else "Not available — download or check DRM",
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f * contentAlpha)
                )
            } else {
                Text(
                    text = song.title ?: "Song not on device!",
                    fontSize = 14.sp,
                    fontStyle = if (transient) FontStyle.Italic else FontStyle.Normal,
                    fontWeight = if (isCurrent && !transient) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                Text(
                    text = "${song.artist ?: ""} - ${song.album ?: ""}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f * contentAlpha)
                )
            }
        }

        if (transient) {
            // No affordances for the transient row.
        } else if (isEditing) {
            // Drag handle (long-press to grab, drag to reorder)
            Box(
                modifier = dragHandleModifier
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Reorder",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
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
