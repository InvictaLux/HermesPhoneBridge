package com.example.hermesbridge.meta

sealed class MetaDatStatus {
    object NotInitialized : MetaDatStatus()
    object Initializing : MetaDatStatus()
    object Ready : MetaDatStatus()
    object MissingMetaApp : MetaDatStatus()
    object RegistrationRequired : MetaDatStatus()
    object RegistrationFailed : MetaDatStatus()
    object PermissionRequired : MetaDatStatus()
    
    // Session states (Gate 8D)
    object SessionIdle : MetaDatStatus()
    object SessionStarting : MetaDatStatus()
    object SessionReady : MetaDatStatus()
    object SessionPaused : MetaDatStatus()
    object SessionStopping : MetaDatStatus()
    object SessionStopped : MetaDatStatus()
    object SessionDisconnected : MetaDatStatus()
    object SessionReconnecting : MetaDatStatus()
    object NoDeviceFound : MetaDatStatus()
    object SearchingDevices : MetaDatStatus()
    data class DeviceFound(val name: String) : MetaDatStatus()
    data class SessionError(val message: String) : MetaDatStatus()

    data class Error(val message: String) : MetaDatStatus()

    fun getUserMessage(): String = when (this) {
        is NotInitialized -> "Meta DAT: Not checked"
        is Initializing -> "Meta DAT: Checking registration..."
        is Ready -> "Meta DAT: Ready"
        is MissingMetaApp -> "Meta DAT: Meta AI companion app required"
        is RegistrationRequired -> "Meta DAT: Registration required"
        is RegistrationFailed -> "Meta DAT: Registration failed"
        is PermissionRequired -> "Meta DAT: Permission required"
        
        is SessionIdle -> "Meta Session: Idle"
        is SessionStarting -> "Meta Session: Connecting..."
        is SessionReady -> "Meta Session: Ready (Connected)"
        is SessionPaused -> "Meta Session: Paused"
        is SessionStopping -> "Meta Session: Disconnecting..."
        is SessionStopped -> "Meta Session: Stopped"
        is SessionDisconnected -> "Meta Session: Disconnected"
        is SessionReconnecting -> "Meta Session: Reconnecting..."
        is NoDeviceFound -> "Meta Session: No paired glasses found"
        is SearchingDevices -> "Meta Session: Searching paired devices..."
        is DeviceFound -> "Meta Session: Found $name"
        is SessionError -> "Meta Session Error: $message"

        is Error -> "Meta DAT: Error: $message"
    }

    override fun toString(): String = getUserMessage()
}
