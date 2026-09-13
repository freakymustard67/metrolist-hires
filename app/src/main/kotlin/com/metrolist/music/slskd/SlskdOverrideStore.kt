/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.slskd

import java.util.concurrent.ConcurrentHashMap

data class SlskdOverride(
    val mediaId: String,
    val fileUrl: String,
    val transferId: String? = null,
)

object SlskdOverrideStore {
    private val overrides = ConcurrentHashMap<String, SlskdOverride>()

    fun get(mediaId: String): SlskdOverride? = overrides[mediaId]

    fun cacheKeyFor(
        mediaId: String,
        transferId: String?,
    ): String = "slskd:${transferId ?: mediaId}"

    fun put(override: SlskdOverride) {
        overrides[override.mediaId] = override
    }

    fun remove(mediaId: String) {
        overrides.remove(mediaId)
    }

    fun contains(mediaId: String): Boolean = overrides.containsKey(mediaId)

    fun clear() {
        overrides.clear()
    }
}
