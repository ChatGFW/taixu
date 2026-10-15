package top.wkbin.taixu.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val AppSans = FontFamily.SansSerif

val AppTypography = Typography(
    displaySmall = Typography().displaySmall.copy(
        fontFamily = AppSans,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    headlineLarge = Typography().headlineLarge.copy(
        fontFamily = AppSans,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    headlineMedium = Typography().headlineMedium.copy(
        fontFamily = AppSans,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    headlineSmall = Typography().headlineSmall.copy(
        fontFamily = AppSans,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    titleLarge = Typography().titleLarge.copy(
        fontFamily = AppSans,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    titleMedium = Typography().titleMedium.copy(
        fontFamily = AppSans,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = Typography().titleSmall.copy(
        fontFamily = AppSans,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = Typography().bodyLarge.copy(
        fontFamily = AppSans,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = Typography().bodyMedium.copy(
        fontFamily = AppSans,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = Typography().bodySmall.copy(
        fontFamily = AppSans,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = Typography().labelLarge.copy(
        fontFamily = AppSans,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = Typography().labelMedium.copy(
        fontFamily = AppSans,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = Typography().labelSmall.copy(
        fontFamily = AppSans,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        // 字重减负：M3 规范 labelSmall 为 Medium，避免全 App 小标签默认加粗
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
    ),
)
