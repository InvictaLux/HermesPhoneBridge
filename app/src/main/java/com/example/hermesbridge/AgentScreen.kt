package com.example.hermesbridge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermesbridge.audio.BluetoothAudioRouteStatus
import com.example.hermesbridge.audio.PcmCaptureStatus
import com.example.hermesbridge.conversation.ConversationTurn
import com.example.hermesbridge.conversation.ConversationTurnSource
import com.example.hermesbridge.conversation.ConversationTurnState
import com.example.hermesbridge.conversation.ConversationTurnStatus
import com.example.hermesbridge.meta.MetaDatStatus
import com.example.hermesbridge.service.WakeServiceState
import com.example.hermesbridge.speech.SpeechRecognitionStatus
import com.example.hermesbridge.wakeword.WakeWordStatus

@Composable
fun AgentScreen(
    viewModel: AgentViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var isDiagnosticsExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            ConversationStatusHeader(
                state = state,
                isDiagnosticsExpanded = isDiagnosticsExpanded,
                onToggleDiagnostics = { isDiagnosticsExpanded = !isDiagnosticsExpanded }
            )
        },
        bottomBar = {
            ChatComposer(
                inputText = state.inputText,
                onInputTextChanged = { viewModel.onInputTextChanged(it) },
                onSendClicked = { viewModel.submitScreenInput() },
                isLoading = state.isLoading,
                enabled = state.turnState is ConversationTurnState.Idle
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (isDiagnosticsExpanded) {
                DiagnosticsPanel(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxHeight(0.5f)
                        .verticalScroll(rememberScrollState())
                )
                HorizontalDivider()
            }

            ChatHistory(
                history = state.conversationHistory,
                onRetry = { viewModel.retryTurn(it) },
                onSpeak = { viewModel.onSpeakTurnResponse(it) },
                onStopSpeaking = { viewModel.stopSpeaking() },
                isSpeaking = state.isTtsSpeaking,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ConversationStatusHeader(
    state: AgentUiState,
    isDiagnosticsExpanded: Boolean,
    onToggleDiagnostics: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hermes Phone Bridge",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Session: ${state.sessionId.takeLast(8)}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                
                IconButton(onClick = onToggleDiagnostics) {
                    Icon(
                        imageVector = if (isDiagnosticsExpanded) Icons.Default.ExpandMore else Icons.Default.BugReport,
                        contentDescription = "Toggle Diagnostics"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusIndicator(
                    label = "Hermes",
                    status = if (state.isLoading) "Sending" else if (state.errorMessage != null) "Error" else "Online",
                    color = if (state.isLoading) Color.Blue else if (state.errorMessage != null) Color.Red else Color.Green
                )
                StatusIndicator(
                    label = "Meta",
                    status = when (state.metaDatStatus) {
                        is MetaDatStatus.SessionReady -> "Ready"
                        is MetaDatStatus.Ready -> "Registered"
                        is MetaDatStatus.NotInitialized -> "Off"
                        else -> "Disconnected"
                    },
                    color = if (state.metaDatStatus is MetaDatStatus.SessionReady) Color.Green else Color.Gray
                )
                StatusIndicator(
                    label = "Wake",
                    status = when {
                        state.wakeServiceState is WakeServiceState.Listening -> "Listening"
                        state.wakeServiceState is WakeServiceState.PausedLowBattery -> "Paused"
                        state.isWakeModeEnabled -> "Starting"
                        else -> "Off"
                    },
                    color = if (state.wakeServiceState is WakeServiceState.Listening) Color.Green else Color.Gray
                )
            }
        }
    }
}

@Composable
fun StatusIndicator(label: String, status: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "$label: $status", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun ChatHistory(
    history: List<ConversationTurn>,
    onRetry: (ConversationTurn) -> Unit,
    onSpeak: (ConversationTurn) -> Unit,
    onStopSpeaking: () -> Unit,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    androidx.compose.runtime.LaunchedEffect(history.size) {
        if (history.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        state = listState,
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(history) { turn ->
            MessageBubble(
                turn = turn,
                onRetry = { onRetry(turn) },
                onSpeak = { onSpeak(turn) },
                onStopSpeaking = onStopSpeaking,
                isSpeaking = isSpeaking
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun MessageBubble(
    turn: ConversationTurn,
    onRetry: () -> Unit,
    onSpeak: () -> Unit,
    onStopSpeaking: () -> Unit,
    isSpeaking: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (turn.source == ConversationTurnSource.PhoneText) "Phone" else "Glasses",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = turn.createdAt.takeLast(9).removeSuffix("Z"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = turn.inputText, style = MaterialTheme.typography.bodyMedium)
                
                if (turn.status == ConversationTurnStatus.Sending) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else if (turn.status == ConversationTurnStatus.Failed) {
                    Text(
                        text = "Error: ${turn.errorMessage ?: "Failed"}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Button(onClick = onRetry, modifier = Modifier.height(32.dp)) {
                        Text("Retry", fontSize = 10.sp)
                    }
                }
            }
        }

        if (turn.responseText != null || turn.status == ConversationTurnStatus.Completed) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Hermes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = turn.responseText ?: "...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { if (isSpeaking) onStopSpeaking() else onSpeak() }) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                contentDescription = "Speak Response",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatComposer(
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    isLoading: Boolean,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask Hermes...") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSendClicked,
                enabled = enabled && !isLoading && inputText.isNotBlank(),
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (enabled && !isLoading && inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (enabled && !isLoading && inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun DiagnosticsPanel(
    state: AgentUiState,
    viewModel: AgentViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("System Diagnostics", style = MaterialTheme.typography.titleSmall)
        
        Button(onClick = { viewModel.onNewSessionClicked() }, modifier = Modifier.fillMaxWidth()) {
            Text("Start New Session")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Switch(
                checked = state.isAutoSpeakEnabled,
                onCheckedChange = { viewModel.onToggleAutoSpeakClicked() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Auto-speak responses", style = MaterialTheme.typography.bodySmall)
        }

        Text("Meta DAT SDK", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.onRegisterMetaDatClicked() }, modifier = Modifier.weight(1f)) {
                Text("Register", fontSize = 10.sp)
            }
            Button(onClick = { viewModel.onCreateSessionClicked() }, modifier = Modifier.weight(1f)) {
                Text("Session", fontSize = 10.sp)
            }
            Button(onClick = { viewModel.onCloseSessionClicked() }, modifier = Modifier.weight(1f)) {
                Text("Close", fontSize = 10.sp)
            }
        }

        Text("Audio & Wake", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.onToggleWakeModeClicked() }, 
                modifier = Modifier.weight(1f)
            ) {
                Text(if (state.isWakeModeEnabled) "Stop Wake" else "Start Wake", fontSize = 10.sp)
            }
            Button(onClick = { viewModel.onStartAudioRouteClicked() }, modifier = Modifier.weight(1f)) {
                Text("Route BT", fontSize = 10.sp)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = """
                        Wake: ${state.reliabilityStats.trueDetections}T / ${state.reliabilityStats.falseDetections}F / ${state.reliabilityStats.missedDetections}M
                        Recall: ${"%.2f".format(state.reliabilityStats.getRecall())}
                        p50 / p95 Latency: ${state.lastLatency.getP50Latency()}ms / ${state.lastLatency.getP95Latency()}ms
                        Battery: ${state.batteryLevel}% (${"%.1f".format(state.batterySnapshot?.temperature ?: 0f)}°C)
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.onMarkDeliberateWakeClicked() }, modifier = Modifier.weight(1f)) {
                        Text("Try Wake", fontSize = 10.sp)
                    }
                    Button(onClick = { viewModel.onMarkMissedWakeClicked() }, modifier = Modifier.weight(1f)) {
                        Text("Missed", fontSize = 10.sp)
                    }
                }
                Button(onClick = { viewModel.onResetMetricsClicked() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Reset Metrics", fontSize = 10.sp)
                }
                Button(onClick = { viewModel.onExportMetricsClicked() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Export Summary", fontSize = 10.sp)
                }
            }
        }

        Button(
            onClick = { viewModel.onStartWearableSpeechTestClicked() },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.metaDatStatus is MetaDatStatus.SessionReady && 
                      state.turnState is ConversationTurnState.Idle
        ) {
            Text("Test Wearable Voice Turn")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun AgentScreenPreview() {
    MaterialTheme {
        // Preview dummy
    }
}
