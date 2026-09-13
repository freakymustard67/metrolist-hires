/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.db.entities.Event
import com.metrolist.music.db.entities.SongEntity
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecentSongsTest {
    private lateinit var database: InternalDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.dao.insert(SongEntity(id = HEAVY_ROTATION_ID, title = "Heavy rotation"))
        database.dao.insert(SongEntity(id = FRESH_ID, title = "Fresh"))
        database.dao.insert(SongEntity(id = NEVER_PLAYED_ID, title = "Never played"))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `songs are ordered by their most recent play, not by how often they were played`() = runBlocking {
        // The heavy rotation song was played five times, but not since last week.
        repeat(5) { index ->
            insertEvent(
                id = index.toLong() + 1,
                songId = HEAVY_ROTATION_ID,
                timestamp = NOW.minusDays(7).plusMinutes(index.toLong()),
                playTime = 180_000,
            )
        }
        // The fresh song was played once, a minute ago.
        insertEvent(id = 99, songId = FRESH_ID, timestamp = NOW.minusMinutes(1), playTime = 5_000)

        assertEquals(listOf(FRESH_ID, HEAVY_ROTATION_ID), recentSongs(limit = 10).map { it.id })
    }

    @Test
    fun `repeated plays collapse into a single entry`() = runBlocking {
        repeat(50) { index ->
            insertEvent(
                id = index.toLong() + 1,
                songId = HEAVY_ROTATION_ID,
                timestamp = NOW.minusMinutes(index.toLong()),
            )
        }

        val songs = recentSongs(limit = 10)

        assertEquals(listOf(HEAVY_ROTATION_ID), songs.map { it.id })
    }

    @Test
    fun `limit keeps only the newest songs`() = runBlocking {
        insertEvent(id = 1, songId = HEAVY_ROTATION_ID, timestamp = NOW.minusDays(1))
        insertEvent(id = 2, songId = FRESH_ID, timestamp = NOW)

        assertEquals(listOf(FRESH_ID), recentSongs(limit = 1).map { it.id })
    }

    @Test
    fun `songs that were never played stay out of the list`() = runBlocking {
        insertEvent(id = 1, songId = FRESH_ID, timestamp = NOW)

        assertEquals(listOf(FRESH_ID), recentSongs(limit = 10).map { it.id })
    }

    private suspend fun recentSongs(limit: Int) = database.dao.recentSongs(limit).first()

    private fun insertEvent(
        id: Long,
        songId: String,
        timestamp: LocalDateTime,
        playTime: Long = 30_000,
    ) {
        database.dao.insert(
            Event(
                id = id,
                songId = songId,
                timestamp = timestamp,
                playTime = playTime,
            ),
        )
    }

    private companion object {
        const val HEAVY_ROTATION_ID = "heavy-rotation"
        const val FRESH_ID = "fresh"
        const val NEVER_PLAYED_ID = "never-played"
        val NOW: LocalDateTime = LocalDateTime.of(2026, 9, 13, 12, 0)
    }
}
