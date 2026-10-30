package com.smartorchard.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartorchard.mobile.R
import com.smartorchard.mobile.data.*
import com.smartorchard.mobile.state.OrchardViewModel
import com.smartorchard.mobile.ui.theme.*

enum class GridMode { HEALTH, ZONES, EDIT_ZONE, VIEW_ZONE }

@Composable fun GardenGrid(vm: OrchardViewModel, data: AppData, mode: GridMode = GridMode.HEALTH) {
    val garden = vm.garden
    val ownPaging = mode == GridMode.EDIT_ZONE || mode == GridMode.VIEW_ZONE
    var localRow by remember(garden.id) { mutableStateOf(0) }
    var localColumn by remember(garden.id) { mutableStateOf(0) }
    val rp = (if (ownPaging) localRow else vm.rowPage).coerceIn(0, (garden.rows - 1) / 4)
    val cp = (if (ownPaging) localColumn else vm.colPage).coerceIn(0, (garden.placesPerRow - 1) / 6)
    val r1 = rp * 4 + 1; val r2 = minOf(r1 + 3, garden.rows)
    val p1 = cp * 6 + 1; val p2 = minOf(p1 + 5, garden.placesPerRow)
    fun page(row: Int = rp, col: Int = cp) { if (ownPaging) { localRow = row; localColumn = col } else { vm.rowPage = row; vm.colPage = col } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Ряды $r1–$r2 · места $p1–$p2", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = OrchardMuted)
            IconButton({ page(row = rp - 1) }, enabled = rp > 0, modifier = Modifier.size(34.dp)) { OrchardIcon(R.drawable.ic_chevron_up, description = "Предыдущие ряды") }
            IconButton({ page(row = rp + 1) }, enabled = r2 < garden.rows, modifier = Modifier.size(34.dp)) { OrchardIcon(R.drawable.ic_chevron_down, description = "Следующие ряды") }
        }
        for (r in r1..r2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.toString().padStart(2, '0'), Modifier.width(18.dp), color = OrchardMuted, fontSize = 10.sp)
                for (p in p1 until p1 + 6) {
                    val tree = garden.trees.find { it.row == r && it.place == p }
                    if (tree == null) { Spacer(Modifier.weight(1f).height(42.dp)); continue }
                    val owner = vm.zoneOf(tree.id)
                    val blocked = mode == GridMode.EDIT_ZONE && owner != null && owner.id != vm.zoneDraft.id
                    val picked = when (mode) {
                        GridMode.EDIT_ZONE -> tree.id in vm.zoneDraft.treeIds
                        GridMode.VIEW_ZONE -> tree.id in (vm.currentZone()?.treeIds ?: emptyList())
                        else -> tree.id == vm.tree.id
                    }
                    val dim = mode == GridMode.HEALTH && vm.filter != null && tree.status != vm.filter
                    val colors = statusColors(tree.status)
                    val background = when (mode) {
                        GridMode.HEALTH -> colors.first
                        GridMode.ZONES -> owner?.let { ZoneColors[it.color % 3] } ?: Color(0xFFF0F2EB)
                        GridMode.EDIT_ZONE -> if (picked) ZoneColors[vm.zoneDraft.color % 3] else Color(0xFFF0F2EB)
                        GridMode.VIEW_ZONE -> if (picked) ZoneColors[(vm.currentZone()?.color ?: 0) % 3] else Color(0xFFF0F2EB)
                    }
                    Box(Modifier.weight(1f).height(42.dp)
                        .background(background.copy(alpha = if (dim || blocked) .4f else 1f), RoundedCornerShape(9.dp))
                        .border(if (picked) 2.dp else 1.dp, if (picked) OrchardGreen else OrchardLine, RoundedCornerShape(9.dp))
                        .clickable(enabled = !dim && !blocked && mode != GridMode.VIEW_ZONE) { if (mode == GridMode.EDIT_ZONE) vm.toggleZoneCell(tree.id) else vm.selectedTreeId = tree.id }
                        .semantics { contentDescription = "Ряд $r, место $p, ${tree.status.label}${if (blocked) ", занято другой зоной" else ""}${if (vm.watering(owner)) ", действует демополив" else ""}" }, contentAlignment = Alignment.Center) {
                        Text(p.toString().padStart(2, '0'), fontSize = 11.sp, color = if (mode == GridMode.HEALTH) colors.second.copy(alpha = if (dim) .35f else 1f) else OrchardGreen)
                        if (vm.watering(owner)) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).fillMaxWidth(.65f).height(3.dp).background(WaterBlue, RoundedCornerShape(2.dp)))
                    }
                }
            }
        }
        if (garden.placesPerRow > 6) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Места в ряду", Modifier.weight(1f), color = OrchardMuted, style = MaterialTheme.typography.bodyMedium)
            IconButton({ page(col = cp - 1) }, enabled = cp > 0) { OrchardIcon(R.drawable.ic_chevron_left, description = "Предыдущие места") }
            IconButton({ page(col = cp + 1) }, enabled = p2 < garden.placesPerRow) { OrchardIcon(R.drawable.ic_chevron_right, description = "Следующие места") }
        }
    }
}

@Composable fun StatusLegend(garden: Garden) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        TreeStatus.entries.chunked(2).forEach { pair -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { status -> Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(7.dp).background(statusColors(status).second, RoundedCornerShape(7.dp)))
                Text("${status.label} · ${garden.trees.count { it.status == status }}", fontSize = 10.sp, color = OrchardMuted)
            } }
        } }
    }
}
