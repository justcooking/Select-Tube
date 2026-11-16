package com.selecttube.app.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onOpenSettings: () -> Unit,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val activeChannels by viewModel.activeChannels.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title   = { Text("Select Tube") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier          = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment  = Alignment.Center,
        ) {
            if (activeChannels.isEmpty()) {
                // Empty state
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier            = Modifier.padding(32.dp),
                ) {
                    Text(
                        text  = "No channels selected",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text      = "Go to Settings to add channels.\nOnly videos from your selected channels will appear here.",
                        style     = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Phase 4 — video feed implemented here
                Text(
                    text      = "Feed coming soon\n(${activeChannels.size} channel${if (activeChannels.size == 1) "" else "s"} active)",
                    textAlign = TextAlign.Center,
                    style     = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
