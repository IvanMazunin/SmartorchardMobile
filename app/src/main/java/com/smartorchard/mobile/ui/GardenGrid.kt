package com.smartorchard.mobile.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartorchard.mobile.R
import com.smartorchard.mobile.data.*
import com.smartorchard.mobile.ui.theme.*

enum class GridMode { HEALTH, ZONES, EDIT_ZONE, VIEW_ZONE }

@Composable
fun GardenGrid(
    garden: Garden,
    selectedTreeId: String,
    zones: List<IrrigationZone>,
    wateringZoneIds: Set<String>,
    rowPage: Int,
    colPage: Int,
    filter: TreeStatus?,
    zoneDraft: ZoneDraft = ZoneDraft(),
    selectedZone: IrrigationZone? = null,
    mode: GridMode = GridMode.HEALTH,
    onPageChange: (row: Int, col: Int) -> Unit = { _, _ -> },
    onSelectTree: (String) -> Unit = {},
    onToggleZoneCell: (String) -> Unit = {}
) {
    val ownPaging = mode == GridMode.EDIT_ZONE || mode == GridMode.VIEW_ZONE
    var localRow by remember(garden.id) { mutableIntStateOf(0) }
    var localColumn by remember(garden.id) { mutableIntStateOf(0) }
    val rp = (if (ownPaging) localRow else rowPage).coerceIn(0, (garden.rows - 1) / 4)
    val cp = (if (ownPaging) localColumn else colPage).coerceIn(0, (garden.placesPerRow - 1) / 6)
    val r1 = rp * 4 + 1
    val r2 = minOf(r1 + 3, garden.rows)
    val p1 = cp * 6 + 1
    val p2 = minOf(p1 + 5, garden.placesPerRow)

    fun page(row: Int = rp, col: Int = cp) {
        if (ownPaging) {
            localRow = row
            localColumn = col
        } else {
            onPageChange(row, col)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ряды $r1–$r2 · места $p1–$p2",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = OrchardMuted
            )
            IconButton(
                onClick = { page(row = rp - 1) },
                enabled = rp > 0,
                modifier = Modifier.size(34.dp)
            ) {
                OrchardIcon(
                    id = R.drawable.ic_chevron_up,
                    description = stringResource(R.string.prev_rows),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = { page(row = rp + 1) },
                enabled = r2 < garden.rows,
                modifier = Modifier.size(34.dp)
            ) {
                OrchardIcon(
                    id = R.drawable.ic_chevron_down,
                    description = stringResource(R.string.next_rows),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        for (r in r1..r2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = r.toString().padStart(2, '0'),
                    modifier = Modifier.width(18.dp),
                    color = OrchardMuted,
                    fontSize = 10.sp
                )
                for (p in p1 until p1 + 6) {
                    val tree = garden.trees.find { it.row == r && it.place == p }
                    if (tree == null) {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        )
                        continue
                    }
                    val owner = zones.find { tree.id in it.treeIds }
                    val isWatering = owner != null && owner.id in wateringZoneIds
                    val blocked = mode == GridMode.EDIT_ZONE && owner != null && owner.id != zoneDraft.id
                    val picked = when (mode) {
                        GridMode.EDIT_ZONE -> tree.id in zoneDraft.treeIds
                        GridMode.VIEW_ZONE -> tree.id in (selectedZone?.treeIds ?: emptyList())
                        else -> tree.id == selectedTreeId
                    }
                    val dim = mode == GridMode.HEALTH && filter != null && tree.status != filter
                    val colors = statusColors(tree.status)
                    val background = when (mode) {
                        GridMode.HEALTH -> colors.first
                        GridMode.ZONES -> owner?.let { ZoneColors[it.color % 3] } ?: Color(0xFFF0F2EB)
                        GridMode.EDIT_ZONE -> if (picked) ZoneColors[zoneDraft.color % 3] else Color(0xFFF0F2EB)
                        GridMode.VIEW_ZONE -> if (picked) ZoneColors[(selectedZone?.color ?: 0) % 3] else Color(0xFFF0F2EB)
                    }

                    val cellDescription = buildString {
                        append("Ряд $r, место $p, ${tree.status.label}")
                        if (blocked) append(", занято другой зоной")
                        if (isWatering) append(", действует демополив")
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .background(
                                color = background.copy(alpha = if (dim || blocked) .4f else 1f),
                                shape = RoundedCornerShape(9.dp)
                            )
                            .border(
                                width = if (picked) 2.dp else 1.dp,
                                color = if (picked) OrchardGreen else OrchardLine,
                                shape = RoundedCornerShape(9.dp)
                            )
                            .clickable(enabled = !dim && !blocked && mode != GridMode.VIEW_ZONE) {
                                if (mode == GridMode.EDIT_ZONE) {
                                    onToggleZoneCell(tree.id)
                                } else {
                                    onSelectTree(tree.id)
                                }
                            }
                            .semantics {
                                contentDescription = cellDescription
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = p.toString().padStart(2, '0'),
                            fontSize = 11.sp,
                            color = if (mode == GridMode.HEALTH) {
                                colors.second.copy(alpha = if (dim) .35f else 1f)
                            } else {
                                OrchardGreen
                            }
                        )
                        if (isWatering) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 4.dp)
                                    .fillMaxWidth(.65f)
                                    .height(3.dp)
                                    .background(WaterBlue, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }

        if (garden.placesPerRow > 6) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Места в ряду",
                    modifier = Modifier.weight(1f),
                    color = OrchardMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                IconButton(
                    onClick = { page(col = cp - 1) },
                    enabled = cp > 0
                ) {
                    OrchardIcon(
                        id = R.drawable.ic_chevron_left,
                        description = stringResource(R.string.prev_places),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { page(col = cp + 1) },
                    enabled = p2 < garden.placesPerRow
                ) {
                    OrchardIcon(
                        id = R.drawable.ic_chevron_right,
                        description = stringResource(R.string.next_places),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusLegend(garden: Garden) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        TreeStatus.entries.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { status ->
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(statusColors(status).second, RoundedCornerShape(7.dp))
                        )
                        Text(
                            text = "${status.label} · ${garden.trees.count { it.status == status }}",
                            fontSize = 10.sp,
                            color = OrchardMuted
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GardenGridPreview() {
    SmartOrchardTheme {
        OrchardCard(modifier = Modifier.padding(16.dp)) {
            GardenGrid(
                garden = PreviewSamples.garden,
                selectedTreeId = PreviewSamples.tree.id,
                zones = PreviewSamples.zones,
                wateringZoneIds = PreviewSamples.wateringZoneIds,
                rowPage = 0,
                colPage = 0,
                filter = null
            )
            StatusLegend(garden = PreviewSamples.garden)
        }
    }
}
