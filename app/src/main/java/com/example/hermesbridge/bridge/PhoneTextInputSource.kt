package com.example.hermesbridge.bridge

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class PhoneTextInputSource : InputSource {
    private var listener: ((BridgeEvent) -> Unit)? = null

    override fun start() {
        // No special startup logic for phone text
    }

    override fun stop() {
        listener = null
    }

    override fun setListener(listener: (BridgeEvent) -> Unit) {
        this.listener = listener
    }

    fun submitText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        val event = BridgeEvent(
            text = trimmed,
            source = "phone_text",
            timestamp = getIsoTimestamp(),
            metadata = mapOf(
                "platform" to "android_phone_test",
                "input_method" to "manual_text",
                "wearable" to "none",
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
