package com.metrolist.music.slskd

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SlskdApiClientTest {

    private fun clientWith(
        handler: io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData,
    ) = SlskdApiClient("http://test:5030", "key", MockEngine(handler))

    private fun json(content: String) =
        headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `startSearch returns the server id`() {
        val client =
            clientWith {
                assertEquals(HttpMethod.Post, it.method)
                assertTrue(it.url.encodedPath.endsWith("/api/v0/searches"))
                respond("""{"id":"search-1","isComplete":false}""", HttpStatusCode.OK, json(""))
            }

        assertEquals("search-1", runBlocking { client.startSearch("artist title") })
    }

    @Test
    fun `startSearch maps 401 to Credential`() {
        val client = clientWith { respond("nope", HttpStatusCode.Unauthorized) }

        runBlocking {
            try {
                client.startSearch("artist title")
                throw AssertionError("expected SlskdException.Credential")
            } catch (e: SlskdException.Credential) {
                // expected
            }
        }
    }

    @Test
    fun `startSearch maps 403 to RelayAgent`() {
        val client = clientWith { respond("forbidden", HttpStatusCode.Forbidden) }

        runBlocking {
            try {
                client.startSearch("artist title")
                throw AssertionError("expected SlskdException.RelayAgent")
            } catch (e: SlskdException.RelayAgent) {
                // expected
            }
        }
    }

    @Test
    fun `startSearch maps 429 to RateLimited`() {
        val client = clientWith { respond("busy", HttpStatusCode.TooManyRequests) }

        runBlocking {
            try {
                client.startSearch("artist title")
                throw AssertionError("expected SlskdException.RateLimited")
            } catch (e: SlskdException.RateLimited) {
                // expected
            }
        }
    }

    @Test
    fun `startSearch maps disconnected body to SoulseekDisconnected`() {
        val client =
            clientWith {
                respond(
                    "The server connection must be connected and logged in to perform a search",
                    HttpStatusCode.InternalServerError,
                )
            }

        runBlocking {
            try {
                client.startSearch("artist title")
                throw AssertionError("expected SlskdException.SoulseekDisconnected")
            } catch (e: SlskdException.SoulseekDisconnected) {
                // expected
            }
        }
    }

    @Test
    fun `pollSearch maps 404 to NotFound`() {
        val client = clientWith { respond("gone", HttpStatusCode.NotFound) }

        runBlocking {
            try {
                client.pollSearch("missing")
                throw AssertionError("expected SlskdException.NotFound")
            } catch (e: SlskdException.NotFound) {
                // expected
            }
        }
    }

    @Test
    fun `enqueueBatch surfaces 200 failures for attach`() {
        val client =
            clientWith {
                respond(
                    """{"batch":{"id":"fresh","transfers":[]},"failures":[{"filename":"f.flac","message":"Skipped: Already in progress"}]}""",
                    HttpStatusCode.OK,
                    json(""),
                )
            }

        val result =
            runBlocking {
                client.enqueueBatch("peer", listOf(SlskdEnqueueBatchFile("f.flac", 10)))
            }

        assertEquals("fresh", result.batch.id)
        assertEquals(1, result.failures.size)
    }

    @Test
    fun `enqueueBatch regenerates the id after a 409`() {
        var calls = 0
        val client =
            clientWith {
                calls++
                if (calls == 1) {
                    respond("conflict", HttpStatusCode.Conflict)
                } else {
                    respond("""{"batch":{"id":"second","transfers":[]},"failures":[]}""", HttpStatusCode.Created, json(""))
                }
            }

        val result =
            runBlocking {
                client.enqueueBatch("peer", listOf(SlskdEnqueueBatchFile("f.flac", 10)))
            }

        assertEquals(2, calls)
        assertEquals("second", result.batch.id)
    }

    @Test
    fun `double 409 surfaces as unexpected rather than duplicate`() {
        val client = clientWith { respond("conflict", HttpStatusCode.Conflict) }

        runBlocking {
            try {
                client.enqueueBatch("peer", listOf(SlskdEnqueueBatchFile("f.flac", 10)))
                throw AssertionError("expected SlskdException.BadResponse")
            } catch (e: SlskdException.BadResponse) {
                assertEquals(409, e.code)
            }
        }
    }

    @Test
    fun `probe 206 means range supported`() {
        val client = clientWith { respond("", HttpStatusCode.PartialContent) }

        val probe = runBlocking { client.probeStreamUrl("http://test/stream") }

        assertTrue(probe is SlskdFileProbe.RangeSupported)
    }

    @Test
    fun `probe 200 means linear only without buffering the body`() {
        val client = clientWith { respond("ignored-bytes", HttpStatusCode.OK) }

        val probe = runBlocking { client.probeStreamUrl("http://test/stream") }

        assertTrue(probe is SlskdFileProbe.LinearOnly)
    }

    @Test
    fun `probe 404 means missing`() {
        val client = clientWith { respond("gone", HttpStatusCode.NotFound) }

        val probe = runBlocking { client.probeStreamUrl("http://test/stream") }

        assertEquals(SlskdFileProbe.Missing, probe)
    }

    @Test
    fun `listUserDownloads parses the grouped object`() {
        val client =
            clientWith {
                respond(
                    """{"username":"peer","directories":[{"directory":"music","fileCount":1,"files":[{"id":"t-1","size":10}]}]}""",
                    HttpStatusCode.OK,
                    json(""),
                )
            }

        val downloads = runBlocking { client.listUserDownloads("peer") }

        assertEquals(listOf("t-1"), downloads.map { it.id })
    }

    @Test
    fun `listUserDownloads maps 404 to empty`() {
        val client = clientWith { respond("gone", HttpStatusCode.NotFound) }

        assertEquals(emptyList<SlskdTransfer>(), runBlocking { client.listUserDownloads("peer") })
    }

    @Test
    fun `transport failures map to Network`() {
        val client =
            clientWith {
                throw IOException("connection reset")
            }

        runBlocking {
            try {
                client.pollSearch("x")
                throw AssertionError("expected SlskdException.Network")
            } catch (e: SlskdException.Network) {
                // expected
            }
        }
    }
}
