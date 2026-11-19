package com.smartorchard.mobile.data.sync

import com.smartorchard.mobile.data.DemoPrinter
import com.smartorchard.mobile.data.Finance
import kotlinx.serialization.Serializable

@Serializable
data class FinanceSyncPayload(
    val gardenId: String,
    val finance: Finance
)

@Serializable
data class SettingsSyncPayload(
    val currentGardenId: String,
    val printer: DemoPrinter = DemoPrinter()
)

@Serializable
data class DeleteSchedulePayload(
    val scheduleId: String
)
