package com.example.hermesbridge.audio

data class PcmCaptureResult(
    val sampleRateHz: Int = 8000,
    val channelCount: Int = 1,
    val encoding: String = "PCM_16BIT",
    val durationMs: Long = 0,
    val bytesCaptured: Long = 0,
    val samplesCaptured: Long = 0,
    val rmsAmplitude: Double = 0.0,
    val peakAmplitude: Int = 0,
    val nonZeroSampleCount: Long = 0,
    val likelySignalPresent: Boolean = false,
    val error: String? = null
) {
    fun toDisplayString(): String {
        if (error != null) return "Capture Result: Error - $error"
        if (samplesCaptured == 0L) return "Capture Result: No data"
        return """
            Bytes: $bytesCaptured
            RMS: ${"%.2f".format(rmsAmplitude)}
            Peak: $peakAmplitude
            Non-zero: $nonZeroSampleCount
            Signal detected: ${if (likelySignalPresent) "Yes" else "No"}
        """.trimIndent()
    }
}
