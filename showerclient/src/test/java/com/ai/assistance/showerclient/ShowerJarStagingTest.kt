package com.ai.assistance.showerclient

import java.io.File
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShowerJarStagingTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun leavesExistingSharedJarUntouchedAndCreatesIndependentFiles() {
        val directory = temporary.newFolder()
        val old = File(directory, "shower-server.jar").apply { writeText("other install") }
        val payload = byteArrayOf(0, 1, 2, 127, -1)
        val first = stageShowerJar(listOf(directory)) { payload.inputStream() }
        val second = stageShowerJar(listOf(directory)) { payload.inputStream() }
        assertNotEquals(old, first)
        assertNotEquals(first, second)
        assertEquals("other install", old.readText())
        assertArrayEquals(payload, first.readBytes())
        assertArrayEquals(payload, second.readBytes())
    }

    @Test fun fallsBackWhenDirectoryCannotBeCreated() {
        val blocked = temporary.newFile()
        val fallback = temporary.newFolder()
        val file = stageShowerJar(listOf(blocked, fallback)) { "jar".byteInputStream() }
        assertEquals(fallback, file.parentFile)
        assertEquals("jar", file.readText())
    }

    @Test fun fallsBackAfterActualCopyFailureAndDeletesPartialFile() {
        val first = temporary.newFolder()
        val fallback = temporary.newFolder()
        var attempts = 0
        val file = stageShowerJar(listOf(first, fallback)) {
            attempts++
            if (attempts == 1) object : InputStream() {
                override fun read(): Int = throw IOException("write/read denied")
            } else "jar".byteInputStream()
        }
        assertTrue(first.listFiles()!!.isEmpty())
        assertEquals(fallback, file.parentFile)
        assertEquals("jar", file.readText())
    }

    @Test fun reportsEveryCandidateFailure() {
        val blocked = temporary.newFile()
        val blockedAgain = temporary.newFile()
        try {
            stageShowerJar(listOf(blocked, blockedAgain)) { "jar".byteInputStream() }
            throw AssertionError("Expected IOException")
        } catch (e: IOException) {
            assertEquals(2, e.suppressed.size)
            assertTrue(e.message!!.contains(blocked.absolutePath))
            assertFalse(blocked.isDirectory)
        }
    }
}
