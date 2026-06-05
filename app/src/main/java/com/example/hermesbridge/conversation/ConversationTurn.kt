package com.example.hermesbridge.conversation

import java.util.UUID

enum class ConversationTurnStatus {
    Pending,
    Sending,
    Completed,
    Failed,
    Canceled
}

data class ConversationTurn(
    val turnId: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val source: ConversationTurnSource,
    val inputText: String,
    val createdAt: String,
    val status: ConversationTurnStatus = ConversationTurnStatus.Pending,
    val responseText: String? = null,
    val errorMessage: String? = null,
    val completedAt: String? = null
)
