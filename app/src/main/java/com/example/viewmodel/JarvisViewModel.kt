package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.CommandLogEntry
import com.example.model.DeviceTelemetrySnapshot
import com.example.model.ExecutionResult
import com.example.model.InstalledAppInfo
import com.example.model.JarvisIntentType
import com.example.model.JarvisPromptContract
import com.example.model.JarvisResponse
import com.example.system.TabletActionExecutor
import com.example.voice.JarvisVoiceManager
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "JarvisViewModel"

sealed interface JarvisAiState {
    data object Ready : JarvisAiState
    data class Processing(val command: String) : JarvisAiState
    data class Success(val entry: CommandLogEntry) : JarvisAiState
    data class Error(val message: String) : JarvisAiState
    data object Standby : JarvisAiState
}

enum class JarvisTab {
    CONSOLE,
    CAPABILITIES,
    SCHEMA_INSPECTOR
}

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val actionExecutor = TabletActionExecutor(application.applicationContext)

    val voiceManager = JarvisVoiceManager(
        context = application.applicationContext,
        onCommandTranscribed = { transcript ->
            processUserCommand(command = transcript, isVoiceOrigin = true)
        },
        onSpeechError = { errorMsg ->
            _aiState.value = JarvisAiState.Error(errorMsg)
        }
    )

    private val jarvisSchema = Schema.obj(
        properties = mapOf(
            "intent" to Schema.enumeration(
                values = listOf(
                    "open_app",
                    "media_control",
                    "adjust_volume",
                    "device_status",
                    "type_text",
                    "general_response"
                ),
                description = "The system capability name to execute."
            ),
            "parameter" to Schema.string(
                description = "The parameter for the capability (package name, media action, volume direction, text to type, or null if not needed).",
                nullable = true
            ),
            "speak" to Schema.string(
                description = "What JARVIS will say back to the user."
            )
        ),
        optionalProperties = listOf("parameter")
    )

    private val model by lazy {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = jarvisSchema
            temperature = 0.2f
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config,
            systemInstruction = content {
                text(JarvisPromptContract.SYSTEM_INSTRUCTION)
            }
        )
    }

    private val _selectedTab = MutableStateFlow(JarvisTab.CONSOLE)
    val selectedTab: StateFlow<JarvisTab> = _selectedTab.asStateFlow()

    private val _aiState = MutableStateFlow<JarvisAiState>(JarvisAiState.Ready)
    val aiState: StateFlow<JarvisAiState> = _aiState.asStateFlow()

    private val _commandInput = MutableStateFlow("")
    val commandInput: StateFlow<String> = _commandInput.asStateFlow()

    private val _activeTextBuffer = MutableStateFlow(
        "STARK HUD TELEMETRY LOG // Ready for type_text(text) injection."
    )
    val activeTextBuffer: StateFlow<String> = _activeTextBuffer.asStateFlow()

    private val _telemetry = MutableStateFlow(DeviceTelemetrySnapshot())
    val telemetry: StateFlow<DeviceTelemetrySnapshot> = _telemetry.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _commandLogs = MutableStateFlow<List<CommandLogEntry>>(emptyList())
    val commandLogs: StateFlow<List<CommandLogEntry>> = _commandLogs.asStateFlow()

    init {
        refreshTelemetry()
        _installedApps.value = actionExecutor.queryInstalledApps()
        seedInitialExamples()
        startTelemetryTicker()
    }

    private fun startTelemetryTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(2_000L)
                refreshTelemetry()
            }
        }
    }

    fun refreshTelemetry() {
        _telemetry.value = actionExecutor.readTelemetry()
    }

    fun selectTab(tab: JarvisTab) {
        _selectedTab.value = tab
        if (tab == JarvisTab.CAPABILITIES) {
            _installedApps.value = actionExecutor.queryInstalledApps()
            refreshTelemetry()
        }
    }

    fun updateCommandInput(text: String) {
        _commandInput.value = text
    }

    fun updateActiveTextBuffer(text: String) {
        _activeTextBuffer.value = text
    }

    fun clearLogs() {
        _commandLogs.value = emptyList()
    }

    fun wakeSystems() {
        _aiState.value = JarvisAiState.Ready
        voiceManager.speak("JARVIS core systems online. Listening for commands, Sir.")
    }

    fun dismissError() {
        if (_aiState.value is JarvisAiState.Error) {
            _aiState.value = JarvisAiState.Ready
        }
    }

    fun processUserCommand(command: String, isVoiceOrigin: Boolean = false) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        _commandInput.value = ""

        // Check for local shutdown/exit loop commands matching the Python reference script
        val normalized = trimmed.lowercase(Locale.US)
        if (normalized in listOf("exit", "shutdown", "goodbye", "jarvis shutdown")) {
            val shutdownSpeak = "Shutting down core systems. Goodbye, Sir."
            val shutdownResponse = JarvisResponse(
                intent = "general_response",
                parameter = null,
                speak = shutdownSpeak
            )
            val entry = CommandLogEntry(
                id = System.currentTimeMillis(),
                timestamp = currentTimestamp(),
                userCommand = trimmed,
                isVoiceOrigin = isVoiceOrigin,
                response = shutdownResponse,
                rawJson = shutdownResponse.toRawJsonString(pretty = true),
                executionResult = ExecutionResult(
                    executed = true,
                    actionSummary = "Core Systems Standby Initiated",
                    technicalDetail = "Session loop paused per user 'shutdown/exit/goodbye' directive.",
                    shellCommandTrace = "# session exit"
                )
            )
            _commandLogs.update { listOf(entry) + it }
            _aiState.value = JarvisAiState.Standby
            voiceManager.speak(shutdownSpeak)
            return
        }

        viewModelScope.launch {
            _aiState.value = JarvisAiState.Processing(trimmed)
            try {
                val installedContext = _installedApps.value
                    .take(25)
                    .joinToString(", ") { "${it.appName} (${it.packageName})" }

                val contextualPrompt = buildString {
                    appendLine("Current tablet telemetry: Battery ${_telemetry.value.batteryPercent}%, Local time ${_telemetry.value.currentTimeFormatted}, Volume ${_telemetry.value.volumeCurrent}/${_telemetry.value.volumeMax}.")
                    if (installedContext.isNotBlank()) {
                        appendLine("Installed launchable tablet apps: $installedContext")
                    }
                    append("User voice command: \"$trimmed\"")
                }

                val response = withTimeout(60_000L) {
                    model.generateContent(contextualPrompt)
                }

                val rawJsonText = response.text
                if (rawJsonText.isNullOrBlank()) {
                    _aiState.value = JarvisAiState.Error("Model returned an empty JSON response.")
                    return@launch
                }

                // Direct JSON parsing without regex or code fence stripping per Firebase AI Logic guidelines
                val jsonObject = JSONObject(rawJsonText)
                val intentStr = jsonObject.optString("intent", "general_response")
                val parameterVal = if (jsonObject.isNull("parameter")) {
                    null
                } else {
                    jsonObject.optString("parameter").takeIf {
                        it.isNotBlank() && !it.equals("null", ignoreCase = true)
                    }
                }
                val speakStr = jsonObject.optString("speak", "Command processed, Sir.")

                val parsedResponse = JarvisResponse(
                    intent = intentStr,
                    parameter = parameterVal,
                    speak = speakStr
                )

                val execResult = actionExecutor.executeCommand(
                    response = parsedResponse,
                    onTypeText = { injectedText ->
                        _activeTextBuffer.value = injectedText
                    }
                )

                refreshTelemetry()

                val formattedJson = runCatching {
                    JSONObject(rawJsonText).toString(2)
                }.getOrDefault(rawJsonText)

                val logEntry = CommandLogEntry(
                    id = System.currentTimeMillis(),
                    timestamp = currentTimestamp(),
                    userCommand = trimmed,
                    isVoiceOrigin = isVoiceOrigin,
                    response = parsedResponse,
                    rawJson = formattedJson,
                    executionResult = execResult
                )

                _commandLogs.update { listOf(logEntry) + it }
                _aiState.value = JarvisAiState.Success(logEntry)
                voiceManager.speak(parsedResponse.speak)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "JARVIS intent analysis timed out", e)
                _aiState.value = JarvisAiState.Error(
                    "Systems timeout after 60 seconds, Sir. Please verify network connectivity and retry."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI generation or JSON parsing failed", e)
                _aiState.value = JarvisAiState.Error(
                    e.localizedMessage ?: "Systems error while analyzing command, Sir."
                )
            }
        }
    }

    /**
     * Allows direct execution of a system capability from the Capabilities & Controls deck,
     * generating the exact canonical JSON response and running the native tablet action immediately.
     */
    fun triggerDirectCapability(
        intentType: JarvisIntentType,
        parameter: String?,
        simulatedUserCommand: String,
        speakMessage: String
    ) {
        val response = JarvisResponse(
            intent = intentType.wireName,
            parameter = parameter,
            speak = speakMessage
        )
        val execResult = actionExecutor.executeCommand(
            response = response,
            onTypeText = { injectedText ->
                _activeTextBuffer.value = injectedText
            }
        )
        refreshTelemetry()
        val entry = CommandLogEntry(
            id = System.currentTimeMillis(),
            timestamp = currentTimestamp(),
            userCommand = simulatedUserCommand,
            isVoiceOrigin = false,
            response = response,
            rawJson = response.toRawJsonString(pretty = true),
            executionResult = execResult
        )
        _commandLogs.update { listOf(entry) + it }
        _aiState.value = JarvisAiState.Success(entry)
        voiceManager.speak(speakMessage)
    }

    private fun seedInitialExamples() {
        val now = currentTimestamp()
        val sample1 = JarvisResponse(
            intent = "media_control",
            parameter = "play",
            speak = "Playing your media, Sir."
        )
        val sample2 = JarvisResponse(
            intent = "open_app",
            parameter = "com.google.android.youtube",
            speak = "Opening YouTube right away."
        )
        val sample3 = JarvisResponse(
            intent = "general_response",
            parameter = null,
            speak = "Checking the latest sports data for you, Sir."
        )

        _commandLogs.value = listOf(
            CommandLogEntry(
                id = 3L,
                timestamp = now,
                userCommand = "JARVIS, play some music",
                isVoiceOrigin = true,
                response = sample1,
                rawJson = sample1.toRawJsonString(pretty = true),
                executionResult = ExecutionResult(
                    executed = true,
                    actionSummary = "Media Transport: PLAY (KeyCode 126)",
                    technicalDetail = "Ready to dispatch KeyEvent.KEYCODE_MEDIA_PLAY via AudioManager.",
                    shellCommandTrace = "input keyevent 126"
                )
            ),
            CommandLogEntry(
                id = 2L,
                timestamp = now,
                userCommand = "Open YouTube",
                isVoiceOrigin = true,
                response = sample2,
                rawJson = sample2.toRawJsonString(pretty = true),
                executionResult = ExecutionResult(
                    executed = true,
                    actionSummary = "Target Package: com.google.android.youtube",
                    technicalDetail = "Configured for PackageManager launch intent resolution.",
                    shellCommandTrace = "monkey -p com.google.android.youtube -c android.intent.category.LAUNCHER 1"
                )
            ),
            CommandLogEntry(
                id = 1L,
                timestamp = now,
                userCommand = "Who won the match yesterday?",
                isVoiceOrigin = true,
                response = sample3,
                rawJson = sample3.toRawJsonString(pretty = true),
                executionResult = ExecutionResult(
                    executed = true,
                    actionSummary = "Voice Synthesis & Dialogue Response",
                    technicalDetail = "Spoken via Android TextToSpeech engine.",
                    shellCommandTrace = "# general_response -> TTS output only"
                )
            )
        )
    }

    private fun currentTimestamp(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.release()
    }
}
