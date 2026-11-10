package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.CommandLogEntry
import com.example.model.DeviceTelemetrySnapshot
import com.example.model.JarvisIntentType
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.ArcCyanDim
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepNavySurface
import com.example.ui.theme.ElectricCobalt
import com.example.ui.theme.ElevatedHudSurface
import com.example.ui.theme.HudGlassBorder
import com.example.ui.theme.IceWhiteText
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SteelCyanText
import com.example.ui.theme.TelemetryEmerald
import com.example.viewmodel.JarvisAiState
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelemetryHeaderBar(
    telemetry: DeviceTelemetrySnapshot,
    aiState: JarvisAiState,
    isListening: Boolean,
    isSpeaking: Boolean,
    ttsEnabled: Boolean,
    onToggleTts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusText, statusColor) = when {
        isListening -> "VOICE CAPTURE ACTIVE" to CrimsonAlert
        aiState is JarvisAiState.Processing -> "ANALYZING INTENT…" to AmberWarning
        isSpeaking -> "SPEAKING RESPONSE" to ArcCyan
        aiState is JarvisAiState.Standby -> "STANDBY MODE" to AmberWarning
        aiState is JarvisAiState.Error -> "ATTENTION REQUIRED" to CrimsonAlert
        else -> "SYSTEMS ONLINE" to TelemetryEmerald
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("telemetry_header_bar"),
        colors = CardDefaults.cardColors(containerColor = DeepNavySurface),
        border = BorderStroke(1.dp, HudGlassBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Column {
                        Text(
                            text = "J.A.R.V.I.S. // TACTICAL TABLET OS",
                            style = MaterialTheme.typography.titleMedium,
                            color = ArcCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor
                        )
                    }
                }

                IconButton(
                    onClick = onToggleTts,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ElevatedHudSurface)
                        .testTag("tts_toggle_button")
                ) {
                    Icon(
                        imageVector = if (ttsEnabled) {
                            Icons.AutoMirrored.Filled.VolumeUp
                        } else {
                            Icons.AutoMirrored.Filled.VolumeOff
                        },
                        contentDescription = if (ttsEnabled) "Mute JARVIS Voice" else "Enable JARVIS Voice",
                        tint = if (ttsEnabled) ArcCyan else SteelCyanText
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryPill(
                    icon = if (telemetry.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                    label = "PWR ${telemetry.batteryPercent}% (${telemetry.batteryTempCelsius}°C)",
                    tint = if (telemetry.batteryPercent > 20) TelemetryEmerald else CrimsonAlert
                )
                TelemetryPill(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    label = if (telemetry.isMuted) "VOL MUTED" else "VOL ${telemetry.volumeCurrent}/${telemetry.volumeMax}",
                    tint = ArcCyan
                )
                TelemetryPill(
                    icon = Icons.Default.Schedule,
                    label = telemetry.currentTimeFormatted,
                    tint = IceWhiteText
                )
                TelemetryPill(
                    icon = Icons.Default.GraphicEq,
                    label = "MEDIA: ${telemetry.mediaPlaybackState}",
                    tint = ElectricCobalt
                )
            }
        }
    }
}

@Composable
private fun TelemetryPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color
) {
    Surface(
        color = ElevatedHudSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, HudGlassBorder.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = IceWhiteText
            )
        }
    }
}

@Composable
fun ArcReactorVisualizerCard(
    isListening: Boolean,
    isSpeaking: Boolean,
    isProcessing: Boolean,
    audioRmsLevel: Float,
    partialTranscript: String,
    latestSpeakText: String,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arc_reactor")
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isProcessing) 3200 else 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_ring"
    )
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isListening || isSpeaking) 2800 else 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_ring"
    )
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isListening || isSpeaking || isProcessing) 550 else 1600,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val coreColor = when {
        isListening -> CrimsonAlert
        isProcessing -> AmberWarning
        isSpeaking -> TelemetryEmerald
        else -> ArcCyan
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("arc_reactor_card"),
        colors = CardDefaults.cardColors(containerColor = DeepNavySurface),
        border = BorderStroke(1.dp, coreColor.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_jarvis_hud_banner_1794282706012),
                contentDescription = "JARVIS Tactical HUD Background",
                contentScale = ContentScale.Crop,
                alpha = 0.22f,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp))
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ObsidianBg.copy(alpha = 0.55f),
                                DeepNavySurface.copy(alpha = 0.92f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CORE NEURAL MATRIX // GEMINI-3.8-FLASH",
                        style = MaterialTheme.typography.labelMedium,
                        color = ArcCyan
                    )
                    Text(
                        text = if (isListening) "MIC LIVE" else "TAP CORE TO SPEAK",
                        style = MaterialTheme.typography.labelMedium,
                        color = coreColor
                    )
                }

                Box(
                    modifier = Modifier
                        .size(148.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onMicClick)
                        .testTag("arc_reactor_mic_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxRadius = size.minDimension / 2f

                        // Glow halo
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    coreColor.copy(alpha = 0.38f * pulsePhase),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = maxRadius
                            ),
                            radius = maxRadius,
                            center = center
                        )

                        // Outer segmented tactical ring
                        rotate(degrees = outerRotation, pivot = center) {
                            for (i in 0 until 6) {
                                drawArc(
                                    color = coreColor.copy(alpha = 0.75f),
                                    startAngle = i * 60f + 8f,
                                    sweepAngle = 42f,
                                    useCenter = false,
                                    topLeft = Offset(center.x - maxRadius * 0.88f, center.y - maxRadius * 0.88f),
                                    size = Size(maxRadius * 1.76f, maxRadius * 1.76f),
                                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }

                        // Inner counter-rotating ring
                        rotate(degrees = innerRotation, pivot = center) {
                            for (i in 0 until 4) {
                                drawArc(
                                    color = ElectricCobalt.copy(alpha = 0.85f),
                                    startAngle = i * 90f + 12f,
                                    sweepAngle = 60f,
                                    useCenter = false,
                                    topLeft = Offset(center.x - maxRadius * 0.66f, center.y - maxRadius * 0.66f),
                                    size = Size(maxRadius * 1.32f, maxRadius * 1.32f),
                                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }

                        // Central energy core
                        val dynamicScale = if (isListening) (0.34f + audioRmsLevel * 0.16f) else 0.34f * pulsePhase
                        drawCircle(
                            color = coreColor.copy(alpha = 0.25f),
                            radius = maxRadius * (dynamicScale + 0.12f),
                            center = center
                        )
                        drawCircle(
                            color = coreColor,
                            radius = maxRadius * dynamicScale,
                            center = center
                        )

                        // Waveform ticks when speaking or listening
                        if (isListening || isSpeaking || isProcessing) {
                            val barCount = 12
                            for (idx in 0 until barCount) {
                                val wave = sin((outerRotation * 0.1f) + idx).toFloat()
                                val barLen = (8f + 10f * kotlin.math.abs(wave)).dp.toPx()
                                rotate(degrees = idx * (360f / barCount), pivot = center) {
                                    drawLine(
                                        color = IceWhiteText.copy(alpha = 0.9f),
                                        start = Offset(center.x, center.y - maxRadius * 0.48f),
                                        end = Offset(center.x, center.y - maxRadius * 0.48f - barLen),
                                        strokeWidth = 2.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Tap to issue voice command to JARVIS",
                        tint = ObsidianBg,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Live JARVIS quote or partial voice transcript
                Surface(
                    color = ObsidianBg.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HudGlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isListening && partialTranscript.isNotBlank()) {
                                "HEARING: \"$partialTranscript\""
                            } else {
                                "JARVIS: \"$latestSpeakText\""
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = IceWhiteText,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CommandLogCard(
    entry: CommandLogEntry,
    onReplaySpeak: (String) -> Unit,
    onReExecute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val intentBadgeColor = when (entry.response.intentType) {
        JarvisIntentType.OPEN_APP -> ElectricCobalt
        JarvisIntentType.MEDIA_CONTROL -> ArcCyan
        JarvisIntentType.ADJUST_VOLUME -> TelemetryEmerald
        JarvisIntentType.DEVICE_STATUS -> AmberWarning
        JarvisIntentType.TYPE_TEXT -> Color(0xFFE040FB)
        JarvisIntentType.GENERAL_RESPONSE -> SteelCyanText
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("command_log_card_${entry.id}"),
        colors = CardDefaults.cardColors(containerColor = DeepNavySurface),
        border = BorderStroke(1.dp, HudGlassBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: User Command + Timestamp + Re-run
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (entry.isVoiceOrigin) Icons.Default.Mic else Icons.Default.Keyboard,
                        contentDescription = if (entry.isVoiceOrigin) "Voice Input" else "Text Input",
                        tint = ArcCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "\"${entry.userCommand}\"",
                        style = MaterialTheme.typography.titleMedium,
                        color = IceWhiteText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = entry.timestamp,
                        style = MaterialTheme.typography.labelMedium,
                        color = SteelCyanText
                    )
                    IconButton(
                        onClick = { onReExecute(entry.userCommand) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Re-run command",
                            tint = ArcCyan
                        )
                    }
                }
            }

            // Intent & Parameter Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = intentBadgeColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, intentBadgeColor),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "intent: ${entry.response.intent}",
                        style = MaterialTheme.typography.labelMedium,
                        color = intentBadgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    color = ElevatedHudSurface,
                    border = BorderStroke(1.dp, HudGlassBorder),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "param: ${entry.response.parameter ?: "null"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = IceWhiteText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Spoken Response Row
            Surface(
                color = ElevatedHudSurface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onReplaySpeak(entry.response.speak) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Speak response",
                            tint = ArcCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = entry.response.speak,
                            style = MaterialTheme.typography.bodyMedium,
                            color = IceWhiteText
                        )
                    }
                    Text(
                        text = "SPEAK",
                        style = MaterialTheme.typography.labelMedium,
                        color = ArcCyan
                    )
                }
            }

            // Raw JSON Schema Output Block
            Surface(
                color = ObsidianBg,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ArcCyanDim.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RAW JSON RESPONSE",
                            style = MaterialTheme.typography.labelMedium,
                            color = ArcCyan
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    copyToClipboard(context, "JARVIS JSON", entry.rawJson)
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy raw JSON",
                                tint = SteelCyanText,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "COPY JSON",
                                style = MaterialTheme.typography.labelMedium,
                                color = SteelCyanText
                            )
                        }
                    }

                    Text(
                        text = entry.rawJson,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = TelemetryEmerald
                    )
                }
            }

            // System Action Execution Trace
            Surface(
                color = ElevatedHudSurface.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.dp,
                    if (entry.executionResult.executed) TelemetryEmerald.copy(alpha = 0.4f)
                    else AmberWarning.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (entry.executionResult.executed) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.Warning
                            },
                            contentDescription = "Execution Status",
                            tint = if (entry.executionResult.executed) TelemetryEmerald else AmberWarning,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = entry.executionResult.actionSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = IceWhiteText
                        )
                    }
                    Text(
                        text = entry.executionResult.technicalDetail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SteelCyanText
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Shell equivalent",
                            tint = ArcCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = entry.executionResult.shellCommandTrace,
                            style = MaterialTheme.typography.labelMedium,
                            color = ArcCyan
                        )
                    }
                }
            }
        }
    }
}

fun copyToClipboard(context: Context, label: String, text: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
