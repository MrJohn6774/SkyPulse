package org.skypulse.app.sdr

enum class RtlTcpState {
    STOPPED,
    WAITING_FOR_DEVICE,
    STARTING_DRIVER,
    CONNECTING_TCP,
    STREAMING,
    RECOVERING,
}
