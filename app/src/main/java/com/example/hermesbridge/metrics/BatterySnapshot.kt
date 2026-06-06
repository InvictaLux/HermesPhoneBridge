package com.example.hermesbridge.metrics

data class BatterySnapshot(
    val timestamp: Long,
    val level: Int,
    val temperature: Float,
    val voltage: Int,
    val currentNow: Long?,
    val chargeCounter: Long?,
    val isCharging: Boolean
)
