package com.smartorchard.mobile.domain.usecase

import com.smartorchard.mobile.data.AiResult
import com.smartorchard.mobile.data.InspectionDraft
import com.smartorchard.mobile.domain.validate
import com.smartorchard.mobile.services.DiagnosisService
import javax.inject.Inject

class AnalyzePhotoUseCase @Inject constructor(
    private val diagnosisService: DiagnosisService
) {
    suspend operator fun invoke(draft: InspectionDraft, isOffline: Boolean): AiResult {
        validate(draft.photoPath != null || draft.samplePhoto) {
            "Добавьте фотографию перед анализом."
        }
        validate(!isOffline) {
            "Для анализа требуется подключение. В макете включен режим без сети."
        }
        return diagnosisService.analyze(draft.photoPath, draft.samplePhoto)
    }
}
