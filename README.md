# SkyPulse

SkyPulse is a GPLv2 Android ADS-B station MVP for a dedicated, unattended 1090 MHz receiver.
It consumes unsigned 8-bit I/Q from an external `rtl_tcp_andro` driver, decodes Mode S/ADS-B
on-device, and publishes standard Beast binary data on `127.0.0.1:30005` for Termux/readsb.

## Implemented MVP

- ebcTech/FlightAware dump1090 detector and decoder with the last GMS type removed
- external `iqsrc://` SDR launch at 1090 MHz / 2.4 MSPS
- explicit supervised RTL-TCP state machine and five-second stale-stream recovery
- bounded I/Q, decoder, Beast, and UI paths
- standard Beast type 2/3 framing, 48-bit timestamp, RSSI, and `0x1a` escaping
- replaceable one-client loopback Beast server that disconnects slow readers
- `connectedDevice` foreground service, partial wake lock, sticky restart, boot/USB receivers
- `http://127.0.0.1:8090/status` health JSON
- receiver, settings, diagnostics, and MapLibre/OpenFreeMap aircraft-map screens
- offline VATSpy FIR and SimAware TRACON snapshots with layer toggles
- weekly private-cache boundary updates with validation and atomic replacement

## Build

The project uses API 36, AGP 9.3, Gradle 9.5, JDK 17+, and minSdk 23.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
./gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## Initial station setup

1. Side-load SkyPulse and a current Signalware-compatible `rtl_tcp_andro` APK.
2. Open Settings and enter the fixed receiver latitude/longitude.
3. Connect the RTL-SDR through powered USB OTG.
4. Tap **Start** and grant the external driver's USB permission when Android asks.
5. Confirm I/Q and messages on Receiver, then connect Termux to `127.0.0.1:30005`.
6. Check health with `curl http://127.0.0.1:8090/status` from Termux.

## Reliability boundary

The software implements automatic socket, stale-I/Q, decoder-thread, driver, USB attach,
client, process, and boot recovery. A real RTL-SDR/phone test is still required before
claiming the mandatory unattended full-reboot, eight-hour screen-off, or 72-hour soak
acceptance cases. Some ROMs may block the external driver's activity at boot; see
`IMPLEMENTATION_PLAN.md` for the required embedded/fork fallback decision.

## Boundary regeneration

See `tools/README.md`.
