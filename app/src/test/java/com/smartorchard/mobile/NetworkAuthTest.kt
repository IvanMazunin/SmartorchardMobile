package com.smartorchard.mobile

import com.smartorchard.mobile.network.AuthState
import com.smartorchard.mobile.network.TokenManager
import com.smartorchard.mobile.network.TokenStorage
import com.smartorchard.mobile.network.interceptor.AuthInterceptor
import com.smartorchard.mobile.network.interceptor.TokenAuthenticator
import com.smartorchard.mobile.network.model.AuthResponse
import com.smartorchard.mobile.network.model.RefreshRequest
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class NetworkAuthTest {

    private val baseUrl = "https://api.smartorchard.example.com/"
    private val jsonMediaType = "application/json".toMediaType()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private class FakeTokenStorage(
        initial: Map<String, String> = emptyMap()
    ) : TokenStorage {
        private val data = LinkedHashMap<String, String>(initial)

        @Synchronized
        override fun getString(key: String): String? = data[key]

        @Synchronized
        override fun putString(key: String, value: String?) {
            if (value == null) data.remove(key) else data[key] = value
        }

        @Synchronized
        override fun clear() {
            data.clear()
        }

        @Synchronized
        fun isEmpty(): Boolean = data.isEmpty()
    }

    private fun buildInMemoryResponse(
        request: Request,
        code: Int = 200,
        bodyJson: String = "{}",
        priorResponse: Response? = null
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(if (code in 200..299) "OK" else "Error")
        .apply {
            if (priorResponse != null) {
                priorResponse(priorResponse)
            }
        }
        .body(bodyJson.toResponseBody(jsonMediaType))
        .build()

    // --- TokenManager ---

    @Test
    fun tokenManager_startsUnauthenticatedWhenStorageIsEmpty() {
        val storage = FakeTokenStorage()
        val manager = TokenManager(storage)

        assertEquals(AuthState.UNAUTHENTICATED, manager.authState.value)
        assertFalse(manager.isLoggedIn)
        assertNull(manager.accessToken)
        assertNull(manager.refreshToken)
    }

    @Test
    fun tokenManager_saveTokensAndClearTokensTransitionAuthStateCorrectly() {
        val storage = FakeTokenStorage()
        val manager = TokenManager(storage)

        manager.saveTokens("acc-1", "ref-1")

        assertEquals(AuthState.AUTHENTICATED, manager.authState.value)
        assertTrue(manager.isLoggedIn)
        assertEquals("acc-1", manager.accessToken)
        assertEquals("ref-1", manager.refreshToken)
        assertEquals("acc-1", storage.getString(TokenManager.KEY_ACCESS))
        assertEquals("ref-1", storage.getString(TokenManager.KEY_REFRESH))

        manager.clearTokens()

        assertEquals(AuthState.UNAUTHENTICATED, manager.authState.value)
        assertFalse(manager.isLoggedIn)
        assertNull(manager.accessToken)
        assertNull(manager.refreshToken)
        assertTrue(storage.isEmpty())
    }

    // --- AuthInterceptor ---

    @Test
    fun authInterceptor_addsBearerHeaderToApiEndpointsAndSkipsAuthEndpointsOrMissingToken() {
        val storage = FakeTokenStorage()
        val manager = TokenManager(storage)
        val capturedRequests = mutableListOf<Request>()

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(manager))
            .addInterceptor(Interceptor { chain ->
                capturedRequests += chain.request()
                buildInMemoryResponse(chain.request(), code = 200)
            })
            .build()

        // 1. When accessToken is null -> no Authorization header
        client.newCall(Request.Builder().url("${baseUrl}api/gardens").build()).execute().close()
        assertNull(capturedRequests.last().header("Authorization"))

        // 2. When logged in -> adds Authorization: Bearer acc-1 to /api/gardens
        manager.saveTokens("acc-1", "ref-1")
        client.newCall(Request.Builder().url("${baseUrl}api/gardens").build()).execute().close()
        assertEquals("Bearer acc-1", capturedRequests.last().header("Authorization"))

        // 3. Does NOT add Authorization header to /api/auth/login or /api/auth/refresh
        client.newCall(Request.Builder().url("${baseUrl}api/auth/login").build()).execute().close()
        assertNull(capturedRequests.last().header("Authorization"))

        client.newCall(Request.Builder().url("${baseUrl}api/auth/refresh").build()).execute().close()
        assertNull(capturedRequests.last().header("Authorization"))
    }

    // --- TokenAuthenticator ---

    @Test
    fun tokenAuthenticator_refreshesTokenOn401UpdatesManagerAndRetriesWithNewBearer() {
        val storage = FakeTokenStorage()
        val manager = TokenManager(storage)
        manager.saveTokens("acc-1", "ref-1")

        var refreshCallCount = 0
        var capturedRefreshBody: RefreshRequest? = null

        val refreshClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                refreshCallCount++
                val req = chain.request()
                assertEquals("/api/auth/refresh", req.url.encodedPath)
                assertEquals("POST", req.method)
                val buf = Buffer()
                req.body?.writeTo(buf)
                capturedRefreshBody = json.decodeFromString<RefreshRequest>(buf.readUtf8())

                val authJson = json.encodeToString(
                    AuthResponse(accessToken = "acc-2", refreshToken = "ref-2", expiresIn = 3600)
                )
                buildInMemoryResponse(req, code = 200, bodyJson = authJson)
            })
            .build()

        val authenticator = TokenAuthenticator(manager, baseUrl, json, refreshClient)

        val failedRequest = Request.Builder()
            .url("${baseUrl}api/gardens")
            .header("Authorization", "Bearer acc-1")
            .build()
        val unauthorizedResponse = buildInMemoryResponse(failedRequest, code = 401)

        val retriedRequest = authenticator.authenticate(null, unauthorizedResponse)

        assertNotNull(retriedRequest)
        assertEquals("Bearer acc-2", retriedRequest!!.header("Authorization"))
        assertEquals(1, refreshCallCount)
        assertEquals(RefreshRequest("ref-1"), capturedRefreshBody)
        assertEquals("acc-2", manager.accessToken)
        assertEquals("ref-2", manager.refreshToken)
        assertEquals(AuthState.AUTHENTICATED, manager.authState.value)
    }

    @Test
    fun tokenAuthenticator_reusesAlreadyUpdatedTokenWithoutCallingRefreshAgain() {
        val storage = FakeTokenStorage()
        val manager = TokenManager(storage)
        // Another thread already refreshed tokens to acc-new / ref-new
        manager.saveTokens("acc-new", "ref-new")

        var refreshCallCount = 0
        val refreshClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                refreshCallCount++
                buildInMemoryResponse(chain.request(), code = 500)
            })
            .build()

        val authenticator = TokenAuthenticator(manager, baseUrl, json, refreshClient)

        // Failed request still carried stale acc-old
        val staleRequest = Request.Builder()
            .url("${baseUrl}api/gardens")
            .header("Authorization", "Bearer acc-old")
            .build()
        val unauthorizedResponse = buildInMemoryResponse(staleRequest, code = 401)

        val retriedRequest = authenticator.authenticate(null, unauthorizedResponse)

        assertNotNull(retriedRequest)
        assertEquals("Bearer acc-new", retriedRequest!!.header("Authorization"))
        assertEquals(0, refreshCallCount)
        assertEquals("acc-new", manager.accessToken)
        assertEquals(AuthState.AUTHENTICATED, manager.authState.value)
    }

    @Test
    fun tokenAuthenticator_clearsTokensAndReturnsNullWhenRefreshReturns401Or400() {
        for (errorCode in listOf(401, 400)) {
            val storage = FakeTokenStorage()
            val manager = TokenManager(storage)
            manager.saveTokens("acc-1", "ref-1")

            var refreshCallCount = 0
            val refreshClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    refreshCallCount++
                    buildInMemoryResponse(chain.request(), code = errorCode, bodyJson = """{"message":"invalid_grant"}""")
                })
                .build()

            val authenticator = TokenAuthenticator(manager, baseUrl, json, refreshClient)
            val failedRequest = Request.Builder()
                .url("${baseUrl}api/gardens")
                .header("Authorization", "Bearer acc-1")
                .build()
            val unauthorizedResponse = buildInMemoryResponse(failedRequest, code = 401)

            val retried = authenticator.authenticate(null, unauthorizedResponse)

            assertNull("Expected null retry request for refresh HTTP $errorCode", retried)
            assertEquals(1, refreshCallCount)
            assertEquals(AuthState.UNAUTHENTICATED, manager.authState.value)
            assertFalse(manager.isLoggedIn)
            assertNull(manager.accessToken)
            assertNull(manager.refreshToken)
        }
    }

    @Test
    fun tokenAuthenticator_abortsAndClearsTokensWhenPriorResponseChainDepthReachesTwo() {
        val storage = FakeTokenStorage()
        val manager = TokenManager(storage)
        manager.saveTokens("acc-2", "ref-2")

        var refreshCallCount = 0
        val refreshClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                refreshCallCount++
                buildInMemoryResponse(chain.request(), code = 200)
            })
            .build()

        val authenticator = TokenAuthenticator(manager, baseUrl, json, refreshClient)

        val initialRequest = Request.Builder()
            .url("${baseUrl}api/gardens")
            .header("Authorization", "Bearer acc-1")
            .build()
        // OkHttp priorResponse must have null body
        val priorResponse = Response.Builder()
            .request(initialRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()

        val secondAttemptRequest = initialRequest.newBuilder()
            .header("Authorization", "Bearer acc-2")
            .build()
        val secondUnauthorizedResponse = buildInMemoryResponse(
            request = secondAttemptRequest,
            code = 401,
            priorResponse = priorResponse
        )

        val retried = authenticator.authenticate(null, secondUnauthorizedResponse)

        assertNull(retried)
        assertEquals(0, refreshCallCount)
        assertEquals(AuthState.UNAUTHENTICATED, manager.authState.value)
        assertNull(manager.accessToken)
        assertNull(manager.refreshToken)
    }
}
