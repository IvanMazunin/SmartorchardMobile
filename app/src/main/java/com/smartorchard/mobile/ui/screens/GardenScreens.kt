package com.smartorchard.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
fun BoardScreen(
    garden: Garden,
    crop: Crop,
    tree: Tree,
    zones: List<IrrigationZone>,
    wateringZoneIds: Set<String>,
    rowPage: Int = 0,
    colPage: Int = 0,
    boardZones: Boolean = false,
    filter: TreeStatus? = null,
    onOpenGardens: () -> Unit = {},
    onOpenZones: () -> Unit = {},
    onBeginZone: () -> Unit = {},
    onBoardZonesChange: (Boolean) -> Unit = {},
    onFilterChange: (TreeStatus?) -> Unit = {},
    onPageChange: (Int, Int) -> Unit = { _, _ -> },
    onSelectTree: (String) -> Unit = {},
    onOpenZoneDetail: (String) -> Unit = {},
    onOpenTreePassport: (String) -> Unit = {}
) {
    TextButton(
        onClick = onOpenGardens,
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(garden.name)
        OrchardIcon(id = R.drawable.ic_chevron_down, modifier = Modifier.size(20.dp))
    }

    ScreenTitle(
        title = "Доска сада",
        subtitle = "${crop.name} · ${crop.variety} · ${number(garden.areaHa.toBigDecimal())} га"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        FilterChip(
            selected = !boardZones,
            onClick = { onBoardZonesChange(false) },
            label = { Text("Состояния") }
        )
        FilterChip(
            selected = boardZones,
            onClick = { onBoardZonesChange(true) },
            label = { Text("Зоны полива") }
        )
    }

    if (zones.any { it.id in wateringZoneIds }) {
        InfoBox(
            text = "Демо: сейчас действует полив. Маркеры показаны только у деревьев активной зоны.",
            icon = R.drawable.ic_droplets
        )
    }

    if (boardZones) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Зоны из отдельных клеток",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = OrchardMuted
            )
            TextButton(onClick = onOpenZones) {
                Text("Все зоны")
                OrchardIcon(R.drawable.ic_chevron_right, Modifier.size(15.dp))
            }
        }
        SecondaryButton(
            text = "Создать зону",
            icon = R.drawable.ic_plus,
            onClick = onBeginZone
        )
    } else {
        val statusFilters = listOf(
            null to "Все",
            TreeStatus.WARNING to "Есть отклонения",
            TreeStatus.UNKNOWN to "Без осмотра"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            statusFilters.forEach { (status, label) ->
                FilterChip(
                    selected = filter == status,
                    onClick = { onFilterChange(status) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }

    GardenGrid(
        garden = garden,
        selectedTreeId = tree.id,
        zones = zones,
        wateringZoneIds = wateringZoneIds,
        rowPage = rowPage,
        colPage = colPage,
        filter = filter,
        selectedZone = null,
        mode = if (boardZones) GridMode.ZONES else GridMode.HEALTH,
        onPageChange = onPageChange,
        onSelectTree = onSelectTree,
        onToggleZoneCell = {}
    )

    Text(
        text = "Синяя полоса — действует демополив",
        color = WaterBlue,
        style = MaterialTheme.typography.bodyMedium
    )

    if (!boardZones) {
        StatusLegend(garden)
    } else {
        zones.forEach { zone ->
            TextButton(onClick = { onOpenZoneDetail(zone.id) }) {
                Text("${zone.name} · ${zone.treeIds.size} деревьев")
            }
        }
    }

    OrchardCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Дерево ${treeCode(crop, tree)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            StatusBadge(tree.status)
        }
        Text(
            text = "Ряд ${tree.row} · место ${tree.place}",
            color = OrchardMuted,
            style = MaterialTheme.typography.bodyMedium
        )
        PrimaryButton(
            text = "Паспорт дерева",
            icon = R.drawable.ic_arrow_right,
            onClick = { onOpenTreePassport(tree.id) }
        )
    }
}

@Composable
fun GardensScreen(
    gardens: List<Garden>,
    crops: List<Crop>,
    onNewGarden: () -> Unit = {},
    onChooseGarden: (String) -> Unit = {}
) {
    LazyScreen {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ScreenTitle(
                    title = "Мои сады",
                    subtitle = "Создание и выбор сада"
                )
                PrimaryButton(
                    text = "Новый сад",
                    icon = R.drawable.ic_plus,
                    onClick = onNewGarden
                )
            }
        }

        items(items = gardens, key = { it.id }) { garden ->
            val crop = crops.find { it.id == garden.cropId } ?: crops.first()
            OrchardCard(modifier = Modifier.clickable { onChooseGarden(garden.id) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OrchardIcon(
                        id = R.drawable.ic_trees,
                        modifier = Modifier.size(32.dp),
                        tint = OrchardGreen
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = garden.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge
                    )
                    OrchardIcon(id = R.drawable.ic_arrow_up_right, modifier = Modifier.size(20.dp))
                }
                val varietySuffix = if (crop.variety.isNotEmpty()) " · ${crop.variety}" else ""
                Text(
                    text = "${crop.name}$varietySuffix · ${garden.plantingDate.take(4)}",
                    color = OrchardMuted
                )
                Detail("Площадь", "${number(garden.areaHa.toBigDecimal())} га")
                Detail("Деревьев", "${garden.trees.size}")
                Text(
                    text = "${garden.rows} рядов · до ${garden.placesPerRow} мест в ряду",
                    color = OrchardMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun NewGardenScreen(
    crops: List<Crop>,
    draft: GardenDraft,
    busy: Boolean = false,
    error: String? = null,
    onBack: () -> Unit = {},
    onUpdateDraft: (GardenDraft) -> Unit = {},
    onCreateCrop: () -> Unit = {},
    onSaveGarden: () -> Unit = {}
) {
    BackLink("Мои сады", onClick = onBack)
    ScreenTitle(
        title = "Новый сад",
        subtitle = "Культура, схема посадки и структура сада"
    )

    val crop = crops.find { it.id == draft.cropId } ?: crops.first()
    val estimate = plantingEstimate(draft.areaHa, crop)
    val places = draft.places.toIntOrNull()

    Field(
        label = "Название сада",
        value = draft.name,
        onChange = { onUpdateDraft(draft.copy(name = it.take(80))) },
        placeholder = "Например, Восточный сад"
    )
    DropdownField(
        label = "Культура и сорт",
        value = draft.cropId,
        options = crops.map { item ->
            val variety = if (item.variety.isNotBlank()) " · ${item.variety}" else ""
            item.id to (item.name + variety)
        },
        onSelect = { onUpdateDraft(draft.copy(cropId = it)) }
    )
    TextButton(onClick = onCreateCrop) {
        OrchardIcon(id = R.drawable.ic_plus, modifier = Modifier.size(20.dp))
        Text("Создать культуру")
    }

    SectionLabel("Схема посадки")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(
            label = "Между рядами, м",
            value = crop.rowSpacing,
            onChange = {},
            modifier = Modifier.weight(1f),
            readOnly = true
        )
        Field(
            label = "Между деревьями, м",
            value = crop.treeSpacing,
            onChange = {},
            modifier = Modifier.weight(1f),
            readOnly = true
        )
    }

    Field(
        label = "Площадь, га",
        value = draft.areaHa,
        onChange = { onUpdateDraft(draft.copy(areaHa = it)) },
        numeric = true,
        placeholder = "0,0312"
    )

    OrchardCard {
        Text("Будет создано деревьев", color = OrchardMuted)
        Text(
            text = estimate?.count?.let(::number) ?: "—",
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            text = "N = площадь × 10 000 / (междурядье × шаг)",
            style = MaterialTheme.typography.bodyMedium
        )
        if (estimate != null) {
            Text(
                text = "${draft.areaHa} × 10 000 / (${crop.rowSpacing} × ${crop.treeSpacing}) = ${number(estimate.raw)} → ${estimate.count}",
                style = MaterialTheme.typography.bodyMedium,
                color = OrchardMuted
            )
        }
        if (
            estimate != null &&
            estimate.count in 1..10000 &&
            places != null &&
            places in 1..minOf(100, estimate.count)
        ) {
            val rows = (estimate.count + places - 1) / places
            Detail("Рядов", rows.toString())
            Detail("В последнем ряду", (estimate.count - (rows - 1) * places).toString())
        }
    }

    Field(
        label = "Дата посадки · ГГГГ-ММ-ДД",
        value = draft.date,
        onChange = { onUpdateDraft(draft.copy(date = it)) }
    )
    Field(
        label = "Мест в полном ряду",
        value = draft.places,
        onChange = { onUpdateDraft(draft.copy(places = it, placesManual = true)) },
        numeric = true
    )
    Text(
        text = "Число деревьев округляется вниз. Пустые места последнего ряда не создают деревья или QR.",
        color = OrchardMuted,
        style = MaterialTheme.typography.bodyMedium
    )

    ErrorBox(error)
    PrimaryButton(
        text = "Создать сад",
        icon = R.drawable.ic_arrow_right,
        enabled = !busy,
        onClick = onSaveGarden
    )
}

@Composable
fun CropsScreen(
    crops: List<Crop>,
    onBack: () -> Unit = {},
    onNewCrop: () -> Unit = {}
) {
    LazyScreen {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BackLink("Ещё", onClick = onBack)
                ScreenTitle(
                    title = "Культуры",
                    subtitle = "Сорта и схемы посадки"
                )
                PrimaryButton(
                    text = "Создать культуру",
                    icon = R.drawable.ic_plus,
                    onClick = onNewCrop
                )
            }
        }

        items(items = crops, key = { it.id }) { crop ->
            OrchardCard {
                val variety = if (crop.variety.isNotBlank()) " · ${crop.variety}" else ""
                Text(
                    text = crop.name + variety,
                    style = MaterialTheme.typography.titleMedium
                )
                Detail("Между рядами", "${crop.rowSpacing} м")
                Detail("Между деревьями", "${crop.treeSpacing} м")
            }
        }
    }
}

@Composable
fun NewCropScreen(
    draft: CropDraft,
    fromGarden: Boolean = false,
    busy: Boolean = false,
    error: String? = null,
    onCancel: () -> Unit = {},
    onUpdateDraft: (CropDraft) -> Unit = {},
    onSaveCrop: () -> Unit = {}
) {
    BackLink(
        text = if (fromGarden) "Новый сад" else "Культуры",
        onClick = onCancel
    )
    ScreenTitle(
        title = "Новая культура",
        subtitle = "Название, сорт и схема посадки"
    )

    Field(
        label = "Культура",
        value = draft.name,
        onChange = { onUpdateDraft(draft.copy(name = it.take(60))) },
        placeholder = "Например, Вишня"
    )
    Field(
        label = "Сорт",
        value = draft.variety,
        onChange = { onUpdateDraft(draft.copy(variety = it.take(60))) },
        placeholder = "Необязательно"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(
            label = "Между рядами, м",
            value = draft.row,
            onChange = { onUpdateDraft(draft.copy(row = it)) },
            modifier = Modifier.weight(1f),
            numeric = true
        )
        Field(
            label = "Между деревьями, м",
            value = draft.tree,
            onChange = { onUpdateDraft(draft.copy(tree = it)) },
            modifier = Modifier.weight(1f),
            numeric = true
        )
    }
    Text(
        text = "Оба расстояния должны быть больше нуля, до трех знаков после запятой. Схема используется при создании сада.",
        color = OrchardMuted,
        style = MaterialTheme.typography.bodyMedium
    )

    ErrorBox(error)
    PrimaryButton(
        text = "Создать культуру",
        enabled = !busy,
        onClick = onSaveCrop
    )
}

@Preview(showBackground = true)
@Composable
private fun BoardScreenPreview() {
    SmartOrchardTheme {
        ScrollableScreen {
            BoardScreen(
                garden = PreviewSamples.garden,
                crop = PreviewSamples.crop,
                tree = PreviewSamples.tree,
                zones = PreviewSamples.zones,
                wateringZoneIds = PreviewSamples.wateringZoneIds
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GardensScreenPreview() {
    SmartOrchardTheme {
        GardensScreen(
            gardens = PreviewSamples.data.gardens,
            crops = PreviewSamples.data.crops
        )
    }
}
