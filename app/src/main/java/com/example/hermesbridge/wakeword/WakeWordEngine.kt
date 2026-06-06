package com.example.hermesbridge.wakeword

interface WakeWordEngine {
    val sampleRate: Int
    val frameLength: Int

    fun start()
    fun processFrame(frame: ShortArray): WakeWordDetection?
    fun stop()
    fun release()
}
