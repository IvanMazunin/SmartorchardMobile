package com.smartorchard.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smartorchard.mobile.R
import com.smartorchard.mobile.data.*
import com.smartorchard.mobile.domain.*
import com.smartorchard.mobile.ui.*
import com.smartorchard.mobile.ui.theme.*
import java.math.BigDecimal

@Composable
fun ExpensesScreen(
    garden: Garden,
    expenses: List<Expense>,
    onAddExpense: () -> Unit = {},
    onOpenEconomics: () -> Unit = {}
) {
    val sorted = remember(expenses) { expenses.sortedByDescending { it.date } }

    LazyScreen {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ScreenTitle(
                    title = "Затраты",
                    subtitle = garden.name
                )

                OrchardCard {
                    Text("Всего вложено", color = OrchardMuted)
                    Text(
                        text = currency(expenses.sumOf { it.totalCents }),
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = "${expenses.size} записей · прямые затраты сада",
                        color = OrchardMuted
                    )
                }

                PrimaryButton(
                    text = "Добавить затрату",
                    icon = R.drawable.ic_plus,
                    onClick = onAddExpense
                )
                SecondaryButton(
                    text = "Окупаемость сада",
                    icon = R.drawable.ic_chart_no_axes_combined,
                    onClick = onOpenEconomics
                )

                if (expenses.isEmpty()) {
                    InfoBox("Затрат пока нет. Добавьте первую запись.")
                }
            }
        }

        items(items = sorted, key = { it.id }) { expense ->
            OrchardCard {
                Text(expense.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${expense.category} · ${prettyDate(expense.date)}",
                    color = OrchardMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Detail(
                    label = "${expense.quantity} ${expense.unit} × ${expense.price} ₽",
                    value = currency(expense.totalCents)
                )
            }
        }
    }
}

@Composable
fun ExpenseFormScreen(
    garden: Garden,
    defaultDate: String = DEMO_DATE,
    busy: Boolean = false,
    error: String? = null,
    onBack: () -> Unit = {},
    onSaveExpense: (
        title: String,
        category: String,
        quantity: String,
        unit: String,
        price: String,
        date: String
    ) -> Unit = { _, _, _, _, _, _ -> }
) {
    BackLink("Затраты", onClick = onBack)
    ScreenTitle(
        title = "Новая затрата",
        subtitle = garden.name
    )

    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(Categories.first()) }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var unit by rememberSaveable { mutableStateOf("шт.") }
    var price by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(defaultDate) }

    Field(
        label = "Наименование",
        value = title,
        onChange = { title = it }
    )
    DropdownField(
        label = "Категория",
        value = category,
        options = Categories.map { it to it },
        onSelect = { category = it }
    )
    Field(
        label = "Количество",
        value = quantity,
        onChange = { quantity = it },
        numeric = true
    )
    Field(
        label = "Единица измерения",
        value = unit,
        onChange = { unit = it }
    )
    Field(
        label = "Цена за единицу, ₽",
        value = price,
        onChange = { price = it },
        numeric = true
    )
    Field(
        label = "Дата · ГГГГ-ММ-ДД",
        value = date,
        onChange = { date = it }
    )

    OrchardCard {
        Detail("Итого", expenseCents(quantity, price)?.let(::currency) ?: "—")
    }
    ErrorBox(error)
    PrimaryButton(
        text = "Сохранить затрату",
        icon = R.drawable.ic_check,
        enabled = !busy,
        onClick = { onSaveExpense(title, category, quantity, unit, price, date) }
    )
}

@Composable
fun EconomicsScreen(
    garden: Garden,
    expenses: List<Expense>,
    busy: Boolean = false,
    error: String? = null,
    onBack: () -> Unit = {},
    onSaveFinance: (
        revenue: String,
        annualRevenue: String,
        annualCosts: String
    ) -> Unit = { _, _, _ -> }
) {
    BackLink("Затраты", onClick = onBack)
    ScreenTitle(
        title = "Окупаемость",
        subtitle = "Сценарный расчет · ${garden.name}"
    )

    val total = expenses.sumOf { it.totalCents }
    val finance = garden.finance

    OrchardCard {
        Detail("Затраты за все время", currency(total))
        Detail("Полученная выручка", currency(finance.revenueCents))
        Detail("Осталось покрыть", currency((total - finance.revenueCents).coerceAtLeast(0)))
        Text(
            text = payback(total, finance),
            style = MaterialTheme.typography.headlineMedium,
            color = OrchardGreen
        )
        Text(
            text = "При сохранении годового дохода и расходов",
            color = OrchardMuted,
            style = MaterialTheme.typography.bodyMedium
        )
    }

    fun amount(value: Long) = BigDecimal.valueOf(value, 2).toPlainString()

    var revenue by rememberSaveable(garden.id) {
        mutableStateOf(amount(finance.revenueCents))
    }
    var annualRevenue by rememberSaveable(garden.id) {
        mutableStateOf(amount(finance.annualRevenueCents))
    }
    var annualCosts by rememberSaveable(garden.id) {
        mutableStateOf(amount(finance.annualCostsCents))
    }

    SectionLabel("Параметры прогноза")
    Field(
        label = "Полученная выручка, ₽",
        value = revenue,
        onChange = { revenue = it },
        numeric = true
    )
    Field(
        label = "Ожидаемая выручка в год, ₽",
        value = annualRevenue,
        onChange = { annualRevenue = it },
        numeric = true
    )
    Field(
        label = "Ожидаемые расходы в год, ₽",
        value = annualCosts,
        onChange = { annualCosts = it },
        numeric = true
    )

    InfoBox("Срок = оставшиеся вложения / (годовая выручка − годовые расходы). При нулевом или отрицательном результате срок не рассчитывается.")
    ErrorBox(error)
    PrimaryButton(
        text = "Пересчитать и сохранить",
        enabled = !busy,
        onClick = { onSaveFinance(revenue, annualRevenue, annualCosts) }
    )
}

@Preview(showBackground = true)
@Composable
private fun ExpensesScreenPreview() {
    SmartOrchardTheme {
        ExpensesScreen(
            garden = PreviewSamples.garden,
            expenses = PreviewSamples.expenses
        )
    }
}
