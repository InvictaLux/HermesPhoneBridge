package com.example.hermesbridge.speech

interface SpeechOutput {
    fun speak(text: String, onComplete: () -> Unit = {})
    fun stop()
    fun shutdown()
}
