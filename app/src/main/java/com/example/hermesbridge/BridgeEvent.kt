package com.example.hermesbridge

import java.util.UUID

sealed interface BridgeEvent {
    val id: String
    val timestamp: Long
    val message: String

    data class TextInput(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val text: String,
        override val message: String = "User input: $text"
    ) : BridgeEvent

    data class NetworkRequestSent(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val url: String,
        override val message: String = "HTTP POST sent to $url"
    ) : BridgeEvent

    data class NetworkResponseReceived(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val responseText: String,
        override val message: String = "Response body: $responseText"
    ) : BridgeEvent

    data class TtsSpoken(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val text: String,
        override val message: String = "Spoke text: $text"
    ) : BridgeEvent

    data class ErrorOccurred(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val error: String,
        override val message: String = "Error: $error"
    ) : BridgeEvent
}
