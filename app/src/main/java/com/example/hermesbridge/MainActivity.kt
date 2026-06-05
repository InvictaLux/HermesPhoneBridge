package com.example.hermesbridge

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.hermesbridge.bridge.PhoneTextInputSource
import com.example.hermesbridge.meta.MetaDatManager
import com.example.hermesbridge.meta.MetaDatStatus
import com.example.hermesbridge.permissions.MetaPermissionManager
import com.example.hermesbridge.permissions.PermissionState
import com.example.hermesbridge.audio.BluetoothAudioRouteManager
import com.example.hermesbridge.audio.PcmCaptureManager
import com.example.hermesbridge.speech.AndroidSpeechRecognizerInput
import com.example.hermesbridge.speech.AndroidTtsSpeechOutput
import com.example.hermesbridge.speech.SpeechRecognitionStatus
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AgentViewModel
    private var speechOutput: AndroidTtsSpeechOutput? = null
    private lateinit var metaDatManager: MetaDatManager
    private lateinit var permissionManager: MetaPermissionManager
    private lateinit var audioRouteManager: BluetoothAudioRouteManager
    private lateinit var pcmCaptureManager: PcmCaptureManager
    private lateinit var speechToText: AndroidSpeechRecognizerInput

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filter { !it.value }.keys
        if (denied.isEmpty()) {
            Log.d("HermesBridge", "All permissions granted.")
            refreshAllStatuses()
        } else {
            Log.w("HermesBridge", "Permissions denied: $denied")
            viewModel.updatePermissionMessage("Permissions denied: ${denied.joinToString(", ")}")
        }
    }

    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = if (level >= 0 && scale > 0) {
                (level * 100 / scale.toFloat()).toInt()
            } else {
                100
            }

            val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL

            viewModel.updateBatteryState(batteryPct, isCharging)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionManager = MetaPermissionManager(this)
        val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        audioRouteManager = BluetoothAudioRouteManager(this)
        pcmCaptureManager = PcmCaptureManager(this, audioManager)
        speechToText = AndroidSpeechRecognizerInput(this) {
            audioRouteManager.isRoutingToBluetooth() 
        }

        // 1. Initialize Inputs and Repositories
        val inputSource = PhoneTextInputSource()
        // Future Gate 6:
        // val inputSource = MetaWearableInputSource()
        val api = OkHttpAgentApi()
        val repository = AgentRepository(api)

        // 2. Instantiate MVVM-lite ViewModel
        val factory = AgentViewModelFactory(
            application = this.application,
            repository = repository,
            inputSource = inputSource
        )
        viewModel = ViewModelProvider(this, factory)[AgentViewModel::class.java]

        // 3. Initialize Android's standard TTS response output player on the UI thread
        speechOutput = AndroidTtsSpeechOutput(this) { success ->
            if (success) {
                Log.d("MainActivity", "TextToSpeech successfully initialized")
                speechOutput?.let { viewModel.setSpeechOutput(it) }
            } else {
                Log.e("MainActivity", "TextToSpeech initialization failed")
            }
        }

        // 4. Meta DAT Initialization Status Only (Gate 7C)
        metaDatManager = MetaDatManager(this)
        lifecycleScope.launch {
            metaDatManager.status.collect { status ->
                Log.d("HermesBridge", "Meta DAT Status Update: $status")
                viewModel.updateMetaDatStatus(status)
                
                // Guidance messaging (Gate 7F)
                if (status is MetaDatStatus.MissingMetaApp) {
                    viewModel.updateMetaDatMessage("Install or open the Meta AI companion app, then return here.")
                } else if (status is MetaDatStatus.Ready) {
                    viewModel.updateMetaDatMessage("Registration ready.")
                }
            }
        }
        lifecycleScope.launch {
            metaDatManager.capabilities.collect { caps ->
                viewModel.updateMetaCapabilities(caps)
            }
        }
        lifecycleScope.launch {
            metaDatManager.audioInfo.collect { audio ->
                viewModel.updateMetaAudioInfo(audio)
            }
        }
        
        lifecycleScope.launch {
            audioRouteManager.status.collect { status ->
                Log.d("HermesBridge", "Audio Route Status Update: $status")
                viewModel.updateAudioRouteStatus(status)
            }
        }

        lifecycleScope.launch {
            pcmCaptureManager.status.collect { status ->
                Log.d("HermesBridge", "PCM Capture Status Update: $status")
                viewModel.updatePcmCaptureStatus(status)
            }
        }

        lifecycleScope.launch {
            pcmCaptureManager.result.collect { result ->
                viewModel.updatePcmCaptureResult(result)
            }
        }

        lifecycleScope.launch {
            speechToText.status.collect { status ->
                Log.d("HermesBridge", "Speech Status Update: $status")
                viewModel.updateSpeechStatus(status)
            }
        }

        lifecycleScope.launch {
            speechToText.result.collect { result ->
                viewModel.updateSpeechResult(result)
            }
        }
        
        // Command listener for UI actions
        lifecycleScope.launch {
            viewModel.commands.collect { command ->
                when (command) {
                    is UiCommand.LaunchMetaDatRegistration -> {
                        viewModel.updateMetaDatMessage("Registration flow launched.")
                        metaDatManager.startRegistration(this@MainActivity)
                    }
                    is UiCommand.RequestMetaPermissions -> {
                        permissionLauncher.launch(permissionManager.requiredPermissions.toTypedArray())
                    }
                    is UiCommand.CreateMetaSession -> {
                        if (permissionManager.isReady()) {
                            viewModel.updateMetaDatMessage("Starting session...")
                            metaDatManager.createDeviceSession()
                        } else {
                            viewModel.updatePermissionMessage("Bluetooth permissions required for session.")
                        }
                    }
                    is UiCommand.CloseMetaSession -> {
                        viewModel.updateMetaDatMessage("Closing session...")
                        metaDatManager.closeDeviceSession()
                    }
                    is UiCommand.ReconnectMetaSession -> {
                        viewModel.updateMetaDatMessage("Reconnecting...")
                        metaDatManager.reconnectDeviceSession()
                    }
                    is UiCommand.RefreshMetaSession -> {
                        viewModel.updateMetaDatMessage("Refreshing health...")
                        metaDatManager.refreshSessionHealth()
                    }
                    is UiCommand.DiscoverMetaCapabilities -> {
                        viewModel.updateMetaDatMessage("Discovering capabilities...")
                        metaDatManager.discoverCapabilities()
                    }
                    is UiCommand.DiscoverMetaAudioApi -> {
                        viewModel.updateMetaDatMessage("Inspecting Audio API...")
                        metaDatManager.discoverAudioApi()
                    }
                    is UiCommand.StartBluetoothAudioRoute -> {
                        if (metaDatManager.hasUsableSession()) {
                            audioRouteManager.startBluetoothRoute()
                        } else {
                            viewModel.updateMetaDatMessage("Active session required for audio routing.")
                        }
                    }
                    is UiCommand.StopBluetoothAudioRoute -> {
                        audioRouteManager.stopBluetoothRoute()
                    }
                    is UiCommand.CapturePcmSample -> {
                        pcmCaptureManager.startCapture()
                    }
                    is UiCommand.StartWearableSpeechTest -> {
                        speechToText.startListening()
                    }
                    is UiCommand.StopWearableSpeechTest -> {
                        speechToText.stopListening()
                    }
                }
            }
        }
        
        metaDatManager.initialize()

        // Register battery status receiver
        registerReceiver(batteryReceiver, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))

        // 5. Inject layouts and Compose Theme
        setContent {
            MaterialTheme {
                AgentScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAllStatuses()
    }

    private fun refreshAllStatuses() {
        if (::metaDatManager.isInitialized) {
            metaDatManager.refreshStatus()
        }
        if (::audioRouteManager.isInitialized) {
            audioRouteManager.refreshRouteStatus()
        }
        if (::permissionManager.isInitialized) {
            val state = permissionManager.checkPermissionState()
            val msg = when (state) {
                is PermissionState.Ready -> "Meta Permissions: Ready"
                is PermissionState.Missing -> "Meta Permissions: Required"
            }
            viewModel.updatePermissionMessage(msg)
        }
    }

    override fun onStop() {
        super.onStop()
        // Stop speaking immediately when backgrounded
        viewModel.stopSpeaking()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Already unregistered or wasn't registered
        }
        // Release hardware synthesis bindings
        if (::metaDatManager.isInitialized) {
            metaDatManager.closeDeviceSession()
        }
        if (::audioRouteManager.isInitialized) {
            audioRouteManager.stopBluetoothRoute()
        }
        if (::pcmCaptureManager.isInitialized) {
            pcmCaptureManager.stopCapture()
        }
        if (::speechToText.isInitialized) {
            speechToText.destroy()
        }
        speechOutput?.shutdown()
    }
}
