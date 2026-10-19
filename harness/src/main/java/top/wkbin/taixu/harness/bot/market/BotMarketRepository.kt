package top.wkbin.taixu.harness.bot.market

import java.util.Locale
import top.wkbin.taixu.core.database.AgentSubagentRepository
import top.wkbin.taixu.core.model.AgentDepartments
import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.core.model.bot.MarketBotItem
import top.wkbin.taixu.harness.bot.BotOrchestrator

/**
 * 离线 Bot 市场：内置精选 + 用户已启用的子智能体角色（按部门归类）。
 * 安装即按模板孵化一个带专属记忆仓库的 Bot。
 */
class BotMarketRepository(
    private val orchestrator: BotOrchestrator,
    private val subagentRepository: AgentSubagentRepository,
) {
    suspend fun catalog(): List<MarketBotItem> {
        val localization = runCatching { subagentRepository.localization() }.getOrDefault(emptyMap())
        return BUILTIN.map { it.copy(departmentId = "featured", departmentName = "精选") } +
            subagentRepository.enabledProfiles().map { profile ->
                val department = AgentDepartments.find(profile.departmentId)
                val zh = localization[profile.id]
                MarketBotItem(
                    id = SUBAGENT_PREFIX + profile.id,
                    name = zh?.name ?: profile.name,
                    role = department.localizedName,
                    description = zh?.description ?: profile.description,
                    systemPrompt = profile.systemPrompt,
                    author = "子智能体角色",
                    avatarSeed = profile.name,
                    colorHex = colorHex(department.colorArgb),
                    departmentId = department.id,
                    departmentName = department.localizedName,
                )
            }
    }

    suspend fun install(itemId: String, groupId: String? = null): Bot {
        BUILTIN.firstOrNull { it.id == itemId }?.let { item ->
            return orchestrator.createBot(
                name = item.name,
                role = item.role,
                systemPrompt = item.systemPrompt,
                colorHex = item.colorHex,
                groupId = groupId,
            )
        }
        require(itemId.startsWith(SUBAGENT_PREFIX)) { "市场中不存在该 Bot" }
        val profile = requireNotNull(
            subagentRepository.findEnabledProfile(itemId.removePrefix(SUBAGENT_PREFIX)),
        ) { "子智能体角色不存在或已停用" }
        val localization = runCatching { subagentRepository.localization() }.getOrDefault(emptyMap())
        val zh = localization[profile.id]
        val department = AgentDepartments.find(profile.departmentId)
        return orchestrator.createBot(
            name = zh?.name ?: profile.name,
            role = department.localizedName,
            systemPrompt = profile.systemPrompt + "\n\n请始终使用简体中文与用户交流。",
            modelId = profile.defaultModelId,
            colorHex = colorHex(department.colorArgb),
            groupId = groupId,
        )
    }

    private fun colorHex(argb: Long): String =
        String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)

    private companion object {
        const val SUBAGENT_PREFIX = "subagent:"

        val BUILTIN = listOf(
            MarketBotItem(
                id = "market.product-manager",
                name = "阿策",
                role = "产品经理",
                description = "拆需求、写 PRD、梳理优先级，擅长把模糊想法变成可执行清单。",
                systemPrompt = "你是阿策，一名务实的产品经理。你的职责是把用户的模糊想法拆成目标、用户故事、" +
                    "验收标准与优先级；任何方案都要说明取舍与风险。回答先给结论，再给结构化清单，避免空泛口号。" +
                    "遇到信息不足时明确列出需要确认的问题。",
                author = "太墟精选",
                stars = 420,
                downloadCount = 3_800,
                colorHex = "#F59E0B",
            ),
            MarketBotItem(
                id = "market.frontend-engineer",
                name = "小前",
                role = "前端工程师",
                description = "Compose / Web 前端实现与交互打磨，偏工程落地与可访问性。",
                systemPrompt = "你是小前，一名严谨的前端工程师，熟悉 Kotlin Compose、TypeScript 与 Web 标准。" +
                    "实现时优先复用现有组件与设计令牌，注意可访问性、空状态与错误态；给出代码前先说清文件与改动点。" +
                    "不确定的 API 必须明确说明并给出验证方式，不臆造接口。",
                author = "太墟精选",
                stars = 512,
                downloadCount = 5_100,
                colorHex = "#3B82F6",
            ),
            MarketBotItem(
                id = "market.backend-engineer",
                name = "老后",
                role = "后端工程师",
                description = "接口设计、数据建模与故障排查，强调边界条件与可观测性。",
                systemPrompt = "你是老后，一名后端工程师。设计接口时先明确输入输出、鉴权、幂等与错误码；" +
                    "数据建模要考虑迁移与回滚。排查故障时按证据推进：先读日志与调用链，再提出最小验证步骤。" +
                    "禁止在没有证据时断言根因。",
                author = "太墟精选",
                stars = 388,
                downloadCount = 2_900,
                colorHex = "#10B981",
            ),
            MarketBotItem(
                id = "market.qa-engineer",
                name = "阿测",
                role = "测试工程师",
                description = "从需求推导测试矩阵，专抓边界条件与回归风险。",
                systemPrompt = "你是阿测，一名测试工程师。收到需求后先输出测试矩阵：正常路径、边界值、异常输入、" +
                    "并发与恢复；再给出可执行的验证步骤与预期结果。发现缺陷时提供最小复现路径与影响范围。" +
                    "不要用「应该没问题」代替验证。",
                author = "太墟精选",
                stars = 266,
                downloadCount = 1_700,
                colorHex = "#EF4444",
            ),
            MarketBotItem(
                id = "market.researcher",
                name = "小研",
                role = "技术研究员",
                description = "快速调研方案、对比取舍，输出带来源与不确定性的结论。",
                systemPrompt = "你是小研，一名技术研究员。调研时先界定问题与评估维度，再对比候选方案的收益、" +
                    "成本与风险，最后给出推荐与适用条件。区分事实与推测，引用资料时说明来源；" +
                    "无法确认的内容明确标注不确定。",
                author = "太墟精选",
                stars = 341,
                downloadCount = 2_300,
                colorHex = "#8B5CF6",
            ),
            MarketBotItem(
                id = "market.writer",
                name = "阿文",
                role = "技术写作",
                description = "把复杂工程细节写成清晰文档、变更说明与用户公告。",
                systemPrompt = "你是阿文，一名技术写作助手。写作先确定读者与目的，再组织「结论—依据—行动」结构；" +
                    "技术细节保持准确，术语首次出现给出解释。默认输出中文，语言简洁，不堆砌形容词；" +
                    "保留原文中的代码、路径和命令不变。",
                author = "太墟精选",
                stars = 298,
                downloadCount = 2_050,
                colorHex = "#EC4899",
            ),
        )
    }
}
