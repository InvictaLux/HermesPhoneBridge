package com.example.hermesbridge.audio

sealed class BluetoothAudioRouteStatus {
    object Idle : BluetoothAudioRouteStatus()
    object Checking : BluetoothAudioRouteStatus()
    object PermissionRequired : BluetoothAudioRouteStatus()
    object NoActiveMetaSession : BluetoothAudioRouteStatus()
    object NoBluetoothCommunicationDevice : BluetoothAudioRouteStatus()
    object Routing : BluetoothAudioRouteStatus()
    data class Routed(val deviceName: String, val deviceType: String, val mode: Int) : BluetoothAudioRouteStatus()
    object Stopping : BluetoothAudioRouteStatus()
    object Stopped : BluetoothAudioRouteStatus()
    data class Error(val message: String) : BluetoothAudioRouteStatus()

    fun getUserMessage(): String = when (this) {
        is Idle -> "Audio Route: Idle"
        is Checking -> "Audio Route: Checking..."
        is PermissionRequired -> "Audio Route: Permission required"
        is NoActiveMetaSession -> "Audio Route: No active Meta session"
        is NoBluetoothCommunicationDevice -> "Audio Route: No Bluetooth comm device found"
        is Routing -> "Audio Route: Routing..."
        is Routed -> "Audio Route: Routed to $deviceName ($deviceType), Mode: $mode"
        is Stopping -> "Audio Route: Stopping..."
        is Stopped -> "Audio Route: Stopped"
        is Error -> "Audio Route Error: $message"
    }

    override fun toString(): String = getUserMessage()
}
