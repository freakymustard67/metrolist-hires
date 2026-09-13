/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.slskd

data class RankedSlskdFile(
    val username: String,
    val filename: String,
    val size: Long,
    val extension: String,
    val bitRate: Int?,
    val sampleRate: Int?,
    val bitDepth: Int?,
    val queueLength: Long,
    val uploadSpeed: Int,
    val hasFreeUploadSlot: Boolean,
)

data class SlskdCandidate(
    val username: String,
    val hasFreeUploadSlot: Boolean,
    val queueLength: Long,
    val uploadSpeed: Int,
    val file: SlskdSearchFile,
)

object SlskdRanker {
    val losslessExtensions = setOf("flac", "wav", "aiff")
    val supportedExtensions = losslessExtensions + setOf("m4a", "ogg", "opus", "mp3")

    const val MAX_RESULTS = 20

    fun normalizeExtension(
        declared: String?,
        filename: String,
    ): String {
        val fromDeclared = declared?.trim()?.trimStart('.')?.lowercase().orEmpty()
        if (fromDeclared.isNotEmpty()) return fromDeclared
        return filename.substringAfterLast('.', "").lowercase()
    }

    fun rank(candidates: List<SlskdCandidate>): List<RankedSlskdFile> =
        candidates
            .map { candidate ->
                val extension = normalizeExtension(candidate.file.extension, candidate.file.filename)
                RankedSlskdFile(
                    username = candidate.username,
                    filename = candidate.file.filename,
                    size = candidate.file.size,
                    extension = extension,
                    bitRate = candidate.file.bitRate,
                    sampleRate = candidate.file.sampleRate,
                    bitDepth = candidate.file.bitDepth,
                    queueLength = candidate.queueLength,
                    uploadSpeed = candidate.uploadSpeed,
                    hasFreeUploadSlot = candidate.hasFreeUploadSlot,
                )
            }
            .filter { it.filename.isNotBlank() && it.extension in supportedExtensions }
            .sortedWith(
                compareByDescending<RankedSlskdFile> { it.extension in losslessExtensions }
                    .thenByDescending { it.bitRate ?: -1 }
                    .thenByDescending { it.sampleRate ?: -1 }
                    .thenByDescending { it.bitDepth ?: -1 }
                    .thenByDescending { it.size }
                    .thenBy { it.queueLength }
                    .thenByDescending { it.uploadSpeed }
                    .thenByDescending { it.hasFreeUploadSlot },
            )
            .take(MAX_RESULTS)

    fun candidatesOf(search: SlskdSearch): List<SlskdCandidate> =
        search.responses.flatMap { response ->
            response.files.map { file ->
                SlskdCandidate(
                    username = response.username,
                    hasFreeUploadSlot = response.hasFreeUploadSlot,
                    queueLength = response.queueLength,
                    uploadSpeed = response.uploadSpeed,
                    file = file,
                )
            }
        }
}
