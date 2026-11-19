package com.smartorchard.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smartorchard.mobile.R
import com.smartorchard.mobile.data.*
import com.smartorchard.mobile.domain.*
import com.smartorchard.mobile.ui.*
import com.smartorchard.mobile.ui.theme.*

@Composable
fun IrrigationScreen(
    garden: Garden,
    zones: List<IrrigationZone>,
    schedules: List<IrrigationSchedule>,
    previewAt: String = DEMO_TIME,
    busy: Boolean = false,
    error: String? = null,
    onOpenZones: () -> Unit = {},
    onNewSchedule: () -> Unit = {},
    onApplyPreviewTime: (String) -> Unit = {},
    onDeleteSchedule: (String) -> Unit = {}
) {
    var time by rememberSaveable { mutableStateOf(previewAt) }

    LazyScreen {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ScreenTitle(
                    title = "Автополив",
                    subtitle = "${garden.name} · зоны и временные интервалы"
                )
                InfoBox(
                    text = "Демо: полив определяется выбранным временем, без подключения оборудования.",
                    icon = R.drawable.ic_droplets
                )
                SecondaryButton(
                    text = "Зоны полива",
                    icon = R.drawable.ic_trees,
                    onClick = onOpenZones
                )
                PrimaryButton(
                    text = "Новый интервал",
                    icon = R.drawable.ic_plus,
                    onClick = onNewSchedule
                )
                Field(
                    label = "Предпросмотр · ГГГГ-ММ-ДДTЧЧ:ММ",
                    value = time,
                    onChange = { time = it },
                    placeholder = DEMO_TIME
                )
                SecondaryButton(
                    text = "Применить время",
                    onClick = { onApplyPreviewTime(time) }
                )
                ErrorBox(error)

                SectionLabel("Интервалы полива")
                if (schedules.isEmpty()) {
                    Text(
                        text = "Интервалов пока нет. Задайте, где и когда действует автополив.",
                        color = OrchardMuted
                    )
                }
            }
        }

        items(items = schedules, key = { it.id }) { schedule ->
            val zone = zones.find { it.id == schedule.zoneId }
            val active = previewAt >= schedule.start && previewAt < schedule.end
            val badgeText = when {
                active -> "Действует"
                previewAt >= schedule.end -> "Завершен"
                else -> "Запланирован"
            }
            OrchardCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = zone?.name.orEmpty(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium
                    )
                    DemoBadge(badgeText)
                }
                Text("${prettyDate(schedule.start)} · ${schedule.start.takeLast(5)} — ${schedule.end.takeLast(5)}")
                Text("${zone?.treeIds?.size ?: 0} деревьев", color = OrchardMuted)
                TextButton(
                    onClick = { onDeleteSchedule(schedule.id) },
                    enabled = !busy
                ) {
                    OrchardIcon(id = R.drawable.ic_trash2, modifier = Modifier.size(20.dp))
                    Text("Удалить интервал")
                }
            }
        }
    }
}

@Composable
fun ZonesScreen(
    zones: List<IrrigationZone>,
    wateringZoneIds: Set<String>,
    onBack: () -> Unit = {},
    onCreateZone: () -> Unit = {},
    onOpenZoneDetail: (String) -> Unit = {}
) {
    LazyScreen {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BackLink("Автополив", onClick = onBack)
                ScreenTitle(
                    title = "Зоны полива",
                    subtitle = "Произвольные клетки сада"
                )
                PrimaryButton(
                    text = "Создать зону",
                    icon = R.drawable.ic_plus,
                    onClick = onCreateZone
                )
            }
        }

        items(items = zones, key = { it.id }) { zone ->
            val isWatering = zone.id in wateringZoneIds
            OrchardCard(modifier = Modifier.clickable { onOpenZoneDetail(zone.id) }) {
                Text(zone.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${zone.treeIds.size} деревьев · выбор клеток",
                    color = OrchardMuted
                )
                Text(
                    text = if (isWatering) "Демо: полив действует" else "Открыть состав зоны",
                    color = if (isWatering) WaterBlue else OrchardGreen
                )
            }
        }

        if (zones.isEmpty()) {
            item(key = "empty") {
                InfoBox("Выберите отдельные деревья на доске. Зона может пересекать несколько рядов.")
            }
        }
    }
}

@Composable
fun ZoneEditorScreen(
    garden: Garden,
    selectedTreeId: String,
    zones: List<IrrigationZone>,
    wateringZoneIds: Set<String>,
    zoneDraft: ZoneDraft,
    busy: Boolean = false,
    error: String? = null,
    onBack: () -> Unit = {},
    onUpdateDraft: (ZoneDraft) -> Unit = {},
    onToggleZoneCell: (String) -> Unit = {},
    onSaveZone: () -> Unit = {}
) {
    BackLink("Зоны полива", onClick = onBack)
    ScreenTitle(
        title = if (zoneDraft.id == null) "Новая зона" else "Изменить зону",
        subtitle = "Выберите отдельные клетки в любых рядах"
    )
    Field(
        label = "Название зоны",
        value = zoneDraft.name,
        onChange = { onUpdateDraft(zoneDraft.copy(name = it.take(60))) }
    )
    DropdownField(
        label = "Цвет на доске",
        value = zoneDraft.color,
        options = listOf(0 to "Зеленый", 1 to "Сиреневый", 2 to "Оливковый"),
        onSelect = { onUpdateDraft(zoneDraft.copy(color = it)) }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Выбрано: ${zoneDraft.treeIds.size}",
            modifier = Modifier.weight(1f),
            color = OrchardMuted
        )
        TextButton(onClick = { onUpdateDraft(zoneDraft.copy(treeIds = emptyList())) }) {
            Text("Очистить")
        }
    }

    GardenGrid(
        garden = garden,
        selectedTreeId = selectedTreeId,
        zones = zones,
        wateringZoneIds = wateringZoneIds,
        rowPage = 0,
        colPage = 0,
        filter = null,
        zoneDraft = zoneDraft,
        selectedZone = null,
        mode = GridMode.EDIT_ZONE,
        onPageChange = { _, _ -> },
        onSelectTree = {},
        onToggleZoneCell = onToggleZoneCell
    )

    Text(
        text = "Дерево может входить только в одну зону. Занятые другой зоной клетки недоступны.",
        color = OrchardMuted,
        style = MaterialTheme.typography.bodyMedium
    )
    ErrorBox(error)
    PrimaryButton(
        text = "Сохранить зону",
        icon = R.drawable.ic_check,
        enabled = !busy,
        onClick = onSaveZone
    )
}

@Composable
fun ZoneDetailScreen(
    garden: Garden,
    zone: IrrigationZone?,
    zones: List<IrrigationZone>,
    wateringZoneIds: Set<String>,
    onBack: () -> Unit = {},
    onEditZone: (String) -> Unit = {},
    onAddSchedule: () -> Unit = {}
) {
    if (zone == null) return

    BackLink("Зоны полива", onClick = onBack)
    ScreenTitle(
        title = zone.name,
        subtitle = "${zone.treeIds.size} деревьев · состав зоны"
    )
    if (zone.id in wateringZoneIds) {
        InfoBox(
            text = "Демо: для этой зоны сейчас действует полив.",
            icon = R.drawable.ic_droplets
        )
    }

    GardenGrid(
        garden = garden,
        selectedTreeId = "",
        zones = zones,
        wateringZoneIds = wateringZoneIds,
        rowPage = 0,
        colPage = 0,
        filter = null,
        selectedZone = zone,
        mode = GridMode.VIEW_ZONE,
        onPageChange = { _, _ -> },
        onSelectTree = {},
        onToggleZoneCell = {}
    )

    SecondaryButton(
        text = "Изменить состав",
        onClick = { onEditZone(zone.id) }
    )
    PrimaryButton(
        text = "Добавить интервал",
        icon = R.drawable.ic_calendar_clock,
        onClick = onAddSchedule
    )
}

@Composable
fun ScheduleFormScreen(
    zones: List<IrrigationZone>,
    initialZone: IrrigationZone?,
    busy: Boolean = false,
    error: String? = null,
    onBack: () -> Unit = {},
    onClearError: () -> Unit = {},
    onSaveSchedule: (zoneId: String, start: String, end: String) -> Unit = { _, _, _ -> }
) {
    BackLink("Автополив", onClick = onBack)
    ScreenTitle(
        title = "Интервал полива",
        subtitle = "Автоматическое действие выбранной зоны"
    )

    var zoneId by rememberSaveable { mutableStateOf(initialZone?.id ?: "") }
    var start by rememberSaveable { mutableStateOf("2026-10-29T11:00") }
    var end by rememberSaveable { mutableStateOf("2026-10-29T11:30") }

    DropdownField(
        label = "Зона полива",
        value = zoneId,
        options = zones.map { it.id to "${it.name} · ${it.treeIds.size} деревьев" },
        onSelect = {
            zoneId = it
            onClearError()
        }
    )
    Field(
        label = "Начало · ГГГГ-ММ-ДДTЧЧ:ММ",
        value = start,
        onChange = {
            start = it
            onClearError()
        }
    )
    Field(
        label = "Окончание · ГГГГ-ММ-ДДTЧЧ:ММ",
        value = end,
        onChange = {
            end = it
            onClearError()
        }
    )
    Text(
        text = "Начало включительно, окончание исключительно. Пересечения в одной зоне не допускаются. В макете выполнение имитируется.",
        color = OrchardMuted,
        style = MaterialTheme.typography.bodyMedium
    )
    ErrorBox(error)
    PrimaryButton(
        text = "Сохранить интервал",
        enabled = !busy,
        onClick = { onSaveSchedule(zoneId, start, end) }
    )
}

@Preview(showBackground = true)
@Composable
private fun IrrigationScreenPreview() {
    SmartOrchardTheme {
        IrrigationScreen(
            garden = PreviewSamples.garden,
            zones = PreviewSamples.zones,
            schedules = PreviewSamples.schedules
        )
    }
}
