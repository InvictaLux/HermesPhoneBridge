package com.example.hermesbridge.metrics

data class WakeReliabilityStats(
    val totalListeningMs: Long = 0,
    val detections: Int = 0,
    val trueDetections: Int = 0,
    val falseDetections: Int = 0,
    val missedDetections: Int = 0,
    val routeLossCount: Int = 0,
    val restartCount: Int = 0,
    val failureCount: Int = 0,
    val serviceRuntimeMs: Long = 0,
    val screenOffListeningMs: Long = 0,
    val servicePauseCount: Int = 0,
    val lowBatteryPauses: Int = 0,
    val thermalPauses: Int = 0,
    val sessionRecoveryCount: Int = 0,
    val routeRecoveryCount: Int = 0,
    val stopReason: String = "Unknown",
    val screenOffLimitReachedCount: Int = 0,
    val deliberateWakeAttempts: Int = 0,
    val duplicateDetectionsIgnored: Int = 0,
    val maxTemperature: Float = 0f,
    val batteryChange: Int = 0,
    val sessionUptimeMs: Long = 0,
    val routeUptimeMs: Long = 0
) {
    fun getPrecision(): Double {
        val total = trueDetections + falseDetections
        if (total == 0) return 0.0
        return trueDetections.toDouble() / total
    }

    fun getRecall(): Double {
        if (deliberateWakeAttempts == 0) return 0.0
        return trueDetections.toDouble() / deliberateWakeAttempts
    }

    fun getMissRate(): Double {
        if (deliberateWakeAttempts == 0) return 0.0
        return missedDetections.toDouble() / deliberateWakeAttempts
    }
    fun getDetectionsPerHour(): Double {
        if (totalListeningMs == 0L) return 0.0
        return (detections.toDouble() / (totalListeningMs.toDouble() / 3600000.0))
    }

    fun getFalseDetectionsPerHour(): Double {
        if (totalListeningMs == 0L) return 0.0
        return (falseDetections.toDouble() / (totalListeningMs.toDouble() / 3600000.0))
    }
}
