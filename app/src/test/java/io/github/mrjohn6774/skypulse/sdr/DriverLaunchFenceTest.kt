package io.github.mrjohn6774.skypulse.sdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverLaunchFenceTest {
    @Test
    fun `only one driver launch is admitted during the start window`() {
        val fence = DriverLaunchFence(singleFlightMs = 45_000)

        assertTrue(fence.tryAcquire(1_000))
        assertFalse(fence.tryAcquire(1_001))
        assertEquals(44_999, fence.remainingMs(1_001))
        assertTrue(fence.tryAcquire(46_000))
    }

    @Test
    fun `confirmed listener teardown releases the launch fence early`() {
        val fence = DriverLaunchFence(singleFlightMs = 45_000)
        assertTrue(fence.tryAcquire(1_000))

        fence.releaseAfterConfirmedTeardown()

        assertTrue(fence.tryAcquire(2_000))
    }
}
