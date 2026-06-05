package com.example.hermesbridge.speech

import kotlinx.coroutines.flow.StateFlow

interface SpeechToTextInput {
    val status: StateFlow<SpeechRecognitionStatus>
    val result: StateFlow<SpeechRecognitionResult>
    
    fun startListening()
    fun stopListening()
    fun destroy()
}
