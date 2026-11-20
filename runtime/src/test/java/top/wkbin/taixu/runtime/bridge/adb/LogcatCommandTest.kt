package top.wkbin.taixu.runtime.bridge.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LogcatCommand 是两条通道（内置无线 ADB / 宿主特权 shell）共用的唯一命令拼接点。
 *
 * 这些断言锁定的是「通道切换后行为不变」这个契约：命令形态、注入面与退出码约定
 * 都必须与改造前 EmbeddedAdbManager 内联的版本逐字一致，否则同一个请求在 ADB
 * 不可用时回退到 Root，会得到与走 ADB 时不同的结果。
 */
class LogcatCommandTest {

    private fun request(
        packageName: String = "",
        tag: String = "",
        priority: Char = 'V',
        keyword: String = "",
    ) = EmbeddedAdbManager.LogcatRequest(packageName, tag, priority, keyword)

    @Test
    fun allPackagesWithoutTagUsesPriorityOnly() {
        assertEquals(
            "/system/bin/logcat -d -v threadtime -t 200 '*:V'",
            LogcatCommand.build(request(), 200),
        )
    }

    @Test
    fun tagSuppressesEverythingBelowRequestedPriority() {
        // 指定 tag 时必须追加 '*:S'，否则其他 tag 会以 default 优先级混进来。
        assertEquals(
            "/system/bin/logcat -d -v threadtime -t 50 'ActivityManager:W' '*:S'",
            LogcatCommand.build(request(tag = "ActivityManager", priority = 'W'), 50),
        )
    }

    @Test
    fun packageFilterResolvesPidAndFailsLoudlyWhenAppIsNotRunning() {
        val command = LogcatCommand.build(request(packageName = "top.wkbin.taixu"), 10)
        assertTrue(command.contains("/system/bin/pidof 'top.wkbin.taixu'"))
        assertTrue(command.contains("--pid=\"\$pid\""))
        // exit 3 是既有约定：上层据此把"目标应用未运行"与真实抓取失败区分开。
        assertTrue(command.contains("exit 3"))
    }

    @Test
    fun packageNameIsSingleQuotedSoShellMetacharactersStayInert() {
        // 校验层已挡非法包名；这里锁的是"即使上游放宽校验也不会变成命令注入"。
        val command = LogcatCommand.build(request(packageName = "a.b;rm -rf /"), 1)
        assertTrue(command.contains("/system/bin/pidof 'a.b;rm -rf /'"))
    }

    @Test
    fun keywordFiltersCaseInsensitivelyWithoutTouchingShell() {
        val output = "D/Foo: Hello\nI/Bar: hello world\nE/Baz: nope"
        assertEquals(
            "D/Foo: Hello\nI/Bar: hello world",
            LogcatCommand.filter(output, "HELLO"),
        )
    }

    @Test
    fun blankKeywordReturnsOutputVerbatim() {
        val output = "line1\nline2"
        assertEquals(output, LogcatCommand.filter(output, ""))
    }
}
