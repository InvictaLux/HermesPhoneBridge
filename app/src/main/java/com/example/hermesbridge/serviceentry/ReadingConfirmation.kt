package com.example.hermesbridge.serviceentry

enum class ReadingConfirmationAction {
    Confirm,
    Reject,
    None
}

class ReadingConfirmation {
    private val confirmAliases = listOf("yes", "correct", "that's right", "save it", "confirmed", "right")
    private val rejectAliases = listOf("no", "wrong", "start over")

    fun getAction(input: String): ReadingConfirmationAction {
        val lower = input.lowercase().trim()
        if (confirmAliases.any { lower == it }) return ReadingConfirmationAction.Confirm
        if (rejectAliases.any { lower == it }) return ReadingConfirmationAction.Reject
        return ReadingConfirmationAction.None
    }
}
