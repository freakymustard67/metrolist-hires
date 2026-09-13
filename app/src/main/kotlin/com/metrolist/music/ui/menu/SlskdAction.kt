/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.menu

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.constants.SlskdApiKeyKey
import com.metrolist.music.constants.SlskdBaseUrlKey
import com.metrolist.music.constants.SlskdEnabledKey
import com.metrolist.music.playback.MusicService
import com.metrolist.music.slskd.RankedSlskdFile
import com.metrolist.music.slskd.SlskdApiClient
import com.metrolist.music.slskd.SlskdConfig
import com.metrolist.music.slskd.SlskdEnqueueBatchFile
import com.metrolist.music.slskd.SlskdErrorMapper
import com.metrolist.music.slskd.SlskdException
import com.metrolist.music.slskd.SlskdFileProbe
import com.metrolist.music.slskd.SlskdOverrideStore
import com.metrolist.music.slskd.SlskdRanker
import com.metrolist.music.slskd.SlskdTransfer
import com.metrolist.music.ui.component.Material3MenuItemData
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.reportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class SlskdProgress(
    val percent: Int?,
    val queuePosition: Int?,
    val totalBytes: Long,
    val bufferedBytes: Long,
    val thresholdBytes: Long,
) {
    val isBuffering: Boolean get() = bufferedBytes < thresholdBytes
}

sealed interface SlskdOutcome {
    data class Playing(
        val seekLimited: Boolean,
    ) : SlskdOutcome

    data object DownloadedOnly : SlskdOutcome
}

private sealed interface SlskdUiState {
    data object Searching : SlskdUiState

    data class Picking(
        val files: List<RankedSlskdFile>,
    ) : SlskdUiState

    data class Downloading(
        val progress: SlskdProgress?,
    ) : SlskdUiState

    data class Failed(
        val messageRes: Int,
    ) : SlskdUiState

    data class Notice(
        val messageRes: Int,
        val seekLimited: Boolean,
    ) : SlskdUiState
}

class SlskdSession(
    private val context: Context,
    private val service: MusicService,
) {
    private var client: SlskdApiClient? = null
    private var searchId: String? = null

    private suspend fun requireClient(): SlskdApiClient {
        client?.let { return it }
        val enabled = context.dataStore.get(SlskdEnabledKey, false)
        val baseUrl = context.dataStore.get(SlskdBaseUrlKey, "")
        val apiKey = context.dataStore.get(SlskdApiKeyKey, "")
        if (!enabled || !SlskdConfig.isConfigured(baseUrl, apiKey)) {
            throw SlskdException.Config("slskd is not configured")
        }
        return SlskdApiClient(baseUrl, apiKey).also { client = it }
    }

    suspend fun search(query: String): List<RankedSlskdFile> {
        val active = requireClient()
        val id = active.startSearch(query)
        searchId = id
        try {
            return SlskdRanker.rank(SlskdRanker.candidatesOf(active.awaitSearch(id)))
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                runCatching { active.cancelSearch(id) }
            }
            throw e
        }
    }

    suspend fun downloadAndPlay(
        mediaId: String,
        file: RankedSlskdFile,
        onProgress: suspend (SlskdProgress?) -> Unit,
    ): SlskdOutcome {
        val active = requireClient()
        val destination = "metrolist/$mediaId"
        val enqueued =
            active.enqueueBatch(
                username = file.username,
                files = listOf(SlskdEnqueueBatchFile(file.filename, file.size)),
                searchId = searchId,
                destination = destination,
            )
        var tracked =
            enqueued.batch.transfers.find { it.filename == file.filename }
                ?: active.listUserDownloads(file.username).find { it.filename == file.filename && !it.isTerminal }
                ?: throw SlskdException.TransferFailed(
                    enqueued.failures.firstOrNull()?.message,
                )
        val batchId = enqueued.batch.id
        var final: SlskdTransfer? = null
        var streamAttempted = false
        try {
            val started = System.currentTimeMillis()
            while (true) {
                coroutineContext.ensureActive()
                val batch = active.pollBatch(batchId)
                val current =
                    batch.transfers.find { it.id == tracked.id }
                        ?: batch.transfers.find { it.filename == file.filename && !it.isTerminal }
                        ?: active.listUserDownloads(file.username).find { it.filename == file.filename && !it.isTerminal }
                if (current != null) {
                    tracked = current
                    onProgress(progressOf(current))
                    if (current.isTerminal) {
                        final = current
                        break
                    }
                    if (!streamAttempted && current.size > 0
                        && current.bytesTransferred >= SlskdConfig.streamStartBytes(current.size)
                    ) {
                        streamAttempted = true
                        try {
                            tryStartStream(active, mediaId, current)?.let { return it }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // fall through to download-then-play (old servers, probe blips)
                        }
                    }
                }
                if (System.currentTimeMillis() - started > SlskdApiClient.BATCH_AWAIT_TIMEOUT_MILLIS) {
                    throw SlskdException.Timeout()
                }
                delay(SlskdApiClient.BATCH_POLL_MILLIS)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                runCatching { active.cancelDownload(file.username, tracked.id) }
            }
            throw e
        }
        val completed = final ?: throw SlskdException.TransferFailed(null)
        if (!completed.isSuccessful) {
            throw SlskdException.TransferFailed(completed.exception)
        }
        val directory = active.listDirectory(destination)
        val local =
            when {
                directory.files.size == 1 -> directory.files.first()
                else -> directory.files.find { it.name == remoteTail(file.filename) }
            } ?: return SlskdOutcome.DownloadedOnly
        return when (val probe = active.probeFileContent("$destination/${local.name}")) {
            is SlskdFileProbe.RangeSupported -> {
                service.setSlskdOverride(mediaId, probe.url, completed.id)
                SlskdOutcome.Playing(seekLimited = false)
            }
            is SlskdFileProbe.LinearOnly -> {
                service.setSlskdOverride(mediaId, probe.url, completed.id)
                SlskdOutcome.Playing(seekLimited = true)
            }
            SlskdFileProbe.Missing -> SlskdOutcome.DownloadedOnly
        }
    }

    suspend fun finish() {
        val id = searchId
        searchId = null
        if (id != null) {
            runCatching { client?.deleteSearch(id) }
        }
        runCatching { client?.close() }
        client = null
    }

    private suspend fun tryStartStream(
        active: SlskdApiClient,
        mediaId: String,
        transfer: SlskdTransfer,
    ): SlskdOutcome.Playing? {
        val url = active.streamUrl(transfer.username.orEmpty(), transfer.id)
        return when (val probe = active.probeStreamUrl(url)) {
            is SlskdFileProbe.RangeSupported -> {
                service.setSlskdOverride(mediaId, probe.url, transfer.id)
                SlskdOutcome.Playing(seekLimited = false)
            }
            is SlskdFileProbe.LinearOnly -> {
                service.setSlskdOverride(mediaId, probe.url, transfer.id)
                SlskdOutcome.Playing(seekLimited = true)
            }
            SlskdFileProbe.Missing -> null
        }
    }

    private fun progressOf(transfer: SlskdTransfer): SlskdProgress {
        val percent =
            if (transfer.size > 0) {
                ((transfer.bytesTransferred * 100) / transfer.size).toInt().coerceIn(0, 100)
            } else {
                null
            }
        return SlskdProgress(
            percent = percent,
            queuePosition = transfer.placeInQueue,
            totalBytes = transfer.size,
            bufferedBytes = transfer.bytesTransferred,
            thresholdBytes = SlskdConfig.streamStartBytes(transfer.size),
        )
    }

    companion object {
        fun remoteTail(filename: String): String =
            filename.substringAfterLast('/').substringAfterLast('\\')
    }
}

@Composable
fun rememberSlskdMenuItems(
    mediaId: String,
    artistName: String,
    title: String,
    service: MusicService,
    onMenuDismiss: () -> Unit,
): List<Material3MenuItemData> {
    val enabled by rememberPreference(SlskdEnabledKey, false)
    val baseUrl by rememberPreference(SlskdBaseUrlKey, "")
    val apiKey by rememberPreference(SlskdApiKeyKey, "")
    var showDialog by rememberSaveable { mutableStateOf(false) }

    val items = mutableListOf<Material3MenuItemData>()
    if (enabled && SlskdConfig.isConfigured(baseUrl, apiKey)) {
        items +=
            Material3MenuItemData(
                icon = { Icon(painterResource(R.drawable.cloud), null) },
                title = { Text(stringResource(R.string.slskd_play_high_res)) },
                onClick = { showDialog = true },
            )
    }
    if (SlskdOverrideStore.contains(mediaId)) {
        items +=
            Material3MenuItemData(
                icon = { Icon(painterResource(R.drawable.refresh), null) },
                title = { Text(stringResource(R.string.slskd_revert_to_youtube)) },
                onClick = {
                    service.clearSlskdOverride(mediaId)
                    onMenuDismiss()
                },
            )
    }
    if (showDialog) {
        SlskdDialog(
            mediaId = mediaId,
            query = "$artistName $title".trim().ifEmpty { title },
            service = service,
            onPlaying = onMenuDismiss,
            onDismiss = { showDialog = false },
        )
    }
    return items
}

@Composable
private fun SlskdDialog(
    mediaId: String,
    query: String,
    service: MusicService,
    onPlaying: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf<SlskdUiState>(SlskdUiState.Searching) }
    var lastResults by remember { mutableStateOf(emptyList<RankedSlskdFile>()) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val session = remember { SlskdSession(context.applicationContext, service) }

    suspend fun dispose() {
        downloadJob?.cancelAndJoin()
        downloadJob = null
        withContext(NonCancellable) {
            runCatching { session.finish() }
        }
    }

    fun close() {
        scope.launch {
            runCatching { dispose() }
            onDismiss()
        }
    }

    fun pick(file: RankedSlskdFile) {
        downloadJob?.cancel()
        downloadJob =
            scope.launch {
                uiState = SlskdUiState.Downloading(null)
                try {
                    val outcome =
                        withContext(Dispatchers.IO) {
                            session.downloadAndPlay(mediaId, file) { progress ->
                                withContext(Dispatchers.Main.immediate) {
                                    uiState = SlskdUiState.Downloading(progress)
                                }
                            }
                        }
                    when (outcome) {
                        is SlskdOutcome.Playing -> {
                            runCatching { session.finish() }
                            if (outcome.seekLimited) {
                                uiState = SlskdUiState.Notice(R.string.slskd_seek_limited, seekLimited = true)
                            } else {
                                onPlaying()
                            }
                        }
                        SlskdOutcome.DownloadedOnly -> {
                            uiState = SlskdUiState.Notice(R.string.slskd_downloaded_no_playback, seekLimited = false)
                        }
                    }
                } catch (e: CancellationException) {
                    // dialog dismissed; cleanup runs in close()
                } catch (e: Exception) {
                    if (e !is SlskdException) reportException(e)
                    uiState = SlskdUiState.Failed(SlskdErrorMapper.messageRes(e))
                }
            }
    }

    LaunchedEffect(mediaId, query) {
        try {
            val files = withContext(Dispatchers.IO) { session.search(query) }
            lastResults = files
            uiState =
                if (files.isEmpty()) {
                    SlskdUiState.Failed(R.string.slskd_no_results)
                } else {
                    SlskdUiState.Picking(files)
                }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                runCatching { session.finish() }
            }
        } catch (e: Exception) {
            if (e !is SlskdException) reportException(e)
            uiState = SlskdUiState.Failed(SlskdErrorMapper.messageRes(e))
        }
    }

    AlertDialog(
        onDismissRequest = { close() },
        title = {
            Text(
                when (val state = uiState) {
                    SlskdUiState.Searching -> stringResource(R.string.slskd_searching)
                    is SlskdUiState.Picking -> stringResource(R.string.slskd_choose_file)
                    is SlskdUiState.Downloading ->
                        if (state.progress?.isBuffering != false) {
                            stringResource(R.string.slskd_buffering)
                        } else {
                            stringResource(R.string.slskd_downloading)
                        }
                    is SlskdUiState.Failed, is SlskdUiState.Notice -> stringResource(R.string.slskd_integration)
                },
            )
        },
        text = {
            when (val state = uiState) {
                SlskdUiState.Searching -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Text(
                            stringResource(R.string.slskd_search_disclosure),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                is SlskdUiState.Picking -> {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(state.files, key = { it.username + "|" + it.filename }) {
                            ListItem(
                                headlineContent = { Text(SlskdSession.remoteTail(it.filename)) },
                                supportingContent = {
                                    Column {
                                        Text(slskdSpecLine(it))
                                        Text(slskdPeerLine(it))
                                    }
                                },
                                modifier = Modifier.clickable { pick(it) },
                            )
                        }
                    }
                }
                is SlskdUiState.Downloading -> {
                    val progress = state.progress
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (progress?.percent != null) {
                            LinearProgressIndicator(
                                progress = { progress.percent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(stringResource(R.string.slskd_progress_percent, progress.percent, formatBytes(progress.totalBytes)))
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        if (progress?.queuePosition != null && progress.queuePosition > 0) {
                            Text(stringResource(R.string.slskd_queue_position, progress.queuePosition))
                        }
                    }
                }
                is SlskdUiState.Failed -> {
                    Text(stringResource(state.messageRes))
                }
                is SlskdUiState.Notice -> {
                    Text(stringResource(state.messageRes))
                }
            }
        },
        confirmButton = {
            when (val state = uiState) {
                is SlskdUiState.Picking -> {
                    TextButton(onClick = { close() }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
                is SlskdUiState.Downloading -> {
                    TextButton(onClick = { close() }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
                is SlskdUiState.Failed -> {
                    if (lastResults.isNotEmpty()) {
                        TextButton(onClick = { uiState = SlskdUiState.Picking(lastResults) }) {
                            Text(stringResource(R.string.back))
                        }
                    }
                    TextButton(onClick = { close() }) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
                is SlskdUiState.Notice -> {
                    TextButton(
                        onClick = {
                            if (state.seekLimited) {
                                onPlaying()
                            } else {
                                close()
                            }
                        },
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
                SlskdUiState.Searching -> {}
            }
        },
    )
}

private fun slskdSpecLine(file: RankedSlskdFile): String {
    val parts = mutableListOf(file.extension.uppercase())
    file.bitDepth?.let { parts += "$it-bit" }
    file.sampleRate?.let { parts += "%.1f kHz".format(it / 1000.0) }
    file.bitRate?.let { parts += "$it kbps" }
    parts += formatBytes(file.size)
    return parts.joinToString(" · ")
}

private fun slskdPeerLine(file: RankedSlskdFile): String {
    val parts = mutableListOf(file.username.ifBlank { "?" })
    if (file.hasFreeUploadSlot) {
        parts += "free slot"
    }
    if (file.queueLength > 0) {
        parts += "queue ${file.queueLength}"
    }
    parts += "${formatBytes(file.uploadSpeed.toLong())}/s"
    return parts.joinToString(" · ")
}

private fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
