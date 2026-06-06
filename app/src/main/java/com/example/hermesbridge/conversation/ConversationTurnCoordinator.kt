package com.example.hermesbridge.conversation

import android.util.Log
import com.example.hermesbridge.BridgeController
import com.example.hermesbridge.audio.BluetoothAudioRouteManager
import com.example.hermesbridge.audio.BluetoothAudioRouteStatus
import com.example.hermesbridge.bridge.MetaWearableInputSource
import com.example.hermesbridge.meta.MetaDatManager
import com.example.hermesbridge.meta.MetaDatStatus
import com.example.hermesbridge.metrics.InteractionMetricsCollector
import com.example.hermesbridge.speech.AndroidSpeechRecognizerInput
import com.example.hermesbridge.speech.SpeechRecognitionStatus
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConversationTurnCoordinator(
    private val scope: CoroutineScope,
    private val controller: BridgeController,
    val metaDatManager: MetaDatManager,
    private val audioRouteManager: BluetoothAudioRouteManager,
    private val speechToText: AndroidSpeechRecognizerInput,
    private val wearableInputSource: MetaWearableInputSource,
    private val metricsCollector: InteractionMetricsCollector? = null,
    private val mediaManager: com.example.hermesbridge.media.MediaCoexistenceManager? = null
) {
    private val _turnState = MutableStateFlow<ConversationTurnState>(ConversationTurnState.Idle)
    val turnState: StateFlow<ConversationTurnState> = _turnState.asStateFlow()

    private var monitoringJob: Job? = null

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = scope.launch {
            // Monitor speech results
            launch {
                speechToText.result.collect { result ->
                    if (result.isFinal && result.finalTranscript.isNotBlank() && _turnState.value is ConversationTurnState.Listening) {
                        handleFinalTranscript(result.finalTranscript, result.confidence)
                    }
                }
            }

            // Monitor speech status for errors/timeouts
            launch {
                speechToText.status.collect { status ->
                    if (_turnState.value is ConversationTurnState.Listening) {
                        when (status) {
                            is SpeechRecognitionStatus.Error -> endTurnWithError(status.message)
                            is SpeechRecognitionStatus.NoSpeech -> endTurnWithCanceled("No speech detected")
                            is SpeechRecognitionStatus.Canceled -> endTurnWithCanceled("Recognition canceled")
                            else -> {}
                        }
                    }
                }
            }

            // Monitor backend loading state via controller
            launch {
                controller.uiState.collect { state ->
                    val current = _turnState.value
                    if (current is ConversationTurnState.Sending && !state.isLoading) {
                        if (state.errorMessage != null) {
                            endTurnWithError(state.errorMessage)
                        } else if (state.latestResponse.isNotBlank()) {
                            startSpeaking(state.latestResponse)
                        } else {
                            resetToIdle()
                        }
                    }
                }
            }
        }
    }

    fun startWearableTurn() {
        if (_turnState.value != ConversationTurnState.Idle && _turnState.value !is ConversationTurnState.Error && _turnState.value != ConversationTurnState.Completed) {
            Log.w("HermesTurn", "Turn already in progress: ${_turnState.value}")
            return
        }

        if (metaDatManager.status.value !is MetaDatStatus.SessionReady) {
            endTurnWithError("Meta session not ready")
            return
        }

        if (audioRouteManager.status.value !is BluetoothAudioRouteStatus.Routed) {
            _turnState.value = ConversationTurnState.PreparingAudioRoute
            audioRouteManager.startBluetoothRoute()
        }

        if (mediaManager?.requestPauseBeforeTurn() == false) {
            Log.w("HermesTurn", "Media pause requested but failed or manual pause required.")
            // Proceed anyway if it was just a policy warning, but for prototype we can show error
            // endTurnWithError("Pause media before speaking")
            // return
        }

        controller.stopSpeaking()
        speechToText.stopListening()

        metricsCollector?.recordLatencyEvent { it.speechRecognizerStart = SystemClock.elapsedRealtime() }
        _turnState.value = ConversationTurnState.Listening
        speechToText.startListening()
    }

    private fun handleFinalTranscript(text: String, confidence: Float) {
        Log.i("HermesTurn", "Final transcript received: $text")
        metricsCollector?.recordLatencyEvent { it.finalTranscript = SystemClock.elapsedRealtime() }
        _turnState.value = ConversationTurnState.ProcessingTranscript
        speechToText.stopListening()
        
        metricsCollector?.recordLatencyEvent { it.backendRequestStart = SystemClock.elapsedRealtime() }
        _turnState.value = ConversationTurnState.Sending
        wearableInputSource.submitTranscript(text, confidence)
    }

    private fun startSpeaking(text: String) {
        metricsCollector?.recordLatencyEvent { it.backendResponseReceived = SystemClock.elapsedRealtime() }
        _turnState.value = ConversationTurnState.Speaking
        metricsCollector?.recordLatencyEvent { it.ttsStart = SystemClock.elapsedRealtime() }
        controller.speakResponse(text) {
            metricsCollector?.recordLatencyEvent { it.ttsComplete = SystemClock.elapsedRealtime() }
            _turnState.value = ConversationTurnState.Completed
            metricsCollector?.takeBatterySnapshot("TurnCompleted")
            
            mediaManager?.handleTurnCompletion()

            scope.launch {
                kotlinx.coroutines.delay(1000)
                resetToIdle()
            }
        }
    }

    fun cancelTurn() {
        Log.i("HermesTurn", "Turn canceled by user")
        speechToText.stopListening()
        controller.stopSpeaking()
        _turnState.value = ConversationTurnState.Canceled
        scope.launch {
            kotlinx.coroutines.delay(1000)
            resetToIdle()
        }
    }

    private fun endTurnWithError(msg: String) {
        Log.e("HermesTurn", "Turn error: $msg")
        _turnState.value = ConversationTurnState.Error(msg)
    }

    private fun endTurnWithCanceled(msg: String) {
        Log.w("HermesTurn", "Turn canceled: $msg")
        _turnState.value = ConversationTurnState.Canceled
        scope.launch {
            kotlinx.coroutines.delay(1000)
            resetToIdle()
        }
    }

    private fun resetToIdle() {
        _turnState.value = ConversationTurnState.Idle
    }
}
