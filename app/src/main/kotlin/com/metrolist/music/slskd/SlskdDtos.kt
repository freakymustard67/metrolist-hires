/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.slskd

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SlskdSearchRequest(
    val searchText: String,
    val searchTimeout: Int? = null,
    val responseLimit: Int? = null,
    val fileLimit: Int? = null,
)

@Serializable
data class SlskdSearch(
    val id: String,
    val isComplete: Boolean = false,
    val endedAt: String? = null,
    val responses: List<SlskdSearchResponse> = emptyList(),
)

@Serializable
data class SlskdSearchResponse(
    val username: String = "",
    val hasFreeUploadSlot: Boolean = false,
    val queueLength: Long = 0L,
    val uploadSpeed: Int = 0,
    val files: List<SlskdSearchFile> = emptyList(),
)

@Serializable
data class SlskdSearchFile(
    val filename: String = "",
    val size: Long = 0L,
    val extension: String? = null,
    val bitRate: Int? = null,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val length: Int? = null,
)

@Serializable
data class SlskdEnqueueBatchRequest(
    val id: String,
    val searchId: String? = null,
    val username: String,
    val files: List<SlskdEnqueueBatchFile>,
    val options: SlskdEnqueueBatchOptions? = null,
)

@Serializable
data class SlskdEnqueueBatchFile(
    val filename: String,
    val size: Long,
)

@Serializable
data class SlskdEnqueueBatchOptions(
    val destination: String? = null,
)

@Serializable
data class SlskdEnqueueBatchResult(
    val batch: SlskdBatch,
    val failures: List<SlskdBatchFailure> = emptyList(),
)

@Serializable
data class SlskdUserDownloads(
    val username: String = "",
    val directories: List<SlskdDownloadDirectory> = emptyList(),
)

@Serializable
data class SlskdDownloadDirectory(
    val directory: String = "",
    val fileCount: Int = 0,
    val files: List<SlskdTransfer> = emptyList(),
)

@Serializable
data class SlskdBatchFailure(
    val filename: String? = null,
    val message: String? = null,
)

@Serializable
data class SlskdBatch(
    val id: String,
    val transfers: List<SlskdTransfer> = emptyList(),
)

@Serializable
data class SlskdTransfer(
    val id: String,
    val username: String? = null,
    val filename: String? = null,
    val size: Long = 0L,
    val bytesTransferred: Long = 0L,
    val endedAt: String? = null,
    val exception: String? = null,
    val placeInQueue: Int? = null,
) {
    val isTerminal: Boolean get() = endedAt != null

    val isSuccessful: Boolean
        get() = endedAt != null && exception == null && size > 0 && bytesTransferred >= size
}

@Serializable
data class SlskdFilesystemDirectory(
    val files: List<SlskdFilesystemFile> = emptyList(),
    val directories: List<SlskdFilesystemDirectory> = emptyList(),
)

@Serializable
data class SlskdFilesystemFile(
    val name: String = "",
    @SerialName("fullName")
    val fullName: String = "",
    val length: Long = 0L,
)
