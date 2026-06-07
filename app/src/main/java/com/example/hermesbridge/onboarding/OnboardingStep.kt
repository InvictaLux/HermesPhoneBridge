package com.example.hermesbridge.onboarding

enum class OnboardingStep {
    Welcome,
    MetaRegistration,
    BluetoothPermissions,
    MicrophonePermission,
    NotificationPermission,
    DeviceSession,
    AudioRouteTest,
    SpeechTest,
    WakeWordTest,
    BackgroundWakeOptIn,
    FinalSummary
}

enum class StepStatus {
    NotStarted,
    InProgress,
    Completed,
    Skipped,
    Blocked,
    Failed
}
