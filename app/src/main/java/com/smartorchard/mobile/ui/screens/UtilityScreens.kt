package com.smartorchard.mobile.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.smartorchard.mobile.R
import com.smartorchard.mobile.data.*
import com.smartorchard.mobile.domain.*
import com.smartorchard.mobile.services.printQrLabel
import com.smartorchard.mobile.services.qrBitmap
import com.smartorchard.mobile.ui.*
import com.smartorchard.mobile.ui.theme.*

@Composable
fun MoreScreen(
    offlineDemo: Boolean = false,
    onNavigateSection: (NavRoute) -> Unit = {},
    onOfflineDemoChange: (Boolean) -> Unit = {}
) {
    ScreenTitle(
        title = "Ещё",
        subtitle = "SmartOrchard · мобильное приложение"
    )
    OrchardCard {
        Text("Иван Мазунин", style = MaterialTheme.typography.titleLarge)
        Text("Демонстрационный профиль · агроном", color = OrchardMuted)
    }

    val sections = listOf(
        Triple("Мои сады", R.drawable.ic_trees, NavRoute.Gardens),
        Triple("Культуры", R.drawable.ic_sprout, NavRoute.Crops),
        Triple("Bluetooth-принтер", R.drawable.ic_printer, NavRoute.Printer(returnToLabel = false)),
        Triple("Данные на устройстве", R.drawable.ic_save, NavRoute.Storage)
    )
    sections.forEach { (text, icon, target) ->
        OrchardCard(modifier = Modifier.clickable { onNavigateSection(target) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OrchardIcon(id = icon, tint = OrchardGreen, modifier = Modifier.size(20.dp))
                Text(text, Modifier.weight(1f))
                OrchardIcon(id = R.drawable.ic_chevron_right, modifier = Modifier.size(20.dp))
            }
        }
    }

    OrchardCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Демо: без сети")
                Text(
                    text = "Проверка ошибки ИИ-анализа",
                    color = OrchardMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Switch(
                checked = offlineDemo,
                onCheckedChange = onOfflineDemoChange
            )
        }
    }

    InfoBox("Все данные хранятся на этом устройстве. Сервер, ИИ, Bluetooth-принтер и оборудование полива пока заменены моками.")
}

@Composable
fun ScanScreen(
    garden: Garden,
    tree: Tree,
    error: String? = null,
    onLookup: (row: String, place: String) -> Unit = { _, _ -> },
    onOpenQr: (String) -> Unit = {}
) {
    ScreenTitle(
        title = "Найти дерево",
        subtitle = "Сканируйте QR на этикетке"
    )

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) onOpenQr(result.contents)
    }

    OrchardCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp),
            contentAlignment = Alignment.Center
        ) {
            OrchardIcon(
                id = R.drawable.ic_scan_line,
                modifier = Modifier.size(105.dp),
                tint = OrchardGreen
            )
        }
        Text("Наведите камеру на QR-код дерева", color = OrchardMuted)
        PrimaryButton(
            text = "Сканировать камерой",
            icon = R.drawable.ic_camera,
            onClick = {
                val options = ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("QR-код SmartOrchard")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
                scanner.launch(options)
            }
        )
    }

    var row by rememberSaveable { mutableStateOf("") }
    var place by rememberSaveable { mutableStateOf("") }

    SectionLabel("Или найдите по месту")
    Text(garden.name, color = OrchardMuted)
    Field(
        label = "Ряд",
        value = row,
        onChange = { row = it },
        numeric = true
    )
    Field(
        label = "Место",
        value = place,
        onChange = { place = it },
        numeric = true
    )
    SecondaryButton(
        text = "Открыть дерево",
        icon = R.drawable.ic_search,
        onClick = { onLookup(row, place) }
    )

    var payload by rememberSaveable { mutableStateOf("") }
    Field(
        label = "Ссылка с этикетки",
        value = payload,
        onChange = { payload = it },
        placeholder = "smartorchard://tree/UUID"
    )
    SecondaryButton(
        text = "Открыть по ссылке",
        onClick = { onOpenQr(payload.trim()) }
    )
    TextButton(onClick = { onOpenQr(qrPayload(tree)) }) {
        Text("Демо: открыть QR выбранного дерева")
    }
    ErrorBox(error)
}

@Composable
fun LabelScreen(
    garden: Garden,
    crop: Crop,
    tree: Tree,
    printerDevice: String?,
    copies: String = "1",
    error: String? = null,
    onBack: () -> Unit = {},
    onCopiesChange: (String) -> Unit = {},
    onOpenPrinter: () -> Unit = {},
    onDemoPrint: () -> Unit = {},
    onFail: (String) -> Unit = {}
) {
    BackLink("Карточка дерева", onClick = onBack)
    ScreenTitle(
        title = "Печать QR",
        subtitle = treeCode(crop, tree)
    )

    val payload = qrPayload(tree)
    val bitmap = remember(payload) { qrBitmap(payload).asImageBitmap() }
    val context = LocalContext.current

    OrchardCard {
        Text(garden.name, style = MaterialTheme.typography.titleMedium)
        Image(
            bitmap = bitmap,
            contentDescription = "QR-код ${treeCode(crop, tree)}",
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
        )
        Text(
            text = "Ряд ${tree.row} · место ${tree.place}",
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = payload,
            style = MaterialTheme.typography.bodyMedium,
            color = OrchardMuted
        )
    }

    Field(
        label = "Количество этикеток · 1–12",
        value = copies,
        onChange = onCopiesChange,
        numeric = true
    )
    SecondaryButton(
        text = printerDevice ?: "Подключить Bluetooth-принтер",
        icon = R.drawable.ic_bluetooth,
        onClick = onOpenPrinter
    )
    PrimaryButton(
        text = "Печать на демопринтере",
        icon = R.drawable.ic_printer,
        onClick = onDemoPrint
    )
    SecondaryButton(
        text = "Системная печать / PDF",
        icon = R.drawable.ic_file_down,
        onClick = {
            val count = copies.toIntOrNull()
            if (count == null || count !in 1..12) {
                onFail("Укажите от 1 до 12 копий.")
            } else {
                runCatching {
                    printQrLabel(
                        context = context,
                        garden = garden.name,
                        code = treeCode(crop, tree),
                        row = tree.row,
                        place = tree.place,
                        payload = payload,
                        copies = count
                    )
                }.onFailure {
                    onFail(it.message ?: "Печать недоступна.")
                }
            }
        }
    )

    ErrorBox(error)
    InfoBox("Bluetooth работает в деморежиме. Системная печать создаёт настоящий лист с QR-кодами; в диалоге Android можно выбрать «Сохранить как PDF».")
}

@Composable
fun PrinterScreen(
    printerDevice: String?,
    returnToLabel: Boolean = false,
    printerSearching: Boolean = false,
    printerSearched: Boolean = false,
    busy: Boolean = false,
    error: String? = null,
    onBack: () -> Unit = {},
    onSearchPrinters: () -> Unit = {},
    onConnectPrinter: (String) -> Unit = {},
    onDisconnectPrinter: () -> Unit = {}
) {
    BackLink(
        text = if (returnToLabel) "Печать QR" else "Ещё",
        onClick = onBack
    )
    ScreenTitle(
        title = "Bluetooth-принтер",
        subtitle = "Этикетки для деревьев"
    )
    DemoBadge("Демонстрационный адаптер")
    InfoBox(
        text = "Для реальной Bluetooth-печати понадобится адаптер протокола конкретной модели. Сейчас используются виртуальные устройства.",
        icon = R.drawable.ic_bluetooth
    )

    if (printerDevice != null) {
        OrchardCard {
            Text(printerDevice, style = MaterialTheme.typography.titleLarge)
            Text("Подключён · деморежим", color = OrchardGreen)
            SecondaryButton(
                text = "Отключить",
                enabled = !busy,
                onClick = onDisconnectPrinter
            )
            if (returnToLabel) {
                PrimaryButton(
                    text = "Вернуться к этикетке",
                    onClick = onBack
                )
            }
        }
    }

    PrimaryButton(
        text = if (printerSearching) "Поиск…" else "Найти демоустройства",
        icon = R.drawable.ic_search,
        enabled = !printerSearching,
        onClick = onSearchPrinters
    )
    if (printerSearching) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }

    if (printerSearched) {
        val demoDevices = listOf("Niimbot D110 · DEMO", "Xprinter XP-365B · DEMO")
        demoDevices.forEach { name ->
            OrchardCard {
                Text(name, style = MaterialTheme.typography.titleMedium)
                SecondaryButton(
                    text = if (name == printerDevice) "Подключён" else "Подключить",
                    enabled = !busy && name != printerDevice,
                    onClick = { onConnectPrinter(name) }
                )
            }
        }
    }

    ErrorBox(error)
}

@Composable
fun StorageScreen(
    data: AppData,
    onBack: () -> Unit = {}
) {
    BackLink("Ещё", onClick = onBack)
    ScreenTitle(
        title = "Данные на устройстве",
        subtitle = "Локальный демонстрационный набор"
    )
    OrchardCard {
        Detail("Сады", data.gardens.size.toString())
        Detail("Культуры", data.crops.size.toString())
        Detail("Деревья", data.gardens.sumOf { it.trees.size }.toString())
        Detail("Осмотры", data.observations.size.toString())
        Detail("Зоны полива", data.zones.size.toString())
        Detail("Затраты", data.expenses.size.toString())
    }
    InfoBox("Изменения сохраняются локально между запусками. Фото копируются в хранилище приложения. Обмена с сервером и веб-дэшбордом в этом шаблоне нет.")
    Text(
        text = "SmartOrchard · Kotlin / Jetpack Compose\nВерсия 1.0.0",
        color = OrchardMuted
    )
}

@Preview(showBackground = true)
@Composable
private fun MoreScreenPreview() {
    SmartOrchardTheme {
        ScrollableScreen {
            MoreScreen()
        }
    }
}
