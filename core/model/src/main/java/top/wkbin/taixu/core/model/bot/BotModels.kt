package top.wkbin.taixu.core.model.bot

import kotlinx.serialization.Serializable

/** 持久化 PersonaBot：每个 Bot 绑定一个独占会话与一棵 Git 记忆仓库。 */
@Serializable
data class Bot(
    val id: String,
    val name: String,
    val role: String,
    val systemPrompt: String,
    /** 默认图案种子、版本化像素角色配方，或内嵌 PNG data URI；均随 Bot 归档携带。 */
    val avatarSeed: String,
    val modelId: String? = null,
    val providerId: String? = null,
    val sessionId: String,
    val isMainBot: Boolean = false,
    val colorHex: String = "#3D5AFE",
    val createdAt: Long,
    val updatedAt: Long,
    /** 已保存供应商档案内的具体模型；null 使用该档案默认模型。 */
    val modelVariant: String? = null,
)

@Serializable
data class GroupChat(
    val id: String,
    val name: String,
    val adminBotId: String,
    val description: String = "",
    val createdAt: Long,
    val members: List<Bot> = emptyList(),
    /** 群聊聊天背景风格：default / warm / mint / night。 */
    val background: String = "default",
)

@Serializable
enum class GroupSenderType {
    USER,
    BOT,
    SYSTEM,
}

@Serializable
data class GroupAttachment(
    val name: String,
    val uri: String,
    val mimeType: String = "",
    val sizeBytes: Long = 0,
)

@Serializable
data class GroupReaction(
    val emoji: String,
    val reactorId: String,
    val reactorName: String,
)

/** 群聊气泡里的工具步骤；success 为 null 表示仍在执行。 */
@Serializable
data class GroupToolStep(
    val id: String,
    val name: String,
    val success: Boolean? = null,
    val durationMs: Long? = null,
)

@Serializable
data class GroupMessage(
    val id: String,
    val groupId: String,
    val senderId: String,
    val senderName: String,
    val senderType: GroupSenderType,
    val content: String,
    val timestamp: Long,
    val isStreaming: Boolean = false,
    val delegatedTaskId: String? = null,
    val attachments: List<GroupAttachment> = emptyList(),
    val reactions: List<GroupReaction> = emptyList(),
    val steps: List<GroupToolStep> = emptyList(),
    val reasoning: String = "",
)

@Serializable
data class MemoryCommit(
    val hash: String,
    val shortHash: String,
    val message: String,
    val author: String,
    val timestamp: Long,
    val filesChanged: List<String> = emptyList(),
)

@Serializable
data class BotMemoryFile(
    val path: String,
    val sizeBytes: Long,
)

/** Bot 作者登记的标准导出清单。 */
@Serializable
data class BotManifest(
    val version: Int = 1,
    val name: String,
    val role: String,
    val systemPrompt: String,
    val modelId: String? = null,
    val avatarSeed: String? = null,
    val colorHex: String = "#3D5AFE",
    val modelVariant: String? = null,
)

/** 离线精选市场条目；本期不依赖远端服务。 */
@Serializable
data class MarketBotItem(
    val id: String,
    val name: String,
    val role: String,
    val description: String,
    val systemPrompt: String,
    val author: String,
    val stars: Int = 0,
    val downloadCount: Int = 0,
    val avatarSeed: String? = null,
    val colorHex: String = "#3D5AFE",
    /** 子智能体角色来源的部门；内置精选为 null。 */
    val departmentId: String? = null,
    val departmentName: String? = null,
)

/** Bot 自主登记的定时任务。nextRunAt 由 Cron 解析结果维护。 */
@Serializable
data class BotSchedule(
    val id: String,
    val botId: String,
    val groupId: String? = null,
    val cron: String,
    val instruction: String,
    val enabled: Boolean = true,
    val nextRunAt: Long,
    val lastRunAt: Long? = null,
    val createdAt: Long,
)
