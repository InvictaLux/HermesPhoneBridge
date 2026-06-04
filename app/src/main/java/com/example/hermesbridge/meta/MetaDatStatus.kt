package com.example.hermesbridge.meta

sealed class MetaDatStatus {
    object NotInitialized : MetaDatStatus()
    object Initializing : MetaDatStatus()
    object Ready : MetaDatStatus()
    object MissingMetaApp : MetaDatStatus()
    object RegistrationRequired : MetaDatStatus()
    object RegistrationFailed : MetaDatStatus()
    object PermissionRequired : MetaDatStatus()
    data class Error(val message: String) : MetaDatStatus()

    fun getUserMessage(): String = when (this) {
        is NotInitialized -> "Meta DAT: Not checked"
        is Initializing -> "Meta DAT: Checking registration..."
        is Ready -> "Meta DAT: Ready"
        is MissingMetaApp -> "Meta DAT: Meta AI companion app required"
        is RegistrationRequired -> "Meta DAT: Registration required"
        is RegistrationFailed -> "Meta DAT: Registration failed"
        is PermissionRequired -> "Meta DAT: Permission required"
        is Error -> "Meta DAT: Error: $message"
    }

    override fun toString(): String = getUserMessage()
}
