package com.example.hermesbridge

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hermesbridge.bridge.InputSource
import com.example.hermesbridge.bridge.PhoneTextInputSource
import com.example.hermesbridge.conversation.*
import com.example.hermesbridge.meta.MetaDatStatus
import com.example.hermesbridge.meta.MetaCapabilityStatus
import com.example.hermesbridge.meta.MetaAudioCapabilityInfo
import com.example.hermesbridge.audio.BluetoothAudioRouteStatus
import com.example.hermesbridge.audio.PcmCaptureStatus
import com.example.hermesbridge.audio.PcmCaptureResult
import com.example.hermesbridge.speech.SpeechOutput
import com.example.hermesbridge.speech.SpeechRecognitionStatus
import com.example.hermesbridge.speech.SpeechRecognitionResult
import com.example.hermesbridge.trigger.WearableTriggerStatus
import com.example.hermesbridge.wakeword.WakeWordStatus
import com.example.hermesbridge.wakeword.WakeWordDetection
import com.example.hermesbridge.metrics.WakeReliabilityStats
import com.example.hermesbridge.metrics.LatencyBreakdown
import com.example.hermesbridge.metrics.BatterySnapshot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

sealed class UiCommand {
    object LaunchMetaDatRegistration : UiCommand()
    object RequestMetaPermissions : UiCommand()
    object CreateMetaSession : UiCommand()
    object CloseMetaSession : UiCommand()
    object ReconnectMetaSession : UiCommand()
    object RefreshMetaSession : UiCommand()
    object DiscoverMetaCapabilities : UiCommand()
    object DiscoverMetaAudioApi : UiCommand()
    object StartBluetoothAudioRoute : UiCommand()
    object StopBluetoothAudioRoute : UiCommand()
    object CapturePcmSample : UiCommand()
    object StartWearableSpeechTest : UiCommand()
    object StopWearableSpeechTest : UiCommand()
    object NewSession : UiCommand()
    object StartWakeWordTest : UiCommand()
    object StopWakeWordTest : UiCommand()
    object EnableWakeMode : UiCommand()
    object DisableWakeMode : UiCommand()
    object MarkMissedWake : UiCommand()
    object ResetMetrics : UiCommand()
    object ExportMetrics : UiCommand()
}

class AgentViewModel(
    application: Application,
    private val repository: AgentRepository,
    private val inputSource: InputSource
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private val _commands = MutableSharedFlow<UiCommand>()
    val commands: SharedFlow<UiCommand> = _commands.asSharedFlow()

    private val sharedPrefs = application.getSharedPreferences(
        AppConfig.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private var speechOutput: SpeechOutput? = null
    private val MAX_HISTORY = 20

    init {
        // Load configurations from persistence
        val savedApiUrl = sharedPrefs.getString(AppConfig.KEY_API_URL, AppConfig.DEFAULT_BASE_URL) ?: AppConfig.DEFAULT_BASE_URL
        
        // Ensure consistent device ID
        val hasDeviceId = sharedPrefs.contains(AppConfig.KEY_DEVICE_ID)
        val savedDeviceId = if (hasDeviceId) {
            sharedPrefs.getString(AppConfig.KEY_DEVICE_ID, AppConfig.DEFAULT_DEVICE_ID) ?: AppConfig.DEFAULT_DEVICE_ID
        } else {
            val defaultId = AppConfig.DEFAULT_DEVICE_ID
            sharedPrefs.edit().putString(AppConfig.KEY_DEVICE_ID, defaultId).apply()
            defaultId
        }

        // Initialize Session ID
        startNewSession(savedApiUrl, savedDeviceId)

        // Connect the InputSource text emissions to the transmission pipeline
        inputSource.setListener { event ->
            val source = if (event.source == "phone_text") ConversationTurnSource.PhoneText else ConversationTurnSource.MetaWearableVoice
            submitNewTurn(event.text, source)
        }
        inputSource.start()
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
                errorMessage = null,
                inputSourceName = if (inputSource is PhoneTextInputSource) "On-Screen Keyboard" else "External Source",
                inputSourceType = if (inputSource is PhoneTextInputSource) "phone_text" else "unknown"
            )
        }
    }

    // Configures the voice output engine (AndroidTtsSpeechOutput)
    fun setSpeechOutput(output: SpeechOutput) {
        this.speechOutput = output
        _uiState.update { it.copy(isTtsReady = true) }
    }

    // Dynamic field updates
    fun updateMetaDatStatus(newStatus: MetaDatStatus) {
        _uiState.update { it.copy(metaDatStatus = newStatus) }
    }

    fun updateMetaDatMessage(message: String?) {
        _uiState.update { it.copy(metaDatMessage = message) }
    }

    fun updatePermissionMessage(message: String?) {
        _uiState.update { it.copy(permissionMessage = message) }
    }

    fun updateMetaCapabilities(capabilities: MetaCapabilityStatus) {
        _uiState.update { it.copy(metaCapabilities = capabilities) }
    }

    fun updateMetaAudioInfo(audioInfo: MetaAudioCapabilityInfo) {
        _uiState.update { it.copy(metaAudioInfo = audioInfo) }
    }

    fun updatePcmCaptureStatus(status: PcmCaptureStatus) {
        _uiState.update { it.copy(pcmCaptureStatus = status) }
    }

    fun updatePcmCaptureResult(result: PcmCaptureResult) {
        _uiState.update { it.copy(pcmCaptureResult = result) }
    }

    fun updateSpeechStatus(status: SpeechRecognitionStatus) {
        _uiState.update { it.copy(speechStatus = status) }
    }

    fun updateSpeechResult(result: SpeechRecognitionResult) {
        _uiState.update { it.copy(speechResult = result) }
    }

    fun updateAudioRouteStatus(status: BluetoothAudioRouteStatus) {
        _uiState.update { it.copy(audioRouteStatus = status) }
    }

    fun updateConversationTurnState(status: ConversationTurnState) {
        _uiState.update { it.copy(turnState = status) }
    }

    fun updateTriggerStatus(status: WearableTriggerStatus) {
        _uiState.update { it.copy(triggerStatus = status) }
    }

    fun updateWakeWordStatus(status: WakeWordStatus) {
        _uiState.update { it.copy(wakeWordStatus = status) }
    }

    fun updateLastWakeDetection(detection: WakeWordDetection?) {
        _uiState.update { it.copy(lastWakeDetection = detection) }
    }

    fun updateWakeModeEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isWakeModeEnabled = enabled) }
    }

    fun updateReliabilityStats(stats: WakeReliabilityStats) {
        _uiState.update { it.copy(reliabilityStats = stats) }
    }

    fun updateLastLatency(latency: LatencyBreakdown) {
        _uiState.update { it.copy(lastLatency = latency) }
    }

    fun updateBatterySnapshot(snapshot: BatterySnapshot) {
        _uiState.update { it.copy(batterySnapshot = snapshot) }
    }

    fun updateBtUptime(uptimeMs: Long) {
        _uiState.update { it.copy(btUptimeMs = uptimeMs) }
    }

    fun onToggleWakeModeClicked() {
        viewModelScope.launch {
            if (_uiState.value.isWakeModeEnabled) {
                _commands.emit(UiCommand.DisableWakeMode)
            } else {
                _commands.emit(UiCommand.EnableWakeMode)
            }
        }
    }

    fun onStartWakeWordTestClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.StartWakeWordTest)
        }
    }

    fun onStopWakeWordTestClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.StopWakeWordTest)
        }
    }

    fun onCorrectDetectionClicked() {
        _uiState.update { it.copy(trueDetectionCount = it.trueDetectionCount + 1) }
    }

    fun onFalseTriggerClicked() {
        _uiState.update { it.copy(falseTriggerCount = it.falseTriggerCount + 1) }
    }

    fun onRegisterMetaDatClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.LaunchMetaDatRegistration)
        }
    }

    fun onGrantPermissionsClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.RequestMetaPermissions)
        }
    }

    fun onCreateSessionClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.CreateMetaSession)
        }
    }

    fun onCloseSessionClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.CloseMetaSession)
        }
    }

    fun onReconnectSessionClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.ReconnectMetaSession)
        }
    }

    fun onRefreshSessionClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.RefreshMetaSession)
        }
    }

    fun onDiscoverCapabilitiesClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.DiscoverMetaCapabilities)
        }
    }

    fun onInspectAudioApiClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.DiscoverMetaAudioApi)
        }
    }

    fun onStartAudioRouteClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.StartBluetoothAudioRoute)
        }
    }

    fun onStopAudioRouteClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.StopBluetoothAudioRoute)
        }
    }

    fun onCapturePcmSampleClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.CapturePcmSample)
        }
    }

    fun onStartWearableSpeechTestClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.StartWearableSpeechTest)
        }
    }

    fun onStopWearableSpeechTestClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.StopWearableSpeechTest)
        }
    }

    fun onNewSessionClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.NewSession)
        }
    }

    fun onMarkMissedWakeClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.MarkMissedWake)
        }
    }

    fun onResetMetricsClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.ResetMetrics)
        }
    }

    fun onExportMetricsClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.ExportMetrics)
        }
    }

    fun onInputTextChanged(newText: String) {
        _uiState.update { it.copy(inputText = newText) }
    }

    fun onApiUrlChanged(newUrl: String) {
        _uiState.update { it.copy(apiUrl = newUrl) }
        sharedPrefs.edit().putString(AppConfig.KEY_API_URL, newUrl).apply()
    }

    fun onDeviceIdChanged(newId: String) {
        _uiState.update { it.copy(deviceId = newId) }
        sharedPrefs.edit().putString(AppConfig.KEY_DEVICE_ID, newId).apply()
    }

    fun onSessionIdChanged(newSessionId: String) {
        _uiState.update { it.copy(sessionId = newSessionId) }
        sharedPrefs.edit().putString(AppConfig.KEY_SESSION_ID, newSessionId).apply()
    }

    fun submitScreenInput() {
        val textToSubmit = _uiState.value.inputText.trim()
        if (textToSubmit.isEmpty()) return
        _uiState.update { it.copy(inputText = "") }

        if (inputSource is PhoneTextInputSource) {
            inputSource.submitText(textToSubmit)
        }
    }

    fun clearEvents() {
        _uiState.update { it.copy(events = emptyList()) }
    }

    fun submitExternalBridgeEvent(event: com.example.hermesbridge.bridge.BridgeEvent) {
        val source = if (event.source.contains("wearable")) ConversationTurnSource.MetaWearableVoice else ConversationTurnSource.PhoneText
        submitNewTurn(event.text, source)
    }

    private fun submitNewTurn(text: String, source: ConversationTurnSource) {
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
                currentTurnId = turnId
            )
        }

        sendTextToBackend(text, turnId, source)
    }

    fun retryTurn(turn: ConversationTurn) {
        if (_uiState.value.isLoading) return
        submitNewTurn(turn.inputText, turn.source)
    }

    private fun sendTextToBackend(rawText: String, turnId: String, source: ConversationTurnSource) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val timestampIso = getIsoTimestamp()

            addEvent(LogEvent.TextInput(text = rawText))

            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    errorMessage = null,
                    conversationHistory = state.conversationHistory.map {
                        if (it.turnId == turnId) it.copy(status = ConversationTurnStatus.Sending) else it
                    }
                )
            }
            addEvent(LogEvent.NetworkRequestSent(url = currentState.apiUrl))

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
                addEvent(LogEvent.NetworkResponseReceived(responseText = textToSpeak))
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
                addEvent(LogEvent.ErrorOccurred(error = errorDetails))
            }
        }
    }

    private fun addEvent(event: LogEvent) {
        _uiState.update {
            it.copy(events = listOf(event) + it.events)
        }
    }

    private fun getIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    fun stopSpeaking() {
        speechOutput?.stop()
        _uiState.update { it.copy(isTtsSpeaking = false) }
    }

    fun speakResponse(text: String, onComplete: () -> Unit = {}) {
        _uiState.update { it.copy(isTtsSpeaking = true) }
        speechOutput?.speak(text) {
            _uiState.update { it.copy(isTtsSpeaking = false) }
            addEvent(LogEvent.TtsSpoken(text = text))
            onComplete()
        }
    }

    fun updateBatteryState(level: Int, isCharging: Boolean) {
        _uiState.update {
            it.copy(
                batteryLevel = level,
                isBatteryCharging = isCharging
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        inputSource.stop()
        speechOutput?.shutdown()
    }
}

class AgentViewModelFactory(
    private val application: Application,
    private val repository: AgentRepository,
    private val inputSource: InputSource
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AgentViewModel::class.java)) {
            return AgentViewModel(application, repository, inputSource) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
