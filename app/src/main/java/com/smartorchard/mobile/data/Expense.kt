package com.smartorchard.mobile.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Expense(
    val id: String,
    val gardenId: String,
    val title: String,
    val category: String,
    val quantity: String,
    val unit: String,
    val price: String,
    val totalCents: Long,
    val date: String
)
