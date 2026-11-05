package top.wkbin.taixu.ui.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import top.wkbin.taixu.harness.environment.SessionExecutionEnvironments
import top.wkbin.taixu.runtime.environment.ExecutionEnvironmentFactory

class WorkspaceMentionSuggestionsTest {
    @Test fun `navigation intermediate workspace pair never allocates an execution world`() = runBlocking {
        val checked = Channel<Unit>(Channel.UNLIMITED)
        val opened = CompletableDeferred<String>()
        val environments = SessionExecutionEnvironments(ExecutionEnvironmentFactory { _, workspace, _ ->
            opened.complete(workspace)
            error("Fixture intentionally has no transport")
        })
        val suggestions = WorkspaceMentionSuggestions(environments) { checked.send(Unit); "/workspace/right" }
        val workspace = MutableStateFlow("/workspace/previous")
        val collection = launch {
            suggestions.observe(MutableStateFlow("@file"), MutableStateFlow("session"), workspace).collect()
        }
        try {
            withTimeout(10_000) { checked.receive() }
            assertEquals(false, opened.isCompleted)
            workspace.value = "/workspace/right"
            assertEquals("/workspace/right", withTimeout(10_000) { opened.await() })
        } finally { collection.cancelAndJoin() }
    }
}
