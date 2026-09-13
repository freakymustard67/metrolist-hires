/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.AutoRadioQueueKey
import com.metrolist.music.constants.ListItemHeight
import com.metrolist.music.constants.RandomizeHomeOrderKey
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.HideOnScrollFAB
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.RandomizeGridItem
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.SpeedDialGridItem
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.menu.YouTubeArtistMenu
import com.metrolist.music.ui.menu.YouTubePlaylistMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.utils.SnapLayoutInfoProvider
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.HomeViewModel
import com.metrolist.music.viewmodels.Recommendation
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.metrolist.music.ui.utils.resize
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * A recommendation card: artwork, song, artist, and the seed it came from.
 */
@Composable
fun RecommendationCard(
    recommendation: Recommendation,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(4.dp),
    ) {
        AsyncImage(
            model = recommendation.thumbnail.resize(280, 280),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(132.dp)
                    .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = recommendation.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = recommendation.artists.joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.because_you_listened, recommendation.seedTitle),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Recommendation.toSongItem() =
    SongItem(
        id = id,
        title = title,
        artists = artists.map { Artist(name = it, id = null) },
        thumbnail = thumbnail,
    )

sealed class HomeSection(
    val id: String,
) {
    data object SpeedDial : HomeSection("speed_dial")

    data object Recommendations : HomeSection("recommendations")

    data object QuickPicks : HomeSection("quick_picks")

    data object RecentSongs : HomeSection("recent_songs")
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
fun HomeScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val listenTogetherManager = LocalListenTogetherManager.current
    val isListenTogetherGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val quickPicks by viewModel.quickPicks.collectAsStateWithLifecycle()
    val recentSongs by viewModel.recentSongs.collectAsStateWithLifecycle()

    val speedDialItems by viewModel.speedDialItems.collectAsStateWithLifecycle()
    val pinnedSpeedDialItems by viewModel.pinnedSpeedDialItems.collectAsStateWithLifecycle()


    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isRandomizing by viewModel.isRandomizing.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()

    val quickPicksLazyGridState = rememberLazyGridState()

    val (randomizeHomeOrder) = rememberPreference(RandomizeHomeOrderKey, true)
    val autoRadioQueue by rememberPreference(AutoRadioQueueKey, defaultValue = true)

    LaunchedEffect(Unit) { viewModel.loadHomeData() }

    val shouldShowWrappedCard by viewModel.showWrappedCard.collectAsStateWithLifecycle()
    val wrappedState by viewModel.wrappedManager.state.collectAsStateWithLifecycle()
    val isWrappedDataReady = wrappedState.isDataReady

    val scope = rememberCoroutineScope()
    // Track randomization job
    var randomizeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val lazylistState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()

    val wrappedDismissed by backStackEntry
        ?.savedStateHandle
        ?.getStateFlow("wrapped_seen", false)
        ?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }

    var randomSeed by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            randomSeed = System.currentTimeMillis()
        }
    }

    val foundInSettings = stringResource(R.string.found_in_settings_content)
    LaunchedEffect(wrappedDismissed) {
        if (wrappedDismissed) {
            viewModel.markWrappedAsSeen()
            scope.launch {
                snackbarHostState.showSnackbar(foundInSettings)
            }
            backStackEntry?.savedStateHandle?.set("wrapped_seen", false) // Reset the value
        }
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val homeSections =
        remember(
            randomizeHomeOrder,
            randomSeed,
            speedDialItems,
            recommendations,
            quickPicks,
            recentSongs,
        ) {
            val list = mutableListOf<HomeSection>()

            if (speedDialItems.isNotEmpty()) list.add(HomeSection.SpeedDial)
            if (recommendations?.isNotEmpty() == true) list.add(HomeSection.Recommendations)
            if (quickPicks?.isNotEmpty() == true) list.add(HomeSection.QuickPicks)
            if (recentSongs.isNotEmpty()) list.add(HomeSection.RecentSongs)

            val weight = { section: HomeSection ->
                when (section) {
                    HomeSection.SpeedDial -> 100
                    HomeSection.Recommendations -> 90
                    HomeSection.QuickPicks -> 85
                    HomeSection.RecentSongs -> 80
                }
            }

            if (randomizeHomeOrder) {
                // Stable per-section seed for the session, so the roll of each section
                // stays put when others appear or disappear.
                list.sortedByDescending { section ->
                    weight(section) + Random(randomSeed + section.id.hashCode()).nextInt(-10, 10)
                }
            } else {
                list.sortedByDescending { section -> weight(section) }
            }
        }

    LaunchedEffect(quickPicks) {
        quickPicksLazyGridState.scrollToItem(0)
    }

    PullToRefreshBox(
        state = pullRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        indicator = {
            Indicator(
                isRefreshing = isRefreshing,
                state = pullRefreshState,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
            )
        },
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart,
        ) {
            val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
            val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor
            val quickPicksSnapLayoutInfoProvider =
                remember(quickPicksLazyGridState) {
                    SnapLayoutInfoProvider(
                        lazyGridState = quickPicksLazyGridState,
                        positionInLayout = { layoutSize, itemSize ->
                            (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                        },
                    )
                }
            LazyColumn(
                state = lazylistState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                item(key = "wrapped_card") {
                    AnimatedVisibility(visible = shouldShowWrappedCard) {
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isWrappedDataReady) {
                                    val bbhFont =
                                        try {
                                            FontFamily(Font(R.font.bbh_bartle_regular))
                                        } catch (e: Exception) {
                                            FontFamily.Default
                                        }
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.wrapped_ready_title),
                                            style =
                                                MaterialTheme.typography.headlineLarge.copy(
                                                    fontFamily = bbhFont,
                                                    textAlign = TextAlign.Center,
                                                ),
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(R.string.wrapped_ready_subtitle),
                                            style =
                                                MaterialTheme.typography.bodyLarge.copy(
                                                    textAlign = TextAlign.Center,
                                                ),
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(onClick = {
                                            navController.navigate("wrapped")
                                        }) {
                                            Text(stringResource(R.string.open))
                                        }
                                    }
                                } else {
                                    ContainedLoadingIndicator()
                                }
                            }
                        }
                    }
                }

                homeSections.forEach { section ->
                    when (section) {
                        HomeSection.SpeedDial -> {
                            speedDialItems.takeIf { it.isNotEmpty() }?.let { items ->
                                item(key = "speed_dial_title") {
                                    NavigationTitle(
                                        title = stringResource(R.string.speed_dial),
                                    )
                                }

                                item(key = "speed_dial_list") {
                                    val targetItemSize = 160.dp
                                    val availableWidth = maxWidth - 32.dp
                                    val columns = (availableWidth / targetItemSize).toInt().coerceAtLeast(3)
                                    val rows =
                                        if (columns >= 6) {
                                            1
                                        } else if (columns >= 4) {
                                            2
                                        } else {
                                            3
                                        }
                                    val itemsPerPage = columns * rows
                                    val itemWidth = availableWidth / columns

                                    val pagerState = rememberPagerState(pageCount = { (items.size + itemsPerPage - 1) / itemsPerPage })

                                    Column(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth(),
                                    ) {
                                        HorizontalPager(
                                            state = pagerState,
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            pageSpacing = 16.dp,
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(itemWidth * rows),
                                        ) { page ->
                                            val pageStartIndex = page * itemsPerPage
                                            val pageItems = items.drop(pageStartIndex).take(itemsPerPage)

                                            Column(modifier = Modifier.fillMaxSize()) {
                                                for (row in 0 until rows) {
                                                    Row(modifier = Modifier.fillMaxWidth()) {
                                                        for (col in 0 until columns) {
                                                            val itemIndex = row * columns + col

                                                            val isRandomizeSlot = (page == 0 && itemIndex == itemsPerPage - 1)

                                                            if (isRandomizeSlot) {
                                                                Box(
                                                                    modifier =
                                                                        Modifier
                                                                            .width(itemWidth)
                                                                            .height(itemWidth)
                                                                            .padding(4.dp),
                                                                ) {
                                                                    RandomizeGridItem(
                                                                        isLoading = isRandomizing,
                                                                        onClick = {
                                                                            if (isRandomizing) {
                                                                                randomizeJob?.cancel()
                                                                            } else if (!isListenTogetherGuest) {
                                                                                randomizeJob =
                                                                                    scope.launch {
                                                                                        val randomItem = viewModel.getRandomItem()
                                                                                        if (randomItem != null) {
                                                                                            when (randomItem) {
                                                                                                is SongItem -> {
                                                                                                    playerConnection.playQueue(
                                                                                                        if (autoRadioQueue) {
                                                                                                            YouTubeQueue(
                                                                                                                randomItem.endpoint
                                                                                                                    ?: WatchEndpoint(
                                                                                                                        videoId = randomItem.id,
                                                                                                                    ),
                                                                                                                randomItem.toMediaMetadata(),
                                                                                                            )
                                                                                                        } else {
                                                                                                            ListQueue(
                                                                                                                title = randomItem.title,
                                                                                                                items = listOf(randomItem.toMediaItem())
                                                                                                            )
                                                                                                        }
                                                                                                    )
                                                                                                }

                                                                                                is AlbumItem -> {
                                                                                                    navController.navigate(
                                                                                                        "album/${randomItem.id}",
                                                                                                    )
                                                                                                }

                                                                                                is ArtistItem -> {
                                                                                                    navController.navigate(
                                                                                                        "artist/${randomItem.id}",
                                                                                                    )
                                                                                                }

                                                                                                is PlaylistItem -> {
                                                                                                    navController.navigate(
                                                                                                        "online_playlist/${randomItem.id}",
                                                                                                    )
                                                                                                }

                                                                                                is PodcastItem -> {
                                                                                                    navController.navigate(
                                                                                                        "online_podcast/${randomItem.id}",
                                                                                                    )
                                                                                                }

                                                                                                is EpisodeItem -> {
                                                                                                    playerConnection.playQueue(
                                                                                                        ListQueue(
                                                                                                            title = randomItem.title,
                                                                                                            items =
                                                                                                                listOf(
                                                                                                                    randomItem
                                                                                                                        .toMediaMetadata()
                                                                                                                        .toMediaItem(),
                                                                                                                ),
                                                                                                        ),
                                                                                                    )
                                                                                                }
                                                                                            }
                                                                                        }
                                                                                    }
                                                                            }
                                                                        },
                                                                    )
                                                                }
                                                            } else if (itemIndex < pageItems.size) {
                                                                val item = pageItems[itemIndex]
                                                                val isPinned by database.speedDialDao
                                                                    .isPinned(
                                                                        item.id,
                                                                    ).collectAsStateWithLifecycle(initialValue = false)

                                                                Box(
                                                                    modifier =
                                                                        Modifier
                                                                            .width(itemWidth)
                                                                            .height(itemWidth)
                                                                            .padding(4.dp),
                                                                ) {
                                                                    SpeedDialGridItem(
                                                                        item = item,
                                                                        isPinned = isPinned,
                                                                        isActive =
                                                                            item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
                                                                        isPlaying = isPlaying,
                                                                        modifier =
                                                                            Modifier
                                                                                .fillMaxSize()
                                                                                .combinedClickable(
                                                                                    onClick = {
                                                                                        when (item) {
                                                                                            is SongItem -> {
                                                                                                if (!isListenTogetherGuest) {
                                                                                                    playerConnection.playQueue(
                                                                                                        if (autoRadioQueue) {
                                                                                                            YouTubeQueue(
                                                                                                                item.endpoint
                                                                                                                    ?: WatchEndpoint(
                                                                                                                        videoId = item.id,
                                                                                                                    ),
                                                                                                                item.toMediaMetadata(),
                                                                                                            )
                                                                                                        } else {
                                                                                                            ListQueue(
                                                                                                                title = item.title,
                                                                                                                items = listOf(item.toMediaItem())
                                                                                                            )
                                                                                                        }
                                                                                                    )
                                                                                                }
                                                                                            }

                                                                                            is AlbumItem -> {
                                                                                                navController.navigate("album/${item.id}")
                                                                                            }

                                                                                            is ArtistItem -> {
                                                                                                navController.navigate("artist/${item.id}")
                                                                                            }

                                                                                            is PlaylistItem -> {
                                                                                                val rawType =
                                                                                                    pinnedSpeedDialItems
                                                                                                        .find {
                                                                                                            it.id ==
                                                                                                                item.id
                                                                                                        }?.type
                                                                                                if (rawType == "LOCAL_PLAYLIST") {
                                                                                                    navController.navigate(
                                                                                                        "local_playlist/${item.id}",
                                                                                                    )
                                                                                                } else {
                                                                                                    navController.navigate(
                                                                                                        "online_playlist/${item.id}",
                                                                                                    )
                                                                                                }
                                                                                            }

                                                                                            is PodcastItem -> {
                                                                                                navController.navigate(
                                                                                                    "online_podcast/${item.id}",
                                                                                                )
                                                                                            }

                                                                                            is EpisodeItem -> {
                                                                                                if (!isListenTogetherGuest) {
                                                                                                    playerConnection.playQueue(
                                                                                                        ListQueue(
                                                                                                            title = item.title,
                                                                                                            items =
                                                                                                                listOf(
                                                                                                                    item
                                                                                                                        .toMediaMetadata()
                                                                                                                        .toMediaItem(),
                                                                                                                ),
                                                                                                        ),
                                                                                                    )
                                                                                                }
                                                                                            }
                                                                                        }
                                                                                    },
                                                                                    onLongClick = {
                                                                                        haptic.performHapticFeedback(
                                                                                            HapticFeedbackType.LongPress,
                                                                                        )
                                                                                        menuState.show {
                                                                                            when (item) {
                                                                                                is SongItem -> {
                                                                                                    YouTubeSongMenu(
                                                                                                        song = item,
                                                                                                        onDismiss = menuState::dismiss,
                                                                                                    )
                                                                                                }

                                                                                                is AlbumItem -> {
                                                                                                    YouTubeAlbumMenu(
                                                                                                        albumItem = item,
                                                                                                        onDismiss = menuState::dismiss,
                                                                                                    )
                                                                                                }

                                                                                                is ArtistItem -> {
                                                                                                    YouTubeArtistMenu(
                                                                                                        artist = item,
                                                                                                        onDismiss = menuState::dismiss,
                                                                                                    )
                                                                                                }

                                                                                                is PlaylistItem -> {
                                                                                                    YouTubePlaylistMenu(
                                                                                                        playlist = item,
                                                                                                        coroutineScope = scope,
                                                                                                        onDismiss = menuState::dismiss,
                                                                                                    )
                                                                                                }

                                                                                                is PodcastItem -> {
                                                                                                    YouTubePlaylistMenu(
                                                                                                        playlist = item.asPlaylistItem(),
                                                                                                        coroutineScope = scope,
                                                                                                        onDismiss = menuState::dismiss,
                                                                                                    )
                                                                                                }

                                                                                                is EpisodeItem -> {
                                                                                                    YouTubeSongMenu(
                                                                                                        song = item.asSongItem(),
                                                                                                        onDismiss = menuState::dismiss,
                                                                                                    )
                                                                                                }
                                                                                            }
                                                                                        }
                                                                                    },
                                                                                ),
                                                                    )
                                                                }
                                                            } else {
                                                                Spacer(modifier = Modifier.width(itemWidth))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (pagerState.pageCount > 1) {
                                            Row(
                                                modifier =
                                                    Modifier
                                                        .height(24.dp)
                                                        .fillMaxWidth(),
                                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                repeat(pagerState.pageCount) { iteration ->
                                                    val color =
                                                        if (pagerState.currentPage == iteration) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                        }
                                                    Box(
                                                        modifier =
                                                            Modifier
                                                                .padding(4.dp)
                                                                .clip(CircleShape)
                                                                .background(color)
                                                                .size(8.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.Recommendations -> {
                            recommendations?.takeIf { it.isNotEmpty() }?.let { items ->
                                item(key = "recommendations_title") {
                                    val recommendationsTitle = stringResource(R.string.recommended_for_you)
                                    NavigationTitle(
                                        title = recommendationsTitle,
                                        onPlayAllClick =
                                            if (!isListenTogetherGuest) {
                                                {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = recommendationsTitle,
                                                            items =
                                                                items.distinctBy { it.id }.map {
                                                                    it.toSongItem().toMediaItem()
                                                                },
                                                        ),
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                    )
                                }

                                item(key = "recommendations_list") {
                                    LazyRow(
                                        contentPadding =
                                            WindowInsets.systemBars
                                                .only(WindowInsetsSides.Horizontal)
                                                .asPaddingValues(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        items(
                                            items = items,
                                            key = { "home_recommendation_${it.id}" },
                                        ) { recommendation ->
                                            val song = recommendation.toSongItem()

                                            RecommendationCard(
                                                recommendation = recommendation,
                                                onClick = {
                                                    if (!isListenTogetherGuest) {
                                                        playerConnection.playQueue(
                                                            YouTubeQueue(
                                                                WatchEndpoint(videoId = recommendation.id),
                                                                song.toMediaMetadata(),
                                                            ),
                                                        )
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        YouTubeSongMenu(
                                                            song = song,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.QuickPicks -> {
                            quickPicks?.takeIf { it.isNotEmpty() }?.let { quickPicks ->
                                item(key = "quick_picks_title") {
                                    val quickPicksTitle = stringResource(R.string.quick_picks)
                                    NavigationTitle(
                                        title = quickPicksTitle,
                                        onPlayAllClick =
                                            if (!isListenTogetherGuest) {
                                                {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = quickPicksTitle,
                                                            items = quickPicks.distinctBy { it.id }.map { it.toMediaItem() },
                                                        ),
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                    )
                                }

                                item(key = "quick_picks_list") {
                                    LazyHorizontalGrid(
                                        state = quickPicksLazyGridState,
                                        rows = GridCells.Fixed(4),
                                        flingBehavior = rememberSnapFlingBehavior(quickPicksSnapLayoutInfoProvider),
                                        contentPadding =
                                            WindowInsets.systemBars
                                                .only(WindowInsetsSides.Horizontal)
                                                .asPaddingValues(),
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(ListItemHeight * 4),
                                        ) {
                                            items(
                                                items = quickPicks.distinctBy { it.id },
                                                key = { "home_quickpick_${it.id}" },
                                            ) { originalSong ->
                                            // fetch song from database to keep updated
                                            val song by database
                                                .song(originalSong.id)
                                                .collectAsStateWithLifecycle(initialValue = originalSong)

                                            SongListItem(
                                                song = song!!,
                                                showInLibraryIcon = true,
                                                isActive = song!!.id == mediaMetadata?.id,
                                                isPlaying = isPlaying,
                                                isSwipeable = false,
                                                trailingContent = {
                                                    IconButton(
                                                        onClick = {
                                                            menuState.show {
                                                                SongMenu(
                                                                    originalSong = song!!,
                                                                    onDismiss = menuState::dismiss,
                                                                )
                                                            }
                                                        },
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.more_vert),
                                                            contentDescription = null,
                                                        )
                                                    }
                                                },
                                                modifier =
                                                    Modifier
                                                        .width(horizontalLazyGridItemWidth)
                                                        .combinedClickable(
                                                            onClick = {
                                                                if (!isListenTogetherGuest) {
                                                                    if (song!!.id == mediaMetadata?.id) {
                                                                        playerConnection.togglePlayPause()
                                                                    } else {
                                                                        playerConnection.playQueue(
                                                                            if (autoRadioQueue) {
                                                                                YouTubeQueue.radio(
                                                                                    song!!.toMediaMetadata(),
                                                                                )
                                                                            } else {
                                                                                ListQueue(
                                                                                    title = song!!.title,
                                                                                    items = listOf(song!!.toMediaItem())
                                                                                )
                                                                            }
                                                                        )
                                                                    }
                                                                }
                                                            },
                                                            onLongClick = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                menuState.show {
                                                                    SongMenu(
                                                                        originalSong = song!!,
                                                                        onDismiss = menuState::dismiss,
                                                                    )
                                                                }
                                                            },
                                                        ),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.RecentSongs -> {
                            recentSongs.takeIf { it.isNotEmpty() }?.let { recent ->
                                item(key = "recent_songs_title") {
                                    val recentSongsTitle = stringResource(R.string.recent_songs)
                                    NavigationTitle(
                                        title = recentSongsTitle,
                                        onPlayAllClick =
                                            if (!isListenTogetherGuest) {
                                                {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = recentSongsTitle,
                                                            items = recent.distinctBy { it.id }.map { it.toMediaItem() },
                                                        ),
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                    )
                                }

                                item(key = "recent_songs_list") {
                                    LazyHorizontalGrid(
                                        rows = GridCells.Fixed(2),
                                        contentPadding =
                                            WindowInsets.systemBars
                                                .only(WindowInsetsSides.Horizontal)
                                                .asPaddingValues(),
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(ListItemHeight * 2),
                                    ) {
                                        items(
                                            items = recent.distinctBy { it.id },
                                            key = { "home_recent_${it.id}" },
                                        ) { originalSong ->
                                            val song by database
                                                .song(originalSong.id)
                                                .collectAsStateWithLifecycle(initialValue = originalSong)

                                            SongListItem(
                                                song = song!!,
                                                showInLibraryIcon = true,
                                                isActive = song!!.id == mediaMetadata?.id,
                                                isPlaying = isPlaying,
                                                isSwipeable = false,
                                                trailingContent = {
                                                    IconButton(
                                                        onClick = {
                                                            menuState.show {
                                                                SongMenu(
                                                                    originalSong = song!!,
                                                                    onDismiss = menuState::dismiss,
                                                                )
                                                            }
                                                        },
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.more_vert),
                                                            contentDescription = null,
                                                        )
                                                    }
                                                },
                                                modifier =
                                                    Modifier
                                                        .width(horizontalLazyGridItemWidth)
                                                        .combinedClickable(
                                                            onClick = {
                                                                if (!isListenTogetherGuest) {
                                                                    if (song!!.id == mediaMetadata?.id) {
                                                                        playerConnection.togglePlayPause()
                                                                    } else {
                                                                        playerConnection.playQueue(
                                                                            if (autoRadioQueue) {
                                                                                YouTubeQueue.radio(
                                                                                    song!!.toMediaMetadata(),
                                                                                )
                                                                            } else {
                                                                                ListQueue(
                                                                                    title = song!!.title,
                                                                                    items = listOf(song!!.toMediaItem())
                                                                                )
                                                                            }
                                                                        )
                                                                    }
                                                                }
                                                            },
                                                            onLongClick = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                menuState.show {
                                                                    SongMenu(
                                                                        originalSong = song!!,
                                                                        onDismiss = menuState::dismiss,
                                                                    )
                                                                }
                                                            },
                                                        ),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            }

            HideOnScrollFAB(
                visible = quickPicks?.isNotEmpty() == true || recentSongs.isNotEmpty(),
                lazyListState = lazylistState,
                icon = R.drawable.shuffle,
                onClick = {
                    if (!isListenTogetherGuest) {
                        val luckySong =
                            (quickPicks.orEmpty() + recentSongs.orEmpty())
                                .distinctBy { it.id }
                                .randomOrNull()
                        luckySong?.let {
                            playerConnection.playQueue(YouTubeQueue.radio(it.toMediaMetadata()))
                        }
                    }
                },
                onRecognitionClick = {
                    navController.navigate("recognition")
                },
            )
        }
    }
}
