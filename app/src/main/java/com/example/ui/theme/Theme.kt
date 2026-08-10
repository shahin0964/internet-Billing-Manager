package com.example.ui.theme

import android.app.Activity
import android.content.Context
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
import androidx.core.view.WindowCompat
import com.example.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// 1. Dark Default
val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = DarkBackground,
    primaryContainer = CyanPrimaryContainer,
    onPrimaryContainer = OnCyanPrimaryContainer,
    secondary = BlueSecondary,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    error = CrimsonDanger,
    errorContainer = CrimsonDangerContainer
)

// 2. Light Default
val LightColorScheme = lightColorScheme(
    primary = LightCyanPrimary,
    onPrimary = LightSurface,
    primaryContainer = LightCyanPrimaryContainer,
    onPrimaryContainer = LightOnCyanPrimaryContainer,
    secondary = LightCyanPrimary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = CrimsonDanger,
    errorContainer = CrimsonDangerContainer
)

// 3. Ocean Blue
val OceanColorScheme = darkColorScheme(
    primary = OceanPrimary,
    onPrimary = OceanBackground,
    primaryContainer = OceanPrimaryContainer,
    onPrimaryContainer = OceanOnPrimaryContainer,
    secondary = BlueSecondary,
    onSecondary = OceanBackground,
    background = OceanBackground,
    onBackground = DarkTextPrimary,
    surface = OceanSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = OceanSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = Color(0x3338BDF8),
    error = CrimsonDanger,
    errorContainer = CrimsonDangerContainer
)

// 4. Emerald
val EmeraldColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldBackground,
    primaryContainer = EmeraldPrimaryContainer,
    onPrimaryContainer = EmeraldOnPrimaryContainer,
    secondary = Color(0xFF34D399),
    onSecondary = EmeraldBackground,
    background = EmeraldBackground,
    onBackground = Color(0xFFF0FDF4),
    surface = EmeraldSurface,
    onSurface = Color(0xFFF0FDF4),
    surfaceVariant = EmeraldSurfaceVariant,
    onSurfaceVariant = Color(0xFFA7F3D0),
    outline = Color(0x3310B981),
    error = CrimsonDanger,
    errorContainer = CrimsonDangerContainer
)

// 5. Royal Purple
val PurpleColorScheme = darkColorScheme(
    primary = PurplePrimary,
    onPrimary = PurpleBackground,
    primaryContainer = PurplePrimaryContainer,
    onPrimaryContainer = PurpleOnPrimaryContainer,
    secondary = Color(0xFFC084FC),
    onSecondary = PurpleBackground,
    background = PurpleBackground,
    onBackground = Color(0xFFFAF5FF),
    surface = PurpleSurface,
    onSurface = Color(0xFFFAF5FF),
    surfaceVariant = PurpleSurfaceVariant,
    onSurfaceVariant = Color(0xFFE9D5FF),
    outline = Color(0x33A855F7),
    error = CrimsonDanger,
    errorContainer = CrimsonDangerContainer
)

// 6. Sunset
val SunsetColorScheme = darkColorScheme(
    primary = SunsetPrimary,
    onPrimary = SunsetBackground,
    primaryContainer = SunsetPrimaryContainer,
    onPrimaryContainer = SunsetOnPrimaryContainer,
    secondary = Color(0xFFFB923C),
    onSecondary = SunsetBackground,
    background = SunsetBackground,
    onBackground = Color(0xFFFFF7ED),
    surface = SunsetSurface,
    onSurface = Color(0xFFFFF7ED),
    surfaceVariant = SunsetSurfaceVariant,
    onSurfaceVariant = Color(0xFFFDBA74),
    outline = Color(0x33F97316),
    error = CrimsonDanger,
    errorContainer = CrimsonDangerContainer
)

// 7. Midnight
val MidnightColorScheme = darkColorScheme(
    primary = MidnightPrimary,
    onPrimary = MidnightBackground,
    primaryContainer = MidnightPrimaryContainer,
    onPrimaryContainer = MidnightOnPrimaryContainer,
    secondary = Color(0xFFA5B4FC),
    onSecondary = MidnightBackground,
    background = MidnightBackground,
    onBackground = Color(0xFFF9FAFB),
    surface = MidnightSurface,
    onSurface = Color(0xFFF9FAFB),
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0x33818CF8),
    error = CrimsonDanger,
    errorContainer = CrimsonDangerContainer
)

// 8. Slate (Light/Minimal Slate)
val SlateColorScheme = lightColorScheme(
    primary = SlatePrimary,
    onPrimary = Color.White,
    primaryContainer = SlatePrimaryContainer,
    onPrimaryContainer = SlateOnPrimaryContainer,
    secondary = Color(0xFF64748B),
    onSecondary = Color.White,
    background = SlateBackground,
    onBackground = Color(0xFF0F172A),
    surface = SlateSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = CrimsonDanger,
    errorContainer = CrimsonDangerContainer
)

// 9. Aqua
val AquaColorScheme = darkColorScheme(
    primary = AquaPrimary,
    onPrimary = AquaBackground,
    primaryContainer = AquaPrimaryContainer,
    onPrimaryContainer = AquaOnPrimaryContainer,
    secondary = Color(0xFF22D3EE),
    onSecondary = AquaBackground,
    background = AquaBackground,
    onBackground = Color(0xFFECFEFF),
    surface = AquaSurface,
    onSurface = Color(0xFFECFEFF),
    surfaceVariant = AquaSurfaceVariant,
    onSurfaceVariant = Color(0xFF67E8F9),
    outline = Color(0x3306B6D4),
    error = CrimsonDanger,
    errorContainer = CrimsonDangerContainer
)

// 10. Liquid Glass Light
val LiquidGlassLightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFF0EA5E9),
    onSecondary = Color.White,
    background = Color(0x59F1F5F9),
    onBackground = Color(0xFF0F172A),
    surface = Color(0x99FFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0x33E2E8F0),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0x80FFFFFF),
    error = CrimsonDanger,
    errorContainer = CrimsonDangerContainer
)

// 11. Liquid Glass Dark
val LiquidGlassDarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF030712),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF0EA5E9),
    onSecondary = Color(0xFF030712),
    background = Color(0xB30B0F19),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0x80111827),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0x4D1F2937),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0x4DFFFFFF),
    error = CrimsonDanger,
    errorContainer = CrimsonDangerContainer
)

// 12. Monet / Dynamic Color
// Colors are dynamically determined by system, so no predefined ColorScheme is needed here.

data class AppThemeItem(
    val key: String,
    val nameRes: Int,
    val defaultName: String,
    val descRes: Int,
    val defaultDesc: String,
    val previewPrimary: Color,
    val previewBackground: Color,
    val previewSurface: Color,
    val isDarkScheme: Boolean
) {
    fun getLocalizedName(context: Context): String {
        return try {
            context.getString(nameRes)
        } catch (e: Exception) {
            defaultName
        }
    }

    fun getDescription(context: Context): String {
        return try {
            context.getString(descRes)
        } catch (e: Exception) {
            defaultDesc
        }
    }
}

val ALL_THEME_ITEMS = listOf(
    AppThemeItem(
        key = "SYSTEM",
        nameRes = R.string.theme_system_default,
        defaultName = "System Default",
        descRes = R.string.theme_system_default_desc,
        defaultDesc = "Follows device appearance automatically",
        previewPrimary = CyanPrimary,
        previewBackground = DarkBackground,
        previewSurface = DarkSurface,
        isDarkScheme = true
    ),
    AppThemeItem(
        key = "LIGHT",
        nameRes = R.string.theme_light,
        defaultName = "Light",
        descRes = R.string.theme_light_desc,
        defaultDesc = "Clean, bright, professional appearance",
        previewPrimary = LightCyanPrimary,
        previewBackground = LightBackground,
        previewSurface = LightSurface,
        isDarkScheme = false
    ),
    AppThemeItem(
        key = "DARK",
        nameRes = R.string.theme_dark,
        defaultName = "Dark",
        descRes = R.string.theme_dark_desc,
        defaultDesc = "Modern dark interface with comfortable contrast",
        previewPrimary = CyanPrimary,
        previewBackground = DarkBackground,
        previewSurface = DarkSurface,
        isDarkScheme = true
    ),
    AppThemeItem(
        key = "OCEAN_BLUE",
        nameRes = R.string.theme_ocean_blue,
        defaultName = "Ocean Blue",
        descRes = R.string.theme_ocean_blue_desc,
        defaultDesc = "Professional blue/teal ISP management style",
        previewPrimary = OceanPrimary,
        previewBackground = OceanBackground,
        previewSurface = OceanSurface,
        isDarkScheme = true
    ),
    AppThemeItem(
        key = "EMERALD",
        nameRes = R.string.theme_emerald,
        defaultName = "Emerald",
        descRes = R.string.theme_emerald_desc,
        defaultDesc = "Elegant green-based professional theme",
        previewPrimary = EmeraldPrimary,
        previewBackground = EmeraldBackground,
        previewSurface = EmeraldSurface,
        isDarkScheme = true
    ),
    AppThemeItem(
        key = "ROYAL_PURPLE",
        nameRes = R.string.theme_royal_purple,
        defaultName = "Royal Purple",
        descRes = R.string.theme_royal_purple_desc,
        defaultDesc = "Premium purple-based theme",
        previewPrimary = PurplePrimary,
        previewBackground = PurpleBackground,
        previewSurface = PurpleSurface,
        isDarkScheme = true
    ),
    AppThemeItem(
        key = "SUNSET",
        nameRes = R.string.theme_sunset,
        defaultName = "Sunset",
        descRes = R.string.theme_sunset_desc,
        defaultDesc = "Warm orange/red accent style",
        previewPrimary = SunsetPrimary,
        previewBackground = SunsetBackground,
        previewSurface = SunsetSurface,
        isDarkScheme = true
    ),
    AppThemeItem(
        key = "MIDNIGHT",
        nameRes = R.string.theme_midnight,
        defaultName = "Midnight",
        descRes = R.string.theme_midnight_desc,
        defaultDesc = "Deep dark premium appearance with subtle blue accents",
        previewPrimary = MidnightPrimary,
        previewBackground = MidnightBackground,
        previewSurface = MidnightSurface,
        isDarkScheme = true
    ),
    AppThemeItem(
        key = "SLATE",
        nameRes = R.string.theme_slate,
        defaultName = "Slate",
        descRes = R.string.theme_slate_desc,
        defaultDesc = "Minimal gray/slate professional appearance",
        previewPrimary = SlatePrimary,
        previewBackground = SlateBackground,
        previewSurface = SlateSurface,
        isDarkScheme = false
    ),
    AppThemeItem(
        key = "AQUA",
        nameRes = R.string.theme_aqua,
        defaultName = "Aqua",
        descRes = R.string.theme_aqua_desc,
        defaultDesc = "Fresh cyan/blue appearance",
        previewPrimary = AquaPrimary,
        previewBackground = AquaBackground,
        previewSurface = AquaSurface,
        isDarkScheme = true
    ),
    AppThemeItem(
        key = "LIQUID_GLASS",
        nameRes = R.string.theme_liquid_glass,
        defaultName = "✨ Liquid Glass",
        descRes = R.string.theme_liquid_glass_desc,
        defaultDesc = "Frosted translucent theme with rounded curves and depth (Android 12+)",
        previewPrimary = Color(0xFF38BDF8),
        previewBackground = Color(0xFF0B0F19),
        previewSurface = Color(0x99111827),
        isDarkScheme = true
    ),
    AppThemeItem(
        key = "DYNAMIC",
        nameRes = R.string.theme_dynamic,
        defaultName = "🎨 Dynamic Color",
        descRes = R.string.theme_dynamic_desc,
        defaultDesc = "Uses your device's wallpaper colors (Android 12+)",
        previewPrimary = Color(0xFF6750A4),
        previewBackground = Color(0xFFFFFBFE),
        previewSurface = Color(0xFFF8F1FF),
        isDarkScheme = false
    )
)

fun isLiquidGlassSupported(): Boolean {
    return true
}

fun isDynamicColorSupported(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

fun getThemeItem(key: String): AppThemeItem {
    return ALL_THEME_ITEMS.find { it.key.equals(key, ignoreCase = true) }
        ?: ALL_THEME_ITEMS.first()
}

fun getThemeColorScheme(themeMode: String, isSystemDark: Boolean): Pair<ColorScheme, Boolean> {
    return when (themeMode.uppercase()) {
        "LIGHT" -> Pair(LightColorScheme, false)
        "DARK" -> Pair(DarkColorScheme, true)
        "OCEAN_BLUE" -> Pair(OceanColorScheme, true)
        "EMERALD" -> Pair(EmeraldColorScheme, true)
        "ROYAL_PURPLE" -> Pair(PurpleColorScheme, true)
        "SUNSET" -> Pair(SunsetColorScheme, true)
        "MIDNIGHT" -> Pair(MidnightColorScheme, true)
        "SLATE" -> Pair(SlateColorScheme, false)
        "AQUA" -> Pair(AquaColorScheme, true)
        "LIQUID_GLASS" -> Pair(LiquidGlassLightColorScheme, false)
        "DYNAMIC" -> Pair(LightColorScheme, false)
        else -> Pair(if (isSystemDark) DarkColorScheme else LightColorScheme, isSystemDark)
    }
}

@Composable
fun IspControlTheme(
    themeMode: String = "SYSTEM",
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val context = LocalContext.current
    
    val colorScheme = when {
        themeMode.uppercase() == "DYNAMIC" && isDynamicColorSupported() -> {
            if (isSystemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            getThemeColorScheme(themeMode, isSystemDark).first
        }
    }
    
    val isDarkStatusBar = when {
        themeMode.uppercase() == "DYNAMIC" && isDynamicColorSupported() -> isSystemDark
        else -> getThemeColorScheme(themeMode, isSystemDark).second
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            if (themeMode.uppercase() == "LIQUID_GLASS") {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
            } else {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkStatusBar
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDarkStatusBar
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun GlassmorphismBackground() {
    val isDark = isSystemInDarkTheme()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            // 1. Draw base gorgeous gradient from top-left (light sky blue/cyan) to bottom-right (vibrant pink/magenta)
            val baseGradient = Brush.linearGradient(
                colors = if (isDark) {
                    listOf(
                        Color(0xFF0D1B2A), // Dark blue
                        Color(0xFF1B263B), // Indigo dark
                        Color(0xFF415A77), // Steel blue
                        Color(0xFF2C1B47)  // Deep magenta-purple
                    )
                } else {
                    listOf(
                        Color(0xFF38BDF8), // Bright Sky Blue / Cyan (from user image)
                        Color(0xFF6366F1), // Vibrant Indigo
                        Color(0xFFC084FC), // Lavender / Light Purple
                        Color(0xFFF472B6)  // Coral Pink
                    )
                },
                start = Offset(0f, 0f),
                end = Offset(width, height)
            )
            drawRect(brush = baseGradient)
            
            // 2. Draw Wave 1 (Upper Wave - flowing from top-left diagonal sweeping down)
            val wave1Path = Path().apply {
                moveTo(0f, height * 0.15f)
                cubicTo(
                    width * 0.35f, height * 0.05f,
                    width * 0.65f, height * 0.35f,
                    width, height * 0.22f
                )
                lineTo(width, 0f)
                lineTo(0f, 0f)
                close()
            }
            val wave1Brush = Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color(0xFF38BDF8).copy(alpha = 0.02f)
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = 0.35f),
                        Color(0xFF38BDF8).copy(alpha = 0.1f)
                    )
                }
            )
            drawPath(path = wave1Path, brush = wave1Brush)
            
            // Wave 1 Edge Highlight stroke (creates shiny liquid glass reflection)
            val wave1Edge = Path().apply {
                moveTo(0f, height * 0.15f)
                cubicTo(
                    width * 0.35f, height * 0.05f,
                    width * 0.65f, height * 0.35f,
                    width, height * 0.22f
                )
            }
            drawPath(
                path = wave1Edge,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.15f),
                        Color.White.copy(alpha = 0.8f),
                        Color.White.copy(alpha = 0.15f)
                    )
                ),
                style = Stroke(width = 2.dp.toPx())
            )
            
            // 3. Draw Wave 2 (Middle/Main sweeping wave curving downwards)
            val wave2Path = Path().apply {
                moveTo(0f, height * 0.45f)
                cubicTo(
                    width * 0.3f, height * 0.3f,
                    width * 0.7f, height * 0.7f,
                    width, height * 0.55f
                )
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            val wave2Brush = Brush.linearGradient(
                colors = if (isDark) {
                    listOf(
                        Color(0x2238BDF8),
                        Color(0x0A0F172A)
                    )
                } else {
                    listOf(
                        Color(0xFF60A5FA).copy(alpha = 0.45f), // Rich Sky Blue
                        Color(0xFF818CF8).copy(alpha = 0.25f)  // Semi-transparent indigo
                    )
                },
                start = Offset(0f, height * 0.45f),
                end = Offset(width, height)
            )
            drawPath(path = wave2Path, brush = wave2Brush)
            
            // Wave 2 Edge Highlight
            val wave2Edge = Path().apply {
                moveTo(0f, height * 0.45f)
                cubicTo(
                    width * 0.3f, height * 0.3f,
                    width * 0.7f, height * 0.7f,
                    width, height * 0.55f
                )
            }
            drawPath(
                path = wave2Edge,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.1f),
                        Color.White.copy(alpha = 0.75f),
                        Color.White.copy(alpha = 0.1f)
                    )
                ),
                style = Stroke(width = 2.5.dp.toPx())
            )
            
            // 4. Draw Wave 3 (Lower Wave with rich magenta/pink flow at the bottom)
            val wave3Path = Path().apply {
                moveTo(0f, height * 0.78f)
                cubicTo(
                    width * 0.4f, height * 0.62f,
                    width * 0.65f, height * 0.95f,
                    width, height * 0.72f
                )
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            val wave3Brush = Brush.linearGradient(
                colors = if (isDark) {
                    listOf(
                        Color(0x33EC4899),
                        Color(0x051E1B4B)
                    )
                } else {
                    listOf(
                        Color(0xFFF472B6).copy(alpha = 0.55f), // Vibrant Pink
                        Color(0xFFC084FC).copy(alpha = 0.3f)   // Soft purple
                    )
                },
                start = Offset(0f, height * 0.78f),
                end = Offset(width, height)
            )
            drawPath(path = wave3Path, brush = wave3Brush)
            
            // Wave 3 Edge Highlight
            val wave3Edge = Path().apply {
                moveTo(0f, height * 0.78f)
                cubicTo(
                    width * 0.4f, height * 0.62f,
                    width * 0.65f, height * 0.95f,
                    width, height * 0.72f
                )
            }
            drawPath(
                path = wave3Edge,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.2f),
                        Color.White.copy(alpha = 0.85f),
                        Color.White.copy(alpha = 0.2f)
                    )
                ),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}
