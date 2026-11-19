package com.smartorchard.mobile.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Crop(
    val id: String,
    val name: String,
    val variety: String = "",
    val rowSpacing: String,
    val treeSpacing: String,
    val prefix: String = "СД"
)
