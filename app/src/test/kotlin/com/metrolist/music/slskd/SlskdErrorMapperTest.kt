package com.metrolist.music.slskd

import com.metrolist.music.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SlskdErrorMapperTest {
    @Test
    fun `http failures map to distinct messages`() {
        val mapped =
            setOf(
                SlskdErrorMapper.messageRes(SlskdException.Credential()),
                SlskdErrorMapper.messageRes(SlskdException.RelayAgent()),
                SlskdErrorMapper.messageRes(SlskdException.RateLimited()),
                SlskdErrorMapper.messageRes(SlskdException.NotFound()),
                SlskdErrorMapper.messageRes(SlskdException.DuplicateBatch()),
            )

        assertEquals(5, mapped.size)
    }

    @Test
    fun `401 maps to the credential message`() {
        assertEquals(
            R.string.slskd_error_credentials,
            SlskdErrorMapper.messageRes(SlskdException.Credential()),
        )
    }

    @Test
    fun `soulseek disconnected maps to the login message`() {
        assertEquals(
            R.string.slskd_error_soulseek_disconnected,
            SlskdErrorMapper.messageRes(SlskdException.SoulseekDisconnected()),
        )
    }

    @Test
    fun `403 relay-agent message mentions controller mode`() {
        assertEquals(
            R.string.slskd_error_relay_agent,
            SlskdErrorMapper.messageRes(SlskdException.RelayAgent()),
        )
        assertNotEquals(
            R.string.slskd_error_relay_agent,
            SlskdErrorMapper.messageRes(SlskdException.Forbidden()),
        )
    }

    @Test
    fun `429 maps to the busy message`() {
        assertEquals(
            R.string.slskd_error_rate_limited,
            SlskdErrorMapper.messageRes(SlskdException.RateLimited()),
        )
    }

    @Test
    fun `404 maps to the missing message`() {
        assertEquals(
            R.string.slskd_error_not_found,
            SlskdErrorMapper.messageRes(SlskdException.NotFound()),
        )
    }

    @Test
    fun `409 maps to the duplicate message`() {
        assertEquals(
            R.string.slskd_error_duplicate,
            SlskdErrorMapper.messageRes(SlskdException.DuplicateBatch()),
        )
    }

    @Test
    fun `unknown failures map to the unexpected message`() {
        assertEquals(
            R.string.slskd_error_unexpected,
            SlskdErrorMapper.messageRes(IllegalStateException("boom")),
        )
    }
}
