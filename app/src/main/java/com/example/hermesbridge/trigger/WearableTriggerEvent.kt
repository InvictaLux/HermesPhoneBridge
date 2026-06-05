package com.example.hermesbridge.trigger

data class WearableTriggerEvent(
    val type: String,
    val timestamp: Long = System.currentTimeMillis()
)
