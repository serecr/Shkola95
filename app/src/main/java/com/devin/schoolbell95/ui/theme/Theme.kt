package com.devin.schoolbell95.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat

data class AccentPreset(
    val key: String,
    val label: String,
    val seed: Color,
) {
    val swatch: Color get() = seed
}

val AccentPresets: List<AccentPreset> = listOf(
    AccentPreset(key = "blue", label = "Синий", seed = Color(0xFF0061A4)),
    AccentPreset(key = "purple", label = "Фиолетовый", seed = Color(0xFF6750A4)),
    AccentPreset(key = "green", label = "Зелёный", seed = Color(0xFF006C4C)),
    AccentPreset(key = "red", label = "Красный", seed = Color(0xFFB3261E)),
    AccentPreset(key = "orange", label = "Оранжевый", seed = Color(0xFFB35900)),
    AccentPreset(key = "pink", label = "Розовый", seed = Color(0xFFB4144A)),
    AccentPreset(key = "teal", label = "Бирюзовый", seed = Color(0xFF006A6A)),
    AccentPreset(key = "graphite", label = "Графит", seed = Color(0xFF3F484A)),
)

private fun presetByKey(key: String): AccentPreset =
    AccentPresets.firstOrNull { it.key == key } ?: AccentPresets.first()

/** Shift the lightness of [this] to [lightness] (0..1) while keeping the seed's hue & saturation. */
private fun Color.withLightness(lightness: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[2] = lightness.coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/** Same hue, but desaturated to [saturation] and set to [lightness] — gives neutral tinted surfaces. */
private fun Color.tonal(saturation: Float, lightness: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[1] = saturation.coerceIn(0f, 1f)
    hsl[2] = lightness.coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun lightSchemeFromSeed(seed: Color): ColorScheme {
    val primary = seed.withLightness(0.40f)
    val primaryContainer = seed.withLightness(0.90f)
    val onPrimaryContainer = seed.withLightness(0.10f)

    val secondary = seed.tonal(0.18f, 0.40f)
    val secondaryContainer = seed.tonal(0.18f, 0.88f)
    val onSecondaryContainer = seed.tonal(0.20f, 0.12f)

    val tertiary = seed.tonal(0.30f, 0.40f)
    val tertiaryContainer = seed.tonal(0.30f, 0.88f)
    val onTertiaryContainer = seed.tonal(0.32f, 0.12f)

    val background = seed.tonal(0.05f, 0.985f)
    val surface = background
    val surfaceVariant = seed.tonal(0.10f, 0.90f)
    val onSurface = seed.tonal(0.10f, 0.10f)
    val onSurfaceVariant = seed.tonal(0.12f, 0.30f)
    val outline = seed.tonal(0.10f, 0.50f)
    val outlineVariant = seed.tonal(0.10f, 0.80f)

    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = Color.White,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        outline = outline,
        outlineVariant = outlineVariant,
        inverseSurface = seed.tonal(0.10f, 0.20f),
        inverseOnSurface = seed.tonal(0.05f, 0.95f),
        inversePrimary = seed.withLightness(0.80f),
    )
}

private fun darkSchemeFromSeed(seed: Color): ColorScheme {
    val primary = seed.withLightness(0.80f)
    val primaryContainer = seed.withLightness(0.30f)
    val onPrimaryContainer = seed.withLightness(0.92f)

    val secondary = seed.tonal(0.20f, 0.80f)
    val secondaryContainer = seed.tonal(0.18f, 0.28f)
    val onSecondaryContainer = seed.tonal(0.18f, 0.92f)

    val tertiary = seed.tonal(0.32f, 0.80f)
    val tertiaryContainer = seed.tonal(0.28f, 0.30f)
    val onTertiaryContainer = seed.tonal(0.30f, 0.92f)

    val background = seed.tonal(0.10f, 0.08f)
    val surface = background
    val surfaceVariant = seed.tonal(0.10f, 0.22f)
    val onSurface = seed.tonal(0.05f, 0.92f)
    val onSurfaceVariant = seed.tonal(0.10f, 0.80f)
    val outline = seed.tonal(0.10f, 0.60f)
    val outlineVariant = seed.tonal(0.10f, 0.30f)

    return darkColorScheme(
        primary = primary,
        onPrimary = seed.withLightness(0.15f),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = seed.tonal(0.10f, 0.15f),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = seed.tonal(0.20f, 0.15f),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        outline = outline,
        outlineVariant = outlineVariant,
        inverseSurface = seed.tonal(0.05f, 0.92f),
        inverseOnSurface = seed.tonal(0.10f, 0.20f),
        inversePrimary = seed.withLightness(0.40f),
    )
}

@Composable
fun AppTheme(
    darkMode: String = "system",
    accentKey: String = "blue",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (darkMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val canDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val preset = presetByKey(accentKey)
    val colors = when {
        canDynamic && useDark -> dynamicDarkColorScheme(context)
        canDynamic && !useDark -> dynamicLightColorScheme(context)
        useDark -> darkSchemeFromSeed(preset.seed)
        else -> lightSchemeFromSeed(preset.seed)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
        }
    }

    MaterialTheme(colorScheme = colors, content = content)
}
