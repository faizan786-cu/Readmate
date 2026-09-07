package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ReadMatePrimaryDark,
    onPrimary = ReadMateBackgroundDark,
    secondary = ReadMateSecondaryDark,
    onSecondary = ReadMateBackgroundDark,
    tertiary = ReadMateTertiaryDark,
    background = ReadMateBackgroundDark,
    surface = ReadMateSurfaceDark,
    surfaceVariant = ReadMateSurfaceVariantDark,
    outline = ReadMateOutlineDark,
    primaryContainer = ReadMateSurfaceVariantDark,
    onPrimaryContainer = ReadMatePrimaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = ReadMateNavyPrimary,
    onPrimary = ReadMateSurfaceLight,
    secondary = ReadMateNavySecondary,
    onSecondary = ReadMateSurfaceLight,
    tertiary = ReadMateAmberTertiary,
    background = ReadMateBackgroundLight,
    surface = ReadMateSurfaceLight,
    surfaceVariant = ReadMateSurfaceVariantLight,
    outline = ReadMateOutlineLight,
    primaryContainer = ReadMateSurfaceVariantLight,
    onPrimaryContainer = ReadMateNavyPrimary
)

@Composable
fun ReadMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent calm reading palette
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = ReadMateTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
