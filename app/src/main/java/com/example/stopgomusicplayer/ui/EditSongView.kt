package com.example.stopgomusicplayer.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EditSongView(
    viewModel: PlayerViewModel,
    songIndex: Int,
    onDismiss: () -> Unit
) {
    val songs = viewModel.playlistSongs
    if (songIndex < 0 || songIndex >= songs.size) {
        onDismiss()
        return
    }

    val song = songs[songIndex]
    var title by remember { mutableStateOf(song.title ?: "") }
    var artist by remember { mutableStateOf(song.artist ?: "") }
    var album by remember { mutableStateOf(song.album ?: "") }
    var notes by remember { mutableStateOf(song.notes ?: "") }

    // Load existing artwork
    val artworkFile = remember { viewModel.artworkFile(forSongAt = songIndex) }
    val artworkBitmap = remember {
        artworkFile?.let { BitmapFactory.decodeFile(it.absolutePath) }
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
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                        TextButton(onClick = {
                            viewModel.updateSong(
                                at = songIndex,
                                title = title.ifEmpty { null },
                                artist = artist.ifEmpty { null },
                                album = album.ifEmpty { null },
                                notes = notes.ifEmpty { null },
                                artworkBitmap = null
                            )
                            onDismiss()
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(
                        text = "Edit Song",
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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Artwork
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (artworkBitmap != null) {
                        Image(
                            bitmap = artworkBitmap.asImageBitmap(),
                            contentDescription = "Artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Text(
                                "No artwork",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Fields
                LabeledField("Title", title) { title = it }
                LabeledField("Artist", artist) { artist = it }
                LabeledField("Album", album) { album = it }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Notes",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}
