package com.smartorchard.mobile.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class AuthState {
    AUTHENTICATED,
    UNAUTHENTICATED
}

@Singleton
class TokenManager @Inject constructor(
    private val storage: TokenStorage
) {
    private val lock = Any()

    private val _authState = MutableStateFlow(
        if (!storage.getString(KEY_ACCESS).isNullOrBlank()) {
            AuthState.AUTHENTICATED
        } else {
            AuthState.UNAUTHENTICATED
        }
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    var accessToken: String?
        get() = synchronized(lock) { storage.getString(KEY_ACCESS) }
        set(value) = synchronized(lock) {
            storage.putString(KEY_ACCESS, value)
            _authState.value = if (!value.isNullOrBlank()) {
                AuthState.AUTHENTICATED
            } else {
                AuthState.UNAUTHENTICATED
            }
        }

    var refreshToken: String?
        get() = synchronized(lock) { storage.getString(KEY_REFRESH) }
        set(value) = synchronized(lock) {
            storage.putString(KEY_REFRESH, value)
        }

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrBlank()

    fun saveTokens(access: String, refresh: String) = synchronized(lock) {
        storage.putString(KEY_ACCESS, access)
        storage.putString(KEY_REFRESH, refresh)
        _authState.value = if (access.isNotBlank()) {
            AuthState.AUTHENTICATED
        } else {
            AuthState.UNAUTHENTICATED
        }
    }

    fun clearTokens() = synchronized(lock) {
        storage.clear()
        _authState.value = AuthState.UNAUTHENTICATED
    }

    companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
    }
}
