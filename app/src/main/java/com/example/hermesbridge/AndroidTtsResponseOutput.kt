package com.example.hermesbridge

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class AndroidTtsResponseOutput(
    context: Context,
    private val onInitResult: (Boolean) -> Unit = {}
) : ResponseOutput, TextToSpeech.OnInitListener {

    override val name: String = "Android Text-To-Speech Engine"
    
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private var activeOnComplete: (() -> Unit)? = null

    init {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d("TTS", "Speech started")
            }
            
            override fun onDone(utteranceId: String?) {
                Log.d("TTS", "Speech finished")
                activeOnComplete?.invoke()
                activeOnComplete = null
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e("TTS", "Speech error occurred")
                activeOnComplete?.invoke()
                activeOnComplete = null
            }
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "English US voice data missing or not supported")
                isInitialized = false
                onInitResult(false)
            } else {
                Log.d("TTS", "TTS Initialized successfully")
                isInitialized = true
                onInitResult(true)
            }
        } else {
            Log.e("TTS", "Initialization failed with status: $status")
            isInitialized = false
            onInitResult(false)
        }
    }

    override fun outputResponse(text: String, onComplete: () -> Unit) {
        if (text.isBlank()) {
            onComplete()
            return
        }
        activeOnComplete = onComplete
        if (isInitialized && tts != null) {
            val params = android.os.Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "hermes_response")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "hermes_response")
        } else {
            Log.w("TTS", "Attempted speech but TTS engine is unavailable")
            onComplete()
        }
    }

    override fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
    }

    override fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("TTS", "Error releasing TTS output: ${e.message}")
        } finally {
            tts = null
            isInitialized = false
        }
    }
}
