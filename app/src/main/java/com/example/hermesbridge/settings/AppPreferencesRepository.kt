package com.example.hermesbridge.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.hermesbridge.media.MediaResumePolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AppPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("hermes_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<PersistedAppSettings> = _settings.asStateFlow()

    private fun loadSettings(): PersistedAppSettings {
        return PersistedAppSettings(
            isAutoSpeakEnabled = prefs.getBoolean("isAutoSpeakEnabled", true),
            mediaAutoPauseEnabled = prefs.getBoolean("mediaAutoPauseEnabled", true),
            mediaResumePolicy = MediaResumePolicy.valueOf(prefs.getString("mediaResumePolicy", MediaResumePolicy.ResumeIfHermesPaused.name) ?: MediaResumePolicy.ResumeIfHermesPaused.name),
            wakeSensitivity = prefs.getFloat("wakeSensitivity", 0.5f),
            wakeDebounceMs = prefs.getLong("wakeDebounceMs", 1000L),
            screenOffRuntimeLimitMinutes = prefs.getInt("screenOffRuntimeLimitMinutes", 60),
            diagnosticsExpanded = prefs.getBoolean("diagnosticsExpanded", false),
            unsentChatDraft = prefs.getString("unsentChatDraft", "") ?: "",
            longRunTestDurationMinutes = prefs.getInt("longRunTestDurationMinutes", 30),
            isOnboardingCompleted = prefs.getBoolean("isOnboardingCompleted", false)
        )
    }

    fun updateOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean("isOnboardingCompleted", completed).apply()
        _settings.update { it.copy(isOnboardingCompleted = completed) }
    }

    fun updateAutoSpeak(enabled: Boolean) {
        prefs.edit().putBoolean("isAutoSpeakEnabled", enabled).apply()
        _settings.update { it.copy(isAutoSpeakEnabled = enabled) }
    }

    fun updateMediaAutoPause(enabled: Boolean) {
        prefs.edit().putBoolean("mediaAutoPauseEnabled", enabled).apply()
        _settings.update { it.copy(mediaAutoPauseEnabled = enabled) }
    }

    fun updateMediaResumePolicy(policy: MediaResumePolicy) {
        prefs.edit().putString("mediaResumePolicy", policy.name).apply()
        _settings.update { it.copy(mediaResumePolicy = policy) }
    }

    fun updateWakeSensitivity(sensitivity: Float) {
        val clamped = sensitivity.coerceIn(0f, 1f)
        prefs.edit().putFloat("wakeSensitivity", clamped).apply()
        _settings.update { it.copy(wakeSensitivity = clamped) }
    }

    fun updateWakeDebounce(ms: Long) {
        prefs.edit().putLong("wakeDebounceMs", ms).apply()
        _settings.update { it.copy(wakeDebounceMs = ms) }
    }

    fun updateScreenOffLimit(mins: Int) {
        prefs.edit().putInt("screenOffRuntimeLimitMinutes", mins).apply()
        _settings.update { it.copy(screenOffRuntimeLimitMinutes = mins) }
    }

    fun updateDiagnosticsExpanded(expanded: Boolean) {
        prefs.edit().putBoolean("diagnosticsExpanded", expanded).apply()
        _settings.update { it.copy(diagnosticsExpanded = expanded) }
    }

    fun updateChatDraft(draft: String) {
        prefs.edit().putString("unsentChatDraft", draft).apply()
        _settings.update { it.copy(unsentChatDraft = draft) }
    }

    fun updateLongRunDuration(mins: Int) {
        prefs.edit().putInt("longRunTestDurationMinutes", mins).apply()
        _settings.update { it.copy(longRunTestDurationMinutes = mins) }
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
        _settings.value = PersistedAppSettings()
    }
}
