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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.selecttube.app.playback.PlaybackState
import com.selecttube.app.ui.theme.Brand500
import com.selecttube.app.ui.theme.Brand900
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

    // Quality menu state (Change 1)
    var isQualityMenuOpen by rememberSaveable { mutableStateOf(false) }

    // Back button handling:
    // 1. If quality menu is open, back button/gesture closes only the menu!
    BackHandler(enabled = isQualityMenuOpen) {
        isQualityMenuOpen = false
        lastUserInteractionTime = System.currentTimeMillis()
    }

    // 2. Back button in fullscreen exits fullscreen
    BackHandler(enabled = !isQualityMenuOpen && isFullscreen) {
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

    // Auto-hide controls after 3 seconds:
    // Once the quality menu is open, it stays open and the controls stay visible.
    // The auto-hide timer must not hide either of them while the menu is open.
    // After selecting a quality or closing the menu, normal ~3s auto-hide resumes.
    LaunchedEffect(controlsVisible, lastUserInteractionTime, isQualityMenuOpen, scrubPositionMs) {
        if (controlsVisible && scrubPositionMs == null && !isQualityMenuOpen) {
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

    val videoGestureModifier = Modifier.pointerInput(isFullscreen, state.error, state.isBackgroundOnly) {
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
                            // Tap on video: distinguish single tap vs double tap (only if not in background mode)
                            if (state.error == null && !state.isBackgroundOnly) {
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

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .offset {
                IntOffset(0, if (isFullscreen) 0 else dragOffsetY.value.roundToInt())
            },
        containerColor = Brand900,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Top header bar colored with our app's brand blue behind status bar
            if (!isFullscreen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(Brand500),
                )
            }

            // Video player sits flush to the top (immediately below the status bar)
            VideoPlayerBox(
                state = state,
                player = viewModel.player,
                controlsVisible = controlsVisible,
                isFullscreen = isFullscreen,
                isQualityMenuOpen = isQualityMenuOpen,
                seekFeedbackText = seekFeedbackText,
                seekFeedbackIsForward = seekFeedbackIsForward,
                scrubPositionMs = scrubPositionMs,
                onTogglePlayPause = {
                    viewModel.togglePlayPause()
                    lastUserInteractionTime = System.currentTimeMillis()
                },
                onSeekTo = {
                    viewModel.seekTo(it)
                    lastUserInteractionTime = System.currentTimeMillis()
                },
                onScrubPositionChange = {
                    scrubPositionMs = it
                    lastUserInteractionTime = System.currentTimeMillis()
                },
                onScrubFinished = {
                    viewModel.seekTo(it)
                    scrubPositionMs = null
                    lastUserInteractionTime = System.currentTimeMillis()
                },
                onToggleFullscreen = {
                    isFullscreen = !isFullscreen
                    lastUserInteractionTime = System.currentTimeMillis()
                },
                onExitFullscreen = {
                    isFullscreen = false
                    lastUserInteractionTime = System.currentTimeMillis()
                },
                onCollapse = triggerCollapse,
                onSetQuality = { q ->
                    viewModel.setQuality(q)
                    lastUserInteractionTime = System.currentTimeMillis()
                },
                onQualityMenuOpenChange = { open ->
                    isQualityMenuOpen = open
                    lastUserInteractionTime = System.currentTimeMillis()
                },
                onResumeVideo = {
                    viewModel.resumeFromBackground()
                    lastUserInteractionTime = System.currentTimeMillis()
                },
                onRetry = { viewModel.retry() },
                modifier = if (isFullscreen) {
                    Modifier
                        .fillMaxSize()
                        .then(videoGestureModifier)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .then(videoGestureModifier)
                },
            )

            // Content below the player (Title, Channel Name, Subscribers, Views, Likes, and Background button)
            if (!isFullscreen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    // Video Title with natural font size and caret icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = state.playbackInfo?.title.orEmpty().ifBlank { "Loading video..." },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Channel Row: Avatar, Name, Subscribers (left) | Views, Likes (right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left: Channel Avatar + Name + Subscribers
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            val avatarUrl = state.playbackInfo?.channelAvatarUrl
                            if (!avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = state.playbackInfo?.channelName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Brand500),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = state.playbackInfo?.channelName?.take(1)?.uppercase() ?: "C",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = state.playbackInfo?.channelName.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val subCount = state.playbackInfo?.subscriberCount ?: -1L
                                val subText = when {
                                    subCount >= 1_000_000 -> String.format(Locale.US, "%.1fM subscribers", subCount / 1_000_000.0)
                                    subCount >= 1_000 -> String.format(Locale.US, "%.1fk subscribers", subCount / 1000.0)
                                    subCount > 0 -> "$subCount subscribers"
                                    else -> "Subscribers"
                                }
                                Text(
                                    text = subText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Right: Views count + Likes
                        Column(
                            horizontalAlignment = Alignment.End,
                        ) {
                            val viewCount = state.playbackInfo?.viewCount ?: -1L
                            val viewText = when {
                                viewCount >= 0 -> String.format(Locale.US, "%,d views", viewCount)
                                else -> ""
                            }
                            if (viewText.isNotEmpty()) {
                                Text(
                                    text = viewText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            val likeCount = state.playbackInfo?.likeCount ?: -1L
                            val likeText = when {
                                likeCount >= 1_000_000 -> String.format(Locale.US, "%.1fM", likeCount / 1_000_000.0)
                                likeCount >= 1_000 -> String.format(Locale.US, "%.1fk", likeCount / 1000.0)
                                likeCount > 0 -> "$likeCount"
                                else -> ""
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ThumbUp,
                                    contentDescription = "Likes",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp),
                                )
                                if (likeText.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = likeText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action buttons row: Background button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (isFullscreen) {
                                        isFullscreen = false
                                    }
                                    viewModel.setBackgroundOnly(true)
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = "Background",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Background",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * On-video player controls styled as NewPipe's player:
 * - Top-left: Close "X" button to collapse/exit
 * - Top-right: Quality selector dropdown menu + Fullscreen toggle
 * - Center: White Play/Pause triangle button
 * - Bottom: Timestamps + Sleek progress scrubber in our brand blue
 */
@Composable
private fun VideoPlayerBox(
    state: PlaybackState,
    player: ExoPlayer,
    controlsVisible: Boolean,
    isFullscreen: Boolean,
    isQualityMenuOpen: Boolean,
    seekFeedbackText: String?,
    seekFeedbackIsForward: Boolean,
    scrubPositionMs: Long?,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onScrubPositionChange: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    onCollapse: () -> Unit,
    onSetQuality: (String) -> Unit,
    onQualityMenuOpenChange: (Boolean) -> Unit,
    onResumeVideo: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (state.isBackgroundOnly) {
            // "Playing in background" view: thumbnail + play button in centre + text below
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val thumbUrl = state.playbackInfo?.thumbnailUrl
                if (!thumbUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, radius = 32.dp),
                                onClick = onResumeVideo,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Resume video",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Playing in background",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                    }
                },
                update = { playerView ->
                    if (playerView.player != player) {
                        playerView.player = player
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

            // Buffering spinner while the player is buffering or resolving
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

            // Double-tap visual feedback indicator: Left = "-10s"
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

            // Double-tap visual feedback indicator: Right = "+10s"
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

            // On-Video Player Controls Overlay (NewPipe Styled)
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
                    // Top row: "X" button on left | Quality dropdown + Fullscreen on right
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent),
                                ),
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left: Close "X" button (collapses to mini player)
                        IconButton(
                            onClick = if (isFullscreen) onExitFullscreen else onCollapse,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                                contentDescription = if (isFullscreen) "Exit fullscreen" else "Close",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        // Right: Quality setting button + Fullscreen toggle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Quality button with dropdown
                            Box {
                                Surface(
                                    onClick = { onQualityMenuOpenChange(true) },
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.55f),
                                ) {
                                    Text(
                                        text = state.videoQuality ?: "720p",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }

                                DropdownMenu(
                                    expanded = isQualityMenuOpen,
                                    onDismissRequest = { onQualityMenuOpenChange(false) },
                                ) {
                                    val available = state.playbackInfo?.availableQualities.orEmpty().ifEmpty {
                                        listOf("1080p", "720p", "480p", "360p", "240p", "144p")
                                    }
                                    available.forEach { q ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = q,
                                                    fontWeight = if (q == state.videoQuality) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (q == state.videoQuality) Brand500 else Color.Unspecified,
                                                )
                                            },
                                            onClick = {
                                                onQualityMenuOpenChange(false)
                                                onSetQuality(q)
                                            },
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Fullscreen button
                            IconButton(
                                onClick = onToggleFullscreen,
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

                    // Centre: White triangular Play/Pause button
                    if (!state.isBuffering && !state.isResolving) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, radius = 32.dp),
                                    onClick = onTogglePlayPause,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp),
                            )
                        }
                    }

                    // Bottom row: Current timestamp, sleek progress bar (our blue color), total duration
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                ),
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatTime(scrubPositionMs ?: state.currentPositionMs),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        val current = (scrubPositionMs ?: state.currentPositionMs).toFloat()
                        val duration = maxOf(state.durationMs.toFloat(), 1f)

                        Slider(
                            value = current.coerceIn(0f, duration),
                            onValueChange = { onScrubPositionChange(it.toLong()) },
                            onValueChangeFinished = { scrubPositionMs?.let { onScrubFinished(it) } },
                            valueRange = 0f..duration,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF2979FF),
                                activeTrackColor = Color(0xFF2979FF),
                                inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                            ),
                            modifier = Modifier.weight(1f),
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = formatTime(state.durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                        )
                    }
                }
            }
        }

        // Error overlay (preserved exactly)
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
                    Button(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Retry")
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
