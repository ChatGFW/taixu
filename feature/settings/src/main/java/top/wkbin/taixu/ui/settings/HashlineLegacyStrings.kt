package top.wkbin.taixu.ui.settings

import androidx.annotation.StringRes
import top.wkbin.taixu.feature.settings.R

/** Hashline 设置文案的旧字符串桥。先于总表匹配，避免把整段 when 留在 [SettingsLocalization]。 */
@StringRes
internal fun hashlineLegacyString(source: String): Int? = when (source) {
    "Hashline 编辑" -> R.string.settings_text_0351
    "read 默认挂行锚" -> R.string.settings_text_0352
    "关闭后 read 默认不再输出 n:h| 前缀；工具参数仍可显式传 anchors=true" -> R.string.settings_text_0353
    "允许 Hashline ops 编辑" -> R.string.settings_text_0354
    "关闭后 edit 拒绝 ops，只能用 oldText/newText" -> R.string.settings_text_0355
    "允许 legacy oldText 编辑" -> R.string.settings_text_0356
    "关闭后只能用锚点 ops；观测期默认保持开启" -> R.string.settings_text_0357
    "默认关闭；开启后才接受 oldText/newText（兼容弱模型）" -> R.string.settings_text_0363
    "累计编辑计数（跨启动保留）" -> R.string.settings_text_0358
    "刷新" -> R.string.settings_text_0359
    "清零" -> R.string.settings_text_0360
    "观测提示：ops 已较稳且 legacy 仍有失败，可考虑关闭「允许 legacy oldText 编辑」" -> R.string.settings_text_0361
    "一键关闭 legacy 编辑" -> R.string.settings_text_0362
    "观测提示：模型仍大量提交 oldText 被拒，且 ops 尚未明显跑通，可临时开启「允许 legacy oldText 编辑」" -> R.string.settings_text_0364
    "一键临时开启 legacy 编辑" -> R.string.settings_text_0365
    else -> null
}
