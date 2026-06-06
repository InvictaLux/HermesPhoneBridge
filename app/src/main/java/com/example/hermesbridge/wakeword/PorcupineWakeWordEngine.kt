package com.example.hermesbridge.wakeword

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException

class PorcupineWakeWordEngine(
    private val context: Context,
    private val accessKey: String,
    private val keyword: Porcupine.BuiltInKeyword = Porcupine.BuiltInKeyword.PORCUPINE
) : WakeWordEngine {

    private var porcupine: Porcupine? = null
    override val sampleRate: Int = 16000 // Porcupine requirement
    override val frameLength: Int
        get() = porcupine?.frameLength ?: 512

    private var startTime: Long = 0

    override fun start() {
        try {
            if (porcupine == null) {
                porcupine = Porcupine.Builder()
                    .setAccessKey(accessKey)
                    .setKeyword(keyword)
                    .build(context)
            }
            startTime = System.currentTimeMillis()
            Log.i("HermesWake", "Porcupine engine started. Sample Rate: $sampleRate, Frame Length: $frameLength")
        } catch (e: PorcupineException) {
            Log.e("HermesWake", "Failed to initialize Porcupine", e)
            throw e
        }
    }

    override fun processFrame(frame: ShortArray): WakeWordDetection? {
        val p = porcupine ?: return null
        try {
            val keywordIndex = p.process(frame)
            if (keywordIndex >= 0) {
                val now = System.currentTimeMillis()
                return WakeWordDetection(
                    keyword = keyword.name,
                    timestamp = now,
                    latencyMs = now - startTime,
                    routeStatus = "verified" // Managed by caller
                )
            }
        } catch (e: PorcupineException) {
            Log.e("HermesWake", "Error processing frame", e)
        }
        return null
    }

    override fun stop() {
        // No-op for the low-level engine, handled in release
    }

    override fun release() {
        porcupine?.delete()
        porcupine = null
    }
}
