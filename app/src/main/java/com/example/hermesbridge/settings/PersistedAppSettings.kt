package com.example.hermesbridge.settings

import com.example.hermesbridge.media.MediaResumePolicy

data class PersistedAppSettings(
    val isAutoSpeakEnabled: Boolean = true,
    val mediaAutoPauseEnabled: Boolean = true,
    val mediaResumePolicy: MediaResumePolicy = MediaResumePolicy.ResumeIfHermesPaused,
    val wakeSensitivity: Float = 0.5f,
    val wakeDebounceMs: Long = 1000,
    val screenOffRuntimeLimitMinutes: Int = 60,
    val diagnosticsExpanded: Boolean = false,
    val unsentChatDraft: String = "",
    val longRunTestDurationMinutes: Int = 30
)
