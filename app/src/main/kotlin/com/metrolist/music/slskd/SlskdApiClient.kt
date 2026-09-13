/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.slskd

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import kotlin.coroutines.coroutineContext

data class SlskdSessionProbe(
    val reachable: Boolean,
    val securityEnabled: Boolean,
    val authValid: Boolean,
)

sealed interface SlskdFileProbe {
    data class RangeSupported(
        val url: String,
    ) : SlskdFileProbe

    data class LinearOnly(
        val url: String,
    ) : SlskdFileProbe

    data object Missing : SlskdFileProbe
}

class SlskdApiClient private constructor(
    val baseUrl: String,
    private val apiKey: String,
    private val client: HttpClient,
) {
    constructor(baseUrl: String, apiKey: String) : this(
        baseUrl = SlskdConfig.normalizeBaseUrl(baseUrl),
        apiKey = apiKey,
        client = createClient(apiKey, OkHttp.create()),
    )

    internal constructor(baseUrl: String, apiKey: String, engine: HttpClientEngine) : this(
        baseUrl = SlskdConfig.normalizeBaseUrl(baseUrl),
        apiKey = apiKey,
        client = createClient(apiKey, engine),
    )

    fun close() {
        client.close()
    }

    suspend fun probe(): SlskdSessionProbe {
        val enabledResponse =
            try {
                client.get("$baseUrl/api/v0/session/enabled")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw mapTransportError(e)
            }
        if (!enabledResponse.status.isSuccess()) {
            throw mapErrorStatus(enabledResponse, relayGuarded = false)
        }
        val securityEnabled = enabledResponse.body<Boolean>()

        val checkResponse =
            try {
                client.get("$baseUrl/api/v0/session")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw mapTransportError(e)
            }
        val authValid =
            when (checkResponse.status) {
                HttpStatusCode.OK -> true
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> false
                else -> throw mapErrorStatus(checkResponse, relayGuarded = false)
            }
        // status-only read: release the body without materializing it
        checkResponse.cancel()
        return SlskdSessionProbe(
            reachable = true,
            securityEnabled = securityEnabled,
            authValid = authValid,
        )
    }

    suspend fun startSearch(
        searchText: String,
        searchTimeout: Int = DEFAULT_SEARCH_TIMEOUT,
        responseLimit: Int = DEFAULT_RESPONSE_LIMIT,
        fileLimit: Int = DEFAULT_FILE_LIMIT,
    ): String {
        val response =
            postJson(
                path = "searches",
                body =
                    SlskdSearchRequest(
                        searchText = searchText,
                        searchTimeout = searchTimeout,
                        responseLimit = responseLimit,
                        fileLimit = fileLimit,
                    ),
                relayGuarded = true,
            ) ?: throw SlskdException.BadResponse(-1, null)
        return parseBody<SlskdSearch>(response).id
    }

    suspend fun pollSearch(id: String): SlskdSearch {
        val response =
            try {
                client.get("$baseUrl/api/v0/searches/$id") {
                    parameter("includeResponses", true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw mapTransportError(e)
            }
        if (!response.status.isSuccess()) {
            throw mapErrorStatus(response, relayGuarded = true)
        }
        return parseBody(response)
    }

    suspend fun awaitSearch(
        id: String,
        timeoutMillis: Long = SEARCH_AWAIT_TIMEOUT_MILLIS,
        pollMillis: Long = SEARCH_POLL_MILLIS,
    ): SlskdSearch {
        val started = nowMillis()
        while (true) {
            coroutineContext.ensureActive()
            val search = pollSearch(id)
            if (search.isComplete || search.endedAt != null) {
                return search
            }
            if (nowMillis() - started > timeoutMillis) {
                throw SlskdException.Timeout()
            }
            delay(pollMillis)
        }
    }

    suspend fun cancelSearch(id: String) {
        val response =
            try {
                client.put("$baseUrl/api/v0/searches/$id")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw mapTransportError(e)
            }
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.NotModified) {
            throw mapErrorStatus(response, relayGuarded = true)
        }
    }

    suspend fun deleteSearch(id: String) {
        val response =
            try {
                client.delete("$baseUrl/api/v0/searches/$id")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw mapTransportError(e)
            }
        if (!response.status.isSuccess()) {
            throw mapErrorStatus(response, relayGuarded = true)
        }
    }

    suspend fun enqueueBatch(
        username: String,
        files: List<SlskdEnqueueBatchFile>,
        searchId: String? = null,
        destination: String? = null,
    ): SlskdEnqueueBatchResult {
        var batchId = UUID.randomUUID().toString()
        repeat(MAX_ENQUEUE_ATTEMPTS) { attempt ->
            val response =
                postJson(
                    path = "transfers/downloads/batches",
                    body =
                        SlskdEnqueueBatchRequest(
                            id = batchId,
                            searchId = searchId,
                            username = username,
                            files = files,
                            options = destination?.let { SlskdEnqueueBatchOptions(destination = it) },
                        ),
                    relayGuarded = true,
                    duplicateAsNull = attempt < MAX_ENQUEUE_ATTEMPTS - 1,
                ) ?: run {
                    batchId = UUID.randomUUID().toString()
                    return@repeat
                }
            return parseBody(response)
        }
        throw SlskdException.DuplicateBatch()
    }

    suspend fun pollBatch(batchId: String): SlskdBatch {
        val response =
            try {
                client.get("$baseUrl/api/v0/transfers/downloads/batches/$batchId")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw mapTransportError(e)
            }
        if (!response.status.isSuccess()) {
            throw mapErrorStatus(response, relayGuarded = true)
        }
        return parseBody(response)
    }

    suspend fun awaitBatch(
        batchId: String,
        timeoutMillis: Long = BATCH_AWAIT_TIMEOUT_MILLIS,
        pollMillis: Long = BATCH_POLL_MILLIS,
        onProgress: suspend (SlskdBatch) -> Unit = {},
    ): SlskdBatch {
        val started = nowMillis()
        while (true) {
            coroutineContext.ensureActive()
            val batch = pollBatch(batchId)
            onProgress(batch)
            if (batch.transfers.isNotEmpty() && batch.transfers.all { it.isTerminal }) {
                return batch
            }
            if (nowMillis() - started > timeoutMillis) {
                throw SlskdException.Timeout()
            }
            delay(pollMillis)
        }
    }

    suspend fun listUserDownloads(username: String): List<SlskdTransfer> {
        val response =
            try {
                client.get("$baseUrl/api/v0/transfers/downloads/${username.encodeURLPathPart()}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw mapTransportError(e)
            }
        // a single grouped object, 404 when the user has no tracked downloads
        if (response.status == HttpStatusCode.NotFound) {
            return emptyList()
        }
        if (!response.status.isSuccess()) {
            throw mapErrorStatus(response, relayGuarded = true)
        }
        return parseBody<SlskdUserDownloads>(response).directories.flatMap { it.files }
    }

    suspend fun cancelDownload(
        username: String,
        transferId: String,
    ) {
        val response =
            try {
                client.delete("$baseUrl/api/v0/transfers/downloads/${username.encodeURLPathPart()}/$transferId")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw mapTransportError(e)
            }
        if (!response.status.isSuccess()) {
            throw mapErrorStatus(response, relayGuarded = true)
        }
    }

    suspend fun listDirectory(relativePath: String): SlskdFilesystemDirectory {
        val response =
            try {
                client.get("$baseUrl/api/v0/files/downloads/directories/${SlskdConfig.base64Url(relativePath)}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw mapTransportError(e)
            }
        if (!response.status.isSuccess()) {
            throw mapErrorStatus(response, relayGuarded = false)
        }
        return parseBody(response)
    }

    fun fileContentUrl(relativePath: String): String =
        "$baseUrl/api/v0/files/downloads/files/${SlskdConfig.base64Url(relativePath)}"

    fun streamUrl(
        username: String,
        transferId: String,
    ): String = "$baseUrl/api/v0/transfers/downloads/stream/${username.encodeURLPathPart()}/$transferId"

    suspend fun probeFileContent(relativePath: String): SlskdFileProbe = probeUrl(fileContentUrl(relativePath))

    suspend fun probeStreamUrl(url: String): SlskdFileProbe = probeUrl(url)

    private suspend fun probeUrl(url: String): SlskdFileProbe {
        val response =
            try {
                client.get(url) {
                    header(HttpHeaders.Range, "bytes=0-0")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw mapTransportError(e)
            }
        // never materialize bodies here: on 200 a range-ignoring server would make us
        // buffer the whole file, and on any status an unread body pins the connection
        if (response.status != HttpStatusCode.PartialContent) {
            response.cancel()
        }
        return when (response.status) {
            HttpStatusCode.PartialContent -> SlskdFileProbe.RangeSupported(url)
            HttpStatusCode.OK -> SlskdFileProbe.LinearOnly(url)
            HttpStatusCode.NotFound, HttpStatusCode.RequestedRangeNotSatisfiable -> SlskdFileProbe.Missing
            else -> throw mapErrorStatus(response, relayGuarded = false)
        }
    }

    private suspend fun postJson(
        path: String,
        body: Any,
        relayGuarded: Boolean,
        duplicateAsNull: Boolean = false,
    ): HttpResponse? {
        val response =
            try {
                client.post("$baseUrl/api/v0/$path") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw mapTransportError(e)
            }
        if (!response.status.isSuccess()) {
            val mapped = runCatching { mapErrorStatus(response, relayGuarded) }.getOrElse { null }
            if (duplicateAsNull && mapped is SlskdException.DuplicateBatch) {
                return null
            }
            throw mapped ?: SlskdException.BadResponse(response.status.value, null)
        }
        return response
    }

    private suspend inline fun <reified T> parseBody(response: HttpResponse): T =
        try {
            response.body<T>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw SlskdException.BadResponse(response.status.value, null)
        }

    private suspend fun mapErrorStatus(
        response: HttpResponse,
        relayGuarded: Boolean,
    ): SlskdException =
        when (response.status) {
            HttpStatusCode.Unauthorized -> SlskdException.Credential()
            HttpStatusCode.Forbidden ->
                if (relayGuarded) {
                    SlskdException.RelayAgent()
                } else {
                    SlskdException.Forbidden()
                }
            HttpStatusCode.NotFound -> SlskdException.NotFound()
            HttpStatusCode.Conflict -> SlskdException.DuplicateBatch()
            HttpStatusCode.TooManyRequests -> SlskdException.RateLimited()
            else -> {
                val code = response.status.value
                val body = runCatching { response.bodyAsText() }.getOrNull()?.take(ERROR_BODY_LIMIT)
                if (body?.contains("connected and logged in", ignoreCase = true) == true) {
                    SlskdException.SoulseekDisconnected()
                } else if (code >= 500) {
                    SlskdException.Server(code, body)
                } else {
                    SlskdException.BadResponse(code, body)
                }
            }
        }

    private fun mapTransportError(e: Exception): SlskdException =
        when (e) {
            is HttpRequestTimeoutException, is SocketTimeoutException -> SlskdException.Timeout(e)
            is IOException -> SlskdException.Network(e)
            else -> SlskdException.Network(IOException(e.message, e))
        }

    private fun nowMillis(): Long = System.currentTimeMillis()

    companion object {
        const val API_KEY_HEADER = "X-API-Key"
        // NOTE: slskd documents searchTimeout as seconds, but passes the value straight into
        // Soulseek.NET's millisecond field (verified: small values kill the search in milliseconds).
        const val DEFAULT_SEARCH_TIMEOUT = 12000
        const val DEFAULT_RESPONSE_LIMIT = 100
        const val DEFAULT_FILE_LIMIT = 1000
        const val SEARCH_POLL_MILLIS = 1500L
        const val SEARCH_AWAIT_TIMEOUT_MILLIS = 30_000L
        const val BATCH_POLL_MILLIS = 2000L
        const val BATCH_AWAIT_TIMEOUT_MILLIS = 10 * 60 * 1000L
        const val CONNECT_TIMEOUT_MILLIS = 10_000L
        const val REQUEST_TIMEOUT_MILLIS = 30_000L
        const val MAX_ENQUEUE_ATTEMPTS = 2
        const val ERROR_BODY_LIMIT = 512

        private val clientJson = Json {
            isLenient = true
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        internal fun createClient(
            apiKey: String,
            engine: HttpClientEngine,
        ): HttpClient =
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(clientJson)
                }
                install(HttpTimeout) {
                    connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                    requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                }
                defaultRequest {
                    header(API_KEY_HEADER, apiKey)
                }
                expectSuccess = false
            }
    }
}
