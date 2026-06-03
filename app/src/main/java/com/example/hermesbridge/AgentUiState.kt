package com.example.hermesbridge

data class AgentUiState(
    val apiUrl: String = AppConfig.DEFAULT_BASE_URL,
    val deviceId: String = AppConfig.DEFAULT_DEVICE_ID,
    val sessionId: String = AppConfig.DEFAULT_SESSION_ID,
    val inputText: String = "",
    val isLoading: Boolean = false,
    val latestResponse: String = "",
    val isTtsReady: Boolean = false,
    val isTtsSpeaking: Boolean = false,
    val events: List<BridgeEvent> = emptyList(),
    val errorMessage: String? = null,
    val inputSourceName: String = "On-Screen Keyboard",
    val inputSourceType: String = "phone_text",
    val isBackendConnected: Boolean = true,
    val batteryLevel: Int = 100,
    val isBatteryCharging: Boolean = false
)
