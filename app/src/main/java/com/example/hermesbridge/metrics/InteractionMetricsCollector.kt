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
    
    private var startBatteryLevel: Int = -1
    private var lastBatterySnapshot: BatterySnapshot? = null

    fun onWakeModeEnabled() {
        sessionStartTime = SystemClock.elapsedRealtime()
        val snapshot = takeBatterySnapshot("WakeModeEnabled")
        startBatteryLevel = snapshot.level
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
        _lastLatency.update { 
            it.recordDetection(latencyMs)
            it.copy().apply { 
                wakeDetected = SystemClock.elapsedRealtime()
                wakeDetectionStart = wakeDetected - latencyMs
            }
        }
        takeBatterySnapshot("WakeDetected")
    }

    fun recordLatencyEvent(action: (LatencyBreakdown) -> Unit) {
        _lastLatency.update { 
            val new = it.copy()
            action(new)
            new
        }
    }

    fun onTrueDetection() { _reliabilityStats.update { it.copy(trueDetections = it.trueDetections + 1) } }
    fun onFalseDetection() { _reliabilityStats.update { it.copy(falseDetections = it.falseDetections + 1) } }
    fun onMissedDetection() { _reliabilityStats.update { it.copy(missedDetections = it.missedDetections + 1) } }
    fun onDeliberateAttempt() { _reliabilityStats.update { it.copy(deliberateWakeAttempts = it.deliberateWakeAttempts + 1) } }

    fun onBluetoothRouteStarted() { bluetoothRouteStartTime = SystemClock.elapsedRealtime() }
    fun onBluetoothRouteStopped() {
        if (bluetoothRouteStartTime > 0) {
            totalBluetoothActiveMs += (SystemClock.elapsedRealtime() - bluetoothRouteStartTime)
            bluetoothRouteStartTime = 0
        }
    }

    fun onRouteLoss() {
        _reliabilityStats.update { it.copy(routeLossCount = it.routeLossCount + 1, routeRecoveryCount = it.routeRecoveryCount + 1) }
    }
    fun onSessionLoss() { _reliabilityStats.update { it.copy(sessionRecoveryCount = it.sessionRecoveryCount + 1) } }
    fun onLowBatteryPause() { _reliabilityStats.update { it.copy(lowBatteryPauses = it.lowBatteryPauses + 1, servicePauseCount = it.servicePauseCount + 1) } }
    fun onThermalPause() { _reliabilityStats.update { it.copy(thermalPauses = it.thermalPauses + 1, servicePauseCount = it.servicePauseCount + 1) } }
    fun onScreenOffLimitReached() { _reliabilityStats.update { it.copy(screenOffLimitReachedCount = it.screenOffLimitReachedCount + 1) } }
    fun setStopReason(reason: String) { _reliabilityStats.update { it.copy(stopReason = reason) } }
    fun onServiceStarted() { serviceStartTime = SystemClock.elapsedRealtime() }
    fun onServiceStopped() {
        if (serviceStartTime > 0) {
            val duration = SystemClock.elapsedRealtime() - serviceStartTime
            _reliabilityStats.update { it.copy(serviceRuntimeMs = it.serviceRuntimeMs + duration) }
            serviceStartTime = 0
        }
    }
    fun onScreenOff() { if (listeningStartTime > 0) screenOffStartTime = SystemClock.elapsedRealtime() }
    fun onScreenOn() {
        if (screenOffStartTime > 0) {
            val duration = SystemClock.elapsedRealtime() - screenOffStartTime
            _reliabilityStats.update { it.copy(screenOffListeningMs = it.screenOffListeningMs + duration) }
            screenOffStartTime = 0
        }
    }

    fun resetMetrics() {
        _reliabilityStats.value = WakeReliabilityStats()
        _lastLatency.value = LatencyBreakdown()
        totalBluetoothActiveMs = 0
        startBatteryLevel = lastBatterySnapshot?.level ?: -1
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

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val snapshot = BatterySnapshot(
            timestamp = System.currentTimeMillis(),
            level = pct,
            temperature = temp,
            voltage = volt,
            currentNow = current,
            chargeCounter = null,
            isCharging = isCharging
        )
        
        lastBatterySnapshot = snapshot
        _reliabilityStats.update { 
            it.copy(
                maxTemperature = if (temp > it.maxTemperature) temp else it.maxTemperature,
                batteryChange = if (startBatteryLevel >= 0) startBatteryLevel - pct else 0
            )
        }
        return snapshot
    }

    fun getBluetoothUptimeMs(): Long {
        var active = totalBluetoothActiveMs
        if (bluetoothRouteStartTime > 0) active += (SystemClock.elapsedRealtime() - bluetoothRouteStartTime)
        return active
    }

    fun generateExportSummary(): String {
        val stats = _reliabilityStats.value
        val lat = _lastLatency.value
        val sb = StringBuilder()
        sb.append("Hermes Bridge Tuning Summary\n")
        sb.append("============================\n")
        sb.append("Device: ${Build.MODEL}\n\n")
        
        sb.append("--- Reliability ---\n")
        sb.append("Attempts: ${stats.deliberateWakeAttempts}, Success: ${stats.trueDetections}, Missed: ${stats.missedDetections}\n")
        sb.append("Recall: ${"%.2f".format(stats.getRecall())}, Precision: ${"%.2f".format(stats.getPrecision())}\n")
        sb.append("False Detections: ${stats.falseDetections} (${"%.2f".format(stats.getFalseDetectionsPerHour())}/hr)\n")
        sb.append("Service Runtime: ${stats.serviceRuntimeMs / 1000}s\n")
        sb.append("Screen-off Listening: ${stats.screenOffListeningMs / 1000}s\n\n")
        
        sb.append("--- Latency ---\n")
        sb.append("p50: ${lat.getP50Latency()}ms, p95: ${lat.getP95Latency()}ms\n\n")
        
        sb.append("--- Resources ---\n")
        sb.append("Listening Time: ${stats.totalListeningMs / 1000}s\n")
        sb.append("Battery Change: ${stats.batteryChange}%\n")
        sb.append("Max Temp: ${stats.maxTemperature}°C\n")
        
        return sb.toString()
    }
}
