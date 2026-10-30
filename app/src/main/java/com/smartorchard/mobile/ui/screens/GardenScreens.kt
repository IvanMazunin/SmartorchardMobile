package com.smartorchard.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartorchard.mobile.R
import com.smartorchard.mobile.data.*
import com.smartorchard.mobile.domain.*
import com.smartorchard.mobile.state.OrchardViewModel
import com.smartorchard.mobile.ui.*
import com.smartorchard.mobile.ui.theme.*

@Composable fun BoardScreen(vm: OrchardViewModel, data: AppData) {
    TextButton({ vm.go(Screen.GARDENS) }, contentPadding = PaddingValues(0.dp)) { Text(vm.garden.name); OrchardIcon(R.drawable.ic_chevron_down) }
    ScreenTitle("Доска сада", "${vm.crop.name} · ${vm.crop.variety} · ${number(vm.garden.areaHa.toBigDecimal())} га")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        FilterChip(!vm.boardZones, { vm.boardZones = false }, { Text("Состояния") })
        FilterChip(vm.boardZones, { vm.boardZones = true }, { Text("Зоны полива") })
    }
    if (vm.zones.any { vm.watering(it) }) InfoBox("Демо: сейчас действует полив. Маркеры показаны только у деревьев активной зоны.", R.drawable.ic_droplets)
    if (vm.boardZones) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Зоны из отдельных клеток", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = OrchardMuted)
            TextButton({ vm.go(Screen.ZONES) }) { Text("Все зоны"); OrchardIcon(R.drawable.ic_chevron_right, Modifier.size(15.dp)) }
        }
        SecondaryButton("Создать зону", R.drawable.ic_plus) { vm.beginZone() }
    } else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(null to "Все", TreeStatus.WARNING to "Есть отклонения", TreeStatus.UNKNOWN to "Без осмотра").forEach { (status, label) ->
            FilterChip(vm.filter == status, { vm.filter = status }, { Text(label, style = MaterialTheme.typography.labelSmall) })
        }
    }
    GardenGrid(vm, data, if (vm.boardZones) GridMode.ZONES else GridMode.HEALTH)
    Text("Синяя полоса — действует демополив", color = WaterBlue, style = MaterialTheme.typography.bodyMedium)
    if (!vm.boardZones) StatusLegend(vm.garden) else vm.zones.forEach { zone -> TextButton({ vm.selectedZoneId = zone.id; vm.go(Screen.ZONE_DETAIL) }) { Text("${zone.name} · ${zone.treeIds.size} деревьев") } }
    OrchardCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Дерево ${treeCode(vm.crop, vm.tree)}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); StatusBadge(vm.tree.status) }
        Text("Ряд ${vm.tree.row} · место ${vm.tree.place}", color = OrchardMuted, style = MaterialTheme.typography.bodyMedium)
        PrimaryButton("Паспорт дерева", R.drawable.ic_arrow_right) { vm.openTree(vm.tree.id) }
    }
}

@Composable fun GardensScreen(vm: OrchardViewModel, data: AppData) {
    ScreenTitle("Мои сады", "Создание и выбор сада")
    PrimaryButton("Новый сад", R.drawable.ic_plus) { vm.beginGarden() }
    data.gardens.forEach { garden ->
        val crop = data.crops.first { it.id == garden.cropId }
        OrchardCard(Modifier.clickable { vm.chooseGarden(garden.id) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { OrchardIcon(R.drawable.ic_trees, Modifier.size(32.dp), OrchardGreen); Spacer(Modifier.width(10.dp)); Text(garden.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); OrchardIcon(R.drawable.ic_arrow_up_right) }
            Text("${crop.name}${if (crop.variety.isNotEmpty()) " · ${crop.variety}" else ""} · ${garden.plantingDate.take(4)}", color = OrchardMuted)
            Detail("Площадь", "${number(garden.areaHa.toBigDecimal())} га")
            Detail("Деревьев", "${garden.trees.size}")
            Text("${garden.rows} рядов · до ${garden.placesPerRow} мест в ряду", color = OrchardMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable fun NewGardenScreen(vm: OrchardViewModel, data: AppData) {
    BackLink("Мои сады") { vm.go(Screen.GARDENS) }
    ScreenTitle("Новый сад", "Культура, схема посадки и структура сада")
    val draft = vm.gardenDraft; val crop = data.crops.first { it.id == draft.cropId }
    val estimate = plantingEstimate(draft.areaHa, crop); val places = draft.places.toIntOrNull()
    Field("Название сада", draft.name, { vm.updateGardenDraft(draft.copy(name = it.take(80))) }, placeholder = "Например, Восточный сад")
    DropdownField("Культура и сорт", draft.cropId, data.crops.map { it.id to (it.name + if (it.variety.isNotBlank()) " · ${it.variety}" else "") }) { vm.updateGardenDraft(draft.copy(cropId = it)) }
    TextButton({ vm.beginCrop(true) }) { OrchardIcon(R.drawable.ic_plus); Text("Создать культуру") }
    SectionLabel("Схема посадки")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Field("Между рядами, м", crop.rowSpacing, {}, Modifier.weight(1f), readOnly = true)
        Field("Между деревьями, м", crop.treeSpacing, {}, Modifier.weight(1f), readOnly = true)
    }
    Field("Площадь, га", draft.areaHa, { vm.updateGardenDraft(draft.copy(areaHa = it)) }, numeric = true, placeholder = "0,0312")
    OrchardCard {
        Text("Будет создано деревьев", color = OrchardMuted)
        Text(estimate?.count?.let(::number) ?: "—", style = MaterialTheme.typography.headlineLarge)
        Text("N = площадь × 10 000 / (междурядье × шаг)", style = MaterialTheme.typography.bodyMedium)
        if (estimate != null) Text("${draft.areaHa} × 10 000 / (${crop.rowSpacing} × ${crop.treeSpacing}) = ${number(estimate.raw)} → ${estimate.count}", style = MaterialTheme.typography.bodyMedium, color = OrchardMuted)
        if (estimate != null && estimate.count in 1..10000 && places != null && places in 1..minOf(100, estimate.count)) {
            val rows = (estimate.count + places - 1) / places
            Detail("Рядов", rows.toString()); Detail("В последнем ряду", (estimate.count - (rows - 1) * places).toString())
        }
    }
    Field("Дата посадки · ГГГГ-ММ-ДД", draft.date, { vm.updateGardenDraft(draft.copy(date = it)) })
    Field("Мест в полном ряду", draft.places, { vm.updateGardenDraft(draft.copy(places = it, placesManual = true)) }, numeric = true)
    Text("Число деревьев округляется вниз. Пустые места последнего ряда не создают деревья или QR.", color = OrchardMuted, style = MaterialTheme.typography.bodyMedium)
    ErrorBox(vm.error)
    PrimaryButton("Создать сад", R.drawable.ic_arrow_right, !vm.busy) { vm.saveGarden() }
}

@Composable fun CropsScreen(vm: OrchardViewModel, data: AppData) {
    BackLink("Ещё") { vm.go(Screen.MORE) }
    ScreenTitle("Культуры", "Сорта и схемы посадки")
    PrimaryButton("Создать культуру", R.drawable.ic_plus) { vm.beginCrop(false) }
    data.crops.forEach { crop -> OrchardCard {
        Text(crop.name + if (crop.variety.isNotBlank()) " · ${crop.variety}" else "", style = MaterialTheme.typography.titleMedium)
        Detail("Между рядами", "${crop.rowSpacing} м"); Detail("Между деревьями", "${crop.treeSpacing} м")
    } }
}
@Composable fun NewCropScreen(vm: OrchardViewModel) {
    BackLink(if (vm.cropReturnToGarden) "Новый сад" else "Культуры") { vm.cancelCrop() }
    ScreenTitle("Новая культура", "Название, сорт и схема посадки")
    val draft = vm.cropDraft
    Field("Культура", draft.name, { vm.cropDraft = draft.copy(name = it.take(60)); vm.clearFormError() }, placeholder = "Например, Вишня")
    Field("Сорт", draft.variety, { vm.cropDraft = draft.copy(variety = it.take(60)); vm.clearFormError() }, placeholder = "Необязательно")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Field("Между рядами, м", draft.row, { vm.cropDraft = draft.copy(row = it); vm.clearFormError() }, Modifier.weight(1f), numeric = true)
        Field("Между деревьями, м", draft.tree, { vm.cropDraft = draft.copy(tree = it); vm.clearFormError() }, Modifier.weight(1f), numeric = true)
    }
    Text("Оба расстояния должны быть больше нуля, до трех знаков после запятой. Схема используется при создании сада.", color = OrchardMuted, style = MaterialTheme.typography.bodyMedium)
    ErrorBox(vm.error)
    PrimaryButton("Создать культуру", enabled = !vm.busy) { vm.saveCrop() }
}
