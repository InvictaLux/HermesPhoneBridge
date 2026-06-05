package com.example.hermesbridge.bridge

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Real implementation of the wearable input adapter for voice transcripts.
 * This adapter bridges the local speech recognizer results into the Hermes pipeline.
 */
class MetaWearableInputSource : InputSource {
    private var listener: ((BridgeEvent) -> Unit)? = null

    override fun start() {
        // Ready for transcript submission
    }

    override fun stop() {
        listener = null
    }

    override fun setListener(listener: (BridgeEvent) -> Unit) {
        this.listener = listener
    }

    /**
     * Submits a verified final transcript from wearable voice input.
     */
    fun submitTranscript(text: String, confidence: Float = 0f) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        val metadata = mutableMapOf(
            "platform" to "android",
            "input_method" to "wearable_voice",
            "wearable" to "rayban_meta",
            "speech_recognizer" to "android_speech_recognizer",
            "audio_route" to "bluetooth_hfp_sco",
            "test_mode" to "true"
        )
        
        if (confidence > 0f) {
            metadata["confidence"] = "%.2f".format(confidence)
        }

        val event = BridgeEvent(
            text = trimmed,
            source = "meta_wearable_voice",
            timestamp = getIsoTimestamp(),
            metadata = metadata
        )
        listener?.invoke(event)
    }

    /**
     * For test injection only.
     */
    fun simulateWearableText(text: String) {
        submitTranscript(text)
    }

    private fun getIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
