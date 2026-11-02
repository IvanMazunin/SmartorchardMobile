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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.smartorchard.mobile.R
import com.smartorchard.mobile.data.*
import com.smartorchard.mobile.domain.*
import com.smartorchard.mobile.services.printQrLabel
import com.smartorchard.mobile.services.qrBitmap
import com.smartorchard.mobile.state.OrchardViewModel
import com.smartorchard.mobile.state.UiState
import com.smartorchard.mobile.ui.*
import com.smartorchard.mobile.ui.theme.*

@Composable fun MoreScreen(vm: OrchardViewModel, data: AppData, ui: UiState, navController: NavController) {
    ScreenTitle("Ещё", "SmartOrchard · мобильное приложение")
    OrchardCard { Text("Иван Мазунин", style = MaterialTheme.typography.titleLarge); Text("Демонстрационный профиль · агроном", color = OrchardMuted) }
    listOf(Triple("Мои сады", R.drawable.ic_trees, Route.GARDENS), Triple("Культуры", R.drawable.ic_sprout, Route.CROPS),
        Triple("Bluetooth-принтер", R.drawable.ic_printer, Route.PRINTER), Triple("Данные на устройстве", R.drawable.ic_save, Route.STORAGE)).forEach { (text, icon, target) ->
        OrchardCard(Modifier.clickable { vm.updatePrinterReturnToLabel(false); navController.navigate(target) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OrchardIcon(icon, tint = OrchardGreen); Text(text, Modifier.weight(1f)); OrchardIcon(R.drawable.ic_chevron_right)
            }
        }
    }
    OrchardCard {
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Демо: без сети"); Text("Проверка ошибки ИИ-анализа", color = OrchardMuted, style = MaterialTheme.typography.bodyMedium) }; Switch(ui.offlineDemo, { vm.updateOfflineDemo(it) }) }
    }
    InfoBox("Все данные хранятся на этом устройстве. Сервер, ИИ, Bluetooth-принтер и оборудование полива пока заменены моками.")
}

@Composable fun ScanScreen(vm: OrchardViewModel, ui: UiState, navController: NavController) {
    ScreenTitle("Найти дерево", "Сканируйте QR на этикетке")
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result -> if (result.contents != null) vm.openQr(result.contents) }
    OrchardCard {
        Box(Modifier.fillMaxWidth().height(155.dp), contentAlignment = Alignment.Center) { OrchardIcon(R.drawable.ic_scan_line, Modifier.size(105.dp), OrchardGreen) }
        Text("Наведите камеру на QR-код дерева", color = OrchardMuted)
        PrimaryButton("Сканировать камерой", R.drawable.ic_camera) {
            scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("QR-код SmartOrchard").setBeepEnabled(false).setOrientationLocked(false))
        }
    }
    var row by rememberSaveable { mutableStateOf("") }; var place by rememberSaveable { mutableStateOf("") }
    SectionLabel("Или найдите по месту")
    Text(vm.garden.name, color = OrchardMuted)
    Field("Ряд", row, { row = it }, numeric = true); Field("Место", place, { place = it }, numeric = true)
    SecondaryButton("Открыть дерево", R.drawable.ic_search) { vm.lookup(row, place) }
    var payload by rememberSaveable { mutableStateOf("") }
    Field("Ссылка с этикетки", payload, { payload = it }, placeholder = "smartorchard://tree/UUID")
    SecondaryButton("Открыть по ссылке") { vm.openQr(payload.trim()) }
    TextButton({ vm.openQr(qrPayload(vm.tree)) }) { Text("Демо: открыть QR выбранного дерева") }
    ErrorBox(ui.error)
}

@Composable fun LabelScreen(vm: OrchardViewModel, data: AppData, ui: UiState, navController: NavController) {
    BackLink("Карточка дерева") { navController.popBackStack() }
    ScreenTitle("Печать QR", treeCode(vm.crop, vm.tree))
    val payload = qrPayload(vm.tree); val bitmap = remember(payload) { qrBitmap(payload).asImageBitmap() }
    val context = LocalContext.current
    OrchardCard {
        Text(vm.garden.name, style = MaterialTheme.typography.titleMedium)
        Image(bitmap, "QR-код ${treeCode(vm.crop, vm.tree)}", Modifier.fillMaxWidth().height(230.dp))
        Text("Ряд ${vm.tree.row} · место ${vm.tree.place}", Modifier.align(Alignment.CenterHorizontally))
        Text(payload, style = MaterialTheme.typography.bodyMedium, color = OrchardMuted)
    }
    Field("Количество этикеток · 1–12", ui.copies, { vm.updateCopies(it) }, numeric = true)
    SecondaryButton(data.printer.device ?: "Подключить Bluetooth-принтер", R.drawable.ic_bluetooth) { vm.updatePrinterReturnToLabel(true); navController.navigate(Route.PRINTER) }
    PrimaryButton("Печать на демопринтере", R.drawable.ic_printer) { vm.demoPrint() }
    SecondaryButton("Системная печать / PDF", R.drawable.ic_file_down) {
        val count = ui.copies.toIntOrNull()
        if (count == null || count !in 1..12) vm.fail("Укажите от 1 до 12 копий.")
        else runCatching { printQrLabel(context, vm.garden.name, treeCode(vm.crop, vm.tree), vm.tree.row, vm.tree.place, payload, count) }.onFailure { vm.fail(it.message ?: "Печать недоступна.") }
    }
    ErrorBox(ui.error)
    InfoBox("Bluetooth работает в деморежиме. Системная печать создаёт настоящий лист с QR-кодами; в диалоге Android можно выбрать «Сохранить как PDF».")
}

@Composable fun PrinterScreen(vm: OrchardViewModel, data: AppData, ui: UiState, navController: NavController) {
    BackLink(if (ui.printerReturnToLabel) "Печать QR" else "Ещё") { navController.popBackStack() }
    ScreenTitle("Bluetooth-принтер", "Этикетки для деревьев")
    DemoBadge("Демонстрационный адаптер")
    InfoBox("Для реальной Bluetooth-печати понадобится адаптер протокола конкретной модели. Сейчас используются виртуальные устройства.", R.drawable.ic_bluetooth)
    if (data.printer.device != null) OrchardCard {
        Text(data.printer.device, style = MaterialTheme.typography.titleLarge)
        Text("Подключён · деморежим", color = OrchardGreen)
        SecondaryButton("Отключить", enabled = !ui.busy) { vm.disconnectPrinter() }
        if (ui.printerReturnToLabel) PrimaryButton("Вернуться к этикетке") { navController.popBackStack() }
    }
    PrimaryButton(if (ui.printerSearching) "Поиск…" else "Найти демоустройства", R.drawable.ic_search, !ui.printerSearching) { vm.searchPrinters() }
    if (ui.printerSearching) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (ui.printerSearched) listOf("Niimbot D110 · DEMO", "Xprinter XP-365B · DEMO").forEach { name -> OrchardCard {
        Text(name, style = MaterialTheme.typography.titleMedium)
        SecondaryButton(if (name == data.printer.device) "Подключён" else "Подключить", enabled = !ui.busy && name != data.printer.device) { vm.connectPrinter(name) }
    } }
    ErrorBox(ui.error)
}

@Composable fun StorageScreen(vm: OrchardViewModel, data: AppData, ui: UiState, navController: NavController) {
    BackLink("Ещё") { navController.popBackStack() }
    ScreenTitle("Данные на устройстве", "Локальный демонстрационный набор")
    OrchardCard {
        Detail("Сады", data.gardens.size.toString()); Detail("Культуры", data.crops.size.toString())
        Detail("Деревья", data.gardens.sumOf { it.trees.size }.toString()); Detail("Осмотры", data.observations.size.toString())
        Detail("Зоны полива", data.zones.size.toString()); Detail("Затраты", data.expenses.size.toString())
    }
    InfoBox("Изменения сохраняются локально между запусками. Фото копируются в хранилище приложения. Обмена с сервером и веб-дэшбордом в этом шаблоне нет.")
    Text("SmartOrchard · Kotlin / Jetpack Compose\nВерсия 1.0.0-demo", color = OrchardMuted)
}
