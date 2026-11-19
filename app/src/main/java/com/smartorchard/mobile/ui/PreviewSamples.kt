package com.smartorchard.mobile.ui

import com.smartorchard.mobile.data.*

object PreviewSamples {
    val data: AppData = SeedData.create()
    val garden: Garden = data.gardens.first()
    val crop: Crop = data.crops.first { it.id == garden.cropId }
    val tree: Tree = garden.trees.first()
    val zones: List<IrrigationZone> = data.zones.filter { it.gardenId == garden.id }
    val schedules: List<IrrigationSchedule> = data.schedules.filter { it.gardenId == garden.id }
    val expenses: List<Expense> = data.expenses.filter { it.gardenId == garden.id }
    val wateringZoneIds: Set<String> = setOfNotNull(zones.firstOrNull()?.id)
    val gardenDraft: GardenDraft = GardenDraft(cropId = crop.id)
}
