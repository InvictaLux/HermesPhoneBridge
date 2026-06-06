package com.example.hermesbridge.media

enum class MediaPlaybackStatus {
    Unknown,
    Playing,
    Paused,
    Inactive
}

data class MediaPlaybackState(
    val status: MediaPlaybackStatus = MediaPlaybackStatus.Unknown,
    val isAutoPauseEnabled: Boolean = true,
    val resumePolicy: MediaResumePolicy = MediaResumePolicy.ResumeIfHermesPaused
)
