package com.smartorchard.mobile.domain.usecase

import com.smartorchard.mobile.data.OrchardRepository
import com.smartorchard.mobile.data.TreeNote
import com.smartorchard.mobile.domain.uid
import com.smartorchard.mobile.domain.validate
import java.time.Clock
import java.time.OffsetDateTime
import javax.inject.Inject

class AddTreeNoteUseCase @Inject constructor(
    private val repository: OrchardRepository,
    private val clock: Clock
) {
    constructor(repository: OrchardRepository) : this(repository, Clock.systemDefaultZone())

    suspend operator fun invoke(treeId: String, text: String): TreeNote {
        validate(text.isNotBlank()) { "Введите текст заметки." }
        val item = TreeNote(
            id = uid(),
            treeId = treeId,
            text = text.trim(),
            createdAt = OffsetDateTime.now(clock).toString()
        )
        repository.addNote(item)
        return item
    }
}
