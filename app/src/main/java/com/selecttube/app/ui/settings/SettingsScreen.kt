package com.selecttube.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.selecttube.app.data.preferences.AppMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onAddChannel: () -> Unit,
    onAddPlaylist: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var channelToRemove by remember { mutableStateOf<ChannelItemUiState?>(null) }
    var playlistToRemove by remember { mutableStateOf<PlaylistItemUiState?>(null) }

    // Lock Settings whenever leaving this screen in Child Mode
    DisposableEffect(Unit) {
        onDispose {
            viewModel.lockSettings()
        }
    }

    // ── 1. Full Screen Parent Gate in Child Mode when locked ──────────────────
    if (uiState.appMode == AppMode.CHILD && !uiState.isParentUnlocked) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp),
                            )
                        }

                        Text(
                            text = "Parental Settings Locked",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )

                        Text(
                            text = "Enter your Parent Master Password to view or edit settings, channels, and parental controls.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        OutlinedTextField(
                            value = uiState.unlockPasswordInput,
                            onValueChange = { viewModel.onUnlockPasswordChange(it) },
                            label = { Text("Parent Master Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (uiState.unlockPasswordInput.isNotBlank()) {
                                        viewModel.submitUnlockPassword()
                                    }
                                }
                            ),
                            isError = uiState.unlockError != null,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (uiState.unlockError != null) {
                            Text(
                                text = uiState.unlockError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                        }

                        Button(
                            onClick = { viewModel.submitUnlockPassword() },
                            enabled = uiState.unlockPasswordInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Icon(
                                Icons.Default.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlock Settings")
                        }
                    }
                }
            }
        }
        return
    }

    // ── 2. Full Settings Content (Adult Mode or Unlocked Child Mode) ─────────
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        floatingActionButton = {
            var showAddMenu by remember { mutableStateOf(false) }

            Box {
                FloatingActionButton(
                    onClick = {
                        when (selectedTab) {
                            1 -> onAddChannel()
                            2 -> onAddPlaylist()
                            else -> showAddMenu = true
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Content",
                    )
                }

                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Add Channel") },
                        leadingIcon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
                        onClick = {
                            showAddMenu = false
                            onAddChannel()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Add Playlist") },
                        leadingIcon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) },
                        onClick = {
                            showAddMenu = false
                            onAddPlaylist()
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // ── Mode & Parental Controls Card ────────────────────────────────
            item(key = "mode_card") {
                ModeSection(
                    uiState = uiState,
                    onRequestSwitchToChild = { viewModel.requestSwitchToChildMode() },
                    onRequestSwitchToAdult = { viewModel.requestSwitchToAdultMode() },
                    onToggleShorts = { viewModel.setParentalShortsDisabled(it) },
                    onToggleDailyLimit = { viewModel.setParentalDailyLimitMinutes(if (it) 60 else 0) },
                    onToggleBedtime = { viewModel.setParentalBedtimeEnabled(it) },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Filter Tabs: All vs Channels vs Playlists ────────────────────
            item(key = "curation_tabs") {
                val pendingPlaylistsCount = uiState.playlists.count { !it.isActive }
                val pendingChannelsCount = uiState.channels.count { !it.isActive }
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("All (${uiState.channels.size + uiState.playlists.size})") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                if (pendingChannelsCount > 0) {
                                    "Channels (${uiState.channels.size} • $pendingChannelsCount ⏳)"
                                } else {
                                    "Channels (${uiState.channels.size})"
                                }
                            )
                        },
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                if (pendingPlaylistsCount > 0) {
                                    "Playlists (${uiState.playlists.size} • $pendingPlaylistsCount ⏳)"
                                } else {
                                    "Playlists (${uiState.playlists.size})"
                                }
                            )
                        },
                    )
                }
                HorizontalDivider()
            }

            // ── Top Pending Activation Section (Shown on All tab if any pending) ──
            val pendingChannels = uiState.channels.filter { !it.isActive }
            val pendingPlaylists = uiState.playlists.filter { !it.isActive }
            if (selectedTab == 0 && (pendingChannels.isNotEmpty() || pendingPlaylists.isNotEmpty())) {
                item(key = "pending_activation_header") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pending Activation (${pendingChannels.size + pendingPlaylists.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    HorizontalDivider()
                }

                items(pendingChannels, key = { "top_pend_ch_${it.channelId}" }) { channel ->
                    ChannelItem(
                        channel = channel,
                        onRemove = { channelToRemove = channel },
                        onCancelPending = { viewModel.cancelPending(channel.channelId) },
                    )
                    HorizontalDivider()
                }

                items(pendingPlaylists, key = { "top_pend_pl_${it.playlistId}" }) { playlist ->
                    PlaylistItem(
                        playlist = playlist,
                        onRemove = { playlistToRemove = playlist },
                        onCancelPending = { viewModel.cancelPendingPlaylist(playlist.playlistId) },
                    )
                    HorizontalDivider()
                }
            }

            // ── Channels Section (Shown on All or Channels tab) ──────────────
            if (selectedTab == 0 || selectedTab == 1) {
                val channelsToShow = if (selectedTab == 0 && (pendingChannels.isNotEmpty() || pendingPlaylists.isNotEmpty())) {
                    uiState.channels.filter { it.isActive }
                } else {
                    uiState.channels
                }

                item(key = "channels_header") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Allowed Channels (${channelsToShow.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(onClick = onAddChannel) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Channel")
                        }
                    }
                    HorizontalDivider()
                }

                if (channelsToShow.isEmpty()) {
                    item(key = "channels_empty_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No channels added yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(channelsToShow, key = { "ch_${it.channelId}" }) { channel ->
                        ChannelItem(
                            channel = channel,
                            onRemove = { channelToRemove = channel },
                            onCancelPending = { viewModel.cancelPending(channel.channelId) },
                        )
                        HorizontalDivider()
                    }
                }
            }

            // ── Playlists Section (Shown on All or Playlists tab) ────────────
            if (selectedTab == 0 || selectedTab == 2) {
                val playlistsToShow = if (selectedTab == 0 && (pendingChannels.isNotEmpty() || pendingPlaylists.isNotEmpty())) {
                    uiState.playlists.filter { it.isActive }
                } else {
                    uiState.playlists
                }

                item(key = "playlists_header") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Curated Playlists (${playlistsToShow.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(onClick = onAddPlaylist) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Playlist")
                        }
                    }
                    HorizontalDivider()
                }

                if (playlistsToShow.isEmpty()) {
                    item(key = "playlists_empty_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No playlists added yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(playlistsToShow, key = { "pl_${it.playlistId}" }) { playlist ->
                        PlaylistItem(
                            playlist = playlist,
                            onRemove = { playlistToRemove = playlist },
                            onCancelPending = { viewModel.cancelPendingPlaylist(playlist.playlistId) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // ── Remove channel confirmation dialog ───────────────────────────────────
    channelToRemove?.let { channel ->
        AlertDialog(
            onDismissRequest = { channelToRemove = null },
            title = { Text("Remove channel?") },
            text = { Text("\"${channel.name}\" will be removed from your selected channels.") },
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

    // ── Remove playlist confirmation dialog ──────────────────────────────────
    playlistToRemove?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistToRemove = null },
            title = { Text("Remove playlist?") },
            text = { Text("\"${playlist.name}\" will be removed from your curated playlists.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removePlaylist(playlist.playlistId)
                    playlistToRemove = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRemove = null }) { Text("Cancel") }
            },
        )
    }

    // ── Switch to Adult Mode Confirmation Dialog ─────────────────────────────
    if (uiState.showConfirmSwitchToAdultDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirmSwitchToAdultDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Switch to Adult Mode?")
                }
            },
            text = {
                Text(
                    "All parental controls (Shorts restrictions, bedtime schedule, screen time limit) will be disabled, and channel/playlist additions will use the 2-hour delay instead of a password.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmSwitchToAdultMode() }) {
                    Text("Switch to Adult Mode")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissConfirmSwitchToAdultDialog() }) {
                    Text("Cancel")
                }
            },
        )
    }

    // ── Switch to Child Mode Dialog (Password Setup) ─────────────────────────
    if (uiState.showSwitchToChildDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSwitchToChildDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set Up Child Mode")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Create a Parent Master Password. You will need this password to open Settings, approve channels and playlists, and modify controls.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = uiState.childPasswordInput,
                        onValueChange = { viewModel.onChildPasswordChange(it) },
                        label = { Text("Parent Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.childConfirmPasswordInput,
                        onValueChange = { viewModel.onChildConfirmPasswordChange(it) },
                        label = { Text("Confirm Parent Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (uiState.childPasswordError != null) {
                        Text(
                            text = uiState.childPasswordError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.submitSwitchToChildMode() }) {
                    Text("Enable Child Mode")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSwitchToChildDialog() }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mode & Parental Controls Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModeSection(
    uiState: SettingsUiState,
    onRequestSwitchToChild: () -> Unit,
    onRequestSwitchToAdult: () -> Unit,
    onToggleShorts: (Boolean) -> Unit,
    onToggleDailyLimit: (Boolean) -> Unit,
    onToggleBedtime: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (uiState.appMode == AppMode.ADULT) Icons.Default.Person else Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.appMode == AppMode.ADULT) "Adult Mode" else "Child Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                StatusBadge(
                    label = if (uiState.appMode == AppMode.ADULT) "Mindful Self-Control" else "Parental Controls",
                    containerColor = if (uiState.appMode == AppMode.ADULT) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.appMode == AppMode.ADULT) {
                Text(
                    text = "Direct channel and playlist curation for mindful focus. Subsequent additions activate a 2-hour wait delay to prevent impulse binging. No passwords needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onRequestSwitchToChild,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Switch to Child Mode (Parental Controls)")
                }
            } else {
                Text(
                    text = "Channels, playlists, and settings are protected with your Parent Master Password to ensure safe, curated viewing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                ControlToggleItem(
                    icon = Icons.Default.SmartDisplay,
                    title = "Block Shorts",
                    subtitle = "Completely hides YouTube Shorts across all channels",
                    checked = uiState.parentalShortsDisabled,
                    onCheckedChange = onToggleShorts,
                )

                ControlToggleItem(
                    icon = Icons.Default.Bedtime,
                    title = "Bedtime Schedule",
                    subtitle = "Pauses playback between 9:00 PM and 7:00 AM",
                    checked = uiState.parentalBedtimeEnabled,
                    onCheckedChange = onToggleBedtime,
                )

                ControlToggleItem(
                    icon = Icons.Default.Timer,
                    title = "Daily Limit (60 min)",
                    subtitle = if (uiState.parentalDailyLimitMinutes > 0) "Limit playback to 60 minutes per day" else "No daily limit",
                    checked = uiState.parentalDailyLimitMinutes > 0,
                    onCheckedChange = onToggleDailyLimit,
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRequestSwitchToAdult,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Switch to Adult Mode")
                }
            }
        }
    }
}

@Composable
private fun ControlToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChannelItem(
    channel: ChannelItemUiState,
    onRemove: () -> Unit,
    onCancelPending: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
            headlineColor = MaterialTheme.colorScheme.onBackground,
        ),
        headlineContent = { Text(channel.name) },
        supportingContent = {
            if (!channel.isActive && channel.remainingTimeFormatted != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = channel.remainingTimeFormatted,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        leadingContent = {
            if (channel.thumbnailUrl != null) {
                AsyncImage(
                    model = channel.thumbnailUrl,
                    contentDescription = channel.name,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (channel.isActive) {
                    StatusBadge(
                        label = "Active",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove channel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    StatusBadge(
                        label = "Pending",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                    IconButton(onClick = onCancelPending) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = "Cancel addition",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun PlaylistItem(
    playlist: PlaylistItemUiState,
    onRemove: () -> Unit,
    onCancelPending: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
            headlineColor = MaterialTheme.colorScheme.onBackground,
        ),
        headlineContent = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(
                    text = "${playlist.uploaderName ?: "Unknown"} • ${playlist.videoCount} videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!playlist.isActive && playlist.remainingTimeFormatted != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = playlist.remainingTimeFormatted,
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (playlist.thumbnailUrl != null) {
                    AsyncImage(
                        model = playlist.thumbnailUrl,
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlaylistPlay,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playlist.isActive) {
                    StatusBadge(
                        label = "Active",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove playlist",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    StatusBadge(
                        label = "Pending",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                    IconButton(onClick = onCancelPending) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = "Cancel addition",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun StatusBadge(label: String, containerColor: Color) {
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
