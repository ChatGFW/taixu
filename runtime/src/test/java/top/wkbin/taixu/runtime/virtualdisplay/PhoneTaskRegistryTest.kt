package top.wkbin.taixu.runtime.virtualdisplay

import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class PhoneTaskRegistryTest {
    @Test fun globalSingleFlightRejectsOtherSessionsAndSafeRelease() {
        val registry = PhoneTaskRegistry()
        val first = registry.start("a", Job())!!
        assertEquals("a", registry.activeSession())
        assertNull(registry.start("a", Job()))
        assertNull(registry.start("b", Job())) // 全局单飞：其他 session 也拒
        registry.release("a", first)
        assertNull(registry.activeSession())
        val replacement = registry.start("a", Job())!!
        registry.release("a", first) // 旧句柄释放应 no-op
        assertSame(replacement, registry.get("a"))
        registry.release("a", replacement)
        assertNotNull(registry.start("b", Job()))
    }

    @Test fun pauseResumeInvalidatesInFlightObservation() = runBlocking {
        val control = PhoneTaskControl(Job())
        val version = control.awaitReady()
        assertTrue(control.isCurrent(version))
        control.pause()
        val waiting = async { control.awaitReady() }
        yield()
        assertFalse(waiting.isCompleted)
        control.resume()
        assertFalse(control.isCurrent(version))
        assertTrue(control.isCurrent(waiting.await()))
    }

    @Test fun handoffWaitsLikePauseUntilResume() = runBlocking {
        val control = PhoneTaskControl(Job())
        control.requestHandoff()
        assertEquals(PhoneTaskState.NEEDS_USER, control.state.value)
        val waiting = async { control.awaitReady() }
        yield()
        assertFalse(waiting.isCompleted)
        control.resume()
        assertEquals(PhoneTaskState.RUNNING, control.state.value)
        assertTrue(control.isCurrent(waiting.await()))
    }

    @Test fun cancelStopsOwningJob() {
        val job = Job()
        val control = PhoneTaskControl(job)
        control.cancel()
        assertTrue(job.isCancelled)
        assertEquals(PhoneTaskState.CANCELLED, control.state.value)
    }

    @Test fun distinguishesPauseFromManualPageChanges() {
        val task = PhoneTaskControl(Job())
        task.pause()
        task.resume()
        assertEquals(0L, task.manualRevision())
        task.pause(manual = true)
        task.resume()
        assertEquals(1L, task.manualRevision())
    }

    @Test fun registryResumeReachesActiveTask() = runBlocking {
        val registry = PhoneTaskRegistry()
        val task = registry.start("s", Job())!!
        registry.pause("s", manual = true)
        assertEquals(PhoneTaskState.PAUSED, task.state.value)
        assertEquals(PhoneTaskState.PAUSED, registry.waiting.value?.state)
        val waiting = async { task.awaitReady() }
        yield()
        registry.resume("s")
        assertEquals(PhoneTaskState.RUNNING, task.state.value)
        assertNull(registry.waiting.value)
        assertTrue(task.isCurrent(waiting.await()))
        registry.release("s", task)
    }

    @Test fun registryHandoffExposesWaitingSnapshot() {
        val registry = PhoneTaskRegistry()
        val task = registry.start("s", Job())!!
        registry.requestHandoff("s", PhoneTaskWaitMode.CONFIRM)
        assertEquals(PhoneTaskState.NEEDS_USER, registry.waiting.value?.state)
        assertEquals(PhoneTaskWaitMode.CONFIRM, registry.waiting.value?.mode)
        assertEquals("s", registry.waiting.value?.sessionId)
        registry.resume("s")
        assertNull(registry.waiting.value)
        registry.release("s", task)
    }
}
