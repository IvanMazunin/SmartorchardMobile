package com.smartorchard.mobile.data.local

import com.smartorchard.mobile.data.*

// ----- Crop -----
fun CropEntity.toDomain() = Crop(id, name, variety, rowSpacing, treeSpacing, prefix)
fun Crop.toEntity() = CropEntity(id, name, variety, rowSpacing, treeSpacing, prefix)

// ----- Tree -----
fun TreeEntity.toDomain() = Tree(
    id = id,
    row = row,
    place = place,
    status = status
)
fun Tree.toEntity(gardenId: String, zoneId: String? = null) = TreeEntity(
    id = id,
    gardenId = gardenId,
    row = row,
    place = place,
    status = status,
    zoneId = zoneId
)

// ----- Garden -----
fun GardenEntity.toDomain(trees: List<Tree>) = Garden(
    id = id,
    name = name,
    cropId = cropId,
    areaHa = areaHa,
    plantingDate = plantingDate,
    placesPerRow = placesPerRow,
    initialTreeCount = initialTreeCount,
    trees = trees,
    finance = Finance(revenueCents, annualRevenueCents, annualCostsCents)
)
fun Garden.toEntity() = GardenEntity(
    id = id,
    name = name,
    cropId = cropId,
    areaHa = areaHa,
    plantingDate = plantingDate,
    placesPerRow = placesPerRow,
    initialTreeCount = initialTreeCount,
    revenueCents = finance.revenueCents,
    annualRevenueCents = finance.annualRevenueCents,
    annualCostsCents = finance.annualCostsCents
)

// ----- Zone -----
fun ZoneEntity.toDomain(treeIds: List<String>) = IrrigationZone(
    id = id,
    gardenId = gardenId,
    name = name,
    color = color,
    treeIds = treeIds
)
fun IrrigationZone.toEntity() = ZoneEntity(
    id = id,
    gardenId = gardenId,
    name = name,
    color = color
)

// ----- Schedule -----
fun ScheduleEntity.toDomain() = IrrigationSchedule(id, gardenId, zoneId, start, end)
fun IrrigationSchedule.toEntity() = ScheduleEntity(id, gardenId, zoneId, start, end)

// ----- Expense -----
fun ExpenseEntity.toDomain() = Expense(id, gardenId, title, category, quantity, unit, price, totalCents, date)
fun Expense.toEntity() = ExpenseEntity(id, gardenId, title, category, quantity, unit, price, totalCents, date)

// ----- Observation -----
fun ObservationEntity.toDomain() = Observation(
    id, gardenId, treeId, createdAt, status, comment, diagnosis,
    recommendations, photoPath, samplePhoto, originalAi
)
fun Observation.toEntity() = ObservationEntity(
    id, gardenId, treeId, createdAt, status, comment, diagnosis,
    recommendations, photoPath, samplePhoto, originalAi
)

// ----- Note -----
fun NoteEntity.toDomain() = TreeNote(id, treeId, text, createdAt)
fun TreeNote.toEntity() = NoteEntity(id, treeId, text, createdAt)
