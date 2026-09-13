package com.metrolist.music.viewmodels

import com.metrolist.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSpeedDialTest {
    @Test
    fun `recent and quick pick songs fill sparse speed dial without replacing pinned items`() {
        val pinned = song("pinned")
        val recent = song("recent")

        val result =
            buildSpeedDialItems(
                pinned = listOf(pinned),
                quickPicks = emptyList(),
                recent = listOf(pinned.copy(title = "duplicate"), recent),
            )

        assertEquals(listOf(pinned, recent), result)
    }

    @Test
    fun `recent plays come before quick picks`() {
        val recent = song("recent")
        val quickPick = song("quick-pick")

        val result =
            buildSpeedDialItems(
                pinned = emptyList(),
                quickPicks = listOf(quickPick),
                recent = listOf(recent),
            )

        assertEquals(listOf(recent, quickPick), result)
    }

    @Test
    fun `speed dial holds at most 27 entries`() {
        val pinned = (1..30).map { song("pinned-$it") }

        val result =
            buildSpeedDialItems(
                pinned = pinned,
                quickPicks = emptyList(),
                recent = emptyList(),
            )

        assertEquals(27, result.size)
    }

    private fun song(id: String) =
        SongItem(
            id = id,
            title = id,
            artists = emptyList(),
            thumbnail = "",
        )
}
