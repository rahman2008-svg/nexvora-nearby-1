package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = NexVoraCyanBright,
    onPrimary = Color(0xFF003831),
    primaryContainer = NexVoraCyanDark,
    onPrimaryContainer = Color(0xFF70F8E3),
    secondary = NexVoraIndigoLight,
    onSecondary = Color(0xFF101B61),
    secondaryContainer = NexVoraIndigoDark,
    onSecondaryContainer = Color(0xFFD6DCFF),
    tertiary = StatusOnline,
    background = DarkBackground,
    onBackground = Color(0xFFE3E8F3),
    surface = DarkSurface,
    onSurface = Color(0xFFE3E8F3),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB4C1D9),
    outline = DarkOutline
  )

private val LightColorScheme =
  lightColorScheme(
    primary = NexVoraCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7F9EE),
    onPrimaryContainer = Color(0xFF003D35),
    secondary = NexVoraIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDEE3FF),
    onSecondaryContainer = Color(0xFF101C69),
    tertiary = StatusOnline,
    background = LightBackground,
    onBackground = Color(0xFF161C24),
    surface = LightSurface,
    onSurface = Color(0xFF161C24),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF4A5568),
    outline = LightOutline
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

@Composable
fun NexVoraTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MyApplicationTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
