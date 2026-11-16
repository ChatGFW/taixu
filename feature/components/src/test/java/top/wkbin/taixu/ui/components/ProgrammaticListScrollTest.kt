package top.wkbin.taixu.ui.components

import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgrammaticListScrollTest {

    @Test
    fun sequencerIsStablePerListState() {
        val state = LazyListState(0)
        assertSame(state.programmaticScrollSequencer(), state.programmaticScrollSequencer())
    }

    @Test
    fun newProgrammaticScrollCancelsPreviousInFlightScroll() = runBlocking {
        val state = LazyListState(0)
        val firstEntered = CompletableDeferred<Unit>()
        val firstCancelled = CompletableDeferred<Unit>()
        val first = launch {
            state.runProgrammaticScroll {
                firstEntered.complete(Unit)
                try {
                    delay(Long.MAX_VALUE)
                } finally {
                    firstCancelled.complete(Unit)
                }
            }
        }
        firstEntered.await()
        launch { state.runProgrammaticScroll { } }
        firstCancelled.await()
        assertTrue(first.isCancelled)
    }

    @Test
    fun programmaticScrollIsSkippedWhileDragActive() = runBlocking {
        val state = LazyListState(0)
        state.programmaticScrollSequencer().dragActive = true
        var executed = false
        state.runProgrammaticScroll { executed = true }
        assertFalse(executed)
    }
}
