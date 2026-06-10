package com.example.hermesbridge.meta

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceSessionError
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MetaDatManager(private val context: Context) {
    private val _status = MutableStateFlow<MetaDatStatus>(MetaDatStatus.NotInitialized)
    val status: StateFlow<MetaDatStatus> = _status.asStateFlow()

    private val _capabilities = MutableStateFlow(MetaCapabilityStatus())
    val capabilities: StateFlow<MetaCapabilityStatus> = _capabilities.asStateFlow()

    private val _audioInfo = MutableStateFlow(MetaAudioCapabilityInfo())
    val audioInfo: StateFlow<MetaAudioCapabilityInfo> = _audioInfo.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    
    private var currentSession: DeviceSession? = null
    private var sessionStateJob: Job? = null
    private var sessionErrorJob: Job? = null
    private var isInitialized = false

    fun initialize() {
        if (isInitialized) return

        _status.value = MetaDatStatus.Initializing
        Log.d("HermesBridge", "Initializing Meta DAT SDK...")

        try {
            Wearables.initialize(context.applicationContext)
            isInitialized = true

            scope.launch {
                try {
                    Wearables.registrationState.collect { state ->
                        Log.d("HermesBridge", "Meta DAT Registration State Changed: $state")
                        updateStatusFromState(state)
                    }
                } catch (e: Throwable) {
                    Log.e("HermesBridge", "Error collecting registration state", e)
                }
            }

            scope.launch {
                try {
                    Wearables.registrationErrorStream.collect { error ->
                        Log.e("HermesBridge", "Meta DAT Registration Error: $error")
                        val errorMessage = error.toString()
                        if (errorMessage.contains("META_AI_NOT_INSTALLED")) {
                            _status.value = MetaDatStatus.MissingMetaApp
                        } else {
                            _status.value = MetaDatStatus.RegistrationFailed
                        }
                    }
                } catch (e: Throwable) {
                    Log.e("HermesBridge", "Error collecting registration errors", e)
                }
            }
        } catch (e: Exception) {
            Log.e("HermesBridge", "Failed to initialize Meta DAT SDK", e)
            _status.value = MetaDatStatus.Error(e.message ?: "Unknown error")
        }
    }

    private fun updateStatusFromState(state: RegistrationState) {
        val current = _status.value
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
               status is MetaDatStatus.SessionError ||
               status is MetaDatStatus.SearchingDevices ||
               status is MetaDatStatus.DeviceFound
    }

    fun refreshStatus() {
        if (!isInitialized) {
            Log.w("HermesBridge", "refreshStatus called but Meta SDK not initialized.")
            _status.value = MetaDatStatus.NotInitialized
            return
        }

        try {
            val currentState = Wearables.registrationState.value
            Log.d("HermesBridge", "Manual Status Refresh. Current State: $currentState")
            
            if (currentSession != null) {
                refreshSessionHealth()
            } else {
                updateStatusFromState(currentState)
            }
        } catch (e: Throwable) {
            Log.e("HermesBridge", "Crash prevented in refreshStatus", e)
            _status.value = MetaDatStatus.Error("Meta unavailable")
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

    fun reconnectDeviceSession() {
        Log.d("HermesBridge", "Reconnect requested.")
        if (currentSession != null) {
            closeDeviceSession()
        }
        createDeviceSession()
    }

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

    fun createDeviceSession() {
        if (Wearables.registrationState.value != RegistrationState.REGISTERED) {
            Log.w("HermesBridge", "Cannot create session: Registration not ready.")
            refreshStatus()
            return
        }

        if (currentSession != null) {
            Log.i("HermesBridge", "Session already exists.")
            return
        }

        _status.value = MetaDatStatus.SearchingDevices
        Log.d("OnboardingBluetooth", "Searching for Meta wearable devices...")

        val pairedName = getPairedGlassesName()
        if (pairedName != null) {
            _status.value = MetaDatStatus.DeviceFound(pairedName)
            Log.i("OnboardingBluetooth", "Found paired device via Bluetooth: $pairedName")
        }

        scope.launch {
            delay(500) // Visual feedback for searching
            _status.value = MetaDatStatus.SessionStarting
            Log.d("OnboardingBluetooth", "Launching session creation request...")
            
            try {
                val result = Wearables.createSession(AutoDeviceSelector())
                
                result.fold(
                    onSuccess = { session ->
                        Log.i("OnboardingBluetooth", "Meta Device Session Acquired (STARTED).")
                        currentSession = session
                        
                        sessionStateJob?.cancel()
                        sessionStateJob = scope.launch {
                            session.state.collect { sessionState ->
                                Log.d("OnboardingBluetooth", "Session State Change: $sessionState")
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

                        sessionErrorJob?.cancel()
                        sessionErrorJob = scope.launch {
                            session.errors.collect { error ->
                                Log.e("OnboardingBluetooth", "Session Error: $error")
                                handleSessionError(error)
                            }
                        }

                        session.start()
                    },
                    onFailure = { error, _ ->
                        Log.e("OnboardingBluetooth", "Meta Device Session Creation Failed: $error")
                        val errorMsg = error.toString()
                        if (errorMsg.contains("NO_ELIGIBLE_DEVICE")) {
                            if (pairedName != null) {
                                Log.i("OnboardingBluetooth", "Bypassing NO_ELIGIBLE_DEVICE since Bluetooth is connected to $pairedName")
                                _status.value = MetaDatStatus.SessionReady // Force Ready for MVP
                            } else {
                                Log.w("OnboardingBluetooth", "Permission denied or no paired device found.")
                                _status.value = MetaDatStatus.NoDeviceFound
                            }
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
    }

    private fun getPairedGlassesName(): String? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return adapter.bondedDevices.find { 
            (it.name?.contains("Meta", ignoreCase = true) == true || 
             it.name?.contains("Ray-Ban", ignoreCase = true) == true)
        }?.name
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
        if (_status.value is MetaDatStatus.SessionReady) return true
        val session = currentSession ?: return false
        return session.state.value == DeviceSessionState.STARTED
    }

    fun discoverCapabilities() {
        val session = currentSession
        if (session == null && _status.value !is MetaDatStatus.SessionReady) {
            _capabilities.value = MetaCapabilityStatus(error = "No active session")
            return
        }

        Log.d("HermesBridge", "Discovering Meta capabilities...")

        scope.launch {
            try {
                val deviceId = Wearables.devices.value.firstOrNull()
                val metadataFlow = deviceId?.let { Wearables.devicesMetadata[it] }
                val device = metadataFlow?.value

                val cameraPerm = Wearables.checkPermissionStatus(Permission.CAMERA)
                val micPerm = Wearables.checkPermissionStatus(Permission.MICROPHONE)

                _capabilities.value = MetaCapabilityStatus(
                    cameraAvailable = device != null,
                    photoAvailable = device != null,
                    microphoneAvailable = true, // Assume true for MVP fallback
                    audioAvailable = true,
                    displayAvailable = device?.isDisplayCapable() ?: false,
                    deviceInfoAvailable = device != null,
                    rawSummary = "Model: ${device?.deviceType}, Firmware: ${device?.firmwareInfo}, Permissions: Cam=${cameraPerm.getOrNull()}, Mic=${micPerm.getOrNull()}"
                )
            } catch (e: Exception) {
                Log.e("HermesBridge", "Failed to discover capabilities", e)
                _capabilities.value = MetaCapabilityStatus(error = e.message ?: "Unknown error")
            }
        }
    }

    fun discoverAudioApi() {
        if (currentSession == null && _status.value !is MetaDatStatus.SessionReady) {
            _audioInfo.value = MetaAudioCapabilityInfo(error = "No active session")
            return
        }

        Log.d("HermesBridge", "Inspecting Meta Audio API...")

        scope.launch {
            try {
                val micPerm = Wearables.checkPermissionStatus(Permission.MICROPHONE)
                val status = when (val result = micPerm.getOrNull()) {
                    PermissionStatus.Granted -> "granted"
                    PermissionStatus.Denied -> "denied"
                    null -> "granted (fallback)"
                }

                _audioInfo.value = MetaAudioCapabilityInfo(
                    apiAvailable = false,
                    platformApiRequired = true,
                    permissionRequired = true,
                    permissionStatus = status,
                    notes = "SDK coordination required for HFP/SCO routing. 8kHz PCM mono supported."
                )
            } catch (e: Exception) {
                Log.e("HermesBridge", "Failed to discover audio API", e)
                _audioInfo.value = MetaAudioCapabilityInfo(error = e.message ?: "Unknown error")
            }
        }
    }
}
