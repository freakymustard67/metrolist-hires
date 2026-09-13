/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.slskd

/**
 * Typed failures for slskd server calls. HTTP mapping lives in [SlskdApiClient];
 * user-facing text lives in [SlskdErrorMapper] so unit tests stay free of resources.
 */
sealed class SlskdException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Config(
        message: String,
    ) : SlskdException(message)

    class Network(
        cause: Throwable,
    ) : SlskdException("Network error contacting slskd server: ${cause.message}", cause)

    class Timeout(
        cause: Throwable? = null,
    ) : SlskdException("slskd request timed out", cause)

    class Credential(
        cause: Throwable? = null,
    ) : SlskdException("slskd rejected the API key (401)", cause)

    class RelayAgent : SlskdException("slskd is running as a relay agent (403)")

    class Forbidden : SlskdException("slskd forbade the request (403)")

    class NotFound : SlskdException("slskd has no such search, batch, user, or file (404)")

    class DuplicateBatch : SlskdException("slskd already has a batch with this id (409)")

    class TransferFailed(
        detail: String?,
    ) : SlskdException("slskd download failed${detail?.let { ": $it" } ?: ""}")

    class SoulseekDisconnected : SlskdException("slskd is not logged into Soulseek")

    class RateLimited : SlskdException("slskd is busy with another operation (429)")

    class Server(
        val code: Int,
        val body: String?,
    ) : SlskdException("slskd server error ($code)")

    class BadResponse(
        val code: Int,
        val body: String?,
    ) : SlskdException("slskd returned an unexpected response ($code)")
}
