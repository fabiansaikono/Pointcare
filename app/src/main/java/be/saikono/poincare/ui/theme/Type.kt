package be.saikono.poincare.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val RobotoFamily = FontFamily.SansSerif

val Typography = Typography(
    displayLarge = Typography().displayLarge.copy(fontFamily = RobotoFamily),
    displayMedium = Typography().displayMedium.copy(fontFamily = RobotoFamily),
    displaySmall = Typography().displaySmall.copy(fontFamily = RobotoFamily),
    headlineLarge = Typography().headlineLarge.copy(fontFamily = RobotoFamily),
    headlineMedium = Typography().headlineMedium.copy(fontFamily = RobotoFamily),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = RobotoFamily),
    titleLarge = Typography().titleLarge.copy(fontFamily = RobotoFamily),
    titleMedium = Typography().titleMedium.copy(fontFamily = RobotoFamily),
    titleSmall = Typography().titleSmall.copy(fontFamily = RobotoFamily),
    bodyLarge = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = RobotoFamily),
    bodySmall = Typography().bodySmall.copy(fontFamily = RobotoFamily),
    labelLarge = Typography().labelLarge.copy(fontFamily = RobotoFamily),
    labelMedium = Typography().labelMedium.copy(fontFamily = RobotoFamily),
    labelSmall = Typography().labelSmall.copy(fontFamily = RobotoFamily)
)
