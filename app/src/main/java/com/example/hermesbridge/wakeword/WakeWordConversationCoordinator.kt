package com.example.hermesbridge.wakeword

import android.util.Log
import com.example.hermesbridge.AgentViewModel
import com.example.hermesbridge.conversation.ConversationTurnCoordinator
import com.example.hermesbridge.conversation.ConversationTurnState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest

class WakeWordConversationCoordinator(
    private val scope: CoroutineScope,
    private val viewModel: AgentViewModel,
    private val wakeWordManager: WakeWordTestManager,
    private val turnCoordinator: ConversationTurnCoordinator
) {
    private val _isWakeModeEnabled = MutableStateFlow(false)
    val isWakeModeEnabled: StateFlow<Boolean> = _isWakeModeEnabled.asStateFlow()

    private var monitoringJob: Job? = null
    private var isProcessingWake = false

    fun setWakeModeEnabled(enabled: Boolean) {
        _isWakeModeEnabled.value = enabled
        if (enabled) {
            startWakeDetectionIfReady()
        } else {
            wakeWordManager.stopTest()
        }
    }

    private fun startWakeDetectionIfReady() {
        if (!_isWakeModeEnabled.value) return
        if (turnCoordinator.turnState.value !is ConversationTurnState.Idle &&
            turnCoordinator.turnState.value !is ConversationTurnState.Error &&
            turnCoordinator.turnState.value !is ConversationTurnState.Completed) {
            Log.d("HermesWakeCoord", "Not starting wake detection: turn in progress")
            return
        }

        Log.i("HermesWakeCoord", "Starting wake detection...")
        wakeWordManager.startTest()
    }

    init {
        monitoringJob = scope.launch {
            // Monitor wake detections
            launch {
                wakeWordManager.lastDetection.collect { detection ->
                    if (detection != null && _isWakeModeEnabled.value && !isProcessingWake) {
                        handleWakeDetected()
                    }
                }
            }

            // Monitor turn state to manage wake detection lifecycle
            launch {
                turnCoordinator.turnState.collectLatest { state ->
                    if (state is ConversationTurnState.Idle) {
                        if (_isWakeModeEnabled.value) {
                            Log.i("HermesWakeCoord", "Turn idle, resuming/starting wake detection in 500ms...")
                            isProcessingWake = false
                            delay(500)
                            startWakeDetectionIfReady()
                        }
                    } else {
                        // Turn started (manually or via wake), stop wake detection if it was running
                        if (wakeWordManager.status.value is WakeWordStatus.Listening) {
                            Log.d("HermesWakeCoord", "Turn active, stopping wake detection.")
                            wakeWordManager.stopTest()
                        }
                    }
                }
            }

            // Monitor TTS speaking state (for manual turns)
            launch {
                viewModel.uiState.collectLatest { state ->
                    if (state.isTtsSpeaking) {
                        if (wakeWordManager.status.value is WakeWordStatus.Listening) {
                            Log.d("HermesWakeCoord", "TTS active, pausing wake detection.")
                            wakeWordManager.stopTest()
                        }
                    } else if (!state.isTtsSpeaking && _isWakeModeEnabled.value && 
                               turnCoordinator.turnState.value is ConversationTurnState.Idle &&
                               wakeWordManager.status.value !is WakeWordStatus.Listening &&
                               wakeWordManager.status.value !is WakeWordStatus.Starting) {
                        // TTS finished and wake mode enabled and no wearable turn active
                        delay(500)
                        startWakeDetectionIfReady()
                    }
                }
            }
        }
    }

    private fun handleWakeDetected() {
        if (isProcessingWake) return
        isProcessingWake = true
        
        Log.i("HermesWakeCoord", "Wake phrase detected! Handing off to conversation turn...")
        
        scope.launch {
            // 1. Stop wake detection to release AudioRecord
            wakeWordManager.stopTest()
            
            // Wait for it to actually stop
            var attempts = 0
            while (wakeWordManager.status.value !is WakeWordStatus.Stopped && 
                   wakeWordManager.status.value !is WakeWordStatus.Idle && 
                   attempts < 10) {
                delay(50)
                attempts++
            }

            // 2. Start the wearable turn
            turnCoordinator.startWearableTurn()
        }
    }

    fun stop() {
        monitoringJob?.cancel()
        wakeWordManager.stopTest()
        isProcessingWake = false
    }
}
