package io.github.mrjohn6774.skypulse.service

import android.content.Intent
import android.hardware.usb.UsbManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiverActionFilterTest {
    @Test fun `boot receiver accepts only declared boot actions`() {
        assertTrue(Intent.ACTION_BOOT_COMPLETED in BootReceiver.ALLOWED_ACTIONS)
        assertFalse("example.intent.UNRELATED" in BootReceiver.ALLOWED_ACTIONS)
    }

    @Test fun `usb receiver accepts only declared usb actions`() {
        assertTrue(UsbManager.ACTION_USB_DEVICE_ATTACHED in UsbEventReceiver.ALLOWED_ACTIONS)
        assertFalse("example.intent.UNRELATED" in UsbEventReceiver.ALLOWED_ACTIONS)
    }
}
