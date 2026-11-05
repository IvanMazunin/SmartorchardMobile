package com.smartorchard.mobile.network.api

import com.smartorchard.mobile.data.*
import com.smartorchard.mobile.network.model.*
import retrofit2.http.*

interface OrchardApi {

    // ----- Gardens -----
    @GET("api/gardens")
    suspend fun getGardens(): ApiResponse<List<Garden>>

    @POST("api/gardens")
    suspend fun createGarden(@Body request: CreateGardenRequest): ApiResponse<Garden>

    @PUT("api/gardens/{id}/finance")
    suspend fun updateGardenFinance(@Path("id") gardenId: String, @Body request: UpdateFinanceRequest): ApiResponse<Garden>

    // ----- Crops -----
    @GET("api/crops")
    suspend fun getCrops(): ApiResponse<List<Crop>>

    @POST("api/crops")
    suspend fun createCrop(@Body request: CreateCropRequest): ApiResponse<Crop>

    // ----- Observations -----
    @GET("api/trees/{treeId}/observations")
    suspend fun getObservations(@Path("treeId") treeId: String): ApiResponse<List<Observation>>

    @POST("api/observations")
    suspend fun createObservation(@Body request: CreateObservationRequest): ApiResponse<Observation>

    // ----- Diagnosis -----
    @POST("api/diagnosis/analyze")
    suspend fun analyzeDiagnosis(@Body request: DiagnosisRequest): ApiResponse<AiResult>

    // ----- Irrigation Zones -----
    @GET("api/gardens/{gardenId}/zones")
    suspend fun getZones(@Path("gardenId") gardenId: String): ApiResponse<List<IrrigationZone>>

    @POST("api/gardens/{gardenId}/zones")
    suspend fun createOrUpdateZone(@Path("gardenId") gardenId: String, @Body request: CreateZoneRequest): ApiResponse<IrrigationZone>

    // ----- Irrigation Schedules -----
    @GET("api/gardens/{gardenId}/schedules")
    suspend fun getSchedules(@Path("gardenId") gardenId: String): ApiResponse<List<IrrigationSchedule>>

    @POST("api/gardens/{gardenId}/schedules")
    suspend fun createSchedule(@Path("gardenId") gardenId: String, @Body request: CreateScheduleRequest): ApiResponse<IrrigationSchedule>

    @DELETE("api/schedules/{id}")
    suspend fun deleteSchedule(@Path("id") scheduleId: String)

    // ----- Expenses -----
    @GET("api/gardens/{gardenId}/expenses")
    suspend fun getExpenses(@Path("gardenId") gardenId: String): ApiResponse<List<Expense>>

    @POST("api/gardens/{gardenId}/expenses")
    suspend fun createExpense(@Path("gardenId") gardenId: String, @Body request: CreateExpenseRequest): ApiResponse<Expense>

    // ----- Notes -----
    @GET("api/trees/{treeId}/notes")
    suspend fun getNotes(@Path("treeId") treeId: String): ApiResponse<List<TreeNote>>

    @POST("api/trees/{treeId}/notes")
    suspend fun createNote(@Path("treeId") treeId: String, @Body request: CreateNoteRequest): ApiResponse<TreeNote>

    // ----- User Settings -----
    @GET("api/user/settings")
    suspend fun getUserSettings(): ApiResponse<UserSettingsResponse>

    @PUT("api/user/settings")
    suspend fun updateUserSettings(@Body settings: UserSettingsResponse): ApiResponse<UserSettingsResponse>
}
