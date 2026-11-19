package com.smartorchard.mobile.services

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun cameraUri(): Uri {
        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val folder = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(folder, "${UUID.randomUUID()}.image")
        try {
            val input = requireNotNull(context.contentResolver.openInputStream(uri)) {
                "Не удалось открыть фотографию."
            }
            input.use { source ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var size = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        size += count
                        require(size <= 10 * 1024 * 1024) {
                            "Фотография должна быть не больше 10 МБ."
                        }
                        output.write(buffer, 0, count)
                    }
                    require(size > 0) { "Выбран пустой файл." }
                }
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            require(
                options.outWidth > 0 &&
                    options.outHeight > 0 &&
                    options.outMimeType in listOf("image/jpeg", "image/png")
            ) {
                "Выберите корректную фотографию JPEG или PNG."
            }
            file.absolutePath
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }
}
