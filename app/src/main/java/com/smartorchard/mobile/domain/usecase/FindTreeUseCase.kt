package com.smartorchard.mobile.domain.usecase

import com.smartorchard.mobile.data.Garden
import com.smartorchard.mobile.data.OrchardRepository
import com.smartorchard.mobile.data.Tree
import com.smartorchard.mobile.domain.ValidationException
import com.smartorchard.mobile.domain.parseQr
import javax.inject.Inject

class FindTreeUseCase @Inject constructor(
    private val repository: OrchardRepository
) {
    fun byCoordinates(garden: Garden, row: String, place: String): Tree =
        garden.trees.find { it.row == row.toIntOrNull() && it.place == place.toIntOrNull() }
            ?: throw ValidationException("По указанным ряду и месту дерева нет.")

    suspend fun openByQr(gardens: List<Garden>, payload: String): String {
        val id = parseQr(payload)
            ?: throw ValidationException("Некорректная метка SmartOrchard.")
        val g = gardens.find { garden -> garden.trees.any { it.id == id } }
            ?: throw ValidationException("Дерево не найдено в локальном наборе данных.")
        repository.setCurrentGarden(g.id)
        return id
    }
}
