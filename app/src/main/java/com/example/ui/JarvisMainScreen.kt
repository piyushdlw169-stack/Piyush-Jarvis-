package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.CommandLogEntry
import com.example.model.JarvisPromptContract
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepNavySurface
import com.example.ui.theme.ElevatedHudSurface
import com.example.ui.theme.HudGlassBorder
import com.example.ui.theme.IceWhiteText
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SteelCyanText
import com.example.ui.theme.TelemetryEmerald
import com.example.viewmodel.JarvisAiState
import com.example.viewmodel.JarvisTab
import com.example.viewmodel.JarvisViewModel

@Composable
fun JarvisMainScreen(
    viewModel: JarvisViewModel
) {
    val context = LocalContext.current
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val commandInput by viewModel.commandInput.collectAsStateWithLifecycle()
    val activeTextBuffer by viewModel.activeTextBuffer.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val commandLogs by viewModel.commandLogs.collectAsStateWithLifecycle()
    val savedApiKey by viewModel.savedApiKey.collectAsStateWithLifecycle()
    val voicePitch by viewModel.voicePitch.collectAsStateWithLifecycle()
    val voiceRate by viewModel.voiceRate.collectAsStateWithLifecycle()

    val isListening by viewModel.voiceManager.isListening.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.voiceManager.isSpeaking.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.voiceManager.partialTranscript.collectAsStateWithLifecycle()
    val audioRmsLevel by viewModel.voiceManager.audioRmsLevel.collectAsStateWithLifecycle()
    val ttsEnabled by viewModel.voiceManager.ttsEnabled.collectAsStateWithLifecycle()

    if (selectedTab != JarvisTab.CONSOLE) {
        BackHandler {
            viewModel.selectTab(JarvisTab.CONSOLE)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.voiceManager.startListening()
        } else {
            viewModel.voiceManager.speak("Microphone permission is required for voice capture, Sir.")
        }
    }

    val onToggleVoiceListen = {
        if (isListening) {
            viewModel.voiceManager.stopListening()
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.voiceManager.startListening()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val latestSpeakText = commandLogs.firstOrNull()?.response?.speech
        ?: "JARVIS AI system online. Listening for commands, Sir."

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val isExpandedTablet = maxWidth >= 720.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = ObsidianBg,
            bottomBar = {
                if (!isExpandedTablet) {
                    JarvisBottomNavigationBar(
                        selectedTab = selectedTab,
                        onSelectTab = viewModel::selectTab
                    )
                }
            }
        ) { innerPadding ->
            if (isExpandedTablet) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    JarvisSideNavigationRail(
                        selectedTab = selectedTab,
                        onSelectTab = viewModel::selectTab
                    )

                    Column(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TelemetryHeaderBar(
                            telemetry = telemetry,
                            aiState = aiState,
                            isListening = isListening,
                            isSpeaking = isSpeaking,
                            ttsEnabled = ttsEnabled,
                            onToggleTts = { viewModel.voiceManager.setTtsEnabled(!ttsEnabled) }
                        )

                        ArcReactorVisualizerCard(
                            isListening = isListening,
                            isSpeaking = isSpeaking,
                            isProcessing = aiState is JarvisAiState.Processing,
                            audioRmsLevel = audioRmsLevel,
                            partialTranscript = partialTranscript,
                            latestSpeakText = latestSpeakText,
                            onMicClick = onToggleVoiceListen
                        )

                        ActiveTextBufferCompactCard(
                            activeTextBuffer = activeTextBuffer,
                            onUpdateBuffer = viewModel::updateActiveTextBuffer
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                            .padding(start = 8.dp, top = 12.dp, bottom = 12.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (selectedTab) {
                            JarvisTab.CONSOLE -> {
                                CommandConsolePane(
                                    aiState = aiState,
                                    commandInput = commandInput,
                                    isListening = isListening,
                                    commandLogs = commandLogs,
                                    onUpdateInput = viewModel::updateCommandInput,
                                    onSubmitCommand = { cmd ->
                                        viewModel.processUserCommand(cmd, isVoiceOrigin = false)
                                    },
                                    onToggleVoiceListen = onToggleVoiceListen,
                                    onReplaySpeak = viewModel.voiceManager::speak,
                                    onClearLogs = viewModel::clearLogs,
                                    onWakeSystems = viewModel::wakeSystems,
                                    onDismissError = viewModel::dismissError,
                                    headerContent = null
                                )
                            }
                            JarvisTab.CAPABILITIES -> {
                                CapabilitiesDeckScreen(
                                    telemetry = telemetry,
                                    installedApps = installedApps,
                                    activeTextBuffer = activeTextBuffer,
                                    onUpdateTextBuffer = viewModel::updateActiveTextBuffer,
                                    onTriggerCapability = viewModel::triggerDirectCapability
                                )
                            }
                            JarvisTab.SCHEMA_INSPECTOR -> {
                                SchemaInspectorScreen()
                            }
                            JarvisTab.SETTINGS -> {
                                SettingsScreen(
                                    isAccessibilityConnected = telemetry.isAccessibilityConnected,
                                    savedApiKey = savedApiKey,
                                    voicePitch = voicePitch,
                                    voiceRate = voiceRate,
                                    onSaveApiKey = viewModel::saveApiKeyToPrefs,
                                    onUpdateVoiceTuning = viewModel::updateVoiceTuning,
                                    onTestVoice = viewModel.voiceManager::speak
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TelemetryHeaderBar(
                        telemetry = telemetry,
                        aiState = aiState,
                        isListening = isListening,
                        isSpeaking = isSpeaking,
                        ttsEnabled = ttsEnabled,
                        onToggleTts = { viewModel.voiceManager.setTtsEnabled(!ttsEnabled) }
                    )

                    when (selectedTab) {
                        JarvisTab.CONSOLE -> {
                            CommandConsolePane(
                                aiState = aiState,
                                commandInput = commandInput,
                                isListening = isListening,
                                commandLogs = commandLogs,
                                onUpdateInput = viewModel::updateCommandInput,
                                onSubmitCommand = { cmd ->
                                    viewModel.processUserCommand(cmd, isVoiceOrigin = false)
                                },
                                onToggleVoiceListen = onToggleVoiceListen,
                                onReplaySpeak = viewModel.voiceManager::speak,
                                onClearLogs = viewModel::clearLogs,
                                onWakeSystems = viewModel::wakeSystems,
                                onDismissError = viewModel::dismissError,
                                headerContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        ArcReactorVisualizerCard(
                                            isListening = isListening,
                                            isSpeaking = isSpeaking,
                                            isProcessing = aiState is JarvisAiState.Processing,
                                            audioRmsLevel = audioRmsLevel,
                                            partialTranscript = partialTranscript,
                                            latestSpeakText = latestSpeakText,
                                            onMicClick = onToggleVoiceListen
                                        )
                                        ActiveTextBufferCompactCard(
                                            activeTextBuffer = activeTextBuffer,
                                            onUpdateBuffer = viewModel::updateActiveTextBuffer
                                        )
                                    }
                                }
                            )
                        }
                        JarvisTab.CAPABILITIES -> {
                            CapabilitiesDeckScreen(
                                telemetry = telemetry,
                                installedApps = installedApps,
                                activeTextBuffer = activeTextBuffer,
                                onUpdateTextBuffer = viewModel::updateActiveTextBuffer,
                                onTriggerCapability = viewModel::triggerDirectCapability
                            )
                        }
                        JarvisTab.SCHEMA_INSPECTOR -> {
                            SchemaInspectorScreen()
                        }
                        JarvisTab.SETTINGS -> {
                            SettingsScreen(
                                isAccessibilityConnected = telemetry.isAccessibilityConnected,
                                savedApiKey = savedApiKey,
                                voicePitch = voicePitch,
                                voiceRate = voiceRate,
                                onSaveApiKey = viewModel::saveApiKeyToPrefs,
                                onUpdateVoiceTuning = viewModel::updateVoiceTuning,
                                onTestVoice = viewModel.voiceManager::speak
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandConsolePane(
    aiState: JarvisAiState,
    commandInput: String,
    isListening: Boolean,
    commandLogs: List<CommandLogEntry>,
    onUpdateInput: (String) -> Unit,
    onSubmitCommand: (String) -> Unit,
    onToggleVoiceListen: () -> Unit,
    onReplaySpeak: (String) -> Unit,
    onClearLogs: () -> Unit,
    onWakeSystems: () -> Unit,
    onDismissError: () -> Unit,
    headerContent: (@Composable () -> Unit)?
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnimatedVisibility(visible = aiState is JarvisAiState.Processing) {
            val processingCmd = (aiState as? JarvisAiState.Processing)?.command.orEmpty()
            Surface(
                color = ElevatedHudSurface,
                border = BorderStroke(1.dp, AmberWarning),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = ArcCyan,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Translating command to JSON action: \"$processingCmd\"…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IceWhiteText
                    )
                }
            }
        }

        AnimatedVisibility(visible = aiState is JarvisAiState.Standby) {
            Surface(
                color = ElevatedHudSurface,
                border = BorderStroke(1.dp, AmberWarning),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Core systems in Standby Mode.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AmberWarning
                    )
                    Button(
                        onClick = onWakeSystems,
                        colors = ButtonDefaults.buttonColors(containerColor = ArcCyan),
                        modifier = Modifier.testTag("wake_systems_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Wake Systems",
                            tint = ObsidianBg,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WAKE JARVIS", color = ObsidianBg, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        AnimatedVisibility(visible = aiState is JarvisAiState.Error) {
            val errMsg = (aiState as? JarvisAiState.Error)?.message.orEmpty()
            Surface(
                color = CrimsonAlert.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, CrimsonAlert),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_error_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = CrimsonAlert
                        )
                        Text(
                            text = errMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = IceWhiteText
                        )
                    }
                    TextButton(onClick = onDismissError) {
                        Text("DISMISS", color = ArcCyan)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("command_log_feed"),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (headerContent != null) {
                item {
                    headerContent()
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "QUICK VOICE / ACTION PRESETS",
                            style = MaterialTheme.typography.labelMedium,
                            color = ArcCyan
                        )
                        if (commandLogs.isNotEmpty()) {
                            TextButton(
                                onClick = onClearLogs,
                                modifier = Modifier.testTag("clear_logs_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear command logs",
                                    tint = SteelCyanText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "CLEAR LOGS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SteelCyanText
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        JarvisPromptContract.PRESET_COMMANDS.forEach { preset ->
                            AssistChip(
                                onClick = { onSubmitCommand(preset.command) },
                                label = {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = IceWhiteText
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = ElevatedHudSurface
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    enabled = true,
                                    borderColor = HudGlassBorder
                                ),
                                modifier = Modifier.testTag("preset_chip_${preset.label}")
                            )
                        }
                    }
                }
            }

            items(commandLogs, key = { it.id }) { entry ->
                CommandLogCard(
                    entry = entry,
                    onReplaySpeak = onReplaySpeak,
                    onReExecute = onSubmitCommand
                )
            }
        }

        Surface(
            color = DeepNavySurface,
            border = BorderStroke(1.dp, HudGlassBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onToggleVoiceListen,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (isListening) CrimsonAlert else ElevatedHudSurface,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .testTag("voice_input_button")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isListening) "Stop listening" else "Speak command to JARVIS",
                        tint = if (isListening) IceWhiteText else ArcCyan
                    )
                }

                OutlinedTextField(
                    value = commandInput,
                    onValueChange = onUpdateInput,
                    placeholder = {
                        Text(
                            text = "Speak or type command (e.g., \"Take a screenshot\", \"Open YouTube\")…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SteelCyanText
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (commandInput.isNotBlank()) {
                                onSubmitCommand(commandInput)
                                focusManager.clearFocus()
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ArcCyan,
                        unfocusedBorderColor = HudGlassBorder,
                        focusedTextColor = IceWhiteText,
                        unfocusedTextColor = IceWhiteText,
                        focusedContainerColor = ObsidianBg,
                        unfocusedContainerColor = ObsidianBg
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_text_input")
                )

                Button(
                    onClick = {
                        if (commandInput.isNotBlank()) {
                            onSubmitCommand(commandInput)
                            focusManager.clearFocus()
                        }
                    },
                    enabled = commandInput.isNotBlank() && aiState !is JarvisAiState.Processing,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArcCyan,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("execute_command_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Execute Command",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveTextBufferCompactCard(
    activeTextBuffer: String,
    onUpdateBuffer: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("compact_text_buffer_card"),
        colors = CardDefaults.cardColors(containerColor = DeepNavySurface),
        border = BorderStroke(1.dp, HudGlassBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "ACTIVE TEXT FIELD TARGET // type_text(text)",
                style = MaterialTheme.typography.labelMedium,
                color = TelemetryEmerald
            )
            OutlinedTextField(
                value = activeTextBuffer,
                onValueChange = onUpdateBuffer,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TelemetryEmerald,
                    unfocusedBorderColor = HudGlassBorder,
                    focusedTextColor = IceWhiteText,
                    unfocusedTextColor = IceWhiteText,
                    focusedContainerColor = ObsidianBg,
                    unfocusedContainerColor = ObsidianBg
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("console_active_text_field")
            )
        }
    }
}

@Composable
private fun JarvisBottomNavigationBar(
    selectedTab: JarvisTab,
    onSelectTab: (JarvisTab) -> Unit
) {
    NavigationBar(
        containerColor = DeepNavySurface,
        contentColor = IceWhiteText,
        modifier = Modifier.navigationBarsPadding()
    ) {
        NavigationBarItem(
            selected = selectedTab == JarvisTab.CONSOLE,
            onClick = { onSelectTab(JarvisTab.CONSOLE) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Command Console"
                )
            },
            label = { Text("Console") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = ObsidianBg,
                selectedTextColor = ArcCyan,
                indicatorColor = ArcCyan,
                unselectedIconColor = SteelCyanText,
                unselectedTextColor = SteelCyanText
            ),
            modifier = Modifier.testTag("nav_tab_console")
        )
        NavigationBarItem(
            selected = selectedTab == JarvisTab.CAPABILITIES,
            onClick = { onSelectTab(JarvisTab.CAPABILITIES) },
            icon = {
                Icon(
                    imageVector = Icons.Default.SettingsInputComponent,
                    contentDescription = "System Capabilities"
                )
            },
            label = { Text("Controls") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = ObsidianBg,
                selectedTextColor = ArcCyan,
                indicatorColor = ArcCyan,
                unselectedIconColor = SteelCyanText,
                unselectedTextColor = SteelCyanText
            ),
            modifier = Modifier.testTag("nav_tab_capabilities")
        )
        NavigationBarItem(
            selected = selectedTab == JarvisTab.SCHEMA_INSPECTOR,
            onClick = { onSelectTab(JarvisTab.SCHEMA_INSPECTOR) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "JSON Schema"
                )
            },
            label = { Text("Schema") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = ObsidianBg,
                selectedTextColor = ArcCyan,
                indicatorColor = ArcCyan,
                unselectedIconColor = SteelCyanText,
                unselectedTextColor = SteelCyanText
            ),
            modifier = Modifier.testTag("nav_tab_schema")
        )
        NavigationBarItem(
            selected = selectedTab == JarvisTab.SETTINGS,
            onClick = { onSelectTab(JarvisTab.SETTINGS) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings & Accessibility"
                )
            },
            label = { Text("Settings") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = ObsidianBg,
                selectedTextColor = ArcCyan,
                indicatorColor = ArcCyan,
                unselectedIconColor = SteelCyanText,
                unselectedTextColor = SteelCyanText
            ),
            modifier = Modifier.testTag("nav_tab_settings")
        )
    }
}

@Composable
private fun JarvisSideNavigationRail(
    selectedTab: JarvisTab,
    onSelectTab: (JarvisTab) -> Unit
) {
    NavigationRail(
        containerColor = DeepNavySurface,
        contentColor = IceWhiteText
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        NavigationRailItem(
            selected = selectedTab == JarvisTab.CONSOLE,
            onClick = { onSelectTab(JarvisTab.CONSOLE) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Command Console"
                )
            },
            label = { Text("Console") },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = ObsidianBg,
                selectedTextColor = ArcCyan,
                indicatorColor = ArcCyan,
                unselectedIconColor = SteelCyanText,
                unselectedTextColor = SteelCyanText
            ),
            modifier = Modifier.testTag("rail_tab_console")
        )
        Spacer(modifier = Modifier.height(12.dp))
        NavigationRailItem(
            selected = selectedTab == JarvisTab.CAPABILITIES,
            onClick = { onSelectTab(JarvisTab.CAPABILITIES) },
            icon = {
                Icon(
                    imageVector = Icons.Default.SettingsInputComponent,
                    contentDescription = "Capabilities"
                )
            },
            label = { Text("Controls") },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = ObsidianBg,
                selectedTextColor = ArcCyan,
                indicatorColor = ArcCyan,
                unselectedIconColor = SteelCyanText,
                unselectedTextColor = SteelCyanText
            ),
            modifier = Modifier.testTag("rail_tab_capabilities")
        )
        Spacer(modifier = Modifier.height(12.dp))
        NavigationRailItem(
            selected = selectedTab == JarvisTab.SCHEMA_INSPECTOR,
            onClick = { onSelectTab(JarvisTab.SCHEMA_INSPECTOR) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "JSON Schema"
                )
            },
            label = { Text("Schema") },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = ObsidianBg,
                selectedTextColor = ArcCyan,
                indicatorColor = ArcCyan,
                unselectedIconColor = SteelCyanText,
                unselectedTextColor = SteelCyanText
            ),
            modifier = Modifier.testTag("rail_tab_schema")
        )
        Spacer(modifier = Modifier.height(12.dp))
        NavigationRailItem(
            selected = selectedTab == JarvisTab.SETTINGS,
            onClick = { onSelectTab(JarvisTab.SETTINGS) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            },
            label = { Text("Settings") },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = ObsidianBg,
                selectedTextColor = ArcCyan,
                indicatorColor = ArcCyan,
                unselectedIconColor = SteelCyanText,
                unselectedTextColor = SteelCyanText
            ),
            modifier = Modifier.testTag("rail_tab_settings")
        )
    }
}
