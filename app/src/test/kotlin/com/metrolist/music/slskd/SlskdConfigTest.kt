package com.metrolist.music.slskd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlskdConfigTest {
    @Test
    fun `trailing slash is normalized once`() {
        assertEquals("http://host:5030", SlskdConfig.normalizeBaseUrl("http://host:5030/"))
        assertEquals("http://host:5030", SlskdConfig.normalizeBaseUrl("http://host:5030///"))
    }

    @Test
    fun `url base prefix is preserved`() {
        assertEquals(
            "https://host/slskd",
            SlskdConfig.normalizeBaseUrl("https://host/slskd/"),
        )
    }

    @Test(expected = SlskdException.Config::class)
    fun `blank url is rejected`() {
        SlskdConfig.normalizeBaseUrl("   ")
    }

    @Test(expected = SlskdException.Config::class)
    fun `non http scheme is rejected`() {
        SlskdConfig.normalizeBaseUrl("ftp://host:21")
    }

    @Test
    fun `stream starts at ten megabytes for large files`() {
        assertEquals(10_000_000L, SlskdConfig.streamStartBytes(200_000_000L))
    }

    @Test
    fun `small files start at completion`() {
        assertEquals(4_000_000L, SlskdConfig.streamStartBytes(4_000_000L))
        assertEquals(0L, SlskdConfig.streamStartBytes(0L))
    }

    @Test(expected = SlskdException.Config::class)
    fun `bare host without scheme is rejected`() {
        SlskdConfig.normalizeBaseUrl("host:5030")
    }

    @Test
    fun `configured requires url and key`() {
        assertTrue(SlskdConfig.isConfigured("http://host:5030", "key"))
        assertFalse(SlskdConfig.isConfigured("", "key"))
        assertFalse(SlskdConfig.isConfigured("http://host:5030", ""))
    }

    @Test
    fun `base64url output never contains route separators`() {
        val encoded = SlskdConfig.base64Url("müsic ♥ test/sub\\dir: file.flac")

        assertTrue('/' !in encoded)
        assertTrue('+' !in encoded)
    }

    @Test(expected = SlskdException.Config::class)
    fun `api path is rejected`() {
        SlskdConfig.normalizeBaseUrl("http://host:5030/api/v0")
    }

    @Test(expected = SlskdException.Config::class)
    fun `query string is rejected`() {
        SlskdConfig.normalizeBaseUrl("http://host:5030/?foo=bar")
    }

}
