package com.smartorchard.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartorchard.mobile.R
import com.smartorchard.mobile.data.*
import com.smartorchard.mobile.domain.*
import com.smartorchard.mobile.state.OrchardViewModel
import com.smartorchard.mobile.ui.*
import com.smartorchard.mobile.ui.theme.*

@Composable fun IrrigationScreen(vm: OrchardViewModel, data: AppData) {
    ScreenTitle("Автополив", "${vm.garden.name} · зоны и временные интервалы")
    InfoBox("Демо: полив определяется выбранным временем, без подключения оборудования.", R.drawable.ic_droplets)
    SecondaryButton("Зоны полива", R.drawable.ic_trees) { vm.go(Screen.ZONES) }
    PrimaryButton("Новый интервал", R.drawable.ic_plus) { if (vm.zones.isEmpty()) { vm.beginZone(); vm.notify("Сначала создайте зону из клеток") } else vm.go(Screen.SCHEDULE) }
    var time by rememberSaveable { mutableStateOf(vm.previewAt) }
    Field("Предпросмотр · ГГГГ-ММ-ДДTЧЧ:ММ", time, { time = it }, placeholder = DEMO_TIME)
    SecondaryButton("Применить время") { if (validDateTime(time)) { vm.previewAt = time; vm.clearFormError() } else vm.fail("Укажите дату и время, например $DEMO_TIME") }
    ErrorBox(vm.error)
    SectionLabel("Интервалы полива")
    if (vm.schedules.isEmpty()) Text("Интервалов пока нет. Задайте, где и когда действует автополив.", color = OrchardMuted)
    vm.schedules.forEach { schedule ->
        val zone = vm.zones.find { it.id == schedule.zoneId }
        val active = vm.previewAt >= schedule.start && vm.previewAt < schedule.end
        OrchardCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(zone?.name.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); DemoBadge(if (active) "Действует" else if (vm.previewAt >= schedule.end) "Завершен" else "Запланирован") }
            Text("${prettyDate(schedule.start)} · ${schedule.start.takeLast(5)} — ${schedule.end.takeLast(5)}")
            Text("${zone?.treeIds?.size ?: 0} деревьев", color = OrchardMuted)
            TextButton({ vm.deleteSchedule(schedule.id) }, enabled = !vm.busy) { OrchardIcon(R.drawable.ic_trash2); Text("Удалить интервал") }
        }
    }
}

@Composable fun ZonesScreen(vm: OrchardViewModel) {
    BackLink("Автополив") { vm.go(Screen.IRRIGATION) }
    ScreenTitle("Зоны полива", "Произвольные клетки сада")
    PrimaryButton("Создать зону", R.drawable.ic_plus) { vm.beginZone() }
    vm.zones.forEach { zone -> OrchardCard(Modifier.clickable { vm.selectedZoneId = zone.id; vm.go(Screen.ZONE_DETAIL) }) {
        Text(zone.name, style = MaterialTheme.typography.titleLarge); Text("${zone.treeIds.size} деревьев · выбор клеток", color = OrchardMuted)
        Text(if (vm.watering(zone)) "Демо: полив действует" else "Открыть состав зоны", color = if (vm.watering(zone)) WaterBlue else OrchardGreen)
    } }
    if (vm.zones.isEmpty()) InfoBox("Выберите отдельные деревья на доске. Зона может пересекать несколько рядов.")
}
@Composable fun ZoneEditorScreen(vm: OrchardViewModel, data: AppData) {
    BackLink("Зоны полива") { vm.go(Screen.ZONES) }
    ScreenTitle(if (vm.zoneDraft.id == null) "Новая зона" else "Изменить зону", "Выберите отдельные клетки в любых рядах")
    Field("Название зоны", vm.zoneDraft.name, { vm.zoneDraft = vm.zoneDraft.copy(name = it.take(60)); vm.clearFormError() })
    DropdownField("Цвет на доске", vm.zoneDraft.color, listOf(0 to "Зеленый", 1 to "Сиреневый", 2 to "Оливковый")) { vm.zoneDraft = vm.zoneDraft.copy(color = it) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Выбрано: ${vm.zoneDraft.treeIds.size}", Modifier.weight(1f), color = OrchardMuted); TextButton({ vm.zoneDraft = vm.zoneDraft.copy(treeIds = emptyList()) }) { Text("Очистить") } }
    GardenGrid(vm, data, GridMode.EDIT_ZONE)
    Text("Дерево может входить только в одну зону. Занятые другой зоной клетки недоступны.", color = OrchardMuted, style = MaterialTheme.typography.bodyMedium)
    ErrorBox(vm.error)
    PrimaryButton("Сохранить зону", R.drawable.ic_check, !vm.busy) { vm.saveZone() }
}
@Composable fun ZoneDetailScreen(vm: OrchardViewModel, data: AppData) {
    val zone = vm.currentZone() ?: return
    BackLink("Зоны полива") { vm.go(Screen.ZONES) }
    ScreenTitle(zone.name, "${zone.treeIds.size} деревьев · состав зоны")
    if (vm.watering(zone)) InfoBox("Демо: для этой зоны сейчас действует полив.", R.drawable.ic_droplets)
    GardenGrid(vm, data, GridMode.VIEW_ZONE)
    SecondaryButton("Изменить состав") { vm.beginZone(zone.id) }
    PrimaryButton("Добавить интервал", R.drawable.ic_calendar_clock) { vm.go(Screen.SCHEDULE) }
}
@Composable fun ScheduleFormScreen(vm: OrchardViewModel) {
    BackLink("Автополив") { vm.go(Screen.IRRIGATION) }
    ScreenTitle("Интервал полива", "Автоматическое действие выбранной зоны")
    var zoneId by rememberSaveable { mutableStateOf(vm.currentZone()?.id ?: "") }
    var start by rememberSaveable { mutableStateOf("2026-10-29T11:00") }; var end by rememberSaveable { mutableStateOf("2026-10-29T11:30") }
    DropdownField("Зона полива", zoneId, vm.zones.map { it.id to "${it.name} · ${it.treeIds.size} деревьев" }) { zoneId = it; vm.clearFormError() }
    Field("Начало · ГГГГ-ММ-ДДTЧЧ:ММ", start, { start = it; vm.clearFormError() })
    Field("Окончание · ГГГГ-ММ-ДДTЧЧ:ММ", end, { end = it; vm.clearFormError() })
    Text("Начало включительно, окончание исключительно. Пересечения в одной зоне не допускаются. В макете выполнение имитируется.", color = OrchardMuted, style = MaterialTheme.typography.bodyMedium)
    ErrorBox(vm.error)
    PrimaryButton("Сохранить интервал", enabled = !vm.busy) { vm.saveSchedule(zoneId, start, end) }
}
