package com.selecttube.app.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPosition
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onNavigateBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Request POST_NOTIFICATIONS on Android 13+ (playback continues regardless of grant/deny)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { /* playback continues regardless */ }

        LaunchedEffect(Unit) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(permission)
            }
        }
    }

    val dragOffsetY = remember { Animatable(0f) }
    val density = LocalDensity.current

    val collapseDistancePx = with(density) { 90.dp.toPx() }
    val flingVelocityPx = with(density) { 600.dp.toPx() }
    val minFlingDistancePx = with(density) { 20.dp.toPx() }
    val maxDragDistancePx = with(density) { 250.dp.toPx() }

    var hasNavigatedBack by remember { mutableStateOf(false) }
    val triggerCollapse: () -> Unit = {
        if (!hasNavigatedBack) {
            hasNavigatedBack = true
            onNavigateBack()
        }
    }

    Scaffold(
        modifier = Modifier.offset {
            IntOffset(0, dragOffsetY.value.roundToInt())
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.playbackInfo?.title ?: "Now Playing",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = triggerCollapse) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            val videoGestureModifier = Modifier.pointerInput(state.error) {
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downY = down.position.y
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        var isDragging = false
                        var accumulatedDragY = 0f
                        val touchSlop = viewConfiguration.touchSlop

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (!change.pressed && change.previousPressed) {
                                if (isDragging) {
                                    change.consume()
                                    val velocityY = velocityTracker.calculateVelocity().y
                                    val shouldCollapse = accumulatedDragY >= collapseDistancePx ||
                                        (velocityY >= flingVelocityPx && accumulatedDragY >= minFlingDistancePx)
                                    if (shouldCollapse) {
                                        triggerCollapse()
                                    } else {
                                        launch {
                                            dragOffsetY.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                            )
                                        }
                                    }
                                } else {
                                    // Tap on video area toggles play/pause when no error is displayed
                                    if (state.error == null) {
                                        viewModel.togglePlayPause()
                                    }
                                }
                                break
                            }

                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val deltaY = change.position.y - change.previousPosition.y

                            if (!isDragging) {
                                val totalDeltaY = change.position.y - downY
                                if (totalDeltaY > touchSlop) {
                                    isDragging = true
                                    accumulatedDragY = totalDeltaY - touchSlop
                                    change.consume()
                                    launch {
                                        dragOffsetY.snapTo(accumulatedDragY.coerceAtMost(maxDragDistancePx))
                                    }
                                } else if (totalDeltaY < -touchSlop) {
                                    // Upward movement: leave unconsumed so parent verticalScroll handles it
                                    break
                                }
                            } else {
                                change.consume()
                                accumulatedDragY = (accumulatedDragY + deltaY).coerceIn(0f, maxDragDistancePx)
                                launch {
                                    dragOffsetY.snapTo(accumulatedDragY)
                                }
                            }
                        }

                        if (isDragging && !hasNavigatedBack) {
                            launch {
                                dragOffsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                )
                            }
                        }
                    }
                }
            }

            // Video Display Container (16:9)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
                    .then(videoGestureModifier),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = viewModel.player
                            useController = false
                        }
                    },
                    update = { playerView ->
                        playerView.player = viewModel.player
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Detach player when leaving the screen so background audio keeps playing without leaking view
                DisposableEffect(Unit) {
                    onDispose {
                        // View detached; background playback continues via PlayerManager and PlaybackService
                    }
                }

                // Loading overlay (resolving or buffering)
                if (state.isResolving || state.isBuffering) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(color = Color.White)
                            if (state.isResolving) {
                                Text(
                                    text = "Resolving video stream...",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                // Error overlay
                if (state.error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp),
                            )
                            Text(
                                text = state.error.orEmpty(),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                            Button(onClick = { viewModel.retry() }) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
            }

            // Playback Progress Slider
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                val current = state.currentPositionMs.toFloat()
                val duration = maxOf(state.durationMs.toFloat(), 1f)

                Slider(
                    value = current.coerceIn(0f, duration),
                    onValueChange = { newPos -> viewModel.seekTo(newPos.toLong()) },
                    valueRange = 0f..duration,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime(state.currentPositionMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatTime(state.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Controls Row (Seek -10s, Play/Pause, Seek +10s)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { viewModel.seekBackward() },
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Rewind 10 seconds",
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(modifier = Modifier.size(16.dp))

                FilledIconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(36.dp),
                    )
                }

                Spacer(modifier = Modifier.size(16.dp))

                IconButton(
                    onClick = { viewModel.seekForward() },
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Forward 10 seconds",
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            // Video Details (Title, Channel Name, Quality Badge)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = state.playbackInfo?.title.orEmpty().ifBlank { "Loading video..." },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.playbackInfo?.channelName.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (!state.videoQuality.isNullOrBlank()) {
                        SuggestionChip(
                            onClick = { /* Quality display */ },
                            label = {
                                Text(
                                    text = state.videoQuality.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
