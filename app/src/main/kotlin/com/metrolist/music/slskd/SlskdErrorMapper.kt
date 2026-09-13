/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.slskd

import com.metrolist.music.R

object SlskdErrorMapper {
    fun messageRes(error: Throwable): Int =
        when (error) {
            is SlskdException.Credential -> R.string.slskd_error_credentials
            is SlskdException.RelayAgent -> R.string.slskd_error_relay_agent
            is SlskdException.Forbidden -> R.string.slskd_error_forbidden
            is SlskdException.NotFound -> R.string.slskd_error_not_found
            is SlskdException.DuplicateBatch -> R.string.slskd_error_duplicate
            is SlskdException.RateLimited -> R.string.slskd_error_rate_limited
            is SlskdException.SoulseekDisconnected -> R.string.slskd_error_soulseek_disconnected
            is SlskdException.Network -> R.string.slskd_error_network
            is SlskdException.Timeout -> R.string.slskd_error_timeout
            is SlskdException.Server -> R.string.slskd_error_server
            is SlskdException.Config -> R.string.slskd_error_config
            else -> R.string.slskd_error_unexpected
        }
}
