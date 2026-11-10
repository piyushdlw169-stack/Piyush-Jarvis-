package com.example.viewmodel

import android.app.Application
import android.content.Context
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
private const val PREFS_NAME = "jarvis_assistant_prefs"
private const val KEY_CUSTOM_GEMINI_API_KEY = "custom_gemini_api_key"
private const val KEY_VOICE_PITCH = "voice_pitch"
private const val KEY_VOICE_RATE = "voice_rate"

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
    SCHEMA_INSPECTOR,
    SETTINGS
}

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
            "action" to Schema.enumeration(
                values = listOf(
                    "open_app",
                    "global_action",
                    "adjust_volume",
                    "flashlight",
                    "tap",
                    "swipe",
                    "media_control",
                    "device_status",
                    "type_text",
                    "chat"
                ),
                description = "The device action or capability for JARVIS to execute."
            ),
            "target" to Schema.string(
                description = "Target parameter (app name/package, global action name, volume direction, flashlight state, x,y coordinates, swipe direction, text to type, or null).",
                nullable = true
            ),
            "speech" to Schema.string(
                description = "What JARVIS speaks out loud to the user."
            )
        ),
        optionalProperties = listOf("target")
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

    private val _savedApiKey = MutableStateFlow(prefs.getString(KEY_CUSTOM_GEMINI_API_KEY, "").orEmpty())
    val savedApiKey: StateFlow<String> = _savedApiKey.asStateFlow()

    private val _voicePitch = MutableStateFlow(prefs.getFloat(KEY_VOICE_PITCH, 0.92f))
    val voicePitch: StateFlow<Float> = _voicePitch.asStateFlow()

    private val _voiceRate = MutableStateFlow(prefs.getFloat(KEY_VOICE_RATE, 1.04f))
    val voiceRate: StateFlow<Float> = _voiceRate.asStateFlow()

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
        if (tab == JarvisTab.CAPABILITIES || tab == JarvisTab.SETTINGS) {
            _installedApps.value = actionExecutor.queryInstalledApps()
            refreshTelemetry()
        }
    }

    fun saveApiKeyToPrefs(apiKey: String) {
        val trimmed = apiKey.trim()
        prefs.edit().putString(KEY_CUSTOM_GEMINI_API_KEY, trimmed).apply()
        _savedApiKey.value = trimmed
    }

    fun updateVoiceTuning(pitch: Float, rate: Float) {
        val coercedPitch = pitch.coerceIn(0.5f, 1.5f)
        val coercedRate = rate.coerceIn(0.6f, 1.6f)
        prefs.edit()
            .putFloat(KEY_VOICE_PITCH, coercedPitch)
            .putFloat(KEY_VOICE_RATE, coercedRate)
            .apply()
        _voicePitch.value = coercedPitch
        _voiceRate.value = coercedRate
        voiceManager.setVoiceTuning(coercedPitch, coercedRate)
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

        val normalized = trimmed.lowercase(Locale.US)
        if (normalized in listOf("exit", "shutdown", "goodbye", "jarvis shutdown")) {
            val shutdownSpeak = "Shutting down core systems. Goodbye, Sir."
            val shutdownResponse = JarvisResponse(
                action = "chat",
                target = null,
                speech = shutdownSpeak
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
                    appendLine("Current tablet telemetry: Battery ${_telemetry.value.batteryPercent}%, Local time ${_telemetry.value.currentTimeFormatted}, Volume ${_telemetry.value.volumeCurrent}/${_telemetry.value.volumeMax}, Flashlight=${if (_telemetry.value.isFlashlightOn) "ON" else "OFF"}, AccessibilityService=${if (_telemetry.value.isAccessibilityConnected) "CONNECTED" else "DISABLED"}.")
                    if (installedContext.isNotBlank()) {
                        appendLine("Installed launchable apps: $installedContext")
                    }
                    append("User command: \"$trimmed\"")
                }

                val response = withTimeout(60_000L) {
                    model.generateContent(contextualPrompt)
                }

                val rawJsonText = response.text
                if (rawJsonText.isNullOrBlank()) {
                    _aiState.value = JarvisAiState.Error("Model returned an empty JSON response.")
                    return@launch
                }

                val jsonObject = JSONObject(rawJsonText)
                val actionStr = jsonObject.optString("action")
                    .ifBlank { jsonObject.optString("intent", "chat") }
                val targetVal = if (!jsonObject.isNull("target") && jsonObject.has("target")) {
                    jsonObject.optString("target").takeIf {
                        it.isNotBlank() && !it.equals("null", ignoreCase = true)
                    }
                } else if (!jsonObject.isNull("parameter") && jsonObject.has("parameter")) {
                    jsonObject.optString("parameter").takeIf {
                        it.isNotBlank() && !it.equals("null", ignoreCase = true)
                    }
                } else {
                    null
                }
                val speechStr = jsonObject.optString("speech")
                    .ifBlank { jsonObject.optString("speak", "Command processed, Sir.") }

                val parsedResponse = JarvisResponse(
                    action = actionStr,
                    target = targetVal,
                    speech = speechStr
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
                voiceManager.speak(parsedResponse.speech)
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

    fun triggerDirectCapability(
        intentType: JarvisIntentType,
        parameter: String?,
        simulatedUserCommand: String,
        speakMessage: String
    ) {
        val response = JarvisResponse(
            action = intentType.wireName,
            target = parameter,
            speech = speakMessage
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
            action = "open_app",
            target = "com.google.android.youtube",
            speech = "Opening YouTube right away, Sir."
        )
        val sample2 = JarvisResponse(
            action = "global_action",
            target = "screenshot",
            speech = "Capturing your screen now, Sir."
        )
        val sample3 = JarvisResponse(
            action = "media_control",
            target = "play",
            speech = "Playing your media, Sir."
        )

        _commandLogs.value = listOf(
            CommandLogEntry(
                id = 3L,
                timestamp = now,
                userCommand = "Open YouTube",
                isVoiceOrigin = true,
                response = sample1,
                rawJson = sample1.toRawJsonString(pretty = true),
                executionResult = ExecutionResult(
                    executed = true,
                    actionSummary = "Target Package: com.google.android.youtube",
                    technicalDetail = "Resolved via JarvisAccessibilityService.openAppByName(PackageManager).",
                    shellCommandTrace = "monkey -p com.google.android.youtube -c android.intent.category.LAUNCHER 1"
                )
            ),
            CommandLogEntry(
                id = 2L,
                timestamp = now,
                userCommand = "JARVIS, take a screenshot",
                isVoiceOrigin = true,
                response = sample2,
                rawJson = sample2.toRawJsonString(pretty = true),
                executionResult = ExecutionResult(
                    executed = true,
                    actionSummary = "Accessibility Global Action: SCREENSHOT",
                    technicalDetail = "Configured for JarvisAccessibilityService.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT).",
                    shellCommandTrace = "AccessibilityService.performGlobalAction(screenshot)"
                )
            ),
            CommandLogEntry(
                id = 1L,
                timestamp = now,
                userCommand = "JARVIS, play some music",
                isVoiceOrigin = true,
                response = sample3,
                rawJson = sample3.toRawJsonString(pretty = true),
                executionResult = ExecutionResult(
                    executed = true,
                    actionSummary = "Media Transport: PLAY (KeyCode 126)",
                    technicalDetail = "Dispatched KeyEvent.KEYCODE_MEDIA_PLAY via AudioManager.",
                    shellCommandTrace = "input keyevent 126"
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
