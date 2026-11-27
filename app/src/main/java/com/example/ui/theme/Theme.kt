package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.core.datastore.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = NavyOnPrimary,
    primaryContainer = NavyPrimaryContainer,
    onPrimaryContainer = NavyOnPrimaryContainer,
    secondary = CoralSecondary,
    onSecondary = CoralOnSecondary,
    secondaryContainer = CoralSecondaryContainer,
    onSecondaryContainer = CoralOnSecondaryContainer,
    tertiary = SageTertiary,
    onTertiary = SageOnTertiary,
    tertiaryContainer = SageTertiaryContainer,
    onTertiaryContainer = SageOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkNavyPrimary,
    onPrimary = DarkNavyOnPrimary,
    primaryContainer = DarkNavyPrimaryContainer,
    onPrimaryContainer = DarkNavyOnPrimaryContainer,
    secondary = DarkCoralSecondary,
    onSecondary = DarkCoralOnSecondary,
    secondaryContainer = DarkCoralSecondaryContainer,
    onSecondaryContainer = DarkCoralOnSecondaryContainer,
    tertiary = DarkSageTertiary,
    onTertiary = DarkSageOnTertiary,
    tertiaryContainer = DarkSageTertiaryContainer,
    onTertiaryContainer = DarkSageOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant
)

private val HighContrastLightColorScheme = LightColorScheme.copy(
    primary = Color(0xFF0A1D30),
    onPrimary = Color.White,
    background = Color.White,
    surface = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    outline = Color(0xFF1F2937)
)

private val HighContrastDarkColorScheme = DarkColorScheme.copy(
    primary = Color(0xFFCCE3FF),
    onPrimary = Color.Black,
    background = Color.Black,
    surface = Color(0xFF0A0D12),
    onBackground = Color.White,
    onSurface = Color.White,
    outline = Color(0xFFE5E7EB)
)

val PdfToolkitShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PdfToolkitTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    highContrast: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        highContrast && darkTheme -> HighContrastDarkColorScheme
        highContrast && !darkTheme -> HighContrastLightColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalPdfToolkitSpacing provides PdfToolkitSpacing(),
        LocalReduceMotion provides reduceMotion,
        LocalIsDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PdfToolkitTypography,
            shapes = PdfToolkitShapes,
            content = content
        )
    }
}
