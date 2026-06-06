package com.example.hermesbridge.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.hermesbridge.MainActivity
import com.example.hermesbridge.HermesBridgeApp
import com.example.hermesbridge.meta.MetaDatStatus
import com.example.hermesbridge.audio.BluetoothAudioRouteStatus
import com.example.hermesbridge.conversation.ConversationTurnState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

class WakeWordForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var app: HermesBridgeApp
    private var state: WakeServiceState = WakeServiceState.Stopped
        set(value) {
            field = value
            app.bridgeController.updateWakeServiceState(value)
            updateNotification()
        }

    private var sessionRecoveryAttempts = 0
    private var routeRecoveryAttempts = 0
    private val MAX_RECOVERY_ATTEMPTS = 3
    private var isPausedByUser = false
    
    private val BATTERY_THRESHOLD = 15
    private val THERMAL_THRESHOLD = 45.0f // Celsius

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> app.metricsCollector.onScreenOff()
                Intent.ACTION_SCREEN_ON -> app.metricsCollector.onScreenOn()
            }
        }
    }

    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            checkBatteryPolicy()
        }
    }

    companion object {
        private const val CHANNEL_ID = "hermes_wake_channel"
        private const val NOTIFICATION_ID = 101
        const val ACTION_START = "START_WAKE"
        const val ACTION_STOP = "STOP_WAKE"
        const val ACTION_PAUSE = "PAUSE_WAKE"
        const val ACTION_RESUME = "RESUME_WAKE"
        
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        app = application as HermesBridgeApp
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundService()
            ACTION_STOP -> stopSelf()
            ACTION_PAUSE -> { isPausedByUser = true; state = WakeServiceState.Stopped; app.wakeWordManager.stopTest() }
            ACTION_RESUME -> { isPausedByUser = false; startDetectionLoop() }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        if (isRunning) return
        isRunning = true
        state = WakeServiceState.Starting
        startForeground(NOTIFICATION_ID, createNotification("Hermes Wake Mode", "Initializing..."))

        app.metricsCollector.onServiceStarted()
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        startDetectionLoop()
    }

    private fun startDetectionLoop() {
        if (isPausedByUser) return
        
        serviceScope.launch {
            combine(
                app.metaDatManager.status,
                app.audioRouteManager.status,
                app.turnCoordinator.turnState
            ) { metaStatus, audioStatus, turnState ->
                Triple(metaStatus, audioStatus, turnState)
            }.collectLatest { (metaStatus, audioStatus, turnState) ->
                handleSystemState(metaStatus, audioStatus, turnState)
            }
        }
    }

    private suspend fun handleSystemState(meta: MetaDatStatus, audio: BluetoothAudioRouteStatus, turn: ConversationTurnState) {
        if (turn !is ConversationTurnState.Idle && turn !is ConversationTurnState.Completed && turn !is ConversationTurnState.Error) {
            state = WakeServiceState.TurnActive
            app.wakeWordManager.stopTest()
            return
        }

        if (meta !is MetaDatStatus.SessionReady) {
            handleSessionLoss()
            return
        }
        sessionRecoveryAttempts = 0
        app.bridgeController.updateSessionRecoveryAttempts(0)

        if (audio !is BluetoothAudioRouteStatus.Routed) {
            handleRouteLoss()
            return
        }
        routeRecoveryAttempts = 0
        app.bridgeController.updateRouteRecoveryAttempts(0)

        if (checkBatteryPolicy()) return

        if (state !is WakeServiceState.Listening) {
            state = WakeServiceState.Listening
            app.wakeTurnCoordinator.setWakeModeEnabled(true)
        }
    }

    private suspend fun handleSessionLoss() {
        if (sessionRecoveryAttempts >= MAX_RECOVERY_ATTEMPTS) {
            state = WakeServiceState.Error("Session recovery failed")
            app.metricsCollector.setStopReason("Max session recovery attempts")
            return
        }
        state = WakeServiceState.RecoveringSession
        sessionRecoveryAttempts++
        app.bridgeController.updateSessionRecoveryAttempts(sessionRecoveryAttempts)
        app.metricsCollector.onSessionLoss()
        app.metaDatManager.reconnectDeviceSession()
        delay(5000L * sessionRecoveryAttempts)
    }

    private suspend fun handleRouteLoss() {
        if (routeRecoveryAttempts >= MAX_RECOVERY_ATTEMPTS) {
            state = WakeServiceState.Error("Route recovery failed")
            app.metricsCollector.setStopReason("Max route recovery attempts")
            return
        }
        state = WakeServiceState.RecoveringRoute
        routeRecoveryAttempts++
        app.bridgeController.updateRouteRecoveryAttempts(routeRecoveryAttempts)
        app.metricsCollector.onRouteLoss()
        app.audioRouteManager.startBluetoothRoute()
        delay(3000L * routeRecoveryAttempts)
    }

    private fun checkBatteryPolicy(): Boolean {
        val snapshot = app.metricsCollector.takeBatterySnapshot("PolicyCheck")
        app.bridgeController.updateBatterySnapshot(snapshot)

        if (snapshot.level < BATTERY_THRESHOLD && !snapshot.isCharging) {
            if (state !is WakeServiceState.PausedLowBattery) {
                state = WakeServiceState.PausedLowBattery
                app.wakeWordManager.stopTest()
                app.metricsCollector.onLowBatteryPause()
            }
            return true
        }

        if (snapshot.temperature > THERMAL_THRESHOLD) {
            if (state !is WakeServiceState.PausedThermal) {
                state = WakeServiceState.PausedThermal
                app.wakeWordManager.stopTest()
                app.metricsCollector.onThermalPause()
            }
            return true
        }

        return false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Hermes Wake Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification("Hermes Wake Mode", state.getUserMessage()))
    }

    private fun createNotification(title: String, text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, WakeWordForegroundService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val pauseIntent = Intent(this, WakeWordForegroundService::class.java).apply { action = ACTION_PAUSE }
        val pausePendingIntent = PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE)

        val resumeIntent = Intent(this, WakeWordForegroundService::class.java).apply { action = ACTION_RESUME }
        val resumePendingIntent = PendingIntent.getService(this, 3, resumeIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)

        if (isPausedByUser) {
            builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
        } else {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
        }

        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        state = WakeServiceState.Stopping
        app.wakeTurnCoordinator.setWakeModeEnabled(false)
        app.metricsCollector.onServiceStopped()
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) {}
        try { unregisterReceiver(batteryReceiver) } catch (e: Exception) {}
        serviceJob.cancel()
        state = WakeServiceState.Stopped
    }
}
