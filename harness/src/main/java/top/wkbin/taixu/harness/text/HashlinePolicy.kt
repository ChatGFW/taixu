package top.wkbin.taixu.harness.text

/**
 * Hashline 运行时闸门（见 docs/HASHLINE_PROTOCOL.md §9）。
 * 默认：挂锚 + 允许 ops；legacy oldText **默认关闭**（设置可手动开启）。
 */
data class HashlinePolicy(
    val readAnchorsDefault: Boolean = true,
    val editOpsEnabled: Boolean = true,
    val legacyEditEnabled: Boolean = false,
) {
    companion object {
        val DEFAULT = HashlinePolicy()
    }
}
