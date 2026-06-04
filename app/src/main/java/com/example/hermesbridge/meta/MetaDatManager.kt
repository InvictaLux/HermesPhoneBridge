package com.example.hermesbridge.meta

import android.app.Activity
import android.content.Context
import android.util.Log
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceSessionError
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MetaDatManager(private val context: Context) {
    private val _status = MutableStateFlow<MetaDatStatus>(MetaDatStatus.NotInitialized)
    val status: StateFlow<MetaDatStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    
    private var currentSession: DeviceSession? = null
    private var sessionStateJob: Job? = null
    private var sessionErrorJob: Job? = null

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
        val current = _status.value
        // If we are already in a session state, handle registration loss
        if (isSessionState(current)) {
            if (state != RegistrationState.REGISTERED) {
                Log.w("HermesBridge", "Lost registration while in session.")
                _status.value = MetaDatStatus.RegistrationRequired
                closeDeviceSession()
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

    private fun isSessionState(status: MetaDatStatus): Boolean {
        return status is MetaDatStatus.SessionReady || 
               status is MetaDatStatus.SessionStarting || 
               status is MetaDatStatus.SessionStopping ||
               status is MetaDatStatus.SessionPaused ||
               status is MetaDatStatus.SessionDisconnected ||
               status is MetaDatStatus.SessionReconnecting ||
               status is MetaDatStatus.SessionError
    }

    fun refreshStatus() {
        val currentState = Wearables.registrationState.value
        Log.d("HermesBridge", "Manual Status Refresh. Current State: $currentState")
        
        if (currentSession != null) {
            refreshSessionHealth()
        } else {
            updateStatusFromState(currentState)
        }
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
     * Reconnect logic: Close existing session if it's dead, then create a new one.
     */
    fun reconnectDeviceSession() {
        Log.d("HermesBridge", "Reconnect requested.")
        if (currentSession != null) {
            closeDeviceSession()
        }
        createDeviceSession()
    }

    /**
     * Refresh health based on current session reference.
     */
    fun refreshSessionHealth() {
        val session = currentSession
        if (session == null) {
            Log.d("HermesBridge", "refreshSessionHealth: No active session.")
            refreshStatus()
            return
        }
        val sessionState = session.state.value
        Log.d("HermesBridge", "refreshSessionHealth: session.state=$sessionState")
        
        if (sessionState == DeviceSessionState.STOPPED) {
            Log.i("HermesBridge", "Detected stale stopped session. Cleaning up.")
            closeDeviceSession()
        }
    }

    /**
     * Acquisition of a device session and starting it. (Gate 8C/8D)
     */
    fun createDeviceSession() {
        if (Wearables.registrationState.value != RegistrationState.REGISTERED) {
            Log.w("HermesBridge", "Cannot create session: Registration not ready.")
            refreshStatus()
            return
        }

        if (currentSession != null) {
            Log.i("HermesBridge", "Session already exists. Use reconnect or refresh instead.")
            return
        }

        _status.value = MetaDatStatus.SessionStarting
        Log.d("HermesBridge", "Creating Meta wearable session...")

        try {
            val result = Wearables.createSession(AutoDeviceSelector())
            
            result.fold(
                onSuccess = { session ->
                    Log.i("HermesBridge", "Meta Device Session Acquired.")
                    currentSession = session
                    
                    // Observe session state
                    sessionStateJob?.cancel()
                    sessionStateJob = scope.launch {
                        session.state.collect { sessionState ->
                            Log.d("HermesBridge", "Session State Change: $sessionState")
                            _status.value = when (sessionState) {
                                DeviceSessionState.IDLE -> MetaDatStatus.SessionIdle
                                DeviceSessionState.STARTING -> MetaDatStatus.SessionStarting
                                DeviceSessionState.STARTED -> MetaDatStatus.SessionReady
                                DeviceSessionState.PAUSED -> MetaDatStatus.SessionPaused
                                DeviceSessionState.STOPPING -> MetaDatStatus.SessionStopping
                                DeviceSessionState.STOPPED -> MetaDatStatus.SessionStopped
                            }
                        }
                    }

                    // Observe session errors (Gate 8D)
                    sessionErrorJob?.cancel()
                    sessionErrorJob = scope.launch {
                        session.errors.collect { error ->
                            Log.e("HermesBridge", "Session Error: $error")
                            handleSessionError(error)
                        }
                    }

                    // Start the session (connection handshake)
                    session.start()
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
            Log.e("HermesBridge", "Permission denied during session creation", e)
            _status.value = MetaDatStatus.PermissionRequired
        } catch (e: Exception) {
            Log.e("HermesBridge", "Exception during session creation", e)
            _status.value = MetaDatStatus.SessionError(e.message ?: "Unknown error")
        }
    }

    private fun handleSessionError(error: DeviceSessionError) {
        when (error) {
            DeviceSessionError.DEVICE_DISCONNECTED -> {
                _status.value = MetaDatStatus.SessionDisconnected
            }
            DeviceSessionError.DEVICE_POWERED_OFF -> {
                _status.value = MetaDatStatus.SessionError("Glasses powered off")
            }
            else -> {
                _status.value = MetaDatStatus.SessionError(error.toString())
            }
        }
    }

    fun closeDeviceSession() {
        Log.d("HermesBridge", "Closing Meta device session...")
        try {
            currentSession?.stop()
        } catch (e: Exception) {
            Log.e("HermesBridge", "Error stopping session", e)
        } finally {
            currentSession = null
            sessionStateJob?.cancel()
            sessionStateJob = null
            sessionErrorJob?.cancel()
            sessionErrorJob = null
            
            // Clean state transition
            if (_status.value !is MetaDatStatus.Ready && !isRegistrationState(_status.value)) {
                _status.value = MetaDatStatus.SessionStopped
            }
        }
    }

    private fun isRegistrationState(status: MetaDatStatus): Boolean {
        return status is MetaDatStatus.Ready || 
               status is MetaDatStatus.RegistrationRequired ||
               status is MetaDatStatus.MissingMetaApp ||
               status is MetaDatStatus.NotInitialized
    }

    fun hasUsableSession(): Boolean {
        val session = currentSession ?: return false
        return session.state.value == DeviceSessionState.STARTED
    }
}
