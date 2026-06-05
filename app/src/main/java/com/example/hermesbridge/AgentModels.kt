package com.example.hermesbridge

import com.google.gson.annotations.SerializedName

data class AgentMetadata(
    val platform: String = "android_phone_test",
    val source: String = "phone_text",
    val wearable: String = "none",
    @SerializedName("audio_transcribed_on_edge") val audioTranscribedOnEdge: Boolean = true,
    @SerializedName("speech_output_on_edge") val speechOutputOnEdge: Boolean = false,
    @SerializedName("test_mode") val testMode: Boolean = true,
    @SerializedName("turn_id") val turnId: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class AgentRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("input_type") val inputType: String = "text",
    val text: String,
    val timestamp: String,
    val metadata: AgentMetadata = AgentMetadata()
)

data class AgentResponse(
    @SerializedName("response")
    val response: String? = null,

    @SerializedName("response_text")
    val responseText: String? = null,

    @SerializedName("session_id")
    val sessionId: String? = null,

    @SerializedName("error")
    val error: String? = null
) {
    val finalResponseText: String
        get() = response ?: responseText ?: error ?: "No response text returned."
}
