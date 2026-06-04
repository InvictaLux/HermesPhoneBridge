package com.example.hermesbridge.meta

import android.app.Activity
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
                    Log.d("HermesBridge", "Meta DAT Registration State Changed: $state")
                    updateStatusFromState(state)
                }
            }

            // 3. Observe registration errors (Gate 7E)
            scope.launch {
                Wearables.registrationErrorStream.collect { error ->
                    Log.e("HermesBridge", "Meta DAT Registration Error: $error")
                    // Map specific errors if needed, but for now just show failed.
                    // Version 0.7.0 might have META_AI_NOT_INSTALLED in error stream too.
                    val errorMessage = error.toString()
                    if (errorMessage.contains("META_AI_NOT_INSTALLED")) {
                        _status.value = MetaDatStatus.MissingMetaApp
                    } else {
                        _status.value = MetaDatStatus.RegistrationFailed
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("HermesBridge", "Failed to initialize Meta DAT SDK", e)
            _status.value = MetaDatStatus.Error(e.message ?: "Unknown error")
        }
    }

    private fun updateStatusFromState(state: RegistrationState) {
        _status.value = when (state) {
            RegistrationState.REGISTERED -> MetaDatStatus.Ready
            RegistrationState.REGISTERING -> MetaDatStatus.Initializing
            RegistrationState.AVAILABLE -> MetaDatStatus.RegistrationRequired
            RegistrationState.UNAVAILABLE -> MetaDatStatus.MissingMetaApp
            else -> MetaDatStatus.Error("State: $state")
        }
    }

    /**
     * Triggers a log-based refresh of current state. 
     * Since the SDK uses StateFlows, the status property is already reactive.
     */
    fun refreshStatus() {
        val currentState = Wearables.registrationState.value
        Log.d("HermesBridge", "Manual Status Refresh. Current State: $currentState")
        updateStatusFromState(currentState)
    }

    fun startRegistration(activity: Activity) {
        try {
            Log.d("HermesBridge", "Launching Meta DAT Registration...")
            _status.value = MetaDatStatus.Initializing
            Wearables.startRegistration(activity)
        } catch (e: Exception) {
            Log.e("HermesBridge", "Failed to launch registration", e)
            _status.value = MetaDatStatus.Error("Launch failed: ${e.message}")
        }
    }
}
