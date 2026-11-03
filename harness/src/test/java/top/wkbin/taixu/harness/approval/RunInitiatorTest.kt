package top.wkbin.taixu.harness.approval

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunInitiatorTest {
    @Test
    fun `unknown kind collapses to person`() {
        assertEquals(RunInitiator.PERSON, RunInitiator("bogus", "x").normalized().kind)
        assertEquals(null, RunInitiator("bogus", "x").normalized().id)
    }

    @Test
    fun `unattended kinds match OpenBot nobody-watching set`() {
        assertTrue(RunInitiator.isUnattended(RunInitiator.SCHEDULE))
        assertTrue(RunInitiator.isUnattended(RunInitiator.HANDOFF))
        assertTrue(RunInitiator.isUnattended(RunInitiator.WORKFLOW))
        assertFalse(RunInitiator.isUnattended(RunInitiator.PERSON))
        assertFalse(RunInitiator.isUnattended(RunInitiator.SYSTEM))
    }

    @Test
    fun `coroutine context supplies current initiator`() = runBlocking {
        assertEquals(RunInitiator.PERSON, currentRunInitiator().kind)
        withContext(RunInitiatorContext(RunInitiator.schedule("s1"))) {
            val current = currentRunInitiator()
            assertEquals(RunInitiator.SCHEDULE, current.kind)
            assertEquals("s1", current.id)
        }
    }
}
