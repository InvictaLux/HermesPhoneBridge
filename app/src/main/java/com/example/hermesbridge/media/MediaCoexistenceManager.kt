package com.example.hermesbridge.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MediaCoexistenceManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    private val _playbackState = MutableStateFlow(MediaPlaybackState())
    val playbackState: StateFlow<MediaPlaybackState> = _playbackState.asStateFlow()

    private var wasPlayingBeforeHermes = false
    private var didHermesPauseMedia = false
    
    private var audioFocusRequest: AudioFocusRequest? = null
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d("HermesMedia", "Audio focus change: $focusChange")
    }

    fun updateStatus() {
        val isMusicActive = audioManager.isMusicActive
        _playbackState.update { 
            it.copy(status = if (isMusicActive) MediaPlaybackStatus.Playing else MediaPlaybackStatus.Paused)
        }
    }

    fun setAutoPauseEnabled(enabled: Boolean) {
        _playbackState.update { it.copy(isAutoPauseEnabled = enabled) }
    }

    fun setResumePolicy(policy: MediaResumePolicy) {
        _playbackState.update { it.copy(resumePolicy = policy) }
    }

    /**
     * Attempts to pause media by requesting audio focus.
     */
    fun requestPauseBeforeTurn(): Boolean {
        wasPlayingBeforeHermes = audioManager.isMusicActive
        didHermesPauseMedia = false
        
        if (!wasPlayingBeforeHermes) {
            Log.d("HermesMedia", "No media playing, nothing to pause.")
            return true
        }

        if (!_playbackState.value.isAutoPauseEnabled) {
            Log.d("HermesMedia", "Auto-pause disabled, requiring manual pause.")
            return false
        }

        Log.i("HermesMedia", "Requesting audio focus to pause media...")
        val result = requestTransientFocus()
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            didHermesPauseMedia = true
            Log.d("HermesMedia", "Audio focus granted, media should be paused.")
            return true
        } else {
            Log.w("HermesMedia", "Audio focus request denied.")
            return false
        }
    }

    /**
     * Abandons audio focus and optionally resumes media based on policy.
     */
    fun handleTurnCompletion() {
        Log.i("HermesMedia", "Turn complete, abandoning audio focus.")
        abandonFocus()

        val policy = _playbackState.value.resumePolicy
        val shouldResume = when (policy) {
            MediaResumePolicy.NeverResume -> false
            MediaResumePolicy.ResumeIfHermesPaused -> wasPlayingBeforeHermes && didHermesPauseMedia
            MediaResumePolicy.AlwaysAsk -> false // Would require UI interaction
        }

        if (shouldResume) {
            Log.i("HermesMedia", "Policy allows resume. wasPlaying=$wasPlayingBeforeHermes, didPause=$didHermesPauseMedia")
            // Audio focus abandonment usually triggers resume in many apps automatically.
            // If not, we could try sending a MEDIA_PLAY key event, but that's risky.
        }
        
        // Reset flags for next turn
        wasPlayingBeforeHermes = false
        didHermesPauseMedia = false
    }

    private fun requestTransientFocus(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }
    
    fun manualPause() {
        requestTransientFocus()
        didHermesPauseMedia = false // It was a manual request via Hermes UI
    }

    fun manualResume() {
        abandonFocus()
    }
}
