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
import com.example.hermesbridge.onboarding.OnboardingStep
import com.example.hermesbridge.onboarding.StepStatus
import com.example.hermesbridge.serviceentry.ServiceVisitState
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
    object ManualMediaPause : UiCommand()
    object ManualMediaResume : UiCommand()
    object ToggleMediaAutoPause : UiCommand()
    data class SetMediaResumePolicy(val policy: com.example.hermesbridge.media.MediaResumePolicy) : UiCommand()
    object ResetSettings : UiCommand()
    object ClearDraft : UiCommand()
    object TriggerLocationHandshake : UiCommand()
    object DetectCurrentPool : UiCommand()
    object SyncRoute : UiCommand()
    data class SelectPool(val pool: com.example.hermesbridge.PoolRecord) : UiCommand()
    object CompleteOnboarding : UiCommand()
    object ResetOnboarding : UiCommand()
    object NextOnboardingStep : UiCommand()
    object PreviousOnboardingStep : UiCommand()
    object RequestBluetoothPermissions : UiCommand()
    object RequestMicrophonePermission : UiCommand()
    object RequestNotificationPermission : UiCommand()
    object OpenAppSettings : UiCommand()
    object ConfirmTreatmentPlan : UiCommand()
    object MarkServiceLogReady : UiCommand()
    data class UpdateTechnicianNotes(val notes: String) : UiCommand()
    object SubmitServiceCompletion : UiCommand()
    object StartManualVoiceTurn : UiCommand()
    object StopVoiceTurn : UiCommand()
}

class AgentViewModel(
    application: Application,
    private val controller: BridgeController,
    private val prefsRepository: com.example.hermesbridge.settings.AppPreferencesRepository
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

    fun onStartVoiceSessionClicked() {
        viewModelScope.launch { _commands.emit(UiCommand.StartManualVoiceTurn) }
    }

    fun onStopVoiceSessionClicked() {
        viewModelScope.launch { _commands.emit(UiCommand.StopVoiceTurn) }
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

    fun onNavigateTo(screen: AppScreen) {
        controller.navigateTo(screen)
    }

    fun onResetSettingsClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.ResetSettings)
        }
    }

    fun onClearDraftClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.ClearDraft)
        }
    }

    fun onTriggerLocationHandshake() {
        viewModelScope.launch {
            _commands.emit(UiCommand.TriggerLocationHandshake)
        }
    }

    fun onDetectCurrentPoolClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.DetectCurrentPool)
        }
    }

    fun onSyncRouteClicked() {
        viewModelScope.launch {
            _commands.emit(UiCommand.SyncRoute)
        }
    }

    fun onPoolSelected(pool: com.example.hermesbridge.PoolRecord) {
        viewModelScope.launch {
            _commands.emit(UiCommand.SelectPool(pool))
        }
    }

    fun onCompleteOnboarding() {
        viewModelScope.launch { _commands.emit(UiCommand.CompleteOnboarding) }
    }

    fun onResetOnboarding() {
        viewModelScope.launch { _commands.emit(UiCommand.ResetOnboarding) }
    }

    fun onNextOnboardingStep() {
        viewModelScope.launch { _commands.emit(UiCommand.NextOnboardingStep) }
    }

    fun onPreviousOnboardingStep() {
        viewModelScope.launch { _commands.emit(UiCommand.PreviousOnboardingStep) }
    }

    fun onConfirmTreatmentPlanClicked() {
        viewModelScope.launch { _commands.emit(UiCommand.ConfirmTreatmentPlan) }
    }

    fun onMarkServiceLogReadyClicked() {
        viewModelScope.launch { _commands.emit(UiCommand.MarkServiceLogReady) }
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

    fun onSetWakeSensitivity(sensitivity: Float) {
        controller.updateWakeSensitivity(sensitivity)
        prefsRepository.updateWakeSensitivity(sensitivity)
    }

    fun onSetWakeDebounce(ms: Long) {
        controller.updateWakeDebounce(ms)
        prefsRepository.updateWakeDebounce(ms)
    }

    fun onManualMediaPauseClicked() { viewModelScope.launch { _commands.emit(UiCommand.ManualMediaPause) } }
    fun onManualMediaResumeClicked() { viewModelScope.launch { _commands.emit(UiCommand.ManualMediaResume) } }
    fun onToggleMediaAutoPause() { viewModelScope.launch { _commands.emit(UiCommand.ToggleMediaAutoPause) } }
    fun onSetMediaResumePolicy(policy: com.example.hermesbridge.media.MediaResumePolicy) { 
        viewModelScope.launch { _commands.emit(UiCommand.SetMediaResumePolicy(policy)) }
    }

    fun onToggleDiagnostics(expanded: Boolean) {
        controller.updateDiagnosticsExpanded(expanded)
        prefsRepository.updateDiagnosticsExpanded(expanded)
    }

    fun onToggleAutoSpeakClicked() {
        val newState = !uiState.value.isAutoSpeakEnabled
        controller.updateAutoSpeak(newState)
        prefsRepository.updateAutoSpeak(newState)
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
    fun onGrantPermissionsClicked() { 
        viewModelScope.launch { 
            when (uiState.value.currentOnboardingStep) {
                OnboardingStep.BluetoothPermissions -> _commands.emit(UiCommand.RequestBluetoothPermissions)
                OnboardingStep.MicrophonePermission -> _commands.emit(UiCommand.RequestMicrophonePermission)
                OnboardingStep.NotificationPermission -> _commands.emit(UiCommand.RequestNotificationPermission)
                else -> _commands.emit(UiCommand.RequestMetaPermissions)
            }
        } 
    }
    
    fun onOpenAppSettingsClicked() {
        viewModelScope.launch { _commands.emit(UiCommand.OpenAppSettings) }
    }

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
        viewModelScope.launch {
            // Simple debounce for draft saving
            kotlinx.coroutines.delay(500)
            if (uiState.value.inputText == newText) {
                prefsRepository.updateChatDraft(newText)
            }
        }
    }

    fun onVisitInspectorIdChanged(newId: String) {
        controller.updateVisitInspectorId(newId)
    }

    fun onLookupVisitClicked() {
        controller.lookupServiceVisit(uiState.value.visitInspectorId)
    }

    fun onHistoryDateSelected(dateIso: String) {
        controller.updateHistoryDate(dateIso)
        // If a pool is selected, trigger lookup for the new date
        uiState.value.selectedHistoryPoolId?.let {
            controller.lookupPoolHistory(it, dateIso)
        }
    }

    fun onHistoryPoolSelected(poolId: String?) {
        controller.updateHistoryPoolId(poolId)
        if (poolId != null) {
            controller.lookupPoolHistory(poolId, uiState.value.selectedHistoryDate)
        }
    }

    fun onLoadMoreHistory(poolId: String) {
        val currentCount = uiState.value.historyResults[poolId]?.size ?: 0
        controller.lookupPoolHistory(poolId, uiState.value.selectedHistoryDate, offset = currentCount)
    }

    fun onHistoryRecordClicked(record: ServiceHistoryRecord) {
        controller.updateVisitInspectorId(record.visitId)
        controller.lookupServiceVisit(record.visitId, record.poolId)
        controller.navigateTo(AppScreen.VisitInspector)
    }

    fun onTechnicianNotesChanged(newNotes: String) {
        viewModelScope.launch { _commands.emit(UiCommand.UpdateTechnicianNotes(newNotes)) }
    }

    fun onSubmitServiceCompletionClicked() {
        viewModelScope.launch { _commands.emit(UiCommand.SubmitServiceCompletion) }
    }
    
    fun submitScreenInput() {
        val text = uiState.value.inputText.trim()
        if (text.isEmpty()) return
        controller.updateInputText("")
        prefsRepository.updateChatDraft("")
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
    private val controller: BridgeController,
    private val prefsRepository: com.example.hermesbridge.settings.AppPreferencesRepository
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AgentViewModel::class.java)) {
            return AgentViewModel(application, controller, prefsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
