# SkyPulse

SkyPulse decodes 1090 MHz Mode S/ADS-B aircraft traffic on Android using an RTL-SDR.

## Features

- On-device ADS-B decoding and live aircraft map
- Offline FIR and TRACON boundary snapshots
- Optional loopback-only Beast TCP output
- Configurable unattended startup after boot

## Requirements

SkyPulse needs an RTL-SDR through USB OTG and the separate `marto.rtl_tcp_andro` driver, available from F-Droid.

## Quick start

1. Enter the receiver latitude and longitude in Settings.
2. Connect the RTL-SDR and tap **Start**.
3. Check receiver status and the aircraft map.

## Map and boundary data

The map uses OpenFreeMap when opened. FIR and TRACON data is bundled for offline use. Automatic boundary updates are off by default; enable them or request a manual update in Settings.

## Beast TCP export

The Beast frame pipeline is part of normal decoding. Beast TCP export is disabled by default. Enable it in **Settings → Data export → Beast TCP export** for local clients such as Termux/readsb. It listens only on `127.0.0.1`, using port `30005` by default.

## Unattended operation

Use **Settings → Start at boot** to allow boot startup. It is user-configurable; Android and ROM background restrictions can affect automatic startup.

## Privacy and network access

See [PRIVACY.md](PRIVACY.md). SkyPulse has no analytics, ads, accounts, or Google Play Services.

## License and attribution

SkyPulse is GPL-3.0-or-later. Decoder, map, and boundary-data attribution is in [NOTICE.md](NOTICE.md).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).
