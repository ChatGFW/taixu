package top.wkbin.taixu.core.model

/**
 * 🔢 工具版本比较与状态判定 —— 纯 Kotlin 逻辑，无 Android / 无沙箱依赖，可直接单测。
 */
object ToolchainVersion {

    /**
     * 从任意工具输出中抽取第一个版本号。
     *
     * 覆盖常见形态：
     *  - `cmake version 3.22.1`→ `3.22.1`
     *  - `jadx 1.5.6`            → `1.5.6`
     *  - `apktool 2.9.3`          → `2.9.3`
     *  - `v22.14.0`              → `22.14.0`
     *  - `ninja 1.11.1`           → `1.11.1`
     *  - `readelf (GNU Binutils) 2.42` → `2.42`
     *
     * 抽取不到时返回 null —— 调用方应据此给 UNKNOWN，绝不臆造版本号。
     */
    fun extractVersion(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        // 匹配整段 x.y(.z...)，避免把 "GNU Binutils for aarch64" 里的杂散数字当成版本。
        // 注意必须取 match.value 整体：若正则里用捕获组只取 group(1) 只会拿到首段数字
        //（"3.22.1" 会变成 "3"），从而把新版本误判成版本过低。
        val match = Regex("""\d+\.\d+(?:\.\d+)*""").find(raw)
        return match?.value
    }

    /**
     * 比较两个版本号（点分数字）。
     *
     * @return >0 表示 [actual] 更新，<0 表示落后，0 表示相同。
     * 段数不同时按 0 补齐，因此 `4.3` 与 `4.3.4` 视为相同。
     * 存在无法解析的段时返回 null，表示「判不了」，由调用方降级为 UNKNOWN。
     */
    fun compare(actual: String?, minimum: String?): Int? {
        if (minimum.isNullOrBlank()) return null
        val actualParts = actual?.takeIf { it.isNotBlank() }?.split('.') ?: return null
        val minParts = minimum.split('.')
        val size = maxOf(actualParts.size, minParts.size)
        for (i in 0 until size) {
            // 段位缺失按 0 补齐（"3.22" 与 "3.22.0" 应视为相同）；
            // 但「存在却解析不出数字」要判不了，返回 null 交由上层降级为 UNKNOWN。
            val a = actualParts.getOrNull(i)?.trim()?.toIntOrNull() ?: if (i < actualParts.size) return null else 0
            val b = minParts.getOrNull(i)?.trim()?.toIntOrNull() ?: if (i < minParts.size) return null else 0
            if (a != b) return if (a > b) 1 else -1
        }
        return 0
    }

    /**
     * 判定单个工具的状态。
     *
     * 判定顺序（与文档口径严格一致）：
     *  1. 探测不可达（[pathFound] 与 [resolvedPath] 都拿不到、且 [reachable] 为 false）→ UNKNOWN，
     *     绝不把「沙箱正忙导致探测超时」误报成「工具缺失」。
     *  2. 未找到可执行文件 → MISSING。
     *  3. 找到了但版本解析不出来，且清单设了最低版本 → UNKNOWN（如实说版本读不到）。
     *  4. 有版本但低于最低版本 → OUTDATED。
     *  5. 其余 → READY。
     *
     * [requiresRootAtRuntime] 的工具额外说明：即便 READY 也要在面板标注 root 依赖，
     * 由 UI 层通过 [ToolchainProbeResult.status] 与探针字段共同表达，不在此处降级状态。
     */
    fun resolveStatus(
        probe: ToolchainProbe,
        resolvedPath: String?,
        version: String?,
        reachable: Boolean = true,
    ): ToolchainStatus {
        if (!reachable) return ToolchainStatus.UNKNOWN
        if (resolvedPath.isNullOrBlank()) return ToolchainStatus.MISSING

        val minimum = probe.minVersion
        if (minimum.isNullOrBlank()) return ToolchainStatus.READY

        if (version.isNullOrBlank()) return ToolchainStatus.UNKNOWN

        val cmp = compare(version, minimum)
        // 判不了版本关系时如实给 UNKNOWN，不武断地判成「已就绪」或「需升级」
        return when {
            cmp == null -> ToolchainStatus.UNKNOWN
            cmp < 0 -> ToolchainStatus.OUTDATED
            else -> ToolchainStatus.READY
        }
    }

    /**
     * 生成面向用户的中文结论。
     *
     * root 依赖工具统一带上明确后缀：「工具已就绪，但实际驱动需要 root 权限」——
     * 让用户一眼看出装好了 ≠ 能用，避免后续踩坑。
     */
    fun describe(probe: ToolchainProbe, resolvedPath: String?, version: String?, status: ToolchainStatus): String {
        val rootNote = if (probe.requiresRootAtRuntime) "（工具已就绪，但实际驱动需要 root 权限）" else ""
        return when (status) {
            ToolchainStatus.READY ->
                "已就绪${version?.let { " v$it" } ?: ""}$rootNote"
            ToolchainStatus.OUTDATED ->
                "版本过低${version?.let { " v$it" } ?: ""}，建议升级到 ${probe.minVersion} 及以上"
            ToolchainStatus.MISSING ->
                "未安装${rootNote}"
            ToolchainStatus.NEEDS_ROOT ->
                "需要 root 权限才能使用"
            ToolchainStatus.UNKNOWN ->
                if (resolvedPath.isNullOrBlank()) "沙箱正忙，暂时无法确认" else "已找到但版本信息读取失败"
        }
    }
}
