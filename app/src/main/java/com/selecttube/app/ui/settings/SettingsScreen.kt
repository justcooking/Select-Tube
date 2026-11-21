package com.selecttube.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.selecttube.app.data.update.UpdateState
import com.selecttube.app.ui.update.UpdateDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onAddChannel: () -> Unit,
    onAddPlaylist: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var channelToRemove by remember { mutableStateOf<ChannelItemUiState?>(null) }
    var playlistToRemove by remember { mutableStateOf<PlaylistItemUiState?>(null) }

    // Lock Settings whenever leaving this screen in Child Mode
    DisposableEffect(Unit) {
        onDispose {
            viewModel.lockSettings()
        }
    }

    UpdateDialog(
        state = updateState,
        onDismiss = viewModel::dismissUpdate,
        onDownload = viewModel::downloadUpdate,
        onInstall = viewModel::installUpdate,
        onRetry = viewModel::retryUpdate,
    )

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
            bottomBar = {
                AppInfoSection(
                    versionName = viewModel.currentVersion,
                    updateState = updateState,
                    onCheckForUpdates = { viewModel.checkForUpdates() },
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
        bottomBar = {
            AppInfoSection(
                versionName = viewModel.currentVersion,
                updateState = updateState,
                onCheckForUpdates = { viewModel.checkForUpdates() },
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

            // ── Curation Filter Chips ─────────────────────────────────────────
            item(key = "curation_chips") {
                val pendingPlaylistsCount = uiState.playlists.count { !it.isActive }
                val pendingChannelsCount = uiState.channels.count { !it.isActive }
                val totalPending = pendingPlaylistsCount + pendingChannelsCount

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("All (${uiState.channels.size + uiState.playlists.size})") },
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("Channels (${uiState.channels.size})") },
                    )
                    FilterChip(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        label = { Text("Playlists (${uiState.playlists.size})") },
                    )
                    if (totalPending > 0) {
                        FilterChip(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            label = { Text("⏳ Pending ($totalPending)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
            }

            // ── Top Pending Activation Section ──
            val pendingChannels = uiState.channels.filter { !it.isActive }
            val pendingPlaylists = uiState.playlists.filter { !it.isActive }
            val showPendingSection = (selectedTab == 0 || selectedTab == 3) && (pendingChannels.isNotEmpty() || pendingPlaylists.isNotEmpty())

            if (showPendingSection) {
                item(key = "pending_activation_header") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.HourglassTop,
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

            if (selectedTab == 3 && pendingChannels.isEmpty() && pendingPlaylists.isEmpty()) {
                item(key = "pending_none_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No pending additions. All channels and playlists are active!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ControlToggleItem(
                        icon = Icons.Default.SmartDisplay,
                        title = "Block Shorts",
                        subtitle = "Completely hides YouTube Shorts across all channels",
                        checked = uiState.parentalShortsDisabled,
                        onCheckedChange = onToggleShorts,
                        iconBackgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        iconColor = MaterialTheme.colorScheme.error,
                    )

                    ControlToggleItem(
                        icon = Icons.Default.Bedtime,
                        title = "Bedtime Schedule",
                        subtitle = "Pauses playback between 9:00 PM and 7:00 AM",
                        checked = uiState.parentalBedtimeEnabled,
                        onCheckedChange = onToggleBedtime,
                        iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        iconColor = MaterialTheme.colorScheme.primary,
                    )

                    ControlToggleItem(
                        icon = Icons.Default.Timer,
                        title = "Daily Limit (60 min)",
                        subtitle = if (uiState.parentalDailyLimitMinutes > 0) "Limit playback to 60 minutes per day" else "No daily limit",
                        checked = uiState.parentalDailyLimitMinutes > 0,
                        onCheckedChange = onToggleDailyLimit,
                        iconBackgroundColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        iconColor = MaterialTheme.colorScheme.secondary,
                    )
                }

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
    iconBackgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBackgroundColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
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
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
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
        headlineContent = {
            Text(
                text = channel.name,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            if (!channel.isActive && channel.remainingTimeFormatted != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Icon(
                            Icons.Default.HourglassTop,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Unlocks in ${channel.remainingTimeFormatted}",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        },
        leadingContent = {
            if (channel.thumbnailUrl != null) {
                AsyncImage(
                    model = channel.thumbnailUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (channel.isActive) {
                    StatusBadge(
                        label = "Active",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
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
        headlineContent = {
            Text(
                text = playlist.name,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = "${playlist.uploaderName ?: "Unknown"} • ${playlist.videoCount} videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!playlist.isActive && playlist.remainingTimeFormatted != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                Icons.Default.HourglassTop,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Unlocks in ${playlist.remainingTimeFormatted}",
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
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
private fun StatusBadge(
    label: String,
    containerColor: Color,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
    }
}

@Composable
private fun AppInfoSection(
    versionName: String,
    updateState: UpdateState,
    onCheckForUpdates: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Select Tube",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Version $versionName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Button(
                    onClick = onCheckForUpdates,
                    enabled = updateState !is UpdateState.Checking && updateState !is UpdateState.Downloading,
                ) {
                    if (updateState is UpdateState.Checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Checking...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check for Updates")
                    }
                }
            }

            if (updateState is UpdateState.UpToDate) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "You are using the latest version of Select Tube.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
