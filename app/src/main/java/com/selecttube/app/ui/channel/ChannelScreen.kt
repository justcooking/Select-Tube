package com.selecttube.app.ui.channel

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.selecttube.app.data.youtube.VideoFeedItem
import com.selecttube.app.ui.theme.Brand500
import com.selecttube.app.ui.theme.Brand700
import com.selecttube.app.ui.theme.Brand900
import java.util.Locale

// App brand palette
private val YouTubeDarkBg = Brand900 // Color(0xFF1A1A2E) - Our main background color
private val YouTubeCardBg = Brand700 // Color(0xFF16213E) - Our surface color
private val YouTubeSecondaryText = Color(0xFFAAAAAA)
private val YouTubeBorderColor = Color(0xFF26334D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    onNavigateBack: () -> Unit,
    onVideoClick: (VideoFeedItem) -> Unit,
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Infinite scrolling pagination: load more when user scrolls near the end
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.hasNextPage && !state.isLoadingMore) {
            viewModel.loadMoreVideos()
        }
    }

    Scaffold(
        containerColor = YouTubeDarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.channel?.name ?: viewModel.channelNameParam.ifBlank { "Channel" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = YouTubeDarkBg,
                ),
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.onRefresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading && state.channel == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Brand500)
                    }
                }

                state.error != null && state.channel == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(52.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Could not load channel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.error.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = YouTubeSecondaryText,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { viewModel.loadChannelProfile(isRefresh = false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Brand500,
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                state.channel != null -> {
                    val channel = state.channel!!
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        // 1. Channel Banner
                        item(key = "banner") {
                            if (!channel.bannerUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = channel.bannerUrl,
                                    contentDescription = "Channel Banner",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 4.5f),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(84.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Brand700.copy(alpha = 0.5f), Color(0xFF1E1E1E), YouTubeDarkBg)
                                            )
                                        ),
                                )
                            }
                        }

                        // 2. Channel Profile & Header Details
                        item(key = "header") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    // Large Circular Avatar
                                    if (!channel.thumbnailUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = channel.thumbnailUrl,
                                            contentDescription = channel.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(CircleShape)
                                                .border(2.dp, YouTubeBorderColor, CircleShape),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(CircleShape)
                                                .background(Brand500),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = channel.name.take(1).uppercase(),
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = channel.name,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )

                                        Spacer(modifier = Modifier.height(3.dp))

                                        val subCount = channel.subscriberCount
                                        val subText = when {
                                            subCount >= 1_000_000 -> String.format(Locale.US, "%.1fM subscribers", subCount / 1_000_000.0)
                                            subCount >= 1_000 -> String.format(Locale.US, "%.1fK subscribers", subCount / 1000.0)
                                            subCount > 0 -> "$subCount subscribers"
                                            else -> "Channel"
                                        }

                                        Text(
                                            text = subText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = YouTubeSecondaryText,
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Uniform YouTube-style Action Pill Buttons
                                when {
                                    state.isAllowed && state.isActive -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            // 1. "Allowed Channel" badge pill
                                            Surface(
                                                shape = RoundedCornerShape(20.dp),
                                                color = Color(0xFF272727),
                                                border = BorderStroke(1.dp, Brand500.copy(alpha = 0.4f)),
                                                modifier = Modifier.height(38.dp),
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 14.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = Brand500,
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Allowed Channel",
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp,
                                                        color = Color.White,
                                                    )
                                                }
                                            }

                                            // 2. "Remove Channel" pill button
                                            OutlinedButton(
                                                onClick = { viewModel.onRemoveChannelClick() },
                                                shape = RoundedCornerShape(20.dp),
                                                modifier = Modifier.height(38.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = Color(0xFFFF5252),
                                                ),
                                                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.45f)),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp),
                                                    tint = Color(0xFFFF5252),
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Remove",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFFFF5252),
                                                )
                                            }
                                        }
                                    }

                                    state.isAllowed && !state.isActive -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            // Pending countdown pill
                                            Surface(
                                                shape = RoundedCornerShape(20.dp),
                                                color = Color(0xFF2E1C0A),
                                                border = BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.4f)),
                                                modifier = Modifier.height(38.dp),
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 14.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.HourglassTop,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(15.dp),
                                                        tint = Color(0xFFFFB74D),
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = state.remainingCountdown ?: "Pending",
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp,
                                                        color = Color(0xFFFFB74D),
                                                    )
                                                }
                                            }

                                            // Cancel button pill
                                            OutlinedButton(
                                                onClick = { viewModel.onRemoveChannelClick() },
                                                shape = RoundedCornerShape(20.dp),
                                                modifier = Modifier.height(38.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = YouTubeSecondaryText,
                                                ),
                                                border = BorderStroke(1.dp, Color(0xFF444444)),
                                            ) {
                                                Text(
                                                    text = "Cancel",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp,
                                                    color = YouTubeSecondaryText,
                                                )
                                            }
                                        }
                                    }

                                    else -> {
                                        // "+ Add to Allowed Channels" prominent full pill button
                                        Button(
                                            onClick = { viewModel.onAllowChannelClick() },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Brand500,
                                                contentColor = Color.White,
                                            ),
                                            shape = RoundedCornerShape(20.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(42.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = Color.White,
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Add to Allowed Channels",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                            )
                                        }
                                    }
                                }

                                // Expandable Channel Description Pill
                                if (!channel.description.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    var isExpanded by remember { mutableStateOf(false) }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = YouTubeCardBg,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isExpanded = !isExpanded },
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                                .animateContentSize(),
                                        ) {
                                            Text(
                                                text = channel.description.orEmpty(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFDDDDDD),
                                                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = 18.sp,
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (isExpanded) "Show less" else "...more",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Brand500,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Videos & Shorts Section (ONLY displayed if the channel is allowed & active!)
                        when {
                            // Channel is NOT allowed yet -> Hide video cards and show restricted card
                            !state.isAllowed -> {
                                item(key = "restricted_not_allowed") {
                                    HorizontalDivider(color = YouTubeBorderColor, thickness = 0.5.dp)
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(18.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = YouTubeCardBg,
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, YouTubeBorderColor),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(Brand500.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = null,
                                                    tint = Brand500,
                                                    modifier = Modifier.size(28.dp),
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(14.dp))

                                            Text(
                                                text = "Channel Not Allowed",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = TextAlign.Center,
                                            )

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Text(
                                                text = "Videos from this channel are hidden because it is not in your curated channels list. Add it to view and watch its content.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = YouTubeSecondaryText,
                                                textAlign = TextAlign.Center,
                                            )

                                            Spacer(modifier = Modifier.height(18.dp))

                                            Button(
                                                onClick = { viewModel.onAllowChannelClick() },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Brand500,
                                                    contentColor = Color.White,
                                                ),
                                                shape = RoundedCornerShape(20.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(42.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Add to Allowed Channels",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Channel is pending activation -> Show countdown card and hide video cards
                            state.isAllowed && !state.isActive -> {
                                item(key = "restricted_pending") {
                                    HorizontalDivider(color = YouTubeBorderColor, thickness = 0.5.dp)
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(18.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = YouTubeCardBg,
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.25f)),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFFFB74D).copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.HourglassTop,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFB74D),
                                                    modifier = Modifier.size(28.dp),
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(14.dp))

                                            Text(
                                                text = "Activation in Progress",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = TextAlign.Center,
                                            )

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Text(
                                                text = "This channel was added and is currently in the intentional waiting period:\n${state.remainingCountdown ?: "Activating soon"}.\n\nVideos will unlock automatically once active.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = YouTubeSecondaryText,
                                                textAlign = TextAlign.Center,
                                            )

                                            Spacer(modifier = Modifier.height(16.dp))

                                            OutlinedButton(
                                                onClick = { viewModel.onRemoveChannelClick() },
                                                shape = RoundedCornerShape(20.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = YouTubeSecondaryText,
                                                ),
                                                border = BorderStroke(1.dp, Color(0xFF555555)),
                                            ) {
                                                Text("Cancel Request", fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Channel is allowed & active -> Render category tabs and video feed
                            else -> {
                                // Category Tab Row: Videos, Shorts, Live
                                item(key = "category_tabs") {
                                    HorizontalDivider(color = YouTubeBorderColor, thickness = 0.5.dp)
                                    val categories = state.availableCategories
                                    val selectedIndex = categories.indexOf(state.selectedCategory)

                                    TabRow(
                                        selectedTabIndex = selectedIndex.coerceAtLeast(0),
                                        containerColor = YouTubeDarkBg,
                                        contentColor = Color.White,
                                        indicator = { tabPositions ->
                                            if (selectedIndex in tabPositions.indices) {
                                                TabRowDefaults.SecondaryIndicator(
                                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                                                    color = Color.White,
                                                    height = 2.5.dp,
                                                )
                                            }
                                        },
                                        divider = {
                                            HorizontalDivider(color = YouTubeBorderColor, thickness = 0.5.dp)
                                        },
                                    ) {
                                        categories.forEachIndexed { index, category ->
                                            val isSelected = selectedIndex == index
                                            Tab(
                                                selected = isSelected,
                                                onClick = { viewModel.onCategorySelected(category) },
                                                text = {
                                                    Text(
                                                        text = category.label,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 14.sp,
                                                        color = if (isSelected) Color.White else YouTubeSecondaryText,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }

                                if (state.isLoadingTab) {
                                    item(key = "tab_loading") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(48.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(color = Brand500)
                                        }
                                    }
                                } else if (state.videos.isEmpty()) {
                                    item(key = "tab_empty") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(48.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = "No ${state.selectedCategory.label.lowercase()} found for this channel.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = YouTubeSecondaryText,
                                            )
                                        }
                                    }
                                } else {
                                    when (state.selectedCategory) {
                                        // -------------------------------------------------------------
                                        // SHORTS TAB: Render in native 2-column vertical 9:16 grid!
                                        // -------------------------------------------------------------
                                        ChannelCategory.SHORTS -> {
                                            item(key = "shorts_top_spacing") {
                                                Spacer(modifier = Modifier.height(10.dp))
                                            }

                                            items(state.videos.chunked(2), key = { pair -> pair.first().url.ifBlank { pair.first().videoId } }) { pair ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 14.dp, vertical = 5.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                ) {
                                                    pair.forEach { shortVideo ->
                                                        Box(modifier = Modifier.weight(1f)) {
                                                            ShortGridItem(
                                                                video = shortVideo,
                                                                onClick = { onVideoClick(shortVideo) },
                                                            )
                                                        }
                                                    }
                                                    // If odd number, balance the grid row
                                                    if (pair.size == 1) {
                                                        Spacer(modifier = Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }

                                        // -------------------------------------------------------------
                                        // VIDEOS & LIVE TABS: Render in rich 16:9 widescreen format
                                        // -------------------------------------------------------------
                                        else -> {
                                            item(key = "videos_top_spacing") {
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }

                                            items(state.videos, key = { it.url.ifBlank { it.videoId } }) { video ->
                                                ChannelWidescreenVideoItem(
                                                    video = video,
                                                    isLiveTab = state.selectedCategory == ChannelCategory.LIVESTREAMS,
                                                    onClick = { onVideoClick(video) },
                                                )
                                            }
                                        }
                                    }

                                    // Loading indicator for infinite scrolling pagination
                                    if (state.isLoadingMore) {
                                        item(key = "loading_more") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(20.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(28.dp),
                                                    strokeWidth = 2.5.dp,
                                                    color = Brand500,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Password Dialog for Allowing Channel
    if (state.showPasswordDialog) {
        var passwordVisible by remember { mutableStateOf(false) }

        AlertDialog(
            containerColor = YouTubeCardBg,
            onDismissRequest = { viewModel.dismissPasswordDialog() },
            title = {
                Text(
                    text = if (state.isFirstTimePasswordSetup) "Set Protection Password" else "Confirm Allow Channel",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            },
            text = {
                Column {
                    Text(
                        text = if (state.isFirstTimePasswordSetup) {
                            "Set a password for Select Tube. This channel will become active immediately."
                        } else {
                            "Enter your password to allow this channel. Once confirmed, it will activate in 2 hours."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = YouTubeSecondaryText,
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = state.passwordInput,
                        onValueChange = { viewModel.onPasswordInputChange(it) },
                        label = { Text("Password", color = YouTubeSecondaryText) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = YouTubeSecondaryText,
                                )
                            }
                        },
                        isError = state.passwordError != null,
                    )

                    if (state.isFirstTimePasswordSetup) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = state.confirmPasswordInput,
                            onValueChange = { viewModel.onConfirmPasswordInputChange(it) },
                            label = { Text("Confirm Password", color = YouTubeSecondaryText) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = state.passwordError != null,
                        )
                    }

                    if (state.passwordError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.passwordError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onConfirmPasswordSubmit() },
                    enabled = !state.isSubmittingAction,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Brand500,
                        contentColor = Color.White,
                    ),
                ) {
                    if (state.isSubmittingAction) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Confirm", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPasswordDialog() }) {
                    Text("Cancel", color = YouTubeSecondaryText)
                }
            },
        )
    }

    // Remove Confirmation Dialog
    if (state.showRemoveConfirmDialog) {
        AlertDialog(
            containerColor = YouTubeCardBg,
            onDismissRequest = { viewModel.dismissRemoveConfirmDialog() },
            title = {
                Text(
                    text = "Remove Channel?",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove '${state.channel?.name ?: "this channel"}' from your allowed channels list? Videos from this channel will no longer appear in your feed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = YouTubeSecondaryText,
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onConfirmRemove() },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Remove", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRemoveConfirmDialog() }) {
                    Text("Cancel", color = YouTubeSecondaryText)
                }
            },
        )
    }
}

/**
 * Native YouTube Shorts vertical card (9:16 aspect ratio).
 * Shows the full vertical short thumbnail with dark overlay gradient and title at bottom.
 */
@Composable
private fun ShortGridItem(
    video: VideoFeedItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = YouTubeCardBg),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!video.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brand500),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = YouTubeSecondaryText,
                    )
                }
            }

            // Bottom dark gradient overlay for sharp text readability
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0.95f),
                            ),
                        ),
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Column {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp,
                    )
                    if (!video.uploadDateText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = video.uploadDateText,
                            style = MaterialTheme.typography.labelSmall,
                            color = YouTubeSecondaryText,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * YouTube-style 16:9 widescreen video card for Videos & Live tabs.
 */
@Composable
private fun ChannelWidescreenVideoItem(
    video: VideoFeedItem,
    isLiveTab: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        // Thumbnail Box (16:9 with rounded corners)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(YouTubeCardBg),
            contentAlignment = Alignment.Center,
        ) {
            if (!video.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = YouTubeSecondaryText,
                )
            }

            // Duration or Live Badge
            // Ended live streams have durationSeconds > 0 and display their duration like regular videos.
            // Only active/ongoing live broadcasts without a fixed duration display the red LIVE badge.
            if (video.durationSeconds > 0) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                ) {
                    Text(
                        text = formatDuration(video.durationSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            } else if (isLiveTab) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFCC0000),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                ) {
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Title & Metadata
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 20.sp,
        )

        if (!video.uploadDateText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = video.uploadDateText,
                style = MaterialTheme.typography.bodySmall,
                color = YouTubeSecondaryText,
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val s = seconds % 60
    val m = (seconds / 60) % 60
    val h = seconds / 3600
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}
