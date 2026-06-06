package com.example.hermesbridge.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.hermesbridge.MainActivity
import com.example.hermesbridge.HermesBridgeApp
import com.example.hermesbridge.wakeword.WakeWordStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class WakeWordForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var app: HermesBridgeApp

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> app.metricsCollector.onScreenOff()
                Intent.ACTION_SCREEN_ON -> app.metricsCollector.onScreenOn()
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "hermes_wake_channel"
        private const val NOTIFICATION_ID = 101
        const val ACTION_START = "START_WAKE"
        const val ACTION_STOP = "STOP_WAKE"
        
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("HermesService", "Foreground service onCreate")
        app = application as HermesBridgeApp
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    startForegroundService()
                }
            }
            ACTION_STOP -> {
                stopWakeMode()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        isRunning = true
        val notification = createNotification("Hermes Wake Mode Active", "Listening through Meta wearable")
        startForeground(NOTIFICATION_ID, notification)

        app.metricsCollector.onServiceStarted()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)

        serviceScope.launch {
            app.wakeTurnCoordinator.setWakeModeEnabled(true)
        }

        // Monitor service prerequisites
        serviceScope.launch {
            app.bridgeController.uiState.collectLatest { state ->
                // Stop service if route is lost or session ended and we are in background
                // For now, let the coordinators handle internal retries.
            }
        }
    }

    private fun stopWakeMode() {
        app.wakeTurnCoordinator.setWakeModeEnabled(false)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Hermes Wake Word Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WakeWordForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Wake Mode", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d("HermesService", "Foreground service onDestroy")
        stopWakeMode()
        app.metricsCollector.onServiceStopped()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) { }
        isRunning = false
        serviceJob.cancel()
    }
}
