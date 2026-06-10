package com.example.hermesbridge.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class AndroidTtsSpeechOutput(
    context: Context,
    private val onInitResult: (Boolean) -> Unit = {}
) : SpeechOutput, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private var activeOnComplete: (() -> Unit)? = null

    init {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d("SpeechOutput", "Speech started")
            }
            
            override fun onDone(utteranceId: String?) {
                Log.d("SpeechOutput", "Speech finished")
                activeOnComplete?.invoke()
                activeOnComplete = null
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e("SpeechOutput", "Speech error occurred")
                activeOnComplete?.invoke()
                activeOnComplete = null
            }
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("SpeechOutput", "English US voice data missing or not supported")
                isInitialized = false
                onInitResult(false)
            } else {
                Log.d("SpeechOutput", "TTS Initialized successfully")
                isInitialized = true
                onInitResult(true)
            }
        } else {
            Log.e("SpeechOutput", "Initialization failed with status: $status")
            isInitialized = false
            onInitResult(false)
        }
    }

    override fun speak(text: String, onComplete: () -> Unit) {
        if (text.isBlank()) {
            onComplete()
            return
        }
        
        activeOnComplete = onComplete
        
        if (isInitialized && tts != null) {
            val attributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(attributes)

            val params = android.os.Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "hermes_response")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "hermes_response")
        } else {
            Log.w("SpeechOutput", "Attempted speech but TTS engine is unavailable")
            onComplete()
        }
    }

    override fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
    }

    override fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("SpeechOutput", "Error shutting down TTS: ${e.message}")
        } finally {
            tts = null
            isInitialized = false
            activeOnComplete = null
        }
    }
}
