package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.wkbin.taixu.core.model.ApprovalMode
import top.wkbin.taixu.ui.components.RuntimeTextButton

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h600dp-mdpi", manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionComposerDraftRenderTest : ModelFlowRenderSupport() {
    @Test fun `native composer restores each session draft and attachment row`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val sessions = MutableStateFlow("a")
        val drafts = SessionComposerDrafts<ChatAttachment>(sessions, scope, MemoryComposerTextState(), {})
        drafts.addAttachments(listOf(ChatAttachment(name = "仅属于A.txt", isImage = false, sizeBytes = 10, uri = Uri.parse("content://test/a"))))
        try {
            render("composer-session-drafts-dark-large", dark = true, scale = 1.3f, width = 360, height = 600, content = {
                val input by drafts.text.collectAsState()
                val files by drafts.attachments.collectAsState()
                val processing by drafts.processing.collectAsState()
                val session by sessions.collectAsState()
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Bottom) {
                    Row {
                        RuntimeTextButton(onClick = { sessions.value = "a" }) { Text("会话 A") }
                        RuntimeTextButton(onClick = { sessions.value = "b" }) { Text("会话 B") }
                    }
                    ChatComposer(listState = rememberLazyListState(), running = false, initializing = false,
                        status = null, messages = emptyList(), toolResults = emptyMap(), workspace = "",
                        input = input, onInputChanged = { drafts.setText(it, session) }, onSend = {}, onSendWithoutAnswer = {}, onPasteLongText = {}, onStop = {},
                        sendMode = ComposerSendMode.NEXT_RUN, matchingCommands = emptyList(), onApplyCommand = {},
                        matchingMentions = emptyList(), onApplyMention = {}, attachments = files,
                        attachmentsProcessing = processing, onAttachmentsPicked = { _, _ -> }, onRemoveAttachment = { drafts.removeAttachment(it, session) },
                        knownMentionNames = emptyList(), attachedMentions = emptyList(), onRemoveMention = {},
                        pinnedCapabilities = emptyList(), onUnpinMention = {}, onOpenSkillsMcp = {}, onCompactContext = { it() }, onOpenModels = {},
                        activeModel = profile, approvalMode = ApprovalMode.REQUEST, onOpenApprovalModes = {}, contextUsage = ContextUsage())
                }
            }) { root ->
                fun field() = nodes(root).single { it.config.getOrNull(SemanticsActions.SetText) != null }
                assertTrue(field().config.getOrNull(SemanticsActions.SetText)!!.action!!.invoke(AnnotatedString("保留 A 草稿")))
                settle()
                click(root, "会话 B")
                assertEquals("", field().config.getOrNull(SemanticsProperties.EditableText)!!.text)
                assertFalse(hasText(root, "仅属于A.txt"))
                field().config.getOrNull(SemanticsActions.SetText)!!.action!!.invoke(AnnotatedString("保留 B 草稿"))
                settle(); click(root, "会话 A")
                assertEquals("保留 A 草稿", field().config.getOrNull(SemanticsProperties.EditableText)!!.text)
                assertEquals(listOf("仅属于A.txt"), drafts.attachments.value.map { it.name })
                // A remounted LazyRow needs another host measure pass in the headless window.
                repeat(2) {
                    root.measure(android.view.View.MeasureSpec.makeMeasureSpec(360, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, 360, 600); settle()
                }
                snapshot(root, 360, 600, "composer-session-drafts-restored")
                assertTrue(nodes(root).joinToString("\n") { it.config.toString() }, hasText(root, "仅属于A.txt"))
                assertEquals("保留 B 草稿", persistenceText(drafts, sessions))
                sessions.value = "a"; settle()
            }
        } finally { scope.cancel() }
    }
    private fun persistenceText(drafts: SessionComposerDrafts<ChatAttachment>, sessions: MutableStateFlow<String>): String {
        sessions.value = "b"
        return drafts.snapshot().draft.text
    }
}
