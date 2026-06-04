package com.example.hermesbridge

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hermesbridge.bridge.InputSource
import com.example.hermesbridge.bridge.PhoneTextInputSource
import com.example.hermesbridge.meta.MetaDatStatus
import com.example.hermesbridge.speech.SpeechOutput
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

sealed class UiCommand {
    object LaunchMetaDatRegistration : UiCommand()
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

        // Check if session ID exists, otherwise generate a new one
        val hasSessionId = sharedPrefs.contains(AppConfig.KEY_SESSION_ID)
        val savedSessionId: String
        val isNewSession: Boolean

        if (hasSessionId) {
            savedSessionId = sharedPrefs.getString(AppConfig.KEY_SESSION_ID, AppConfig.DEFAULT_SESSION_ID) ?: AppConfig.DEFAULT_SESSION_ID
            isNewSession = false
        } else {
            // Generate a fresh session ID in a unique UUID-based format
            val randomUuid = java.util.UUID.randomUUID().toString().substring(0, 8)
            savedSessionId = "phone-session-$randomUuid"
            sharedPrefs.edit().putString(AppConfig.KEY_SESSION_ID, savedSessionId).apply()
            isNewSession = true
        }

        _uiState.update {
            it.copy(
                apiUrl = savedApiUrl,
                deviceId = savedDeviceId,
                sessionId = savedSessionId,
                inputSourceName = if (inputSource is PhoneTextInputSource) "On-Screen Keyboard" else "External Source",
                inputSourceType = if (inputSource is PhoneTextInputSource) "phone_text" else "unknown"
            )
        }

        // Connect the InputSource text emissions to the transmission pipeline
        inputSource.setListener { event ->
            sendTextToBackend(event.text, event.source)
        }
        inputSource.start()

        // If a new session ID was dynamically generated on startup, send initialized event to the backend
        if (isNewSession) {
            viewModelScope.launch {
                sendTextToBackend("New Session Initialized", "system")
            }
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

    fun onRegisterMetaDatClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.LaunchMetaDatRegistration)
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

    // Submits on-screen text to the active InputSource
    fun submitScreenInput() {
        val textToSubmit = _uiState.value.inputText.trim()
        if (textToSubmit.isEmpty()) return

        // Clear input text field straight away
        _uiState.update { it.copy(inputText = "") }

        if (inputSource is PhoneTextInputSource) {
            inputSource.submitText(textToSubmit)
        }
    }

    // Dispatches TTS stop signal
    fun stopSpeaking() {
        speechOutput?.stop()
        _uiState.update { it.copy(isTtsSpeaking = false) }
    }

    fun updateBatteryState(level: Int, isCharging: Boolean) {
        _uiState.update {
            it.copy(
                batteryLevel = level,
                isBatteryCharging = isCharging
            )
        }
    }

    // Resets event history
    fun clearEvents() {
        _uiState.update { it.copy(events = emptyList()) }
    }

    // Pipeline: Receives input, constructs API POST, handles feedback
    private fun sendTextToBackend(rawText: String, source: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val timestampIso = getIsoTimestamp()

            // 1. Post local input event
            val inputEvent = LogEvent.TextInput(text = rawText)
            addEvent(inputEvent)

            // 2. Mark loading and post sent message
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            addEvent(LogEvent.NetworkRequestSent(url = currentState.apiUrl))

            // 3. Assemble JSON Payload strictly adhering to the schema request
            val requestPayload = AgentRequest(
                deviceId = currentState.deviceId,
                sessionId = currentState.sessionId,
                inputType = "text",
                text = rawText,
                timestamp = timestampIso,
                metadata = AgentMetadata(
                    source = source,
                    wearable = if (source == "wearable_meta") "meta_glasses" else "none"
                )
            )

            // 4. Perform Network Request
            val response = repository.sendMessage(currentState.apiUrl, requestPayload)

            _uiState.update { it.copy(isLoading = false) }

            if (response.error == null) {
                val textToSpeak = response.finalResponseText

                _uiState.update {
                    it.copy(
                        latestResponse = textToSpeak,
                        errorMessage = null,
                        isBackendConnected = true
                    )
                }

                // Log response receipt
                addEvent(LogEvent.NetworkResponseReceived(responseText = textToSpeak))

                // Robustly capture backend session ID rotation/sync and save to SharedPreferences
                val responseSessionId = response.sessionId
                if (!responseSessionId.isNullOrEmpty() && responseSessionId != _uiState.value.sessionId) {
                    _uiState.update { it.copy(sessionId = responseSessionId) }
                    sharedPrefs.edit().putString(AppConfig.KEY_SESSION_ID, responseSessionId).apply()
                    addEvent(LogEvent.NetworkResponseReceived(
                        responseText = "[Session Sync] Updated local Session ID to: $responseSessionId"
                    ))
                }

                // 5. Trigger Speech synthesis
                _uiState.update { it.copy(isTtsSpeaking = true) }
                speechOutput?.speak(textToSpeak)
                
                addEvent(LogEvent.TtsSpoken(text = textToSpeak))

            } else {
                val errorDetails = response.error ?: "Communication Failure"
                _uiState.update {
                    it.copy(
                        errorMessage = errorDetails,
                        latestResponse = "",
                        isBackendConnected = false
                    )
                }
                addEvent(LogEvent.ErrorOccurred(error = errorDetails))
            }
        }
    }

    private fun addEvent(event: LogEvent) {
        _uiState.update {
            it.copy(events = listOf(event) + it.events) // Prepend for live logging stream
        }
    }

    private fun getIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
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
