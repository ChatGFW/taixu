package top.wkbin.taixu.ui.chat

import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.wkbin.taixu.core.model.ChatTopToolbarAction
import top.wkbin.taixu.core.model.RunMode
import top.wkbin.taixu.harness.session.ConversationBranch
import top.wkbin.taixu.harness.session.ConversationBranchKind
import top.wkbin.taixu.ui.theme.TaiXuTheme

/**
 * 手机宽度下量「更多」抽屉：双列横排小块必须明显矮于旧的纵向大卡片，
 * 入口文案和点击仍在。无头测量，不代表真机手势。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h640dp-mdpi", manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkbenchSheetDensityTest {
    @Test fun defaultSheetStaysShortEnoughForASmallPhone() {
        var runModes = 0
        measure(scale = 1f, content = { sheet(onRunMode = { runModes += 1 }) }) { sheet ->
            // 旧双列大卡片（约 100dp × 4 行）加上会话行会超过这块 640dp 屏的抽屉可视区。
            assertTrue("default sheet height ${sheet.height}px", sheet.height in 280..560)
            assertTrue(sheet.texts.contains("运行意图"))
            assertTrue(sheet.texts.contains("会话分支"))
            assertTrue(sheet.texts.contains("浏览器"))
            assertTrue(sheet.texts.contains("会话工作记忆"))
            click(sheet.root, "运行意图")
            assertEquals(1, runModes)
        }
    }

    @Test fun actionGridIsShorterThanTheOldStackedCards() {
        measure(scale = 1f, content = { controls() }) { grid ->
            // 7 个动作、双列、横排：4 行。旧卡片单行最低约 96dp，四行会超过 380px。
            assertTrue("action grid height ${grid.height}px", grid.height in 140..280)
        }
    }

    @Test fun largeFontFallsBackToOneColumnWithoutRestoringTallCards() {
        measure(scale = 1.5f, content = { controls() }) { grid ->
            assertTrue("large-font action grid height ${grid.height}px", grid.height in 280..560)
            assertTrue(grid.texts.contains("悬浮小窗"))
        }
    }

    @Composable
    private fun controls(onRunMode: () -> Unit = {}) = ChatWorkbenchControls(
        runMode = RunMode.BUILD,
        branch = branch,
        events = emptyList(),
        running = false,
        onRunMode = onRunMode,
        onBranches = {},
        onRuntime = {},
        onTools = {},
        toolchainHighlight = false,
        onReasoning = {},
        onFloatingChat = {},
        speech = ChatSpeechToggle(enabled = false, onToggle = {}),
    )

    @Composable
    private fun sheet(onRunMode: () -> Unit) {
        ChatWorkbenchSheetBody(
            pinned = ChatTopToolbarAction.DEFAULT_PINNED.toSet(),
            companion = ChatCompanionToggle(open = false, onToggle = {}),
            speech = ChatSpeechToggle(enabled = false, onToggle = {}),
            browserHighlight = false,
            repositoryHighlight = false,
            toolchainHighlight = false,
            runMode = RunMode.BUILD,
            branch = branch,
            events = emptyList(),
            running = false,
            onOpenBrowser = {},
            onOpenRepository = {},
            onOpenBots = {},
            onOpenWorkshop = {},
            onOpenMemory = {},
            onOpenOutline = {},
            onExportMarkdown = null,
            onOpenRunModes = onRunMode,
            onOpenBranches = {},
            onOpenRuntime = {},
            onOpenToolchain = {},
            onOpenReasoning = {},
            onFloatingChat = {},
            closeThen = { action -> action },
        )
    }

    private val branch = ConversationBranch(
        id = "main", name = "main", leafId = null, depth = 0, preview = "", updatedAt = 0,
        kind = ConversationBranchKind.MAIN, isCurrent = true, isBusy = false, faulted = false, toolCallCount = 0,
    )

    private fun measure(scale: Float, content: @Composable () -> Unit, check: (Measured) -> Unit) {
        RuntimeEnvironment.setFontScale(scale)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.get().apply { setTheme(android.R.style.Theme_Material_Light_NoActionBar) }
        Settings.Global.putFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        controller.setup()
        val height = mutableIntStateOf(0)
        try {
            activity.setContent {
                TaiXuTheme(darkTheme = false, dynamicColor = false) {
                    Column(Modifier.onSizeChanged { height.intValue = it.height }) { content() }
                }
            }
            val root = activity.window.decorView
            // 给 verticalScroll 一个有限上限；外层 Column 仍按内容收缩，onSizeChanged 才是正文高度。
            val viewport = 2400
            fun layout() {
                root.measure(
                    View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(viewport, View.MeasureSpec.EXACTLY),
                )
                root.layout(0, 0, 360, viewport)
            }
            layout()
            repeat(4) {
                Snapshot.sendApplyNotifications()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
                layout()
            }
            check(Measured(root, height.intValue, texts(root)))
        } finally {
            (activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0) as? ComposeView)
                ?.disposeComposition()
            controller.pause().stop().destroy()
        }
    }

    private fun texts(root: View): List<String> = nodes(root).flatMap { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    }

    private fun click(root: View, label: String) {
        val node = nodes(root).first { candidate ->
            candidate.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == label }
        }
        val clickable = generateSequence(node) { it.parent }.first { candidate ->
            candidate.config.getOrNull(SemanticsActions.OnClick) != null
        }
        assertTrue(requireNotNull(clickable.config.getOrNull(SemanticsActions.OnClick)?.action).invoke())
    }

    private fun nodes(root: View): List<SemanticsNode> {
        fun owner(view: View): ViewRootForTest? {
            if (view is ViewRootForTest) return view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) owner(view.getChildAt(index))?.let { return it }
            }
            return null
        }
        fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { walk(it) }
        return walk(requireNotNull(owner(root)).semanticsOwner.rootSemanticsNode)
    }

    private data class Measured(val root: View, val height: Int, val texts: List<String>)
}
