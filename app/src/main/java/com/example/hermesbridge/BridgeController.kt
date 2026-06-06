package com.example.hermesbridge

import android.content.Context
import android.util.Log
import com.example.hermesbridge.conversation.*
import com.example.hermesbridge.metrics.BatterySnapshot
import com.example.hermesbridge.metrics.LatencyBreakdown
import com.example.hermesbridge.metrics.WakeReliabilityStats
import com.example.hermesbridge.speech.SpeechOutput
import com.example.hermesbridge.wakeword.WakeWordDetection
import com.example.hermesbridge.wakeword.WakeWordStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Singleton controller for the Hermes Bridge interaction loop.
 * Survives Activity destruction and can be used by the Foreground Service.
 */
class BridgeController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repository: AgentRepository,
    private val speechOutput: SpeechOutput,
    private val metaDatManager: com.example.hermesbridge.meta.MetaDatManager,
    private val audioRouteManager: com.example.hermesbridge.audio.BluetoothAudioRouteManager,
    private val speechToText: com.example.hermesbridge.speech.AndroidSpeechRecognizerInput,
    private val wakeWordManager: com.example.hermesbridge.wakeword.WakeWordTestManager,
    private val metricsCollector: com.example.hermesbridge.metrics.InteractionMetricsCollector
) {
    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private val sharedPrefs = context.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
    private val MAX_HISTORY = 20

    init {
        val savedApiUrl = sharedPrefs.getString(AppConfig.KEY_API_URL, AppConfig.DEFAULT_BASE_URL) ?: AppConfig.DEFAULT_BASE_URL
        val savedDeviceId = sharedPrefs.getString(AppConfig.KEY_DEVICE_ID, AppConfig.DEFAULT_DEVICE_ID) ?: AppConfig.DEFAULT_DEVICE_ID
        startNewSession(savedApiUrl, savedDeviceId)
        startObserving()
    }

    private fun startObserving() {
        scope.launch {
            metaDatManager.status.collect { s -> updateMetaDatStatus(s) }
        }
        scope.launch {
            metaDatManager.capabilities.collect { c -> updateMetaCapabilities(c) }
        }
        scope.launch {
            metaDatManager.audioInfo.collect { a -> updateMetaAudioInfo(a) }
        }
        scope.launch {
            audioRouteManager.status.collect { s -> 
                updateAudioRouteStatus(s)
                if (s is com.example.hermesbridge.audio.BluetoothAudioRouteStatus.Routed) {
                    metricsCollector.onBluetoothRouteStarted()
                } else {
                    metricsCollector.onBluetoothRouteStopped()
                }
            }
        }
        scope.launch {
            speechToText.status.collect { s -> updateSpeechStatus(s) }
        }
        scope.launch {
            speechToText.result.collect { r -> updateSpeechResult(r) }
        }
        scope.launch {
            wakeWordManager.status.collect { s -> updateWakeWordStatus(s) }
        }
        scope.launch {
            wakeWordManager.lastDetection.collect { d -> updateLastWakeDetection(d) }
        }
        scope.launch {
            metricsCollector.reliabilityStats.collect { s -> updateReliabilityStats(s) }
        }
        scope.launch {
            metricsCollector.lastLatency.collect { l -> updateLastLatency(l) }
        }
        scope.launch {
            while (isActive) {
                updateBtUptime(metricsCollector.getBluetoothUptimeMs())
                delay(1000)
            }
        }
    }

    fun startNewSession(apiUrl: String? = null, deviceId: String? = null) {
        val newSessionId = "phone-session-${UUID.randomUUID().toString().substring(0, 8)}"
        val url = apiUrl ?: _uiState.value.apiUrl
        val dId = deviceId ?: _uiState.value.deviceId

        _uiState.update {
            it.copy(
                apiUrl = url,
                deviceId = dId,
                sessionId = newSessionId,
                conversationHistory = emptyList(),
                currentTurnId = null,
                latestResponse = "",
                errorMessage = null
            )
        }
        Log.i("HermesBridgeController", "Started new session: $newSessionId")
    }

    fun submitBridgeEvent(text: String, source: ConversationTurnSource) {
        if (_uiState.value.isLoading) return

        val turnId = UUID.randomUUID().toString()
        val createdAt = getIsoTimestamp()
        val turn = ConversationTurn(
            turnId = turnId,
            sessionId = _uiState.value.sessionId,
            source = source,
            inputText = text,
            createdAt = createdAt,
            status = ConversationTurnStatus.Pending
        )

        _uiState.update { state ->
            val newHistory = (listOf(turn) + state.conversationHistory).take(MAX_HISTORY)
            state.copy(
                conversationHistory = newHistory,
                currentTurnId = turnId,
                isLoading = true,
                errorMessage = null
            )
        }

        sendTextToBackend(text, turnId, source)
    }

    private fun sendTextToBackend(rawText: String, turnId: String, source: ConversationTurnSource) {
        scope.launch {
            val currentState = _uiState.value
            val timestampIso = getIsoTimestamp()

            val requestPayload = AgentRequest(
                deviceId = currentState.deviceId,
                sessionId = currentState.sessionId,
                inputType = "text",
                text = rawText,
                timestamp = timestampIso,
                metadata = AgentMetadata(
                    source = if (source == ConversationTurnSource.MetaWearableVoice) "meta_wearable_voice" else "phone_text",
                    wearable = if (source == ConversationTurnSource.MetaWearableVoice) "meta_glasses" else "none",
                    turnId = turnId,
                    createdAt = timestampIso
                )
            )

            val response = repository.sendMessage(currentState.apiUrl, requestPayload)

            if (_uiState.value.currentTurnId != turnId) return@launch

            _uiState.update { it.copy(isLoading = false) }

            if (response.error == null) {
                val textToSpeak = response.finalResponseText
                _uiState.update { state ->
                    state.copy(
                        latestResponse = textToSpeak,
                        errorMessage = null,
                        isBackendConnected = true,
                        conversationHistory = state.conversationHistory.map {
                            if (it.turnId == turnId) it.copy(
                                status = ConversationTurnStatus.Completed,
                                responseText = textToSpeak,
                                completedAt = getIsoTimestamp()
                            ) else it
                        }
                    )
                }
                speakResponse(textToSpeak)
            } else {
                val errorDetails = response.error ?: "Communication Failure"
                _uiState.update { state ->
                    state.copy(
                        errorMessage = errorDetails,
                        latestResponse = "",
                        isBackendConnected = false,
                        conversationHistory = state.conversationHistory.map {
                            if (it.turnId == turnId) it.copy(
                                status = ConversationTurnStatus.Failed,
                                errorMessage = errorDetails
                            ) else it
                        }
                    )
                }
            }
        }
    }

    fun speakResponse(text: String, onComplete: () -> Unit = {}) {
        _uiState.update { it.copy(isTtsSpeaking = true) }
        speechOutput.speak(text) {
            _uiState.update { it.copy(isTtsSpeaking = false) }
            onComplete()
        }
    }

    fun stopSpeaking() {
        speechOutput.stop()
        _uiState.update { it.copy(isTtsSpeaking = false) }
    }

    private fun getIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    // Proxy methods for state updates from coordinators
    fun updateMetaDatStatus(s: com.example.hermesbridge.meta.MetaDatStatus) { _uiState.update { it.copy(metaDatStatus = s) } }
    fun updateMetaDatMessage(m: String?) { _uiState.update { it.copy(metaDatMessage = m) } }
    fun updatePermissionMessage(m: String?) { _uiState.update { it.copy(permissionMessage = m) } }
    fun updateMetaCapabilities(c: com.example.hermesbridge.meta.MetaCapabilityStatus) { _uiState.update { it.copy(metaCapabilities = c) } }
    fun updateMetaAudioInfo(a: com.example.hermesbridge.meta.MetaAudioCapabilityInfo) { _uiState.update { it.copy(metaAudioInfo = a) } }
    fun updatePcmCaptureStatus(s: com.example.hermesbridge.audio.PcmCaptureStatus) { _uiState.update { it.copy(pcmCaptureStatus = s) } }
    fun updatePcmCaptureResult(r: com.example.hermesbridge.audio.PcmCaptureResult) { _uiState.update { it.copy(pcmCaptureResult = r) } }
    fun updateSpeechStatus(s: com.example.hermesbridge.speech.SpeechRecognitionStatus) { _uiState.update { it.copy(speechStatus = s) } }
    fun updateSpeechResult(r: com.example.hermesbridge.speech.SpeechRecognitionResult) { _uiState.update { it.copy(speechResult = r) } }
    fun updateAudioRouteStatus(s: com.example.hermesbridge.audio.BluetoothAudioRouteStatus) { _uiState.update { it.copy(audioRouteStatus = s) } }
    fun updateConversationTurnState(s: ConversationTurnState) { _uiState.update { it.copy(turnState = s) } }
    fun updateTriggerStatus(s: com.example.hermesbridge.trigger.WearableTriggerStatus) { _uiState.update { it.copy(triggerStatus = s) } }
    fun updateWakeWordStatus(s: WakeWordStatus) { _uiState.update { it.copy(wakeWordStatus = s) } }
    fun updateLastWakeDetection(d: WakeWordDetection?) { _uiState.update { it.copy(lastWakeDetection = d) } }
    fun updateWakeModeEnabled(e: Boolean) { _uiState.update { it.copy(isWakeModeEnabled = e) } }
    fun updateReliabilityStats(s: WakeReliabilityStats) { _uiState.update { it.copy(reliabilityStats = s) } }
    fun updateLastLatency(l: LatencyBreakdown) { _uiState.update { it.copy(lastLatency = l) } }
    fun updateBatterySnapshot(s: BatterySnapshot) { _uiState.update { it.copy(batterySnapshot = s) } }
    fun updateBtUptime(u: Long) { _uiState.update { it.copy(btUptimeMs = u) } }
    fun updateBatteryState(level: Int, isCharging: Boolean) { _uiState.update { it.copy(batteryLevel = level, isBatteryCharging = isCharging) } }
    
    fun onTrueDetection() {
        metricsCollector.onTrueDetection()
    }

    fun onFalseDetection() {
        metricsCollector.onFalseDetection()
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }
}
