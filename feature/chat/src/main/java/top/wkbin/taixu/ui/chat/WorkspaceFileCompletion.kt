package top.wkbin.taixu.ui.chat

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import top.wkbin.taixu.runtime.environment.ExecutionFileEntry
import top.wkbin.taixu.runtime.environment.ExecutionFiles

/** Bounded guest-path inventory; never resolves a path against the phone filesystem. */
internal class WorkspaceFileCompletion(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    data class Entry(val path: String, val directory: Boolean)
    private data class Cache(val key: Any, val at: Long, val entries: List<Entry>)
    private var cache: Cache? = null

    suspend fun complete(key: Any, root: String, remote: Boolean, files: ExecutionFiles, query: String): List<Entry> {
        val base = root.trimEnd('/').takeIf { it.startsWith('/') && it.isNotEmpty() } ?: return emptyList()
        val relativeQuery = if (query.startsWith('/')) {
            if (!query.startsWith("$base/")) return emptyList()
            query.removePrefix("$base/")
        } else query
        if (relativeQuery.split('/').any { it == "." || it == ".." } || relativeQuery.any { it.isISOControl() }) return emptyList()
        val directory = relativeQuery.substringBeforeLast('/', "")
        val cacheKey = Triple(key, base, directory)
        val previous = cache?.takeIf { it.key == cacheKey && clock() - it.at < if (remote) 20_000 else 5_000 }
        val entries = previous?.entries ?: scan(base, files, remote, directory).also { cache = Cache(cacheKey, clock(), it) }
        val q = relativeQuery.lowercase()
        return entries.asSequence().filter { it.path.lowercase().contains(q) }
            .sortedWith(compareBy<Entry> { !it.path.substringAfterLast('/').lowercase().startsWith(q) }
                .thenBy { !it.path.lowercase().startsWith(q) }.thenBy { it.path.length }.thenBy { it.path })
            .take(20).toList()
    }

    private suspend fun scan(root: String, files: ExecutionFiles, remote: Boolean, start: String): List<Entry> {
        val result = mutableListOf<Entry>()
        withTimeoutOrNull(if (remote) 4_000L else 2_000L) {
            data class Directory(val path: String, val rules: List<WorkspaceIgnoreRule>)
            var visited = 0
            var prefix = ""
            var inherited = emptyList<WorkspaceIgnoreRule>()
            // Explicit directory navigation gets its own bounded inventory. Walk ancestors first
            // so typing an ignored path cannot bypass the parent's ignore rules.
            for (segment in start.split('/').filter { it.isNotEmpty() }) {
                if (++visited >= 16 || !validCompletionName(segment) || segment in excluded) return@withTimeoutOrNull
                val parent = if (prefix.isEmpty()) root else "$root/$prefix"
                val children = files.listEntries(parent).getOrNull() ?: return@withTimeoutOrNull
                inherited = rules(files, parent, prefix, children, inherited)
                val next = if (prefix.isEmpty()) segment else "$prefix/$segment"
                if (children.none { it.name == segment && it.directory } || WorkspaceIgnoreRule.ignored(next, true, inherited)) return@withTimeoutOrNull
                prefix = next
            }
            val queue = ArrayDeque<Directory>().apply { add(Directory(prefix, inherited)) }
            while (queue.isNotEmpty() && visited++ < if (remote) 24 else 64) {
                currentCoroutineContext().ensureActive()
                val (path, inherited) = queue.removeFirst()
                val absolute = if (path.isEmpty()) root else "$root/$path"
                val children = files.listEntries(absolute).getOrNull() ?: continue
                val rules = rules(files, absolute, path, children, inherited)
                for (child in children) {
                    currentCoroutineContext().ensureActive()
                    if (!validCompletionName(child.name) || child.name in excluded) continue
                    val relative = if (path.isEmpty()) child.name else "$path/${child.name}"
                    if (WorkspaceIgnoreRule.ignored(relative, child.directory, rules)) continue
                    result += Entry(relative, child.directory)
                    if (result.size >= 512) return@withTimeoutOrNull
                    if (child.directory && relative.count { it == '/' } - prefix.count { it == '/' } < 7 && queue.size < 64) queue.add(Directory(relative, rules))
                }
            }
        }
        return result
    }

    private suspend fun rules(files: ExecutionFiles, absolute: String, path: String,
        children: List<ExecutionFileEntry>, inherited: List<WorkspaceIgnoreRule>): List<WorkspaceIgnoreRule> {
        val ignore = children.firstOrNull { it.name == ".gitignore" && !it.directory && it.size in 0..32_768 }
        val text = if (ignore != null) files.readTextForEdit("$absolute/.gitignore").getOrNull()?.take(32_768) else null
        return inherited + WorkspaceIgnoreRule.parse(path, text.orEmpty())
    }

    companion object {
        private val excluded = setOf(".git", ".gradle", ".idea", "node_modules", "build", ".venv", "__pycache__")
    }
}

internal fun validCompletionName(name: String) = name.isNotEmpty() && name !in setOf(".", "..") &&
    name.none { it == '/' || it == '\\' || it.isISOControl() }

/** Completion is a path reference; selecting it never reads file contents into the prompt. */
internal fun completionReference(path: String, directory: Boolean): String {
    val full = path + if (directory) "/" else ""
    return if (full.any { it.isWhitespace() || it == '"' || it == '@' }) {
        val quoted = kotlinx.serialization.json.JsonPrimitive(full).toString()
        "@" + if (directory) quoted.dropLast(1) else "$quoted "
    } else "@$full" + if (directory) "" else " "
}
