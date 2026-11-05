package com.smartorchard.mobile.network.interceptor

import com.smartorchard.mobile.network.TokenManager
import com.smartorchard.mobile.network.model.AuthResponse
import com.smartorchard.mobile.network.model.RefreshRequest
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Authenticator: при получении 401 обновляет access-токен
 * через refresh-токен и повторяет исходный запрос.
 * Работает синхронно на OkHttp-потоке — не использует suspend-функции.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager
) : Authenticator {

    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    var baseUrl: String = ""  // инжектируется из NetworkModule

    override fun authenticate(route: Route?, response: Response): Request? {
        // Защита от бесконечного цикла
        if (responseCount(response) >= 2) {
            tokenManager.clearTokens()
            return null
        }

        synchronized(lock) {
            // Если другой поток уже обновил токен — повторяем запрос с новым
            val currentToken = tokenManager.accessToken
            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            if (currentToken != null && currentToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshToken = tokenManager.refreshToken ?: run {
                tokenManager.clearTokens()
                return null
            }

            // Синхронный запрос на обновление токена (отдельный OkHttpClient без авторизации)
            return try {
                val client = OkHttpClient()
                val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
                    .toRequestBody("application/json".toMediaType())
                val refreshRequest = Request.Builder()
                    .url("${baseUrl}api/auth/refresh")
                    .post(body)
                    .build()

                val refreshResponse = client.newCall(refreshRequest).execute()
                if (refreshResponse.isSuccessful) {
                    val responseBody = refreshResponse.body?.string() ?: throw Exception("Empty response")
                    val auth = json.decodeFromString(AuthResponse.serializer(), responseBody)
                    tokenManager.accessToken = auth.accessToken
                    tokenManager.refreshToken = auth.refreshToken

                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${auth.accessToken}")
                        .build()
                } else {
                    tokenManager.clearTokens()
                    null
                }
            } catch (e: Exception) {
                tokenManager.clearTokens()
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
