package com.example.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DeviceTelemetrySnapshot
import com.example.model.InstalledAppInfo
import com.example.model.JarvisIntentType
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.DeepNavySurface
import com.example.ui.theme.ElectricCobalt
import com.example.ui.theme.ElevatedHudSurface
import com.example.ui.theme.HudGlassBorder
import com.example.ui.theme.IceWhiteText
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SteelCyanText
import com.example.ui.theme.TelemetryEmerald

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CapabilitiesDeckScreen(
    telemetry: DeviceTelemetrySnapshot,
    installedApps: List<InstalledAppInfo>,
    activeTextBuffer: String,
    onUpdateTextBuffer: (String) -> Unit,
    onTriggerCapability: (
        intentType: JarvisIntentType,
        parameter: String?,
        simulatedUserCommand: String,
        speakMessage: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var customTextToInject by remember {
        mutableStateOf("All Stark tablet diagnostics nominal at 100% efficiency.")
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("capabilities_deck_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Accessibility Global Actions (Home, Back, Recents, Notifications, Screenshot)
        item {
            CapabilitySectionCard(
                number = "01",
                signature = "global_action(target) // JarvisAccessibilityService",
                subtitle = "Targets: \"home\", \"back\", \"recents\", \"notifications\", \"screenshot\" • Service Status: ${if (telemetry.isAccessibilityConnected) "CONNECTED" else "NOT ENABLED"}",
                accentColor = ArcCyan
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!telemetry.isAccessibilityConnected) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            },
                            border = BorderStroke(1.dp, AmberWarning),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("open_accessibility_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessibilityNew,
                                contentDescription = "Enable Accessibility Service",
                                tint = AmberWarning,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ENABLE JARVIS IN ACCESSIBILITY SETTINGS",
                                color = AmberWarning,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HudActionButton(
                            label = "GO HOME",
                            icon = Icons.Default.Home,
                            accent = ArcCyan,
                            testTag = "cap_global_home",
                            onClick = {
                                onTriggerCapability(
                                    JarvisIntentType.GLOBAL_ACTION,
                                    "home",
                                    "JARVIS, go to the home screen",
                                    "Returning to the home screen, Sir."
                                )
                            }
                        )
                        HudActionButton(
                            label = "GO BACK",
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            accent = ArcCyan,
                            testTag = "cap_global_back",
                            onClick = {
                                onTriggerCapability(
                                    JarvisIntentType.GLOBAL_ACTION,
                                    "back",
                                    "JARVIS, go back",
                                    "Navigating back, Sir."
                                )
                            }
                        )
                        HudActionButton(
                            label = "RECENTS",
                            icon = Icons.Default.ViewCarousel,
                            accent = ArcCyan,
                            testTag = "cap_global_recents",
                            onClick = {
                                onTriggerCapability(
                                    JarvisIntentType.GLOBAL_ACTION,
                                    "recents",
                                    "Open recent apps",
                                    "Opening recent applications, Sir."
                                )
                            }
                        )
                        HudActionButton(
                            label = "NOTIFICATIONS",
                            icon = Icons.Default.Notifications,
                            accent = ArcCyan,
                            testTag = "cap_global_notifications",
                            onClick = {
                                onTriggerCapability(
                                    JarvisIntentType.GLOBAL_ACTION,
                                    "notifications",
                                    "Open my notifications",
                                    "Expanding the notification shade, Sir."
                                )
                            }
                        )
                        HudActionButton(
                            label = "TAKE SCREENSHOT",
                            icon = Icons.Default.CameraAlt,
                            accent = TelemetryEmerald,
                            testTag = "cap_global_screenshot",
                            onClick = {
                                onTriggerCapability(
                                    JarvisIntentType.GLOBAL_ACTION,
                                    "screenshot",
                                    "JARVIS, take a screenshot",
                                    "Capturing your screen now, Sir."
                                )
                            }
                        )
                    }
                }
            }
        }

        // 2. Screen Gestures: dispatchGesture (Tap & Swipe) + Flashlight
        item {
            CapabilitySectionCard(
                number = "02",
                signature = "tap(x,y) & swipe(direction) & flashlight(mode)",
                subtitle = "Dispatches GestureDescription paths via AccessibilityService & toggles CameraManager torch",
                accentColor = Color(0xFFFF80AB)
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HudActionButton(
                        label = "TAP (540, 960)",
                        icon = Icons.Default.TouchApp,
                        accent = Color(0xFFFF80AB),
                        testTag = "cap_gesture_tap",
                        onClick = {
                            onTriggerCapability(
                                JarvisIntentType.TAP,
                                "540,960",
                                "Tap the center of the screen",
                                "Tapping screen coordinates 540 by 960, Sir."
                            )
                        }
                    )
                    HudActionButton(
                        label = "SWIPE UP",
                        icon = Icons.Default.Swipe,
                        accent = Color(0xFFFF80AB),
                        testTag = "cap_gesture_swipe_up",
                        onClick = {
                            onTriggerCapability(
                                JarvisIntentType.SWIPE,
                                "up",
                                "Swipe up on the screen",
                                "Scrolling up, Sir."
                            )
                        }
                    )
                    HudActionButton(
                        label = "SWIPE DOWN",
                        icon = Icons.Default.Swipe,
                        accent = Color(0xFFFF80AB),
                        testTag = "cap_gesture_swipe_down",
                        onClick = {
                            onTriggerCapability(
                                JarvisIntentType.SWIPE,
                                "down",
                                "Swipe down on the screen",
                                "Scrolling down, Sir."
                            )
                        }
                    )
                    HudActionButton(
                        label = "FLASHLIGHT ON",
                        icon = Icons.Default.FlashlightOn,
                        accent = AmberWarning,
                        testTag = "cap_flashlight_on",
                        onClick = {
                            onTriggerCapability(
                                JarvisIntentType.FLASHLIGHT,
                                "on",
                                "Turn on the flashlight",
                                "Flashlight activated, Sir."
                            )
                        }
                    )
                    HudActionButton(
                        label = "FLASHLIGHT OFF",
                        icon = Icons.Default.FlashlightOff,
                        accent = AmberWarning,
                        testTag = "cap_flashlight_off",
                        onClick = {
                            onTriggerCapability(
                                JarvisIntentType.FLASHLIGHT,
                                "off",
                                "Turn off the flashlight",
                                "Flashlight deactivated, Sir."
                            )
                        }
                    )
                }
            }
        }

        // 3. media_control(action)
        item {
            CapabilitySectionCard(
                number = "03",
                signature = "media_control(action)",
                subtitle = "Actions: \"play\", \"pause\", \"next\", \"previous\" • Dispatches Android KeyEvent 126/127/87/88",
                accentColor = ArcCyan
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HudActionButton(
                        label = "PLAY",
                        icon = Icons.Default.PlayArrow,
                        accent = ArcCyan,
                        testTag = "cap_media_play",
                        onClick = {
                            onTriggerCapability(
                                JarvisIntentType.MEDIA_CONTROL,
                                "play",
                                "JARVIS, play some music",
                                "Playing your media, Sir."
                            )
                        }
                    )
                    HudActionButton(
                        label = "PAUSE",
                        icon = Icons.Default.Pause,
                        accent = ArcCyan,
                        testTag = "cap_media_pause",
                        onClick = {
                            onTriggerCapability(
                                JarvisIntentType.MEDIA_CONTROL,
                                "pause",
                                "JARVIS, pause playback",
                                "Pausing media playback, Sir."
                            )
                        }
                    )
                    HudActionButton(
                        label = "NEXT",
                        icon = Icons.Default.SkipNext,
                        accent = ArcCyan,
                        testTag = "cap_media_next",
                        onClick = {
                            onTriggerCapability(
                                JarvisIntentType.MEDIA_CONTROL,
                                "next",
                                "Skip to the next track",
                                "Skipping to the next track, Sir."
                            )
                        }
                    )
                    HudActionButton(
                        label = "PREVIOUS",
                        icon = Icons.Default.SkipPrevious,
                        accent = ArcCyan,
                        testTag = "cap_media_previous",
                        onClick = {
                            onTriggerCapability(
                                JarvisIntentType.MEDIA_CONTROL,
                                "previous",
                                "Go back to the previous track",
                                "Returning to the previous track, Sir."
                            )
                        }
                    )
                }
            }
        }

        // 4. adjust_volume(direction)
        item {
            CapabilitySectionCard(
                number = "04",
                signature = "adjust_volume(direction)",
                subtitle = "Directions: \"up\", \"down\", \"mute\", \"ring_up\", \"ring_down\" • Current Media Volume: ${telemetry.volumeCurrent}/${telemetry.volumeMax} (${telemetry.volumePercent}%)",
                accentColor = TelemetryEmerald
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HudActionButton(
                        label = "MEDIA VOL UP",
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        accent = TelemetryEmerald,
                        testTag = "cap_vol_up",
                        onClick = {
                            onTriggerCapability(
                                JarvisIntentType.ADJUST_VOLUME,
                                "up",
                                "JARVIS, raise the volume",
                                "Increasing media volume, Sir."
                            )
                        }
                    )
                    HudActionButton(
                        label = "MEDIA VOL DOWN",
                        icon = Icons.AutoMirrored.Filled.VolumeDown,
                        accent = TelemetryEmerald,
                        testTag = "cap_vol_down",
                        onClick = {
                            onTriggerCapability(
                                JarvisIntentType.ADJUST_VOLUME,
                                "down",
                                "JARVIS, lower the volume",
                                "Lowering media volume, Sir."
                            )
                        }
                    )
                    HudActionButton(
                        label = "MUTE AUDIO",
                        icon = Icons.AutoMirrored.Filled.VolumeOff,
                        accent = AmberWarning,
                        testTag = "cap_vol_mute",
                        onClick = {
                            onTriggerCapability(
                                JarvisIntentType.ADJUST_VOLUME,
                                "mute",
                                "Mute the tablet audio",
                                "Audio stream muted, Sir."
                            )
                        }
                    )
                    HudActionButton(
                        label = "RING VOL UP",
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        accent = ArcCyan,
                        testTag = "cap_ring_up",
                        onClick = {
                            onTriggerCapability(
                                JarvisIntentType.ADJUST_VOLUME,
                                "ring_up",
                                "Raise the ringtone volume",
                                "Increasing ringtone volume, Sir."
                            )
                        }
                    )
                }
            }
        }

        // 5. device_status() & type_text(text)
        item {
            CapabilitySectionCard(
                number = "05",
                signature = "device_status() & type_text(text)",
                subtitle = "Queries BatteryManager diagnostics or injects text into active field & ClipboardManager",
                accentColor = Color(0xFFE040FB)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HudActionButton(
                        label = "RUN BATTERY & CLOCK DIAGNOSTIC",
                        icon = Icons.Default.BatteryChargingFull,
                        accent = AmberWarning,
                        testTag = "cap_device_status",
                        onClick = {
                            onTriggerCapability(
                                JarvisIntentType.DEVICE_STATUS,
                                null,
                                "JARVIS, check device status and battery",
                                "Power reserves are at ${telemetry.batteryPercent} percent and local time is ${telemetry.currentTimeFormatted}, Sir."
                            )
                        }
                    )

                    OutlinedTextField(
                        value = customTextToInject,
                        onValueChange = { customTextToInject = it },
                        label = { Text("Text target to inject") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("type_text_param_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = HudGlassBorder,
                            focusedTextColor = IceWhiteText,
                            unfocusedTextColor = IceWhiteText,
                            focusedContainerColor = ObsidianBg,
                            unfocusedContainerColor = ObsidianBg
                        )
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HudActionButton(
                            label = "EXECUTE type_text",
                            icon = Icons.Default.EditNote,
                            accent = Color(0xFFE040FB),
                            testTag = "cap_type_text_button",
                            onClick = {
                                val textVal = customTextToInject.ifBlank { "Hello from JARVIS" }
                                onTriggerCapability(
                                    JarvisIntentType.TYPE_TEXT,
                                    textVal,
                                    "Type text: $textVal",
                                    "Typing your requested text into the active field, Sir."
                                )
                            }
                        )

                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, "JARVIS Buffer", activeTextBuffer)
                            },
                            border = BorderStroke(1.dp, HudGlassBorder),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Active Buffer",
                                tint = ArcCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("COPY BUFFER", color = IceWhiteText)
                        }
                    }

                    OutlinedTextField(
                        value = activeTextBuffer,
                        onValueChange = onUpdateTextBuffer,
                        label = { Text("ACTIVE TABLET TEXT FIELD (Target of type_text)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("active_text_buffer_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TelemetryEmerald,
                            unfocusedBorderColor = HudGlassBorder,
                            focusedTextColor = TelemetryEmerald,
                            unfocusedTextColor = TelemetryEmerald,
                            focusedContainerColor = ObsidianBg,
                            unfocusedContainerColor = ObsidianBg
                        )
                    )
                }
            }
        }

        // 6. open_app(app_package_name)
        item {
            CapabilitySectionCard(
                number = "06",
                signature = "open_app(app_name_or_package)",
                subtitle = "Launches installed Android apps via PackageManager without root",
                accentColor = ElectricCobalt
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Standard Package Targets:",
                        style = MaterialTheme.typography.labelMedium,
                        color = SteelCyanText
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val commonPackages = listOf(
                            "YouTube" to "com.google.android.youtube",
                            "Settings" to "com.android.settings",
                            "Chrome" to "com.android.chrome",
                            "Clock" to "com.google.android.deskclock",
                            "Calculator" to "com.google.android.calculator"
                        )
                        commonPackages.forEach { (name, pkg) ->
                            HudActionButton(
                                label = "$name ($pkg)",
                                icon = Icons.Default.Apps,
                                accent = ElectricCobalt,
                                testTag = "cap_open_$name",
                                onClick = {
                                    onTriggerCapability(
                                        JarvisIntentType.OPEN_APP,
                                        pkg,
                                        "Open $name",
                                        "Opening $name right away, Sir."
                                    )
                                }
                            )
                        }
                    }

                    if (installedApps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Detected Launchable Apps on Device (${installedApps.size}):",
                            style = MaterialTheme.typography.labelMedium,
                            color = ArcCyan
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            installedApps.take(12).forEach { app ->
                                OutlinedButton(
                                    onClick = {
                                        onTriggerCapability(
                                            JarvisIntentType.OPEN_APP,
                                            app.packageName,
                                            "Open ${app.appName}",
                                            "Launching ${app.appName} for you, Sir."
                                        )
                                    },
                                    border = BorderStroke(1.dp, HudGlassBorder),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = ElevatedHudSurface
                                    )
                                ) {
                                    Text(
                                        text = "${app.appName} (${app.packageName.substringAfterLast('.')})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = IceWhiteText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilitySectionCard(
    number: String,
    signature: String,
    subtitle: String,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DeepNavySurface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "[$number]",
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
                Column {
                    Text(
                        text = signature,
                        style = MaterialTheme.typography.titleMedium,
                        color = IceWhiteText,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SteelCyanText
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun HudActionButton(
    label: String,
    icon: ImageVector,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent.copy(alpha = 0.16f),
            contentColor = IceWhiteText
        ),
        border = BorderStroke(1.dp, accent),
        modifier = Modifier
            .height(48.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = IceWhiteText,
            fontWeight = FontWeight.Bold
        )
    }
}
