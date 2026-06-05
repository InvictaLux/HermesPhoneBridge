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

            Button(
                onClick = { viewModel.onStartWearableSpeechTestClicked() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.audioRouteStatus is BluetoothAudioRouteStatus.Routed && 
                          state.speechStatus !is SpeechRecognitionStatus.Listening
            ) {
                Text("Start Wearable Speech Test")
            }

            Text(
                text = state.speechStatus.getUserMessage(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )

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
