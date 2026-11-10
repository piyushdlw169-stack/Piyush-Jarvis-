package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "JarvisAccessService"

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
        Log.d(TAG, "JarvisAccessibilityService connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Optional window state observation; JARVIS primarily acts on voice/text directives
    }

    override fun onInterrupt() {
        Log.w(TAG, "JarvisAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        _isConnected.value = false
    }

    /**
     * Performs global Android navigation and system actions without root.
     */
    fun executeGlobalActionByName(target: String): Boolean {
        val normalized = target.trim().lowercase(Locale.US)
        val actionId = when {
            normalized.contains("home") -> GLOBAL_ACTION_HOME
            normalized.contains("back") -> GLOBAL_ACTION_BACK
            normalized.contains("recent") || normalized.contains("overview") -> GLOBAL_ACTION_RECENTS
            normalized.contains("notification") -> GLOBAL_ACTION_NOTIFICATIONS
            normalized.contains("quick_settings") -> GLOBAL_ACTION_QUICK_SETTINGS
            normalized.contains("screenshot") -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    GLOBAL_ACTION_TAKE_SCREENSHOT
                } else {
                    return false
                }
            }
            else -> return false
        }
        return performGlobalAction(actionId)
    }

    /**
     * Dispatches a single tap gesture at (x, y) screen coordinates using AccessibilityService.
     */
    fun performTap(x: Float, y: Float, durationMs: Long = 80L): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return runCatching {
            val path = Path().apply {
                moveTo(x.coerceAtLeast(1f), y.coerceAtLeast(1f))
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()
            dispatchGesture(gesture, null, null)
        }.getOrElse { e ->
            Log.e(TAG, "performTap failed", e)
            false
        }
    }

    /**
     * Dispatches a directional or coordinate-based swipe gesture on screen.
     */
    fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 320L
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return runCatching {
            val path = Path().apply {
                moveTo(startX.coerceAtLeast(1f), startY.coerceAtLeast(1f))
                lineTo(endX.coerceAtLeast(1f), endY.coerceAtLeast(1f))
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()
            dispatchGesture(gesture, null, null)
        }.getOrElse { e ->
            Log.e(TAG, "performSwipe failed", e)
            false
        }
    }

    /**
     * Helper to swipe by direction keyword ("up", "down", "left", "right") relative to screen size.
     */
    fun performDirectionalSwipe(direction: String): Boolean {
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels.toFloat().coerceAtLeast(800f)
        val h = metrics.heightPixels.toFloat().coerceAtLeast(1200f)
        val cx = w / 2f
        val cy = h / 2f

        return when (direction.trim().lowercase(Locale.US)) {
            "up", "scroll_down" -> performSwipe(cx, h * 0.75f, cx, h * 0.25f)
            "down", "scroll_up" -> performSwipe(cx, h * 0.25f, cx, h * 0.75f)
            "left", "next_page" -> performSwipe(w * 0.80f, cy, w * 0.20f, cy)
            "right", "prev_page" -> performSwipe(w * 0.20f, cy, w * 0.80f, cy)
            else -> performSwipe(cx, h * 0.75f, cx, h * 0.25f)
        }
    }

    companion object {
        @Volatile
        var instance: JarvisAccessibilityService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _isFlashlightOn = MutableStateFlow(false)
        val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

        /**
         * Opens any installed app by human-readable label or package name.
         */
        fun openAppByName(context: Context, appNameOrPackage: String): Boolean {
            val pm = context.packageManager
            val query = appNameOrPackage.trim()
            if (query.isEmpty()) return false

            // 1. Direct package match
            pm.getLaunchIntentForPackage(query)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            }

            // 2. Search all launchable apps
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }

            val matched = apps.firstOrNull { info ->
                val label = info.loadLabel(pm)?.toString().orEmpty()
                val pkg = info.activityInfo?.packageName.orEmpty()
                label.equals(query, ignoreCase = true) ||
                    label.contains(query, ignoreCase = true) ||
                    query.contains(label, ignoreCase = true) ||
                    pkg.equals(query, ignoreCase = true) ||
                    pkg.substringAfterLast('.').equals(query, ignoreCase = true)
            }

            val targetPkg = matched?.activityInfo?.packageName ?: return false
            val launchIntent = pm.getLaunchIntentForPackage(targetPkg) ?: return false
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return true
        }

        /**
         * Adjusts Media or Ring volume using AudioManager.
         */
        fun adjustVolume(context: Context, directionOrTarget: String): String {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val normalized = directionOrTarget.trim().lowercase(Locale.US)
            val stream = if (normalized.contains("ring")) {
                AudioManager.STREAM_RING
            } else {
                AudioManager.STREAM_MUSIC
            }

            when {
                normalized.contains("up") || normalized.contains("raise") || normalized.contains("high") -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager.isStreamMute(stream)) {
                        audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
                    }
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                }
                normalized.contains("down") || normalized.contains("lower") || normalized.contains("quiet") -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                }
                normalized.contains("mute") || normalized.contains("silent") -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    } else {
                        audioManager.setStreamVolume(stream, 0, AudioManager.FLAG_SHOW_UI)
                    }
                }
                normalized.contains("unmute") -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                    }
                }
                else -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                }
            }

            val current = audioManager.getStreamVolume(stream)
            val max = audioManager.getStreamMaxVolume(stream)
            val streamName = if (stream == AudioManager.STREAM_RING) "RING" else "MEDIA"
            return "$streamName Volume: $current / $max"
        }

        /**
         * Toggles or sets hardware camera flashlight using CameraManager.
         */
        fun setFlashlight(context: Context, mode: String): Pair<Boolean, String> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return false to "Flashlight API requires Android 6.0+ (API 23)."
            }
            return runCatching {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val torchCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return false to "No hardware camera flash unit detected on this tablet/emulator."

                val normalized = mode.trim().lowercase(Locale.US)
                val targetState = when {
                    normalized.contains("on") || normalized.contains("enable") -> true
                    normalized.contains("off") || normalized.contains("disable") -> false
                    else -> !_isFlashlightOn.value
                }

                cameraManager.setTorchMode(torchCameraId, targetState)
                _isFlashlightOn.value = targetState
                true to "Flashlight ${if (targetState) "ENABLED" else "DISABLED"} on camera $torchCameraId"
            }.getOrElse { e ->
                Log.e(TAG, "setFlashlight error", e)
                false to (e.localizedMessage ?: "Unable to toggle flashlight hardware.")
            }
        }
    }
}
