package com.example.hermesbridge.meta

sealed class MetaDatStatus {
    object NotInitialized : MetaDatStatus()
    object Initializing : MetaDatStatus()
    object Ready : MetaDatStatus()
    object MissingMetaApp : MetaDatStatus()
    object RegistrationRequired : MetaDatStatus()
    object PermissionRequired : MetaDatStatus()
    data class Error(val message: String) : MetaDatStatus()

    override fun toString(): String = when (this) {
        is NotInitialized -> "Not Checked"
        is Initializing -> "Initializing..."
        is Ready -> "Ready"
        is MissingMetaApp -> "Meta AI App Missing"
        is RegistrationRequired -> "Registration Required"
        is PermissionRequired -> "Permission Required"
        is Error -> "Error: $message"
    }
}
