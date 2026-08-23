# Contributing to SkyPulse

## Prerequisites

- JDK 17 or later
- Android SDK with API 36
- The Gradle wrapper

## Build

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
```

## Architecture

```text
rtl_tcp_andro → RTL-TCP client → decoder → AircraftRepository → UI/map
                                                              → optional Beast server
```

## Boundary data

Regenerate bundled boundary assets with the scripts described in [tools/README.md](tools/README.md).

## F-Droid checks

```bash
fdroid lint
fdroid scanner
fdroid build --test
```
