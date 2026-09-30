package top.wkbin.taixu.feature.a2uipoc

import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.model.catalog.functions.A2uiLocaleProvider
import androidx.compose.material3.a2ui.A2uiSurface
import androidx.compose.material3.a2ui.catalog.materialA2uiBasicCatalogV1
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray

/**
 * 太墟 A2UI 渲染器包装：把 androidx.a2ui 官方渲染器收敛为单例入口。
 *
 * - Catalog 使用官方 Material3 Basic Catalog（Text/Row/Column/Card/Button/Tabs 等），
 *   catalogId 与 harness 契约（A2uiSurfaceContract.CATALOG_ID，即官方 basic catalog.json）
 *   保持一致：智能体只能使用目录内声明的组件，与工具白名单同一套安全哲学；
 * - processor 单例持有全部活动 surface：聊天流滚动导致组合销毁重建时，
 *   界面状态不丢失（滚动回来即恢复）。
 *
 * 注意：A2UI 库当前为 1.0.0-alpha01，API 可能随版本变动；本文件是唯一的对接点，
 * 升级库版本时只需调整这里（关键符号：materialA2uiBasicCatalogV1 /
 * A2uiMessageProcessor / A2uiMessageParser / A2uiSurface）。
 */
object TaiXuA2uiRenderer {

    /**
     * 官方 Basic Catalog + 太墟占位媒体组件：image/video/audioPlayer 与
     * urlOpener/messageFormatter 官方不提供默认实现（媒体渲染与出链策略留给宿主），
     * 其余组件走官方默认（Text/Row/Column/Card/Button/Tabs 等）。
     */
    private val processor = A2uiMessageProcessor(
        catalogs = listOf(
            materialA2uiBasicCatalogV1(
                image = TaiXuImageComponent(),
                video = TaiXuVideoComponent(),
                audioPlayer = TaiXuAudioPlayerComponent(),
                urlOpener = TaiXuUrlOpener,
                messageFormatter = TaiXuMessageFormatter,
                localeProvider = A2uiLocaleProvider.Default,
            ),
        ),
    )

    private val parser = A2uiMessageParser()

    /**
     * 逐条投喂 A2UI 协议消息（JSON Lines 数组字符串）：
     * 每条先经 parser.parse 反序列化为协议对象，再交给 processor.processMessage。
     * 返回 null 表示全部消息受理成功，否则返回错误文案（可直接展示给用户）。
     */
    fun processMessages(messagesJson: String): String? = runCatching {
        Json.parseToJsonElement(messagesJson).jsonArray.forEach { element ->
            processor.processMessage(parser.parse(element.toString()))
        }
    }.fold(
        onSuccess = { null },
        onFailure = { it.message?.let { msg -> "A2UI 消息处理失败：$msg" } ?: "A2UI 消息处理失败" },
    )

    /** 渲染指定 surface；尚未收到该 surface 的组件数据时返回 false（调用方可回退展示原文）。 */
    @Composable
    fun SurfaceView(surfaceId: String, modifier: Modifier = Modifier): Boolean {
        val surfaces by processor.activeSurfaces.collectAsState()
        val surface = surfaces.firstOrNull { it.id == surfaceId } ?: return false
        A2uiSurface(surfaceModel = surface, modifier = modifier)
        return true
    }
}
