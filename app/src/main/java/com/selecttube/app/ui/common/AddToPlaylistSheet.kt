package com.selecttube.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.selecttube.app.data.db.LocalPlaylistWithCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    video: VideoActionTarget,
    playlists: List<LocalPlaylistWithCount>,
    assignedPlaylistIds: Set<String>,
    onTogglePlaylist: (playlistId: String) -> Unit,
    onCreatePlaylist: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newPlaylistName = ""
            },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isNotBlank()) {
                            onCreatePlaylist(name)
                            showCreateDialog = false
                            newPlaylistName = ""
                        }
                    },
                    enabled = newPlaylistName.isNotBlank(),
                ) {
                    Text("Create & Add")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    newPlaylistName = ""
                }) {
                    Text("Cancel")
                }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Save to playlist",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                TextButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("New playlist", modifier = Modifier.padding(start = 4.dp))
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )

            if (playlists.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Text(
                        text = "No custom playlists yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Create your first playlist", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        val isAssigned = assignedPlaylistIds.contains(playlist.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTogglePlaylist(playlist.id) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${playlist.videoCount} videos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Checkbox(
                                checked = isAssigned,
                                onCheckedChange = { onTogglePlaylist(playlist.id) },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = onDismiss) {
                    Text("Done")
                }
            }
        }
    }
}
