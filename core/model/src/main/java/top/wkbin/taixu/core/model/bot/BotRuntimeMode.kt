package top.wkbin.taixu.core.model.bot

/** LOCAL: phone Harness executes group deliveries. EXTERNAL: only a bound runtime may claim them. */
enum class BotRuntimeMode {
    LOCAL,
    EXTERNAL,
    ;

    companion object {
        fun parse(raw: String?): BotRuntimeMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: LOCAL
    }
}
