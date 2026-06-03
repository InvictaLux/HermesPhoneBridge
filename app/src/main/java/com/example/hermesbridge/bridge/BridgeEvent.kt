package com.example.hermesbridge.bridge

data class BridgeEvent(
    val text: String,
    val source: String,
    val timestamp: String,
    val metadata: Map<String, String> = emptyMap()
)
