package com.example.hermesbridge.onboarding

import android.Manifest
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermesbridge.AgentUiState
import com.example.hermesbridge.AgentViewModel
import com.example.hermesbridge.meta.MetaDatStatus
import com.example.hermesbridge.audio.BluetoothAudioRouteStatus
import com.example.hermesbridge.wakeword.WakeWordStatus

@Composable
fun OnboardingScreen(
    viewModel: AgentViewModel,
    state: AgentUiState
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            OnboardingProgress(currentStep = state.currentOnboardingStep)
            
            Spacer(modifier = Modifier.height(32.dp))

            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = state.currentOnboardingStep,
                    label = "OnboardingContent"
                ) { step ->
                    when (step) {
                        OnboardingStep.Welcome -> WelcomeStep(viewModel)
                        OnboardingStep.MetaRegistration -> MetaRegistrationStep(state, viewModel)
                        OnboardingStep.BluetoothPermissions -> PermissionStep(
                            title = "Bluetooth Connection",
                            description = "Needed to discover and connect to your Ray-Ban Meta glasses.",
                            icon = Icons.Default.Bluetooth,
                            onGrant = { viewModel.onGrantPermissionsClicked() }
                        )
                        OnboardingStep.MicrophonePermission -> PermissionStep(
                            title = "Microphone Access",
                            description = "Used for voice commands and hands-free interaction. Audio is processed locally.",
                            icon = Icons.Default.Mic,
                            onGrant = { viewModel.onGrantPermissionsClicked() }
                        )
                        OnboardingStep.NotificationPermission -> PermissionStep(
                            title = "Notifications",
                            description = "Required to keep the hands-free assistant active while the app is in the background.",
                            icon = Icons.Default.Notifications,
                            onGrant = { viewModel.onGrantPermissionsClicked() }
                        )
                        OnboardingStep.DeviceSession -> DeviceSessionStep(state, viewModel)
                        OnboardingStep.AudioRouteTest -> AudioRouteStep(state, viewModel)
                        OnboardingStep.SpeechTest -> SpeechTestStep(state, viewModel)
                        OnboardingStep.WakeWordTest -> WakeWordTestStep(state, viewModel)
                        OnboardingStep.BackgroundWakeOptIn -> BackgroundWakeOptInStep(state, viewModel)
                        OnboardingStep.FinalSummary -> FinalSummaryStep(state, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingProgress(currentStep: OnboardingStep) {
    val progress = (currentStep.ordinal + 1).toFloat() / OnboardingStep.entries.size
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF2196F3),
            trackColor = Color.DarkGray
        )
        Text(
            text = "Step ${currentStep.ordinal + 1} of ${OnboardingStep.entries.size}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
    }
}

@Composable
fun WelcomeStep(viewModel: AgentViewModel) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color(0xFF2196F3)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Welcome to Field Assistant",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your hands-free AI bridge for field operations. Use it manually or connect your Ray-Ban Meta glasses for the full experience.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = Color.LightGray
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = { viewModel.onNextOnboardingStep() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Get Started")
        }
        TextButton(onClick = { viewModel.onCompleteOnboarding() }) {
            Text("Skip to Manual Mode", color = Color.Gray)
        }
    }
}

@Composable
fun MetaRegistrationStep(state: AgentUiState, viewModel: AgentViewModel) {
    StepLayout(
        title = "Meta Registration",
        description = "This app uses the Meta Device Access Toolkit to talk to your glasses.",
        icon = Icons.Default.Link
    ) {
        ReadinessRow("Meta Status", state.metaDatStatus.getUserMessage())
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (state.metaDatStatus is MetaDatStatus.Ready || state.metaDatStatus is MetaDatStatus.SessionReady) {
            Button(onClick = { viewModel.onNextOnboardingStep() }, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
        } else {
            Button(onClick = { viewModel.onRegisterMetaDatClicked() }, modifier = Modifier.fillMaxWidth()) {
                Text("Launch Meta Registration")
            }
            TextButton(onClick = { viewModel.onRefreshSessionClicked() }) {
                Text("Refresh Status")
            }
        }
    }
}

@Composable
fun PermissionStep(title: String, description: String, icon: ImageVector, onGrant: () -> Unit) {
    StepLayout(title, description, icon) {
        Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
            Text("Grant Permission")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = { /* Check status then next */ }, modifier = Modifier.fillMaxWidth()) {
            // Simplified for prototype: user must grant or we block/skip manually
            Text("Continue")
        }
    }
}

@Composable
fun DeviceSessionStep(state: AgentUiState, viewModel: AgentViewModel) {
    StepLayout(
        title = "Connect Glasses",
        description = "Power on your glasses and ensure they are nearby.",
        icon = Icons.Default.BluetoothConnected
    ) {
        ReadinessRow("Session", state.metaDatStatus.getUserMessage())
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (state.metaDatStatus is MetaDatStatus.SessionReady) {
            Button(onClick = { viewModel.onNextOnboardingStep() }, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
        } else {
            Button(onClick = { viewModel.onCreateSessionClicked() }, modifier = Modifier.fillMaxWidth()) {
                Text("Start Device Session")
            }
        }
    }
}

@Composable
fun AudioRouteStep(state: AgentUiState, viewModel: AgentViewModel) {
    StepLayout(
        title = "Audio Connection",
        description = "We'll test if we can route audio from the glasses.",
        icon = Icons.Default.BluetoothAudio
    ) {
        ReadinessRow("Route", state.audioRouteStatus.getUserMessage())
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (state.audioRouteStatus is BluetoothAudioRouteStatus.Routed) {
            Button(onClick = { viewModel.onNextOnboardingStep() }, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
        } else {
            Button(onClick = { viewModel.onStartAudioRouteClicked() }, modifier = Modifier.weight(1f)) {
                Text("Test Route")
            }
        }
    }
}

@Composable
fun SpeechTestStep(state: AgentUiState, viewModel: AgentViewModel) {
    StepLayout(
        title = "Voice Test",
        description = "Say something through the glasses to test recognition.",
        icon = Icons.Default.SettingsVoice
    ) {
        Text("Transcript: ${state.speechResult.finalTranscript}", style = MaterialTheme.typography.bodyMedium)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.onStartWearableSpeechTestClicked() }, modifier = Modifier.weight(1f)) {
                Text("Record Test")
            }
            if (state.speechResult.finalTranscript.isNotBlank()) {
                Button(onClick = { viewModel.onNextOnboardingStep() }, modifier = Modifier.weight(1f)) {
                    Text("It Works")
                }
            }
        }
    }
}

@Composable
fun WakeWordTestStep(state: AgentUiState, viewModel: AgentViewModel) {
    StepLayout(
        title = "Wake Word Test",
        description = "Say \"Hey Assistant\" (or \"Porcupine\" for this test) to verify wake detection.",
        icon = Icons.Default.Hearing
    ) {
        ReadinessRow("Status", state.wakeWordStatus.getUserMessage())
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (state.wakeWordStatus is WakeWordStatus.Detected) {
            Button(onClick = { viewModel.onNextOnboardingStep() }, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
        } else {
            Button(onClick = { viewModel.onStartWakeWordTestClicked() }, modifier = Modifier.fillMaxWidth()) {
                Text("Start Detection Test")
            }
        }
    }
}

@Composable
fun BackgroundWakeOptInStep(state: AgentUiState, viewModel: AgentViewModel) {
    StepLayout(
        title = "Always Listening",
        description = "Enable background wake mode to talk to the assistant even when your phone is in your pocket.",
        icon = Icons.Default.Bolt
    ) {
        Text(
            text = "• Uses extra battery\n• Persistent notification shown\n• Screen-off support",
            style = MaterialTheme.typography.bodySmall,
            color = Color.LightGray
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = { viewModel.onToggleWakeModeClicked(); viewModel.onNextOnboardingStep() }, modifier = Modifier.fillMaxWidth()) {
            Text("Enable Background Mode")
        }
        TextButton(onClick = { viewModel.onNextOnboardingStep() }) {
            Text("Not Now", color = Color.Gray)
        }
    }
}

@Composable
fun FinalSummaryStep(state: AgentUiState, viewModel: AgentViewModel) {
    StepLayout(
        title = "Ready for Work",
        description = "Setup is complete. You can always re-run this from Settings.",
        icon = Icons.Default.CheckCircle
    ) {
        ReadinessSummary(state)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = { viewModel.onCompleteOnboarding() }, modifier = Modifier.fillMaxWidth()) {
            Text("Finish")
        }
    }
}

@Composable
fun StepLayout(title: String, description: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color(0xFF2196F3))
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = description, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        content()
    }
}

@Composable
fun ReadinessRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ReadinessSummary(state: AgentUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryItem("Manual Chat", true)
        SummaryItem("Meta Glasses", state.metaDatStatus is MetaDatStatus.SessionReady)
        SummaryItem("Wake Detection", state.trueDetectionCount > 0) // Basic heuristic
        SummaryItem("Background Mode", state.isWakeModeEnabled)
    }
}

@Composable
fun SummaryItem(label: String, isReady: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isReady) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (isReady) Color.Green else Color.Red,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
