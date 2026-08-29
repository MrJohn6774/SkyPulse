# Notices and attribution

## SkyPulse licensing

SPDX-License-Identifier: GPL-3.0-or-later

SkyPulse as a whole is distributed under the GNU General Public License, version 3 or later.
The complete GPLv3 text is in the root `LICENSE` file.

## FlightAware / ebcTech decoder

The Mode S/ADS-B analyzer code under
`app/src/main/java/com/flightaware/android/flightfeeder/analyzers` derives from FlightAware
ADS-B Flight Scanner and the published ebcTech dump1090 Android GPL sources.

Both upstream projects license this code under the GNU General Public License, either
version 2 or, at the recipient's option, any later version (`GPL-2.0-or-later`).

Authoritative upstream licence declarations:

- https://github.com/ebc81/dump1090andro-gpl-sources/blob/main/LICENSE.md
- https://github.com/flightaware/adsb-flight-scanner-android/blob/master/LICENSE.md

SkyPulse exercises the permitted later-version option and distributes the combined
SkyPulse application under `GPL-3.0-or-later`.

The retained provenance includes:

- Copyright (C) FlightAware, LLC.
- ebcTech modifications (C) ebcTech / ebc81, 2024-2025.
- SkyPulse modifications, 2026: removed Google `LatLng`; connected application-owned
  `GeoPoint`; added deterministic shutdown/queue clearing and worker health; moved raw
  export to the post-validation decoder stage.

Copyright and modification notices in the derived source files are preserved.

## MapLibre Native

Map rendering uses MapLibre Native Android, BSD 2-Clause licensed.
Copyright © MapLibre contributors. See `third_party/licenses/BSD-2-Clause.txt`.

## OpenFreeMap / OpenMapTiles / OpenStreetMap

The default style is OpenFreeMap Liberty. Map data attribution remains visible in MapLibre.
OpenFreeMap uses OpenMapTiles and OpenStreetMap data; OpenStreetMap data is © OpenStreetMap
contributors and available under the Open Database License.

## VATSpy and SimAware boundary data

`fir_boundaries.geojson` is adapted from the VATSIM VATSpy Data Project.
`tracon_boundaries.geojson` is adapted from the VATSIM SimAware TRACON Project.
The bundled normalized snapshots were generated from upstream revisions
`0c48fc1664cfc4b9d7f97f744654746f4b72c6b4` and
`79e7c09c62cb0591df5042abe31607b8f3273cc7`, respectively.

Both derived datasets are offered under CC BY-SA 4.0. SkyPulse modifies them by validating,
combining, removing unused properties, and compacting JSON. See
`third_party/licenses/CC-BY-SA-4.0.txt`.

## AndroidX and Material Components

AndroidX Core, AppCompat, and Material Components are Apache License 2.0 software.
See `third_party/licenses/Apache-2.0.txt`.
