package com.selecttube.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.selecttube.app.domain.Channel
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack : () -> Unit,
    onAddChannel   : () -> Unit,
    viewModel      : SettingsViewModel = hiltViewModel(),
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    var channelToRemove by remember { mutableStateOf<Channel?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddChannel) {
                Icon(Icons.Default.Add, contentDescription = "Add channel")
            }
        },
    ) { innerPadding ->

        if (channels.isEmpty()) {
            // ── Empty state ──────────────────────────────────────────────────
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier            = Modifier.padding(32.dp),
                ) {
                    Icon(
                        imageVector     = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        modifier        = Modifier.size(56.dp),
                        tint            = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text  = "No channels yet",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text      = "Tap + to add your first channel.\nYour password is set during the first add.",
                        style     = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            // ── Channel list ─────────────────────────────────────────────────
            LazyColumn(
                modifier        = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding  = PaddingValues(vertical = 8.dp),
            ) {
                items(channels, key = { it.channelId }) { channel ->
                    ChannelItem(
                        channel        = channel,
                        onRemove       = { channelToRemove = channel },
                        onCancelPending = { viewModel.cancelPending(channel.channelId) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // ── Remove confirmation dialog ───────────────────────────────────────────
    channelToRemove?.let { channel ->
        AlertDialog(
            onDismissRequest = { channelToRemove = null },
            title   = { Text("Remove channel?") },
            text    = { Text("\"${channel.name}\" will be removed from your selected channels.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeChannel(channel.channelId)
                    channelToRemove = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { channelToRemove = null }) { Text("Cancel") }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChannelItem(
    channel         : Channel,
    onRemove        : () -> Unit,
    onCancelPending : () -> Unit,
) {
    ListItem(
        headlineContent = { Text(channel.name) },
        supportingContent = {
            if (channel.isPending) {
                val hours   = TimeUnit.MILLISECONDS.toHours(channel.msUntilActive)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(channel.msUntilActive) % 60
                Text(
                    text  = "Active in ${hours}h ${minutes}m",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        leadingContent = {
            if (channel.thumbnailUrl != null) {
                AsyncImage(
                    model              = channel.thumbnailUrl,
                    contentDescription = channel.name,
                    modifier           = Modifier.size(48.dp),
                )
            } else {
                Icon(
                    imageVector        = Icons.Default.Person,
                    contentDescription = null,
                    modifier           = Modifier.size(48.dp),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (channel.isActive) {
                    // Status badge
                    StatusBadge(label = "Active", containerColor = MaterialTheme.colorScheme.primaryContainer)
                    // Remove
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove channel",
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    // Pending — cancel addition
                    StatusBadge(label = "Pending", containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    IconButton(onClick = onCancelPending) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = "Cancel addition",
                            tint               = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun StatusBadge(label: String, containerColor: androidx.compose.ui.graphics.Color) {
    Surface(
        color  = containerColor,
        shape  = MaterialTheme.shapes.small,
    ) {
        Text(
            text     = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style    = MaterialTheme.typography.labelSmall,
        )
    }
}
