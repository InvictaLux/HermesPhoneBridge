package com.example.hermesbridge.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

sealed class PermissionState {
    object Ready : PermissionState()
    data class Missing(val permissions: List<String>) : PermissionState()
}

class MetaPermissionManager(private val context: Context) {

    /**
     * Minimal permissions needed for Meta DAT session discovery (Gate 8B).
     * RECORD_AUDIO and CAMERA are delayed until specific hardware gates.
     */
    val requiredPermissions: List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        // For API 29-30, Bluetooth permissions are normal (not runtime), 
        // but scanning might require Location.
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun checkPermissionState(): PermissionState {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        return if (missing.isEmpty()) {
            PermissionState.Ready
        } else {
            PermissionState.Missing(missing)
        }
    }

    fun isReady(): Boolean = checkPermissionState() is PermissionState.Ready

    fun isBluetoothGranted(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isMicrophoneGranted(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    fun isNotificationsGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
