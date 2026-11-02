package top.wkbin.taixu.runtime.proot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotMountLayoutTest {

    @Test
    fun isBindableHostPathAcceptsReadableFilesAndDirectories() {
        val dir = File.createTempFile("taixu-bind", "dir").apply {
            delete()
            mkdirs()
        }
        val child = File(dir, "child.txt").apply { writeText("ok") }
        try {
            assertTrue(ProotMountLayout.isBindableHostPath(dir))
            assertTrue(ProotMountLayout.isBindableHostPath(child))
            assertFalse(ProotMountLayout.isBindableHostPath(File(dir, "missing")))
        } finally {
            child.delete()
            dir.delete()
        }
    }

    @Test
    fun androidLinkerBindPathsIsSafeOnNonAndroidHosts() {
        // JVM 单元测试跑在桌面 OS 上时通常没有 /system/bin/linker64；应返回空列表而不是抛异常。
        val paths = ProotMountLayout.androidLinkerBindPaths()
        assertEquals(emptyList<String>(), paths.filter { !it.startsWith("/") })
        assertTrue(paths.all { it.startsWith("/apex") || it.contains("linker") || it.startsWith("/") })
    }
}
