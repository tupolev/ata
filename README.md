# ATA — Android Tracker Alarm

ATA is an experimental Android app that monitors Google Find Hub compatible trackers and raises a local movement alarm when an armed tracker leaves its safe zone.

## v0.1

- Native Kotlin + Jetpack Compose UI
- Native Google authentication and Find Hub encryption-unlock flow
- Import an existing GoogleFindMyTools `Auth/secrets.json`
- Secrets copied into encrypted Android storage; the imported file is not retained
- List Find Hub trackers
- Fetch and decrypt crowdsourced locations
- Arm/disarm each tracker at its latest known position
- Per-tracker safe radius, immediate-alarm threshold, confirmation count, polling interval and stale timeout
- Duplicate/old reports ignored by timestamp
- Foreground monitoring service, allowing a default 5-minute interval
- High-priority local movement notifications
- Local recent history (up to 50 observations per tracker)

### Default movement policy

| Condition | State |
|---|---|
| <= 120 m from armed point | SAFE |
| > 120 m, first new report | SUSPICIOUS |
| > 120 m, two consecutive new reports | ALARM |
| >= 300 m | immediate ALARM |
| report older than 30 min | STALE |

These values are configurable per tracker.

## Authentication

ATA supports two setup paths.

### Native setup

Choose **Sign in with Google**. ATA completes the Google account flow and then asks to unlock the Find Hub E2EE key.

### Import GoogleFindMyTools credentials

Choose **Import GoogleFindMyTools secrets.json** and select the `Auth/secrets.json` file created by GoogleFindMyTools.

ATA currently imports:

- `username`
- `aas_token`
- `shared_key`
- `owner_key`

The values are stored using Android encrypted preferences backed by the Android Keystore. Never commit or share `secrets.json`.

## Background monitoring

Android periodic WorkManager jobs have a 15-minute minimum interval. ATA therefore uses a foreground service while monitoring is active, with a persistent notification. The default polling interval is 5 minutes.

## Build

Open the project in a current Android Studio installation, or use Gradle 9.5+ with JDK 17:

```bash
gradle assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Status and caveats

ATA is unofficial and relies on internal Google Find Hub services. Those interfaces can change without notice.

The initial release is intentionally conservative about movement detection because crowdsourced Find Hub observations can jump tens of metres even while a tracker is stationary.

## Credits

The Find Hub authentication, Nova/Spot access, FCM/MCS and location-decryption layer is adapted from [Traccar Relay](https://github.com/traccar/traccar-relay), licensed under Apache License 2.0.

Development and behaviour were also validated against [GoogleFindMyTools](https://github.com/leonboe1/GoogleFindMyTools).
