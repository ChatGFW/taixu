package top.wkbin.taixu.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class UserMessagePresentationTest {
    private val header = "\n\n[附件：已复制并挂载到 Linux 沙箱]\n"
    private val hint = "需要通过工具访问时，请使用 base 在上述绝对路径读取；不要使用 Android content:// URI 或宿主机路径。"
    private val image = "1. [图片] photo.png (2.1 MB) -> Linux 沙箱绝对路径: /attachments/id_photo.png\n"
    private val file = "2. [文件] report.pdf (1.0 MB) -> Linux 沙箱绝对路径: /attachments/id_report.pdf\n"

    @Test
    fun `image only message shows its preview without generated text`() {
        assertEquals("", userMessageDisplayText("请分析附带的图片：$header$image$hint", true))
    }

    @Test
    fun `user text and file names remain visible`() {
        val message = "帮我总结$header$image${file}正文提取说明\n$hint @helper"
        assertEquals("帮我总结\n\nreport.pdf (1.0 MB)\n\n@helper", userMessageDisplayText(message, true))
    }

    @Test
    fun `files only message keeps a visible attachment summary`() {
        assertEquals("report.pdf (1.0 MB)", userMessageDisplayText("请分析并处理以下附带的文件：$header$file$hint", false))
    }

    @Test
    fun `images without previews retain their names`() {
        assertEquals("photo.png (2.1 MB)", userMessageDisplayText("请分析附带的图片：$header$image$hint", false))
        assertEquals("photo.png (2.1 MB)", userMessageDisplayText("$header$image$hint".trim(), false))
    }

    @Test
    fun `english history is recognized independently of current locale`() {
        val message = "Analyze the attached images:\n\n[Attachments copied and mounted in the Linux sandbox]\n" +
            "1. [Image] photo.png (2.1 MB) -> Linux sandbox absolute path: /attachments/photo.png\n" +
            "When tool access is needed, use base to read the absolute paths above. Do not use Android content:// URIs or host paths."
        assertEquals("", userMessageDisplayText(message, true))
    }

    @Test
    fun `ordinary text and incomplete attachment blocks remain intact`() {
        for (text in listOf("请分析附带的图片：", "引用$header$image", "引用$header$hint", "/attachments/photo.png")) {
            assertEquals(text, userMessageDisplayText(text, false))
        }
    }
}
