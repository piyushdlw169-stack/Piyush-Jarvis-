package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisDarkColorScheme = darkColorScheme(
    primary = ArcCyan,
    onPrimary = Color(0xFF00262C),
    primaryContainer = Color(0xFF004853),
    onPrimaryContainer = Color(0xFFB8F8FF),
    secondary = ElectricCobalt,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF102E5A),
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = TelemetryEmerald,
    onTertiary = Color(0xFF003919),
    background = ObsidianBg,
    onBackground = IceWhiteText,
    surface = DeepNavySurface,
    onSurface = IceWhiteText,
    surfaceVariant = ElevatedHudSurface,
    onSurfaceVariant = SteelCyanText,
    outline = HudGlassBorder,
    error = CrimsonAlert,
    onError = Color.White
)

@Composable
fun JarvisCommandTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisDarkColorScheme,
        typography = Typography,
        content = content
    )
}
