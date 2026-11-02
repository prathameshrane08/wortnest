package com.prathameshrane.wortnest

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Clean and calm: white space, one indigo accent, slate text. The same palette as the website.
private val Light = lightColorScheme(
    primary = Color(0xFF6366F1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF4F46E5),
    secondaryContainer = Color(0xFFEEF2FF),
    onSecondaryContainer = Color(0xFF4F46E5),
    background = Color.White,
    surface = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF8FAFC),
    surfaceContainerHigh = Color(0xFFF1F5F9),
    surfaceContainerHighest = Color(0xFFF1F5F9),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFE2E8F0),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF0B1120),
    primaryContainer = Color(0xFF1E1F4A),
    onPrimaryContainer = Color(0xFFA5B4FC),
    secondaryContainer = Color(0xFF1E1F4A),
    onSecondaryContainer = Color(0xFFA5B4FC),
    background = Color(0xFF0B1120),
    surface = Color(0xFF0B1120),
    surfaceContainerLowest = Color(0xFF0B1120),
    surfaceContainerLow = Color(0xFF111A2E),
    surfaceContainer = Color(0xFF111A2E),
    surfaceContainerHigh = Color(0xFF1A2438),
    surfaceContainerHighest = Color(0xFF1A2438),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF243049),
    outlineVariant = Color(0xFF243049),
)

/** True when the learner switched to dark with the sun/moon button; the app is white otherwise. */
val LocalDark = staticCompositionLocalOf { false }

@Composable
fun WortNestTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDark provides dark) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}

/** der blue, die rose, das green: the classroom convention. */
@Composable
fun genderColor(g: String): Color {
    val dark = LocalDark.current
    return when (g) {
        "m" -> if (dark) Color(0xFF60A5FA) else Color(0xFF2563EB)
        "f" -> if (dark) Color(0xFFFB7185) else Color(0xFFE11D48)
        "n" -> if (dark) Color(0xFF34D399) else Color(0xFF059669)
        "p" -> if (dark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
        else -> MaterialTheme.colorScheme.onSurface
    }
}

/** The same colours on the dark word-of-the-day card, in both themes. */
fun genderColorOnDark(g: String): Color = when (g) {
    "m" -> Color(0xFF60A5FA); "f" -> Color(0xFFFB7185); "n" -> Color(0xFF34D399); else -> Color.White
}

/** Background of the word-of-the-day card. */
@Composable
fun heroColor(): Color = if (LocalDark.current) Color(0xFF1E1B4B) else Color(0xFF0F172A)

/** Forms that break the regular pattern. */
@Composable
fun irregularColor(): Color = if (LocalDark.current) Color(0xFFFDBA74) else Color(0xFFC2410C)

@Composable
fun okColor(): Color = if (LocalDark.current) Color(0xFF34D399) else Color(0xFF059669)

@Composable
fun badColor(): Color = if (LocalDark.current) Color(0xFFFB7185) else Color(0xFFE11D48)
