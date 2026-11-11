package top.wkbin.taixu.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import top.wkbin.taixu.core.model.ChatTopToolbarAction

class ChatTopToolbarPreferencesTest {
    @get:Rule val temporary = TemporaryFolder()
    @get:Rule val renameGuard = RequiresAtomicRenameOverwrite()

    @Test fun defaultsToBotsAndWorkspace() = runBlocking {
        withStore { preferences ->
            assertEquals(ChatTopToolbarAction.DEFAULT_PINNED, preferences.pinnedActions.first())
        }
    }

    @Test fun rejectsFourthPinAndKeepsOrder() = runBlocking {
        withStore { preferences ->
            assertTrue(preferences.setPinned(ChatTopToolbarAction.MEMORY, pin = true))
            assertFalse(preferences.setPinned(ChatTopToolbarAction.OUTLINE, pin = true))
            assertEquals(
                listOf(
                    ChatTopToolbarAction.BOTS,
                    ChatTopToolbarAction.WORKSPACE,
                    ChatTopToolbarAction.MEMORY,
                ),
                preferences.pinnedActions.first(),
            )
        }
    }

    @Test fun emptyStringMeansOnlyMoreButton() = runBlocking {
        withStore { preferences ->
            preferences.setPinnedActions(emptyList())
            assertEquals(emptyList<ChatTopToolbarAction>(), preferences.pinnedActions.first())
        }
    }

    @Test fun dirtyStorageFallsBackToDefault() = runBlocking {
        val file = File(temporary.root, "top-toolbar.preferences_pb")
        val firstJob = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(firstJob + Dispatchers.IO),
                produceFile = { file },
            )
            store.edit { it[stringPreferencesKey("chat_top_toolbar_pinned")] = "ghost,bots" }
        } finally {
            firstJob.cancelAndJoin()
        }

        val secondJob = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(secondJob + Dispatchers.IO),
                produceFile = { file },
            )
            val preferences = ChatTopToolbarPreferences(store)
            assertEquals(listOf(ChatTopToolbarAction.BOTS), preferences.pinnedActions.first())
        } finally {
            secondJob.cancelAndJoin()
        }
    }

    private suspend fun withStore(block: suspend (ChatTopToolbarPreferences) -> Unit) {
        val file = File(temporary.root, "top-toolbar-${System.nanoTime()}.preferences_pb")
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(job + Dispatchers.IO),
                produceFile = { file },
            )
            block(ChatTopToolbarPreferences(store))
        } finally {
            job.cancelAndJoin()
        }
    }
}
