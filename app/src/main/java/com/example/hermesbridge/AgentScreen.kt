package com.example.hermesbridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.hermesbridge.meta.MetaDatStatus
import com.example.hermesbridge.audio.BluetoothAudioRouteStatus
import com.example.hermesbridge.audio.PcmCaptureStatus
import com.example.hermesbridge.speech.SpeechRecognitionStatus
import com.example.hermesbridge.conversation.ConversationTurnState
import com.example.hermesbridge.conversation.ConversationTurnStatus
import com.example.hermesbridge.wakeword.WakeWordStatus
import androidx.compose.ui.unit.dp

@Composable
fun AgentScreen(
    viewModel: AgentViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Hermes Phone Bridge",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Phone test bridge to Hermes backend",
            style = MaterialTheme.typography.bodyMedium
        )

        Button(
            onClick = { viewModel.onNewSessionClicked() },
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
        ) {
            Text("New Session")
        }

        Column {
            Text(
                text = state.metaDatStatus.getUserMessage(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            state.metaDatMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            state.permissionMessage?.let { pMsg ->
                Text(
                    text = pMsg,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pMsg.contains("Ready")) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
            }
        }

        if (state.metaDatStatus is MetaDatStatus.RegistrationRequired || 
            state.metaDatStatus is MetaDatStatus.MissingMetaApp ||
            state.metaDatStatus is MetaDatStatus.RegistrationFailed) {
            Button(
                onClick = { viewModel.onRegisterMetaDatClicked() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.metaDatStatus !is MetaDatStatus.Initializing
            ) {
                Text(if (state.metaDatStatus is MetaDatStatus.Initializing) "Launching..." else "Register Meta DAT")
            }
        }

        if (state.permissionMessage?.contains("Ready") == false) {
            Button(
                onClick = { viewModel.onGrantPermissionsClicked() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Meta Permissions")
            }
        }

        if (state.metaDatStatus is MetaDatStatus.Ready || 
            state.metaDatStatus is MetaDatStatus.NoDeviceFound ||
            state.metaDatStatus is MetaDatStatus.SessionError ||
            state.metaDatStatus is MetaDatStatus.SessionStopped) {
            Button(
                onClick = { viewModel.onCreateSessionClicked() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create Meta Session")
            }
        }

        if (state.metaDatStatus is MetaDatStatus.SessionReady ||
            state.metaDatStatus is MetaDatStatus.SessionStarting ||
            state.metaDatStatus is MetaDatStatus.SessionPaused ||
            state.metaDatStatus is MetaDatStatus.SessionDisconnected) {
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.onReconnectSessionClicked() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reconnect")
                }
                Button(
                    onClick = { viewModel.onRefreshSessionClicked() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Refresh")
                }
            }

            Button(
                onClick = { viewModel.onCloseSessionClicked() },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Close Meta Session")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.onDiscoverCapabilitiesClicked() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Discover Meta Capabilities")
            }

            Text(
                text = "Capabilities:\n${state.metaCapabilities.toDisplayString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )

            Button(
                onClick = { viewModel.onInspectAudioApiClicked() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Inspect Meta Audio API")
            }

            Text(
                text = "Audio Info:\n${state.metaAudioInfo.toDisplayString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.onStartAudioRouteClicked() },
                    modifier = Modifier.weight(1f),
                    enabled = state.audioRouteStatus !is BluetoothAudioRouteStatus.Routed
                ) {
                    Text("Start Audio Route")
                }
                Button(
                    onClick = { viewModel.onStopAudioRouteClicked() },
                    modifier = Modifier.weight(1f),
                    enabled = state.audioRouteStatus is BluetoothAudioRouteStatus.Routed
                ) {
                    Text("Stop Audio Route")
                }
            }

            Text(
                text = state.audioRouteStatus.getUserMessage(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.onCapturePcmSampleClicked() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.audioRouteStatus is BluetoothAudioRouteStatus.Routed && 
                          state.pcmCaptureStatus !is PcmCaptureStatus.Recording
            ) {
                Text("Capture 3-Second Mic Sample")
            }

            Text(
                text = state.pcmCaptureStatus.getUserMessage(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )

            if (state.pcmCaptureResult.samplesCaptured > 0 || state.pcmCaptureResult.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "PCM Capture Metrics",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = state.pcmCaptureResult.toDisplayString(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Tuning Controls", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.onMarkDeliberateWakeClicked() }, modifier = Modifier.weight(1f)) {
                            Text("Try Wake")
                        }
                        Button(onClick = { viewModel.onMarkMissedWakeClicked() }, modifier = Modifier.weight(1f)) {
                            Text("Missed")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = state.turnState.getUserMessage(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = state.triggerStatus.getUserMessage(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.onStartWearableSpeechTestClicked() },
                    modifier = Modifier.weight(1f),
                    enabled = state.metaDatStatus is MetaDatStatus.SessionReady && 
                              state.turnState is ConversationTurnState.Idle
                ) {
                    Text("Ask Hermes Through Glasses")
                }
                
                if (state.turnState !is ConversationTurnState.Idle) {
                    Button(
                        onClick = { viewModel.onStopWearableSpeechTestClicked() },
                        modifier = Modifier.weight(0.5f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Cancel")
                    }
                }
            }

            if (state.speechResult.partialTranscript.isNotBlank() || state.speechResult.finalTranscript.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Wearable Transcript",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        if (state.speechResult.partialTranscript.isNotBlank()) {
                            Text(
                                text = "Partial: ${state.speechResult.partialTranscript}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        if (state.speechResult.finalTranscript.isNotBlank()) {
                            Text(
                                text = "Final: ${state.speechResult.finalTranscript}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Text(
                                text = "Confidence: ${"%.2f".format(state.speechResult.confidence)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.onToggleWakeModeClicked() },
                    modifier = Modifier.weight(1f),
                    colors = if (state.isWakeModeEnabled) 
                        androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        else androidx.compose.material3.ButtonDefaults.buttonColors()
                ) {
                    Text(if (state.isWakeModeEnabled) "Disable Background Wake" else "Enable Background Wake")
                }
            }

            Text(
                text = "Background Wake: ${state.wakeServiceState.getUserMessage()}",
                style = MaterialTheme.typography.labelSmall,
                color = if (state.isWakeModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            
            if (state.isWakeModeEnabled) {
                Text(
                    text = "Policy: Level ${state.batteryLevel}% / Temp ${state.batterySnapshot?.temperature ?: "N/A"}°C",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Recovery: Session ${state.sessionRecoveryAttempts} / Route ${state.routeRecoveryAttempts}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Wake Word Offline Smoke Test",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { 
                        if (state.wakeWordStatus is WakeWordStatus.Listening) {
                            viewModel.onStopWakeWordTestClicked()
                        } else {
                            viewModel.onStartWakeWordTestClicked()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = state.audioRouteStatus is BluetoothAudioRouteStatus.Routed && 
                              state.turnState is ConversationTurnState.Idle
                ) {
                    Text(if (state.wakeWordStatus is WakeWordStatus.Listening) "Stop Wake Test" else "Start Wake Test")
                }
            }

            Text(
                text = state.wakeWordStatus.getUserMessage(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )

            state.lastWakeDetection?.let { detection ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "DETECTED: ${detection.keyword}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = "Latency: ${detection.latencyMs}ms",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.onCorrectDetectionClicked() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Correct")
                            }
                            Button(
                                onClick = { viewModel.onFalseTriggerClicked() },
                                modifier = Modifier.weight(1f),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("False")
                            }
                        }
                    }
                }
            }

            if (state.trueDetectionCount > 0 || state.falseTriggerCount > 0) {
                Text(
                    text = "Results: ${state.trueDetectionCount} True / ${state.falseTriggerCount} False",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Debug Metrics",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = """
                            Wake Detections: ${state.reliabilityStats.detections}
                            Attempts: ${state.reliabilityStats.deliberateWakeAttempts}
                            True/False/Missed: ${state.reliabilityStats.trueDetections} / ${state.reliabilityStats.falseDetections} / ${state.reliabilityStats.missedDetections}
                            Recall: ${"%.2f".format(state.reliabilityStats.getRecall())}
                            Precision: ${"%.2f".format(state.reliabilityStats.getPrecision())}
                            False Detections/Hr: ${"%.2f".format(state.reliabilityStats.getFalseDetectionsPerHour())}
                            p50 / p95 Latency: ${state.lastLatency.getP50Latency()}ms / ${state.lastLatency.getP95Latency()}ms
                            Last STT Latency: ${state.lastLatency.getSpeechStartToFinal()}ms
                            Last Backend RTT: ${state.lastLatency.getBackendLatency()}ms
                            Total Wake-to-Resp: ${state.lastLatency.getTotalWakeToResponse()}ms
                            BT Route Uptime: ${state.btUptimeMs / 1000}s
                            Battery: ${state.batteryLevel}% (${"%.1f".format(state.batterySnapshot?.temperature ?: 0f)}°C)
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.onMarkMissedWakeClicked() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Mark Missed")
                        }
                        Button(
                            onClick = { viewModel.onResetMetricsClicked() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset")
                        }
                    }
                    Button(
                        onClick = { viewModel.onExportMetricsClicked() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export Metrics Summary")
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.inputText,
            onValueChange = { viewModel.onInputTextChanged(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Message to backend") },
            placeholder = { Text("Ask Hermes what to check next...") },
            minLines = 3
        )

        Button(
            onClick = { viewModel.submitScreenInput() },
            enabled = !state.isLoading && state.inputText.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isLoading) "Sending..." else "Send to Hermes")
        }

        if (state.isLoading) {
            CircularProgressIndicator()
        }

        state.errorMessage?.let { error ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Error",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error)
                }
            }
        }

        // Display recent events for debugging/visibility
        state.events.forEach { event ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = event.message,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Response",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.latestResponse.ifBlank {
                        "No response yet."
                    }
                )
            }
        }

        if (state.conversationHistory.isNotEmpty()) {
            Text(
                text = "Conversation History",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            state.conversationHistory.forEach { turn ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (turn.status) {
                            ConversationTurnStatus.Completed -> MaterialTheme.colorScheme.surface
                            ConversationTurnStatus.Failed -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = turn.source.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = turn.status.name,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text(
                            text = turn.inputText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        turn.responseText?.let {
                            Text(
                                text = "Hermes: $it",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        turn.errorMessage?.let {
                            Text(
                                text = "Error: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Button(
                                onClick = { viewModel.retryTurn(turn) },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AgentScreenPreview() {
    MaterialTheme {
        // We cannot easily preview without mocking the ViewModel or providing a wrapper.
        // For Gate 0 compilation, this is kept minimal.
    }
}
