package com.example.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.DeepNavySurface
import com.example.ui.theme.ElevatedHudSurface
import com.example.ui.theme.HudGlassBorder
import com.example.ui.theme.IceWhiteText
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SteelCyanText
import com.example.ui.theme.TelemetryEmerald

@Composable
fun SettingsScreen(
    isAccessibilityConnected: Boolean,
    savedApiKey: String,
    voicePitch: Float,
    voiceRate: Float,
    onSaveApiKey: (String) -> Unit,
    onUpdateVoiceTuning: (Float, Float) -> Unit,
    onTestVoice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var apiKeyDraft by remember(savedApiKey) { mutableStateOf(savedApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var keySavedConfirmation by remember { mutableStateOf(false) }

    var pitchDraft by remember(voicePitch) { mutableFloatStateOf(voicePitch) }
    var rateDraft by remember(voiceRate) { mutableFloatStateOf(voiceRate) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Accessibility Service Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DeepNavySurface),
                border = BorderStroke(
                    1.dp,
                    if (isAccessibilityConnected) TelemetryEmerald else AmberWarning
                ),
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
                        Icon(
                            imageVector = Icons.Default.AccessibilityNew,
                            contentDescription = "Accessibility Service",
                            tint = if (isAccessibilityConnected) TelemetryEmerald else AmberWarning,
                            modifier = Modifier.size(26.dp)
                        )
                        Column {
                            Text(
                                text = "JARVIS ACCESSIBILITY SERVICE",
                                style = MaterialTheme.typography.titleMedium,
                                color = IceWhiteText,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isAccessibilityConnected) {
                                    "STATUS: CONNECTED & ACTIVE"
                                } else {
                                    "STATUS: NOT ENABLED IN ANDROID SETTINGS"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isAccessibilityConnected) TelemetryEmerald else AmberWarning
                            )
                        }
                    }

                    Text(
                        text = "Enabling JarvisAccessibilityService allows JARVIS to execute system-wide navigation (Go Home, Go Back, Open Recents, Expand Notifications, Take Screenshot) and screen touch gestures (Tap, Swipe) without root or ADB.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SteelCyanText
                    )

                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArcCyan,
                            contentColor = ObsidianBg
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_open_accessibility_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessibilityNew,
                            contentDescription = "Open Accessibility Settings",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OPEN ANDROID ACCESSIBILITY SETTINGS",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. Gemini API Key & Backend Configuration Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DeepNavySurface),
                border = BorderStroke(1.dp, HudGlassBorder),
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
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Gemini API Key Configuration",
                            tint = ArcCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "GEMINI AI ENGINE & SHAREDPREFERENCES VAULT",
                                style = MaterialTheme.typography.titleMedium,
                                color = ArcCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Active Model: gemini-3.8-flash (Firebase AI Logic + App Check)",
                                style = MaterialTheme.typography.labelMedium,
                                color = TelemetryEmerald
                            )
                        }
                    }

                    Text(
                        text = "Out of the box, this build uses zero-trust Firebase AI Logic (gemini-3.8-flash). For standalone GitHub APK builds, you can also store your personal Gemini API Key in SharedPreferences below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SteelCyanText
                    )

                    OutlinedTextField(
                        value = apiKeyDraft,
                        onValueChange = {
                            apiKeyDraft = it
                            keySavedConfirmation = false
                        },
                        label = { Text("Gemini API Key (Saved in SharedPreferences)") },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        visualTransformation = if (showApiKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = if (showApiKey) "Hide API Key" else "Show API Key",
                                    tint = SteelCyanText
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = HudGlassBorder,
                            focusedTextColor = IceWhiteText,
                            unfocusedTextColor = IceWhiteText,
                            focusedContainerColor = ObsidianBg,
                            unfocusedContainerColor = ObsidianBg
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_api_key_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                onSaveApiKey(apiKeyDraft)
                                keySavedConfirmation = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArcCyan,
                                contentColor = ObsidianBg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("settings_save_api_key_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save API Key",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SAVE KEY TO PREFS", fontWeight = FontWeight.Bold)
                        }

                        if (keySavedConfirmation) {
                            Surface(
                                color = TelemetryEmerald.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, TelemetryEmerald),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Saved",
                                        tint = TelemetryEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "SAVED",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TelemetryEmerald
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Text-to-Speech (TTS) Voice Synthesis Tuning
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DeepNavySurface),
                border = BorderStroke(1.dp, HudGlassBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "TEXT-TO-SPEECH (TTS) VOICE MODULATION",
                        style = MaterialTheme.typography.titleMedium,
                        color = ArcCyan,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Voice Pitch: ${"%.2f".format(pitchDraft)} (Default: 0.92 calm assistant tone)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IceWhiteText
                    )
                    Slider(
                        value = pitchDraft,
                        onValueChange = {
                            pitchDraft = it
                            onUpdateVoiceTuning(pitchDraft, rateDraft)
                        },
                        valueRange = 0.5f..1.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = ArcCyan,
                            activeTrackColor = ArcCyan,
                            inactiveTrackColor = ElevatedHudSurface
                        )
                    )

                    Text(
                        text = "Speech Rate: ${"%.2f".format(rateDraft)} (Default: 1.04 crisp articulation)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IceWhiteText
                    )
                    Slider(
                        value = rateDraft,
                        onValueChange = {
                            rateDraft = it
                            onUpdateVoiceTuning(pitchDraft, rateDraft)
                        },
                        valueRange = 0.6f..1.6f,
                        colors = SliderDefaults.colors(
                            thumbColor = ArcCyan,
                            activeTrackColor = ArcCyan,
                            inactiveTrackColor = ElevatedHudSurface
                        )
                    )

                    OutlinedButton(
                        onClick = {
                            onTestVoice("All systems operational, Sir. Voice synthesis parameters calibrated.")
                        },
                        border = BorderStroke(1.dp, ArcCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("settings_test_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Test Voice",
                            tint = ArcCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TEST JARVIS VOICE SYNTHESIS", color = IceWhiteText)
                    }
                }
            }
        }
    }
}
