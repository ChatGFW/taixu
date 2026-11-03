package top.wkbin.taixu.harness.text

/**
 * HashlineEditStats ↔ DataStore JSON 编解码。
 * 手写固定键，避免依赖 Android `org.json`（JVM 单测未 mock）。
 */
object HashlineEditStatsCodec {
    private val KEYS = listOf(
        "ops_success", "ops_stale", "ops_other_failure", "ops_disabled",
        "legacy_success", "legacy_failure", "legacy_disabled",
    )

    fun encode(snapshot: Map<String, Int>): String =
        KEYS.joinToString(prefix = "{", postfix = "}") { key ->
            "\"$key\":${snapshot[key] ?: 0}"
        }

    fun decode(raw: String): Map<String, Int> {
        if (raw.isBlank()) return zeros()
        return runCatching {
            KEYS.associateWith { key ->
                val needle = "\"$key\""
                val idx = raw.indexOf(needle)
                if (idx < 0) return@associateWith 0
                var i = idx + needle.length
                while (i < raw.length && (raw[i] == ':' || raw[i].isWhitespace())) i++
                val start = i
                while (i < raw.length && (raw[i].isDigit() || raw[i] == '-')) i++
                raw.substring(start, i).toIntOrNull()?.coerceAtLeast(0) ?: 0
            }
        }.getOrElse { zeros() }
    }

    private fun zeros(): Map<String, Int> = KEYS.associateWith { 0 }
}
