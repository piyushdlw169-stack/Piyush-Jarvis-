package com.example.model

enum class JarvisIntentType(
    val wireName: String,
    val title: String,
    val signature: String,
    val description: String
) {
    OPEN_APP(
        wireName = "open_app",
        title = "Launch Application",
        signature = "open_app(app_package_name)",
        description = "Resolves and launches an Android application by package name or label."
    ),
    MEDIA_CONTROL(
        wireName = "media_control",
        title = "Media Transport",
        signature = "media_control(action) -> \"play\" | \"pause\" | \"next\" | \"previous\"",
        description = "Dispatches hardware media key events (KeyEvent 126/127/87/88) to AudioManager."
    ),
    ADJUST_VOLUME(
        wireName = "adjust_volume",
        title = "Audio Stream Control",
        signature = "adjust_volume(direction) -> \"up\" | \"down\" | \"mute\"",
        description = "Adjusts STREAM_MUSIC volume via AudioManager with live hardware UI feedback."
    ),
    DEVICE_STATUS(
        wireName = "device_status",
        title = "Device Diagnostics",
        signature = "device_status() -> null | \"battery\" | \"time\"",
        description = "Reads real-time BatteryManager telemetry, charging voltage/temp, and local clock."
    ),
    TYPE_TEXT(
        wireName = "type_text",
        title = "Active Text Injection",
        signature = "type_text(text)",
        description = "Types text into the active HUD input buffer and copies to system ClipboardManager."
    ),
    GENERAL_RESPONSE(
        wireName = "general_response",
        title = "Conversational AI",
        signature = "general_response(text) -> parameter: null",
        description = "Answers general queries and synthesizes spoken feedback via Android TTS."
    );

    companion object {
        fun fromWireName(name: String?): JarvisIntentType {
            return entries.firstOrNull { it.wireName.equals(name?.trim(), ignoreCase = true) }
                ?: GENERAL_RESPONSE
        }
    }
}

data class JarvisResponse(
    val intent: String,
    val parameter: String?,
    val speak: String
) {
    val intentType: JarvisIntentType
        get() = JarvisIntentType.fromWireName(intent)

    fun toRawJsonString(pretty: Boolean = true): String {
        val escapedIntent = escapeJson(intent)
        val paramJson = if (parameter == null) "null" else "\"${escapeJson(parameter)}\""
        val escapedSpeak = escapeJson(speak)
        return if (pretty) {
            """
            {
              "intent": "$escapedIntent",
              "parameter": $paramJson,
              "speak": "$escapedSpeak"
            }
            """.trimIndent()
        } else {
            """{"intent": "$escapedIntent", "parameter": $paramJson, "speak": "$escapedSpeak"}"""
        }
    }

    companion object {
        private fun escapeJson(value: String): String {
            return buildString {
                for (ch in value) {
                    when (ch) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> append(ch)
                    }
                }
            }
        }
    }
}

data class ExecutionResult(
    val executed: Boolean,
    val actionSummary: String,
    val technicalDetail: String,
    val shellCommandTrace: String
)

data class CommandLogEntry(
    val id: Long,
    val timestamp: String,
    val userCommand: String,
    val isVoiceOrigin: Boolean,
    val response: JarvisResponse,
    val rawJson: String,
    val executionResult: ExecutionResult
)

data class DeviceTelemetrySnapshot(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val batteryTempCelsius: Float = 28.5f,
    val batteryVoltageMv: Int = 4150,
    val volumeCurrent: Int = 10,
    val volumeMax: Int = 15,
    val isMuted: Boolean = false,
    val currentTimeFormatted: String = "00:00:00",
    val currentDateFormatted: String = "2026-11-09",
    val mediaPlaybackState: String = "READY",
    val installedAppsCount: Int = 0
) {
    val volumePercent: Int
        get() = if (volumeMax > 0) ((volumeCurrent.toFloat() / volumeMax) * 100).toInt() else 0
}

data class InstalledAppInfo(
    val appName: String,
    val packageName: String
)

data class QuickCommandPreset(
    val label: String,
    val command: String,
    val category: JarvisIntentType
)

object JarvisPromptContract {
    val SYSTEM_INSTRUCTION = """
        You are JARVIS, a highly advanced AI voice assistant running locally on the user's Android tablet.
        Your job is to analyze the user's voice command and translate it into a specific system action.

        You have access to the following system capabilities:
        1. open_app(app_package_name)
           Common Android package names to use when asked to open an app:
           - YouTube: "com.google.android.youtube"
           - Chrome / Browser: "com.android.chrome"
           - Settings: "com.android.settings"
           - Gmail / Email: "com.google.android.gm"
           - Maps: "com.google.android.apps.maps"
           - Calendar: "com.google.android.calendar"
           - Calculator: "com.google.android.calculator"
           - Camera: "com.android.camera2"
           - Clock / Alarms: "com.google.android.deskclock"
           - Contacts: "com.google.android.contacts"
           - Files: "com.google.android.documentsui"
           - Play Store: "com.android.vending"
           - Spotify: "com.spotify.music"
           - Netflix: "com.netflix.mediaclient"
           - Photos / Gallery: "com.google.android.apps.photos"
        2. media_control(action) -> actions: "play", "pause", "next", "previous"
        3. adjust_volume(direction) -> directions: "up", "down", "mute"
        4. device_status() -> check battery or time (set parameter to "battery", "time", or null)
        5. type_text(text) -> type text into an active field (set parameter to the exact text to type)
        6. general_response(text) -> if the user is just chatting or asking a question (set parameter to null)

        CRITICAL: You must always respond in raw JSON format matching this schema:
        {
          "intent": "capability_name",
          "parameter": "value_or_null",
          "speak": "What JARVIS will say back to the user"
        }

        Tone Guidelines for "speak":
        - Speak like J.A.R.V.I.S.: articulate, composed, concise, helpful, and address the user respectfully as "Sir" when appropriate.
        - Examples:
          User: "JARVIS, play some music"
          Response: {"intent": "media_control", "parameter": "play", "speak": "Playing your media, Sir."}
          User: "Open YouTube"
          Response: {"intent": "open_app", "parameter": "com.google.android.youtube", "speak": "Opening YouTube right away."}
          User: "Who won the match yesterday?"
          Response: {"intent": "general_response", "parameter": null, "speak": "Checking the latest sports data for you, Sir."}
    """.trimIndent()

    val PRESET_COMMANDS = listOf(
        QuickCommandPreset(
            label = "Play Music",
            command = "JARVIS, play some music",
            category = JarvisIntentType.MEDIA_CONTROL
        ),
        QuickCommandPreset(
            label = "Open YouTube",
            command = "Open YouTube",
            category = JarvisIntentType.OPEN_APP
        ),
        QuickCommandPreset(
            label = "Open Settings",
            command = "JARVIS, open Android Settings",
            category = JarvisIntentType.OPEN_APP
        ),
        QuickCommandPreset(
            label = "Volume Up",
            command = "Turn the volume up",
            category = JarvisIntentType.ADJUST_VOLUME
        ),
        QuickCommandPreset(
            label = "Mute Audio",
            command = "Mute the tablet audio",
            category = JarvisIntentType.ADJUST_VOLUME
        ),
        QuickCommandPreset(
            label = "Battery & Time",
            command = "What is my battery level and current time?",
            category = JarvisIntentType.DEVICE_STATUS
        ),
        QuickCommandPreset(
            label = "Type Note",
            command = "Type text: Diagnostic scan complete. All thrusters nominal.",
            category = JarvisIntentType.TYPE_TEXT
        ),
        QuickCommandPreset(
            label = "General Query",
            command = "Who won the match yesterday?",
            category = JarvisIntentType.GENERAL_RESPONSE
        )
    )
}
