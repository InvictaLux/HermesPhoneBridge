package com.example.hermesbridge.serviceentry

import com.example.hermesbridge.PoolRecord
import com.example.hermesbridge.UserProfile

enum class ServiceFlowState {
    OffRoute,
    SyncingRoute,
    ArrivalCandidate,
    PoolConfirmation,
    ServiceRecordOpening,
    WaitingForReadings,
    ParsingReadings,
    ConfirmingReadings,
    SavingReadings,
    CalculatingTreatment,
    TreatmentReady,
    StandingBy,
    ServiceComplete
}

data class ServiceVisitState(
    val flowState: ServiceFlowState = ServiceFlowState.OffRoute,
    val userProfile: UserProfile? = null,
    val routeId: String? = null,
    val syncedPools: List<PoolRecord> = emptyList(),
    val activePool: PoolRecord? = null,
    val visitId: String? = null,
    val currentReadings: ParsedWaterTest = ParsedWaterTest(),
    val recommendations: List<ChemicalRecommendation> = emptyList(),
    val arrivalConfirmationRequired: Boolean = false,
    val candidatePools: List<PoolRecord> = emptyList(),
    val isReadyForReadings: Boolean = false,
    val backendServiceStatus: String? = null,
    val historicalContext: com.example.hermesbridge.HistoricalChemistryContext? = null
)

data class ChemicalRecommendation(
    val productId: String,
    val name: String?,
    val role: String,
    val amount: Float,
    val unit: String,
    val reason: String
)
