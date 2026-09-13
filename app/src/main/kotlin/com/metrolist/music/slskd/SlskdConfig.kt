/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.slskd

import kotlin.io.encoding.Base64

object SlskdConfig {
    const val STREAM_START_BYTES = 10_000_000L

    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) {
            throw SlskdException.Config("Server URL is empty")
        }
        val lower = trimmed.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw SlskdException.Config("Server URL must start with http:// or https://")
        }
        val uri = runCatching { java.net.URI(trimmed) }.getOrNull()
            ?: throw SlskdException.Config("Server URL is not a valid URL")
        if (!uri.query.isNullOrEmpty() || !uri.fragment.isNullOrEmpty()) {
            throw SlskdException.Config("Server URL must not include a query or fragment")
        }
        // Calls append /api/v0/... to this base, so a path containing the API root
        // would double it (http://host/api/v0 -> .../api/v0/api/v0/...). A plain
        // reverse-proxy prefix (slskd web.url_base) composes correctly and stays allowed.
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        if (segments.any { it.equals("api", ignoreCase = true) }) {
            throw SlskdException.Config("Server URL must not include the /api path; use http(s)://host:port only")
        }
        return trimmed
    }

    fun isConfigured(
        baseUrl: String,
        apiKey: String,
    ): Boolean = baseUrl.isNotBlank() && apiKey.isNotBlank()

    fun base64Url(text: String): String =
        Base64.UrlSafe.encode(text.toByteArray(Charsets.UTF_8))

    /**
     * Buffered bytes after which progressive playback may start.
     * Files under the buffer behave exactly as before (await completion).
     */
    fun streamStartBytes(totalBytes: Long): Long = minOf(totalBytes.coerceAtLeast(0L), STREAM_START_BYTES)
}
