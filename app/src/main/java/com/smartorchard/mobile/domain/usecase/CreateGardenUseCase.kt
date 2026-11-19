package com.smartorchard.mobile.domain.usecase

import com.smartorchard.mobile.data.Crop
import com.smartorchard.mobile.data.Garden
import com.smartorchard.mobile.data.GardenDraft
import com.smartorchard.mobile.data.OrchardRepository
import com.smartorchard.mobile.domain.ValidationException
import com.smartorchard.mobile.domain.newGarden
import com.smartorchard.mobile.domain.plantingEstimate
import com.smartorchard.mobile.domain.prettyDate
import com.smartorchard.mobile.domain.validDate
import com.smartorchard.mobile.domain.validate
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class CreateGardenUseCase @Inject constructor(
    private val repository: OrchardRepository,
    private val clock: Clock
) {
    constructor(repository: OrchardRepository) : this(repository, Clock.systemDefaultZone())

    suspend operator fun invoke(draft: GardenDraft, crops: List<Crop>): Garden {
        val chosen = crops.find { it.id == draft.cropId }
            ?: throw ValidationException("Выберите культуру.")
        val estimate = plantingEstimate(draft.areaHa, chosen)
            ?: throw ValidationException("Укажите положительную площадь до четырех знаков после запятой.")
        validate(draft.name.isNotBlank()) { "Укажите название сада." }
        validate(estimate.count in 1..10000) { "Расчет должен дать от 1 до 10 000 деревьев." }
        val p = draft.places.toIntOrNull()
        validate(p != null && p in 1..minOf(100, estimate.count) && (estimate.count + p - 1) / p <= 100) {
            "Задайте до 100 мест в полном ряду и не более 100 рядов."
        }
        val maxDate = LocalDate.now(clock).toString()
        validate(validDate(draft.date) && draft.date <= maxDate) {
            "Укажите дату посадки не позже ${prettyDate(maxDate)}."
        }
        val item = newGarden(draft.name, chosen, draft.areaHa, draft.date, p!!)
        repository.addGarden(item)
        return item
    }
}
