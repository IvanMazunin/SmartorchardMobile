package com.smartorchard.mobile.domain.usecase

import com.smartorchard.mobile.data.IrrigationSchedule
import com.smartorchard.mobile.data.IrrigationZone
import com.smartorchard.mobile.data.OrchardRepository
import com.smartorchard.mobile.domain.overlaps
import com.smartorchard.mobile.domain.uid
import com.smartorchard.mobile.domain.validDateTime
import com.smartorchard.mobile.domain.validate
import javax.inject.Inject

class SaveScheduleUseCase @Inject constructor(
    private val repository: OrchardRepository
) {
    suspend operator fun invoke(
        gardenId: String,
        zoneId: String,
        start: String,
        end: String,
        gardenZones: List<IrrigationZone>,
        gardenSchedules: List<IrrigationSchedule>
    ): IrrigationSchedule {
        validate(gardenZones.any { it.id == zoneId }) { "Выберите зону полива." }
        validate(validDateTime(start) && validDateTime(end) && start < end) {
            "Окончание должно быть позже начала."
        }
        validate(gardenSchedules.none { it.zoneId == zoneId && overlaps(start, end, it) }) {
            "В этой зоне уже есть пересекающийся интервал."
        }
        val item = IrrigationSchedule(
            id = uid(),
            gardenId = gardenId,
            zoneId = zoneId,
            start = start,
            end = end
        )
        repository.addSchedule(item)
        return item
    }
}
