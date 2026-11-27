package com.selecttube.app.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.selecttube.app.ui.theme.Accent
import com.selecttube.app.ui.theme.TextMuted
import com.selecttube.app.ui.theme.TextSecondary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoItemCard(
    title: String,
    channelName: String,
    thumbnailUrl: String?,
    durationSeconds: Long,
    uploadDateText: String? = null,
    watchProgress: Float = 0f,
    onClick: () -> Unit,
    onMoreOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMoreOptions,
            ),
    ) {
        // ── Thumbnail (16:9, full-bleed) ───────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (!thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = TextMuted,
                )
            }

            // Duration badge — crisp, tabular, bottom-right
            if (durationSeconds > 0) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.80f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                ) {
                    Text(
                        text = formatDuration(durationSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            // Watch-progress bar — Accent red at bottom edge
            if (watchProgress > 0.02f) {
                LinearProgressIndicator(
                    progress = { watchProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = Accent,
                    trackColor = Color.White.copy(alpha = 0.20f),
                )
            }
        }

        // ── Metadata row ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Channel • date in a single line
                val subtitle = buildString {
                    append(channelName)
                    if (!uploadDateText.isNullOrBlank()) append(" • $uploadDateText")
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 36dp touch target, 18dp icon
            IconButton(
                onClick = onMoreOptions,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

internal fun formatDuration(durationSeconds: Long): String {
    if (durationSeconds <= 0) return ""
    val hours   = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    val seconds = durationSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
