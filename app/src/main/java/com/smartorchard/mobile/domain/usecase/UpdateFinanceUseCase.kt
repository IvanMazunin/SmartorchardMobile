package com.smartorchard.mobile.domain.usecase

import com.smartorchard.mobile.data.Finance
import com.smartorchard.mobile.data.OrchardRepository
import com.smartorchard.mobile.domain.decimal
import com.smartorchard.mobile.domain.validate
import javax.inject.Inject

class UpdateFinanceUseCase @Inject constructor(
    private val repository: OrchardRepository
) {
    suspend operator fun invoke(
        gardenId: String,
        revenue: String,
        annualRevenue: String,
        annualCosts: String
    ): Finance {
        val values = listOf(revenue, annualRevenue, annualCosts).map { raw ->
            decimal(raw, 2)
                ?.movePointRight(2)
                ?.let { runCatching { it.longValueExact() }.getOrNull() }
                ?.takeIf { it in 0..100_000_000_000_000L }
        }
        validate(values.all { it != null }) {
            "Введите неотрицательные суммы до двух знаков после запятой."
        }
        val finance = Finance(
            revenueCents = values[0]!!,
            annualRevenueCents = values[1]!!,
            annualCostsCents = values[2]!!
        )
        repository.updateGardenFinance(gardenId, finance)
        return finance
    }
}
