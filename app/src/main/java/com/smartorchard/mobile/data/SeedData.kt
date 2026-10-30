package com.smartorchard.mobile.data

import com.smartorchard.mobile.domain.seedId

object SeedData {
    fun create(): AppData {
        val crops = listOf(
            Crop(seedId("crop-almond"), "Миндаль", "Никитский", "6", "4", "МД"),
            Crop(seedId("crop-apple"), "Яблоня", "Гала", "4", "2", "ЯБ"),
            Crop(seedId("crop-pear"), "Груша", "Лада", "5", "3", "ГР")
        )
        fun garden(key: String, name: String, crop: Crop, area: String, date: String, places: Int, count: Int, finance: Finance): Garden {
            val trees = List(count) { i ->
                val status = when (i) {
                    in setOf(5, 14, 24, 33, 42, 51, 66, 73, 85, 92) -> TreeStatus.WARNING
                    in setOf(17, 36, 57, 78, 99) -> TreeStatus.DEAD
                    in setOf(9, 28, 47, 68, 89) -> TreeStatus.UNKNOWN
                    else -> TreeStatus.HEALTHY
                }
                Tree(seedId("$key-tree-$i"), i / places + 1, i % places + 1, status)
            }
            return Garden(seedId(key), name, crop.id, area, date, places, count, trees, finance)
        }
        val north = garden("north", "Северный сад", crops[0], "2.0", "2021-03-15", 10, 100, Finance(36_000_000, 90_000_000, 30_000_000))
        val apple = garden("apple", "Яблоневый сад", crops[1], "1.4", "2024-04-12", 6, 72, Finance(0, 50_000_000, 18_000_000))
        fun zone(key: String, g: Garden, name: String, color: Int, coords: List<Pair<Int, Int>>) = IrrigationZone(seedId(key), g.id, name, color,
            coords.mapNotNull { (r, p) -> g.trees.find { it.row == r && it.place == p }?.id })
        val zones = listOf(
            zone("north-a", north, "Зона А", 0, listOf(1 to 1, 1 to 2, 1 to 3, 2 to 2, 2 to 3, 2 to 4, 3 to 3, 3 to 4, 3 to 5, 4 to 4, 4 to 5, 4 to 6)),
            zone("north-b", north, "Зона Б", 1, listOf(1 to 5, 1 to 6, 2 to 5, 2 to 6, 3 to 6, 5 to 1, 5 to 2, 6 to 1, 6 to 2, 7 to 2, 7 to 3, 8 to 3)),
            zone("apple-a", apple, "Зона А", 0, listOf(1 to 1, 1 to 2, 2 to 1, 2 to 2, 2 to 3, 3 to 2, 3 to 3, 3 to 4)),
            zone("apple-b", apple, "Зона Б", 1, listOf(4 to 2, 4 to 3, 5 to 3, 5 to 4, 6 to 4, 6 to 5))
        )
        val schedules = listOf(
            IrrigationSchedule(seedId("schedule1"), north.id, zones[0].id, "2026-10-29T09:00", "2026-10-29T10:30"),
            IrrigationSchedule(seedId("schedule2"), north.id, zones[1].id, "2026-10-29T18:00", "2026-10-29T18:30"),
            IrrigationSchedule(seedId("schedule3"), apple.id, zones[2].id, "2026-10-29T09:20", "2026-10-29T10:10")
        )
        val expenses = listOf(
            Expense(seedId("e1"), north.id, "Саженцы миндаля", "Посадочный материал", "100", "шт.", "7600", 76_000_000, "2021-03-15"),
            Expense(seedId("e2"), north.id, "Капельная система", "Оборудование", "1", "компл.", "220000", 22_000_000, "2021-03-20"),
            Expense(seedId("e3"), north.id, "Удобрения", "Материалы", "700", "кг", "200", 14_000_000, "2026-04-15"),
            Expense(seedId("e4"), north.id, "Электроэнергия", "Вода и энергия", "5000", "кВт·ч", "8", 4_000_000, "2026-09-30"),
            Expense(seedId("e5"), north.id, "Агрономические услуги", "Услуги", "1", "усл.", "80000", 8_000_000, "2026-10-20"),
            Expense(seedId("e6"), apple.id, "Закладка яблоневого сада", "Посадочный материал", "1", "компл.", "540000", 54_000_000, "2024-04-12")
        )
        // The phone prototype starts with no saved inspections; AI results appear after explicit save.
        return AppData(crops = crops, gardens = listOf(north, apple), currentGardenId = north.id, zones = zones, schedules = schedules, expenses = expenses)
    }
}
