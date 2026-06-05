package com.example.hermesbridge.speech

data class SpeechRecognitionResult(
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val confidence: Float = 0f,
    val isFinal: Boolean = false,
    val error: String? = null
)
