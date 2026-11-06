package top.wkbin.taixu.harness

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import top.wkbin.taixu.harness.session.ApiMessageProjector

class ResponsesImageInputTest {
    private val model = ModelConfig(
        name = "test", provider = "DeepSeek", model = "deepseek-flash",
        baseUrl = "https://example.com/v1", apiKey = null, responseApiEnabled = true,
        toolCallMode = ToolCallMode.DISABLED,
    )
    private val image = "data:image/png;base64,aGVsbG8="

    @Test
    fun `user image serializes as a flat Responses input image block`() {
        val projected = ApiMessageProjector.project(
            listOf(UserMessage("user", 1, "这张图片里有什么？", listOf(image))),
            ToolCallMode.DISABLED, visionEnabled = true,
        )
        val request = buildResponsesRequest(model, projected, stream = true)
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        val body = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
        val message = body.getValue("input").jsonArray.single().jsonObject
        val content = message.getValue("content").jsonArray
        assertEquals("https://example.com/v1/responses", request.url.toString())
        assertEquals("user", message.getValue("role").jsonPrimitive.content)
        assertEquals("input_text", content[0].jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("这张图片里有什么？", content[0].jsonObject.getValue("text").jsonPrimitive.content)
        assertEquals("input_image", content[1].jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals(image, content[1].jsonObject.getValue("image_url").jsonPrimitive.content)
        assertFalse(content[1].jsonObject.containsKey("file_id"))
    }
}
