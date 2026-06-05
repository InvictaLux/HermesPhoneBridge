package com.example.hermesbridge.meta

data class MetaAudioCapabilityInfo(
    val apiAvailable: Boolean = false,
    val platformApiRequired: Boolean = true,
    val permissionRequired: Boolean = true,
    val permissionStatus: String = "unknown",
    val streamType: String = "Bluetooth HFP / SCO",
    val format: String = "PCM",
    val sampleRateHz: Int = 8000,
    val channelCount: Int = 1,
    val encoding: String = "ENCODING_PCM_16BIT",
    val startMethod: String = "AudioManager.startBluetoothSco()",
    val stopMethod: String = "AudioManager.stopBluetoothSco()",
    val sessionRequirement: String = "Active DeviceSession recommended for coordination",
    val notes: String = "8kHz mono bidirectional audio. Mutually exclusive with A2DP high-quality stereo.",
    val error: String? = null
) {
    fun toDisplayString(): String {
        if (error != null) return "Error: $error"
        return """
            Audio API: ${if (apiAvailable) "Native SDK" else "Standard Android"}
            Permission: $permissionStatus
            Format: $format ($sampleRateHz Hz, $channelCount ch)
            Start: $startMethod
            Note: $notes
        """.trimIndent()
    }
}
