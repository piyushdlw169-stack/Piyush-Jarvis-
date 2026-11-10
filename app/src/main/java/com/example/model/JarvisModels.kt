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
        signature = "action: \"open_app\" | target: \"<app_name_or_package>\"",
        description = "Resolves and launches any installed Android application by name or package via PackageManager."
    ),
    GLOBAL_ACTION(
        wireName = "global_action",
        title = "Accessibility Global Action",
        signature = "action: \"global_action\" | target: \"home\" | \"back\" | \"recents\" | \"notifications\" | \"screenshot\"",
        description = "Executes system-wide navigation or screenshot via JarvisAccessibilityService.performGlobalAction()."
    ),
    ADJUST_VOLUME(
        wireName = "adjust_volume",
        title = "Audio Stream Control",
        signature = "action: \"adjust_volume\" | target: \"up\" | \"down\" | \"mute\" | \"ring_up\" | \"ring_down\"",
        description = "Adjusts STREAM_MUSIC or STREAM_RING volume via AudioManager with hardware UI feedback."
    ),
    FLASHLIGHT(
        wireName = "flashlight",
        title = "Hardware Torch Control",
        signature = "action: \"flashlight\" | target: \"on\" | \"off\" | \"toggle\"",
        description = "Toggles the device camera flashlight via CameraManager.setTorchMode()."
    ),
    TAP(
        wireName = "tap",
        title = "Screen Tap Gesture",
        signature = "action: \"tap\" | target: \"<x>,<y>\"",
        description = "Dispatches a precision screen tap gesture at (x, y) via AccessibilityService.dispatchGesture()."
    ),
    SWIPE(
        wireName = "swipe",
        title = "Screen Swipe Gesture",
        signature = "action: \"swipe\" | target: \"up\" | \"down\" | \"left\" | \"right\" | \"x1,y1,x2,y2\"",
        description = "Dispatches a smooth swipe path across the screen via AccessibilityService.dispatchGesture()."
    ),
    MEDIA_CONTROL(
        wireName = "media_control",
        title = "Media Transport",
        signature = "action: \"media_control\" | target: \"play\" | \"pause\" | \"next\" | \"previous\"",
        description = "Dispatches hardware media key events (KeyEvent 126/127/87/88) to AudioManager."
    ),
    DEVICE_STATUS(
        wireName = "device_status",
        title = "Device Diagnostics",
        signature = "action: \"device_status\" | target: \"battery\" | \"time\" | null",
        description = "Reads real-time BatteryManager telemetry, charging voltage/temp, and local clock."
    ),
    TYPE_TEXT(
        wireName = "type_text",
        title = "Active Text Injection",
        signature = "action: \"type_text\" | target: \"<text>\"",
        description = "Types text into the active HUD input buffer and copies to system ClipboardManager."
    ),
    GENERAL_RESPONSE(
        wireName = "chat",
        title = "Conversational AI",
        signature = "action: \"chat\" | target: null",
        description = "Answers general queries and synthesizes spoken feedback via Android TextToSpeech."
    );

    companion object {
        fun fromWireName(name: String?): JarvisIntentType {
            val cleaned = name?.trim()?.lowercase() ?: return GENERAL_RESPONSE
            return when (cleaned) {
                "open_app", "launch_app" -> OPEN_APP
                "global_action", "system_action", "navigate" -> GLOBAL_ACTION
                "adjust_volume", "volume" -> ADJUST_VOLUME
                "flashlight", "torch" -> FLASHLIGHT
                "tap", "click" -> TAP
                "swipe", "scroll" -> SWIPE
                "media_control", "media" -> MEDIA_CONTROL
                "device_status", "status", "battery" -> DEVICE_STATUS
                "type_text", "type" -> TYPE_TEXT
                "chat", "general_response", "none" -> GENERAL_RESPONSE
                else -> entries.firstOrNull { it.wireName.equals(cleaned, ignoreCase = true) }
                    ?: GENERAL_RESPONSE
            }
        }
    }
}

data class JarvisResponse(
    val action: String,
    val target: String?,
    val speech: String
) {
    // Compatibility properties for intent/parameter/speak callers
    val intent: String get() = action
    val parameter: String? get() = target
    val speak: String get() = speech

    val intentType: JarvisIntentType
        get() = JarvisIntentType.fromWireName(action)

    fun toRawJsonString(pretty: Boolean = true): String {
        val escapedAction = escapeJson(action)
        val targetJson = if (target == null) "null" else "\"${escapeJson(target)}\""
        val escapedSpeech = escapeJson(speech)
        return if (pretty) {
            """
            {
              "action": "$escapedAction",
              "target": $targetJson,
              "speech": "$escapedSpeech"
            }
            """.trimIndent()
        } else {
            """{"action": "$escapedAction", "target": $targetJson, "speech": "$escapedSpeech"}"""
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
    val isFlashlightOn: Boolean = false,
    val isAccessibilityConnected: Boolean = false,
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
        You are JARVIS, a highly advanced AI voice assistant running natively on the user's Android phone/tablet.
        Analyze the user's spoken or typed command and return ONLY a strict JSON object with the fields:
        {
          "action": "open_app | global_action | adjust_volume | flashlight | tap | swipe | media_control | device_status | type_text | chat",
          "target": "value_or_null",
          "speech": "What JARVIS speaks out loud to the user"
        }

        Supported actions and target values:
        1. "open_app" -> target: app name or package name (e.g., "YouTube", "com.google.android.youtube", "Settings", "Chrome", "Calculator", "Spotify")
        2. "global_action" -> target: "home", "back", "recents", "notifications", or "screenshot"
        3. "adjust_volume" -> target: "up", "down", "mute", "ring_up", or "ring_down"
        4. "flashlight" -> target: "on", "off", or "toggle"
        5. "tap" -> target: "x,y" screen coordinates in pixels (e.g., "540,960")
        6. "swipe" -> target: "up", "down", "left", "right", or "x1,y1,x2,y2"
        7. "media_control" -> target: "play", "pause", "next", or "previous"
        8. "device_status" -> target: "battery", "time", or null
        9. "type_text" -> target: exact text string to type into the active field
        10. "chat" -> target: null (when the user is chatting or asking a general question)

        Tone Guidelines for "speech":
        - Speak like J.A.R.V.I.S.: calm, intelligent, concise, and address the user respectfully as "Sir".
        - Examples:
          User: "JARVIS, open YouTube"
          Response: {"action": "open_app", "target": "com.google.android.youtube", "speech": "Opening YouTube right away, Sir."}
          User: "Take a screenshot"
          Response: {"action": "global_action", "target": "screenshot", "speech": "Capturing your screen now, Sir."}
          User: "Turn on the flashlight"
          Response: {"action": "flashlight", "target": "on", "speech": "Flashlight activated, Sir."}
          User: "Swipe up"
          Response: {"action": "swipe", "target": "up", "speech": "Scrolling up, Sir."}
          User: "JARVIS, play some music"
          Response: {"action": "media_control", "target": "play", "speech": "Playing your media, Sir."}
    """.trimIndent()

    val PRESET_COMMANDS = listOf(
        QuickCommandPreset(
            label = "Open YouTube",
            command = "JARVIS, open YouTube",
            category = JarvisIntentType.OPEN_APP
        ),
        QuickCommandPreset(
            label = "Go Home",
            command = "Go to the home screen",
            category = JarvisIntentType.GLOBAL_ACTION
        ),
        QuickCommandPreset(
            label = "Take Screenshot",
            command = "JARVIS, take a screenshot",
            category = JarvisIntentType.GLOBAL_ACTION
        ),
        QuickCommandPreset(
            label = "Flashlight On",
            command = "Turn on the flashlight",
            category = JarvisIntentType.FLASHLIGHT
        ),
        QuickCommandPreset(
            label = "Swipe Up",
            command = "Swipe up on the screen",
            category = JarvisIntentType.SWIPE
        ),
        QuickCommandPreset(
            label = "Play Music",
            command = "JARVIS, play some music",
            category = JarvisIntentType.MEDIA_CONTROL
        ),
        QuickCommandPreset(
            label = "Volume Up",
            command = "Turn the volume up",
            category = JarvisIntentType.ADJUST_VOLUME
        ),
        QuickCommandPreset(
            label = "Battery Status",
            command = "What is my battery level and current time?",
            category = JarvisIntentType.DEVICE_STATUS
        )
    )
}
