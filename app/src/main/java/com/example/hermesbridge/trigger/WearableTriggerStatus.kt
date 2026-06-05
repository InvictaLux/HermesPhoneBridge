package com.example.hermesbridge.trigger

sealed class WearableTriggerStatus {
    object Idle : WearableTriggerStatus()
    object Listening : WearableTriggerStatus()
    object Ready : WearableTriggerStatus()
    object Unsupported : WearableTriggerStatus()
    object IgnoredWhileBusy : WearableTriggerStatus()
    data class Error(val message: String) : WearableTriggerStatus()

    fun getUserMessage(): String = when (this) {
        is Idle -> "Trigger: Idle"
        is Listening -> "Trigger: Active"
        is Ready -> "Trigger: Ready"
        is Unsupported -> "Trigger: No supported wearable event found in core SDK"
        is IgnoredWhileBusy -> "Trigger: Ignored (System busy)"
        is Error -> "Trigger Error: $message"
    }
}
