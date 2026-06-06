package com.example.hermesbridge

import com.example.hermesbridge.meta.MetaDatStatus
import com.example.hermesbridge.meta.MetaCapabilityStatus
import com.example.hermesbridge.meta.MetaAudioCapabilityInfo
import com.example.hermesbridge.audio.BluetoothAudioRouteStatus
import com.example.hermesbridge.audio.PcmCaptureStatus
import com.example.hermesbridge.audio.PcmCaptureResult
import com.example.hermesbridge.speech.SpeechRecognitionStatus
import com.example.hermesbridge.speech.SpeechRecognitionResult
import com.example.hermesbridge.conversation.ConversationTurn
import com.example.hermesbridge.conversation.ConversationTurnState
import com.example.hermesbridge.trigger.WearableTriggerStatus
import com.example.hermesbridge.wakeword.WakeWordStatus
import com.example.hermesbridge.wakeword.WakeWordDetection
import com.example.hermesbridge.metrics.WakeReliabilityStats
import com.example.hermesbridge.metrics.LatencyBreakdown
import com.example.hermesbridge.metrics.BatterySnapshot
import com.example.hermesbridge.service.WakeServiceState

data class AgentUiState(
    val apiUrl: String = AppConfig.DEFAULT_BASE_URL,
    val deviceId: String = AppConfig.DEFAULT_DEVICE_ID,
    val sessionId: String = "", // Will be initialized by ViewModel
    val inputText: String = "",
    val isLoading: Boolean = false,
    val latestResponse: String = "",
    val isTtsReady: Boolean = false,
    val isTtsSpeaking: Boolean = false,
    val events: List<LogEvent> = emptyList(),
    val errorMessage: String? = null,
    val inputSourceName: String = "On-Screen Keyboard",
    val inputSourceType: String = "phone_text",
    val isBackendConnected: Boolean = true,
    val batteryLevel: Int = 100,
    val isBatteryCharging: Boolean = false,
    val metaDatStatus: MetaDatStatus = MetaDatStatus.NotInitialized,
    val metaDatMessage: String? = null,
    val permissionMessage: String? = null,
    val metaCapabilities: MetaCapabilityStatus = MetaCapabilityStatus(),
    val metaAudioInfo: MetaAudioCapabilityInfo = MetaAudioCapabilityInfo(),
    val audioRouteStatus: BluetoothAudioRouteStatus = BluetoothAudioRouteStatus.Idle,
    val pcmCaptureStatus: PcmCaptureStatus = PcmCaptureStatus.Idle,
    val pcmCaptureResult: PcmCaptureResult = PcmCaptureResult(),
    val speechStatus: SpeechRecognitionStatus = SpeechRecognitionStatus.Idle,
    val speechResult: SpeechRecognitionResult = SpeechRecognitionResult(),
    val turnState: ConversationTurnState = ConversationTurnState.Idle,
    val conversationHistory: List<ConversationTurn> = emptyList(),
    val currentTurnId: String? = null,
    val triggerStatus: WearableTriggerStatus = WearableTriggerStatus.Idle,
    val wakeWordStatus: WakeWordStatus = WakeWordStatus.Idle,
    val lastWakeDetection: WakeWordDetection? = null,
    val trueDetectionCount: Int = 0,
    val falseTriggerCount: Int = 0,
    val isWakeModeEnabled: Boolean = false,
    val reliabilityStats: WakeReliabilityStats = WakeReliabilityStats(),
    val lastLatency: LatencyBreakdown = LatencyBreakdown(),
    val batterySnapshot: BatterySnapshot? = null,
    val btUptimeMs: Long = 0,
    val wakeServiceState: WakeServiceState = WakeServiceState.Stopped,
    val sessionRecoveryAttempts: Int = 0,
    val routeRecoveryAttempts: Int = 0,
    val screenOffLimitMinutes: Int = 60
)
