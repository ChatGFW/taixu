package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.model.ApprovalMode
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeActionRow
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.core.model.McpServerConfig
import top.yukonga.miuix.kmp.basic.Text
import top.wkbin.taixu.ui.miuix.Scaffold
import top.wkbin.taixu.ui.theme.TaiXuTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h800dp-mdpi")
class MiuixDeepPageRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeApprovalPreferencePreservesItsSelectionCallback() {
        val mode = mutableStateOf(ApprovalMode.FULL_ACCESS)
        compose.setContent {
            TaiXuTheme(dynamicColor = false) {
                Scaffold {
                    RuntimeCard(contentPadding = PaddingValues()) {
                        ApprovalModeSelectorRow(mode.value) { mode.value = it }
                    }
                }
            }
        }
        compose.onNodeWithText("全局工具权限").performClick()
        compose.onNodeWithText("请求批准").performClick()
        compose.runOnIdle { assertEquals(ApprovalMode.REQUEST, mode.value) }
    }

    @Test fun phoneModelFormKeepsItsActionsReachableWithLargeText() {
        RuntimeEnvironment.setFontScale(1.3f)
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                TaiXuTheme(darkTheme = true, dynamicColor = false) {
                    PhoneAgentSettingsScreen(onBack = {}, viewModel = null)
                }
            }
        }
        compose.onNodeWithText("清除").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("已清除").performScrollTo().assertIsDisplayed()
    }

    @Test fun unequalActionLabelsStillHaveMatchingBoundsWithLargeText() {
        RuntimeEnvironment.setFontScale(1.3f)
        compose.setContent {
            TaiXuTheme(dynamicColor = false) {
                Column(Modifier.width(320.dp)) {
                    RuntimeActionRow {
                        RuntimeTextButton(onClick = {}) { Text("取消") }
                        RuntimeButton(onClick = {}) { Text("保存较长的配置") }
                    }
                }
            }
        }
        val cancel = compose.onNodeWithText("取消").fetchSemanticsNode().boundsInRoot
        val save = compose.onNodeWithText("保存较长的配置").fetchSemanticsNode().boundsInRoot
        assertEquals(cancel.width, save.width, 1f)
        assertEquals(cancel.height, save.height, 1f)
        assertEquals(cancel.top, save.top, 1f)
    }

    @Test fun mcpEditorCancelAndSaveAreEqualAndSaveStillWorks() {
        RuntimeEnvironment.setFontScale(1f)
        var saved = false
        compose.setContent {
            TaiXuTheme(dynamicColor = false) {
                McpServerEditorDialog(onDismiss = {}, onSave = { saved = true },
                    initial = McpServerConfig("fixture", "本地检索", command = "uvx"))
            }
        }
        val cancel = compose.onNodeWithText("取消").fetchSemanticsNode().boundsInRoot
        val save = compose.onNodeWithText("保存").fetchSemanticsNode().boundsInRoot
        assertEquals(cancel.width, save.width, 1f)
        assertEquals(cancel.height, save.height, 1f)
        assertEquals(cancel.top, save.top, 1f)
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(saved) }
    }

    @Test fun narrowActionGroupsWrapInsideScrollingPages() {
        RuntimeEnvironment.setFontScale(1.5f)
        compose.setContent {
            TaiXuTheme(dynamicColor = false) {
                Column(Modifier.width(240.dp).verticalScroll(rememberScrollState())) {
                    RuntimeActionRow {
                        RuntimeTextButton(onClick = {}) { Text("取消") }
                        RuntimeButton(onClick = {}) { Text("启动服务") }
                        RuntimeTextButton(onClick = {}) { Text("打开控制台") }
                    }
                }
            }
        }
        val cells = listOf("取消", "启动服务", "打开控制台").map {
            compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot
        }
        cells.forEach {
            assertEquals(cells.first().width, it.width, 1f)
            assertEquals(cells.first().height, it.height, 1f)
            assertEquals(cells.first().left, it.left, 1f)
        }
        org.junit.Assert.assertTrue(cells[0].bottom < cells[1].top)
        org.junit.Assert.assertTrue(cells[1].bottom < cells[2].top)
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun modelImportActionsRemainAlignedAndCancelClosesTheForm() {
        RuntimeEnvironment.setFontScale(1f)
        var dismissed = false
        compose.setContent {
            TaiXuTheme(dynamicColor = false) {
                CompositionLocalProvider(top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled provides false) {
                    ModelImportDialog(models = emptyList(), parseProfiles = { Result.success(emptyList()) },
                        onDismiss = { dismissed = true }, onImportJson = { _, _ -> })
                }
            }
        }
        compose.onNodeWithText("取消").assertIsDisplayed()
        compose.onNodeWithText("预览导入").assertIsDisplayed()
        val cancel = compose.onNodeWithText("取消").fetchSemanticsNode().boundsInRoot
        val preview = compose.onNodeWithText("预览导入").fetchSemanticsNode().boundsInRoot
        assertEquals(cancel.width, preview.width, 1f)
        assertEquals(cancel.height, preview.height, 1f)
        assertEquals(cancel.top, preview.top, 1f)
        val screenshot = java.io.File("build/reports/model-editor/model-import-actions.png")
        screenshot.parentFile.mkdirs()
        screenshot.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(dismissed) }
    }


    @Test fun emptyActionGroupsLeaveRoomForTheirHeading() {
        compose.setContent {
            TaiXuTheme(dynamicColor = false) {
                androidx.compose.foundation.layout.Row(Modifier.width(320.dp)) {
                    Text("本地环境检查", Modifier.weight(1f))
                    RuntimeActionRow {}
                }
            }
        }
        val heading = compose.onNodeWithText("本地环境检查").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(heading.width > 200f)
        org.junit.Assert.assertTrue(heading.height < 50f)
    }


    @Test fun searchIconsKeepAnInsetFromTheFieldEdge() {
        compose.setContent {
            TaiXuTheme(dynamicColor = false) {
                top.wkbin.taixu.ui.components.RuntimeSearchBar("", {},
                    Modifier.width(320.dp).testTag("search"), leadingIcon = { Text("⌕", Modifier.testTag("search-icon")) })
            }
        }
        val field = compose.onNodeWithTag("search").fetchSemanticsNode().boundsInRoot
        val icon = compose.onNodeWithTag("search-icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(icon.left - field.left >= 16f)
    }


    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun compactLoadingIndicatorsDrawWithinTheirMeasuredBounds() {
        RuntimeEnvironment.setFontScale(1f)
        compose.setContent {
            TaiXuTheme(dynamicColor = false) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(72.dp).background(androidx.compose.ui.graphics.Color.White).testTag("loading-frame"),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator(
                        Modifier.size(16.dp), color = androidx.compose.ui.graphics.Color.Blue,
                        trackColor = androidx.compose.ui.graphics.Color.Blue, strokeWidth = 1.5.dp)
                }
            }
        }
        val pixels = compose.onNodeWithTag("loading-frame").captureToImage().toPixelMap()
        var ink = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val pixel = pixels[x, y]
            if (pixel.red < 0.8f && pixel.blue > 0.8f) {
                ink++
                org.junit.Assert.assertTrue("Loading ink exceeds its 16dp bounds at $x,$y", x in 27..44 && y in 27..44)
            }
        }
        org.junit.Assert.assertTrue("The loading ring must remain visible", ink > 0)
    }


    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun loadingIndicatorsInPrimaryActionsInheritReadableInk() {
        RuntimeEnvironment.setFontScale(1f)
        compose.setContent {
            TaiXuTheme(dynamicColor = false) {
                RuntimeButton(onClick = {}, colors = top.wkbin.taixu.ui.miuix.ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color.Blue,
                    contentColor = androidx.compose.ui.graphics.Color.White)) {
                    top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator(
                        Modifier.size(16.dp).testTag("button-loading"), strokeWidth = 1.5.dp)
                    Text("下一步")
                }
            }
        }
        val pixels = compose.onNodeWithTag("button-loading", useUnmergedTree = true).captureToImage().toPixelMap()
        var whiteInk = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val pixel = pixels[x, y]
            if (pixel.red > 0.85f && pixel.green > 0.85f && pixel.blue > 0.85f) whiteInk++
        }
        org.junit.Assert.assertTrue("Primary action loading ink must contrast with its blue container", whiteInk > 5)
    }

}
