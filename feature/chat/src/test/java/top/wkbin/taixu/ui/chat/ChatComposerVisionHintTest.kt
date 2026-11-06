package top.wkbin.taixu.ui.chat

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.feature.chat.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatComposerVisionHintTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val image get() = ChatAttachment(
        name = "photo.png", isImage = true, sizeBytes = 1024,
        uri = Uri.EMPTY, guestFilePath = "/attachments/photo.png",
        base64DataUrl = "data:image/png;base64,aGVsbG8=",
    )

    @Test
    fun `visual input includes direct inspection guidance but transcript hides it`() {
        val text = buildComposerMessage(context, "图里有什么", listOf(image))
        assertTrue(text.contains(context.getString(R.string.chat_attachment_vision_hint)))
        assertTrue(text.contains("/attachments/photo.png"))
        assertEquals("图里有什么", userMessageDisplayText(text, true))
    }

    @Test
    fun `path only image does not claim visual input was attached`() {
        val text = buildComposerMessage(context, "图里有什么", listOf(image.copy(base64DataUrl = null)))
        assertFalse(text.contains(context.getString(R.string.chat_attachment_vision_hint)))
        assertEquals("图里有什么\n\nphoto.png (1 KB)", userMessageDisplayText(text, false))
    }

    @Test
    fun `document messages keep their file access instructions`() {
        val text = buildComposerMessage(context, "总结", listOf(image.copy(isImage = false, base64DataUrl = null)))
        assertFalse(text.contains(context.getString(R.string.chat_attachment_vision_hint)))
        assertTrue(text.contains(context.getString(R.string.chat_attachment_access_hint)))
    }
}
