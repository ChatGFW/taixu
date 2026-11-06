package top.wkbin.taixu.ui.chat

import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedComposerDraftTextStateTest {
    @Test fun `legacy single draft migrates to the initially selected session`() {
        val handle = SavedStateHandle(mapOf(SavedComposerDraftTextState.LEGACY_KEY to "legacy text"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val persistence = SavedComposerDraftTextState(handle, "a")
            val draft = SessionComposerDrafts<String>(MutableStateFlow("a"), scope, persistence, {})
            assertEquals("legacy text", draft.text.value)
            assertFalse(handle.contains(SavedComposerDraftTextState.LEGACY_KEY))
            assertEquals("legacy text", persistence.restore().texts["a"])
        } finally { scope.cancel() }
    }
    @Test fun `malformed system state is ignored without a startup crash`() {
        val handle = SavedStateHandle(mapOf(SavedComposerDraftTextState.KEY to "wrong type",
            SavedComposerDraftTextState.LEGACY_KEY to 42))
        assertEquals(ComposerTextSnapshot("a"), SavedComposerDraftTextState(handle, "a").restore())
        handle[SavedComposerDraftTextState.KEY] = android.os.Bundle().apply {
            putString("owner", "a")
            putStringArrayList("ids", arrayListOf("a", "b"))
            putStringArrayList("texts", arrayListOf("valid text"))
        }
        assertEquals(mapOf("a" to "valid text"), SavedComposerDraftTextState(handle, "a").restore().texts)
    }

    @Test fun `saved bundle stays small and does not contain attachment payloads`() {
        val handle = SavedStateHandle()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val sessions = MutableStateFlow("a")
        try {
            val persistence = SavedComposerDraftTextState(handle, "a")
            val drafts = SessionComposerDrafts<String>(sessions, scope, persistence, {})
            repeat(10) { n -> sessions.value = "s$n"; drafts.setText("中".repeat(10_000)) }
            drafts.addAttachments(listOf("image data:" + "x".repeat(1_000_000)))
            val bundle = handle.get<android.os.Bundle>(SavedComposerDraftTextState.KEY)!!
            val parcel = Parcel.obtain()
            try {
                parcel.writeBundle(bundle)
                assertTrue("Bundle must stay below 100 KB", parcel.dataSize() < 100_000)
            } finally { parcel.recycle() }
            assertEquals(3, persistence.restore().texts.size)
            assertEquals(listOf("image data:" + "x".repeat(1_000_000)), drafts.attachments.value)
        } finally { scope.cancel() }
    }
}
