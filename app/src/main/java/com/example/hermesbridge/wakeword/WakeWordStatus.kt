package com.example.hermesbridge.wakeword

sealed class WakeWordStatus {
    object Idle : WakeWordStatus()
    object Checking : WakeWordStatus()
    object PermissionRequired : WakeWordStatus()
    object AudioRouteRequired : WakeWordStatus()
    object UnsupportedAudioFormat : WakeWordStatus()
    object Starting : WakeWordStatus()
    object Listening : WakeWordStatus()
    object Detected : WakeWordStatus()
    object Stopped : WakeWordStatus()
    object NotConfigured : WakeWordStatus()
    data class Error(val message: String) : WakeWordStatus()

    fun getUserMessage(): String = when (this) {
        is Idle -> "Wake Word: Idle"
        is Checking -> "Wake Word: Checking..."
        is PermissionRequired -> "Wake Word: Permission required"
        is AudioRouteRequired -> "Wake Word: Audio route required"
        is UnsupportedAudioFormat -> "Wake Word: Unsupported format (mismatch)"
        is Starting -> "Wake Word: Starting..."
        is Listening -> "Wake Word: Listening locally..."
        is Detected -> "Wake Word: DETECTED"
        is Stopped -> "Wake Word: Stopped"
        is NotConfigured -> "Wake Word: Not Configured (API Key Missing)"
        is Error -> "Wake Word Error: $message"
    }
}
