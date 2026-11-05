package com.smartorchard.mobile.network.interceptor

import com.smartorchard.mobile.network.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // Не добавляем токен к auth-эндпоинтам
        if (original.url.encodedPath.contains("/api/auth/")) {
            return chain.proceed(original)
        }

        val token = tokenManager.accessToken
        if (token.isNullOrBlank()) {
            return chain.proceed(original)
        }

        val request = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(request)
    }
}
