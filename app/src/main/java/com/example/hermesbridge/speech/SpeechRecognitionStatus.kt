package com.example.hermesbridge.speech

sealed class SpeechRecognitionStatus {
    object Idle : SpeechRecognitionStatus()
    object Checking : SpeechRecognitionStatus()
    object PermissionRequired : SpeechRecognitionStatus()
    object AudioRouteRequired : SpeechRecognitionStatus()
    object Listening : SpeechRecognitionStatus()
    object Processing : SpeechRecognitionStatus()
    object Completed : SpeechRecognitionStatus()
    object NoSpeech : SpeechRecognitionStatus()
    object Canceled : SpeechRecognitionStatus()
    data class Error(val message: String) : SpeechRecognitionStatus()

    fun getUserMessage(): String = when (this) {
        is Idle -> "Speech: Idle"
        is Checking -> "Speech: Checking availability..."
        is PermissionRequired -> "Speech: Microphone permission required"
        is AudioRouteRequired -> "Speech: Bluetooth wearable route required"
        is Listening -> "Speech: Listening through wearable..."
        is Processing -> "Speech: Processing..."
        is Completed -> "Speech: Completed"
        is NoSpeech -> "Speech: No speech detected"
        is Canceled -> "Speech: Canceled"
        is Error -> "Speech Error: $message"
    }
}
