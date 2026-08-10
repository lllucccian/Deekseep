package com.dsmod.probe.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6B45),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F1C5),
    onPrimaryContainer = Color(0xFF0C3B22),
    secondary = Color(0xFF4F6354),
    secondaryContainer = Color(0xFFD2E8D5),
    background = Color(0xFFF7FAF6),
    surface = Color(0xFFF7FAF6),
    surfaceVariant = Color(0xFFDDE5DD),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ED8AC),
    onPrimary = Color(0xFF07391E),
    primaryContainer = Color(0xFF205332),
    onPrimaryContainer = Color(0xFFB9F2C7),
    secondary = Color(0xFFB6CCBA),
    secondaryContainer = Color(0xFF384B3D),
    background = Color(0xFF101411),
    surface = Color(0xFF101411),
    surfaceVariant = Color(0xFF404942),
)

/** Deekseep-owned Material 3 theme; no third-party application theme source is used. */
@Composable
fun DeekseepTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
