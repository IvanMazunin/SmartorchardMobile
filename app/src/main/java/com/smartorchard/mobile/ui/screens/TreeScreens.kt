package com.smartorchard.mobile.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.smartorchard.mobile.R
import com.smartorchard.mobile.data.*
import com.smartorchard.mobile.domain.*
import com.smartorchard.mobile.ui.*
import com.smartorchard.mobile.ui.theme.*

@Composable
fun TreeScreen(
    garden: Garden,
    crop: Crop,
    tree: Tree,
    zone: IrrigationZone?,
    isWatering: Boolean,
    records: List<Observation>,
    treeNotes: List<TreeNote>,
    busy: Boolean = false,
    error: String? = null,
    onBack: () -> Unit = {},
    onBeginInspection: () -> Unit = {},
    onOpenLabel: () -> Unit = {},
    onOpenObservationDetail: (String) -> Unit = {},
    onAddNote: (String) -> Unit = {}
) {
    var notes by remember { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }

    LazyScreen {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BackLink("Доска сада", onClick = onBack)
                Text(
                    text = "ПАСПОРТ РАСТЕНИЯ",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrchardMuted
                )
                ScreenTitle(
                    title = "Дерево ${treeCode(crop, tree)}",
                    subtitle = "${garden.name} · ряд ${tree.row} · место ${tree.place}"
                )
                StatusBadge(tree.status)

                if (isWatering) {
                    InfoBox(
                        text = "Демо: действует полив · ${zone?.name}",
                        icon = R.drawable.ic_droplets
                    )
                }

                OrchardCard {
                    val variety = if (crop.variety.isNotEmpty()) " · ${crop.variety}" else ""
                    Detail("Культура и сорт", crop.name + variety)
                    Detail("Год посадки", garden.plantingDate.take(4))
                    Detail("Зона автополива", zone?.name ?: "Без зоны")
                    Detail(
                        label = "Последний осмотр",
                        value = records.firstOrNull()?.createdAt?.let(::prettyTimestamp)
                            ?: "Нет сохраненных осмотров"
                    )
                }

                PrimaryButton(
                    text = "Новый осмотр",
                    icon = R.drawable.ic_camera,
                    onClick = onBeginInspection
                )
                SecondaryButton(
                    text = "Печать QR",
                    icon = R.drawable.ic_printer,
                    onClick = onOpenLabel
                )

                val latest = records.firstOrNull()
                if (latest != null && latest.diagnosis.isNotBlank()) {
                    OrchardCard {
                        SectionLabel("Последний сохраненный осмотр")
                        Text(latest.diagnosis, style = MaterialTheme.typography.titleMedium)
                        Text(latest.recommendations, color = OrchardMuted)
                        TextButton(onClick = { onOpenObservationDetail(latest.id) }) {
                            Text("Открыть осмотр")
                            OrchardIcon(id = R.drawable.ic_arrow_up_right, modifier = Modifier.size(20.dp))
                        }
                    }
                } else {
                    InfoBox(
                        text = "ИИ-диагностика по фото: проверьте предполагаемый диагноз и рекомендации, внесите правки и сохраните для дерева.",
                        icon = R.drawable.ic_scan_eye
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !notes,
                        onClick = { notes = false },
                        label = { Text("История") }
                    )
                    FilterChip(
                        selected = notes,
                        onClick = { notes = true },
                        label = { Text("Заметки") }
                    )
                }
            }
        }

        if (!notes) {
            if (records.isEmpty()) {
                item(key = "empty_records") {
                    Text(
                        text = "История осмотров пока пуста. Новый осмотр появится после сохранения.",
                        color = OrchardMuted
                    )
                }
            }
            items(items = records, key = { it.id }) { record ->
                OrchardCard(modifier = Modifier.clickable { onOpenObservationDetail(record.id) }) {
                    Text(
                        text = prettyTimestamp(record.createdAt),
                        color = OrchardMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = record.diagnosis.ifBlank { "Осмотр дерева" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    StatusBadge(record.status)
                }
            }
        } else {
            if (treeNotes.isEmpty()) {
                item(key = "empty_notes") {
                    Text("Заметок пока нет.", color = OrchardMuted)
                }
            }
            items(items = treeNotes, key = { it.id }) { item ->
                OrchardCard {
                    Text(item.text)
                    Text(
                        text = prettyTimestamp(item.createdAt),
                        color = OrchardMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            item(key = "note_form") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Field(
                        label = "Новая заметка",
                        value = note,
                        onChange = { note = it.take(2000) },
                        lines = 3
                    )
                    ErrorBox(error)
                    SecondaryButton(
                        text = "Добавить заметку",
                        enabled = !busy,
                        onClick = {
                            onAddNote(note)
                            if (note.isNotBlank()) note = ""
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ObservationFormScreen(
    crop: Crop,
    tree: Tree,
    draft: InspectionDraft?,
    busy: Boolean = false,
    error: String? = null,
    onBack: () -> Unit = {},
    onUseSamplePhoto: () -> Unit = {},
    onProvideCameraUri: () -> Uri = { Uri.EMPTY },
    onCameraCaptured: (Boolean) -> Unit = {},
    onImportPhoto: (Uri) -> Unit = {},
    onFail: (String) -> Unit = {},
    onUpdateDraft: (InspectionDraft) -> Unit = {},
    onAnalyze: () -> Unit = {},
    onSaveWithoutAi: () -> Unit = {}
) {
    if (draft == null) return

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onImportPhoto(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        onCameraCaptured(success)
    }
    val context = LocalContext.current

    fun launchCamera() {
        runCatching {
            val uri = onProvideCameraUri()
            camera.launch(uri)
        }.onFailure {
            onFail("Камера недоступна. Выберите фотографию из файлов.")
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            onFail("Разрешение на камеру не выдано. Выберите фото из файлов.")
        }
    }

    BackLink("Паспорт дерева", onClick = onBack)
    Text(
        text = "${treeCode(crop, tree)} · ряд ${tree.row}, место ${tree.place}",
        color = OrchardMuted,
        style = MaterialTheme.typography.bodyMedium
    )
    ScreenTitle(
        title = "Новый осмотр",
        subtitle = "Фото и наблюдения о выбранном дереве"
    )
    PhotoPreview(draft.photoPath, draft.samplePhoto)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            SecondaryButton(
                text = "Пример фото",
                icon = R.drawable.ic_image,
                onClick = onUseSamplePhoto
            )
        }
        Box(Modifier.weight(1f)) {
            SecondaryButton(
                text = "Загрузить",
                icon = R.drawable.ic_image_plus,
                onClick = { picker.launch("image/*") }
            )
        }
    }

    SecondaryButton(
        text = "Сделать фото",
        icon = R.drawable.ic_camera,
        onClick = {
            val hasCameraPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (hasCameraPermission) {
                launchCamera()
            } else {
                permission.launch(Manifest.permission.CAMERA)
            }
        }
    )

    Text(
        text = "JPEG / PNG до 10 МБ. Выбранная фотография сохраняется на устройстве.",
        color = OrchardMuted,
        style = MaterialTheme.typography.bodyMedium
    )
    Field(
        label = "Наблюдение",
        value = draft.comment,
        onChange = { onUpdateDraft(draft.copy(comment = it.take(3000))) },
        lines = 3,
        placeholder = "Что вы заметили при осмотре?"
    )
    DropdownField(
        label = "Состояние дерева",
        value = draft.status,
        options = TreeStatus.entries.map { it to it.label },
        onSelect = { onUpdateDraft(draft.copy(status = it)) }
    )
    Text(
        text = "По умолчанию — прошлое состояние: ${draft.previousStatus.label}.",
        color = OrchardMuted,
        style = MaterialTheme.typography.bodyMedium
    )
    InfoBox(
        text = "Демо ИИ: приложение покажет пример предполагаемого диагноза и рекомендаций. Их можно изменить перед сохранением.",
        icon = R.drawable.ic_scan_eye
    )
    ErrorBox(error)
    PrimaryButton(
        text = "Отправить фото на анализ",
        icon = R.drawable.ic_sparkles,
        enabled = !busy,
        onClick = onAnalyze
    )
    SecondaryButton(
        text = "Сохранить осмотр без ИИ",
        enabled = !busy,
        onClick = onSaveWithoutAi
    )
}

@Composable
fun AnalysisScreen(
    crop: Crop,
    tree: Tree,
    draft: InspectionDraft?,
    busy: Boolean = false,
    error: String? = null,
    onBackToInspection: () -> Unit = {},
    onUpdateDraft: (InspectionDraft) -> Unit = {},
    onSaveWithAi: () -> Unit = {}
) {
    if (draft == null) return

    BackLink("К осмотру", onClick = onBackToInspection)
    DemoBadge("Демо ИИ")
    ScreenTitle(
        title = if (draft.phase == AnalysisPhase.PROCESSING) {
            "Анализ фотографии"
        } else {
            "Результат анализа"
        },
        subtitle = treeCode(crop, tree)
    )
    PhotoPreview(draft.photoPath, draft.samplePhoto)

    if (draft.phase == AnalysisPhase.PROCESSING) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            Text("Анализируем фотографию…")
        }
        Text(
            text = "Показывается демонстрационный ответ. Осмотр пока не сохранен, состояние дерева не меняется.",
            color = OrchardMuted
        )
    } else {
        Field(
            label = "Предполагаемый диагноз",
            value = draft.diagnosis,
            onChange = { onUpdateDraft(draft.copy(diagnosis = it.take(500))) },
            lines = 2
        )
        Field(
            label = "Рекомендации",
            value = draft.recommendations,
            onChange = { onUpdateDraft(draft.copy(recommendations = it.take(3000))) },
            lines = 4
        )
        DropdownField(
            label = "Состояние дерева",
            value = draft.status,
            options = TreeStatus.entries.map { it to it.label },
            onSelect = { onUpdateDraft(draft.copy(status = it)) }
        )
        Text(
            text = "Ответ ИИ не переключает состояние автоматически. По умолчанию использовано прошлое значение.",
            color = OrchardMuted
        )

        var original by remember { mutableStateOf(false) }
        TextButton(onClick = { original = !original }) {
            Text(if (original) "Скрыть исходный ответ ИИ" else "Исходный ответ ИИ")
        }
        if (original) {
            OrchardCard {
                Text(
                    text = draft.originalAi?.diagnosis.orEmpty(),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(draft.originalAi?.recommendations.orEmpty())
            }
        }

        ErrorBox(error)
        PrimaryButton(
            text = "Сохранить для дерева",
            icon = R.drawable.ic_save,
            enabled = !busy,
            onClick = onSaveWithAi
        )
    }
}

@Composable
fun ObservationDetailScreen(
    record: Observation?,
    onBackToTree: (String) -> Unit = {}
) {
    if (record == null) return

    BackLink("Паспорт дерева", onClick = { onBackToTree(record.treeId) })
    ScreenTitle(
        title = "Осмотр дерева",
        subtitle = prettyTimestamp(record.createdAt)
    )
    StatusBadge(record.status)

    if (record.samplePhoto || record.photoPath != null) {
        PhotoPreview(record.photoPath, record.samplePhoto)
    }

    if (record.diagnosis.isNotBlank()) {
        SectionLabel("Предполагаемый диагноз")
        Text(record.diagnosis, style = MaterialTheme.typography.titleLarge)
        SectionLabel("Рекомендации")
        Text(record.recommendations)
    }

    if (record.comment.isNotBlank()) {
        SectionLabel("Наблюдение")
        Text(record.comment)
    }

    record.originalAi?.let { ai ->
        DemoBadge("Демо ИИ · проверено садоводом")
        val edited = record.diagnosis != ai.diagnosis ||
            record.recommendations != ai.recommendations
        Text(
            text = if (edited) "Внесены правки садовода" else "Ответ сохранен без правок",
            color = OrchardMuted
        )
        OrchardCard {
            SectionLabel("Исходный ответ ИИ")
            Text(ai.diagnosis)
            Text(ai.recommendations, color = OrchardMuted)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TreeScreenPreview() {
    val zone = PreviewSamples.zones.find { PreviewSamples.tree.id in it.treeIds }
    SmartOrchardTheme {
        TreeScreen(
            garden = PreviewSamples.garden,
            crop = PreviewSamples.crop,
            tree = PreviewSamples.tree,
            zone = zone,
            isWatering = zone != null && zone.id in PreviewSamples.wateringZoneIds,
            records = PreviewSamples.data.observations
                .filter { it.treeId == PreviewSamples.tree.id },
            treeNotes = PreviewSamples.data.notes
                .filter { it.treeId == PreviewSamples.tree.id }
        )
    }
}
