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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CalmBluePrimary,
    onPrimary = CalmBlueOnPrimary,
    primaryContainer = CalmBlueContainer,
    onPrimaryContainer = CalmBlueOnContainer,
    secondary = WarmSandSecondary,
    onSecondary = WarmSandOnSecondary,
    secondaryContainer = WarmSandContainer,
    onSecondaryContainer = WarmSandOnContainer,
    tertiary = SoftSageTertiary,
    onTertiary = SoftSageOnTertiary,
    tertiaryContainer = SoftSageContainer,
    onTertiaryContainer = SoftSageOnContainer,
    background = CreamBackground,
    onBackground = DeepSlateText,
    surface = CreamSurface,
    onSurface = DeepSlateText,
    surfaceVariant = SandSurfaceVariant,
    onSurfaceVariant = MutedSlateText,
    outline = SoftOutline
)

private val DarkColorScheme = darkColorScheme(
    primary = NightBluePrimary,
    onPrimary = NightBlueOnPrimary,
    primaryContainer = NightBlueContainer,
    onPrimaryContainer = NightBlueOnContainer,
    secondary = NightSandSecondary,
    onSecondary = NightSandOnSecondary,
    secondaryContainer = NightSandContainer,
    onSecondaryContainer = NightSandOnContainer,
    tertiary = NightSageTertiary,
    onTertiary = NightSageOnTertiary,
    tertiaryContainer = NightSageContainer,
    onTertiaryContainer = NightSageOnContainer,
    background = NightBackground,
    onBackground = NightOnText,
    surface = NightSurface,
    onSurface = NightOnText,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightMutedText,
    outline = NightOutline
)

val KhulKeBoloShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KhulKeBoloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
        typography = KhulKeBoloTypography,
        shapes = KhulKeBoloShapes,
        content = content
    )
}
