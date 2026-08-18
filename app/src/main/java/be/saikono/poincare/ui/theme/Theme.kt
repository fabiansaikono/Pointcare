package be.saikono.poincare.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DeepSpaceBlue,
    secondary = PlanetGold,
    tertiary = PlanetRed,
    background = DeepSpaceBlue,
    surface = CockpitGray,
    onPrimary = StarWhite,
    onSecondary = StarWhite,
    onTertiary = StarWhite,
    onBackground = StarWhite,
    onSurface = StarWhite,
)

private val LightColorScheme = lightColorScheme(
    primary = DeepSpaceBlue,
    secondary = PlanetGold,
    tertiary = PlanetRed,
    background = StarWhite,
    surface = StarWhite,
    onPrimary = DeepSpaceBlue,
    onSecondary = DeepSpaceBlue,
    onTertiary = DeepSpaceBlue,
    onBackground = DeepSpaceBlue,
    onSurface = DeepSpaceBlue,

    /* Other default colors to override
    PlanetBlue
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun PoincareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
