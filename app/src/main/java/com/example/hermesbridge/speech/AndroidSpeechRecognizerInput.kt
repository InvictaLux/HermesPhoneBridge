package com.example.hermesbridge.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class AndroidSpeechRecognizerInput(
    private val context: Context,
    private val isRouteActive: () -> Boolean
) : SpeechToTextInput, RecognitionListener {

    private val _status = MutableStateFlow<SpeechRecognitionStatus>(SpeechRecognitionStatus.Idle)
    override val status: StateFlow<SpeechRecognitionStatus> = _status.asStateFlow()

    private val _result = MutableStateFlow(SpeechRecognitionResult())
    override val result: StateFlow<SpeechRecognitionResult> = _result.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    override fun startListening() {
        if (isListening) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _status.value = SpeechRecognitionStatus.Error("Speech recognition unavailable on this device")
            return
        }

        if (!isRouteActive()) {
            _status.value = SpeechRecognitionStatus.AudioRouteRequired
            return
        }

        try {
            _result.value = SpeechRecognitionResult()
            _status.value = SpeechRecognitionStatus.Checking

            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(this@AndroidSpeechRecognizerInput)
                }
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            _status.value = SpeechRecognitionStatus.Listening
            Log.d("HermesSpeech", "Speech recognizer started.")

        } catch (e: Exception) {
            Log.e("HermesSpeech", "Failed to start recognition", e)
            _status.value = SpeechRecognitionStatus.Error(e.message ?: "Unknown error")
            isListening = false
        }
    }

    override fun stopListening() {
        if (!isListening) return
        speechRecognizer?.stopListening()
        isListening = false
        _status.value = SpeechRecognitionStatus.Processing
        Log.d("HermesSpeech", "Speech recognizer stop requested.")
    }

    override fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
        _status.value = SpeechRecognitionStatus.Idle
    }

    // RecognitionListener implementation

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d("HermesSpeech", "Ready for speech")
    }

    override fun onBeginningOfSpeech() {
        Log.d("HermesSpeech", "Speech beginning")
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Optional: Could expose audio levels to UI
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        // Not used
    }

    override fun onEndOfSpeech() {
        Log.d("HermesSpeech", "Speech end")
        isListening = false
        _status.value = SpeechRecognitionStatus.Processing
    }

    override fun onError(error: Int) {
        isListening = false
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error ($error)"
        }
        Log.e("HermesSpeech", "Recognizer error: $message")
        
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            _status.value = SpeechRecognitionStatus.NoSpeech
        } else {
            _status.value = SpeechRecognitionStatus.Error(message)
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        
        val topMatch = matches?.firstOrNull() ?: ""
        val confidence = scores?.firstOrNull() ?: 0f
        
        Log.i("HermesSpeech", "Final result: $topMatch (conf: $confidence)")
        
        _result.value = _result.value.copy(
            finalTranscript = topMatch,
            confidence = confidence,
            isFinal = true
        )
        _status.value = SpeechRecognitionStatus.Completed
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        if (text.isNotEmpty()) {
            _result.value = _result.value.copy(partialTranscript = text)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        // Not used
    }
}
