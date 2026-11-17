package com.selecttube.app.ui.player

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onNavigateBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

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

    // Fullscreen state
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(isFullscreen) {
        activity?.let { act ->
            setFullscreen(act, isFullscreen)
        }
        onDispose {
            if (isFullscreen) {
                activity?.let { act ->
                    setFullscreen(act, false)
                }
            }
        }
    }

    // Ensure orientation and system bars are restored if navigating away while in fullscreen
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                setFullscreen(act, false)
            }
        }
    }

    // Back button in fullscreen exits fullscreen first (Decision 1)
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    // Controls visibility and auto-hide
    var controlsVisible by remember { mutableStateOf(false) }
    var lastUserInteractionTime by remember { mutableLongStateOf(0L) }
    var scrubPositionMs by remember { mutableStateOf<Long?>(null) }

    // Double-tap visual feedback state (Decision 3)
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var seekFeedbackIsForward by remember { mutableStateOf(false) }
    var seekFeedbackVersion by remember { mutableIntStateOf(0) }

    // Auto-hide controls after 3 seconds (Decision 2: always auto-hide, unless actively scrubbing)
    LaunchedEffect(controlsVisible, lastUserInteractionTime) {
        if (controlsVisible && scrubPositionMs == null) {
            delay(3000L)
            controlsVisible = false
        }
    }

    // Auto-hide double-tap seek feedback badge after 700ms
    LaunchedEffect(seekFeedbackVersion) {
        if (seekFeedbackVersion > 0) {
            delay(700L)
            seekFeedbackText = null
        }
    }

    // Drag-down to collapse state
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
            IntOffset(0, if (isFullscreen) 0 else dragOffsetY.value.roundToInt())
        },
        topBar = {
            if (!isFullscreen) {
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
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullscreen) PaddingValues(0.dp) else innerPadding)
                .then(
                    if (!isFullscreen) Modifier.verticalScroll(rememberScrollState())
                    else Modifier
                ),
        ) {
            val videoGestureModifier = Modifier.pointerInput(isFullscreen, state.error) {
                coroutineScope {
                    var lastTapTime = 0L
                    var lastTapX = 0f
                    var pendingSingleTapJob: Job? = null
                    val doubleTapTimeoutMs = 300L

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downY = down.position.y
                        val downX = down.position.x
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
                                    val shouldCollapse = !isFullscreen && (
                                        accumulatedDragY >= collapseDistancePx ||
                                            (velocityY >= flingVelocityPx && accumulatedDragY >= minFlingDistancePx)
                                    )
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
                                    // Tap on video: distinguish single tap vs double tap
                                    if (state.error == null) {
                                        val now = System.currentTimeMillis()
                                        val tapX = change.position.x
                                        val width = size.width

                                        if (now - lastTapTime < doubleTapTimeoutMs && (tapX - lastTapX).absoluteValue < touchSlop * 4) {
                                            // Double-tap confirmed! Cancel single-tap toggle
                                            pendingSingleTapJob?.cancel()
                                            pendingSingleTapJob = null
                                            lastTapTime = 0L

                                            val isForward = tapX >= width / 2
                                            if (isForward) {
                                                viewModel.seekForward()
                                                seekFeedbackText = "+10s"
                                                seekFeedbackIsForward = true
                                            } else {
                                                viewModel.seekBackward()
                                                seekFeedbackText = "-10s"
                                                seekFeedbackIsForward = false
                                            }
                                            seekFeedbackVersion++
                                        } else {
                                            // Potential single-tap: wait 300ms for possible second tap
                                            lastTapTime = now
                                            lastTapX = tapX
                                            pendingSingleTapJob?.cancel()
                                            pendingSingleTapJob = launch {
                                                delay(doubleTapTimeoutMs)
                                                // Single-tap confirmed: toggle controls visibility
                                                controlsVisible = !controlsVisible
                                                lastUserInteractionTime = System.currentTimeMillis()
                                                lastTapTime = 0L
                                            }
                                        }
                                    }
                                }
                                break
                            }

                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val deltaY = change.position.y - change.previousPosition.y

                            if (!isDragging) {
                                val totalDeltaY = change.position.y - downY
                                val totalDeltaX = (change.position.x - downX).absoluteValue

                                if (!isFullscreen && totalDeltaY > touchSlop && totalDeltaY > totalDeltaX) {
                                    // Start downward drag to collapse
                                    pendingSingleTapJob?.cancel()
                                    pendingSingleTapJob = null
                                    lastTapTime = 0L

                                    isDragging = true
                                    accumulatedDragY = totalDeltaY - touchSlop
                                    change.consume()
                                    launch {
                                        dragOffsetY.snapTo(accumulatedDragY.coerceAtMost(maxDragDistancePx))
                                    }
                                } else if (totalDeltaY < -touchSlop || totalDeltaX > touchSlop) {
                                    // Upward or horizontal movement: not a downward collapse swipe
                                    pendingSingleTapJob?.cancel()
                                    pendingSingleTapJob = null
                                    lastTapTime = 0L
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

            // Video Display Container
            val videoBoxModifier = if (isFullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            }

            Box(
                modifier = videoBoxModifier
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
                        if (playerView.player != viewModel.player) {
                            playerView.player = viewModel.player
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Detach player when leaving the screen so background audio keeps playing without leaking view
                DisposableEffect(Unit) {
                    onDispose {
                        // View detached; background playback continues via PlayerManager and PlaybackService
                    }
                }

                // 7. Buffering spinner while the player is buffering or resolving
                if (state.isResolving || state.isBuffering) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = if (controlsVisible) 0f else 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 3.dp,
                            )
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

                // Double-tap visual feedback indicator (Decision 3: Left = "-10s", Right = "+10s")
                AnimatedVisibility(
                    visible = seekFeedbackText != null && !seekFeedbackIsForward,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 40.dp),
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = CircleShape,
                        shadowElevation = 6.dp,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "10s",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = seekFeedbackText != null && seekFeedbackIsForward,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 40.dp),
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = CircleShape,
                        shadowElevation = 6.dp,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "10s",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // On-Video Player Controls (NewPipe style)
                AnimatedVisibility(
                    visible = controlsVisible && state.error == null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                    ) {
                        // 1. Top bar: Video title (and back button when in fullscreen)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent),
                                    ),
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isFullscreen) {
                                IconButton(
                                    onClick = {
                                        isFullscreen = false
                                        lastUserInteractionTime = System.currentTimeMillis()
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Exit fullscreen",
                                        tint = Color.White,
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = state.playbackInfo?.title.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // 2. Centre: Play/Pause button (hidden while buffering/resolving)
                        if (!state.isBuffering && !state.isResolving) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = true, radius = 32.dp),
                                        onClick = {
                                            viewModel.togglePlayPause()
                                            lastUserInteractionTime = System.currentTimeMillis()
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        }

                        // 3. Bottom row: current time, seek bar, total duration, fullscreen button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                    ),
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatTime(scrubPositionMs ?: state.currentPositionMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            val current = (scrubPositionMs ?: state.currentPositionMs).toFloat()
                            val duration = maxOf(state.durationMs.toFloat(), 1f)

                            Slider(
                                value = current.coerceIn(0f, duration),
                                onValueChange = { newPos ->
                                    scrubPositionMs = newPos.toLong()
                                    lastUserInteractionTime = System.currentTimeMillis()
                                },
                                onValueChangeFinished = {
                                    scrubPositionMs?.let { viewModel.seekTo(it) }
                                    scrubPositionMs = null
                                    lastUserInteractionTime = System.currentTimeMillis()
                                },
                                valueRange = 0f..duration,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                                ),
                                modifier = Modifier.weight(1f),
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = formatTime(state.durationMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    isFullscreen = !isFullscreen
                                    lastUserInteractionTime = System.currentTimeMillis()
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }

                // 8. Error overlay (preserved exactly)
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

            // Video Details below video (shown in portrait mode)
            if (!isFullscreen) {
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
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun setFullscreen(activity: Activity, fullscreen: Boolean) {
    val window = activity.window ?: return
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    if (fullscreen) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        insetsController.show(WindowInsetsCompat.Type.systemBars())
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
