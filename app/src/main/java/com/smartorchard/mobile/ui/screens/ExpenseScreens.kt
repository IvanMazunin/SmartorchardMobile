package com.smartorchard.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartorchard.mobile.R
import com.smartorchard.mobile.data.*
import com.smartorchard.mobile.domain.*
import com.smartorchard.mobile.state.OrchardViewModel
import com.smartorchard.mobile.ui.*
import com.smartorchard.mobile.ui.theme.*
import java.math.BigDecimal

@Composable fun ExpensesScreen(vm: OrchardViewModel, data: AppData) {
    ScreenTitle("Затраты", vm.garden.name)
    OrchardCard {
        Text("Всего вложено", color = OrchardMuted)
        Text(currency(vm.expenses.sumOf { it.totalCents }), style = MaterialTheme.typography.headlineLarge)
        Text("${vm.expenses.size} записей · прямые затраты сада", color = OrchardMuted)
    }
    PrimaryButton("Добавить затрату", R.drawable.ic_plus) { vm.go(Screen.NEW_EXPENSE) }
    SecondaryButton("Окупаемость сада", R.drawable.ic_chart_no_axes_combined) { vm.go(Screen.ECONOMICS) }
    if (vm.expenses.isEmpty()) InfoBox("Затрат пока нет. Добавьте первую запись.")
    vm.expenses.sortedByDescending { it.date }.forEach { expense -> OrchardCard {
        Text(expense.title, style = MaterialTheme.typography.titleMedium)
        Text("${expense.category} · ${prettyDate(expense.date)}", color = OrchardMuted, style = MaterialTheme.typography.bodyMedium)
        Detail("${expense.quantity} ${expense.unit} × ${expense.price} ₽", currency(expense.totalCents))
    } }
}

@Composable fun ExpenseFormScreen(vm: OrchardViewModel) {
    BackLink("Затраты") { vm.go(Screen.EXPENSES) }
    ScreenTitle("Новая затрата", vm.garden.name)
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(Categories.first()) }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var unit by rememberSaveable { mutableStateOf("шт.") }
    var price by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(DEMO_DATE) }
    Field("Наименование", title, { title = it })
    DropdownField("Категория", category, Categories.map { it to it }, { category = it })
    Field("Количество", quantity, { quantity = it }, numeric = true)
    Field("Единица измерения", unit, { unit = it })
    Field("Цена за единицу, ₽", price, { price = it }, numeric = true)
    Field("Дата · ГГГГ-ММ-ДД", date, { date = it })
    OrchardCard { Detail("Итого", expenseCents(quantity, price)?.let(::currency) ?: "—") }
    ErrorBox(vm.error)
    PrimaryButton("Сохранить затрату", R.drawable.ic_check, !vm.busy) { vm.saveExpense(title, category, quantity, unit, price, date) }
}

@Composable fun EconomicsScreen(vm: OrchardViewModel, data: AppData) {
    BackLink("Затраты") { vm.go(Screen.EXPENSES) }
    ScreenTitle("Окупаемость", "Сценарный расчет · ${vm.garden.name}")
    val total = vm.expenses.sumOf { it.totalCents }; val finance = vm.garden.finance
    OrchardCard {
        Detail("Затраты за все время", currency(total))
        Detail("Полученная выручка", currency(finance.revenueCents))
        Detail("Осталось покрыть", currency((total - finance.revenueCents).coerceAtLeast(0)))
        Text(payback(total, finance), style = MaterialTheme.typography.headlineMedium, color = OrchardGreen)
        Text("При сохранении годового дохода и расходов", color = OrchardMuted, style = MaterialTheme.typography.bodyMedium)
    }
    fun amount(value: Long) = BigDecimal.valueOf(value, 2).toPlainString()
    var revenue by rememberSaveable(vm.garden.id) { mutableStateOf(amount(finance.revenueCents)) }
    var annualRevenue by rememberSaveable(vm.garden.id) { mutableStateOf(amount(finance.annualRevenueCents)) }
    var annualCosts by rememberSaveable(vm.garden.id) { mutableStateOf(amount(finance.annualCostsCents)) }
    SectionLabel("Параметры прогноза")
    Field("Полученная выручка, ₽", revenue, { revenue = it }, numeric = true)
    Field("Ожидаемая выручка в год, ₽", annualRevenue, { annualRevenue = it }, numeric = true)
    Field("Ожидаемые расходы в год, ₽", annualCosts, { annualCosts = it }, numeric = true)
    InfoBox("Срок = оставшиеся вложения / (годовая выручка − годовые расходы). При нулевом или отрицательном результате срок не рассчитывается.")
    ErrorBox(vm.error)
    PrimaryButton("Пересчитать и сохранить", enabled = !vm.busy) { vm.saveFinance(revenue, annualRevenue, annualCosts) }
}
