package top.wkbin.taixu.ui.chat

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.wkbin.taixu.ui.components.LocalMarkdownComponents
import top.wkbin.taixu.ui.components.MarkdownComponents

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h700dp-mdpi", manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownDefaultRendererRenderTest : ModelFlowRenderSupport() {
    @Test fun `existing native blocks render and copy literal code`() {
        val code = "println(\"ok\")"
        render("markdown-default", content = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                MarkdownText("# 标题😀\n\n普通 **粗体** 与 [链接](https://example.test)\n\n```kotlin\n$code\n```\n\n| 名称 | 状态 |\n| --- | --- |\n| 解析 | 正常 |\n\n> 引用内容\n\n- 第一项\n- 第二项")
            }
        }) { root ->
            for (label in listOf("标题😀", "普通", "链接", code, "名称", "正常", "引用内容", "第一项")) assertTrue(label, hasText(root, label))
            val copy = nodes(root).first { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.contains("复制") } &&
                    node.config.getOrNull(SemanticsActions.OnClick) != null
            }
            assertTrue(copy.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            val clipboard = root.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(code, clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h600dp-mdpi", manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownCustomRendererRenderTest : ModelFlowRenderSupport() {
    @Test fun `scoped native hooks receive unchanged blocks and stable media key`() {
        var media: Pair<String, String?>? = null
        val components = MarkdownComponents(
            codeBlock = { Text("代码插槽:${it.language}:${it.code}") },
            table = { Text("表格插槽:${it.headers.joinToString()}") },
            media = { block, key -> media = block.url to key; Text("图片插槽:${block.description}") },
        )
        render("markdown-custom-dark-large", dark = true, scale = 1.3f, width = 360, height = 600, content = {
            Column {
                CompositionLocalProvider(LocalMarkdownComponents provides components) {
                    MarkdownText("```kt\noriginal\n```\n\n| A | B |\n| --- | --- |\n| x | y |\n\n![本地图片](/workspace/a.png)", contentCacheKey = "fixture")
                }
                MarkdownText("```txt\ndefault renderer\n```")
            }
        }) { root ->
            assertTrue(hasText(root, "代码插槽:kt:original"))
            assertTrue(hasText(root, "表格插槽:A, B"))
            assertTrue(hasText(root, "图片插槽:本地图片"))
            assertTrue(hasText(root, "default renderer"))
            assertTrue(hasText(root, "复制"))
            assertEquals("/workspace/a.png" to "fixture:media:2", media)
        }
    }
}
