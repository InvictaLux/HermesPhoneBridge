package com.example.hermesbridge.metrics

data class LatencyBreakdown(
    var wakeDetectionStart: Long = 0,
    var wakeDetected: Long = 0,
    var wakeStopConfirmed: Long = 0,
    var speechRecognizerStart: Long = 0,
    var firstPartialTranscript: Long = 0,
    var finalTranscript: Long = 0,
    var backendRequestStart: Long = 0,
    var backendResponseReceived: Long = 0,
    var ttsStart: Long = 0,
    var ttsComplete: Long = 0,
    var wakeResume: Long = 0
) {
    fun getWakeDetectionLatency(): Long = if (wakeDetected > 0 && wakeDetectionStart > 0) wakeDetected - wakeDetectionStart else 0
    fun getWakeToListenLatency(): Long = if (speechRecognizerStart > 0 && wakeDetected > 0) speechRecognizerStart - wakeDetected else 0
    fun getSpeechStartToFirstPartial(): Long = if (firstPartialTranscript > 0 && speechRecognizerStart > 0) firstPartialTranscript - speechRecognizerStart else 0
    fun getSpeechStartToFinal(): Long = if (finalTranscript > 0 && speechRecognizerStart > 0) finalTranscript - speechRecognizerStart else 0
    fun getBackendLatency(): Long = if (backendResponseReceived > 0 && backendRequestStart > 0) backendResponseReceived - backendRequestStart else 0
    fun getResponseToTtsStart(): Long = if (ttsStart > 0 && backendResponseReceived > 0) ttsStart - backendResponseReceived else 0
    fun getTotalWakeToResponse(): Long = if (ttsStart > 0 && wakeDetected > 0) ttsStart - wakeDetected else 0
    fun getFullTurnDuration(): Long = if (ttsComplete > 0 && wakeDetected > 0) ttsComplete - wakeDetected else 0
}
