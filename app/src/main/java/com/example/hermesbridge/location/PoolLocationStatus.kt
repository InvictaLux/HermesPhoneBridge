package com.example.hermesbridge.location

sealed class PoolLocationStatus {
    object Idle : PoolLocationStatus()
    object Checking : PoolLocationStatus()
    object PermissionRequired : PoolLocationStatus()
    object LocationDisabled : PoolLocationStatus()
    object LocationUnavailable : PoolLocationStatus()
    data class Success(val latitude: Double, val longitude: Double, val accuracy: Float) : PoolLocationStatus()
    data class Error(val message: String) : PoolLocationStatus()

    fun getUserMessage(): String = when (this) {
        is Idle -> ""
        is Checking -> "Checking location..."
        is PermissionRequired -> "Location permission required."
        is LocationDisabled -> "Location services are disabled."
        is LocationUnavailable -> "Location unavailable."
        is Success -> "Location acquired (±${accuracy.toInt()}m)."
        is Error -> "Location error: $message"
    }
}
