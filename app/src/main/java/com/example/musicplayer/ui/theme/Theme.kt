package com.example.musicplayer.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils

private val DarkColorScheme = darkColorScheme(
    primary = LocalMusicVioletPrimary,
    onPrimary = Color.White,
    primaryContainer = LocalMusicDarkPrimaryContainer,
    onPrimaryContainer = LocalMusicVioletSoft,
    inversePrimary = LocalMusicVioletVibrant,

    secondary = LocalMusicVioletLight,
    onSecondary = Color(0xFF250A4E),
    secondaryContainer = LocalMusicDarkSecondaryContainer,
    onSecondaryContainer = LocalMusicVioletSoft,

    tertiary = LocalMusicVioletNeon,
    onTertiary = Color.White,
    tertiaryContainer = LocalMusicDarkTertiaryContainer,
    onTertiaryContainer = LocalMusicVioletGlow,

    background = LocalMusicDarkBackground,
    onBackground = LocalMusicDarkTextPrimary,

    surface = LocalMusicDarkSurface,
    onSurface = LocalMusicDarkTextPrimary,
    surfaceVariant = LocalMusicDarkSurfaceVariant,
    onSurfaceVariant = LocalMusicDarkTextSecondary,

    surfaceContainerLowest = Color(0xFF09070F),
    surfaceContainerLow = Color(0xFF140F22),
    surfaceContainer = LocalMusicDarkSurfaceCard,
    surfaceContainerHigh = Color(0xFF231B3C),
    surfaceContainerHighest = LocalMusicDarkSurfaceCardHighest,

    outline = LocalMusicDarkOutline,
    outlineVariant = LocalMusicDarkOutlineVariant,

    inverseSurface = LocalMusicDarkTextPrimary,
    inverseOnSurface = LocalMusicDarkBackground
)

private val LightColorScheme = lightColorScheme(
    primary = LocalMusicVioletVibrant,
    onPrimary = Color.White,
    primaryContainer = LocalMusicLightPrimaryContainer,
    onPrimaryContainer = LocalMusicLightOnPrimaryContainer,
    inversePrimary = LocalMusicVioletLight,

    secondary = LocalMusicVioletDark,
    onSecondary = Color.White,
    secondaryContainer = LocalMusicLightSecondaryContainer,
    onSecondaryContainer = LocalMusicLightOnSecondaryContainer,

    tertiary = LocalMusicVioletNeon,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5E8FF),
    onTertiaryContainer = Color(0xFF380860),

    background = LocalMusicLightBackground,
    onBackground = LocalMusicLightTextPrimary,

    surface = LocalMusicLightSurface,
    onSurface = LocalMusicLightTextPrimary,
    surfaceVariant = LocalMusicLightSurfaceVariant,
    onSurfaceVariant = LocalMusicLightTextSecondary,

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F3FB),
    surfaceContainer = LocalMusicLightSurfaceCard,
    surfaceContainerHigh = Color(0xFFECE6F4),
    surfaceContainerHighest = LocalMusicLightSurfaceCardHighest,

    outline = LocalMusicLightOutline,
    outlineVariant = LocalMusicLightOutlineVariant,

    inverseSurface = Color(0xFF2A233A),
    inverseOnSurface = Color(0xFFF7F2FA)
)

/**
 * Armonizza un colore di accento dinamico di sistema con la tonalità dell'icona (MusiCal).
 * Conserva la dinamicità di luminosità del sistema ma vincola la tonalità (Hue) e la saturazione
 * allo spettro viola elettrico / neon dell'icona.
 */
private fun harmonizeAccent(systemColor: Color, brandBaseColor: Color): Color {
    val sysHsl = FloatArray(3)
    val brandHsl = FloatArray(3)
    ColorUtils.colorToHSL(systemColor.toArgb(), sysHsl)
    ColorUtils.colorToHSL(brandBaseColor.toArgb(), brandHsl)

    val brandHue = brandHsl[0] // ~260°-271°
    val hueDelta = ((sysHsl[0] - brandHue + 180f) % 360f) - 180f
    // Sposta l'85% della tonalità verso il viola dell'icona, permettendo un 15% di sfumatura del wallpaper
    val finalHue = (brandHue + hueDelta * 0.15f + 360f) % 360f
    // Mantiene una saturazione ricca e vivace per evitare toni sbiaditi o grigiastri
    val finalSaturation = (sysHsl[1] * 0.35f + brandHsl[1] * 0.65f).coerceIn(0.55f, 0.95f)
    // Preserva la luminanza calcolata da Android per garantire contrasto e leggibilità
    val finalLightness = sysHsl[2].coerceIn(0.20f, 0.85f)

    val finalArgb = ColorUtils.HSLToColor(floatArrayOf(finalHue, finalSaturation, finalLightness))
    return Color(finalArgb)
}

/**
 * Armonizza le superfici scure con la base notte ossidiana e viola profondo dell'icona.
 */
private fun harmonizeSurface(systemSurface: Color, brandSurface: Color): Color {
    val sysHsl = FloatArray(3)
    val brandHsl = FloatArray(3)
    ColorUtils.colorToHSL(systemSurface.toArgb(), sysHsl)
    ColorUtils.colorToHSL(brandSurface.toArgb(), brandHsl)

    val finalHue = brandHsl[0]
    val finalSaturation = brandHsl[1].coerceIn(0.18f, 0.30f)
    val finalLightness = sysHsl[2].coerceIn(0.04f, 0.22f)

    val finalArgb = ColorUtils.HSLToColor(floatArrayOf(finalHue, finalSaturation, finalLightness))
    return Color(finalArgb)
}

/**
 * Armonizza le superfici chiare per mantenere una delicata sfumatura lavanda luminosa.
 */
private fun harmonizeLightSurface(systemSurface: Color, brandSurface: Color): Color {
    val sysHsl = FloatArray(3)
    val brandHsl = FloatArray(3)
    ColorUtils.colorToHSL(systemSurface.toArgb(), sysHsl)
    ColorUtils.colorToHSL(brandSurface.toArgb(), brandHsl)

    val finalHue = brandHsl[0]
    val finalSaturation = (sysHsl[1] * 0.3f + brandHsl[1] * 0.7f).coerceIn(0.04f, 0.12f)
    val finalLightness = sysHsl[2].coerceIn(0.92f, 0.98f)

    val finalArgb = ColorUtils.HSLToColor(floatArrayOf(finalHue, finalSaturation, finalLightness))
    return Color(finalArgb)
}

private fun harmonizedDynamicDarkColorScheme(context: Context): ColorScheme {
    val sys = dynamicDarkColorScheme(context)
    return DarkColorScheme.copy(
        primary = harmonizeAccent(sys.primary, LocalMusicVioletPrimary),
        onPrimary = Color.White,
        primaryContainer = harmonizeAccent(sys.primaryContainer, LocalMusicDarkPrimaryContainer),
        onPrimaryContainer = LocalMusicVioletSoft,
        inversePrimary = LocalMusicVioletVibrant,

        secondary = harmonizeAccent(sys.secondary, LocalMusicVioletLight),
        onSecondary = Color(0xFF250A4E),
        secondaryContainer = harmonizeAccent(sys.secondaryContainer, LocalMusicDarkSecondaryContainer),
        onSecondaryContainer = LocalMusicVioletSoft,

        tertiary = harmonizeAccent(sys.tertiary, LocalMusicVioletNeon),
        onTertiary = Color.White,
        tertiaryContainer = harmonizeAccent(sys.tertiaryContainer, LocalMusicDarkTertiaryContainer),
        onTertiaryContainer = LocalMusicVioletGlow,

        background = LocalMusicDarkBackground,
        onBackground = LocalMusicDarkTextPrimary,

        surface = harmonizeSurface(sys.surface, LocalMusicDarkSurface),
        onSurface = LocalMusicDarkTextPrimary,
        surfaceVariant = harmonizeSurface(sys.surfaceVariant, LocalMusicDarkSurfaceVariant),
        onSurfaceVariant = LocalMusicDarkTextSecondary,

        surfaceContainerLowest = Color(0xFF09070F),
        surfaceContainerLow = harmonizeSurface(sys.surfaceContainerLow, Color(0xFF140F22)),
        surfaceContainer = harmonizeSurface(sys.surfaceContainer, LocalMusicDarkSurfaceCard),
        surfaceContainerHigh = harmonizeSurface(sys.surfaceContainerHigh, Color(0xFF231B3C)),
        surfaceContainerHighest = harmonizeSurface(sys.surfaceContainerHighest, LocalMusicDarkSurfaceCardHighest),

        outline = harmonizeSurface(sys.outline, LocalMusicDarkOutline),
        outlineVariant = harmonizeSurface(sys.outlineVariant, LocalMusicDarkOutlineVariant),

        inverseSurface = LocalMusicDarkTextPrimary,
        inverseOnSurface = LocalMusicDarkBackground
    )
}

private fun harmonizedDynamicLightColorScheme(context: Context): ColorScheme {
    val sys = dynamicLightColorScheme(context)
    return LightColorScheme.copy(
        primary = harmonizeAccent(sys.primary, LocalMusicVioletVibrant),
        onPrimary = Color.White,
        primaryContainer = harmonizeAccent(sys.primaryContainer, LocalMusicLightPrimaryContainer),
        onPrimaryContainer = LocalMusicLightOnPrimaryContainer,
        inversePrimary = LocalMusicVioletLight,

        secondary = harmonizeAccent(sys.secondary, LocalMusicVioletDark),
        onSecondary = Color.White,
        secondaryContainer = harmonizeAccent(sys.secondaryContainer, LocalMusicLightSecondaryContainer),
        onSecondaryContainer = LocalMusicLightOnSecondaryContainer,

        tertiary = harmonizeAccent(sys.tertiary, LocalMusicVioletNeon),
        onTertiary = Color.White,
        tertiaryContainer = harmonizeAccent(sys.tertiaryContainer, Color(0xFFF5E8FF)),
        onTertiaryContainer = Color(0xFF380860),

        background = LocalMusicLightBackground,
        onBackground = LocalMusicLightTextPrimary,

        surface = harmonizeLightSurface(sys.surface, LocalMusicLightSurface),
        onSurface = LocalMusicLightTextPrimary,
        surfaceVariant = harmonizeLightSurface(sys.surfaceVariant, LocalMusicLightSurfaceVariant),
        onSurfaceVariant = LocalMusicLightTextSecondary,

        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = harmonizeLightSurface(sys.surfaceContainerLow, Color(0xFFF7F3FB)),
        surfaceContainer = harmonizeLightSurface(sys.surfaceContainer, LocalMusicLightSurfaceCard),
        surfaceContainerHigh = harmonizeLightSurface(sys.surfaceContainerHigh, Color(0xFFECE6F4)),
        surfaceContainerHighest = harmonizeLightSurface(sys.surfaceContainerHighest, LocalMusicLightSurfaceCardHighest),

        outline = harmonizeLightSurface(sys.outline, LocalMusicLightOutline),
        outlineVariant = harmonizeLightSurface(sys.outlineVariant, LocalMusicLightOutlineVariant),

        inverseSurface = Color(0xFF2A233A),
        inverseOnSurface = Color(0xFFF7F2FA)
    )
}

@Composable
fun MusicPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor -> {
            if (darkTheme) harmonizedDynamicDarkColorScheme(context) else harmonizedDynamicLightColorScheme(context)
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
