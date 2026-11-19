package com.smartorchard.mobile.domain.usecase

import com.smartorchard.mobile.data.Crop
import com.smartorchard.mobile.data.CropDraft
import com.smartorchard.mobile.data.OrchardRepository
import com.smartorchard.mobile.domain.decimal
import com.smartorchard.mobile.domain.sameCrop
import com.smartorchard.mobile.domain.uid
import com.smartorchard.mobile.domain.validate
import java.math.BigDecimal
import javax.inject.Inject

class SaveCropUseCase @Inject constructor(
    private val repository: OrchardRepository
) {
    suspend operator fun invoke(draft: CropDraft, existingCrops: List<Crop>): Crop {
        validate(draft.name.isNotBlank() && draft.name.trim().length <= 60 && draft.variety.trim().length <= 60) {
            "Укажите название культуры до 60 символов; сорт — до 60 символов."
        }
        validate(
            decimal(draft.row, 3)?.let { it > BigDecimal.ZERO } == true &&
                decimal(draft.tree, 3)?.let { it > BigDecimal.ZERO } == true
        ) {
            "Укажите положительные расстояния, до трех знаков после запятой."
        }
        val prefix = mapOf(
            "Миндаль" to "МД",
            "Яблоня" to "ЯБ",
            "Груша" to "ГР"
        )[draft.name.trim()] ?: "СД"
        val item = Crop(
            id = uid(),
            name = draft.name.trim(),
            variety = draft.variety.trim(),
            rowSpacing = draft.row.trim().replace(',', '.'),
            treeSpacing = draft.tree.trim().replace(',', '.'),
            prefix = prefix
        )
        validate(existingCrops.none { sameCrop(it, item) }) {
            "Такая культура с этим сортом и схемой посадки уже есть."
        }
        repository.addCrop(item)
        return item
    }
}
