package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.JarvisIntentType
import com.example.model.JarvisPromptContract
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.DeepNavySurface
import com.example.ui.theme.ElevatedHudSurface
import com.example.ui.theme.HudGlassBorder
import com.example.ui.theme.IceWhiteText
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SteelCyanText
import com.example.ui.theme.TelemetryEmerald

@Composable
fun SchemaInspectorScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val jsonSchemaDisplay = """
{
  "action": "open_app | global_action | adjust_volume | flashlight | tap | swipe | media_control | device_status | type_text | chat",
  "target": "value_or_null",
  "speech": "What JARVIS speaks out loud to the user"
}
    """.trimIndent()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("schema_inspector_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DeepNavySurface),
                border = BorderStroke(1.dp, ArcCyan.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "ENFORCED GEMINI JSON SCHEMA CONTRACT",
                                style = MaterialTheme.typography.titleMedium,
                                color = ArcCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Configured via Firebase.ai responseMimeType = \"application/json\" & Schema.obj(...)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SteelCyanText
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, "JARVIS Schema", jsonSchemaDisplay)
                            },
                            border = BorderStroke(1.dp, HudGlassBorder),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Schema",
                                tint = ArcCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("COPY", color = IceWhiteText)
                        }
                    }

                    Surface(
                        color = ObsidianBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HudGlassBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = jsonSchemaDisplay,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = TelemetryEmerald,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "REGISTERED SYSTEM & ACCESSIBILITY ACTIONS (${JarvisIntentType.entries.size})",
                style = MaterialTheme.typography.titleMedium,
                color = IceWhiteText,
                fontWeight = FontWeight.Bold
            )
        }

        items(JarvisIntentType.entries) { capability ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DeepNavySurface),
                border = BorderStroke(1.dp, HudGlassBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = capability.signature,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ArcCyan
                    )
                    Text(
                        text = capability.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IceWhiteText
                    )
                }
            }
        }

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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "ACTIVE SYSTEM INSTRUCTION",
                        style = MaterialTheme.typography.titleMedium,
                        color = ArcCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = ElevatedHudSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = JarvisPromptContract.SYSTEM_INSTRUCTION,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = IceWhiteText,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
