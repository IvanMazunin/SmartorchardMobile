package com.smartorchard.mobile

import com.smartorchard.mobile.data.*
import com.smartorchard.mobile.domain.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    private val crop = Crop("crop", "Миндаль", "Никитский", "6", "4")

    @Test
    fun densityFloorsAndPartialRowHasNoPhantomTrees() {
        assertEquals(833, plantingEstimate("2", crop)?.count)
        val garden = newGarden("Тест", crop, "0.025", DEMO_DATE, 6)
        assertEquals(10, garden.trees.size)
        assertEquals(2, garden.rows)
        assertEquals(4, garden.trees.last().place)
        assertEquals(10, garden.trees.map { it.id }.distinct().size)
        assertTrue(garden.trees.all { it.status == TreeStatus.UNKNOWN })
        assertEquals(crop.id, garden.cropId)
    }

    @Test
    fun invalidAndExcessivePrecisionAreasAreRejected() {
        listOf("0", "-1", "abc", "1.00001", "1e2", "99999999999999999999").forEach { assertNull(plantingEstimate(it, crop)) }
        assertEquals(416, plantingEstimate("1,0", crop)?.count)
        assertNull(plantingEstimate("1", crop.copy(rowSpacing = "0")))
    }

    @Test
    fun cropDuplicatesNormalizeNamesAndDecimalNotation() {
        assertTrue(sameCrop(crop, crop.copy(id = "other", name = "  миндаль ", variety = "НИКИТСКИЙ", rowSpacing = "6.000", treeSpacing = "4,0")))
        assertFalse(sameCrop(crop, crop.copy(variety = "Другой")))
        assertFalse(sameCrop(crop, crop.copy(treeSpacing = "5")))
    }

    @Test
    fun costsUseExactDecimalAndHalfUpRounding() {
        assertEquals(101L, expenseCents("0.5", "2.01"))
        assertEquals(3000L, expenseCents("3", "10"))
        assertEquals(0L, expenseCents("1", "0"))
        assertNull(expenseCents("0", "10")); assertNull(expenseCents("1", "-1"))
    }

    @Test
    fun adjoiningIntervalsDoNotOverlap() {
        val interval = IrrigationSchedule("i", "g", "z", "2026-10-29T09:00", "2026-10-29T10:00")
        assertFalse(overlaps("2026-10-29T10:00", "2026-10-29T11:00", interval))
        assertFalse(overlaps("2026-10-29T08:00", "2026-10-29T09:00", interval))
        assertTrue(overlaps("2026-10-29T09:30", "2026-10-29T10:30", interval))
    }

    @Test
    fun qrIdentifiersAreStableAndStrict() {
        val tree = Tree(seedId("tree"), 2, 4)
        assertEquals(tree.id, parseQr(qrPayload(tree)))
        assertNull(parseQr("https://example.com/")); assertNull(parseQr("smartorchard://tree/1-2-3-4-5"))
        assertNull(parseQr(qrPayload(tree) + "/extra"))
    }

    @Test
    fun seedZonesHaveValidExclusiveMembership() {
        val data = SeedData.create()
        assertEquals(data, SeedData.create())
        assertEquals(172, data.gardens.sumOf { it.trees.size })
        val assigned = mutableSetOf<String>()
        data.zones.forEach { zone ->
            val garden = data.gardens.first { it.id == zone.gardenId }
            zone.treeIds.forEach { id -> assertTrue(garden.trees.any { it.id == id }); assertTrue(assigned.add(id)) }
        }
    }

    @Test
    fun savedObservationRetainsEditedAndOriginalResultSeparately() {
        val ai = AiResult("Исходный диагноз", "Исходные рекомендации")
        val observation = Observation("o", "g", "t", "2026-10-29T10:00:00Z", TreeStatus.WARNING,
            diagnosis = "Уточненный диагноз", recommendations = "Правки", originalAi = ai)
        val saved = Json.decodeFromString<Observation>(Json.encodeToString(observation))
        assertEquals(observation, saved); assertEquals(ai, saved.originalAi)
        assertNotEquals(saved.diagnosis, saved.originalAi?.diagnosis)
    }

    @Test
    fun scheduleDatesAreCanonicalAndReal() {
        assertTrue(validDateTime("2026-10-29T09:41"))
        assertFalse(validDateTime("2026-02-30T09:00"))
        assertFalse(validDateTime("2026-10-29T25:00"))
        assertFalse(validDateTime("2026-10-29T09:00:30"))
    }

    @Test
    fun paybackHandlesNonpositiveNetAndCoveredCosts() {
        assertEquals("Не окупается", payback(10000, Finance(0, 1000, 1000)))
        assertEquals("Затраты покрыты", payback(10000, Finance(10000, 0, 0)))
        assertEquals("Нет затрат", payback(0, Finance()))
    }
}
