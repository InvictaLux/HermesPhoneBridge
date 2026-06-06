package com.example.hermesbridge.service

sealed class WakeServiceState {
    object Stopped : WakeServiceState()
    object Starting : WakeServiceState()
    object Listening : WakeServiceState()
    object TurnActive : WakeServiceState()
    object RecoveringSession : WakeServiceState()
    object RecoveringRoute : WakeServiceState()
    object PausedLowBattery : WakeServiceState()
    object PausedThermal : WakeServiceState()
    data class Error(val message: String) : WakeServiceState()
    object Stopping : WakeServiceState()

    fun getUserMessage(): String = when (this) {
        is Stopped -> "Background Wake: Off"
        is Starting -> "Background Wake: Starting..."
        is Listening -> "Background Wake: Listening"
        is TurnActive -> "Background Wake: Turn Active"
        is RecoveringSession -> "Background Wake: Recovering Session..."
        is RecoveringRoute -> "Background Wake: Recovering Route..."
        is PausedLowBattery -> "Background Wake: Paused (Low Battery)"
        is PausedThermal -> "Background Wake: Paused (High Temp)"
        is Error -> "Background Wake Error: $message"
        is Stopping -> "Background Wake: Stopping..."
    }
}
