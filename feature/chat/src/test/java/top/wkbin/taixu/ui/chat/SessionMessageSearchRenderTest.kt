package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.wkbin.taixu.core.database.MessageSearchHitRow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h600dp-mdpi", manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionMessageSearchResultsRenderTest : ModelFlowRenderSupport() {
    @Test fun `literal matching is highlighted and result opens the correct session`() {
        var session: String? = null
        val excerpt = "前".repeat(16) + "100%_匹配 中文内容"
        render("message-search-dark-large", dark = true, scale = 1.3f, width = 360, height = 600, content = {
            SessionMessageSearchContent(SessionMessageSearchState("100%_", hits = listOf(
                MessageSearchHitRow("session-id", "会话搜索结果", "message-id", false, 1, excerpt))),
                onSwitch = { session = it }, onRetry = {})
        }) { root ->
            val text = nodes(root).flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
                .first { it.text == excerpt }
            assertTrue(text.spanStyles.any { it.start == 16 && it.end == 21 })
            val row = nodes(root).first { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "会话搜索结果" } &&
                    node.config.getOrNull(SemanticsActions.OnClick) != null
            }
            assertTrue(row.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            assertEquals("session-id", session)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h700dp-mdpi", manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionMessageSearchStatesRenderTest : ModelFlowRenderSupport() {
    @Test fun `loading empty and error states are distinct and retry is actionable`() {
        var retried = false
        render("message-search-states", content = {
            Column {
                SessionMessageSearchContent(SessionMessageSearchState("loading", loading = true), {}, {})
                SessionMessageSearchContent(SessionMessageSearchState("empty"), {}, {})
                SessionMessageSearchContent(SessionMessageSearchState("failure", failed = true), {}, { retried = true })
            }
        }) { root ->
            assertTrue(hasText(root, "正在搜索消息"))
            assertTrue(hasText(root, "没有找到匹配消息"))
            assertTrue(hasText(root, "消息搜索失败"))
            click(root, "重试")
            assertTrue(retried)
        }
    }
}
