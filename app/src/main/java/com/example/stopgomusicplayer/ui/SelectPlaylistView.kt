package com.example.stopgomusicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SelectPlaylistView(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    var isEditMode by remember { mutableStateOf(false) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var showCannotDeleteAlert by remember { mutableStateOf(false) }
    var dialogText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.reloadAvailablePlaylists()
    }

    // New playlist dialog
    if (showNewPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showNewPlaylistDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = dialogText,
                    onValueChange = { dialogText = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dialogText.isNotEmpty()) {
                        val result = viewModel.createNewPlaylist(dialogText)
                        if (result != null) {
                            viewModel.switchToPlaylist(result)
                            onDismiss()
                        }
                    }
                    showNewPlaylistDialog = false
                    dialogText = ""
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNewPlaylistDialog = false
                    dialogText = ""
                }) { Text("Cancel") }
            }
        )
    }

    // Rename dialog
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null; dialogText = "" },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = dialogText,
                    onValueChange = { dialogText = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dialogText.isNotEmpty()) {
                        viewModel.renamePlaylist(renameTarget!!, dialogText)
                    }
                    renameTarget = null
                    dialogText = ""
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    renameTarget = null
                    dialogText = ""
                }) { Text("Cancel") }
            }
        )
    }

    // Cannot delete alert
    if (showCannotDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showCannotDeleteAlert = false },
            title = { Text("Cannot delete the current playlist") },
            text = { Text("Switch to another playlist before deleting this one.") },
            confirmButton = {
                TextButton(onClick = { showCannotDeleteAlert = false }) { Text("OK") }
            }
        )
    }

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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
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
                        text = "Playlists",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Playlist list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                itemsIndexed(viewModel.availablePlaylists) { index, name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isEditMode) {
                                    dialogText = name
                                    renameTarget = name
                                } else {
                                    viewModel.switchToPlaylist(name)
                                    onDismiss()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (name == viewModel.playlistName)
                                Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (name == viewModel.playlistName) Color(0xFF4CAF50)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = name,
                            fontWeight = if (name == viewModel.playlistName) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isEditMode) {
                            IconButton(onClick = {
                                if (name == viewModel.playlistName) {
                                    showCannotDeleteAlert = true
                                } else {
                                    viewModel.deletePlaylist(name)
                                }
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            // Bottom bar
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            dialogText = ""
                            showNewPlaylistDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Playlist", modifier = Modifier.size(28.dp))
                        Text("New Playlist", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
