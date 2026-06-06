package com.example.hermesbridge.metrics

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InteractionMetricsCollector(private val context: Context) {
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    
    private val _reliabilityStats = MutableStateFlow(WakeReliabilityStats())
    val reliabilityStats: StateFlow<WakeReliabilityStats> = _reliabilityStats.asStateFlow()

    private val _lastLatency = MutableStateFlow(LatencyBreakdown())
    val lastLatency: StateFlow<LatencyBreakdown> = _lastLatency.asStateFlow()

    private var sessionStartTime: Long = 0
    private var listeningStartTime: Long = 0
    private var bluetoothRouteStartTime: Long = 0
    private var totalBluetoothActiveMs: Long = 0
    private var serviceStartTime: Long = 0
    private var screenOffStartTime: Long = 0

    fun onServiceStarted() {
        serviceStartTime = SystemClock.elapsedRealtime()
    }

    fun onServiceStopped() {
        if (serviceStartTime > 0) {
            val duration = SystemClock.elapsedRealtime() - serviceStartTime
            _reliabilityStats.update { it.copy(serviceRuntimeMs = it.serviceRuntimeMs + duration) }
            serviceStartTime = 0
        }
    }

    fun onScreenOff() {
        if (listeningStartTime > 0) {
            screenOffStartTime = SystemClock.elapsedRealtime()
        }
    }

    fun onScreenOn() {
        if (screenOffStartTime > 0) {
            val duration = SystemClock.elapsedRealtime() - screenOffStartTime
            _reliabilityStats.update { it.copy(screenOffListeningMs = it.screenOffListeningMs + duration) }
            screenOffStartTime = 0
        }
    }

    fun onWakeModeEnabled() {
        sessionStartTime = SystemClock.elapsedRealtime()
        takeBatterySnapshot("WakeModeEnabled")
    }

    fun onWakeListeningStarted() {
        listeningStartTime = SystemClock.elapsedRealtime()
        _reliabilityStats.update { it.copy(restartCount = it.restartCount + 1) }
    }

    fun onWakeListeningStopped() {
        if (listeningStartTime > 0) {
            val duration = SystemClock.elapsedRealtime() - listeningStartTime
            _reliabilityStats.update { it.copy(totalListeningMs = it.totalListeningMs + duration) }
            listeningStartTime = 0
        }
    }

    fun onWakeDetected(latencyMs: Long) {
        _reliabilityStats.update { it.copy(detections = it.detections + 1) }
        _lastLatency.update { LatencyBreakdown().apply { 
            wakeDetected = SystemClock.elapsedRealtime()
            wakeDetectionStart = wakeDetected - latencyMs
        }}
        takeBatterySnapshot("WakeDetected")
    }

    fun recordLatencyEvent(action: (LatencyBreakdown) -> Unit) {
        _lastLatency.update { 
            val new = it.copy()
            action(new)
            new
        }
    }

    fun onTrueDetection() {
        _reliabilityStats.update { it.copy(trueDetections = it.trueDetections + 1) }
    }

    fun onFalseDetection() {
        _reliabilityStats.update { it.copy(falseDetections = it.falseDetections + 1) }
    }

    fun onMissedDetection() {
        _reliabilityStats.update { it.copy(missedDetections = it.missedDetections + 1) }
    }

    fun onBluetoothRouteStarted() {
        bluetoothRouteStartTime = SystemClock.elapsedRealtime()
    }

    fun onBluetoothRouteStopped() {
        if (bluetoothRouteStartTime > 0) {
            totalBluetoothActiveMs += (SystemClock.elapsedRealtime() - bluetoothRouteStartTime)
            bluetoothRouteStartTime = 0
        }
    }

    fun onRouteLoss() {
        _reliabilityStats.update { it.copy(routeLossCount = it.routeLossCount + 1) }
    }

    fun resetMetrics() {
        _reliabilityStats.value = WakeReliabilityStats()
        _lastLatency.value = LatencyBreakdown()
        totalBluetoothActiveMs = 0
        if (listeningStartTime > 0) listeningStartTime = SystemClock.elapsedRealtime()
        if (bluetoothRouteStartTime > 0) bluetoothRouteStartTime = SystemClock.elapsedRealtime()
    }

    fun takeBatterySnapshot(tag: String): BatterySnapshot {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (scale > 0) (level * 100 / scale) else -1
        
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val volt = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        
        val current = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        } else null

        val charge = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        } else null

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val snapshot = BatterySnapshot(
            timestamp = System.currentTimeMillis(),
            level = pct,
            temperature = temp,
            voltage = volt,
            currentNow = current,
            chargeCounter = charge,
            isCharging = isCharging
        )
        
        Log.i("HermesMetrics", "Battery Snapshot [$tag]: $pct%, $temp°C, ${current ?: "N/A"}uA")
        return snapshot
    }

    fun getBluetoothUptimeMs(): Long {
        var active = totalBluetoothActiveMs
        if (bluetoothRouteStartTime > 0) {
            active += (SystemClock.elapsedRealtime() - bluetoothRouteStartTime)
        }
        return active
    }

    fun generateExportSummary(): String {
        val stats = _reliabilityStats.value
        val lat = _lastLatency.value
        val sb = StringBuilder()
        sb.append("Hermes Phone Bridge - Metrics Summary\n")
        sb.append("====================================\n")
        sb.append("Device: ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\n")
        sb.append("Date: ${java.util.Date()}\n\n")
        
        sb.append("--- Wake Word Stats ---\n")
        sb.append("Listening Time: ${stats.totalListeningMs / 1000}s\n")
        sb.append("Detections: ${stats.detections}\n")
        sb.append("True: ${stats.trueDetections}, False: ${stats.falseDetections}, Missed: ${stats.missedDetections}\n")
        sb.append("False/Hour: ${"%.2f".format(stats.getFalseDetectionsPerHour())}\n")
        sb.append("Service Runtime: ${stats.serviceRuntimeMs / 1000}s\n")
        sb.append("Screen-off Listening: ${stats.screenOffListeningMs / 1000}s\n\n")
        
        sb.append("--- Latency (Last Turn) ---\n")
        sb.append("Wake Detection: ${lat.getWakeDetectionLatency()}ms\n")
        sb.append("Wake to Listen: ${lat.getWakeToListenLatency()}ms\n")
        sb.append("STT Final: ${lat.getSpeechStartToFinal()}ms\n")
        sb.append("Backend RTT: ${lat.getBackendLatency()}ms\n")
        sb.append("Total Wake-to-Response: ${lat.getTotalWakeToResponse()}ms\n\n")
        
        sb.append("--- Connectivity ---\n")
        sb.append("BT Route Uptime: ${getBluetoothUptimeMs() / 1000}s\n")
        sb.append("Route Loss Count: ${stats.routeLossCount}\n\n")
        
        val currentBattery = takeBatterySnapshot("Export")
        sb.append("--- Final Battery ---\n")
        sb.append("Level: ${currentBattery.level}%\n")
        sb.append("Temp: ${currentBattery.temperature}°C\n")
        sb.append("Current: ${currentBattery.currentNow ?: "N/A"}uA\n")
        
        return sb.toString()
    }
}
