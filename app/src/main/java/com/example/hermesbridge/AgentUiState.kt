package com.example.hermesbridge

import com.example.hermesbridge.meta.MetaDatStatus
import com.example.hermesbridge.meta.MetaCapabilityStatus
import com.example.hermesbridge.meta.MetaAudioCapabilityInfo
import com.example.hermesbridge.audio.BluetoothAudioRouteStatus

data class AgentUiState(
    val apiUrl: String = AppConfig.DEFAULT_BASE_URL,
    val deviceId: String = AppConfig.DEFAULT_DEVICE_ID,
    val sessionId: String = AppConfig.DEFAULT_SESSION_ID,
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
    val audioRouteStatus: BluetoothAudioRouteStatus = BluetoothAudioRouteStatus.Idle
)
