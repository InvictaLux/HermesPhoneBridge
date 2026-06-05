package com.example.hermesbridge.conversation

sealed class ConversationTurnState {
    object Idle : ConversationTurnState()
    object PreparingAudioRoute : ConversationTurnState()
    object Listening : ConversationTurnState()
    object ProcessingTranscript : ConversationTurnState()
    object Sending : ConversationTurnState()
    object WaitingForResponse : ConversationTurnState()
    object Speaking : ConversationTurnState()
    object Completed : ConversationTurnState()
    object Canceled : ConversationTurnState()
    data class Error(val message: String) : ConversationTurnState()

    fun getUserMessage(): String = when (this) {
        is Idle -> "Turn: Idle"
        is PreparingAudioRoute -> "Turn: Preparing Audio..."
        is Listening -> "Turn: Listening..."
        is ProcessingTranscript -> "Turn: Processing Voice..."
        is Sending -> "Turn: Sending..."
        is WaitingForResponse -> "Turn: Waiting for Hermes..."
        is Speaking -> "Turn: Speaking..."
        is Completed -> "Turn: Completed"
        is Canceled -> "Turn: Canceled"
        is Error -> "Turn Error: $message"
    }
}
