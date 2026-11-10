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
        val param = response.parameter?.trim()
        return when (response.intentType) {
            JarvisIntentType.OPEN_APP -> executeOpenApp(param)
            JarvisIntentType.MEDIA_CONTROL -> executeMediaControl(param)
            JarvisIntentType.ADJUST_VOLUME -> executeAdjustVolume(param)
            JarvisIntentType.DEVICE_STATUS -> executeDeviceStatus(param)
            JarvisIntentType.TYPE_TEXT -> executeTypeText(param, onTypeText)
            JarvisIntentType.GENERAL_RESPONSE -> ExecutionResult(
                executed = true,
                actionSummary = "Voice Synthesis & Dialogue Response",
                technicalDetail = "Spoken via Android TextToSpeech engine (no physical hardware mutation required).",
                shellCommandTrace = "# general_response -> TTS output only"
            )
        }
    }

    private fun executeOpenApp(targetParam: String?): ExecutionResult {
        val pkgOrName = targetParam?.takeIf { it.isNotBlank() } ?: "com.android.settings"
        val shellTrace = "monkey -p $pkgOrName -c android.intent.category.LAUNCHER 1"
        val pm = context.packageManager

        // 1. Direct launch intent by package name
        val directIntent = pm.getLaunchIntentForPackage(pkgOrName)
        if (directIntent != null) {
            return runCatching {
                directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(directIntent)
                ExecutionResult(
                    executed = true,
                    actionSummary = "Launched Package: $pkgOrName",
                    technicalDetail = "Resolved via PackageManager.getLaunchIntentForPackage(\"$pkgOrName\")",
                    shellCommandTrace = shellTrace
                )
            }.getOrElse { e ->
                ExecutionResult(
                    executed = false,
                    actionSummary = "Launch Error: $pkgOrName",
                    technicalDetail = e.localizedMessage ?: "Unable to start activity.",
                    shellCommandTrace = shellTrace
                )
            }
        }

        // 2. Fuzzy match against installed launchable apps by package suffix or label
        val installed = queryInstalledApps()
        val matchedApp = installed.firstOrNull { app ->
            app.packageName.equals(pkgOrName, ignoreCase = true) ||
                app.appName.equals(pkgOrName, ignoreCase = true) ||
                pkgOrName.contains(app.appName, ignoreCase = true) ||
                app.packageName.substringAfterLast('.').equals(
                    pkgOrName.substringAfterLast('.'),
                    ignoreCase = true
                )
        }

        if (matchedApp != null) {
            val matchedIntent = pm.getLaunchIntentForPackage(matchedApp.packageName)
            if (matchedIntent != null) {
                return runCatching {
                    matchedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(matchedIntent)
                    ExecutionResult(
                        executed = true,
                        actionSummary = "Launched ${matchedApp.appName} (${matchedApp.packageName})",
                        technicalDetail = "Matched installed tablet package ${matchedApp.packageName}",
                        shellCommandTrace = "monkey -p ${matchedApp.packageName} -c android.intent.category.LAUNCHER 1"
                    )
                }.getOrElse { e ->
                    ExecutionResult(
                        executed = false,
                        actionSummary = "Failed launching ${matchedApp.appName}",
                        technicalDetail = e.localizedMessage ?: "Activity launch error",
                        shellCommandTrace = shellTrace
                    )
                }
            }
        }

        // 3. System action/URI fallbacks for standard tablet apps that may have different AOSP package names
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
                    technicalDetail = "Package '$pkgOrName' not installed natively; routed via system Intent (${fallbackIntent.action}).",
                    shellCommandTrace = shellTrace
                )
            }
        }

        return ExecutionResult(
            executed = false,
            actionSummary = "Package Not Installed: $pkgOrName",
            technicalDetail = "Verified via PackageManager; '$pkgOrName' is not installed on this tablet image. Try an installed app from the Capabilities tab.",
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
                technicalDetail = "Dispatched KeyEvent.ACTION_DOWN & ACTION_UP via AudioManager.dispatchMediaKeyEvent($keyCode). Active state: $stateLabel",
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
        val shellTrace = when (direction) {
            "up", "raise", "higher", "louder" -> "cmd audio volume raise"
            "down", "lower", "quieter", "softer" -> "cmd audio volume lower"
            "mute", "silence", "unmute" -> "input keyevent 164"
            else -> "cmd audio volume raise"
        }

        return runCatching {
            val beforeVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

            when (direction) {
                "up", "raise", "higher", "louder" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
                    ) {
                        audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_UNMUTE,
                            0
                        )
                    }
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
                "down", "lower", "quieter", "softer" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
                "mute", "silence" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_MUTE,
                            AudioManager.FLAG_SHOW_UI
                        )
                    } else {
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            0,
                            AudioManager.FLAG_SHOW_UI
                        )
                    }
                }
                else -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
            }

            val afterVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            ExecutionResult(
                executed = true,
                actionSummary = "STREAM_MUSIC Volume: $beforeVol → $afterVol / $maxVol (${direction.uppercase(Locale.US)})",
                technicalDetail = "Executed AudioManager.adjustStreamVolume(STREAM_MUSIC, FLAG_SHOW_UI).",
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
                technicalDetail = "Parameter was null or empty for type_text intent.",
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
