package top.wkbin.taixu.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneAgentActionTest {
    @Test
    fun parsesTapAfterThinking() {
        val action = parsePhoneAgentAction(
            """
            小红书已打开，现在需要点击搜索框
            do(action="Tap", element=[500, 100])
            """.trimIndent(),
        )
        assertEquals(PhoneAgentAction.Tap(500, 100), action)
    }

    @Test
    fun parsesSwipeAndFinish() {
        assertEquals(
            PhoneAgentAction.Swipe(100, 800, 100, 200),
            parsePhoneAgentAction("""do(action="Swipe", start=[100, 800], end=[100, 200])"""),
        )
        assertEquals(
            PhoneAgentAction.Finish("已发出"),
            parsePhoneAgentAction("""finish(message="已发出")"""),
        )
    }

    @Test
    fun parsesTypeAndMapsPoint() {
        assertEquals(
            PhoneAgentAction.Type("你好"),
            parsePhoneAgentAction("""do(action="Type", text="你好")"""),
        )
        assertEquals(0, phoneAgentPoint(0, 1080))
        assertEquals(1079, phoneAgentPoint(1000, 1080))
        assertEquals(539, phoneAgentPoint(500, 1080))
    }

    @Test
    fun parsesAnswerWrappedTap() {
        assertEquals(
            PhoneAgentAction.Tap(811, 188),
            parsePhoneAgentAction("""<answer>do(action="Tap", element=[811, 188])</answer>"""),
        )
    }

    @Test
    fun resolvesAppNameWithoutGuessing() {
        val apps = listOf(
            "com.tencent.mm" to "微信",
            "com.tencent.mobileqq" to "QQ",
            "com.tencent.qqmusic" to "QQ音乐",
            "com.xingin.xhs" to "小红书",
        )
        assertEquals("com.tencent.mm", resolveLaunchablePackage("微信", apps))
        assertEquals("com.tencent.mobileqq", resolveLaunchablePackage("com.tencent.mobileqq", apps))
        assertEquals("com.tencent.mobileqq", resolveLaunchablePackage("QQ", apps))
        assertEquals("com.xingin.xhs", resolveLaunchablePackage("红书", apps))
        assertNull(resolveLaunchablePackage("Q", apps))
        assertEquals("com.not.listed", resolveLaunchablePackage("com.not.listed", apps))
    }

    @Test
    fun rejectsUnknownAction() {
        assertNull(parsePhoneAgentAction("我先看看画面"))
        assertNull(parsePhoneAgentAction("""do(action="Fly")"""))
    }
}
