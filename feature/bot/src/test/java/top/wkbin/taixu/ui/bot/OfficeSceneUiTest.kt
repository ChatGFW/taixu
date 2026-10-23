package top.wkbin.taixu.ui.bot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.core.model.bot.BotAssignment
import top.wkbin.taixu.core.model.bot.BotAssignmentStatus
import top.wkbin.taixu.ui.theme.TaiXuTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w480dp-h900dp")
class OfficeSceneUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `replacing scene moves zone signs and creates cat targets from instance data`() {
        val scene = mutableStateOf(OfficeSceneSpec.Default)
        compose.setContent {
            TaiXuTheme {
                Box(Modifier.requiredSize(384.dp, 808.dp)) {
                    GroupPixelOffice(
                        actors = emptyList(), recipes = emptyMap(), bubbles = emptyMap(),
                        selectedId = null, emptyHint = null, onSelect = {}, scene = scene.value,
                    )
                }
            }
        }
        val scale = compose.onNodeWithTag("pixel-office-scene").fetchSemanticsNode().boundsInRoot.width / 192f
        val oldCat = compose.onNodeWithTag("office-cat-lobby").fetchSemanticsNode().boundsInRoot
        val oldZone = compose.onNodeWithTag("office-zone-think").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val original = scene.value
            scene.value = original.copy(
                cats = original.cats.map {
                    if (it.id == "office-cat-lobby") it.copy(id = "visitor-cat", x = it.x + 20, y = it.y + 20) else it
                } + OfficeCatSpec("extra-cat", OfficeCatStyle.SLEEPING, 20, 54),
                zones = original.zones.map {
                    if (it.id == "think") it.copy(anchor = OfficeAnchor(it.anchor.x + 10, it.anchor.y + 20)) else it
                },
            )
        }
        compose.onNodeWithTag("office-cat-lobby").assertDoesNotExist()
        val newCat = compose.onNodeWithTag("visitor-cat").assertHasClickAction().fetchSemanticsNode().boundsInRoot
        val newZone = compose.onNodeWithTag("office-zone-think").fetchSemanticsNode().boundsInRoot
        assertEquals(20f * scale, newCat.left - oldCat.left, 1f)
        assertEquals(20f * scale, newCat.top - oldCat.top, 1f)
        assertEquals(10f * scale, newZone.left - oldZone.left, 1f)
        assertEquals(20f * scale, newZone.top - oldZone.top, 1f)
        compose.onNodeWithTag("extra-cat").assertHasClickAction().performClick()
        compose.onNodeWithTag("visitor-cat").performClick()
    }

    @Test
    fun `studio uses the same changed scene for actor placement and rendering`() {
        val scene = mutableStateOf(OfficeSceneSpec.Default)
        val bot = Bot(
            id = "coder", name = "工程师", role = "开发", systemPrompt = "", avatarSeed = "",
            sessionId = "session", createdAt = 1L, updatedAt = 1L,
        )
        val task = BotAssignment(
            id = "task", groupId = "group", botId = bot.id, botName = bot.name, title = "工作",
            task = "工作", status = BotAssignmentStatus.RUNNING, startedAt = 1L, createdAt = 1L,
        )
        compose.setContent {
            TaiXuTheme {
                Box(Modifier.requiredSize(384.dp, 808.dp)) {
                    GroupCyberStudio(
                        members = listOf(bot), messages = emptyList(), assignments = listOf(task),
                        onSelectBot = {}, onOpenBot = {}, onCancelAssignment = {},
                        onContinueAssignment = { _, _, _ -> }, onBackToChat = {}, scene = scene.value,
                    )
                }
            }
        }
        val scale = compose.onNodeWithTag("pixel-office-scene").fetchSemanticsNode().boundsInRoot.width / 192f
        val before = compose.onNodeWithTag("studio-actor-coder").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            scene.value = scene.value.copy(desks = scene.value.desks.map {
                if (it.id == "desk") it.copy(x = it.x + 4, y = it.y + 4) else it
            })
        }
        compose.mainClock.advanceTimeBy(900L)
        val after = compose.onNodeWithTag("studio-actor-coder").fetchSemanticsNode().boundsInRoot
        assertEquals(4f * scale, after.left - before.left, 1f)
        assertEquals(4f * scale, after.top - before.top, 1f)
    }
}
