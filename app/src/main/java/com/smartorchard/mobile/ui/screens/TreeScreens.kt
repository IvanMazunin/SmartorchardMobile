package com.smartorchard.mobile.ui.screens

import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

@Composable fun TreeScreen(vm: OrchardViewModel, data: AppData) {
    var notes by remember { mutableStateOf(false) }; var note by rememberSaveable { mutableStateOf("") }
    BackLink("Доска сада") { vm.go(Screen.BOARD) }
    Text("ПАСПОРТ РАСТЕНИЯ", style = MaterialTheme.typography.labelSmall, color = OrchardMuted)
    ScreenTitle("Дерево ${treeCode(vm.crop, vm.tree)}", "${vm.garden.name} · ряд ${vm.tree.row} · место ${vm.tree.place}")
    StatusBadge(vm.tree.status)
    val zone = vm.zoneOf(vm.tree.id)
    if (vm.watering(zone)) InfoBox("Демо: действует полив · ${zone?.name}", R.drawable.ic_droplets)
    OrchardCard {
        Detail("Культура и сорт", vm.crop.name + if (vm.crop.variety.isNotEmpty()) " · ${vm.crop.variety}" else "")
        Detail("Год посадки", vm.garden.plantingDate.take(4)); Detail("Зона автополива", zone?.name ?: "Без зоны")
        Detail("Последний осмотр", vm.records.firstOrNull()?.createdAt?.let(::prettyTimestamp) ?: "Нет сохраненных осмотров")
    }
    PrimaryButton("Новый осмотр", R.drawable.ic_camera) { vm.beginInspection() }
    SecondaryButton("Печать QR", R.drawable.ic_printer) { vm.go(Screen.LABEL) }
    val latest = vm.records.firstOrNull()
    if (!latest?.diagnosis.isNullOrBlank()) OrchardCard {
        SectionLabel("Последний сохраненный осмотр"); Text(latest.diagnosis, style = MaterialTheme.typography.titleMedium)
        Text(latest.recommendations, color = OrchardMuted)
        TextButton({ vm.selectedObservationId = latest.id; vm.go(Screen.OBSERVATION_DETAIL) }) { Text("Открыть осмотр"); OrchardIcon(R.drawable.ic_arrow_up_right) }
    } else InfoBox("ИИ-диагностика по фото: проверьте предполагаемый диагноз и рекомендации, внесите правки и сохраните для дерева.", R.drawable.ic_scan_eye)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(!notes, { notes = false }, { Text("История") }); FilterChip(notes, { notes = true }, { Text("Заметки") }) }
    if (!notes) {
        if (vm.records.isEmpty()) Text("История осмотров пока пуста. Новый осмотр появится после сохранения.", color = OrchardMuted)
        vm.records.forEach { record -> OrchardCard(Modifier.clickable { vm.selectedObservationId = record.id; vm.go(Screen.OBSERVATION_DETAIL) }) {
            Text(prettyTimestamp(record.createdAt), color = OrchardMuted, style = MaterialTheme.typography.bodyMedium)
            Text(record.diagnosis.ifBlank { "Осмотр дерева" }, style = MaterialTheme.typography.titleMedium); StatusBadge(record.status)
        } }
    } else {
        val list = data.notes.filter { it.treeId == vm.tree.id }
        if (list.isEmpty()) Text("Заметок пока нет.", color = OrchardMuted)
        list.forEach { item -> OrchardCard { Text(item.text); Text(prettyTimestamp(item.createdAt), color = OrchardMuted, style = MaterialTheme.typography.bodyMedium) } }
        Field("Новая заметка", note, { note = it.take(2000) }, lines = 3)
        ErrorBox(vm.error)
        SecondaryButton("Добавить заметку", enabled = !vm.busy) { vm.addNote(note); if (note.isNotBlank()) note = "" }
    }
}

@Composable fun ObservationFormScreen(vm: OrchardViewModel) {
    val draft = vm.inspection ?: return
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) vm.importPhoto(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> if (success) cameraUri?.let { vm.importPhoto(Uri.parse(it)) } }
    val context = LocalContext.current
    fun launchCamera() { runCatching { val uri = vm.cameraUri(); cameraUri = uri.toString(); camera.launch(uri) }.onFailure { vm.fail("Камера недоступна. Выберите фотографию из файлов.") } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) launchCamera() else vm.fail("Разрешение на камеру не выдано. Выберите фото из файлов.") }
    BackLink("Паспорт дерева") { vm.go(Screen.TREE) }
    Text("${treeCode(vm.crop, vm.tree)} · ряд ${vm.tree.row}, место ${vm.tree.place}", color = OrchardMuted, style = MaterialTheme.typography.bodyMedium)
    ScreenTitle("Новый осмотр", "Фото и наблюдения о выбранном дереве")
    PhotoPreview(draft.photoPath, draft.samplePhoto)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { SecondaryButton("Пример фото", R.drawable.ic_image) { vm.useSamplePhoto() } }
        Box(Modifier.weight(1f)) { SecondaryButton("Загрузить", R.drawable.ic_image_plus) { picker.launch("image/*") } }
    }
    SecondaryButton("Сделать фото", R.drawable.ic_camera) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera() else permission.launch(Manifest.permission.CAMERA)
    }
    Text("JPEG / PNG до 10 МБ. Выбранная фотография сохраняется на устройстве.", color = OrchardMuted, style = MaterialTheme.typography.bodyMedium)
    Field("Наблюдение", draft.comment, { vm.inspection = draft.copy(comment = it.take(3000)); vm.clearFormError() }, lines = 3, placeholder = "Что вы заметили при осмотре?")
    DropdownField("Состояние дерева", draft.status, TreeStatus.entries.map { it to it.label }) { vm.inspection = draft.copy(status = it); vm.clearFormError() }
    Text("По умолчанию — прошлое состояние: ${draft.previousStatus.label}.", color = OrchardMuted, style = MaterialTheme.typography.bodyMedium)
    InfoBox("Демо ИИ: приложение покажет пример предполагаемого диагноза и рекомендаций. Их можно изменить перед сохранением.", R.drawable.ic_scan_eye)
    ErrorBox(vm.error)
    PrimaryButton("Отправить фото на анализ", R.drawable.ic_sparkles, !vm.busy) { vm.analyze() }
    SecondaryButton("Сохранить осмотр без ИИ", enabled = !vm.busy) { vm.saveInspection(false) }
}

@Composable fun AnalysisScreen(vm: OrchardViewModel) {
    val draft = vm.inspection ?: return
    BackLink("К осмотру") { vm.backToInspection() }
    DemoBadge("Демо ИИ")
    ScreenTitle(if (draft.phase == AnalysisPhase.PROCESSING) "Анализ фотографии" else "Результат анализа", treeCode(vm.crop, vm.tree))
    PhotoPreview(draft.photoPath, draft.samplePhoto)
    if (draft.phase == AnalysisPhase.PROCESSING) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp); Text("Анализируем фотографию…") }
        Text("Показывается демонстрационный ответ. Осмотр пока не сохранен, состояние дерева не меняется.", color = OrchardMuted)
    } else {
        Field("Предполагаемый диагноз", draft.diagnosis, { vm.inspection = draft.copy(diagnosis = it.take(500)); vm.clearFormError() }, lines = 2)
        Field("Рекомендации", draft.recommendations, { vm.inspection = draft.copy(recommendations = it.take(3000)); vm.clearFormError() }, lines = 4)
        DropdownField("Состояние дерева", draft.status, TreeStatus.entries.map { it to it.label }) { vm.inspection = draft.copy(status = it) }
        Text("Ответ ИИ не переключает состояние автоматически. По умолчанию использовано прошлое значение.", color = OrchardMuted)
        var original by remember { mutableStateOf(false) }
        TextButton({ original = !original }) { Text(if (original) "Скрыть исходный ответ ИИ" else "Исходный ответ ИИ") }
        if (original) OrchardCard { Text(draft.originalAi?.diagnosis.orEmpty(), style = MaterialTheme.typography.titleMedium); Text(draft.originalAi?.recommendations.orEmpty()) }
        ErrorBox(vm.error)
        PrimaryButton("Сохранить для дерева", R.drawable.ic_save, !vm.busy) { vm.saveInspection(true) }
    }
}

@Composable fun ObservationDetailScreen(vm: OrchardViewModel, data: AppData) {
    val record = data.observations.find { it.id == vm.selectedObservationId } ?: return
    BackLink("Паспорт дерева") { vm.openTree(record.treeId) }
    ScreenTitle("Осмотр дерева", prettyTimestamp(record.createdAt))
    StatusBadge(record.status)
    if (record.samplePhoto || record.photoPath != null) PhotoPreview(record.photoPath, record.samplePhoto)
    if (record.diagnosis.isNotBlank()) { SectionLabel("Предполагаемый диагноз"); Text(record.diagnosis, style = MaterialTheme.typography.titleLarge); SectionLabel("Рекомендации"); Text(record.recommendations) }
    if (record.comment.isNotBlank()) { SectionLabel("Наблюдение"); Text(record.comment) }
    record.originalAi?.let { ai ->
        DemoBadge("Демо ИИ · проверено садоводом")
        Text(if (record.diagnosis != ai.diagnosis || record.recommendations != ai.recommendations) "Внесены правки садовода" else "Ответ сохранен без правок", color = OrchardMuted)
        OrchardCard { SectionLabel("Исходный ответ ИИ"); Text(ai.diagnosis); Text(ai.recommendations, color = OrchardMuted) }
    }
}
