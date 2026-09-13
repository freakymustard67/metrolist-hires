package com.metrolist.music.viewmodels

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
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
        val shared = song("shared")
        val related =
            mapOf(
                "seed-1" to listOf(shared),
                "seed-2" to listOf(shared, song("only-second")),
            )

        val result = buildRecommendations(seeds, related)

        assertEquals(listOf("shared", "only-second"), result.map { it.id })
        assertEquals(listOf("First seed", "Second seed"), result.map { it.seedTitle })
    }

    @Test
    fun `a seed never recommends itself`() {
        val seed = RecommendationSeed(id = "seed", title = "Seed")
        val related = mapOf("seed" to listOf(song("seed"), song("other")))

        val result = buildRecommendations(listOf(seed), related)

        assertEquals(listOf("other"), result.map { it.id })
    }

    @Test
    fun `each seed contributes at most perSeed songs`() {
        val seeds = listOf(RecommendationSeed(id = "seed", title = "Seed"))
        val related = mapOf("seed" to (1..10).map { song("song-$it") })

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
                "seed-1" to (1..4).map { song("first-$it") },
                "seed-2" to (1..4).map { song("second-$it") },
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

        val result = buildRecommendations(seeds, mapOf("seed" to listOf(song("only"))))

        assertEquals(listOf("only"), result.map { it.id })
    }

    private fun song(id: String) =
        SongItem(
            id = id,
            title = id,
            artists = listOf(Artist(name = "artist", id = null)),
            thumbnail = "",
        )
}
