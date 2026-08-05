package com.dd3boh.outertune.ui.screens.search

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.LocalSnackbarHostState
import com.dd3boh.outertune.constants.SwipeToQueueKey
import com.dd3boh.outertune.constants.ListThumbnailSize
import com.dd3boh.outertune.constants.ThumbnailCornerRadius
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.extensions.togglePlayPause
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.source.lx.LxSearchSource
import com.dd3boh.outertune.ui.component.EmptyPlaceholder
import com.dd3boh.outertune.ui.component.LazyColumnScrollbar
import com.dd3boh.outertune.ui.component.NavigationTitle
import com.dd3boh.outertune.ui.component.SwipeToQueueBox
import com.dd3boh.outertune.ui.component.items.ItemThumbnail
import com.dd3boh.outertune.ui.component.items.ListItem
import com.dd3boh.outertune.ui.component.items.SongListItem
import com.dd3boh.outertune.ui.component.shimmer.ListItemPlaceHolder
import com.dd3boh.outertune.ui.component.shimmer.ShimmerHost
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.utils.joinByBullet
import com.dd3boh.outertune.utils.makeTimeString
import com.dd3boh.outertune.viewmodels.OnlineSearchViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun OnlineSearchResult(
    navController: NavController,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
) {
    val density = LocalDensity.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val state by viewModel.viewState.collectAsState()
    val activeMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val swipeEnabled by rememberPreference(SwipeToQueueKey, true)
    val snackbarHostState = LocalSnackbarHostState.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LazyColumn(
        state = listState,
        contentPadding = LocalPlayerAwareWindowInsets.current.add(WindowInsets.systemBars).asPaddingValues(),
    ) {
        if (state.isLoading && state.localSongs.isEmpty()) {
            item {
                ShimmerHost { repeat(8) { ListItemPlaceHolder() } }
            }
        }

        if (state.localSongs.isNotEmpty()) {
            item(key = "local_header") {
                NavigationTitle(title = "本地歌曲")
            }

            val thumbnailSize = (ListThumbnailSize.value * density.density).roundToInt()
            items(state.localSongs, key = { "local_${it.id}" }) { song ->
                SongListItem(
                    song = song,
                    navController = navController,
                    snackbarHostState = snackbarHostState,
                    isActive = activeMetadata?.id == song.id,
                    isPlaying = isPlaying,
                    inSelectMode = false,
                    isSelected = false,
                    onSelectedChange = {},
                    swipeEnabled = swipeEnabled,
                    thumbnailSize = thumbnailSize,
                    onPlay = {
                        playerConnection.playQueue(
                            ListQueue(
                                title = "本地搜索：${viewModel.query}",
                                items = state.localSongs.map { it.toMediaMetadata() },
                                startIndex = state.localSongs.indexOfFirst { it.id == song.id },
                            )
                        )
                    },
                )
            }
        }

        item(key = "source_picker") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(LxSearchSource.entries, key = { it.sourceId }) { source ->
                    FilterChip(
                        selected = state.source == source,
                        onClick = { viewModel.selectSource(source) },
                        label = { androidx.compose.material3.Text(source.displayName) },
                    )
                }
            }
        }

        if (state.items.isNotEmpty()) {
            item(key = "online_header") {
                NavigationTitle(title = "${state.source.displayName}歌曲")
            }
        }

        items(state.items, key = { it.id }) { item ->
            SwipeToQueueBox(
                item = item.toMediaItem(),
                swipeEnabled = swipeEnabled,
                snackbarHostState = snackbarHostState,
            ) {
                ListItem(
                    title = item.title,
                    subtitle = joinByBullet(
                        item.artists.joinToString { it.name },
                        item.duration.takeIf { it >= 0 }?.let { makeTimeString(it * 1000L) },
                    ),
                    thumbnailContent = {
                        ItemThumbnail(
                            thumbnailUrl = item.thumbnailUrl,
                            isActive = activeMetadata?.id == item.id,
                            isPlaying = isPlaying,
                            shape = RoundedCornerShape(ThumbnailCornerRadius),
                            modifier = Modifier.size(ListThumbnailSize),
                        )
                    },
                    isActive = activeMetadata?.id == item.id,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (activeMetadata?.id == item.id) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                scope.launch {
                                    val playbackItems = viewModel.preparePlaybackQueue(item.id)
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = "${state.source.displayName}搜索：${viewModel.query}",
                                            items = playbackItems,
                                            startIndex = playbackItems.indexOfFirst { it.id == item.id },
                                        ),
                                        replace = true,
                                    )
                                }
                            }
                        },
                        onLongClick = {},
                    ),
                )
            }
        }

        if (!state.isLoading && state.localSongs.isEmpty() && state.items.isEmpty()) {
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
