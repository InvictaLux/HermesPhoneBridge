package com.example.hermesbridge.trigger

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementation of a wearable trigger listener.
 * Discovery (Gate 10C): The Meta DAT SDK 0.7.0 does not currently expose 
 * generic global hardware button/gesture events in the core library. 
 * Interactions are scoped to the Display capability (Declarative UI).
 */
class MetaWearableTurnTrigger : WearableTurnTrigger {
    private val _status = MutableStateFlow<WearableTriggerStatus>(WearableTriggerStatus.Idle)
    override val status: StateFlow<WearableTriggerStatus> = _status.asStateFlow()

    private var listener: ((WearableTriggerEvent) -> Unit)? = null
    private var lastTriggerTime: Long = 0
    private val DEBOUNCE_MS = 1000

    override fun start() {
        Log.d("HermesTrigger", "MetaWearableTurnTrigger start called.")
        // Since no generic hardware event is exposed in mwdat-core, 
        // we report Unsupported to remain honest to the SDK discovery.
        _status.value = WearableTriggerStatus.Unsupported
    }

    override fun stop() {
        Log.d("HermesTrigger", "MetaWearableTurnTrigger stop called.")
        listener = null
        _status.value = WearableTriggerStatus.Idle
    }

    override fun setListener(listener: (WearableTriggerEvent) -> Unit) {
        this.listener = listener
    }

    /**
     * For internal testing or future Display-based tap events.
     */
    fun onHardwareEvent(type: String) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < DEBOUNCE_MS) {
            Log.d("HermesTrigger", "Ignoring duplicate/debounced trigger: $type")
            return
        }

        lastTriggerTime = now
        Log.i("HermesTrigger", "Valid hardware trigger detected: $type")
        listener?.invoke(WearableTriggerEvent(type, now))
    }
}
