package com.smartorchard.mobile.services

import com.smartorchard.mobile.data.AiResult
import kotlinx.coroutines.delay

interface DiagnosisService { suspend fun analyze(photoPath: String?, sample: Boolean): AiResult }

class DemoDiagnosisService : DiagnosisService {
    override suspend fun analyze(photoPath: String?, sample: Boolean): AiResult {
        require(sample || photoPath != null)
        delay(1000)
        return AiResult("Предполагаемые признаки пятнистости листьев",
            "Осмотрите листья с обеих сторон и соседние деревья. Зафиксируйте распространение пятен. Для уточнения причины обратитесь к агроному.")
    }
}
