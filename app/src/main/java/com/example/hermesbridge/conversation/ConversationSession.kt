package com.example.hermesbridge.conversation

data class ConversationSession(
    val sessionId: String,
    val startedAt: String,
    val turns: List<ConversationTurn> = emptyList()
)
