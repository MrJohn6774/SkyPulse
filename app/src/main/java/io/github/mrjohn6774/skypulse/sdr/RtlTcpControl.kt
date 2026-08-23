package io.github.mrjohn6774.skypulse.sdr

import java.io.OutputStream

internal object RtlTcpControl {
    // Signalware's Android-specific rtl_tcp command. A command is one byte followed
    // by a network-order uint32 parameter; zero is the only meaningful exit value.
    internal val EXIT_DRIVER_COMMAND = byteArrayOf(0x7E, 0, 0, 0, 0)

    fun requestDriverExit(output: OutputStream) {
        output.write(EXIT_DRIVER_COMMAND)
        output.flush()
    }
}
