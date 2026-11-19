package com.smartorchard.mobile.domain

import android.database.sqlite.SQLiteException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ValidationException(override val message: String) : IllegalArgumentException(message)

fun validate(condition: Boolean, lazyMessage: () -> String) {
    if (!condition) throw ValidationException(lazyMessage())
}

sealed class AppError(val userMessage: String) {
    class Validation(message: String) : AppError(message)
    class NetworkOffline(message: String = "Нет подключения к интернету. Проверьте сеть и повторите попытку.") : AppError(message)
    class Timeout(message: String = "Превышено время ожидания ответа от сервера.") : AppError(message)
    class Unauthorized(message: String = "Сессия истекла. Войдите в аккаунт повторно.") : AppError(message)
    class Server(val code: Int, message: String = "Ошибка сервера ($code). Попробуйте позже.") : AppError(message)
    class Storage(message: String = "Ошибка локальной базы данных на устройстве.") : AppError(message)
    class Unknown(message: String = "Не удалось выполнить операцию.") : AppError(message)
}

fun Throwable.toAppError(defaultMessage: String = "Не удалось сохранить данные."): AppError = when (this) {
    is ValidationException -> AppError.Validation(message)
    is UnknownHostException, is ConnectException -> AppError.NetworkOffline()
    is SocketTimeoutException -> AppError.Timeout()
    is HttpException -> when (code()) {
        401, 403 -> AppError.Unauthorized()
        else -> AppError.Server(code())
    }
    is SQLiteException -> AppError.Storage()
    is IOException -> AppError.NetworkOffline("Ошибка ввода-вывода или отсутствует сеть.")
    is IllegalArgumentException -> AppError.Validation(message ?: defaultMessage)
    else -> AppError.Unknown(message?.takeIf { it.isNotBlank() && !it.contains("Exception") } ?: defaultMessage)
}

fun Throwable.toUserMessage(defaultMessage: String = "Не удалось сохранить данные."): String =
    toAppError(defaultMessage).userMessage
