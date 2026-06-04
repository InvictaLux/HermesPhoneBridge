package com.example.hermesbridge.meta

import android.app.Activity
import android.content.Context
import android.util.Log
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
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

            // 3. Observe registration errors
            scope.launch {
                Wearables.registrationErrorStream.collect { error ->
                    Log.e("HermesBridge", "Meta DAT Registration Error: $error")
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
        // If we are already in a session state, don't revert to Ready unless requested
        val current = _status.value
        if (current is MetaDatStatus.SessionReady || current is MetaDatStatus.NoDeviceFound || current is MetaDatStatus.SessionChecking) {
            if (state != RegistrationState.REGISTERED) {
                // We lost registration
                _status.value = MetaDatStatus.RegistrationRequired
            }
            return
        }

        _status.value = when (state) {
            RegistrationState.REGISTERED -> MetaDatStatus.Ready
            RegistrationState.REGISTERING -> MetaDatStatus.Initializing
            RegistrationState.AVAILABLE -> MetaDatStatus.RegistrationRequired
            RegistrationState.UNAVAILABLE -> MetaDatStatus.MissingMetaApp
            else -> MetaDatStatus.Error("State: $state")
        }
    }

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

    /**
     * Attempts to create a device session to discover if hardware is available. (Gate 8A)
     */
    fun checkDeviceSession() {
        if (Wearables.registrationState.value != RegistrationState.REGISTERED) {
            Log.w("HermesBridge", "Cannot check session: Registration not ready.")
            refreshStatus()
            return
        }

        _status.value = MetaDatStatus.SessionChecking
        Log.d("HermesBridge", "Checking for Meta wearable session...")

        try {
            val result = Wearables.createSession(AutoDeviceSelector())
            
            result.fold(
                onSuccess = { session ->
                    Log.i("HermesBridge", "Meta Device Session Created Successfully.")
                    // We do NOT call session.start() yet.
                    _status.value = MetaDatStatus.SessionReady
                },
                onFailure = { error, _ ->
                    Log.e("HermesBridge", "Meta Device Session Creation Failed: $error")
                    val errorMsg = error.toString()
                    if (errorMsg.contains("NO_ELIGIBLE_DEVICE")) {
                        _status.value = MetaDatStatus.NoDeviceFound
                    } else {
                        _status.value = MetaDatStatus.SessionError(errorMsg)
                    }
                }
            )
        } catch (e: SecurityException) {
            Log.e("HermesBridge", "Permission denied during session check", e)
            _status.value = MetaDatStatus.PermissionRequired
        } catch (e: Exception) {
            Log.e("HermesBridge", "Exception during session check", e)
            _status.value = MetaDatStatus.SessionError(e.message ?: "Unknown error")
        }
    }
}
