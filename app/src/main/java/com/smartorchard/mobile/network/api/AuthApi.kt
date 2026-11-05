package com.smartorchard.mobile.network.api

import com.smartorchard.mobile.network.model.AuthResponse
import com.smartorchard.mobile.network.model.LoginRequest
import com.smartorchard.mobile.network.model.RefreshRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): AuthResponse

    @POST("api/auth/logout")
    suspend fun logout()
}
