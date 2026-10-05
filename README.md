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

## Authentication and first-time setup

ATA supports two setup paths:

1. **Native setup inside ATA** — sign in with Google and unlock Find Hub encryption.
2. **Import an existing GoogleFindMyTools `Auth/secrets.json`** — recommended for the first v1 tests because it uses the exact authentication flow already proven to work with your account.

### Option A — create `secrets.json` with GoogleFindMyTools and import it into ATA

This is the recommended v0.1 path.

#### 1. Prepare GoogleFindMyTools on a computer

Clone the upstream project:

```bash
git clone https://github.com/leonboe1/GoogleFindMyTools.git
cd GoogleFindMyTools
```

Create and activate a Python virtual environment:

```bash
python3 -m venv venv
source venv/bin/activate
```

Install its dependencies:

```bash
pip install -r requirements.txt
```

Run it:

```bash
python3 main.py
```

On the first run, GoogleFindMyTools opens Chrome and asks you to authenticate with the same Google account that owns the Find Hub trackers.

Depending on your Chrome / ChromeDriver combination, you may need to point the project at the installed Chrome binary or force the matching major ChromeDriver version. This is a GoogleFindMyTools setup issue, not an ATA requirement.

#### 2. Complete one full Find Hub location request

Do not stop after only listing the trackers. Select at least one tracker and request/decrypt its location successfully.

A successful run should show:

- the available Find Hub trackers;
- a successful shared-key retrieval;
- a successful owner-key retrieval;
- one or more decrypted locations.

Example output looks broadly like:

```text
The following trackers are available:
1. MyTracker: ...

Retrieved owner key with version: 1

[DecryptLocations] Decrypted Locations:
Latitude: ...
Longitude: ...
Time: ...
Status: ...
```

This full pass ensures that GoogleFindMyTools has cached the reusable authentication and encryption material ATA needs.

#### 3. Locate the generated file

GoogleFindMyTools stores the authentication results at:

```text
GoogleFindMyTools/Auth/secrets.json
```

You can verify that it exists without displaying its contents:

```bash
ls -lh Auth/secrets.json
```

Do **not** paste the contents into an issue, chat, README, Git commit, screenshot or forum post.

Treat `secrets.json` like a password. It can contain reusable account authentication material and Find Hub E2EE keys.

ATA currently imports these values when present:

- `username`
- `aas_token`
- `shared_key`
- `owner_key`
- `fcm_credentials`

When `fcm_credentials` is present, ATA converts and reuses the existing GoogleFindMyTools GCM/FCM registration and encryption keys instead of immediately creating a new registration. This makes the recommended import path more self-contained and reduces dependence on build-time FCM registration.

ATA does not retain the original JSON after importing the required values.

#### 4. Transfer `secrets.json` to the Android device

Copy the file to a location that Android's document picker can access, for example:

- Downloads;
- a temporary USB transfer;
- a private cloud-drive folder you control.

Avoid sending it through public or shared channels.

Once ATA has imported the file successfully, you can remove that copied JSON from the phone if you no longer need it there.

#### 5. Import it in ATA

Open ATA and choose:

```text
Import GoogleFindMyTools secrets.json
```

Then select the file with Android's system document picker.

ATA copies the required values into **encrypted Android storage backed by the Android Keystore**. ATA does not keep the original JSON file as its credential store.

After a successful import ATA should:

1. reuse the stored Google/Find Hub credentials;
2. reuse imported FCM credentials when available, or obtain a new app-specific FCM registration if needed;
3. load the trackers belonging to the account;
4. show them on the **Trackers** screen.

If ATA reports that the imported file is missing the reusable AAS or owner key, return to GoogleFindMyTools and complete a real location/decryption request first, then import the updated `Auth/secrets.json`.

### Option B — native Google setup inside ATA

Choose **Sign in with Google**.

ATA opens the Google authentication flow and then performs the Find Hub E2EE unlock step. When successful, ATA stores the resulting credentials in the same encrypted Android storage used by the import flow.

This path is more convenient, but the Google web-authentication flow is also the part most likely to change upstream. For early v0.1 testing, importing a known-good GoogleFindMyTools `secrets.json` is the safer path.

## Using ATA

### 1. Load your trackers

After authentication, open **Trackers**. ATA queries Find Hub and lists the compatible trackers available to the account.

Press **Refresh** on a tracker to request its latest Find Hub location.

The first location request can take several seconds because ATA sends the Find Hub request and waits for the corresponding FCM/MCS response.

### 2. Arm a tracker

ATA needs at least one usable location before it can establish an armed position.

For the tracker you want to monitor:

1. press **Refresh**;
2. wait for a valid location;
3. press **Arm here**.

The latest valid tracker position becomes the reference point.

ATA does **not** use the phone GPS as the armed point. The reference comes from the tracker's own latest Find Hub observation.

### 3. Start background monitoring

Open the **Monitoring** tab and press:

```text
Start monitoring
```

On Android 13+ ATA asks for notification permission.

While monitoring is active ATA runs a foreground service and displays a persistent notification. This is intentional: Android's normal periodic background jobs cannot reliably provide the default 5-minute polling interval.

You can stop it with **Stop service**.

### 4. Understand the states

The default policy is:

| Condition | ATA state |
|---|---|
| up to 120 m from the armed point | `SAFE` |
| first new observation beyond 120 m | `SUSPICIOUS` / `CHECK` |
| two consecutive new observations beyond 120 m | `ALARM` |
| one new observation at or beyond 300 m | immediate `ALARM` |
| newest observation older than 30 min | `STALE` |

ATA ignores reports whose timestamp is not newer than the last processed report. This prevents the same Find Hub observation from counting twice even if Google returns it more than once with different metadata/status values.

### 5. Configure each tracker

In **Monitoring**, each armed tracker has its own settings:

- **Safe radius** — default 120 m;
- **Immediate alarm distance** — default 300 m;
- **Outside reports before alarm** — default 2;
- **Polling interval** — default 5 min;
- **Stale timeout** — default 30 min.

The conservative defaults are deliberate. Crowdsourced Find Hub locations can jump by tens of metres, or occasionally around 100 m, even while a tracker is stationary.

### 6. Alarm behaviour

When movement reaches the configured alarm threshold, ATA raises a high-priority local notification identifying the tracker and its approximate distance from the armed point.

Disarming the tracker resets the suspicious counter and stops evaluating new positions against that armed point.

## Credential security

Never commit or share `secrets.json`.

Also keep these values private:

- OAuth/AAS tokens;
- `shared_key`;
- `owner_key`;
- generated FCM credentials.

The public repository must not contain personal tracker IDs, coordinates, tokens or imported credential material.


## Background monitoring

Android periodic WorkManager jobs have a 15-minute minimum interval. ATA therefore uses a foreground service while monitoring is active, with a persistent notification. The default polling interval is 5 minutes.

## Build-time Find Hub API key

ATA does not commit the Google/FCM client API key to the repository because GitHub Secret Scanning flags Google-style `AIza...` keys even when they are public client identifiers inherited from the upstream protocol implementation.

For local builds, provide the key as a Gradle property or environment variable named:

```text
ATA_GOOGLE_API_KEY
```

Example with `~/.gradle/gradle.properties`:

```properties
ATA_GOOGLE_API_KEY=your_key_here
```

or for one shell session:

```bash
export ATA_GOOGLE_API_KEY='your_key_here'
gradle assembleDebug
```

For GitHub Actions, create a repository Actions secret named:

```text
ATA_GOOGLE_API_KEY
```

under **Settings → Secrets and variables → Actions → New repository secret**.

The normal CI build can still compile without the value. Runtime creation of a **new** Find Hub FCM registration requires it. A complete GoogleFindMyTools `secrets.json` that already contains reusable `fcm_credentials` can avoid that registration step on the recommended import path.

### Automatic APK releases

The repository includes `.github/workflows/release.yml`.

When a tag such as `v0.1.0` is pushed, GitHub Actions:

1. builds an installable debug-signed APK;
2. renames it to `ATA-v0.1.0.apk`;
3. creates a GitHub Release automatically;
4. attaches the APK to that release.

Make sure `ATA_GOOGLE_API_KEY` exists as a GitHub Actions secret before creating the release tag.

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
