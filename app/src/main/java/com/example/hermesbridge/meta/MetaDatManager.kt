package com.example.hermesbridge.meta

import android.content.Context
import android.util.Log
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MetaDatManager(private val context: Context) {
    private val _status = MutableStateFlow<MetaDatStatus>(MetaDatStatus.NotInitialized)
    val status: StateFlow<MetaDatStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)

    fun initialize() {
        if (_status.value !is MetaDatStatus.NotInitialized && _status.value !is MetaDatStatus.Error) {
            return
        }

        _status.value = MetaDatStatus.Initializing
        Log.d("HermesBridge", "Initializing Meta DAT SDK...")

        try {
            // 1. Initialize the SDK
            Wearables.initialize(context.applicationContext)

            // 2. Observe registration state
            scope.launch {
                Wearables.registrationState.collect { state ->
                    Log.d("HermesBridge", "Meta DAT Registration State: $state")
                    _status.value = when (state) {
                        RegistrationState.REGISTERED -> MetaDatStatus.Ready
                        RegistrationState.REGISTERING -> MetaDatStatus.Initializing
                        RegistrationState.AVAILABLE -> MetaDatStatus.RegistrationRequired
                        RegistrationState.UNAVAILABLE -> MetaDatStatus.MissingMetaApp
                        else -> MetaDatStatus.Error("State: $state")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("HermesBridge", "Failed to initialize Meta DAT SDK", e)
            _status.value = MetaDatStatus.Error(e.message ?: "Unknown error")
        }
    }
}
