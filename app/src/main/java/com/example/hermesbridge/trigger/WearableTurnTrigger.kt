package com.example.hermesbridge.trigger

import kotlinx.coroutines.flow.StateFlow

interface WearableTurnTrigger {
    val status: StateFlow<WearableTriggerStatus>
    fun start()
    fun stop()
    fun setListener(listener: (WearableTriggerEvent) -> Unit)
}
