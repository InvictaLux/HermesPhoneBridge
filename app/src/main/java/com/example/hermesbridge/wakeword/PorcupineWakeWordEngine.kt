package com.example.hermesbridge.wakeword

import android.content.Context
import android.os.SystemClock
import android.util.Log
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException

class PorcupineWakeWordEngine(
    private val context: Context,
    private val accessKey: String,
    private val keyword: Porcupine.BuiltInKeyword = Porcupine.BuiltInKeyword.PORCUPINE
) : WakeWordEngine {

    private var porcupine: Porcupine? = null
    private var currentSensitivity: Float = 0.5f
    override val sampleRate: Int = 16000
    override val frameLength: Int
        get() = porcupine?.frameLength ?: 512

    private var startTime: Long = 0

    override fun start() {
        try {
            if (porcupine == null) {
                porcupine = Porcupine.Builder()
                    .setAccessKey(accessKey)
                    .setKeyword(keyword)
                    .setSensitivity(currentSensitivity)
                    .build(context)
            }
            startTime = SystemClock.elapsedRealtime()
            Log.i("HermesWake", "Porcupine engine started. Sensitivity: $currentSensitivity")
        } catch (e: PorcupineException) {
            Log.e("HermesWake", "Failed to initialize Porcupine", e)
        }
    }

    override fun processFrame(frame: ShortArray): WakeWordDetection? {
        val p = porcupine ?: return null
        try {
            val keywordIndex = p.process(frame)
            if (keywordIndex >= 0) {
                val now = SystemClock.elapsedRealtime()
                return WakeWordDetection(
                    keyword = keyword.name,
                    timestamp = now,
                    latencyMs = now - startTime,
                    routeStatus = "verified"
                )
            }
        } catch (e: PorcupineException) {
            Log.e("HermesWake", "Error processing frame", e)
        }
        return null
    }

    override fun stop() {}

    override fun release() {
        porcupine?.delete()
        porcupine = null
    }

    override fun setSensitivity(sensitivity: Float) {
        currentSensitivity = sensitivity.coerceIn(0f, 1f)
        if (porcupine != null) {
            // Need to recreate if sensitivity changes during run
            release()
            start()
        }
    }
}
