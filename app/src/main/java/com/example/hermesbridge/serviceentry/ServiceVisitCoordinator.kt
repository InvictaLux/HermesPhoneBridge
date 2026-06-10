package com.example.hermesbridge.serviceentry

import android.util.Log
import com.example.hermesbridge.AgentRepository
import com.example.hermesbridge.AgentRequest
import com.example.hermesbridge.BridgeController
import com.example.hermesbridge.PoolRecord
import com.example.hermesbridge.ServiceLocation
import com.example.hermesbridge.location.PoolLocationManager
import com.example.hermesbridge.location.PoolLocationStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.*

class ServiceVisitCoordinator(
    private val scope: CoroutineScope,
    private val controller: BridgeController,
    private val repository: AgentRepository,
    private val locationManager: PoolLocationManager
) {
    private val _state = MutableStateFlow(ServiceVisitState())
    val state: StateFlow<ServiceVisitState> = _state.asStateFlow()

    private val parser = SpokenWaterTestParser()
    private val confirmation = ReadingConfirmation()

    init {
        scope.launch {
            state.collect { 
                controller.updateServiceVisit(it)
            }
        }
        
        scope.launch {
            locationManager.status.collect { status ->
                if (status is PoolLocationStatus.Success) {
                    processLocationMatch(status.latitude, status.longitude, status.accuracy)
                }
            }
        }
    }

    fun syncRoute() {
        _state.update { it.copy(flowState = ServiceFlowState.SyncingRoute) }
        controller.updateMetaDatMessage("Syncing route...")
        
        scope.launch {
            val request = AgentRequest(
                deviceId = controller.uiState.value.deviceId,
                sessionId = UUID.randomUUID().toString(),
                text = "Sync my route",
                timestamp = getIsoTimestamp(),
                eventType = "sync_route",
                userId = "user_00001"
            )

            val response = repository.sendMessage(controller.uiState.value.apiUrl, request)
            if (response.status == "ok" && response.route != null) {
                _state.update { it.copy(
                    flowState = ServiceFlowState.OffRoute,
                    userProfile = response.user,
                    routeId = response.route.routeId,
                    syncedPools = response.route.pools.sortedBy { p -> p.stopOrder },
                    treatmentPlanConfirmed = false,
                    serviceLogReady = false
                )}
                controller.updateMetaDatMessage("Route synced: ${response.route.routeName}")
                if (response.speak == true) {
                    controller.speakResponse(response.finalResponseText)
                }
            } else {
                _state.update { it.copy(flowState = ServiceFlowState.OffRoute) }
                controller.updateMetaDatMessage("Could not sync route: ${response.error}")
            }
        }
    }

    fun startLocationDetection() {
        if (_state.value.syncedPools.isEmpty()) {
            controller.speakResponse("Please sync your route first.")
            return
        }
        scope.launch {
            locationManager.getCurrentLocation()
        }
    }

    fun simulateMcCulloughArrival() {
        processLocationMatch(34.0522, -118.2437, 10f)
    }

    fun confirmTreatmentPlan() {
        _state.update { it.copy(treatmentPlanConfirmed = true) }
    }

    fun updateTechnicianNotes(notes: String) {
        _state.update { it.copy(technicianNotes = notes) }
    }

    fun submitServiceCompletion() {
        val currentState = _state.value
        if (currentState.visitId == null) {
            _state.update { it.copy(completionError = "Cannot complete: Missing Visit ID") }
            return
        }

        _state.update { it.copy(completionLoading = true, completionError = null) }

        scope.launch {
            val request = AgentRequest(
                deviceId = controller.uiState.value.deviceId,
                sessionId = controller.uiState.value.sessionId,
                text = "service_completion",
                timestamp = getIsoTimestamp(),
                eventType = "service_completion",
                visitId = currentState.visitId,
                poolId = currentState.activePool?.poolId,
                userId = "user_00001",
                treatmentPlanConfirmed = true,
                serviceLogReady = true,
                technicianNotes = currentState.technicianNotes,
                completionSource = "frontend"
            )

            val response = repository.sendMessage(controller.uiState.value.apiUrl, request)
            if (response.status == "ok" && response.saved == true) {
                _state.update { it.copy(
                    flowState = ServiceFlowState.ServiceComplete,
                    completionLoading = false,
                    completedAt = response.serviceEvent?.completedAt ?: getIsoTimestamp(),
                    backendServiceStatus = "completed"
                )}
                if (response.speak == true) {
                    controller.speakResponse(response.finalResponseText)
                }
            } else {
                _state.update { it.copy(
                    completionLoading = false,
                    completionError = response.error ?: "Failed to complete service."
                )}
            }
        }
    }

    fun markServiceLogReady() {
        _state.update { it.copy(serviceLogReady = true) }
    }

    private fun processLocationMatch(lat: Double, lon: Double, accuracy: Float) {
        if (_state.value.flowState != ServiceFlowState.OffRoute) return

        val thresholdFeet = 1000.0
        val nearby = _state.value.syncedPools.filter { pool ->
            if (pool.latitude == null || pool.longitude == null) return@filter false
            calculateDistanceFeet(lat, lon, pool.latitude, pool.longitude) <= thresholdFeet
        }

        if (nearby.isEmpty()) {
            controller.updateMetaDatMessage("No assigned pool nearby (1000ft).")
            return
        }

        if (nearby.size == 1) {
            val pool = nearby.first()
            _state.update { it.copy(
                flowState = ServiceFlowState.PoolConfirmation,
                activePool = pool,
                arrivalConfirmationRequired = true,
                candidatePools = nearby
            )}
            controller.speakResponse("Are you arriving at ${pool.customerName}?")
        } else {
            _state.update { it.copy(
                flowState = ServiceFlowState.PoolConfirmation,
                candidatePools = nearby,
                arrivalConfirmationRequired = true
            )}
            controller.speakResponse("Multiple pools nearby. Please select one.")
        }
    }

    fun onPoolSelected(pool: PoolRecord) {
        _state.update { it.copy(
            flowState = ServiceFlowState.ServiceRecordOpening,
            activePool = pool,
            arrivalConfirmationRequired = false,
            treatmentPlanConfirmed = false,
            serviceLogReady = false
        )}
        performArrivalHandshake(pool)
    }

    private fun performArrivalHandshake(pool: PoolRecord) {
        scope.launch {
            controller.updateMetaDatMessage("Opening service record...")
            
            val location = locationManager.status.value as? PoolLocationStatus.Success
            
            val request = AgentRequest(
                deviceId = controller.uiState.value.deviceId,
                sessionId = controller.uiState.value.sessionId,
                text = "Service arrival at ${pool.customerName}",
                timestamp = getIsoTimestamp(),
                eventType = "service_arrival",
                userId = "user_00001",
                poolId = pool.poolId,
                location = location?.let { ServiceLocation(it.latitude, it.longitude, it.accuracy) }
            )

            val response = repository.sendMessage(controller.uiState.value.apiUrl, request)
            if (response.status == "ok" && response.visitId != null) {
                _state.update { it.copy(
                    flowState = ServiceFlowState.WaitingForReadings,
                    visitId = response.visitId,
                    isReadyForReadings = response.readyForReadings ?: false,
                    backendServiceStatus = response.serviceStatus
                )}
                if (response.speak == true) {
                    controller.speakResponse(response.finalResponseText)
                }
            } else {
                Log.e("HermesService", "Handshake failed: ${response.error}")
                controller.updateMetaDatMessage("Could not open service record.")
                _state.update { it.copy(flowState = ServiceFlowState.OffRoute) }
            }
        }
    }

    fun handleInput(text: String): Boolean {
        val currentState = _state.value
        
        if (currentState.flowState == ServiceFlowState.PoolConfirmation && currentState.arrivalConfirmationRequired) {
            val act = confirmation.getAction(text)
            if (act == ReadingConfirmationAction.Confirm && currentState.activePool != null) {
                onPoolSelected(currentState.activePool)
                return true
            } else if (act == ReadingConfirmationAction.Reject) {
                _state.update { it.copy(
                    flowState = ServiceFlowState.OffRoute, 
                    arrivalConfirmationRequired = false,
                    treatmentPlanConfirmed = false,
                    serviceLogReady = false
                ) }
                controller.speakResponse("Standing by.")
                return true
            }
        }

        if (currentState.flowState == ServiceFlowState.OffRoute) return false

        when (currentState.flowState) {
            ServiceFlowState.WaitingForReadings, ServiceFlowState.ConfirmingReadings -> {
                val action = confirmation.getAction(text)
                if (action == ReadingConfirmationAction.Confirm) {
                    if (currentState.currentReadings.isReadyForSubmission()) {
                        submitReadings()
                    } else {
                        controller.speakResponse("I still need chlorine, pH, and alkalinity. Please provide them.")
                    }
                    return true
                } else if (action == ReadingConfirmationAction.Reject) {
                    _state.update { it.copy(
                        flowState = ServiceFlowState.WaitingForReadings,
                        currentReadings = ParsedWaterTest(),
                        treatmentPlanConfirmed = false,
                        serviceLogReady = false
                    )}
                    controller.speakResponse("Start over. Standing by for readings.")
                    return true
                }

                val parsed = parser.parse(text, currentState.currentReadings)
                if (parsed != currentState.currentReadings) {
                    _state.update { it.copy(
                        currentReadings = parsed,
                        flowState = ServiceFlowState.ConfirmingReadings
                    )}
                    speakConfirmationRequest(parsed)
                    return true
                } else {
                    if (text.lowercase().contains("stand by") || text.lowercase().contains("hold on")) {
                        controller.speakResponse("Standing by.")
                        return true
                    }
                }
            }
            ServiceFlowState.TreatmentReady, ServiceFlowState.StandingBy -> {
                if (text.lowercase().contains("ready") || text.lowercase().contains("add") || text.lowercase().contains("treatment")) {
                    readTreatmentPlan()
                    return true
                } else if (text.lowercase().contains("stand by") || text.lowercase().contains("hold on")) {
                    _state.update { it.copy(flowState = ServiceFlowState.StandingBy) }
                    controller.speakResponse("Standing by.")
                    return true
                }
            }
            else -> {}
        }
        return false
    }

    private fun speakConfirmationRequest(readings: ParsedWaterTest) {
        val summary = buildString {
            append("I heard ")
            readings.freeChlorine?.let { append("chlorine $it, ") }
            readings.ph?.let { append("pH $it, ") }
            readings.totalAlkalinity?.let { append("alkalinity ${it.toInt()}, ") }
            readings.cya?.let { append("CYA ${it.toInt()}, ") }
            readings.calciumHardness?.let { append("hardness ${it.toInt()}, ") }
            readings.salt?.let { append("salt ${it.toInt()}, ") }
            append("is that correct?")
        }
        controller.speakResponse(summary)
    }

    private fun submitReadings() {
        val currentState = _state.value
        _state.update { it.copy(
            flowState = ServiceFlowState.SavingReadings, 
            treatmentPlanConfirmed = false,
            serviceLogReady = false
        ) }
        controller.speakResponse("Saving the results.")

        scope.launch {
            val request = AgentRequest(
                deviceId = controller.uiState.value.deviceId,
                sessionId = controller.uiState.value.sessionId,
                text = "Submitting readings",
                timestamp = getIsoTimestamp(),
                eventType = "service_readings",
                visitId = currentState.visitId,
                poolId = currentState.activePool?.poolId,
                userId = "user_00001",
                readings = currentState.currentReadings.toNormalizedMap(),
                raw = mapOf(
                    "spoken_input" to currentState.currentReadings.toString(),
                    "normalized_summary" to "FC ${currentState.currentReadings.freeChlorine}, PH ${currentState.currentReadings.ph}, TA ${currentState.currentReadings.totalAlkalinity}"
                )
            )

            val response = repository.sendMessage(controller.uiState.value.apiUrl, request)
            if (response.status == "ok" && response.saved == true) {
                val recs = response.recommendations?.map { 
                    ChemicalRecommendation(it.productId, it.name, it.chemicalRole, it.amount, it.unit, it.reason)
                } ?: emptyList()
                
                val backendReadings = response.readings?.let {
                    ParsedWaterTest(
                        freeChlorine = it[ReadingField.FreeChlorine.jsonKey],
                        ph = it[ReadingField.Ph.jsonKey],
                        totalAlkalinity = it[ReadingField.Alkalinity.jsonKey], // Use alkalinity_ppm from backend
                        cya = it[ReadingField.Cya.jsonKey],
                        calciumHardness = it[ReadingField.CalciumHardness.jsonKey],
                        salt = it[ReadingField.Salt.jsonKey]
                    )
                } ?: currentState.currentReadings

                _state.update { it.copy(
                    flowState = ServiceFlowState.TreatmentReady,
                    recommendations = recs,
                    currentReadings = backendReadings,
                    backendServiceStatus = response.serviceStatus,
                    historicalContext = response.historicalContext
                )}
                
                val speakText = if (response.historicalContext != null) {
                    val trends = response.historicalContext.getTrendsForSpeech(limit = 2)
                    if (trends.isNotEmpty()) "$trends ${response.finalResponseText}" else response.finalResponseText
                } else {
                    response.finalResponseText
                }

                if (response.speak == true) {
                    controller.speakResponse(speakText)
                }
            } else {
                val errorMsg = response.error ?: "Readings received, but save was not confirmed."
                controller.speakResponse(errorMsg)
                _state.update { it.copy(flowState = ServiceFlowState.ConfirmingReadings) }
            }
        }
    }

    private fun readTreatmentPlan() {
        val recs = _state.value.recommendations
        if (recs.isEmpty()) {
            controller.speakResponse("No chemical additions are required. The pool is balanced.")
            return
        }

        val speech = buildString {
            append("Add ")
            recs.forEachIndexed { index, rec ->
                val prodName = rec.name ?: rec.productId.replace("_", " ")
                append("${formatAmount(rec.amount)} ${rec.unit} of $prodName")
                if (index < recs.size - 1) append(" and ")
            }
            append(".")
        }
        
        controller.speakResponse(speech)
    }

    private fun formatAmount(amount: Float): String {
        return if (amount == 0.5f) "half a" else if (amount == 1.0f) "one" else amount.toString()
    }

    private fun getIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun calculateDistanceFeet(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return (r * c) * 3.28084
    }
}
