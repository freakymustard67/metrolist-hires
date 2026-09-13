package com.metrolist.music.viewmodels

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationsTest {
    @Test
    fun `a song suggested by two seeds is credited to the first seed`() {
        val seeds =
            listOf(
                RecommendationSeed(id = "seed-1", title = "First seed"),
                RecommendationSeed(id = "seed-2", title = "Second seed"),
            )
        val shared = ytSong("shared")
        val related =
            mapOf(
                "seed-1" to listOf(shared),
                "seed-2" to listOf(shared, ytSong("only-second")),
            )

        val result = buildRecommendations(seeds, related)

        assertEquals(listOf("shared", "only-second"), result.map { it.id })
        assertEquals(listOf("First seed", "Second seed"), result.map { it.seedTitle })
    }

    @Test
    fun `a seed never recommends itself`() {
        val seed = RecommendationSeed(id = "seed", title = "Seed")
        val related = mapOf("seed" to listOf(ytSong("seed"), ytSong("other")))

        val result = buildRecommendations(listOf(seed), related)

        assertEquals(listOf("other"), result.map { it.id })
    }

    @Test
    fun `each seed contributes at most perSeed songs`() {
        val seeds = listOf(RecommendationSeed(id = "seed", title = "Seed"))
        val related = mapOf("seed" to (1..10).map { ytSong("song-$it") })

        val result = buildRecommendations(seeds, related, perSeed = 3)

        assertEquals(listOf("song-1", "song-2", "song-3"), result.map { it.id })
    }

    @Test
    fun `the total never exceeds the limit and keeps seed order`() {
        val seeds =
            listOf(
                RecommendationSeed(id = "seed-1", title = "First"),
                RecommendationSeed(id = "seed-2", title = "Second"),
            )
        val related =
            mapOf(
                "seed-1" to (1..4).map { ytSong("first-$it") },
                "seed-2" to (1..4).map { ytSong("second-$it") },
            )

        val result = buildRecommendations(seeds, related, perSeed = 4, limit = 5)

        assertEquals(listOf("first-1", "first-2", "first-3", "first-4", "second-1"), result.map { it.id })
    }

    @Test
    fun `a seed without related songs contributes nothing`() {
        val seeds =
            listOf(
                RecommendationSeed(id = "empty", title = "Empty"),
                RecommendationSeed(id = "seed", title = "Seed"),
            )

        val result = buildRecommendations(seeds, mapOf("seed" to listOf(ytSong("only"))))

        assertEquals(listOf("only"), result.map { it.id })
    }

    @Test
    fun `seeds mix two recent plays with one liked song`() {
        val recent = listOf(song("recent-1"), song("recent-2"), song("recent-3"))
        val liked = listOf(song("liked-1"), song("liked-2"))

        val seeds = buildRecommendationSeeds(recent, liked)

        assertEquals(listOf("recent-1", "recent-2", "liked-1"), seeds.map { it.id })
    }

    @Test
    fun `a song that is both recent and liked is only used once`() {
        val recent = listOf(song("both"), song("recent-2"))
        val liked = listOf(song("both"), song("liked-1"))

        val seeds = buildRecommendationSeeds(recent, liked)

        assertEquals(listOf("both", "recent-2", "liked-1"), seeds.map { it.id })
    }

    @Test
    fun `seeds fall back to liked songs when nothing was played recently`() {
        val liked = listOf(song("liked-1"), song("liked-2"), song("liked-3"))

        val seeds = buildRecommendationSeeds(recent = emptyList(), liked = liked)

        assertEquals(listOf("liked-1", "liked-2", "liked-3"), seeds.map { it.id })
    }

    @Test
    fun `seeds are fetched through the radio endpoint, not the empty related tab`() {
        val endpoint = radioEndpointFor("track-id")

        assertEquals("track-id", endpoint.videoId)
        assertEquals("RDAMVMtrack-id", endpoint.playlistId)
    }

    private fun song(id: String) =
        Song(
            song = SongEntity(id = id, title = id),
            artists = emptyList(),
        )

    private fun ytSong(id: String) =
        SongItem(
            id = id,
            title = id,
            artists = listOf(Artist(name = "artist", id = null)),
            thumbnail = "",
        )
}
