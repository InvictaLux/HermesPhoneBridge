package com.example.hermesbridge.wakeword

data class WakeWordDetection(
    val keyword: String,
    val timestamp: Long = System.currentTimeMillis(),
    val latencyMs: Long,
    val confidence: Float = 0f,
    val routeStatus: String
)
