package com.example.hermesbridge.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BluetoothAudioRouteManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _status = MutableStateFlow<BluetoothAudioRouteStatus>(BluetoothAudioRouteStatus.Idle)
    val status: StateFlow<BluetoothAudioRouteStatus> = _status.asStateFlow()

    private var previousMode: Int = AudioManager.MODE_NORMAL
    private var isRoutingActive = false

    fun startBluetoothRoute() {
        if (isRoutingActive) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.MODIFY_AUDIO_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            _status.value = BluetoothAudioRouteStatus.PermissionRequired
            return
        }

        _status.value = BluetoothAudioRouteStatus.Routing
        Log.d("HermesAudio", "Attempting to start Bluetooth audio route...")

        try {
            previousMode = audioManager.mode
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val bluetoothDevice = devices.find { 
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                }

                if (bluetoothDevice != null) {
                    val result = audioManager.setCommunicationDevice(bluetoothDevice)
                    if (result) {
                        isRoutingActive = true
                        _status.value = BluetoothAudioRouteStatus.Routed(
                            deviceName = bluetoothDevice.productName.toString(),
                            deviceType = bluetoothDevice.type.toString(),
                            mode = audioManager.mode
                        )
                        Log.d("HermesAudio", "Communication device set: ${bluetoothDevice.productName}. Mode: ${audioManager.mode}")
                    } else {
                        _status.value = BluetoothAudioRouteStatus.Error("Failed to set communication device")
                    }
                } else {
                    _status.value = BluetoothAudioRouteStatus.NoBluetoothCommunicationDevice
                }
            } else {
                // Fallback for API 29-30
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                isRoutingActive = true
                _status.value = BluetoothAudioRouteStatus.Routed("Legacy SCO", "HFP", audioManager.mode)
                Log.d("HermesAudio", "Legacy SCO started. Mode: ${audioManager.mode}")
            }
        } catch (e: Exception) {
            Log.e("HermesAudio", "Error starting audio route", e)
            _status.value = BluetoothAudioRouteStatus.Error(e.message ?: "Unknown error")
        }
    }

    fun stopBluetoothRoute() {
        if (!isRoutingActive) return

        _status.value = BluetoothAudioRouteStatus.Stopping
        Log.d("HermesAudio", "Stopping Bluetooth audio route...")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                audioManager.mode = previousMode
            }
            isRoutingActive = false
            _status.value = BluetoothAudioRouteStatus.Stopped
            Log.d("HermesAudio", "Audio route stopped and restored.")
        } catch (e: Exception) {
            Log.e("HermesAudio", "Error stopping audio route", e)
            _status.value = BluetoothAudioRouteStatus.Error(e.message ?: "Unknown error")
        }
    }

    fun refreshRouteStatus() {
        if (isRoutingActive) {
            Log.d("HermesAudio", "Refreshing route status. Mode: ${audioManager.mode}")
        }
    }

    fun isRoutingToBluetooth(): Boolean = isRoutingActive
}
