package com.example.hermesbridge

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermesbridge.conversation.*
import com.example.hermesbridge.service.WakeServiceState
import com.example.hermesbridge.meta.MetaDatStatus
import com.example.hermesbridge.audio.BluetoothAudioRouteStatus
import com.example.hermesbridge.audio.PcmCaptureStatus
import com.example.hermesbridge.wakeword.WakeWordStatus
import com.example.hermesbridge.onboarding.OnboardingScreen

// Theme Colors
private val DarkBackground = Color(0xFF000000)
private val AccentColor = Color(0xFF2196F3)
private val SecondaryText = Color(0xFF9E9E9E)
private val CardBackground = Color(0xFF121212)

// Spacing Scale
private val SpacingMicro = 4.dp
private val SpacingCompact = 8.dp
private val SpacingSmall = 12.dp
private val SpacingStandard = 16.dp
private val SpacingSection = 24.dp
private val SpacingMajor = 32.dp

@Composable
fun AgentScreen(
    viewModel: AgentViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AccentColor,
            background = DarkBackground,
            surface = CardBackground,
            onBackground = Color.White,
            onSurface = Color.White,
            secondary = SecondaryText
        )
    ) {
        if (!state.isOnboardingCompleted) {
            OnboardingScreen(viewModel = viewModel, state = state)
        } else {
            AppShell(state, viewModel, modifier)
        }
    }
}

@Composable
fun AppShell(
    state: AgentUiState,
    viewModel: AgentViewModel,
    modifier: Modifier = Modifier
) {
    var showPlusMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
        topBar = {
            SystemStatusHeader(state, onDiagnosticsClick = { 
                viewModel.onToggleDiagnostics(!state.diagnosticsExpanded)
            })
        },
        bottomBar = {
            BottomNavigationBar(
                currentScreen = state.currentScreen,
                onScreenSelected = { viewModel.onNavigateTo(it) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPlusMenu = true },
                containerColor = AccentColor,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.offset(y = 44.dp) // Align with bottom bar center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Actions")
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(DarkBackground)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.diagnosticsExpanded) {
                    DiagnosticsPanel(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxHeight(0.5f)
                            .verticalScroll(rememberScrollState())
                    )
                    HorizontalDivider(color = Color.DarkGray)
                }

                Crossfade(targetState = state.currentScreen, label = "ScreenTransition") { screen ->
                    when (screen) {
                        AppScreen.Home -> HomeScreen(state, viewModel)
                        AppScreen.Customers -> PlaceholderScreen(
                            title = "Customers List",
                            description = "Future CRM integration here.",
                            icon = Icons.Default.People
                        )
                        AppScreen.Route -> PlaceholderScreen(
                            title = "Route Planner",
                            description = "Future route and stop planning here.",
                            icon = Icons.Default.Route
                        )
                        AppScreen.Chat -> ChatScreen(
                            state = state,
                            viewModel = viewModel,
                            onPlusClick = { showPlusMenu = true }
                        )
                        AppScreen.Library -> LibraryScreen()
                    }
                }
            }

            if (state.serviceVisit.arrivalConfirmationRequired) {
                PoolSelectionMenu(
                    candidates = state.serviceVisit.candidatePools,
                    onSelected = { viewModel.onPoolSelected(it) },
                    onDismiss = { /* Hide logic if needed */ }
                )
            }

            if (showPlusMenu) {
                PlusActionMenu(onDismiss = { showPlusMenu = false })
            }
        }
    }
}

@Composable
fun SystemStatusHeader(
    state: AgentUiState,
    onDiagnosticsClick: () -> Unit
) {
    Surface(
        color = DarkBackground,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = SpacingStandard, vertical = SpacingSmall)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(SpacingStandard)) {
                StatusIndicatorSmall("Hermes", state.isLoading, state.errorMessage != null)
                StatusIndicatorSmall("Meta", state.metaDatStatus is MetaDatStatus.SessionReady, false)
                StatusIndicatorSmall("Wake", state.wakeServiceState is WakeServiceState.Listening, false)
            }
            
            IconButton(onClick = onDiagnosticsClick) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Diagnostics",
                    tint = if (state.diagnosticsExpanded) AccentColor else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun StatusIndicatorSmall(label: String, isActive: Boolean, isError: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpacingMicro)) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isError -> Color.Red
                        isActive -> Color.Green
                        else -> Color.DarkGray
                    }
                )
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = SecondaryText)
    }
}

@Composable
fun BottomNavigationBar(
    currentScreen: AppScreen,
    onScreenSelected: (AppScreen) -> Unit
) {
    NavigationBar(
        containerColor = Color.Black,
        tonalElevation = 0.dp,
        modifier = Modifier.navigationBarsPadding()
    ) {
        NavigationItem(AppScreen.Home, Icons.Default.Home, currentScreen, onScreenSelected)
        NavigationItem(AppScreen.Customers, Icons.Default.People, currentScreen, onScreenSelected)
        
        Spacer(Modifier.weight(1f))
        
        NavigationItem(AppScreen.Route, Icons.Default.Route, currentScreen, onScreenSelected)
        NavigationItem(AppScreen.Chat, Icons.AutoMirrored.Filled.Chat, currentScreen, onScreenSelected)
        NavigationItem(AppScreen.Library, Icons.Default.LibraryBooks, currentScreen, onScreenSelected)
    }
}

@Composable
fun RowScope.NavigationItem(
    screen: AppScreen,
    icon: ImageVector,
    currentScreen: AppScreen,
    onScreenSelected: (AppScreen) -> Unit
) {
    NavigationBarItem(
        selected = currentScreen == screen,
        onClick = { onScreenSelected(screen) },
        icon = { Icon(icon, contentDescription = screen.name, modifier = Modifier.size(22.dp)) },
        label = { Text(screen.name, fontSize = 10.sp, fontWeight = if (currentScreen == screen) FontWeight.Bold else FontWeight.Normal) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = AccentColor,
            selectedTextColor = AccentColor,
            unselectedIconColor = SecondaryText,
            unselectedTextColor = SecondaryText,
            indicatorColor = Color.Transparent
        )
    )
}

@Composable
fun HomeScreen(state: AgentUiState, viewModel: AgentViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SpacingStandard)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SpacingSection)
    ) {
        Spacer(modifier = Modifier.height(SpacingCompact))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = state.serviceVisit.userProfile?.displayName ?: "Field Assistant",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Role: ${state.serviceVisit.userProfile?.userRole ?: "cleaner"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryText
                )
            }
            if (state.serviceVisit.syncedPools.isEmpty()) {
                IconButton(onClick = { viewModel.onSyncRouteClicked() }) {
                    Icon(Icons.Default.Sync, contentDescription = "Sync Route", tint = AccentColor)
                }
            }
        }

        if (state.serviceVisit.flowState != com.example.hermesbridge.serviceentry.ServiceFlowState.OffRoute) {
            ServiceVisitCard(state.serviceVisit, viewModel)
        } else {
            LargeActionCard(
                title = "Detect Current Pool",
                subtitle = "Find nearest pool by GPS",
                icon = Icons.Default.LocationOn,
                onClick = { viewModel.onDetectCurrentPoolClicked() }
            )
        }

        if (state.serviceVisit.syncedPools.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(SpacingCompact)) {
                SectionHeader("Assigned Route")
                state.serviceVisit.syncedPools.forEach { pool ->
                    PoolListItem(pool, onSelect = { viewModel.onPoolSelected(pool) })
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(SpacingCompact)) {
            SectionHeader("Quick Actions")
            QuickActionGrid()
        }
        
        Spacer(modifier = Modifier.height(SpacingMajor * 2))
    }
}

@Composable
fun PoolListItem(pool: PoolRecord, onSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(SpacingStandard),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = AccentColor.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(pool.stopOrder.toString(), style = MaterialTheme.typography.labelSmall, color = AccentColor, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(SpacingStandard))
            Column(modifier = Modifier.weight(1f)) {
                Text(pool.customerName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(pool.serviceAddress, style = MaterialTheme.typography.labelSmall, color = SecondaryText, maxLines = 1)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SecondaryText)
        }
    }
}

@Composable
fun LargeActionCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AccentColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(SpacingSection),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpacingStandard)
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.15f)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(SpacingSmall), tint = Color.White)
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = SecondaryText,
        letterSpacing = 1.sp
    )
}

@Composable
fun QuickActionGrid() {
    Row(horizontalArrangement = Arrangement.spacedBy(SpacingSmall)) {
        QuickActionItem("New Note", Icons.Default.NoteAdd, Modifier.weight(1f))
        QuickActionItem("Log Service", Icons.Default.AssignmentTurnedIn, Modifier.weight(1f))
        QuickActionItem("Capture", Icons.Default.CameraAlt, Modifier.weight(1f))
    }
}

@Composable
fun QuickActionItem(label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(96.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = AccentColor, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(SpacingCompact))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}

@Composable
fun RecentActivityItem(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(SpacingSmall),
            horizontalArrangement = Arrangement.spacedBy(SpacingSmall),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.History, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Text(description, style = MaterialTheme.typography.bodySmall, color = SecondaryText, maxLines = 2)
            }
        }
    }
}

@Composable
fun ChatScreen(state: AgentUiState, viewModel: AgentViewModel, onPlusClick: () -> Unit) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.conversationHistory.size) {
        if (state.conversationHistory.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = SpacingSmall),
            state = listState,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(SpacingSmall)
        ) {
            item { Spacer(modifier = Modifier.height(SpacingStandard)) }
            items(state.conversationHistory) { turn ->
                MessageBubble(
                    turn = turn,
                    onRetry = { viewModel.retryTurn(turn) },
                    onSpeak = { viewModel.onSpeakTurnResponse(turn) },
                    onStopSpeaking = { viewModel.stopSpeaking() },
                    isSpeaking = state.isTtsSpeaking
                )
            }
            item { 
                if (state.turnState !is ConversationTurnState.Idle) {
                    TurnStatusOverlay(state.turnState)
                }
            }
            item { Spacer(modifier = Modifier.height(SpacingStandard)) }
        }

        ChatComposer(
            inputText = state.inputText,
            onInputTextChanged = { viewModel.onInputTextChanged(it) },
            onSendClicked = { viewModel.submitScreenInput() },
            onPlusClicked = onPlusClick,
            isLoading = state.isLoading,
            enabled = state.turnState is ConversationTurnState.Idle
        )
    }
}

@Composable
fun TurnStatusOverlay(turnState: ConversationTurnState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(SpacingCompact),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = AccentColor)
        Spacer(Modifier.width(SpacingCompact))
        Text(turnState.getUserMessage(), style = MaterialTheme.typography.labelSmall, color = AccentColor)
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
    Column(modifier = Modifier.fillMaxWidth()) {
        // User Message
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp))
                    .background(if (turn.source == ConversationTurnSource.PhoneText) Color(0xFF1A1A1A) else Color(0xFF1E3A5F))
                    .padding(SpacingSmall)
            ) {
                Text(
                    text = if (turn.source == ConversationTurnSource.PhoneText) "PHONE" else "GLASSES",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
                Spacer(Modifier.height(SpacingMicro))
                Text(turn.inputText, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                
                if (turn.status == ConversationTurnStatus.Failed) {
                    Text("Failed", color = Color.Red, style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                        Text("Retry", fontSize = 10.sp)
                    }
                }
            }
        }

        // Assistant Response
        if (turn.responseText != null) {
            Spacer(modifier = Modifier.height(SpacingCompact))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp))
                        .background(CardBackground)
                        .padding(SpacingSmall)
                ) {
                    Text(
                        text = "ASSISTANT",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                    Spacer(Modifier.height(SpacingMicro))
                    Text(turn.responseText ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { if (isSpeaking) onStopSpeaking() else onSpeak() }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                contentDescription = "Speak",
                                modifier = Modifier.size(16.dp),
                                tint = AccentColor
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
    onPlusClicked: () -> Unit,
    isLoading: Boolean,
    enabled: Boolean
) {
    Surface(
        color = Color.Black,
        modifier = Modifier.imePadding()
    ) {
        Row(
            modifier = Modifier
                .padding(SpacingCompact)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(SpacingCompact)
        ) {
            IconButton(onClick = onPlusClicked, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.AddCircle, contentDescription = "More", tint = SecondaryText)
            }
            
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask Assistant...", color = Color.DarkGray) },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = CardBackground,
                    focusedContainerColor = CardBackground,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = AccentColor
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            
            IconButton(
                onClick = onSendClicked,
                enabled = enabled && !isLoading && inputText.isNotBlank(),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (enabled && !isLoading && inputText.isNotBlank()) AccentColor else Color(0xFF1A1A1A))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun PlaceholderScreen(title: String, description: String, icon: ImageVector) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SpacingMajor),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = CardBackground
        )
        Spacer(modifier = Modifier.height(SpacingSection))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(SpacingCompact))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = SecondaryText, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(SpacingMajor))
        Text("COMING SOON", style = MaterialTheme.typography.labelSmall, color = AccentColor, letterSpacing = 2.sp)
    }
}

@Composable
fun LibraryScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SpacingStandard),
        verticalArrangement = Arrangement.spacedBy(SpacingStandard)
    ) {
        Text("Knowledge Library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
        
        Column(verticalArrangement = Arrangement.spacedBy(SpacingCompact)) {
            LibraryCategory("Pool Chemistry", Icons.Default.Science)
            LibraryCategory("Equipment Manuals", Icons.Default.Settings)
            LibraryCategory("Procedures", Icons.Default.MenuBook)
            LibraryCategory("Learning Path", Icons.Default.School)
        }
    }
}

@Composable
fun LibraryCategory(label: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(SpacingStandard),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpacingStandard)
        ) {
            Icon(icon, contentDescription = null, tint = AccentColor)
            Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SecondaryText)
        }
    }
}

@Composable
fun PlusActionMenu(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 120.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpacingStandard)
        ) {
            PlusMenuItem("Ask Assistant", Icons.AutoMirrored.Filled.Chat, onDismiss)
            PlusMenuItem("New Note", Icons.Default.NoteAdd, onDismiss)
            PlusMenuItem("Log Service", Icons.Default.AssignmentTurnedIn, onDismiss)
            PlusMenuItem("Capture Photo", Icons.Default.CameraAlt, onDismiss)
        }
    }
}

@Composable
fun PlusMenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(CardBackground)
            .clickable { onClick() }
            .padding(horizontal = SpacingSection, vertical = SpacingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpacingSmall)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
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
            .padding(SpacingStandard),
        verticalArrangement = Arrangement.spacedBy(SpacingSmall)
    ) {
        Text("SYSTEM DIAGNOSTICS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AccentColor)
        
        Button(onClick = { viewModel.onNewSessionClicked() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Text("Start New Session")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = state.isAutoSpeakEnabled,
                onCheckedChange = { viewModel.onToggleAutoSpeakClicked() }
            )
            Spacer(modifier = Modifier.width(SpacingCompact))
            Text("Auto-speak responses", style = MaterialTheme.typography.bodySmall)
        }

        Text("META DAT SDK", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(SpacingCompact)) {
            Button(onClick = { viewModel.onRegisterMetaDatClicked() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                Text("Register", fontSize = 10.sp)
            }
            Button(onClick = { viewModel.onCreateSessionClicked() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                Text("Session", fontSize = 10.sp)
            }
            Button(onClick = { viewModel.onCloseSessionClicked() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                Text("Close", fontSize = 10.sp)
            }
        }

        Text("AUDIO & WAKE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(SpacingCompact)) {
            Button(
                onClick = { viewModel.onToggleWakeModeClicked() }, 
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = if (state.isWakeModeEnabled) ButtonDefaults.buttonColors(containerColor = Color.Red) else ButtonDefaults.buttonColors()
            ) {
                Text(if (state.isWakeModeEnabled) "Stop Wake" else "Start Wake", fontSize = 10.sp)
            }
            Button(onClick = { viewModel.onStartAudioRouteClicked() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                Text("Route BT", fontSize = 10.sp)
            }
        }

        Text("METRICS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(SpacingSmall)) {
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
                Spacer(modifier = Modifier.height(SpacingCompact))
                Row(horizontalArrangement = Arrangement.spacedBy(SpacingCompact)) {
                    Button(onClick = { viewModel.onMarkDeliberateWakeClicked() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("Try Wake", fontSize = 10.sp)
                    }
                    Button(onClick = { viewModel.onMarkMissedWakeClicked() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("Missed", fontSize = 10.sp)
                    }
                }
                Button(onClick = { viewModel.onResetMetricsClicked() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Text("Reset Metrics", fontSize = 10.sp)
                }
                Button(onClick = { viewModel.onExportMetricsClicked() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Text("Export Summary", fontSize = 10.sp)
                }
            }
        }

        Text("APP DATA", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(SpacingCompact)) {
            Button(onClick = { viewModel.onSyncRouteClicked() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                Text("Sync Route", fontSize = 10.sp)
            }
            Button(onClick = { viewModel.onClearDraftClicked() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                Text("Clear Draft", fontSize = 10.sp)
            }
        }
        Button(
            onClick = { viewModel.onResetSettingsClicked() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Reset All Settings", fontSize = 10.sp)
        }

        Text("WAKE WORD TUNING", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(SpacingMicro)) {
            Text("Sensitivity: ${"%.2f".format(state.wakeSensitivity)}", style = MaterialTheme.typography.bodySmall) 
            Row(horizontalArrangement = Arrangement.spacedBy(SpacingMicro)) {
                listOf(0.3f, 0.5f, 0.7f).forEach { s ->
                    Button(
                        onClick = { viewModel.onSetWakeSensitivity(s) }, 
                        modifier = Modifier.weight(1f),
                        colors = if (state.wakeSensitivity == s) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(s.toString(), fontSize = 10.sp)
                    }
                }
            }
            Text("Debounce: ${state.wakeDebounceMs}ms", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(SpacingMicro)) {
                listOf(750L, 1000L, 1500L).forEach { d ->
                    Button(
                        onClick = { viewModel.onSetWakeDebounce(d) }, 
                        modifier = Modifier.weight(1f),
                        colors = if (state.wakeDebounceMs == d) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(d.toString(), fontSize = 10.sp)
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.onTriggerLocationHandshake() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Simulate McCullough GPS Arrival", fontSize = 10.sp)
        }
        
        Button(
            onClick = { viewModel.onStartWearableSpeechTestClicked() },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.metaDatStatus is MetaDatStatus.SessionReady && 
                      state.turnState is ConversationTurnState.Idle,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Test Wearable Voice Turn")
        }
        
        Spacer(modifier = Modifier.height(SpacingStandard))
    }
}

@Composable
fun ServiceVisitCard(state: com.example.hermesbridge.serviceentry.ServiceVisitState, viewModel: AgentViewModel) {
    var showTreatmentPlan by remember { mutableStateOf(state.historicalContext == null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(SpacingStandard)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.activePool?.customerName ?: "Unknown Pool",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = state.activePool?.serviceAddress ?: "No address",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentColor.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = (state.backendServiceStatus ?: state.flowState.name).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(SpacingCompact))
            Row(horizontalArrangement = Arrangement.spacedBy(SpacingStandard)) {
                Text(
                    text = "VOL: ${state.activePool?.workingVolumeGallons} gal",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryText
                )
                if (state.visitId != null) {
                    Text(
                        text = "VISIT ID: ${state.visitId.takeLast(8)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText
                    )
                }
            }
            
            if (state.isReadyForReadings) {
                Spacer(modifier = Modifier.height(SpacingStandard))
                Text("WATER TEST", style = MaterialTheme.typography.labelSmall, color = SecondaryText, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ReadingMiniTag("CL", state.currentReadings.freeChlorine)
                    ReadingMiniTag("PH", state.currentReadings.ph)
                    ReadingMiniTag("TA", state.currentReadings.totalAlkalinity)
                    ReadingMiniTag("CH", state.currentReadings.calciumHardness)
                }
            }

            state.historicalContext?.let { context ->
                Spacer(modifier = Modifier.height(SpacingStandard))
                Text("HISTORICAL CONTEXT", style = MaterialTheme.typography.labelSmall, color = SecondaryText, fontWeight = FontWeight.Bold)
                
                context.previousVisitDate?.let { date ->
                    Text("Previous Visit: $date", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                
                context.previousReadings?.let { prev ->
                    if (prev.freeChlorine != null || prev.ph != null || prev.totalAlkalinity != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ReadingMiniTag("CL", prev.freeChlorine)
                            ReadingMiniTag("PH", prev.ph)
                            ReadingMiniTag("TA", prev.totalAlkalinity)
                        }
                    }
                }
                
                val displayTrends = context.getTrendsForDisplay(limit = 3)
                if (displayTrends.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(SpacingCompact))
                    displayTrends.forEach { trend ->
                        Text(
                            text = "• $trend",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryText,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    val moreCount = context.getMoreTrendsCount(limit = 3)
                    if (moreCount > 0) {
                        Text(
                            text = "+$moreCount more",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // F13D: History-informed note
                    Spacer(modifier = Modifier.height(SpacingSmall))
                    Text(
                        text = "Use history as context before confirming treatment.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentColor,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }

                // F13D: Review Treatment Plan Button
                if (state.recommendations.isNotEmpty() && !showTreatmentPlan) {
                    Spacer(modifier = Modifier.height(SpacingSmall))
                    OutlinedButton(
                        onClick = { showTreatmentPlan = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentColor)
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Review Treatment Plan", fontSize = 11.sp)
                    }
                }
            }

            if (state.recommendations.isNotEmpty() && showTreatmentPlan) {
                Spacer(modifier = Modifier.height(SpacingStandard))
                Text("RECOMMENDED TREATMENT", style = MaterialTheme.typography.labelSmall, color = AccentColor, fontWeight = FontWeight.Bold)
                state.recommendations.forEach { rec ->
                    val prodName = rec.name ?: rec.productId.replace("_", " ")
                    Text(
                        text = "• ${rec.amount} ${rec.unit} $prodName",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (!state.treatmentPlanConfirmed) {
                    Spacer(modifier = Modifier.height(SpacingSmall))
                    Button(
                        onClick = { viewModel.onConfirmTreatmentPlanClicked() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
                    ) {
                        Text("Confirm Treatment Plan", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(modifier = Modifier.height(SpacingSmall))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Treatment plan confirmed. Ready to log service.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Green,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!state.serviceLogReady) {
                        Spacer(modifier = Modifier.height(SpacingSmall))
                        Button(
                            onClick = { viewModel.onMarkServiceLogReadyClicked() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
                        ) {
                            Text("Ready to Log Service", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(modifier = Modifier.height(SpacingSmall))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = AccentColor, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Service log ready. Final completion step pending.",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReadingMiniTag(label: String, value: Float?) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = SecondaryText)
        Text(
            text = if (value == 0.0f) "0.0" else value?.toString() ?: "--",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (value != null) Color.White else Color.DarkGray
        )
    }
}

@Composable
fun PoolSelectionMenu(
    candidates: List<com.example.hermesbridge.PoolRecord>,
    onSelected: (com.example.hermesbridge.PoolRecord) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Arrival") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                candidates.forEach { pool ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(pool) },
                        colors = CardDefaults.cardColors(containerColor = CardBackground)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(pool.customerName, fontWeight = FontWeight.Bold)
                            Text(pool.serviceAddress, style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = DarkBackground,
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Preview(showBackground = true)
@Composable
fun AgentScreenPreview() {
    MaterialTheme {
        // Preview dummy
    }
}
