package com.smartorchard.mobile.services

import com.smartorchard.mobile.data.AiResult
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

interface DiagnosisService {
    suspend fun analyze(photoPath: String?, sample: Boolean): AiResult
}

@Singleton
class DemoDiagnosisService @Inject constructor() : DiagnosisService {
    override suspend fun analyze(photoPath: String?, sample: Boolean): AiResult {
        require(sample || photoPath != null)
        delay(1000.milliseconds)
        return AiResult(
            diagnosis = "Предполагаемые признаки пятнистости листьев",
            recommendations = "Осмотрите листья с обеих сторон и соседние деревья. Зафиксируйте распространение пятен. Для уточнения причины обратитесь к агроному."
        )
    }
}
