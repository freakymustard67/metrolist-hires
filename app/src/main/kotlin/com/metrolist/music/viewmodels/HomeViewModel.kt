/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterYoutubeShorts
import kotlinx.coroutines.flow.combine
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.HideYoutubeShortsKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.QuickPicks
import com.metrolist.music.constants.QuickPicksKey
import com.metrolist.music.constants.RecommendationsCacheKey
import com.metrolist.music.constants.ShowWrappedCardKey
import com.metrolist.music.constants.WrappedSeenKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SpeedDialItem
import com.metrolist.music.extensions.filterVideoSongs
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.ui.screens.wrapped.WrappedAudioService
import com.metrolist.music.ui.screens.wrapped.WrappedManager
import com.metrolist.music.utils.NetworkConnectivityObserver
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

internal fun buildSpeedDialItems(
    pinned: List<YTItem>,
    quickPicks: List<YTItem>,
    recent: List<YTItem>,
): List<YTItem> =
    (pinned + recent + quickPicks)
        .distinctBy { it.id }
        .take(27)

/**
 * One "because you listened to <seed>" recommendation. Flat and primitive-only so it
 * can go straight into the DataStore cache.
 */
@Serializable
data class Recommendation(
    val id: String,
    val title: String,
    val artists: List<String>,
    val thumbnail: String,
    val seedId: String,
    val seedTitle: String,
)

data class RecommendationSeed(
    val id: String,
    val title: String,
)

@Serializable
internal data class RecommendationsCache(
    val refreshedAt: Long,
    val items: List<Recommendation>,
)

/**
 * Picks the seeds for the recommendation row: recent plays first, one liked song mixed in,
 * then whatever is left as top-up. A song that is both recent and liked is used once.
 */
internal fun buildRecommendationSeeds(
    recent: List<Song>,
    liked: List<Song>,
    count: Int = 3,
): List<RecommendationSeed> =
    (recent.take(2) + liked.take(1) + recent.drop(2) + liked.drop(1))
        .distinctBy { it.id }
        .take(count)
        .map { RecommendationSeed(id = it.id, title = it.title) }

/**
 * Interleaves the fetched related songs by seed. A song suggested by several seeds is
 * credited to the first one, and a seed never recommends itself.
 */
internal fun buildRecommendations(
    seeds: List<RecommendationSeed>,
    relatedBySeed: Map<String, List<SongItem>>,
    perSeed: Int = 6,
    limit: Int = 18,
): List<Recommendation> {
    val seen = mutableSetOf<String>()
    val result = mutableListOf<Recommendation>()

    for (seed in seeds) {
        var takenFromSeed = 0

        for (item in relatedBySeed[seed.id].orEmpty()) {
            if (takenFromSeed == perSeed || result.size == limit) break
            if (item.id == seed.id || !seen.add(item.id)) continue

            result +=
                Recommendation(
                    id = item.id,
                    title = item.title,
                    artists = item.artists.map { it.name },
                    thumbnail = item.thumbnail,
                    seedId = seed.id,
                    seedTitle = seed.title,
                )
            takenFromSeed++
        }

        if (result.size == limit) break
    }

    return result
}

/**
 * The radio endpoint for a seed — the same call the player's radio queue uses.
 *
 * Verified against the live API: `next(WatchEndpoint(videoId))` returns
 * `relatedEndpoint = null` on WEB_REMIX (for videos and for ATV music tracks alike), while
 * this radio form returns the seed plus ~50 related songs. Do not go back to the related tab.
 */
internal fun radioEndpointFor(seedId: String) = WatchEndpoint(videoId = seedId, playlistId = "RDAMVM$seedId")

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
    val wrappedManager: WrappedManager,
    private val wrappedAudioService: WrappedAudioService,
    private val networkConnectivity: NetworkConnectivityObserver,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isRandomizing = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    val quickPicks = MutableStateFlow<List<Song>?>(null)

    val recommendations = MutableStateFlow<List<Recommendation>?>(null)

    private var recommendationsJob: Job? = null

    private val hideVideoSongs =
        context.dataStore.data
            .map { it[HideVideoSongsKey] ?: false }
            .distinctUntilChanged()

    /**
     * Songs the user actually played, newest first. Local-only by design: the home
     * screen must never wait on the network.
     */
    val recentSongs: StateFlow<List<Song>> =
        combine(database.recentSongs(30), hideVideoSongs) { songs, hide ->
            songs.filterVideoSongs(hide)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val pinnedSpeedDialItems: StateFlow<List<SpeedDialItem>> =
        database.speedDialDao.getAll()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val speedDialItems: StateFlow<List<YTItem>> =
        combine(
            database.speedDialDao.getAll(),
            quickPicks,
            recentSongs,
        ) { pinned, quick, recent ->
            buildSpeedDialItems(
                pinned = pinned.map { it.toYTItem() },
                quickPicks =
                    quick.orEmpty().map { song ->
                        SongItem(
                            id = song.id,
                            title = song.title,
                            artists = song.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = song.thumbnailUrl ?: "",
                        )
                    },
                recent =
                    recent.map { song ->
                        SongItem(
                            id = song.id,
                            title = song.title,
                            artists = song.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = song.thumbnailUrl ?: "",
                        )
                    },
            )
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

suspend fun getRandomItem(): YTItem? {
        isRandomizing.value = true
        try {
            // Visual feedback for the animation
            kotlinx.coroutines.delay(1000)

            val candidates = (quickPicks.value.orEmpty() + recentSongs.value).distinctBy { it.id }
            val song = candidates.randomOrNull() ?: return null

            return SongItem(
                id = song.id,
                title = song.title,
                artists = song.artists.map { Artist(name = it.name, id = it.id) },
                thumbnail = song.thumbnailUrl ?: "",
            )
        } finally {
            isRandomizing.value = false
        }
    }

        val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

	val showWrappedCard: StateFlow<Boolean> = context.dataStore.data.map { prefs ->
        val showWrappedPref = prefs[ShowWrappedCardKey] ?: false
        val seen = prefs[WrappedSeenKey] ?: false
        val isBeforeDate = LocalDate.now().isBefore(LocalDate.of(2026, 2, 1))

        isBeforeDate && (!seen || showWrappedPref)
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    val wrappedSeen: StateFlow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[WrappedSeenKey] ?: false
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun markWrappedAsSeen() {
        viewModelScope.launch(Dispatchers.IO) {
            context.safeDataStoreEdit {
                it[WrappedSeenKey] = true
            }
        }
    }
    private suspend fun getQuickPicks() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                val relatedSongs = database.quickPicks().first().filterVideoSongs(hideVideoSongs)
                val forgotten = database.forgottenFavorites().first().filterVideoSongs(hideVideoSongs).take(8)

                quickPicks.value = (relatedSongs + forgotten)
                    .distinctBy { it.id }
                    .shuffled()
                    .take(20)
            }
            QuickPicks.LAST_LISTEN -> {
                val song = database.latestEvent().first()?.song
                if (song != null && database.hasRelatedSongs(song.id)) {
                    quickPicks.value = database.getRelatedSongs(song.id).first().filterVideoSongs(hideVideoSongs).shuffled().take(20)
                }
            }
        }
    }

    private suspend fun load() {
        getQuickPicks()
        loadRecommendations()

        if (YouTube.cookie != null) {
            viewModelScope.launch(Dispatchers.IO) { loadAccountInfo() }
        }
    }

    /**
     * Shows the cached recommendations immediately, then refreshes them in the
     * background when they are stale. The home screen never waits on this.
     */
    private suspend fun loadRecommendations() {
        val cached = readRecommendationsCache()
        if (cached?.items?.isNotEmpty() == true) {
            recommendations.value = cached.items
        }

        val isFresh = cached != null && System.currentTimeMillis() - cached.refreshedAt < RECOMMENDATIONS_TTL
        if (!isFresh && recommendationsJob?.isActive != true) {
            recommendationsJob = viewModelScope.launch(Dispatchers.IO) { refreshRecommendations() }
        }
    }

    private suspend fun refreshRecommendations() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideShorts = context.dataStore.get(HideYoutubeShortsKey, false)

        val seeds = recommendationSeeds()
        if (seeds.isEmpty()) return

        val playedRecently = database.recentSongs(RECOMMENDATION_EXCLUDE_COUNT).first().map { it.id }.toSet()

        val relatedBySeed =
            fetchRelatedSongs(seeds, hideExplicit, hideVideoSongs, hideShorts, playedRecently)
                .ifEmpty { localRelatedSongs(seeds, hideVideoSongs, playedRecently) }

        val built = buildRecommendations(seeds, relatedBySeed)
        if (built.isEmpty()) return

        recommendations.value = built
        writeRecommendationsCache(RecommendationsCache(System.currentTimeMillis(), built))
    }

    /**
     * The watch-next "related" tab is empty on WEB_REMIX, so the radio queue is the source:
     * it is the same endpoint the player uses, and its items are the songs that follow from
     * the seed. One request per seed, plus one retry because the first call after a cold
     * start can race visitor-data setup.
     */
    private suspend fun fetchRelatedSongs(
        seeds: List<RecommendationSeed>,
        hideExplicit: Boolean,
        hideVideoSongs: Boolean,
        hideShorts: Boolean,
        exclude: Set<String>,
    ): Map<String, List<SongItem>> {
        val result = mutableMapOf<String, List<SongItem>>()

        for (seed in seeds) {
            val endpoint = radioEndpointFor(seed.id)
            var page = YouTube.next(endpoint).onFailure { reportException(it) }.getOrNull()

            if (page == null) {
                delay(RECOMMENDATION_RETRY_DELAY)
                page = YouTube.next(endpoint).onFailure { reportException(it) }.getOrNull()
            }

            val songs =
                page?.items
                    ?.filter { it.id != seed.id && it.id !in exclude }
                    ?.filterExplicit(hideExplicit)
                    ?.filterVideoSongs(hideVideoSongs)
                    ?.filterYoutubeShorts(hideShorts)
                    .orEmpty()

            if (songs.isNotEmpty()) result[seed.id] = songs
        }

        return result
    }

    /** Offline fallback: the songs the app already mapped as related to what was played. */
    private suspend fun localRelatedSongs(
        seeds: List<RecommendationSeed>,
        hideVideoSongs: Boolean,
        exclude: Set<String>,
    ): Map<String, List<SongItem>> {
        val result = mutableMapOf<String, List<SongItem>>()

        for (seed in seeds) {
            val songs =
                database.getRelatedSongs(seed.id).first()
                    .filter { it.id !in exclude }
                    .filterVideoSongs(hideVideoSongs)
                    .map { song ->
                        SongItem(
                            id = song.id,
                            title = song.title,
                            artists = song.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = song.thumbnailUrl ?: "",
                        )
                    }

            if (songs.isNotEmpty()) result[seed.id] = songs
        }

        return result
    }

    /** Recently played first, then liked songs: the row follows what the user actually listens to. */
    private suspend fun recommendationSeeds(): List<RecommendationSeed> {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)

        val recent = database.recentSongs(RECOMMENDATION_SEED_COUNT).first()
        val liked = database.likedSongsByCreateDateDesc(limit = RECOMMENDATION_SEED_COUNT, offset = 0)

        return buildRecommendationSeeds(
            recent = recent.filterVideoSongs(hideVideoSongs),
            liked = liked.filterVideoSongs(hideVideoSongs),
        )
    }

    private suspend fun readRecommendationsCache(): RecommendationsCache? =
        context.dataStore.data
            .map { it[RecommendationsCacheKey] }
            .first()
            ?.let { raw -> runCatching { recommendationJson.decodeFromString<RecommendationsCache>(raw) }.getOrNull() }

    private suspend fun writeRecommendationsCache(cache: RecommendationsCache) {
        runCatching {
            context.safeDataStoreEdit {
                it[RecommendationsCacheKey] = recommendationJson.encodeToString(cache)
            }
        }.onFailure { reportException(it) }
    }

    private suspend fun loadAccountInfo() {
        YouTube.accountInfo().onSuccess { info ->
            accountName.value = info.name
            accountImageUrl.value = info.thumbnailUrl
        }.onFailure {
            reportException(it)
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        isRefreshing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            load()
            isRefreshing.value = false
        }
        // Run sync when user manually refreshes
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }
    }

    override fun onCleared() {
        super.onCleared()
        wrappedManager.dispose()
    }

    init {
        // Run sync in separate coroutine with cooldown to avoid blocking UI
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }

        var wasOffline = !networkConnectivity.networkStatus.value
        viewModelScope.launch(Dispatchers.IO) {
            networkConnectivity.networkStatus.collect { isConnected ->
                if (!isConnected) {
                    wasOffline = true
                } else if (wasOffline) {
                    wasOffline = false
                    refresh()
                }
            }
        }

        // Prepare wrapped data in background
        viewModelScope.launch(Dispatchers.IO) {
            showWrappedCard.collect { shouldShow ->
                if (shouldShow && !wrappedManager.state.value.isDataReady) {
                    try {
                        wrappedManager.prepare()
                        val state = wrappedManager.state.first { it.isDataReady }
                        val trackMap = state.trackMap
                        if (trackMap.isNotEmpty()) {
                            val firstTrackId = trackMap.entries.first().value
                            wrappedAudioService.prepareTrack(firstTrackId)
                        }
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }
        }

        // Listen for cookie changes and reload account data
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] to it[AccountNameKey] }
                .distinctUntilChanged()
                .collect { (cookie, savedAccountName) ->
                    if (!cookie.isNullOrEmpty()) {
                        YouTube.cookie = cookie
                        accountName.value = savedAccountName.orEmpty().ifBlank { "Guest" }
                        loadAccountInfo()
                    } else {
                        accountName.value = "Guest"
                        accountImageUrl.value = null
                    }
                }
        }
    }

    private var isHomeDataLoaded = false

    private companion object {
        val recommendationJson = Json { ignoreUnknownKeys = true }

        /** How long a recommendation set is reused before it is fetched again. */
        const val RECOMMENDATIONS_TTL = 12 * 60 * 60 * 1000L

        /** Seeds per refill; each seed costs one radio request. */
        const val RECOMMENDATION_SEED_COUNT = 3

        /** Songs played this recently are not worth recommending back. */
        const val RECOMMENDATION_EXCLUDE_COUNT = 50

        /** Wait before retrying a seed whose first request came back empty. */
        const val RECOMMENDATION_RETRY_DELAY = 1_500L
    }

    fun loadHomeData() {
        if (isHomeDataLoaded) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cookie = context.dataStore.data
                    .map { it[InnerTubeCookieKey] }
                    .distinctUntilChanged()
                    .first()

                if (!cookie.isNullOrEmpty()) {
                    YouTube.cookie = cookie
                }

                isHomeDataLoaded = true
                load()
            } catch (e: Exception) {
                isHomeDataLoaded = false
                Timber.e(e, "Failed to load home data")
            }
        }
    }
}
