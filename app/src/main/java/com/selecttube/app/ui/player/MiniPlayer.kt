package com.selecttube.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.selecttube.app.playback.PlaybackState

/**
 * Docked mini player shown at the bottom of the screen across Feed and Settings.
 *
 * Features:
 * - Small video preview (live PlayerView with thumbnail fallback)
 * - Video title and channel name
 * - Play/Pause and Close (X) controls
 * - Progress bar across the top
 * - Tapping or swiping up expands back to the full player screen without restarting playback
 */
@Composable
fun MiniPlayer(
    playbackState: PlaybackState,
    player: ExoPlayer,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = playbackState.playbackInfo ?: return

    val progress = if (playbackState.durationMs > 0L) {
        (playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    val draggableState = rememberDraggableState { delta ->
        accumulatedDrag += delta
        if (accumulatedDrag < -20f) {
            onExpand()
            accumulatedDrag = 0f
        }
    }

    Surface(
        onClick = onExpand,
        modifier = modifier
            .fillMaxWidth()
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStopped = { accumulatedDrag = 0f },
            ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progress)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Small video preview / thumbnail container
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!info.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = info.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

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

                    if (playbackState.isResolving || playbackState.isBuffering) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Title and Channel Name
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = info.title.ifBlank { "Playing video" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (info.channelName.isNotBlank()) {
                        Text(
                            text = info.channelName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Play / Pause button
                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(28.dp),
                    )
                }

                // Close (X) button
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Inset spacer so the surface background extends cleanly behind the system navigation bar
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
