package com.dd3boh.outertune.ui.screens.search

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.LocalSnackbarHostState
import com.dd3boh.outertune.constants.ListThumbnailSize
import com.dd3boh.outertune.constants.SwipeToQueueKey
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.extensions.togglePlayPause
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.ui.component.EmptyPlaceholder
import com.dd3boh.outertune.ui.component.LazyColumnScrollbar
import com.dd3boh.outertune.ui.component.SwipeToQueueBox
import com.dd3boh.outertune.ui.component.items.MediaMetadataListItem
import com.dd3boh.outertune.ui.component.shimmer.ListItemPlaceHolder
import com.dd3boh.outertune.ui.component.shimmer.ShimmerHost
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.viewmodels.OnlineSearchViewModel
import kotlin.math.roundToInt

@Composable
fun OnlineSearchResult(
    navController: NavController,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val state by viewModel.viewState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val activeMetadata by playerConnection.mediaMetadata.collectAsState()
    val swipeEnabled by rememberPreference(SwipeToQueueKey, true)
    val snackbarHostState = LocalSnackbarHostState.current
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val thumbnailSize = (ListThumbnailSize.value * density.density).roundToInt()

    LazyColumn(
        state = listState,
        contentPadding = LocalPlayerAwareWindowInsets.current.add(WindowInsets.systemBars).asPaddingValues(),
    ) {
        if (state.isLoading) {
            item {
                ShimmerHost { repeat(8) { ListItemPlaceHolder() } }
            }
        }

        items(state.items, key = { it.id }) { item ->
            SwipeToQueueBox(
                item = item.toMediaItem(),
                swipeEnabled = swipeEnabled,
                snackbarHostState = snackbarHostState,
            ) {
                MediaMetadataListItem(
                    mediaMetadata = item,
                    preferredSize = thumbnailSize,
                    isActive = activeMetadata?.id == item.id,
                    isPlaying = isPlaying,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (activeMetadata?.id == item.id) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = "洛雪搜索：${viewModel.query}",
                                        items = state.items,
                                        startIndex = state.items.indexOf(item),
                                    ),
                                    replace = true,
                                )
                            }
                        },
                        onLongClick = {},
                    ),
                )
            }
        }

        if (!state.isLoading && state.items.isEmpty()) {
            item {
                EmptyPlaceholder(
                    icon = Icons.Rounded.Search,
                    text = state.error ?: "没有搜索到歌曲",
                )
            }
        }
    }
    LazyColumnScrollbar(state = listState)
    Box(Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current).align(Alignment.BottomCenter),
        )
    }
}
