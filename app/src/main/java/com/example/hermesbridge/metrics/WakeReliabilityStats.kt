package com.example.hermesbridge.metrics

data class WakeReliabilityStats(
    val totalListeningMs: Long = 0,
    val detections: Int = 0,
    val trueDetections: Int = 0,
    val falseDetections: Int = 0,
    val missedDetections: Int = 0,
    val routeLossCount: Int = 0,
    val restartCount: Int = 0,
    val failureCount: Int = 0
) {
    fun getDetectionsPerHour(): Double {
        if (totalListeningMs == 0L) return 0.0
        return (detections.toDouble() / (totalListeningMs.toDouble() / 3600000.0))
    }

    fun getFalseDetectionsPerHour(): Double {
        if (totalListeningMs == 0L) return 0.0
        return (falseDetections.toDouble() / (totalListeningMs.toDouble() / 3600000.0))
    }
}
