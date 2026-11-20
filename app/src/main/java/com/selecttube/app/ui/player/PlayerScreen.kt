package com.selecttube.app.ui.player

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.selecttube.app.data.youtube.DescriptionType
import com.selecttube.app.playback.PlaybackState
import com.selecttube.app.ui.theme.Brand500
import com.selecttube.app.ui.theme.Brand700
import com.selecttube.app.ui.theme.Brand900
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.absoluteValue

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp =
    (start.value + (stop.value - start.value) * fraction).dp

/**
 * Unified sliding Player sheet that seamlessly bridges Full Player and Mini Player.
 * Uses a single persistent PlayerView so video playback never stops, hitches, or restarts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = { viewModel.collapsePlayer() },
    onChannelClick: (String, String) -> Unit = { _, _ -> },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isExpanded by viewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    val parentalRestriction by viewModel.parentalRestriction.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val coroutineScope = rememberCoroutineScope()

    // Request POST_NOTIFICATIONS on Android 13+
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

    // Fullscreen state: synced with both manual toggle and device landscape orientation
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var enteredViaAutoRotate by remember { mutableStateOf(false) }
    var hasSeenLandscapeOrientation by remember { mutableStateOf(false) }
    var isManuallyLockedToPortrait by remember { mutableStateOf(false) }

    // Detect if current video has vertical aspect ratio (e.g. Shorts or portrait videos)
    val isVerticalVideo = remember(state.videoWidth, state.videoHeight, state.playbackInfo?.videoWidth, state.playbackInfo?.videoHeight) {
        when {
            state.videoWidth > 0 && state.videoHeight > 0 -> state.videoHeight > state.videoWidth
            (state.playbackInfo?.videoHeight ?: 0) > 0 && (state.playbackInfo?.videoWidth ?: 0) > 0 ->
                (state.playbackInfo?.videoHeight ?: 0) > (state.playbackInfo?.videoWidth ?: 0)
            else -> false
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val inFullscreenMode = isFullscreen || isLandscape

    val currentInFullscreenMode by rememberUpdatedState(inFullscreenMode)
    val currentIsFullscreen by rememberUpdatedState(isFullscreen)

    LaunchedEffect(inFullscreenMode) {
        activity?.let { act ->
            setFullscreen(act, inFullscreenMode)
        }
    }

    LaunchedEffect(isExpanded) {
        if (!isExpanded) {
            isFullscreen = false
            enteredViaAutoRotate = false
            hasSeenLandscapeOrientation = false
            isManuallyLockedToPortrait = false
            activity?.let { act ->
                setFullscreen(act, false)
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // Hardware orientation listener: auto-rotates to fullscreen when tilted to landscape,
    // and returns to portrait when tilted vertical (just like YouTube).
    DisposableEffect(activity, isExpanded) {
        val act = activity ?: return@DisposableEffect onDispose {}
        if (!isExpanded) {
            return@DisposableEffect onDispose {}
        }

        val listener = object : OrientationEventListener(act) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                val autoRotateOn = try {
                    android.provider.Settings.System.getInt(
                        act.contentResolver,
                        android.provider.Settings.System.ACCELEROMETER_ROTATION,
                        0,
                    ) == 1
                } catch (_: Exception) {
                    true
                }
                if (!autoRotateOn) return

                val isDeviceLandscape = (orientation in 60..120) || (orientation in 240..300)
                val isDevicePortrait = (orientation in 0..45) || (orientation in 315..359)

                if (isDevicePortrait) {
                    if (isManuallyLockedToPortrait) {
                        isManuallyLockedToPortrait = false
                        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                    if (enteredViaAutoRotate || hasSeenLandscapeOrientation) {
                        isFullscreen = false
                        enteredViaAutoRotate = false
                        hasSeenLandscapeOrientation = false
                        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                } else if (isDeviceLandscape) {
                    if (!isManuallyLockedToPortrait && !isVerticalVideo) {
                        hasSeenLandscapeOrientation = true
                        if (!currentInFullscreenMode) {
                            enteredViaAutoRotate = true
                            isFullscreen = true
                            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    }
                }
            }
        }

        if (listener.canDetectOrientation()) {
            listener.enable()
        }

        onDispose {
            listener.disable()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                setFullscreen(act, false)
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // Quality, Audio & Subtitle menu states
    var isQualityMenuOpen by rememberSaveable { mutableStateOf(false) }
    var isAudioMenuOpen by rememberSaveable { mutableStateOf(false) }
    var isSubtitleMenuOpen by rememberSaveable { mutableStateOf(false) }

    // Subtitle cues state — updated via Player.Listener, cleared when subtitle changes/disabled
    var currentCues by remember { mutableStateOf<List<androidx.media3.common.text.Cue>>(emptyList()) }

    LaunchedEffect(state.selectedSubtitle) {
        currentCues = emptyList()
    }

    DisposableEffect(viewModel.player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                currentCues = cueGroup.cues
            }
        }
        viewModel.player.addListener(listener)
        onDispose {
            viewModel.player.removeListener(listener)
            currentCues = emptyList()
        }
    }

    // Description expanded state (collapsed by default; reset on new video)
    var isDescriptionExpanded by rememberSaveable(state.playbackInfo?.videoId) { mutableStateOf(false) }

    // Controls visibility and auto-hide
    var controlsVisible by remember { mutableStateOf(false) }
    var lastUserInteractionTime by remember { mutableLongStateOf(0L) }
    var scrubPositionMs by remember { mutableStateOf<Long?>(null) }

    // Back button priority
    BackHandler(enabled = isQualityMenuOpen) {
        isQualityMenuOpen = false
        lastUserInteractionTime = System.currentTimeMillis()
    }

    BackHandler(enabled = !isQualityMenuOpen && isAudioMenuOpen) {
        isAudioMenuOpen = false
        lastUserInteractionTime = System.currentTimeMillis()
    }

    BackHandler(enabled = !isQualityMenuOpen && !isAudioMenuOpen && isSubtitleMenuOpen) {
        isSubtitleMenuOpen = false
        lastUserInteractionTime = System.currentTimeMillis()
    }

    BackHandler(enabled = !isQualityMenuOpen && !isAudioMenuOpen && !isSubtitleMenuOpen && inFullscreenMode) {
        isFullscreen = false
        enteredViaAutoRotate = false
        hasSeenLandscapeOrientation = false
        isManuallyLockedToPortrait = true
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    BackHandler(enabled = !isQualityMenuOpen && !isAudioMenuOpen && !isSubtitleMenuOpen && !inFullscreenMode && isExpanded) {
        viewModel.collapsePlayer()
    }

    // Double-tap visual feedback state
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var seekFeedbackIsForward by remember { mutableStateOf(false) }
    var seekFeedbackVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(controlsVisible, lastUserInteractionTime, isQualityMenuOpen, isAudioMenuOpen, isSubtitleMenuOpen, scrubPositionMs) {
        if (isQualityMenuOpen || isAudioMenuOpen || isSubtitleMenuOpen) {
            return@LaunchedEffect
        }
        if (controlsVisible && scrubPositionMs == null) {
            delay(3000L)
            if (!isQualityMenuOpen && !isAudioMenuOpen && !isSubtitleMenuOpen) {
                controlsVisible = false
            }
        }
    }

    LaunchedEffect(seekFeedbackVersion) {
        if (seekFeedbackVersion > 0) {
            delay(700L)
            seekFeedbackText = null
        }
    }

    // Sheet spring spec (critically damped, tuned to 380f stiffness, 0.85f damping for smooth organic tracking)
    val sheetSpringSpec = remember {
        spring<Float>(
            dampingRatio = 0.85f,
            stiffness = 380f,
            visibilityThreshold = 0.001f,
        )
    }

    // Sheet animation progress: 1f = Expanded (Full Player), 0f = Collapsed (Mini Player)
    val sheetProgress = remember { Animatable(if (isExpanded) 1f else 0f) }

    // Conflated channel to synchronize drag updates 1:1 with the screen refresh rate (VSYNC)
    // Eliminates micro-stutters and Handler message queue thrashing
    val dragChannel = remember { Channel<Float>(Channel.CONFLATED) }

    LaunchedEffect(Unit) {
        for (targetProgress in dragChannel) {
            sheetProgress.snapTo(targetProgress)
        }
    }

    LaunchedEffect(isExpanded) {
        val target = if (isExpanded) 1f else 0f
        if (sheetProgress.value != target) {
            sheetProgress.animateTo(
                targetValue = target,
                animationSpec = sheetSpringSpec,
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val density = LocalDensity.current
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val miniPlayerHeight = 64.dp
        val collapsedOffsetY = (screenHeight - miniPlayerHeight - navBarHeight).coerceAtLeast(0.dp)
        val collapsedOffsetYPx = with(density) { collapsedOffsetY.toPx() }.coerceAtLeast(1f)
        val flingVelocityPx = with(density) { 600.dp.toPx() }

        val progress = if (inFullscreenMode) 1f else sheetProgress.value.coerceIn(0f, 1f)
        val currentSheetOffsetY = lerpDp(collapsedOffsetY, 0.dp, progress)

        // Drag gesture modifier attached to the video/drag area
        val dragGestureModifier = Modifier.pointerInput(inFullscreenMode, state.error, state.isBackgroundOnly, isQualityMenuOpen, isAudioMenuOpen, isSubtitleMenuOpen) {
            var lastTapTime = 0L
            var lastTapX = 0f
            var pendingSingleTapJob: Job? = null
            val doubleTapTimeoutMs = 300L

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Stop any running animation immediately so user can catch the sheet mid-flight
                coroutineScope.launch { sheetProgress.stop() }

                val downY = down.position.y
                val downX = down.position.x
                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)

                var isDragging = false
                val startProgress = sheetProgress.value
                val touchSlop = viewConfiguration.touchSlop

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                    if (!change.pressed && change.previousPressed) {
                        if (isDragging) {
                            change.consume()
                            val velocityY = velocityTracker.calculateVelocity().y
                            val currentP = sheetProgress.value

                            val shouldCollapse = !inFullscreenMode && (
                                (velocityY > flingVelocityPx && currentP < 0.95f) ||
                                    (currentP < 0.65f && velocityY >= -flingVelocityPx)
                            )

                            coroutineScope.launch {
                                if (shouldCollapse) {
                                    viewModel.collapsePlayer()
                                    sheetProgress.animateTo(
                                        targetValue = 0f,
                                        animationSpec = sheetSpringSpec,
                                    )
                                } else {
                                    viewModel.expandPlayer()
                                    sheetProgress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = sheetSpringSpec,
                                    )
                                }
                            }
                        } else if (!change.isConsumed && !isQualityMenuOpen && !isAudioMenuOpen && !isSubtitleMenuOpen) {
                            // Tap on video area: only if not consumed by a button and menu not open
                            val currentP = sheetProgress.value
                            if (currentP > 0.85f) {
                                if (state.error == null && !state.isBackgroundOnly) {
                                    val now = System.currentTimeMillis()
                                    val tapX = change.position.x
                                    val width = size.width

                                    if (now - lastTapTime < doubleTapTimeoutMs && (tapX - lastTapX).absoluteValue < touchSlop * 4) {
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
                                        lastTapTime = now
                                        lastTapX = tapX
                                        pendingSingleTapJob?.cancel()
                                        pendingSingleTapJob = coroutineScope.launch {
                                            delay(doubleTapTimeoutMs)
                                            controlsVisible = !controlsVisible
                                            lastUserInteractionTime = System.currentTimeMillis()
                                            lastTapTime = 0L
                                        }
                                    }
                                }
                            } else if (currentP < 0.2f) {
                                // Tap on mini player expands to full player
                                viewModel.expandPlayer()
                            }
                        }
                        break
                    }

                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    val deltaY = change.position.y - downY
                    val deltaX = (change.position.x - downX).absoluteValue

                    if (!isDragging) {
                        if (!inFullscreenMode && deltaY.absoluteValue > touchSlop && deltaY.absoluteValue > deltaX) {
                            isDragging = true
                            pendingSingleTapJob?.cancel()
                            pendingSingleTapJob = null
                            lastTapTime = 0L
                            change.consume()
                        }
                    } else {
                        change.consume()
                        val progressDelta = deltaY / collapsedOffsetYPx
                        val newProgress = (startProgress - progressDelta).coerceIn(0f, 1f)
                        dragChannel.trySend(newProgress)
                    }
                }
            }
        }

        // Entire Sliding Sheet Container
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, with(density) { currentSheetOffsetY.roundToPx() }) },
            color = if (progress > 0.4f) Brand900 else Brand700,
            tonalElevation = if (progress < 0.4f) 8.dp else 0.dp,
            shadowElevation = if (progress < 0.4f) 8.dp else 0.dp,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // 1. Top status bar blue block (fades in when nearing full expansion in portrait)
                if (!inFullscreenMode && progress > 0.6f) {
                    val statusAlpha = ((progress - 0.6f) / 0.4f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(statusBarHeight)
                            .align(Alignment.TopCenter)
                            .alpha(statusAlpha)
                            .background(Brand500),
                    )
                }

                // 2. Mini Player Top Divider & 2dp Progress Bar (visible when collapsed / collapsing)
                if (progress < 0.4f) {
                    val miniBarAlpha = ((0.4f - progress) / 0.4f).coerceIn(0f, 1f)
                    val progressFraction = if (state.durationMs > 0L) {
                        (state.currentPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .alpha(miniBarAlpha),
                    ) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            if (progressFraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = progressFraction)
                                        .fillMaxHeight()
                                        .background(Color(0xFF2979FF)),
                                )
                            }
                        }
                    }
                }

                // 3. Mini Player Controls Row (right of the 96dp preview inside 64dp bar)
                if (progress < 0.4f) {
                    val miniControlsAlpha = ((0.4f - progress) / 0.4f).coerceIn(0f, 1f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(miniPlayerHeight)
                            .padding(start = 112.dp, end = 8.dp)
                            .align(Alignment.TopStart)
                            .alpha(miniControlsAlpha)
                            .clickable(enabled = progress < 0.15f) { viewModel.expandPlayer() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = state.playbackInfo?.title.orEmpty().ifBlank { "Playing video" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!state.playbackInfo?.channelName.isNullOrBlank()) {
                                Text(
                                    text = state.playbackInfo?.channelName.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            enabled = progress < 0.2f,
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }

                        IconButton(
                            onClick = { viewModel.stop() },
                            enabled = progress < 0.2f,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                // 4. Content Below Full Player (Title, Caret, Channel Row, Background Button)
                if (progress > 0.2f && !inFullscreenMode) {
                    val fullContentAlpha = ((progress - 0.2f) / 0.8f).coerceIn(0f, 1f)
                    val topPaddingBelowVideo = statusBarHeight + (screenWidth * (9f / 16f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = topPaddingBelowVideo)
                            .fillMaxHeight()
                            .alpha(fullContentAlpha)
                            .verticalScroll(rememberScrollState(), enabled = progress > 0.85f)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        // Title Row (tapping expands/collapses description)
                        val description = state.playbackInfo?.description
                        val chapters = state.playbackInfo?.chapters ?: emptyList()
                        val hasDescription = !description.isNullOrBlank() || chapters.isNotEmpty()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (hasDescription) {
                                        Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                isDescriptionExpanded = !isDescriptionExpanded
                                            }
                                            .padding(vertical = 2.dp)
                                    } else {
                                        Modifier
                                    }
                                ),
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
                            if (hasDescription) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = if (isDescriptionExpanded) {
                                        Icons.Default.KeyboardArrowUp
                                    } else {
                                        Icons.Default.KeyboardArrowDown
                                    },
                                    contentDescription = if (isDescriptionExpanded) "Collapse description" else "Expand description",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Channel Row: Avatar, Name, Subscribers (left) | Views, Likes (right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val channelId = state.playbackInfo?.channelId.orEmpty()
                                .ifBlank { state.playbackInfo?.channelName.orEmpty() }
                            val channelName = state.playbackInfo?.channelName.orEmpty()
                            val isChannelClickable = channelId.isNotBlank() || channelName.isNotBlank()

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = isChannelClickable) {
                                        onChannelClick(channelId, channelName)
                                    }
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
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

                                Column(modifier = Modifier.weight(1f, fill = false)) {
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

                                if (isChannelClickable) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "View Channel",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Views + Likes
                            Column(horizontalAlignment = Alignment.End) {
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
                                            enteredViaAutoRotate = false
                                            hasSeenLandscapeOrientation = false
                                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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

                        // Description View (expandable/collapsible, only when non-empty)
                        if (hasDescription) {
                            AnimatedVisibility(
                                visible = isDescriptionExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp),
                                ) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(bottom = 14.dp),
                                    )

                                    // 1. Timestamps / Chapters Section (shown at the top of description when available)
                                    if (chapters.isNotEmpty()) {
                                        Text(
                                            text = "Timestamps (${chapters.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.06f))
                                                .padding(vertical = 4.dp),
                                        ) {
                                            chapters.forEach { chapter ->
                                                val timestampStr = formatTime(chapter.startSeconds * 1000L)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            viewModel.seekTo(chapter.startSeconds * 1000L)
                                                            lastUserInteractionTime = System.currentTimeMillis()
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFF2979FF).copy(alpha = 0.2f),
                                                    ) {
                                                        Text(
                                                            text = timestampStr,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color(0xFF2979FF),
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        text = chapter.title,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.White,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }

                                        if (!description.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(14.dp))
                                            HorizontalDivider(
                                                thickness = 0.5.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                                modifier = Modifier.padding(bottom = 12.dp),
                                            )
                                        }
                                    }

                                    // 2. Full Description Body
                                    if (!description.isNullOrBlank()) {
                                        val uriHandler = LocalUriHandler.current
                                        val descType = state.playbackInfo?.descriptionType ?: DescriptionType.PLAIN_TEXT
                                        val formattedDescription = remember(description, descType) {
                                            formatDescriptionToAnnotatedString(
                                                content = description.orEmpty(),
                                                type = descType,
                                                uriHandler = uriHandler,
                                                onSeekTo = { targetMs ->
                                                    viewModel.seekTo(targetMs)
                                                    lastUserInteractionTime = System.currentTimeMillis()
                                                },
                                            )
                                        }
                                        Text(
                                            text = formattedDescription,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.9f),
                                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.35f,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. THE SINGLE PERSISTENT VIDEO CONTAINER
                // Smoothly interpolates width, height, X offset, Y offset and corner radius
                val videoLeft = lerpDp(8.dp, 0.dp, progress)
                val videoTop = lerpDp(5.dp, statusBarHeight, progress)
                val videoWidth = lerpDp(96.dp, screenWidth, progress)
                val videoHeight = lerpDp(54.dp, screenWidth * (9f / 16f), progress)
                val videoCornerRadius = lerpDp(4.dp, 0.dp, progress)

                val videoBoxModifier = if (inFullscreenMode) {
                    Modifier
                        .fillMaxSize()
                        .then(dragGestureModifier)
                } else {
                    Modifier
                        .offset {
                            IntOffset(
                                with(density) { videoLeft.roundToPx() },
                                with(density) { videoTop.roundToPx() },
                            )
                        }
                        .size(videoWidth, videoHeight)
                        .clip(RoundedCornerShape(videoCornerRadius))
                        .then(dragGestureModifier)
                }

                Box(
                    modifier = videoBoxModifier.background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    // Persistent Video PlayerView / Thumbnail (never unmounts)
                    if (state.isBackgroundOnly) {
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

                            if (progress > 0.5f) {
                                val bgScrimAlpha = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(bgScrimAlpha)
                                        .background(Color.Black.copy(alpha = 0.55f)),
                                )

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.alpha(bgScrimAlpha),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = ripple(bounded = true, radius = 32.dp),
                                                onClick = {
                                                    viewModel.resumeFromBackground()
                                                    lastUserInteractionTime = System.currentTimeMillis()
                                                },
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
                        }
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                androidx.media3.ui.PlayerView(ctx).apply {
                                    this.player = viewModel.player
                                    useController = false
                                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    // Subtitles are rendered directly in Compose below so they
                                    // are 100% positioned bottom-center and unaffected by WebVTT line tags.
                                    subtitleView?.visibility = android.view.View.GONE
                                    subtitleView?.alpha = 0f
                                }
                            },
                            update = { playerView ->
                                if (playerView.player != viewModel.player) {
                                    playerView.player = viewModel.player
                                }
                                playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                playerView.subtitleView?.visibility = android.view.View.GONE
                                playerView.subtitleView?.alpha = 0f
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // In-Player Subtitle overlay (positioned bottom-center with spacing from bottom)
                    val showSubtitleOverlay = state.selectedSubtitle != null && !state.isBackgroundOnly && progress > 0.5f && currentCues.isNotEmpty()
                    if (showSubtitleOverlay) {
                        val subtitleText = remember(currentCues) {
                            currentCues.mapNotNull { it.text?.toString() }
                                .filter { it.isNotBlank() }
                                .joinToString("\n")
                        }
                        if (subtitleText.isNotBlank()) {
                            val bottomSpace = when {
                                inFullscreenMode && controlsVisible -> 56.dp
                                inFullscreenMode -> 24.dp
                                controlsVisible -> 48.dp
                                else -> 24.dp
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(
                                        bottom = bottomSpace,
                                        start = if (inFullscreenMode) 32.dp else 16.dp,
                                        end = if (inFullscreenMode) 32.dp else 16.dp,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.75f),
                                    shape = RoundedCornerShape(4.dp),
                                ) {
                                    Text(
                                        text = subtitleText,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 14.sp,
                                            lineHeight = 18.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center,
                                        ),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Buffering Spinner (Expanded only)
                    if (progress > 0.8f && (state.isResolving || state.isBuffering)) {
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

                    // Seek feedback badge: Left -10s
                    if (progress > 0.8f) {
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

                        // Seek feedback badge: Right +10s
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
                    }

                    // On-Video Controls Overlay (NewPipe Styled, Expanded only)
                    if (progress > 0.6f && !state.isBackgroundOnly) {
                        val controlsAlpha = ((progress - 0.6f) / 0.4f).coerceIn(0f, 1f)

                        AnimatedVisibility(
                            visible = (controlsVisible || isQualityMenuOpen || isAudioMenuOpen || isSubtitleMenuOpen) && state.error == null,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(controlsAlpha),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f)),
                            ) {
                                // Top row: Subtitle button (top left), Audio dropdown, Quality dropdown, Fullscreen
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent),
                                            ),
                                        )
                                        .padding(
                                            horizontal = if (inFullscreenMode) (if (isLandscape) 28.dp else 16.dp) else 12.dp,
                                            vertical = if (inFullscreenMode) (if (isLandscape) 14.dp else 24.dp) else 8.dp,
                                        ),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (inFullscreenMode) {
                                            IconButton(
                                                onClick = {
                                                    isFullscreen = false
                                                    enteredViaAutoRotate = false
                                                    hasSeenLandscapeOrientation = false
                                                    isManuallyLockedToPortrait = true
                                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                                    lastUserInteractionTime = System.currentTimeMillis()
                                                },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Black.copy(alpha = 0.5f)),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = "Exit fullscreen",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }

                                        // Subtitles dropdown button (CC) placed on top left most
                                        val availableSubtitles = state.playbackInfo?.availableSubtitles.orEmpty()
                                        if (availableSubtitles.isNotEmpty()) {
                                            Box {
                                                Surface(
                                                    onClick = {
                                                        isSubtitleMenuOpen = true
                                                        controlsVisible = true
                                                        lastUserInteractionTime = System.currentTimeMillis()
                                                    },
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (state.selectedSubtitle != null) Brand500.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.55f),
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                    ) {
                                                        Icon(
                                                            imageVector = if (state.selectedSubtitle != null) Icons.Filled.ClosedCaption else Icons.Filled.ClosedCaptionOff,
                                                            contentDescription = "Subtitles",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                        if (state.selectedSubtitle != null) {
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = state.selectedSubtitle.orEmpty(),
                                                                color = Color.White,
                                                                style = MaterialTheme.typography.labelMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.widthIn(max = 70.dp),
                                                            )
                                                        }
                                                    }
                                                }

                                                DropdownMenu(
                                                    expanded = isSubtitleMenuOpen,
                                                    onDismissRequest = {
                                                        isSubtitleMenuOpen = false
                                                        lastUserInteractionTime = System.currentTimeMillis()
                                                    },
                                                ) {
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = "Off",
                                                                fontWeight = if (state.selectedSubtitle == null) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (state.selectedSubtitle == null) Brand500 else Color.Unspecified,
                                                            )
                                                        },
                                                        onClick = {
                                                            isSubtitleMenuOpen = false
                                                            lastUserInteractionTime = System.currentTimeMillis()
                                                            viewModel.setSubtitle(null)
                                                        },
                                                    )

                                                    availableSubtitles.forEach { sub ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text(
                                                                    text = sub.displayName,
                                                                    fontWeight = if (sub.displayName == state.selectedSubtitle) FontWeight.Bold else FontWeight.Normal,
                                                                    color = if (sub.displayName == state.selectedSubtitle) Brand500 else Color.Unspecified,
                                                                )
                                                            },
                                                            onClick = {
                                                                isSubtitleMenuOpen = false
                                                                lastUserInteractionTime = System.currentTimeMillis()
                                                                viewModel.setSubtitle(sub.displayName)
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {

                                        // Audio track selection with dropdown
                                        val availableTracks = state.playbackInfo?.availableAudioTracks.orEmpty()
                                        if (availableTracks.isNotEmpty()) {
                                            Box {
                                                Surface(
                                                    onClick = {
                                                        isAudioMenuOpen = true
                                                        controlsVisible = true
                                                        lastUserInteractionTime = System.currentTimeMillis()
                                                    },
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color.Black.copy(alpha = 0.55f),
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Audiotrack,
                                                            contentDescription = "Audio language",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(14.dp),
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = state.audioTrack ?: availableTracks.firstOrNull() ?: "Audio",
                                                            color = Color.White,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.widthIn(max = 110.dp),
                                                        )
                                                    }
                                                }

                                                DropdownMenu(
                                                    expanded = isAudioMenuOpen,
                                                    onDismissRequest = {
                                                        isAudioMenuOpen = false
                                                        lastUserInteractionTime = System.currentTimeMillis()
                                                    },
                                                ) {
                                                    availableTracks.forEach { track ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text(
                                                                    text = track,
                                                                    fontWeight = if (track == state.audioTrack) FontWeight.Bold else FontWeight.Normal,
                                                                    color = if (track == state.audioTrack) Brand500 else Color.Unspecified,
                                                                )
                                                            },
                                                            onClick = {
                                                                isAudioMenuOpen = false
                                                                lastUserInteractionTime = System.currentTimeMillis()
                                                                viewModel.setAudioTrack(track)
                                                            },
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))
                                        }

                                        // Quality button with dropdown
                                        Box {
                                            Surface(
                                                onClick = {
                                                    isQualityMenuOpen = true
                                                    controlsVisible = true
                                                    lastUserInteractionTime = System.currentTimeMillis()
                                                },
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
                                                onDismissRequest = {
                                                    isQualityMenuOpen = false
                                                    lastUserInteractionTime = System.currentTimeMillis()
                                                },
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
                                                            isQualityMenuOpen = false
                                                            lastUserInteractionTime = System.currentTimeMillis()
                                                            viewModel.setQuality(q)
                                                        },
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        IconButton(
                                            onClick = {
                                                if (inFullscreenMode) {
                                                    isFullscreen = false
                                                    enteredViaAutoRotate = false
                                                    hasSeenLandscapeOrientation = false
                                                    isManuallyLockedToPortrait = true
                                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                                } else {
                                                    isFullscreen = true
                                                    enteredViaAutoRotate = false
                                                    hasSeenLandscapeOrientation = false
                                                    isManuallyLockedToPortrait = false
                                                    if (isVerticalVideo) {
                                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                                    } else {
                                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                                    }
                                                }
                                                lastUserInteractionTime = System.currentTimeMillis()
                                            },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (inFullscreenMode) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                                contentDescription = if (inFullscreenMode) "Exit fullscreen" else "Fullscreen",
                                                tint = Color.White,
                                            )
                                        }
                                    }
                                }

                                // Center: Large Play/Pause button
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
                                            modifier = Modifier.size(44.dp),
                                        )
                                    }
                                }

                                // Bottom row: Timestamps + Sleek progress scrubber
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                            ),
                                        )
                                        .padding(
                                            horizontal = if (inFullscreenMode) (if (isLandscape) 28.dp else 16.dp) else 14.dp,
                                            vertical = if (inFullscreenMode) (if (isLandscape) 12.dp else 24.dp) else 6.dp,
                                        ),
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
                                        onValueChange = {
                                            scrubPositionMs = it.toLong()
                                            lastUserInteractionTime = System.currentTimeMillis()
                                        },
                                        onValueChangeFinished = {
                                            scrubPositionMs?.let {
                                                viewModel.seekTo(it)
                                                scrubPositionMs = null
                                                lastUserInteractionTime = System.currentTimeMillis()
                                            }
                                        },
                                        valueRange = 0f..duration,
                                        thumb = {
                                            Spacer(modifier = Modifier.size(10.dp))
                                        },
                                        track = { sliderState ->
                                            Canvas(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(10.dp),
                                            ) {
                                                val trackHeight = size.height
                                                val trackCornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                                                val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                                                val fraction = if (range > 0f) {
                                                    ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(0f, 1f)
                                                } else 0f

                                                // Inactive background track
                                                drawRoundRect(
                                                    color = Color.White.copy(alpha = 0.35f),
                                                    size = size,
                                                    cornerRadius = trackCornerRadius,
                                                )

                                                // Geometry for centered dot and concentric blue head
                                                val dotRadius = 3.dp.toPx()
                                                val minX = trackHeight / 2f
                                                val maxX = (size.width - (trackHeight / 2f)).coerceAtLeast(minX)
                                                val dotX = minX + (maxX - minX) * fraction
                                                val centerY = size.height / 2f

                                                // Active played track
                                                val activeWidth = (dotX + trackHeight / 2f).coerceIn(0f, size.width)
                                                if (activeWidth > 0f) {
                                                    drawRoundRect(
                                                        color = Color(0xFF2979FF),
                                                        size = Size(activeWidth, trackHeight),
                                                        cornerRadius = trackCornerRadius,
                                                    )
                                                }

                                                // White circular dot centered vertically and inside the blue bar
                                                drawCircle(
                                                    color = Color.White,
                                                    radius = dotRadius,
                                                    center = Offset(dotX, centerY),
                                                )
                                            }
                                        },
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.Transparent,
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

                    // Error overlay
                    if (state.error != null && progress > 0.5f) {
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

                // Inset spacer so the surface background extends cleanly behind navigation bars when docked in portrait
                if (!inFullscreenMode) {
                    Spacer(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsBottomHeight(WindowInsets.navigationBars),
                    )
                }
            }
        }
    }

    // ── Bedtime restriction dialog ───────────────────────────────────────────
    if (parentalRestriction.isBedtimeRestricted) {
        AlertDialog(
            onDismissRequest = { /* Modal: require action */ },
            icon = {
                Icon(
                    Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            },
            title = { Text("Bedtime Schedule Active") },
            text = {
                Text(
                    "It's bedtime! 🌙 Playback is locked until morning to ensure healthy rest.\n\nA parent can unlock playback if needed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.requestParentalUnlock() }) {
                    Text("Parent Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.stop()
                    onNavigateBack()
                }) {
                    Text("Close")
                }
            },
        )
    }

    // ── Parental Unlock Dialog ───────────────────────────────────────────────
    if (parentalRestriction.showUnlockDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissParentalUnlockDialog() },
            title = { Text("Parent Authentication") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter Parent Master Password to unlock playback:")
                    OutlinedTextField(
                        value = parentalRestriction.unlockPasswordInput,
                        onValueChange = { viewModel.onParentalUnlockPasswordChange(it) },
                        label = { Text("Parent Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = parentalRestriction.unlockError != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (parentalRestriction.unlockError != null) {
                        Text(
                            text = parentalRestriction.unlockError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.submitParentalUnlock() },
                    enabled = parentalRestriction.unlockPasswordInput.isNotBlank(),
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissParentalUnlockDialog() }) {
                    Text("Cancel")
                }
            },
        )
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = params
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            window.attributes = params
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
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

private fun plainTextToHtml(text: String): String {
    val escaped = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    val urlRegex = Regex("""(https?://[^\s<]+)""")
    val linked = urlRegex.replace(escaped) { match ->
        val url = match.value
        "<a href=\"$url\">$url</a>"
    }
    return linked.replace("\n", "<br>")
}

private fun markdownToHtml(text: String): String {
    // 1. Markdown links [text](url)
    val mdLinkRegex = Regex("""\[([^\]]+)\]\((https?://[^\s)]+)\)""")
    var result = mdLinkRegex.replace(text) { match ->
        val linkText = match.groupValues[1]
        val url = match.groupValues[2]
        "<a href=\"$url\">$linkText</a>"
    }
    // 2. Bold **text** or __text__
    result = result.replace(Regex("""\*\*(.+?)\*\*"""), "<b>$1</b>")
    result = result.replace(Regex("""__(.+?)__"""), "<b>$1</b>")
    // 3. Italic *text* or _text_
    result = result.replace(Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)"""), "<i>$1</i>")
    result = result.replace(Regex("""(?<!_)_(?!_)(.+?)(?<!_)_(?!_)"""), "<i>$1</i>")
    // 4. Headers: # Header
    result = result.replace(Regex("""(?m)^#{1,6}\s*(.*)$"""), "<b>$1</b><br>")
    // 5. Bare URLs not already in href
    val bareUrlRegex = Regex("""(?<!href=")(https?://[^\s<")]+)""")
    result = bareUrlRegex.replace(result) { match ->
        val url = match.value
        "<a href=\"$url\">$url</a>"
    }
    return result.replace("\n", "<br>")
}

private fun timestampToSeconds(ts: String): Long? {
    val parts = ts.split(':').mapNotNull { it.toLongOrNull() }
    return when (parts.size) {
        2 -> parts[0] * 60L + parts[1]
        3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
        else -> null
    }
}

private fun linkifyRawTimestamps(html: String): String {
    val pattern = Regex("""(?<![0-9:a-zA-Z/="\'_])(\b(?:\d{1,2}:)?[0-5]?\d:[0-5]\d\b)(?![0-9:a-zA-Z/="\'_])""")
    val tagSplit = Regex("""(<a\b[^>]*>.*?</a>)""", RegexOption.IGNORE_CASE)
    val tokens = tagSplit.split(html)
    val matches = tagSplit.findAll(html).map { it.value }.toList()

    val result = StringBuilder()
    for (i in tokens.indices) {
        val token = tokens[i]
        val replaced = pattern.replace(token) { m ->
            val ts = m.groupValues[1]
            val sec = timestampToSeconds(ts)
            if (sec != null) {
                "<a href=\"selecttube://seek?seconds=$sec\">$ts</a>"
            } else {
                ts
            }
        }
        result.append(replaced)
        if (i < matches.size) {
            result.append(matches[i])
        }
    }
    return result.toString()
}

private fun parseTimeParamToMs(timeParam: String): Long? {
    val clean = timeParam.trim().lowercase(Locale.ROOT)
    if (clean.all { it.isDigit() }) {
        return clean.toLongOrNull()?.times(1000L)
    }
    if (clean.endsWith("s") && clean.dropLast(1).all { it.isDigit() }) {
        return clean.dropLast(1).toLongOrNull()?.times(1000L)
    }

    val parts = Regex("""(\d+)([hms])""").findAll(clean).toList()
    if (parts.isNotEmpty()) {
        var totalSeconds = 0L
        for (part in parts) {
            val count = part.groupValues[1].toLongOrNull() ?: 0L
            when (part.groupValues[2]) {
                "h" -> totalSeconds += count * 3600L
                "m" -> totalSeconds += count * 60L
                "s" -> totalSeconds += count
            }
        }
        return totalSeconds * 1000L
    }
    return null
}

private fun parseTimestampToMs(url: String): Long? {
    // 1. Inbuilt selecttube://seek link
    val stMatch = Regex("""selecttube://seek\?(?:ms=(\d+)|seconds=(\d+))""").find(url)
    if (stMatch != null) {
        val msStr = stMatch.groupValues[1]
        val secStr = stMatch.groupValues[2]
        if (msStr.isNotEmpty()) return msStr.toLongOrNull()
        if (secStr.isNotEmpty()) return (secStr.toLongOrNull() ?: 0L) * 1000L
    }

    // 2. YouTube timestamp URL parameters (?t=..., &t=..., #t=..., ?time_continue=..., &time_continue=...)
    val ytTimeMatch = Regex("""(?:[?&#]t=|time_continue=)([0-9hmsHMS]+)""").find(url)
    if (ytTimeMatch != null) {
        val timeParam = ytTimeMatch.groupValues[1]
        return parseTimeParamToMs(timeParam)
    }

    return null
}

private fun formatDescriptionToAnnotatedString(
    content: String,
    type: DescriptionType,
    uriHandler: UriHandler,
    onSeekTo: (Long) -> Unit,
): AnnotatedString {
    val baseHtml = when (type) {
        DescriptionType.HTML -> content
        DescriptionType.MARKDOWN -> markdownToHtml(content)
        DescriptionType.PLAIN_TEXT -> plainTextToHtml(content)
    }

    val finalHtml = linkifyRawTimestamps(baseHtml)

    return try {
        AnnotatedString.fromHtml(
            htmlString = finalHtml,
            linkStyles = TextLinkStyles(
                style = SpanStyle(
                    color = Color(0xFF2979FF),
                    textDecoration = TextDecoration.Underline,
                ),
            ),
            linkInteractionListener = { link ->
                if (link is LinkAnnotation.Url) {
                    val targetMs = parseTimestampToMs(link.url)
                    if (targetMs != null) {
                        onSeekTo(targetMs)
                    } else {
                        try {
                            uriHandler.openUri(link.url)
                        } catch (_: Exception) {
                            // Ignore unhandled links
                        }
                    }
                }
            },
        )
    } catch (_: Exception) {
        AnnotatedString(content)
    }
}
