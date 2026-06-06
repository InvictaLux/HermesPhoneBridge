package com.example.hermesbridge

import android.content.Intent
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
import com.example.hermesbridge.bridge.MetaWearableInputSource
import com.example.hermesbridge.bridge.PhoneTextInputSource
import com.example.hermesbridge.meta.MetaDatManager
import com.example.hermesbridge.meta.MetaDatStatus
import com.example.hermesbridge.permissions.MetaPermissionManager
import com.example.hermesbridge.permissions.PermissionState
import com.example.hermesbridge.audio.BluetoothAudioRouteManager
import com.example.hermesbridge.audio.BluetoothAudioRouteStatus
import com.example.hermesbridge.audio.PcmCaptureManager
import com.example.hermesbridge.conversation.ConversationTurnCoordinator
import com.example.hermesbridge.metrics.InteractionMetricsCollector
import com.example.hermesbridge.trigger.MetaWearableTurnTrigger
import com.example.hermesbridge.wakeword.PorcupineWakeWordEngine
import com.example.hermesbridge.wakeword.WakeWordConversationCoordinator
import com.example.hermesbridge.wakeword.WakeWordTestManager
import com.example.hermesbridge.speech.AndroidSpeechRecognizerInput
import com.example.hermesbridge.speech.AndroidTtsSpeechOutput
import com.example.hermesbridge.speech.SpeechRecognitionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AgentViewModel
    private var speechOutput: AndroidTtsSpeechOutput? = null
    private lateinit var metaDatManager: MetaDatManager
    private lateinit var permissionManager: MetaPermissionManager
    private lateinit var audioRouteManager: BluetoothAudioRouteManager
    private lateinit var pcmCaptureManager: PcmCaptureManager
    private lateinit var speechToText: AndroidSpeechRecognizerInput
    private lateinit var wearableInputSource: MetaWearableInputSource
    private lateinit var turnCoordinator: ConversationTurnCoordinator
    private lateinit var turnTrigger: MetaWearableTurnTrigger
    private lateinit var wakeWordManager: WakeWordTestManager
    private lateinit var wakeTurnCoordinator: WakeWordConversationCoordinator
    private lateinit var metricsCollector: InteractionMetricsCollector

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
        wearableInputSource = MetaWearableInputSource()
        metaDatManager = MetaDatManager(this)
        turnTrigger = MetaWearableTurnTrigger()
        metricsCollector = InteractionMetricsCollector(this)

        val porcupineEngine = PorcupineWakeWordEngine(this, BuildConfig.PICOVOICE_ACCESS_KEY)
        wakeWordManager = WakeWordTestManager(this, porcupineEngine) {
            audioRouteManager.isRoutingToBluetooth()
        }

        // 1. Initialize Inputs and Repositories
        val phoneInputSource = PhoneTextInputSource()
        val api = OkHttpAgentApi()
        val repository = AgentRepository(api)

        // 2. Instantiate MVVM-lite ViewModel
        val factory = AgentViewModelFactory(
            application = this.application,
            repository = repository,
            inputSource = phoneInputSource
        )
        viewModel = ViewModelProvider(this, factory)[AgentViewModel::class.java]

        turnCoordinator = ConversationTurnCoordinator(
            lifecycleScope,
            viewModel,
            metaDatManager,
            audioRouteManager,
            speechToText,
            wearableInputSource,
            metricsCollector
        )

        wakeTurnCoordinator = WakeWordConversationCoordinator(
            lifecycleScope,
            viewModel,
            wakeWordManager,
            turnCoordinator,
            metricsCollector
        )

        // Connect wearable input source to the ViewModel logic
        wearableInputSource.setListener { event ->
            viewModel.submitExternalBridgeEvent(event)
        }
        wearableInputSource.start()

        // 3. Initialize Android's standard TTS response output player on the UI thread
        speechOutput = AndroidTtsSpeechOutput(this) { success ->
            if (success) {
                Log.d("MainActivity", "TextToSpeech successfully initialized")
                speechOutput?.let { viewModel.setSpeechOutput(it) }
            } else {
                Log.e("MainActivity", "TextToSpeech initialization failed")
            }
        }

        // 4. Status Observers
        lifecycleScope.launch {
            metaDatManager.status.collect { status ->
                viewModel.updateMetaDatStatus(status)
                if (status is MetaDatStatus.MissingMetaApp) {
                    viewModel.updateMetaDatMessage("Install or open the Meta AI companion app, then return here.")
                } else if (status is MetaDatStatus.Ready) {
                    viewModel.updateMetaDatMessage("Registration ready.")
                }
            }
        }
        
        lifecycleScope.launch {
            audioRouteManager.status.collect { status ->
                viewModel.updateAudioRouteStatus(status)
                if (status is BluetoothAudioRouteStatus.Routed) {
                    metricsCollector.onBluetoothRouteStarted()
                } else {
                    metricsCollector.onBluetoothRouteStopped()
                }
            }
        }
        
        lifecycleScope.launch {
            metricsCollector.reliabilityStats.collect { viewModel.updateReliabilityStats(it) }
        }
        
        lifecycleScope.launch {
            metricsCollector.lastLatency.collect { viewModel.updateLastLatency(it) }
        }

        lifecycleScope.launch {
            while (true) {
                viewModel.updateBtUptime(metricsCollector.getBluetoothUptimeMs())
                delay(1000)
            }
        }

        lifecycleScope.launch {
            wakeTurnCoordinator.isWakeModeEnabled.collect { viewModel.updateWakeModeEnabled(it) }
        }

        lifecycleScope.launch {
            wakeWordManager.status.collect { viewModel.updateWakeWordStatus(it) }
        }

        lifecycleScope.launch {
            wakeWordManager.lastDetection.collect { viewModel.updateLastWakeDetection(it) }
        }
        
        lifecycleScope.launch {
            turnCoordinator.turnState.collect { viewModel.updateConversationTurnState(it) }
        }
        
        // Command listener for UI actions
        lifecycleScope.launch {
            viewModel.commands.collect { command ->
                when (command) {
                    is UiCommand.LaunchMetaDatRegistration -> metaDatManager.startRegistration(this@MainActivity)
                    is UiCommand.RequestMetaPermissions -> permissionLauncher.launch(permissionManager.requiredPermissions.toTypedArray())
                    is UiCommand.CreateMetaSession -> metaDatManager.createDeviceSession()
                    is UiCommand.CloseMetaSession -> metaDatManager.closeDeviceSession()
                    is UiCommand.ReconnectMetaSession -> metaDatManager.reconnectDeviceSession()
                    is UiCommand.RefreshMetaSession -> metaDatManager.refreshSessionHealth()
                    is UiCommand.DiscoverMetaCapabilities -> metaDatManager.discoverCapabilities()
                    is UiCommand.DiscoverMetaAudioApi -> metaDatManager.discoverAudioApi()
                    is UiCommand.StartBluetoothAudioRoute -> audioRouteManager.startBluetoothRoute()
                    is UiCommand.StopBluetoothAudioRoute -> audioRouteManager.stopBluetoothRoute()
                    is UiCommand.CapturePcmSample -> pcmCaptureManager.startCapture()
                    is UiCommand.StartWearableSpeechTest -> turnCoordinator.startWearableTurn()
                    is UiCommand.StopWearableSpeechTest -> turnCoordinator.cancelTurn()
                    is UiCommand.NewSession -> viewModel.startNewSession()
                    is UiCommand.StartWakeWordTest -> wakeWordManager.startTest()
                    is UiCommand.StopWakeWordTest -> wakeWordManager.stopTest()
                    is UiCommand.EnableWakeMode -> wakeTurnCoordinator.setWakeModeEnabled(true)
                    is UiCommand.DisableWakeMode -> wakeTurnCoordinator.setWakeModeEnabled(false)
                    is UiCommand.MarkMissedWake -> metricsCollector.onMissedDetection()
                    is UiCommand.ResetMetrics -> metricsCollector.resetMetrics()
                    is UiCommand.ExportMetrics -> exportMetrics()
                }
            }
        }

        // Hardware trigger subscription (Gate 10C)
        turnTrigger.setListener { event ->
            Log.d("HermesBridge", "Hardware trigger: ${event.type}")
            turnCoordinator.startWearableTurn()
        }
        
        metaDatManager.initialize()
        turnTrigger.start()

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

    private fun exportMetrics() {
        val summary = metricsCollector.generateExportSummary()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Hermes Bridge Metrics Export")
            putExtra(Intent.EXTRA_TEXT, summary)
        }
        startActivity(Intent.createChooser(intent, "Export Metrics Summary"))
    }

    override fun onResume() {
        super.onResume()
        refreshAllStatuses()
    }

    private fun refreshAllStatuses() {
        if (::metaDatManager.isInitialized) metaDatManager.refreshStatus()
        if (::audioRouteManager.isInitialized) audioRouteManager.refreshRouteStatus()
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
        viewModel.stopSpeaking()
        if (::wakeTurnCoordinator.isInitialized) {
            wakeTurnCoordinator.setWakeModeEnabled(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) { }
        
        if (::metaDatManager.isInitialized) metaDatManager.closeDeviceSession()
        if (::audioRouteManager.isInitialized) audioRouteManager.stopBluetoothRoute()
        if (::pcmCaptureManager.isInitialized) pcmCaptureManager.stopCapture()
        if (::speechToText.isInitialized) speechToText.destroy()
        if (::turnTrigger.isInitialized) turnTrigger.stop()
        if (::wakeWordManager.isInitialized) wakeWordManager.release()
        if (::wakeTurnCoordinator.isInitialized) wakeTurnCoordinator.stop()

        wearableInputSource.stop()
        speechOutput?.shutdown()
    }
}
