package com.example.hermesbridge.meta

data class MetaCapabilityStatus(
    val cameraAvailable: Boolean? = null,
    val photoAvailable: Boolean? = null,
    val microphoneAvailable: Boolean? = null,
    val audioAvailable: Boolean? = null,
    val gestureEventsAvailable: Boolean? = null,
    val buttonEventsAvailable: Boolean? = null,
    val transcriptionAvailable: Boolean? = null,
    val deviceInfoAvailable: Boolean? = null,
    val displayAvailable: Boolean? = null,
    val rawSummary: String = "Not discovered",
    val error: String? = null
) {
    fun toDisplayString(): String {
        if (error != null) return "Error: $error"
        val sb = StringBuilder()
        sb.append("Display: ${displayAvailable.toStatus()}\n")
        sb.append("Camera: ${cameraAvailable.toStatus()}\n")
        sb.append("Mic: ${microphoneAvailable.toStatus()}\n")
        sb.append("Info: ${deviceInfoAvailable.toStatus()}")
        return sb.toString()
    }

    private fun Boolean?.toStatus(): String = when (this) {
        true -> "available"
        false -> "unavailable"
        null -> "unknown"
    }
}
