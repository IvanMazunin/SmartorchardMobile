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
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.smartorchard.mobile.services.PlantLabel
import com.smartorchard.mobile.services.PrinterStatus
import com.smartorchard.mobile.services.PairedPrinter
import com.smartorchard.mobile.services.printQrLabel
import com.smartorchard.mobile.services.renderPlantLabel
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

    InfoBox("Все данные хранятся на этом устройстве. Сервер, ИИ и оборудование полива пока заменены моками.")
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
    busy: Boolean = false,
    onPrint: (PlantLabel) -> Unit = {},
    onFail: (String) -> Unit = {}
) {
    BackLink("Карточка дерева", onClick = onBack)
    ScreenTitle(
        title = "Печать QR",
        subtitle = treeCode(crop, tree)
    )

    val payload = qrPayload(tree)
    val label = PlantLabel(garden.name, treeCode(crop, tree), tree.row, tree.place, payload)
    val bitmap = remember(label) { renderPlantLabel(label).asImageBitmap() }
    val context = LocalContext.current

    OrchardCard {
        Image(
            bitmap = bitmap,
            contentDescription = "${label.garden}, ${label.code}, ряд ${label.row}, место ${label.place}",
            modifier = Modifier.fillMaxWidth().aspectRatio(1.5f)
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
        text = "Печать на термопринтере",
        enabled = !busy,
        icon = R.drawable.ic_printer,
        onClick = { onPrint(label) }
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

}

@Composable
fun PrinterScreen(
    status: PrinterStatus,
    devices: List<PairedPrinter>,
    returnToLabel: Boolean,
    printerSearched: Boolean,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSearchPrinters: () -> Unit,
    onConnectPrinter: (PairedPrinter) -> Unit,
    onDisconnectPrinter: () -> Unit,
    onFail: (String) -> Unit
) {
    val context = LocalContext.current
    val permissions = if (Build.VERSION.SDK_INT >= 31) arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN
    ) else emptyArray()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (permissions.all { result[it] == true }) onSearchPrinters()
        else onFail("Разрешите доступ к устройствам поблизости в настройках приложения.")
    }
    BackLink(if (returnToLabel) "Печать QR" else "Ещё", onClick = onBack)
    ScreenTitle("Bluetooth-принтер", "Термопринтер TSPL · этикетка 60 × 40 мм")
    InfoBox("Сначала выполните сопряжение с принтером в настройках Bluetooth телефона.")
    status.connected?.let { device ->
        OrchardCard {
            Text("Подключён: ${device.name}")
            Text(device.address)
            SecondaryButton("Отключить", enabled = !busy, onClick = onDisconnectPrinter)
            if (returnToLabel) PrimaryButton("К этикетке", onClick = onBack)
        }
    }
    status.connecting?.let {
        Text("Подключение к ${it.name}…")
        SecondaryButton("Отменить", onClick = onDisconnectPrinter)
    }
    PrimaryButton("Показать сопряжённые устройства", R.drawable.ic_bluetooth) {
        if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) onSearchPrinters()
        else launcher.launch(permissions)
    }
    if (printerSearched && devices.isEmpty()) Text("Сопряжённых устройств нет.")
    devices.forEach { device ->
        SecondaryButton("${device.name} · ${device.address}", enabled = !busy && status.connecting == null && device != status.connected) {
            onConnectPrinter(device)
        }
    }
    ErrorBox(error ?: status.error)
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
