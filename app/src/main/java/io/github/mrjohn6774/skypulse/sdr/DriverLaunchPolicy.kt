package io.github.mrjohn6774.skypulse.sdr

internal enum class DriverLaunchRoute {
    /** Android permits the activity start as-is. */
    DIRECT,

    /** Show a 1x1 overlay first; Android 15+ only honours the overlay permission while one is visible. */
    OVERLAY,

    /** Android will silently drop the start, so ask the user to tap a notification instead. */
    NOTIFY,
}

internal object DriverLaunchPolicy {
    // Android 10 introduced background activity start restrictions. A foreground service alone
    // is not an exemption, and startActivity() does not throw when the start is blocked.
    private const val BACKGROUND_START_RESTRICTED_SDK = 29

    fun route(sdkInt: Int, appVisible: Boolean, canDrawOverlays: Boolean): DriverLaunchRoute = when {
        sdkInt < BACKGROUND_START_RESTRICTED_SDK || appVisible -> DriverLaunchRoute.DIRECT
        canDrawOverlays -> DriverLaunchRoute.OVERLAY
        else -> DriverLaunchRoute.NOTIFY
    }
}
