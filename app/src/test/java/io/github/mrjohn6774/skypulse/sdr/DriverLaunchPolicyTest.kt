package io.github.mrjohn6774.skypulse.sdr

import org.junit.Assert.assertEquals
import org.junit.Test

class DriverLaunchPolicyTest {
    @Test
    fun `starts directly before Android 10 or while an activity is visible`() {
        assertEquals(DriverLaunchRoute.DIRECT, DriverLaunchPolicy.route(28, appVisible = false, canDrawOverlays = false))
        assertEquals(DriverLaunchRoute.DIRECT, DriverLaunchPolicy.route(36, appVisible = true, canDrawOverlays = false))
    }

    @Test
    fun `background start uses the overlay when permitted`() {
        assertEquals(DriverLaunchRoute.OVERLAY, DriverLaunchPolicy.route(36, appVisible = false, canDrawOverlays = true))
    }

    @Test
    fun `background start without overlay permission asks the user`() {
        assertEquals(DriverLaunchRoute.NOTIFY, DriverLaunchPolicy.route(29, appVisible = false, canDrawOverlays = false))
    }
}
