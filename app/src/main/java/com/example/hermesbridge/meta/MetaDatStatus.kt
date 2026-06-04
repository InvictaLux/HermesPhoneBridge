package com.example.hermesbridge.meta

sealed class MetaDatStatus {
    object NotInitialized : MetaDatStatus()
    object Initializing : MetaDatStatus()
    object Ready : MetaDatStatus()
    object MissingMetaApp : MetaDatStatus()
    object RegistrationRequired : MetaDatStatus()
    object RegistrationFailed : MetaDatStatus()
    object PermissionRequired : MetaDatStatus()
    
    // Session states (Gate 8A)
    object SessionNotChecked : MetaDatStatus()
    object SessionChecking : MetaDatStatus()
    object SessionReady : MetaDatStatus()
    object NoDeviceFound : MetaDatStatus()
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
        
        is SessionNotChecked -> "Meta Session: Not checked"
        is SessionChecking -> "Meta Session: Searching for devices..."
        is SessionReady -> "Meta Session: Ready (Connected)"
        is NoDeviceFound -> "Meta Session: No paired glasses found"
        is SessionError -> "Meta Session Error: $message"

        is Error -> "Meta DAT: Error: $message"
    }

    override fun toString(): String = getUserMessage()
}
