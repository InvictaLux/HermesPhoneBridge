package com.example.hermesbridge.bridge

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Stub implementation of a wearable input adapter.
 * Future Gate: Replace this with real Ray-Ban Meta / wearable SDK implementation.
 */
class MetaWearableInputSource : InputSource {
    private var listener: ((BridgeEvent) -> Unit)? = null

    override fun start() {
        // TODO: Initialize Wearable SDK and connect hardware event listeners
    }

    override fun stop() {
        // TODO: Disconnect hardware event listeners and release Wearable SDK resources
        listener = null
    }

    override fun setListener(listener: (BridgeEvent) -> Unit) {
        this.listener = listener
    }

    /**
     * For test injection only. Simulates receiving text from the wearable hardware.
     */
    fun simulateWearableText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        val event = BridgeEvent(
            text = trimmed,
            source = "meta_wearable_stub",
            timestamp = getIsoTimestamp(),
            metadata = mapOf(
                "platform" to "android",
                "input_method" to "wearable_stub",
                "wearable" to "rayban_meta_future",
                "test_mode" to "true"
            )
        )
        listener?.invoke(event)
    }

    private fun getIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
