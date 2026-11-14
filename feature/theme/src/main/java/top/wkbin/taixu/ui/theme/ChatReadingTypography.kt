package top.wkbin.taixu.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.ui.miuix.RuntimeTheme

/**
 * 聊天阅读面字阶：相对全局 [RuntimeTheme.typography] 略小、行距更松、不加粗，
 * 让长回复更接近轻松对话密度（避免正文「又密又粗」）。
 */
@Immutable
data class ChatReadingTypography(
    val body: TextStyle,
    /** 输入框正文/占位，与气泡阅读面同级，避免比消息更大。 */
    val composer: TextStyle,
    val heading1: TextStyle,
    val heading2: TextStyle,
    val heading3: TextStyle,
    val heading4: TextStyle,
)

@Composable
fun chatReadingTypography(): ChatReadingTypography {
    val base = RuntimeTheme.typography
    return remember(base) {
        val body = base.bodyMedium.copy(
            fontSize = 15.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
        )
        ChatReadingTypography(
            body = body,
            composer = body.copy(lineHeight = 21.sp),
            heading1 = base.titleMedium.copy(
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
            ),
            heading2 = base.titleSmall.copy(
                fontSize = 16.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
            ),
            heading3 = base.titleSmall.copy(
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            ),
            heading4 = base.bodyMedium.copy(
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            ),
        )
    }
}
