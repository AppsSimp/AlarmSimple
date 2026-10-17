package com.appssimple.alarmsimple.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Dark theme colors — deep blue/indigo palette (calming, sleep-oriented)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8B9CF7),
    onPrimary = Color(0xFF1A1B3D),
    primaryContainer = Color(0xFF3949AB),
    onPrimaryContainer = Color(0xFFDBE1FF),
    secondary = Color(0xFF7DD6C0),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF1B5E4F),
    onSecondaryContainer = Color(0xFFA5F2DA),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF4A2800),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE3E3EC),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE3E3EC),
    surfaceVariant = Color(0xFF1F2937),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// Light theme colors
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3949AB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE1FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF1B6B5A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFA5F2DA),
    onSecondaryContainer = Color(0xFF00201A),
    tertiary = Color(0xFFE65100),
    onTertiary = Color.White,
    background = Color(0xFFFBFBFF),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFBFBFF),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF767680),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun AlarmSimpleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Use dynamic color on Android 12+ if available
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
