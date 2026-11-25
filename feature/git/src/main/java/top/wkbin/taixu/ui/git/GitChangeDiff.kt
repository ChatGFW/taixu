package top.wkbin.taixu.ui.git

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.dircache.DirCacheIterator
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.treewalk.filter.PathFilter
import top.wkbin.taixu.ui.git.GitManager.ChangeType
import top.wkbin.taixu.ui.git.GitManager.GitFileChange

internal fun Status.toFileChanges(): List<GitFileChange> = buildList {
    fun addPaths(paths: Set<String>, type: ChangeType, staged: Boolean) {
        (paths - conflicting).forEach { add(GitFileChange(it, type, staged)) }
    }
    addPaths(added, ChangeType.ADDED, staged = true)
    addPaths(changed, ChangeType.MODIFIED, staged = true)
    addPaths(removed, ChangeType.DELETED, staged = true)
    addPaths(modified, ChangeType.MODIFIED, staged = false)
    addPaths(missing, ChangeType.DELETED, staged = false)
    addPaths(untracked, ChangeType.UNTRACKED, staged = false)
    conflicting.forEach { add(GitFileChange(it, ChangeType.CONFLICT, staged = false)) }
}.sortedBy { it.path }

internal data class GitChangeDiff(val text: String, val truncated: Boolean)

internal fun Repository.readChangeDiff(change: GitFileChange): GitChangeDiff {
    val index = DirCacheIterator(readDirCache())
    val before = if (change.staged) headTree() else index
    val after = if (change.staged) index else FileTreeIterator(this)
    val output = DiffPreviewOutput()
    val truncated = try {
        DiffFormatter(output).use { formatter ->
            formatter.setRepository(this)
            formatter.setPathFilter(PathFilter.create(change.path))
            formatter.setBinaryFileThreshold(512 * 1024)
            formatter.format(formatter.scan(before, after).filter {
                it.oldPath == change.path || it.newPath == change.path
            })
        }
        false
    } catch (_: DiffPreviewLimitReached) {
        true
    }
    return GitChangeDiff(output.text(), truncated)
}

private fun Repository.headTree(): AbstractTreeIterator {
    val tree = resolve("${Constants.HEAD}^{tree}") ?: return EmptyTreeIterator()
    return newObjectReader().use { reader -> CanonicalTreeParser(null, reader, tree) }
}

private class DiffPreviewLimitReached : IOException()

private class DiffPreviewOutput : OutputStream() {
    private val limit = 48 * 1024
    private val buffer = ByteArrayOutputStream()

    override fun write(value: Int) {
        if (buffer.size() >= limit) throw DiffPreviewLimitReached()
        buffer.write(value)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        val count = minOf(length, limit - buffer.size())
        buffer.write(bytes, offset, count)
        if (count < length) throw DiffPreviewLimitReached()
    }

    fun text(): String = buffer.toString(Charsets.UTF_8.name())
}
