package top.wkbin.taixu.core.database

import java.io.File

/** Skill 目录是资源包边界；只有无入口的集合目录才继续发现下一级技能。 */
object SkillDirectoryDiscovery {
    /**
     * 扫描根下以此命名的一级目录视为「插件技能集合」：
     * 子 SKILL.md 只作资源，不单独入库（由内置导航 skill / 集合入口覆盖）。
     */
    val AUTO_COLLAPSED_COLLECTIONS = setOf("android-skills")

    fun promptFile(dir: File): File? = dir.listFiles().orEmpty()
        .filter { it.isFile && it.name.lowercase() in AgentSkillRepository.SKILL_PROMPT_FILE_NAMES }
        .let { files -> files.firstOrNull { it.name.equals("SKILL.md", ignoreCase = true) } ?: files.firstOrNull() }

    /** 返回各独立 Skill 的主入口，保留其下所有文档和脚本为子资源。 */
    fun discover(root: File): List<File> = root.walkTopDown()
        .maxDepth(6)
        .onEnter { dir ->
            when {
                dir == root -> true
                // 插件集合整树跳过：不把 36 个子技能平铺进技能列表。
                dir.parentFile == root && dir.name in AUTO_COLLAPSED_COLLECTIONS -> false
                else -> dir.parentFile?.let(::promptFile) == null
            }
        }
        .filter { it.isDirectory }
        .mapNotNull(::promptFile)
        .toList()
}
