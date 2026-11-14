package top.wkbin.taixu.runtime.virtualdisplay

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceAttachGateTest {
    @Test fun supersededCommandDoesNotRun() = runBlocking {
        val gate = SurfaceAttachGate()
        val first = gate.bump()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val firstJob = launch(Dispatchers.Default) {
            gate.runIfCurrent(first) {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        val second = gate.bump()
        var staleRan = false
        var secondRan = false
        val staleJob = launch(Dispatchers.Default) {
            gate.runIfCurrent(first) { staleRan = true }
        }
        val secondJob = launch(Dispatchers.Default) {
            gate.runIfCurrent(second) { secondRan = true }
        }
        yield()
        release.complete(Unit)
        firstJob.join()
        staleJob.join()
        secondJob.join()
        assertFalse(staleRan)
        assertTrue(secondRan)
    }

    @Test fun currentDetachRunsBeforeRelease() = runBlocking {
        val gate = SurfaceAttachGate()
        val gen = gate.bump()
        val order = mutableListOf<String>()
        gate.detachThenRelease(gen, detach = { order += "detach" }) {
            order += "release"
        }
        assertEquals(listOf("detach", "release"), order)
    }

    @Test fun releaseWaitsUntilNewerAttachApplied() = runBlocking {
        val gate = SurfaceAttachGate()
        val old = gate.bump()
        val newer = gate.bump()
        val entered = CompletableDeferred<Unit>()
        val allowFinish = CompletableDeferred<Unit>()
        val newerJob = launch(Dispatchers.Default) {
            gate.runIfCurrent(newer) {
                entered.complete(Unit)
                allowFinish.await()
            }
        }
        entered.await()
        var detached = false
        var released = false
        val releaseJob = launch(Dispatchers.Default) {
            gate.detachThenRelease(old, detach = { detached = true }) {
                released = true
            }
        }
        yield()
        assertFalse(released)
        assertFalse(detached)
        allowFinish.complete(Unit)
        newerJob.join()
        releaseJob.join()
        assertFalse(detached)
        assertTrue(released)
    }
}
