package com.ounben.amaradio.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ounben.amaradio.Utils
import com.ounben.amaradio.utils.UiScaler

// ─────────────────────────────────────────────────────────────
//  Blue palette: ocean-blue brand + blue-tinted neutrals.
//  AmaradioAmber keeps its legacy name (≈120 call sites) but is the brand blue.
// ─────────────────────────────────────────────────────────────

val AmaradioAmber = Color(0xFF3D9BFF)        // brand blue (readable on dark surfaces)
val BrandBlue = Color(0xFF1677F2)            // brand blue (light theme / filled buttons)
val BrandBlueDeep = Color(0xFF0A5CDC)
val BrandBlueSky = Color(0xFF58BEFF)
val SolidRedError = Color(0xFFFF5A52)
val SolidRedErrorLight = Color(0xFFE5352B)

// ── Light palette ───────────────────────────────────────────
val LightBackground = Color(0xFFF1F5FB)        // cool, faintly blue canvas
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE6EDF8)
val LightOnBackground = Color(0xFF0B1B33)
val LightOnSurfaceVariant = Color(0xFF55657F)
val LightOutline = Color(0xFFD5DEEC)

// ── Dark palette (night / car) — deep navy, never pure black ──
val DarkBackground = Color(0xFF0B1220)
val DarkSurface = Color(0xFF151E31)
val DarkSurfaceVariant = Color(0xFF1F2A41)
val DarkOnBackground = Color(0xFFF2F6FF)
val DarkOnSurfaceVariant = Color(0xFF9AA8C2)
val DarkOutline = Color(0xFF2A3753)

// ── Shape system ────────────────────────────────────────────
val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
)

// ── Typography system (new — gives clear weight hierarchy) ──
val AppTypography = Typography(
    headlineLarge = androidx.compose.material3.Typography().headlineLarge.copy(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
    ),
    headlineMedium = androidx.compose.material3.Typography().headlineMedium.copy(
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
    ),
    titleLarge = androidx.compose.material3.Typography().titleLarge.copy(
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
    ),
    titleMedium = androidx.compose.material3.Typography().titleMedium.copy(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
    ),
    titleSmall = androidx.compose.material3.Typography().titleSmall.copy(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
    ),
    bodyLarge = androidx.compose.material3.Typography().bodyLarge.copy(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
    ),
    bodyMedium = androidx.compose.material3.Typography().bodyMedium.copy(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
    ),
    labelMedium = androidx.compose.material3.Typography().labelMedium.copy(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
    ),
    labelSmall = androidx.compose.material3.Typography().labelSmall.copy(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
    )
)

@Composable
fun AMARadioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val useDarkTheme = when (Utils.getTheme(context)) {
        "dark" -> true
        "light" -> false
        else -> darkTheme
    }

    val colorScheme = if (useDarkTheme) {
        darkColorScheme(
            primary = AmaradioAmber,
            onPrimary = Color.White,
            primaryContainer = Color(0xFF123A73),
            onPrimaryContainer = Color(0xFFCFE4FF),
            secondary = DarkSurfaceVariant,
            onSecondary = Color(0xFFD7E2F7),
            secondaryContainer = Color(0xFF1B2E52),
            onSecondaryContainer = Color(0xFFCFE4FF),
            tertiary = BrandBlueSky,
            onTertiary = Color(0xFF00213F),
            tertiaryContainer = Color(0xFF123A73),
            onTertiaryContainer = Color(0xFFCFE4FF),
            error = SolidRedError,
            onError = Color.White,
            errorContainer = Color(0xFF5C1A1A),
            onErrorContainer = Color(0xFFFFB4A8),
            background = DarkBackground,
            onBackground = DarkOnBackground,
            surface = DarkSurface,
            onSurface = DarkOnBackground,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkOnSurfaceVariant,
            surfaceContainer = DarkSurface,
            surfaceContainerHigh = Color(0xFF1B2640),
            surfaceContainerHighest = DarkSurfaceVariant,
            outline = DarkOutline,
            outlineVariant = DarkOutline,
            surfaceTint = Color.Transparent
        )
    } else {
        lightColorScheme(
            primary = BrandBlue,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDCEBFF),
            onPrimaryContainer = Color(0xFF0A3A7A),
            secondary = LightSurfaceVariant,
            onSecondary = Color(0xFF2B3A55),
            secondaryContainer = Color(0xFFDCEBFF),
            onSecondaryContainer = Color(0xFF0A3A7A),
            tertiary = BrandBlueDeep,
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFDCEBFF),
            onTertiaryContainer = Color(0xFF0A3A7A),
            error = SolidRedErrorLight,
            onError = Color.White,
            errorContainer = Color(0xFFFFE0DD),
            onErrorContainer = Color(0xFF5C1A1A),
            background = LightBackground,
            onBackground = LightOnBackground,
            surface = LightSurface,
            onSurface = LightOnBackground,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightOnSurfaceVariant,
            surfaceContainer = LightSurface,
            surfaceContainerHigh = Color(0xFFEAF0FA),
            surfaceContainerHighest = LightSurfaceVariant,
            outline = LightOutline,
            outlineVariant = LightOutline,
            surfaceTint = Color.Transparent
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !useDarkTheme
                isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    // Observe car-mode so the whole UI rescales live when Bluetooth connects
    val isCarMode by com.ounben.amaradio.CarModeManager.isCarMode.collectAsState()
    val scale = if (isCarMode) UiScaler.SCALE_CAR else UiScaler.getScaleFactor(context)
    val currentDensity = LocalDensity.current
    // The context wrapper already applied the user-preference scale to fontScale, so for
    // the preference path only density needs scaling; car mode is applied here exclusively.
    val prefScale = UiScaler.getPreferenceScaleFactor(context)
    val scaledDensity = Density(
        density = currentDensity.density * scale,
        fontScale = currentDensity.fontScale * (scale / prefScale)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = {
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    content = content
                )
            }
        }
    )
}
