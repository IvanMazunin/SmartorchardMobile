package com.smartorchard.mobile.data.local

import com.smartorchard.mobile.data.*

// ----- Crop -----
fun CropEntity.toDomain() = Crop(id, name, variety, rowSpacing, treeSpacing, prefix)
fun Crop.toEntity() = CropEntity(id, name, variety, rowSpacing, treeSpacing, prefix)

// ----- Garden -----
fun GardenEntity.toDomain() = Garden(
    id, name, cropId, areaHa, plantingDate, placesPerRow, initialTreeCount, trees,
    Finance(revenueCents, annualRevenueCents, annualCostsCents)
)
fun Garden.toEntity() = GardenEntity(
    id, name, cropId, areaHa, plantingDate, placesPerRow, initialTreeCount, trees,
    finance.revenueCents, finance.annualRevenueCents, finance.annualCostsCents
)

// ----- Zone -----
fun ZoneEntity.toDomain() = IrrigationZone(id, gardenId, name, color, treeIds)
fun IrrigationZone.toEntity() = ZoneEntity(id, gardenId, name, color, treeIds)

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
