package com.example.hermesbridge.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class PoolLocationManager(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _status = MutableStateFlow<PoolLocationStatus>(PoolLocationStatus.Idle)
    val status: StateFlow<PoolLocationStatus> = _status.asStateFlow()

    suspend fun getCurrentLocation(): Location? {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            _status.value = PoolLocationStatus.PermissionRequired
            return null
        }

        _status.value = PoolLocationStatus.Checking
        return try {
            val cancellationTokenSource = CancellationTokenSource()
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).await()

            if (location != null) {
                _status.value = PoolLocationStatus.Success(
                    location.latitude,
                    location.longitude,
                    location.accuracy
                )
            } else {
                _status.value = PoolLocationStatus.LocationUnavailable
            }
            location
        } catch (e: Exception) {
            Log.e("PoolLocationManager", "Error getting location", e)
            _status.value = PoolLocationStatus.Error(e.message ?: "Unknown error")
            null
        }
    }
}
