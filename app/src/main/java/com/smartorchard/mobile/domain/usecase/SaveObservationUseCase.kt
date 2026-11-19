package com.smartorchard.mobile.domain.usecase

import com.smartorchard.mobile.data.Garden
import com.smartorchard.mobile.data.InspectionDraft
import com.smartorchard.mobile.data.Observation
import com.smartorchard.mobile.data.OrchardRepository
import com.smartorchard.mobile.domain.ValidationException
import com.smartorchard.mobile.domain.uid
import com.smartorchard.mobile.domain.validate
import java.time.Clock
import java.time.OffsetDateTime
import javax.inject.Inject

class SaveObservationUseCase @Inject constructor(
    private val repository: OrchardRepository,
    private val clock: Clock
) {
    constructor(repository: OrchardRepository) : this(repository, Clock.systemDefaultZone())

    suspend operator fun invoke(
        draft: InspectionDraft,
        garden: Garden,
        withAi: Boolean
    ): Observation {
        val originalTree = garden.trees.find { it.id == draft.treeId }
            ?: throw ValidationException("Дерево недоступно.")
        if (withAi) {
            validate(draft.originalAi != null && draft.diagnosis.isNotBlank() && draft.recommendations.isNotBlank()) {
                "Заполните предполагаемый диагноз и рекомендации."
            }
        }
        if (!withAi) {
            validate(
                draft.comment.isNotBlank() ||
                    draft.photoPath != null ||
                    draft.samplePhoto ||
                    draft.status != draft.previousStatus
            ) {
                "Добавьте фото, наблюдение или измените состояние."
            }
        }
        val record = Observation(
            id = uid(),
            gardenId = garden.id,
            treeId = draft.treeId,
            createdAt = OffsetDateTime.now(clock).toString(),
            status = draft.status,
            comment = draft.comment.trim(),
            diagnosis = if (withAi) draft.diagnosis.trim() else "",
            recommendations = if (withAi) draft.recommendations.trim() else "",
            photoPath = draft.photoPath,
            samplePhoto = draft.samplePhoto,
            originalAi = if (withAi) draft.originalAi else null
        )
        repository.addObservation(record, garden.id, originalTree.id, draft.status)
        return record
    }
}
