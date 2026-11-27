package top.wkbin.taixu.ui.git

import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitChangeDiffTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun stagedAndUnstagedChangesToOneFileHaveIndependentDiffs() {
        val directory = temp.newFolder()
        val path = "file name.txt"
        val file = directory.resolve(path)
        Git.init().setDirectory(directory).call().use { git ->
            file.writeText("base\n")
            git.add().addFilepattern(path).call()
            git.commit().setMessage("initial").setAuthor("Tester", "test@example.com").call()
            file.writeText("index\n")
            git.add().addFilepattern(path).call()
            file.writeText("work tree\n")
            directory.resolve("other.txt").writeText("other file\n")

            val changes = git.status().call().toFileChanges().filter { it.path == path }
            assertEquals(listOf(true, false), changes.map { it.staged })
            val staged = git.repository.readChangeDiff(changes.first()).text
            val unstaged = git.repository.readChangeDiff(changes.last()).text
            assertTrue(staged.contains("-base\n+index\n"))
            assertFalse(staged.contains("work tree"))
            assertTrue(unstaged.contains("-index\n+work tree\n"))
            assertFalse(unstaged.contains("other file"))
        }
    }

    @Test
    fun newFileDiffWorksBeforeFirstCommitAndWhenUntracked() {
        val directory = temp.newFolder()
        val path = "new.txt"
        directory.resolve(path).writeText("hello\n")
        Git.init().setDirectory(directory).call().use { git ->
            val untracked = git.status().call().toFileChanges().single()
            assertEquals(GitManager.ChangeType.UNTRACKED, untracked.changeType)
            assertTrue(git.repository.readChangeDiff(untracked).text.contains("+hello\n"))

            git.add().addFilepattern(path).call()
            val staged = git.status().call().toFileChanges().single()
            assertTrue(staged.staged)
            assertTrue(git.repository.readChangeDiff(staged).text.contains("+hello\n"))
        }
    }

    @Test
    fun largePatchIsTruncatedAndBinaryContentIsNotDecoded() {
        val directory = temp.newFolder()
        directory.resolve("long.txt").writeText("0123456789\n".repeat(8_000))
        directory.resolve("binary.dat").writeBytes(byteArrayOf(0, 11, 0, 24))
        Git.init().setDirectory(directory).call().use { git ->
            val changes = git.status().call().toFileChanges().associateBy { it.path }
            val text = git.repository.readChangeDiff(changes.getValue("long.txt"))
            assertTrue(text.truncated)
            assertTrue(text.text.toByteArray().size <= 48 * 1024)
            val binary = git.repository.readChangeDiff(changes.getValue("binary.dat"))
            assertTrue(binary.text.contains("Binary files differ"))
        }
    }
}
