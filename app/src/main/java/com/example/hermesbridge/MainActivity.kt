package com.example.hermesbridge

import android.content.Intent
import android.os.Build
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
import com.example.hermesbridge.meta.MetaDatStatus
import com.example.hermesbridge.permissions.MetaPermissionManager
import com.example.hermesbridge.permissions.PermissionState
import com.example.hermesbridge.audio.BluetoothAudioRouteStatus
import com.example.hermesbridge.audio.PcmCaptureManager
import com.example.hermesbridge.conversation.ConversationTurnCoordinator
import com.example.hermesbridge.conversation.ConversationTurnState
import com.example.hermesbridge.metrics.InteractionMetricsCollector
import com.example.hermesbridge.service.WakeServiceState
import com.example.hermesbridge.service.WakeWordForegroundService
import com.example.hermesbridge.trigger.MetaWearableTurnTrigger
import com.example.hermesbridge.wakeword.WakeWordConversationCoordinator
import com.example.hermesbridge.wakeword.WakeWordTestManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AgentViewModel
    private lateinit var app: HermesBridgeApp
    private lateinit var permissionManager: MetaPermissionManager
    private lateinit var pcmCaptureManager: PcmCaptureManager
    private lateinit var turnTrigger: MetaWearableTurnTrigger

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filter { !it.value }.keys
        if (denied.isEmpty()) {
            Log.d("HermesBridge", "All permissions granted.")
            refreshAllStatuses()
        } else {
            Log.w("HermesBridge", "Permissions denied: $denied")
            app.bridgeController.updatePermissionMessage("Permissions denied: ${denied.joinToString(", ")}")
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

            app.bridgeController.updateBatteryState(batteryPct, isCharging)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as HermesBridgeApp

        permissionManager = MetaPermissionManager(this)
        val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        pcmCaptureManager = PcmCaptureManager(this, audioManager)
        turnTrigger = MetaWearableTurnTrigger()

        val factory = AgentViewModelFactory(application, app.bridgeController)
        viewModel = ViewModelProvider(this, factory)[AgentViewModel::class.java]

        // Proxy commands to managers
        lifecycleScope.launch {
            viewModel.commands.collect { command ->
                when (command) {
                    is UiCommand.LaunchMetaDatRegistration -> app.metaDatManager.startRegistration(this@MainActivity)
                    is UiCommand.RequestMetaPermissions -> permissionLauncher.launch(permissionManager.requiredPermissions.toTypedArray())
                    is UiCommand.CreateMetaSession -> app.metaDatManager.createDeviceSession()
                    is UiCommand.CloseMetaSession -> app.metaDatManager.closeDeviceSession()
                    is UiCommand.ReconnectMetaSession -> app.metaDatManager.reconnectDeviceSession()
                    is UiCommand.RefreshMetaSession -> app.metaDatManager.refreshSessionHealth()
                    is UiCommand.DiscoverMetaCapabilities -> app.metaDatManager.discoverCapabilities()
                    is UiCommand.DiscoverMetaAudioApi -> app.metaDatManager.discoverAudioApi()
                    is UiCommand.StartBluetoothAudioRoute -> app.audioRouteManager.startBluetoothRoute()
                    is UiCommand.StopBluetoothAudioRoute -> app.audioRouteManager.stopBluetoothRoute()
                    is UiCommand.CapturePcmSample -> pcmCaptureManager.startCapture()
                    is UiCommand.StartWearableSpeechTest -> app.turnCoordinator.startWearableTurn()
                    is UiCommand.StopWearableSpeechTest -> app.turnCoordinator.cancelTurn()
                    is UiCommand.NewSession -> app.bridgeController.startNewSession()
                    is UiCommand.StartWakeWordTest -> app.wakeWordManager.startTest()
                    is UiCommand.StopWakeWordTest -> app.wakeWordManager.stopTest()
                    is UiCommand.EnableWakeMode -> startWakeService()
                    is UiCommand.DisableWakeMode -> stopWakeService()
                    is UiCommand.MarkMissedWake -> app.bridgeController.onMissedDetection()
                    is UiCommand.ResetMetrics -> app.metricsCollector.resetMetrics()
                    is UiCommand.ExportMetrics -> exportMetrics()
                    is UiCommand.TogglePauseWake -> togglePauseWake()
                    is UiCommand.SetScreenOffLimit -> app.bridgeController.updateScreenOffLimit(command.mins)
                    is UiCommand.ManualMediaPause -> app.mediaCoexistenceManager.manualPause()
                    is UiCommand.ManualMediaResume -> app.mediaCoexistenceManager.manualResume()
                    is UiCommand.ToggleMediaAutoPause -> app.mediaCoexistenceManager.setAutoPauseEnabled(!app.bridgeController.uiState.value.mediaState.isAutoPauseEnabled)
                    is UiCommand.SetMediaResumePolicy -> app.mediaCoexistenceManager.setResumePolicy(command.policy)
                }
            }
        }

        // Hardware trigger
        turnTrigger.setListener { app.turnCoordinator.startWearableTurn() }
        turnTrigger.start()

        // Sync coordinators with global state loop (already happening in components)
        
        // Background Service logic: If service is running, it owns the coordinators.
        // For now, let's keep it simple.

        registerReceiver(batteryReceiver, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))

        setContent {
            MaterialTheme {
                AgentScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())
            }
        }
    }

    private fun startWakeService() {
        val intent = Intent(this, WakeWordForegroundService::class.java).apply {
            action = WakeWordForegroundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopWakeService() {
        val intent = Intent(this, WakeWordForegroundService::class.java).apply {
            action = WakeWordForegroundService.ACTION_STOP
        }
        startService(intent)
    }

    private fun togglePauseWake() {
        val currentState = app.bridgeController.uiState.value.wakeServiceState
        val action = if (currentState == WakeServiceState.Stopped) {
            WakeWordForegroundService.ACTION_RESUME
        } else {
            WakeWordForegroundService.ACTION_PAUSE
        }
        val intent = Intent(this, WakeWordForegroundService::class.java).apply {
            this.action = action
        }
        startService(intent)
    }

    private fun exportMetrics() {
        val summary = app.metricsCollector.generateExportSummary()
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
        app.metaDatManager.refreshStatus()
        app.audioRouteManager.refreshRouteStatus()
        val state = permissionManager.checkPermissionState()
        val msg = when (state) {
            is PermissionState.Ready -> "Meta Permissions: Ready"
            is PermissionState.Missing -> "Meta Permissions: Required"
        }
        app.bridgeController.updatePermissionMessage(msg)
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopSpeaking()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(batteryReceiver) } catch (e: Exception) { }
        turnTrigger.stop()
    }
}
