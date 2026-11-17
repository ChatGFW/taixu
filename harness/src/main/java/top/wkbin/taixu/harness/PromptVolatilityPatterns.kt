package top.wkbin.taixu.harness

/**
 * 系统提示词「易变内容」探测（日期/时间/时间戳/电量）用的模式。
 *
 * 每次压缩都要扫一遍 prompt，正则不能在函数体内现编；
 * 单独成文件是为了不撑大已登记架构基线的 `ContextWindowPolicy.kt`。
 */
internal val ISO_DATE_REGEX = Regex("""\b20\d{2}[-/]\d{2}[-/]\d{2}\b""")

internal val CLOCK_TIME_REGEX = Regex("""\b\d{1,2}:\d{2}(:\d{2})?\b""")

internal val UNIX_TIMESTAMP_REGEX = Regex("""\b1[6-9]\d{8}\b|\b17\d{8}\b|\b1[6-9]\d{11}\b|\b17\d{11}\b""")

internal val CHINESE_DATE_REGEX = Regex("""\d{4}年\d{1,2}月\d{1,2}日""")

internal val BATTERY_PERCENT_REGEX = Regex("""(?:电量|battery)[^\n]{0,20}?\d{1,3}%""", RegexOption.IGNORE_CASE)