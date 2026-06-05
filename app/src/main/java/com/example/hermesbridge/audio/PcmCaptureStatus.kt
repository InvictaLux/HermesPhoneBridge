package com.example.hermesbridge.audio

sealed class PcmCaptureStatus {
    object Idle : PcmCaptureStatus()
    object Checking : PcmCaptureStatus()
    object PermissionRequired : PcmCaptureStatus()
    object NoActiveMetaSession : PcmCaptureStatus()
    object AudioRouteRequired : PcmCaptureStatus()
    object Preparing : PcmCaptureStatus()
    object Recording : PcmCaptureStatus()
    object Completed : PcmCaptureStatus()
    object Stopped : PcmCaptureStatus()
    data class Error(val message: String) : PcmCaptureStatus()

    fun getUserMessage(): String = when (this) {
        is Idle -> "PCM Capture: Idle"
        is Checking -> "PCM Capture: Checking..."
        is PermissionRequired -> "PCM Capture: Record Audio permission required"
        is NoActiveMetaSession -> "PCM Capture: No active Meta session"
        is AudioRouteRequired -> "PCM Capture: Bluetooth route required"
        is Preparing -> "PCM Capture: Preparing..."
        is Recording -> "PCM Capture: Recording (3s)..."
        is Completed -> "PCM Capture: Completed"
        is Stopped -> "PCM Capture: Stopped"
        is Error -> "PCM Capture Error: $message"
    }

    override fun toString(): String = getUserMessage()
}
