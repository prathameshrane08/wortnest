package com.prathameshrane.wortkiste

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFFB06A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3BD),
    onPrimaryContainer = Color(0xFF3A2200),
    background = Color(0xFFF3F5F7),
    surface = Color(0xFFF3F5F7),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFE8ECF0),
    onSurface = Color(0xFF18202B),
    onSurfaceVariant = Color(0xFF5D6876),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF0A929),
    onPrimary = Color(0xFF2E1B00),
    primaryContainer = Color(0xFF4A3000),
    onPrimaryContainer = Color(0xFFFFDDB0),
    background = Color(0xFF11161D),
    surface = Color(0xFF11161D),
    surfaceContainer = Color(0xFF19202A),
    surfaceContainerHigh = Color(0xFF232C38),
    onSurface = Color(0xFFE6EBF1),
    onSurfaceVariant = Color(0xFF97A3B2),
)

@Composable
fun WortkisteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** der blue, die red, das green: the classroom convention. */
@Composable
fun genderColor(g: String): Color {
    val dark = isSystemInDarkTheme()
    return when (g) {
        "m" -> if (dark) Color(0xFF7AA7F5) else Color(0xFF2A64C5)
        "f" -> if (dark) Color(0xFFF07C80) else Color(0xFFC8383D)
        "n" -> if (dark) Color(0xFF5FC694) else Color(0xFF24895A)
        "p" -> if (dark) Color(0xFFB596EA) else Color(0xFF7A54B8)
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
fun okColor(): Color = if (isSystemInDarkTheme()) Color(0xFF5FC694) else Color(0xFF24895A)

@Composable
fun badColor(): Color = if (isSystemInDarkTheme()) Color(0xFFF07C80) else Color(0xFFC8383D)
