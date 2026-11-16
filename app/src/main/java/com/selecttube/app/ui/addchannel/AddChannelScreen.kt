package com.selecttube.app.ui.addchannel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.selecttube.app.data.youtube.ResolvedChannel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChannelScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddChannelViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Channels") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Section 1: Mode Selector ──────────────────────────────────────
            TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                Tab(
                    selected = state.selectedTab == AddChannelTab.SEARCH,
                    onClick = { viewModel.onTabSelected(AddChannelTab.SEARCH) },
                    text = { Text("Search Channels") },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                )
                Tab(
                    selected = state.selectedTab == AddChannelTab.DIRECT_INPUT,
                    onClick = { viewModel.onTabSelected(AddChannelTab.DIRECT_INPUT) },
                    text = { Text("ID, URL or Handle") },
                    icon = { Icon(Icons.Default.Link, contentDescription = null) },
                )
            }

            // ── Section 2: Input / Search Area ───────────────────────────────
            when (state.selectedTab) {
                AddChannelTab.SEARCH -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            label = { Text("Search YouTube channels") },
                            placeholder = { Text("e.g. Veritasium, Kurzgesagt") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { viewModel.searchChannels() }),
                        )

                        Button(
                            onClick = viewModel::searchChannels,
                            enabled = !state.isSearching && state.searchQuery.isNotBlank(),
                            modifier = Modifier.height(56.dp),
                        ) {
                            if (state.isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                    }

                    // Search Results List
                    if (state.searchResults.isNotEmpty()) {
                        Text(
                            text = "Search Results",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.searchResults.forEach { channel ->
                                val isAlreadyStaged = state.stagedChannels.any { it.channelId == channel.channelId }
                                SearchResultCard(
                                    channel = channel,
                                    isStaged = isAlreadyStaged,
                                    onAdd = { viewModel.addResolvedChannelToStaged(channel) },
                                )
                            }
                        }
                    }
                }

                AddChannelTab.DIRECT_INPUT -> {
                    OutlinedTextField(
                        value = state.directInput,
                        onValueChange = viewModel::onDirectInputChange,
                        label = { Text("Channel ID, URL or @Handle") },
                        placeholder = { Text("e.g. @veritasium or UC_x5XG1OV2P6uZZ5FSM9Ttw") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { viewModel.resolveAndStageDirectInput() }),
                    )

                    OutlinedButton(
                        onClick = viewModel::resolveAndStageDirectInput,
                        enabled = !state.isResolving && state.directInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isResolving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Resolving channel with NewPipe...")
                        } else {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Resolve & Add to Review List")
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Section 3: Review List ────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Review List",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${state.stagedChannels.size} channel${if (state.stagedChannels.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (state.stagedChannels.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "No channels in review list yet. Search or enter a channel above to stage it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                state.stagedChannels.forEach { item ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (item.thumbnailUrl != null) {
                                AsyncImage(
                                    model = item.thumbnailUrl,
                                    contentDescription = item.displayName,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = item.channelId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            IconButton(onClick = { viewModel.removeStagedChannel(item.channelId) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Section 4: Password & Batch Confirmation ─────────────────────
            if (state.isFirstBatch) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "First-Ever Batch Setup",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "All channels in this initial setup batch will become active immediately. Set a password to protect future additions. The password cannot be changed once set.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }

                OutlinedTextField(
                    value = state.passwordInput,
                    onValueChange = viewModel::onPasswordInputChange,
                    label = { Text("Set Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                )

                OutlinedTextField(
                    value = state.confirmPasswordInput,
                    onValueChange = viewModel::onConfirmPasswordInputChange,
                    label = { Text("Confirm Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                )
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Password Protected Batch",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Enter your password once to confirm this batch. All channels in this batch will share a single 2-hour wait before becoming active.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedTextField(
                    value = state.passwordInput,
                    onValueChange = viewModel::onPasswordInputChange,
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                )
            }

            // Error display
            if (state.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val buttonText = when {
                state.isFirstBatch -> "Set Password & Add ${if (state.stagedChannels.isNotEmpty()) "(${state.stagedChannels.size})" else ""}"
                else -> "Confirm & Add Batch ${if (state.stagedChannels.isNotEmpty()) "(${state.stagedChannels.size})" else ""}"
            }

            Button(
                onClick = viewModel::confirmBatch,
                enabled = !state.isLoading && state.stagedChannels.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    channel: ResolvedChannel,
    isStaged: Boolean,
    onAdd: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (channel.thumbnailUrl != null) {
                AsyncImage(
                    model = channel.thumbnailUrl,
                    contentDescription = channel.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (channel.description != null && channel.description.isNotBlank()) {
                    Text(
                        text = channel.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (isStaged) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Added",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else {
                Button(
                    onClick = onAdd,
                    modifier = Modifier.height(36.dp),
                ) {
                    Text("Add")
                }
            }
        }
    }
}
