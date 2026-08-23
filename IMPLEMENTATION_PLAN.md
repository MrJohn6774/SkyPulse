# SkyPulse MVP technical assessment

This assessment records the implementation choices made before the receiver and Beast
pipeline were integrated. Upstream revisions inspected on 2026-08-19 were:

- ebcTech decoder: `9acc8e71b53f925f5ebc9f63d4588cf24dcd99e4`
- Signalware `rtl_tcp_andro-`: `bf421f0d6983d665158fbc4729831ec88c244fac`
- FlightAware ADS-B Flight Scanner: `119735ecc81edea7a4ac79fdb9f4f9dccb84477a`

## 1. Reused decoder files

The primary source is `ebc81/dump1090andro-gpl-sources`. SkyPulse reuses the 1090 MHz
pipeline only: `Aircraft`, `Analyzer`, `AnalyzerBridge`, `RawPosition`,
`RecentAircraftCache`, the four analyzer bridge interfaces, `MovingAverage`, and the
`dump1090` detector/decoder/queue classes. The 978 MHz pipeline, embedded HTTP server,
old Android UI, proprietary ebcTech application code, and direct USB code are excluded.

SkyPulse modifications remove Google `LatLng`, use `GeoPoint`, expose decoder worker
health/queue clearing, and dispatch raw frames only after the decoder has accepted their
CRC/parity and ICAO semantics.

## 2. GPL implications

The reused FlightAware/ebcTech decoder retains its GPLv2 provenance and upstream file
headers. SkyPulse as a whole is distributed under GPL-3.0-or-later; the root `LICENSE`
contains the complete GPLv3 text, while the historical GPLv2 upstream text is retained at
`third_party/licenses/GPL-2.0-only.txt`. `NOTICE.md` documents the provenance and
modifications. Source distributions of the APK must provide the complete corresponding source.

The normalized VATSpy and SimAware data remain CC BY-SA 4.0 derived datasets; their
license and attribution are separate from the application code license.

## 3. `rtl_tcp_andro` launch

`RtlTcpDriver` builds an `ACTION_VIEW` `iqsrc://` intent, queries every compatible activity
through `PackageManager.queryIntentActivities`, prefers package `marto.rtl_tcp_andro`, and
uses an explicit component. The settings screen starts it with an Activity Result contract.
The recovery supervisor can also request a background start, while handling Android's
background-activity rejection as a degraded/retry state.

The current Signalware `SdrTcpArguments.java` confirms `-a`, `-p`, `-f`, `-s`, and `-g`.
No undocumented flags are used.

## 4. Exact RTL-TCP parameters

Default intent arguments are:

```text
-a 127.0.0.1 -p 1234 -f 1090000000 -s 2400000 -g 0
```

`-g 0` represents the default AGC setting in SkyPulse. Manual gain is stored in tenths of
a dB and substituted for zero. Address is deliberately not configurable away from loopback.

## 5. Sample ingress

`RtlTcpClient` connects only to loopback, consumes the 12-byte `RTL0` device header, keeps
I/Q byte pairs aligned across socket reads, and copies even-sized blocks into the decoder's
bounded `RtlSdrDataQueue`. The inherited workers compute magnitudes, detect Mode S preambles,
validate/correct messages, decode aircraft state, and update the repository. A five-second
read-silence threshold forces recovery.

## 6. Frame-to-Beast path

`DecodeFramesThread` invokes the export bridge after `Decoder.decodeModeS` accepts a frame.
`DecoderBridge` immediately increments health counters and sends the same raw 7- or 14-byte
Mode S frame to `BeastTcpServer`. Aircraft/UI work is separate and cannot gate export.

## 7. Beast timestamp and RSSI

The detector's `ModeSMessage.clockCount` is a 12 MHz sample-derived counter. Its low 48 bits
are emitted big-endian as the standard six-byte Beast timestamp. RSSI is
`round(sqrt(signalLevel) * 255)`, clamped to one byte. Timestamp, RSSI, and Mode S bytes use
standard doubled-`0x1a` escaping. Types `'2'` and `'3'` represent short and long Mode S.

## 8. Foreground service and boot

`AdsbForegroundService` is `START_STICKY`, type `connectedDevice`, enters foreground in
`onCreate`, holds a non-reference-counted partial wake lock, supervises decoder workers,
and owns RTL-TCP, Beast, and health listeners. `BootReceiver` handles boot, locked boot, and
package replacement using device-protected preferences. `UsbEventReceiver` handles driver
and USB attach/detach events. Internet state is never part of receiver health.

## 9. USB permission

The external driver owns `UsbManager.requestPermission` and the file descriptor. SkyPulse
does not request or steal the SDR device. Initial interactive setup launches the driver so
the user can grant its one-time permission. Later attach events start the station and the
supervisor reconnects to its loopback server.

## 10. Full-reboot assessment

An external-driver-only app cannot honestly guarantee the full-reboot acceptance test on
every ROM. Signalware opens the device through an activity, and Android/OEM background
activity and post-reboot USB permission behavior can still require interaction. SkyPulse
implements all safe automatic attempts and exposes the failure explicitly in state/logs;
the required real power-cycle test remains a hardware gate. If the target ROM blocks it,
the next engineering step is the specified Signalware driver service fork or embedded-SDR
flavor—not a false “healthy” state.

## 11. Module/component structure

The MVP uses one Android application module with separated packages:

```text
service -> lifecycle, boot, USB, watchdog
sdr     -> driver discovery, RTL-TCP client/state/recovery
decoder -> GPL bridge and inherited decoder sources
export  -> Beast encoder and non-blocking loopback server
aircraft-> bounded/current aircraft snapshots
health  -> counters and localhost HTTP status
map     -> MapLibre UI, overlays, atomic updater
settings-> device-protected station configuration
tools   -> build-time boundary normalization
```

## 12. Dependency proof (no GMS)

Direct runtime dependencies are AndroidX Core, AndroidX AppCompat, Material Components,
and MapLibre Native Android. JUnit is test-only. There are no Google Play Services,
Firebase, Google Maps, location-services, analytics, account, or Play Store libraries.
The build is additionally checked with a source scan and Gradle dependency report for
`com.google.android.gms` references.
