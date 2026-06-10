package com.example.hermesbridge

import com.google.gson.annotations.SerializedName

data class AgentMetadata(
    val platform: String = "android_phone_test",
    val source: String = "phone_text",
    val wearable: String = "none",
    @SerializedName("audio_transcribed_on_edge") val audioTranscribedOnEdge: Boolean = true,
    @SerializedName("speech_output_on_edge") val speechOutputOnEdge: Boolean = false,
    @SerializedName("test_mode") val testMode: Boolean = true,
    @SerializedName("turn_id") val turnId: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class AgentRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("input_type") val inputType: String = "text",
    val text: String,
    val timestamp: String,
    val metadata: AgentMetadata = AgentMetadata(),
    
    @SerializedName("event_type") val eventType: String? = null,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("pool_id") val poolId: String? = null,
    @SerializedName("visit_id") val visitId: String? = null,
    val location: ServiceLocation? = null,
    val readings: Map<String, Float?>? = null,
    val raw: Map<String, String>? = null,

    // Completion Fields (Gate F14B)
    @SerializedName("treatment_plan_confirmed") val treatmentPlanConfirmed: Boolean? = null,
    @SerializedName("service_log_ready") val serviceLogReady: Boolean? = null,
    @SerializedName("technician_notes") val technicianNotes: String? = null,
    @SerializedName("completion_source") val completionSource: String? = null,

    // History Lookup (Gate F14C)
    val date: String? = null,
    val limit: Int? = null,
    val offset: Int? = null
)

data class ServiceLocation(
    val latitude: Double,
    val longitude: Double,
    @SerializedName("accuracy_meters") val accuracyMeters: Float
)

data class AgentResponse(
    val status: String? = null,
    @SerializedName("event_type") val eventType: String? = null,
    @SerializedName("response") val response: String? = null,
    @SerializedName("response_text") val responseText: String? = null,
    @SerializedName("session_id") val sessionId: String? = null,
    val error: String? = null,
    val speak: Boolean? = null,
    val retryable: Boolean? = null,
    @SerializedName("turn_id") val turnId: String? = null,

    val user: UserProfile? = null,
    val route: RouteData? = null,

    val saved: Boolean? = null,
    @SerializedName("visit_id") val visitId: String? = null,
    @SerializedName("pool_id") val poolId: String? = null,
    @SerializedName("customer_name") val customerName: String? = null,
    @SerializedName("service_address") val serviceAddress: String? = null,
    @SerializedName("working_volume_gallons") val workingVolumeGallons: Int? = null,
    @SerializedName("ready_for_readings") val readyForReadings: Boolean? = null,
    @SerializedName("service_status") val serviceStatus: String? = null,
    @SerializedName("recommendation_status") val recommendationStatus: String? = null,
    val readings: Map<String, Float?>? = null,
    val recommendations: List<RecommendationResponse>? = null,
    @SerializedName("historical_context") val historicalContext: HistoricalChemistryContext? = null,

    // Visit Inspector Fields (Gate F14A)
    @SerializedName("service_event") val serviceEvent: ServiceEventSummary? = null,
    @SerializedName("chemical_applications") val chemicalApplications: List<ChemicalApplication>? = null,
    @SerializedName("recommendation_variance") val recommendationVariance: List<RecommendationVariance>? = null,
    @SerializedName("missing_sections") val missingSections: List<String>? = null,

    // History Lookup Fields (Gate F14C)
    @SerializedName("record_count") val recordCount: Int? = null,
    @SerializedName("has_more") val hasMore: Boolean? = null,
    val records: List<ServiceHistoryRecord>? = null
) {
    val finalResponseText: String
        get() = response ?: responseText ?: error ?: "No response text returned."
}

data class ServiceEventSummary(
    @SerializedName("arrived_at") val arrivedAt: String?,
    @SerializedName("readings_saved_at") val readingsSavedAt: String?,
    @SerializedName("completed_at") val completedAt: String?,
    @SerializedName("treatment_plan_confirmed") val treatmentPlanConfirmed: Boolean?,
    @SerializedName("service_log_ready") val serviceLogReady: Boolean?,
    @SerializedName("technician_notes") val technicianNotes: String?
)

data class ChemicalApplication(
    @SerializedName("product_id") val productId: String,
    val amount: Float,
    val unit: String,
    val appliedAt: String?
)

data class RecommendationVariance(
    @SerializedName("product_id") val productId: String,
    val recommended: Float,
    val actual: Float,
    val variance: Float
)

data class ServiceHistoryRecord(
    @SerializedName("visit_id") val visitId: String,
    @SerializedName("pool_id") val poolId: String,
    @SerializedName("customer_name") val customerName: String,
    @SerializedName("service_date") val serviceDate: String,
    @SerializedName("service_status") val serviceStatus: String,
    @SerializedName("arrived_at") val arrivedAt: String?,
    @SerializedName("readings_saved_at") val readingsSavedAt: String?,
    @SerializedName("completed_at") val completedAt: String?,
    @SerializedName("treatment_plan_confirmed") val treatmentPlanConfirmed: Boolean?,
    @SerializedName("service_log_ready") val serviceLogReady: Boolean?,
    @SerializedName("technician_notes") val technicianNotes: String?,
    @SerializedName("readings_summary") val readingsSummary: Map<String, Float?>?,
    @SerializedName("recommendation_count") val recommendationCount: Int?,
    @SerializedName("chemical_application_count") val chemicalApplicationCount: Int?,
    @SerializedName("variance_count") val varianceCount: Int?
)

data class HistoricalChemistryContext(
    @SerializedName("previous_visit_date") val previousVisitDate: String?,
    @SerializedName("previous_readings") val previousReadings: PreviousReadings?,
    @SerializedName("trend_summary") val trendSummary: List<String>?
) {
    fun getTrendsForDisplay(limit: Int = 3): List<String> {
        return trendSummary?.take(limit) ?: emptyList()
    }

    fun getMoreTrendsCount(limit: Int = 3): Int {
        val total = trendSummary?.size ?: 0
        return if (total > limit) total - limit else 0
    }

    fun getTrendsForSpeech(limit: Int = 2): String {
        val selected = trendSummary?.take(limit) ?: emptyList()
        if (selected.isEmpty()) return ""
        return "Compared with last visit, ${selected.joinToString(" ")}"
    }
}

data class PreviousReadings(
    @SerializedName("free_chlorine") val freeChlorine: Float?,
    @SerializedName("ph") val ph: Float?,
    @SerializedName("total_alkalinity") val totalAlkalinity: Float?,
    @SerializedName("calcium_hardness") val calciumHardness: Float?,
    @SerializedName("cyanuric_acid") val cyanuricAcid: Float?,
    @SerializedName("salt") val salt: Float?
)

data class UserProfile(
    @SerializedName("user_id") val userId: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("user_role") val userRole: String
)

data class RouteData(
    @SerializedName("route_id") val routeId: String,
    @SerializedName("route_name") val routeName: String,
    val pools: List<PoolRecord>
)

data class PoolRecord(
    @SerializedName("pool_id") val poolId: String,
    @SerializedName("customer_name") val customerName: String,
    @SerializedName("service_address") val serviceAddress: String,
    @SerializedName("gate_code") val gateCode: String?,
    @SerializedName("working_volume_gallons") val workingVolumeGallons: Int,
    @SerializedName("stop_order") val stopOrder: Int,
    val latitude: Double?,
    val longitude: Double?
)

data class RecommendationResponse(
    @SerializedName("product_id") val productId: String,
    val name: String?,
    @SerializedName("chemical_role") val chemicalRole: String,
    val amount: Float,
    val unit: String,
    val reason: String
)
