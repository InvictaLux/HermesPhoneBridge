package com.example.hermesbridge

import java.util.UUID

sealed interface LogEvent {
    val id: String
    val timestamp: Long
    val message: String

    data class TextInput(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val text: String,
        override val message: String = "User input: $text"
    ) : LogEvent

    data class NetworkRequestSent(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val url: String,
        override val message: String = "HTTP POST sent to $url"
    ) : LogEvent

    data class NetworkResponseReceived(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val responseText: String,
        override val message: String = "Response body: $responseText"
    ) : LogEvent

    data class TtsSpoken(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val text: String,
        override val message: String = "Spoke text: $text"
    ) : LogEvent

    data class ErrorOccurred(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val error: String,
        override val message: String = "Error: $error"
    ) : LogEvent
}
