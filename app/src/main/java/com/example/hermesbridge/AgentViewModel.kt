package com.example.hermesbridge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hermesbridge.conversation.*
import com.example.hermesbridge.meta.MetaDatStatus
import com.example.hermesbridge.meta.MetaCapabilityStatus
import com.example.hermesbridge.meta.MetaAudioCapabilityInfo
import com.example.hermesbridge.audio.BluetoothAudioRouteStatus
import com.example.hermesbridge.audio.PcmCaptureStatus
import com.example.hermesbridge.audio.PcmCaptureResult
import com.example.hermesbridge.speech.SpeechRecognitionStatus
import com.example.hermesbridge.speech.SpeechRecognitionResult
import com.example.hermesbridge.trigger.WearableTriggerStatus
import com.example.hermesbridge.wakeword.WakeWordStatus
import com.example.hermesbridge.wakeword.WakeWordDetection
import com.example.hermesbridge.metrics.WakeReliabilityStats
import com.example.hermesbridge.metrics.LatencyBreakdown
import com.example.hermesbridge.metrics.BatterySnapshot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

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
    object TogglePauseWake : UiCommand()
    data class SetScreenOffLimit(val mins: Int) : UiCommand()
}

class AgentViewModel(
    application: Application,
    private val controller: BridgeController
) : AndroidViewModel(application) {

    val uiState: StateFlow<AgentUiState> = controller.uiState

    private val _commands = MutableSharedFlow<UiCommand>()
    val commands: SharedFlow<UiCommand> = _commands.asSharedFlow()

    fun onToggleWakeModeClicked() {
        viewModelScope.launch {
            if (uiState.value.isWakeModeEnabled) {
                _commands.emit(UiCommand.DisableWakeMode)
            } else {
                _commands.emit(UiCommand.EnableWakeMode)
            }
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

    fun onMarkDeliberateWakeClicked() {
        controller.onDeliberateAttempt()
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

    fun onTogglePauseWakeClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.TogglePauseWake)
        }
    }

    fun onSetScreenOffLimit(mins: Int) {
        viewModelScope.launch {
            _commands.emit(UiCommand.SetScreenOffLimit(mins))
        }
    }

    fun onToggleAutoSpeakClicked() {
        controller.updateAutoSpeak(!uiState.value.isAutoSpeakEnabled)
    }

    fun onSpeakTurnResponse(turn: ConversationTurn) {
        turn.responseText?.let {
            controller.speakResponse(it)
        }
    }

    fun onCorrectDetectionClicked() {
        controller.onTrueDetection()
    }

    fun onFalseTriggerClicked() {
        controller.onFalseDetection()
    }

    fun onRegisterMetaDatClicked() { viewModelScope.launch { _commands.emit(UiCommand.LaunchMetaDatRegistration) } }
    fun onGrantPermissionsClicked() { viewModelScope.launch { _commands.emit(UiCommand.RequestMetaPermissions) } }
    fun onCreateSessionClicked() { viewModelScope.launch { _commands.emit(UiCommand.CreateMetaSession) } }
    fun onCloseSessionClicked() { viewModelScope.launch { _commands.emit(UiCommand.CloseMetaSession) } }
    fun onReconnectSessionClicked() { viewModelScope.launch { _commands.emit(UiCommand.ReconnectMetaSession) } }
    fun onRefreshSessionClicked() { viewModelScope.launch { _commands.emit(UiCommand.RefreshMetaSession) } }
    fun onDiscoverCapabilitiesClicked() { viewModelScope.launch { _commands.emit(UiCommand.DiscoverMetaCapabilities) } }
    fun onInspectAudioApiClicked() { viewModelScope.launch { _commands.emit(UiCommand.DiscoverMetaAudioApi) } }
    fun onStartAudioRouteClicked() { viewModelScope.launch { _commands.emit(UiCommand.StartBluetoothAudioRoute) } }
    fun onStopAudioRouteClicked() { viewModelScope.launch { _commands.emit(UiCommand.StopBluetoothAudioRoute) } }
    fun onCapturePcmSampleClicked() { viewModelScope.launch { _commands.emit(UiCommand.CapturePcmSample) } }
    fun onStartWakeWordTestClicked() { viewModelScope.launch { _commands.emit(UiCommand.StartWakeWordTest) } }
    fun onStopWakeWordTestClicked() { viewModelScope.launch { _commands.emit(UiCommand.StopWakeWordTest) } }

    fun onInputTextChanged(newText: String) {
        controller.updateInputText(newText)
    }
    
    fun submitScreenInput() {
        val text = uiState.value.inputText.trim()
        if (text.isEmpty()) return
        controller.updateInputText("")
        controller.submitBridgeEvent(text, ConversationTurnSource.PhoneText)
    }

    fun retryTurn(turn: ConversationTurn) {
        controller.submitBridgeEvent(turn.inputText, turn.source)
    }

    fun stopSpeaking() {
        controller.stopSpeaking()
    }
}

class AgentViewModelFactory(
    private val application: Application,
    private val controller: BridgeController
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AgentViewModel::class.java)) {
            return AgentViewModel(application, controller) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
