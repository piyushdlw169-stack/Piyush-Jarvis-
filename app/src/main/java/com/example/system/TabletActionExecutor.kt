package com.example.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import com.example.model.DeviceTelemetrySnapshot
import com.example.model.ExecutionResult
import com.example.model.InstalledAppInfo
import com.example.model.JarvisIntentType
import com.example.model.JarvisResponse
import com.example.service.JarvisAccessibilityService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TabletActionExecutor(private val context: Context) {

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val clipboardManager: ClipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private var lastMediaState: String = "STANDBY"

    fun readTelemetry(): DeviceTelemetrySnapshot {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) {
            ((level.toFloat() / scale.toFloat()) * 100f).toInt()
        } else {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: 100
        }

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 290) ?: 290
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4120) ?: 4120

        val currentVol = runCatching {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }.getOrDefault(8)
        val maxVol = runCatching {
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        }.getOrDefault(15)
        val isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { audioManager.isStreamMute(AudioManager.STREAM_MUSIC) }.getOrDefault(currentVol == 0)
        } else {
            currentVol == 0
        }

        val now = Date()
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US).format(now)
        val dateFmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).format(now)

        val installedCount = queryInstalledApps().size

        return DeviceTelemetrySnapshot(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            batteryTempCelsius = tempTenths / 10f,
            batteryVoltageMv = voltageMv,
            volumeCurrent = currentVol,
            volumeMax = maxVol,
            isMuted = isMuted,
            currentTimeFormatted = timeFmt,
            currentDateFormatted = dateFmt,
            mediaPlaybackState = lastMediaState,
            isFlashlightOn = JarvisAccessibilityService.isFlashlightOn.value,
            isAccessibilityConnected = JarvisAccessibilityService.isConnected.value,
            installedAppsCount = installedCount
        )
    }

    fun queryInstalledApps(): List<InstalledAppInfo> {
        return runCatching {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }
            resolveInfos
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName) return@mapNotNull null
                    val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                    InstalledAppInfo(appName = label, packageName = pkg)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.appName.lowercase(Locale.US) }
        }.getOrDefault(emptyList())
    }

    fun executeCommand(
        response: JarvisResponse,
        onTypeText: (String) -> Unit
    ): ExecutionResult {
        val param = response.target?.trim()
        return when (response.intentType) {
            JarvisIntentType.OPEN_APP -> executeOpenApp(param)
            JarvisIntentType.GLOBAL_ACTION -> executeGlobalAction(param)
            JarvisIntentType.ADJUST_VOLUME -> executeAdjustVolume(param)
            JarvisIntentType.FLASHLIGHT -> executeFlashlight(param)
            JarvisIntentType.TAP -> executeTap(param)
            JarvisIntentType.SWIPE -> executeSwipe(param)
            JarvisIntentType.MEDIA_CONTROL -> executeMediaControl(param)
            JarvisIntentType.DEVICE_STATUS -> executeDeviceStatus(param)
            JarvisIntentType.TYPE_TEXT -> executeTypeText(param, onTypeText)
            JarvisIntentType.GENERAL_RESPONSE -> ExecutionResult(
                executed = true,
                actionSummary = "Voice Synthesis & Dialogue Response",
                technicalDetail = "Spoken via Android TextToSpeech engine (no physical hardware mutation required).",
                shellCommandTrace = "# action: chat -> TTS output"
            )
        }
    }

    private fun executeGlobalAction(targetParam: String?): ExecutionResult {
        val target = targetParam?.lowercase(Locale.US)?.trim() ?: "home"
        val service = JarvisAccessibilityService.instance
        if (service != null) {
            val ok = service.executeGlobalActionByName(target)
            return ExecutionResult(
                executed = ok,
                actionSummary = if (ok) {
                    "Accessibility Global Action: ${target.uppercase(Locale.US)}"
                } else {
                    "Global Action Unsupported: $target"
                },
                technicalDetail = "Invoked JarvisAccessibilityService.performGlobalAction() for target '$target'.",
                shellCommandTrace = "AccessibilityService.performGlobalAction($target)"
            )
        }

        // Fallback when AccessibilityService is not yet toggled on in Android Settings
        if (target.contains("home")) {
            runCatching {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
                return ExecutionResult(
                    executed = true,
                    actionSummary = "Navigated to Home Screen via Intent",
                    technicalDetail = "AccessibilityService not yet enabled; navigated Home via CATEGORY_HOME Intent.",
                    shellCommandTrace = "am start -a android.intent.action.MAIN -c android.intent.category.HOME"
                )
            }
        }

        return ExecutionResult(
            executed = false,
            actionSummary = "Enable JARVIS Accessibility Service for '$target'",
            technicalDetail = "Open the Settings tab and tap 'Enable Accessibility Service' so JARVIS can perform global Back/Recents/Notifications/Screenshot actions.",
            shellCommandTrace = "AccessibilityService.performGlobalAction($target)"
        )
    }

    private fun executeFlashlight(targetParam: String?): ExecutionResult {
        val mode = targetParam?.lowercase(Locale.US)?.trim() ?: "toggle"
        val (success, detail) = JarvisAccessibilityService.setFlashlight(context, mode)
        return ExecutionResult(
            executed = success,
            actionSummary = if (success) "Flashlight: ${mode.uppercase(Locale.US)}" else "Flashlight Hardware Notice",
            technicalDetail = detail,
            shellCommandTrace = "CameraManager.setTorchMode(cameraId, $mode)"
        )
    }

    private fun executeTap(targetParam: String?): ExecutionResult {
        val raw = targetParam?.trim() ?: "540,960"
        val parts = raw.split(",").mapNotNull { it.trim().toFloatOrNull() }
        val x = parts.getOrNull(0) ?: 540f
        val y = parts.getOrNull(1) ?: 960f

        val service = JarvisAccessibilityService.instance
        if (service != null) {
            val ok = service.performTap(x, y)
            return ExecutionResult(
                executed = ok,
                actionSummary = if (ok) "Dispatched Screen Tap at (${x.toInt()}, ${y.toInt()})" else "Tap Gesture Rejected",
                technicalDetail = "Executed AccessibilityService.dispatchGesture(StrokeDescription(Path.moveTo($x, $y), 80ms)).",
                shellCommandTrace = "input tap ${x.toInt()} ${y.toInt()}"
            )
        }

        return ExecutionResult(
            executed = false,
            actionSummary = "Accessibility Service Required for Tap (${x.toInt()}, ${y.toInt()})",
            technicalDetail = "Enable JARVIS in Android Accessibility Settings to dispatch touch gestures across the screen.",
            shellCommandTrace = "input tap ${x.toInt()} ${y.toInt()}"
        )
    }

    private fun executeSwipe(targetParam: String?): ExecutionResult {
        val raw = targetParam?.trim() ?: "up"
        val service = JarvisAccessibilityService.instance
        if (service != null) {
            val coordParts = raw.split(",").mapNotNull { it.trim().toFloatOrNull() }
            val ok = if (coordParts.size == 4) {
                service.performSwipe(coordParts[0], coordParts[1], coordParts[2], coordParts[3])
            } else {
                service.performDirectionalSwipe(raw)
            }
            return ExecutionResult(
                executed = ok,
                actionSummary = if (ok) "Dispatched Screen Swipe: ${raw.uppercase(Locale.US)}" else "Swipe Gesture Rejected",
                technicalDetail = "Executed AccessibilityService.dispatchGesture(Path) for '$raw'.",
                shellCommandTrace = "AccessibilityService.dispatchGesture(swipe=$raw)"
            )
        }

        return ExecutionResult(
            executed = false,
            actionSummary = "Accessibility Service Required for Swipe ($raw)",
            technicalDetail = "Enable JARVIS in Android Accessibility Settings to dispatch swipe gestures.",
            shellCommandTrace = "AccessibilityService.dispatchGesture(swipe=$raw)"
        )
    }

    private fun executeOpenApp(targetParam: String?): ExecutionResult {
        val pkgOrName = targetParam?.takeIf { it.isNotBlank() } ?: "com.android.settings"
        val shellTrace = "monkey -p $pkgOrName -c android.intent.category.LAUNCHER 1"

        if (JarvisAccessibilityService.openAppByName(context, pkgOrName)) {
            return ExecutionResult(
                executed = true,
                actionSummary = "Launched App: $pkgOrName",
                technicalDetail = "Resolved and started via JarvisAccessibilityService.openAppByName(PackageManager).",
                shellCommandTrace = shellTrace
            )
        }

        // System action/URI fallbacks for standard apps that may not be installed on an AOSP emulator
        val fallbackIntent: Intent? = when {
            pkgOrName.contains("settings", ignoreCase = true) ->
                Intent(Settings.ACTION_SETTINGS)
            pkgOrName.contains("youtube", ignoreCase = true) ->
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
            pkgOrName.contains("chrome", ignoreCase = true) || pkgOrName.contains("browser", ignoreCase = true) ->
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            pkgOrName.contains("maps", ignoreCase = true) ->
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=Stark+Tower"))
            pkgOrName.contains("clock", ignoreCase = true) || pkgOrName.contains("alarm", ignoreCase = true) ->
                Intent(Settings.ACTION_DATE_SETTINGS)
            else -> null
        }

        if (fallbackIntent != null) {
            val resolved = runCatching {
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallbackIntent)
                true
            }.getOrDefault(false)
            if (resolved) {
                return ExecutionResult(
                    executed = true,
                    actionSummary = "Opened $pkgOrName via Android System Intent",
                    technicalDetail = "Target '$pkgOrName' routed via system Intent (${fallbackIntent.action}).",
                    shellCommandTrace = shellTrace
                )
            }
        }

        return ExecutionResult(
            executed = false,
            actionSummary = "App Not Installed: $pkgOrName",
            technicalDetail = "Verified via PackageManager; '$pkgOrName' is not installed on this device.",
            shellCommandTrace = shellTrace
        )
    }

    private fun executeMediaControl(actionParam: String?): ExecutionResult {
        val action = actionParam?.lowercase(Locale.US)?.trim() ?: "play"
        val (keyCode, shellCode, stateLabel) = when (action) {
            "play" -> Triple(KeyEvent.KEYCODE_MEDIA_PLAY, 126, "PLAYING")
            "pause", "stop" -> Triple(KeyEvent.KEYCODE_MEDIA_PAUSE, 127, "PAUSED")
            "next", "skip" -> Triple(KeyEvent.KEYCODE_MEDIA_NEXT, 87, "NEXT TRACK")
            "previous", "prev", "back" -> Triple(KeyEvent.KEYCODE_MEDIA_PREVIOUS, 88, "PREVIOUS TRACK")
            else -> Triple(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 85, "PLAY/PAUSE")
        }

        val shellTrace = "input keyevent $shellCode"
        return runCatching {
            val eventTime = System.currentTimeMillis()
            val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
            val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
            lastMediaState = stateLabel

            ExecutionResult(
                executed = true,
                actionSummary = "Media Transport: ${action.uppercase(Locale.US)} (KeyCode $shellCode)",
                technicalDetail = "Dispatched KeyEvent.ACTION_DOWN & ACTION_UP via AudioManager.dispatchMediaKeyEvent($keyCode).",
                shellCommandTrace = shellTrace
            )
        }.getOrElse { e ->
            ExecutionResult(
                executed = false,
                actionSummary = "Media Control Failed: $action",
                technicalDetail = e.localizedMessage ?: "AudioManager error",
                shellCommandTrace = shellTrace
            )
        }
    }

    private fun executeAdjustVolume(directionParam: String?): ExecutionResult {
        val direction = directionParam?.lowercase(Locale.US)?.trim() ?: "up"
        val shellTrace = when {
            direction.contains("down") || direction.contains("lower") -> "cmd audio volume lower"
            direction.contains("mute") -> "input keyevent 164"
            else -> "cmd audio volume raise"
        }

        return runCatching {
            val summary = JarvisAccessibilityService.adjustVolume(context, direction)
            ExecutionResult(
                executed = true,
                actionSummary = "$summary (${direction.uppercase(Locale.US)})",
                technicalDetail = "Executed AudioManager.adjustStreamVolume with FLAG_SHOW_UI.",
                shellCommandTrace = shellTrace
            )
        }.getOrElse { e ->
            ExecutionResult(
                executed = false,
                actionSummary = "Volume Adjustment Failed ($direction)",
                technicalDetail = e.localizedMessage ?: "AudioManager error",
                shellCommandTrace = shellTrace
            )
        }
    }

    private fun executeDeviceStatus(param: String?): ExecutionResult {
        val snapshot = readTelemetry()
        val chargeLabel = if (snapshot.isCharging) "CHARGING (AC/USB)" else "DISCHARGING"
        return ExecutionResult(
            executed = true,
            actionSummary = "Battery: ${snapshot.batteryPercent}% ($chargeLabel) • Local Time: ${snapshot.currentTimeFormatted}",
            technicalDetail = "Temp: ${snapshot.batteryTempCelsius}°C | Voltage: ${snapshot.batteryVoltageMv}mV | Audio: ${snapshot.volumeCurrent}/${snapshot.volumeMax} | Date: ${snapshot.currentDateFormatted}",
            shellCommandTrace = "dumpsys battery && date"
        )
    }

    private fun executeTypeText(
        textParam: String?,
        onTypeText: (String) -> Unit
    ): ExecutionResult {
        val textToType = textParam?.takeIf { it.isNotBlank() } ?: ""
        val safeShellText = textToType.replace(" ", "%s")
        val shellTrace = "input text \"$safeShellText\""

        if (textToType.isEmpty()) {
            return ExecutionResult(
                executed = false,
                actionSummary = "No Text Provided to Type",
                technicalDetail = "Target was null or empty for type_text action.",
                shellCommandTrace = shellTrace
            )
        }

        return runCatching {
            onTypeText(textToType)
            val clip = ClipData.newPlainText("JARVIS Typed Text", textToType)
            clipboardManager.setPrimaryClip(clip)

            ExecutionResult(
                executed = true,
                actionSummary = "Typed ${textToType.length} chars into Active Buffer & Clipboard",
                technicalDetail = "Injected \"$textToType\" into HUD Active Text Field and system ClipboardManager.",
                shellCommandTrace = shellTrace
            )
        }.getOrElse { e ->
            ExecutionResult(
                executed = false,
                actionSummary = "Text Injection Error",
                technicalDetail = e.localizedMessage ?: "Failed to inject text",
                shellCommandTrace = shellTrace
            )
        }
    }
}
