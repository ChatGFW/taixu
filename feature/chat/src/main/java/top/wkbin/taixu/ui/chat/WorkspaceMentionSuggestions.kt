package top.wkbin.taixu.ui.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest
import top.wkbin.taixu.harness.environment.SessionExecutionEnvironments
import top.wkbin.taixu.ui.components.RuntimeIconName

internal class WorkspaceMentionSuggestions(private val environments: SessionExecutionEnvironments,
    private val workspaceOf: suspend (String) -> String?) {
    private val index = WorkspaceFileCompletion()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(input: StateFlow<String>, session: StateFlow<String>, workspace: StateFlow<String>): Flow<List<MentionItem>> =
        combine(input, session, workspace) { text, id, root -> Triple(text, id, root) }.distinctUntilChanged()
            .transformLatest { (text, id, root) ->
                emit(emptyList())
                val start = trailingMentionStart(text) ?: return@transformLatest
                if (id.isBlank() || root.isBlank()) return@transformLatest
                val query = text.substring(start + 1).removePrefix("\"")
                delay(180)
                try {
                    // Session id and workspace are separate StateFlows; never allocate a world
                    // from an intermediate pair while foreground navigation is still loading.
                    if (workspaceOf(id) != root) return@transformLatest
                    val suggestions = environments.activity(id, root) {
                        val world = environments.environment(id, root)
                        val base = world.logicalWorkspace.trimEnd('/')
                        index.complete(world.id, base, !world.supportsSharedLocalServices, world.files, query).map { entry ->
                            MentionItem(
                                id = "$base/${entry.path}", name = entry.path,
                                description = "$base/${entry.path}", category = "工作区",
                                type = if (entry.directory) MentionType.DIRECTORY else MentionType.FILE,
                                icon = if (entry.directory) RuntimeIconName.Folder else RuntimeIconName.File,
                                sessionId = id, workspace = root, completionQuery = query,
                            )
                        }
                    }
                    emit(suggestions)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { emit(emptyList()) }
            }.flowOn(Dispatchers.IO)
}

/** End-of-input tokens only; email addresses and completed quoted references are not queries. */
internal fun trailingMentionStart(text: String): Int? {
    val start = text.lastIndexOf('@')
    if (start < 0) return null
    val previous = text.getOrNull(start - 1)
    if (previous != null && previous.code < 128 && (previous.isLetterOrDigit() || previous in "._%+-")) return null
    val token = text.substring(start + 1)
    val body = token.removePrefix("\"")
    return start.takeIf { body.none { it == '"' || it.isISOControl() || (!token.startsWith('"') && it.isWhitespace()) } }
}
