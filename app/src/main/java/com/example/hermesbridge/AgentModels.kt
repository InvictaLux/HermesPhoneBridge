package com.example.hermesbridge

import com.google.gson.annotations.SerializedName

data class AgentMetadata(
    val platform: String = "android_phone_test",
    val source: String = "phone_text",
    val wearable: String = "none",
    @SerializedName("audio_transcribed_on_edge") val audioTranscribedOnEdge: Boolean = true,
    @SerializedName("speech_output_on_edge") val speechOutputOnEdge: Boolean = false,
    @SerializedName("test_mode") val testMode: Boolean = true
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
    val ok: Boolean,
    @SerializedName("response_text") val responseText: String?,
    @SerializedName("session_id") val sessionId: String?,
    val error: String?
)
