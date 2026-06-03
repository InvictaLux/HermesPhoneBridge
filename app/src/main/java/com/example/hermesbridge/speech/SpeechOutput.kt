package com.example.hermesbridge.speech

interface SpeechOutput {
    fun speak(text: String)
    fun stop()
    fun shutdown()
}
