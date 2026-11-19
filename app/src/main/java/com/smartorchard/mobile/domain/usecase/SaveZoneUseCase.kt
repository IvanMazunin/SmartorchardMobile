package com.smartorchard.mobile.domain.usecase

import com.smartorchard.mobile.data.Garden
import com.smartorchard.mobile.data.IrrigationZone
import com.smartorchard.mobile.data.OrchardRepository
import com.smartorchard.mobile.data.ZoneDraft
import com.smartorchard.mobile.domain.normalized
import com.smartorchard.mobile.domain.uid
import com.smartorchard.mobile.domain.validate
import javax.inject.Inject

class SaveZoneUseCase @Inject constructor(
    private val repository: OrchardRepository
) {
    suspend operator fun invoke(
        draft: ZoneDraft,
        garden: Garden,
        gardenZones: List<IrrigationZone>
    ): IrrigationZone {
        validate(draft.name.isNotBlank()) { "Укажите название зоны." }
        validate(draft.treeIds.isNotEmpty()) { "Выберите хотя бы одно дерево." }
        validate(gardenZones.none { it.id != draft.id && normalized(it.name) == normalized(draft.name) }) {
            "Зона с таким названием уже есть."
        }
        validate(
            draft.treeIds.none { id ->
                garden.trees.none { it.id == id } ||
                    gardenZones.any { it.id != draft.id && id in it.treeIds }
            }
        ) {
            "Некоторые деревья недоступны или заняты другой зоной."
        }
        val item = IrrigationZone(
            id = draft.id ?: uid(),
            gardenId = garden.id,
            name = draft.name.trim(),
            color = draft.color,
            treeIds = draft.treeIds
        )
        repository.saveZone(item)
        return item
    }
}
