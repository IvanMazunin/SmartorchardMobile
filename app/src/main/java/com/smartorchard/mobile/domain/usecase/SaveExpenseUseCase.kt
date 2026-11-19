package com.smartorchard.mobile.domain.usecase

import com.smartorchard.mobile.data.Expense
import com.smartorchard.mobile.data.OrchardRepository
import com.smartorchard.mobile.domain.ValidationException
import com.smartorchard.mobile.domain.expenseCents
import com.smartorchard.mobile.domain.prettyDate
import com.smartorchard.mobile.domain.uid
import com.smartorchard.mobile.domain.validDate
import com.smartorchard.mobile.domain.validate
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class SaveExpenseUseCase @Inject constructor(
    private val repository: OrchardRepository,
    private val clock: Clock
) {
    constructor(repository: OrchardRepository) : this(repository, Clock.systemDefaultZone())

    suspend operator fun invoke(
        gardenId: String,
        title: String,
        category: String,
        quantity: String,
        unit: String,
        price: String,
        date: String
    ): Expense {
        validate(title.isNotBlank() && unit.isNotBlank()) {
            "Укажите наименование и единицу измерения."
        }
        val cents = expenseCents(quantity, price)
            ?: throw ValidationException("Количество должно быть положительным, цена — неотрицательной.")
        val maxDate = LocalDate.now(clock).toString()
        validate(validDate(date) && date <= maxDate) {
            "Укажите дату расхода не позже ${prettyDate(maxDate)}."
        }
        val item = Expense(
            id = uid(),
            gardenId = gardenId,
            title = title.trim(),
            category = category,
            quantity = quantity,
            unit = unit.trim(),
            price = price,
            totalCents = cents,
            date = date
        )
        repository.addExpense(item)
        return item
    }
}
