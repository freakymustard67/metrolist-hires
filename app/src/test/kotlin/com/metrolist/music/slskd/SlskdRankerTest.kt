package com.metrolist.music.slskd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlskdRankerTest {
    private fun candidate(
        filename: String,
        size: Long = 10_000_000L,
        extension: String? = null,
        bitRate: Int? = null,
        sampleRate: Int? = null,
        bitDepth: Int? = null,
        queueLength: Long = 0L,
        uploadSpeed: Int = 1000,
        hasFreeUploadSlot: Boolean = true,
        username: String = "peer",
    ) = SlskdCandidate(
        username = username,
        hasFreeUploadSlot = hasFreeUploadSlot,
        queueLength = queueLength,
        uploadSpeed = uploadSpeed,
        file =
            SlskdSearchFile(
                filename = filename,
                size = size,
                extension = extension,
                bitRate = bitRate,
                sampleRate = sampleRate,
                bitDepth = bitDepth,
            ),
    )

    @Test
    fun `lossless ranks above lossy at equal size`() {
        val ranked =
            SlskdRanker.rank(
                listOf(
                    candidate("song.mp3", bitRate = 320),
                    candidate("song.flac"),
                ),
            )

        assertEquals(listOf("flac", "mp3"), ranked.map { it.extension })
    }

    @Test
    fun `higher bitrate ranks first within the same tier`() {
        val ranked =
            SlskdRanker.rank(
                listOf(
                    candidate("a.flac", bitRate = 800),
                    candidate("b.flac", bitRate = 1400),
                ),
            )

        assertEquals(listOf("b.flac", "a.flac"), ranked.map { it.filename })
    }

    @Test
    fun `shorter queue ranks first at equal quality`() {
        val ranked =
            SlskdRanker.rank(
                listOf(
                    candidate("busy.flac", bitRate = 900, queueLength = 50),
                    candidate("free.flac", bitRate = 900, queueLength = 0),
                ),
            )

        assertEquals(listOf("free.flac", "busy.flac"), ranked.map { it.filename })
    }

    @Test
    fun `faster uploader ranks first when quality and queue tie`() {
        val ranked =
            SlskdRanker.rank(
                listOf(
                    candidate("slow.flac", bitRate = 900, uploadSpeed = 100),
                    candidate("fast.flac", bitRate = 900, uploadSpeed = 9000),
                ),
            )

        assertEquals(listOf("fast.flac", "slow.flac"), ranked.map { it.filename })
    }

    @Test
    fun `aiff is excluded since no extractor can parse it`() {
        val ranked =
            SlskdRanker.rank(
                listOf(
                    candidate("song.aiff"),
                    candidate("song.flac"),
                ),
            )

        assertEquals(listOf("song.flac"), ranked.map { it.filename })
    }

    @Test
    fun `matroska audio containers are accepted`() {
        val ranked =
            SlskdRanker.rank(
                listOf(
                    candidate("song.mka"),
                    candidate("song.webm"),
                ),
            )

        assertEquals(2, ranked.size)
    }

    @Test
    fun `free upload slot is preferred on a full tie`() {
        val ranked =
            SlskdRanker.rank(
                listOf(
                    candidate("a.flac", bitRate = 900, hasFreeUploadSlot = false),
                    candidate("b.flac", bitRate = 900, hasFreeUploadSlot = true),
                ),
            )

        assertEquals(listOf("b.flac", "a.flac"), ranked.map { it.filename })
    }

    @Test
    fun `larger file ranks first when audio attributes tie`() {
        val ranked =
            SlskdRanker.rank(
                listOf(
                    candidate("small.flac", size = 5_000_000L),
                    candidate("big.flac", size = 30_000_000L),
                ),
            )

        assertEquals(listOf("big.flac", "small.flac"), ranked.map { it.filename })
    }

    @Test
    fun `unsupported extensions are dropped`() {
        val ranked =
            SlskdRanker.rank(
                listOf(
                    candidate("song.txt"),
                    candidate("song.exe"),
                    candidate("song.flac"),
                ),
            )

        assertEquals(listOf("song.flac"), ranked.map { it.filename })
    }

    @Test
    fun `extension falls back to filename when undeclared`() {
        val ranked = SlskdRanker.rank(listOf(candidate("song.FLAC")))

        assertEquals("flac", ranked.single().extension)
    }

    @Test
    fun `results are capped at twenty`() {
        val ranked = SlskdRanker.rank((1..40).map { candidate("song%02d.flac".format(it)) })

        assertEquals(20, ranked.size)
        assertTrue(ranked.all { it.extension == "flac" })
    }
}
