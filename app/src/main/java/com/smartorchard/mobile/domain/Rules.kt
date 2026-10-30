package com.smartorchard.mobile.domain

import com.smartorchard.mobile.data.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

const val DEMO_DATE = "2026-10-29"
const val DEMO_TIME = "2026-10-29T09:41"
val Categories = listOf("Посадочный материал", "Материалы", "Оборудование", "Вода и энергия", "Услуги", "Другое")
fun uid(): String = UUID.randomUUID().toString()
fun seedId(value: String): String = UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()
fun normalized(value: String): String = value.trim().lowercase(Locale.forLanguageTag("ru")).replace(Regex("\\s+"), " ")
fun decimal(value: String, scale: Int): BigDecimal? {
    val text = value.trim().replace(',', '.')
    if (text.length > 24 || !Regex("^\\d+(?:\\.\\d{1,$scale})?$").matches(text)) return null
    return text.toBigDecimalOrNull()
}
data class PlantingEstimate(val count: Int, val raw: BigDecimal, val density: BigDecimal)
fun plantingEstimate(area: String, crop: Crop): PlantingEstimate? = runCatching {
    val a = decimal(area, 4)?.takeIf { it > BigDecimal.ZERO } ?: return@runCatching null
    val r = decimal(crop.rowSpacing, 3)?.takeIf { it > BigDecimal.ZERO } ?: return@runCatching null
    val t = decimal(crop.treeSpacing, 3)?.takeIf { it > BigDecimal.ZERO } ?: return@runCatching null
    val divisor = r * t
    val count = (a * BigDecimal(10000)).divide(divisor, 0, RoundingMode.FLOOR).intValueExact()
    PlantingEstimate(count, (a * BigDecimal(10000)).divide(divisor, 6, RoundingMode.HALF_UP), BigDecimal(10000).divide(divisor, 4, RoundingMode.HALF_UP))
}.getOrNull()
fun sameCrop(a: Crop, b: Crop): Boolean {
    val row = decimal(b.rowSpacing, 3) ?: return false
    val tree = decimal(b.treeSpacing, 3) ?: return false
    return normalized(a.name) == normalized(b.name) && normalized(a.variety) == normalized(b.variety)
        && decimal(a.rowSpacing, 3)?.compareTo(row) == 0 && decimal(a.treeSpacing, 3)?.compareTo(tree) == 0
}
fun newGarden(name: String, crop: Crop, area: String, date: String, places: Int): Garden {
    val count = requireNotNull(plantingEstimate(area, crop)).count
    require(count in 1..10000 && places in 1..minOf(100, count) && (count + places - 1) / places <= 100)
    return Garden(uid(), name.trim(), crop.id, area.replace(',', '.'), date, places, count,
        List(count) { i -> Tree(uid(), i / places + 1, i % places + 1) })
}
fun expenseCents(quantity: String, price: String): Long? = runCatching {
    val q = decimal(quantity, 3)?.takeIf { it > BigDecimal.ZERO } ?: return@runCatching null
    val p = decimal(price, 2) ?: return@runCatching null
    val result = (q * p).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    result.takeIf { it in 0..100_000_000_000_000L }
}.getOrNull()
fun currency(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ru-RU")).apply {
    maximumFractionDigits = if (cents % 100 == 0L) 0 else 2
}.format(BigDecimal.valueOf(cents, 2))
fun number(value: Number): String = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU")).apply { maximumFractionDigits = 4 }.format(value)
fun treeCode(crop: Crop, tree: Tree): String = "${crop.prefix}-${tree.row.toString().padStart(2, '0')}${tree.place.toString().padStart(2, '0')}"
fun qrPayload(tree: Tree): String = "smartorchard://tree/${tree.id}"
fun parseQr(payload: String): String? {
    if (!payload.startsWith("smartorchard://tree/")) return null
    val id = payload.removePrefix("smartorchard://tree/")
    return runCatching { UUID.fromString(id).toString().takeIf { it.equals(id, true) } }.getOrNull()
}
fun overlaps(start: String, end: String, other: IrrigationSchedule): Boolean = start < other.end && end > other.start
fun validDate(value: String): Boolean = runCatching { LocalDate.parse(value).toString() == value }.getOrDefault(false)
fun validDateTime(value: String): Boolean = runCatching {
    Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}$").matches(value) && LocalDateTime.parse(value).year in 1..9999
}.getOrDefault(false)
fun prettyDate(value: String): String = runCatching {
    LocalDate.parse(value.take(10)).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru")))
}.getOrDefault(value)
fun prettyTimestamp(value: String): String = runCatching {
    OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("d MMMM HH:mm", Locale.forLanguageTag("ru")))
}.getOrDefault(value)
fun payback(total: Long, finance: Finance): String {
    val remaining = (total - finance.revenueCents).coerceAtLeast(0)
    val net = finance.annualRevenueCents - finance.annualCostsCents
    return when {
        total == 0L -> "Нет затрат"
        remaining == 0L -> "Затраты покрыты"
        net <= 0L -> "Не окупается"
        remaining.toDouble() / net < .1 -> "Менее 0,1 года"
        else -> "${number(BigDecimal.valueOf(remaining).divide(BigDecimal.valueOf(net), 1, RoundingMode.HALF_UP))} года"
    }
}
