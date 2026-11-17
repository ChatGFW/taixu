package top.wkbin.taixu.ui.settings

/**
 * `resolveLegacyString` 的历史文案匹配模式。
 *
 * 这些 `Regex` 原先直接写在 @Composable 里：每渲染一段文本都要重新编译十来个正则，
 * 而且发生在重组热路径上。模式本身是编译期常量（`Regex` 不能用 `const val`），
 * 提到文件级复用即可；单独成文件是为了不撑大已登记架构基线的 `SettingsLocalization.kt`。
 *
 * 注意 raw 字符串不做转义：这里必须写 `\d`，写成 `\\d` 会匹配字面 "\d" 导致永不命中。
 */
internal val legacySystemModeRegex = Regex("""(\d+) 套系统 · (.+)""")

internal val legacySystemCountRegex = Regex("""(\d+) 套系统""")

internal val legacyModelSkillCountRegex = Regex("""(\d+) 个模型 · (\d+) 技能""")

internal val legacyModelCountRegex = Regex("""(\d+) 个模型""")

internal val legacySkillCountRegex = Regex("""(\d+) 个技能""")

internal val legacyItemCountRegex = Regex("""(\d+) 个""")

internal val legacyRoundCountRegex = Regex("""(\d+) 轮""")

internal val legacyDownloadProgressRegex = Regex("""下载中：(.+) / (.+) MB""")

internal val legacyDownloadedRegex = Regex("""已下载：(.+) MB""")

internal val legacyToolDiscoveredRegex = Regex("""成功探测到 (\d+) 个工具""")

internal val legacyRoundBudgetRegex = Regex("""轮次用尽时先让模型收束并记录进度，再自动续跑，无需用户点击继续；总预算上限 (\d+) 轮""")

internal val legacyTimesRegex = Regex("""(\d+) 次""")