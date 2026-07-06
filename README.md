# PulseKit

[![Test](https://github.com/alirezajavan/PulseKit/actions/workflows/test.yml/badge.svg)](https://github.com/alirezajavan/PulseKit/actions/workflows/test.yml)
[![Release](https://github.com/alirezajavan/PulseKit/actions/workflows/release.yml/badge.svg)](https://github.com/alirezajavan/PulseKit/actions/workflows/release.yml)
[![Maven Central (Core)](https://img.shields.io/maven-central/v/io.github.alirezajavan/pulsekit-core?label=pulsekit-core)](https://central.sonatype.com/artifact/io.github.alirezajavan/pulsekit-core)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A Kotlin Multiplatform (Android + iOS) library for continuous or periodic, multi-day background
sensor tracking — location, motion, step count, and BLE scanning — with batched local persistence
and a pluggable sync engine that never assumes your backend's wire format. Each data source
describes its own permissions, so the library handles manifest validation, foreground-service
typing, and the permission-request UI for you.

## Project Structure

Follows the **Now In Android**-style modular architecture, adapted for Kotlin Multiplatform:

- **`pulsekit-core`**: Tracking engine, SQLDelight-backed batched/bounded persistence, the
  cross-platform `PermissionController` abstraction, and Android foreground-service +
  boot-resume infrastructure.
- **`pulsekit-location`**: GPS/network location `DataSource`.
- **`pulsekit-motion`**: Raw accelerometer `DataSource` and step-counter `DataSource`.
- **`pulsekit-bluetooth`**: BLE scan `DataSource`.
- **`pulsekit-sync`**: Inversion-of-control network sync engine (`SyncEngine` handles retry/
  backoff; `SyncUploader` is the contract your app implements for its own backend, with
  `JsonHttpSyncUploader` provided as a ready-made default).
- **`pulsekit-ui`**: Android infrastructure (Services, Receivers) and Jetpack Compose layer over
  `PermissionController`'s staged request flow -- pull this in if you want a ready-made
  `PermissionGate` composable instead of hand-writing the sequencing.
- **`app`**: Android demo app wiring all of the above together end-to-end.

Feature modules depend only on `pulsekit-core`, never on each other — if you only need location
tracking, you only pull in `pulsekit-core` + `pulsekit-location`.

## Features

- Continuous **or periodic** background collection designed to survive for days: Android
  foreground service with `START_STICKY` + periodically-renewed wake lock, boot-resume via a
  `BroadcastReceiver`; iOS background location as the anchor that keeps motion/BLE sampling alive.
  Per-source `CollectionMode.Periodic(interval, window)` collects in bursts (e.g. scan BLE 30s
  every 5min) for power-sensitive sources.
- **Per-source control**: `startSources(setOf("location"))` / `stopSources(...)` begin or end
  individual sources on demand, with `activeSourceIds: StateFlow<Set<String>>` as an observable,
  UI-bindable single source of truth for what's collecting.
- **Self-describing data sources**: each `DataSource` declares its own `displayName`,
  `requiredPermissions`, and `optionalPermissions`, so the library derives manifest validation,
  foreground-service typing, and the permission-request UI for you — the app never hardcodes which
  permissions a source needs.
- **Batteries-included Android service**: extend `BasePulseKitService`, override only `pulseKit`,
  and get the whole foreground-service lifecycle, a customizable notification, wake-lock renewal,
  and permission-masked service typing for free.
- Batched, bounded local persistence (SQLDelight) — high-frequency sensor bursts are coalesced
  into transactions instead of one write per sample, with a count-based prune cap
  (`PulseKitConfig.maxStoredEvents`) and an optional age-based cap
  (`PulseKitConfig.maxEventAgeMillis`) so an offline device can't grow the database unbounded.
- `PulseKit.eraseAllData()` for right-to-erasure (GDPR/CCPA) requests — unconditionally removes
  every stored row, regardless of sync status, distinct from routine pruning.
- A single cross-platform `PermissionController` that encodes the OS-mandated staged permission
  flows for you: Android's foreground-then-background location sequence (10+ requires this as two
  separate calls) and iOS's when-in-use-then-always progression.
- Sync is entirely inversion-of-control: `SyncEngine` owns claim/retry/backoff, `SyncUploader` is
  the one interface you implement for your own backend's wire format — the library never assumes
  JSON-over-HTTP unless you opt into the provided `JsonHttpSyncUploader`.
- `SyncEngine.observeState(): StateFlow<SyncState>` for a UI-ready sync status (last success time,
  last error, consecutive failure count) instead of guessing at internal retry state.
- A pluggable `PulseKitLogger` (`PulseKit.Builder.logger(...)`, also accepted by `SyncEngine`) so
  you can route PulseKit's internal diagnostics (prune passes, failed uploads) through your own
  Timber/Crashlytics/etc. — silent by default.

## Installation

Add whichever modules you need to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.alirezajavan:pulsekit-core:0.1.0")
    implementation("io.github.alirezajavan:pulsekit-location:0.1.0")
    implementation("io.github.alirezajavan:pulsekit-motion:0.1.0")
    implementation("io.github.alirezajavan:pulsekit-bluetooth:0.1.0")
    implementation("io.github.alirezajavan:pulsekit-sync:0.1.0")
    implementation("io.github.alirezajavan:pulsekit-ui:0.1.0") // optional
}
```

## Usage

### 1. Build a `PulseKit` instance

```kotlin
val database = createPulseKitDatabase(applicationContext)

val pulseKit = PulseKit.builder(database)
    .addDataSource(LocationDataSource(applicationContext))
    .addDataSource(MotionDataSource(applicationContext))
    .addDataSource(StepCounterDataSource(applicationContext))
    // Periodic sources collect in bursts instead of continuously:
    .addDataSource(
        BluetoothDataSource(applicationContext),
        mode = CollectionMode.Periodic(intervalMillis = 5.minutes.inWholeMilliseconds,
                                       windowMillis = 30.seconds.inWholeMilliseconds),
    )
    .build()
```

Drive collection through the platform host (Android `Service` or iOS `IosPulseKitHost`) to
ensure the app process is kept alive for background collection:

```kotlin
// Android: Start the tracking service
BasePulseKitService.startCollection(context, MyTrackingService::class)

// iOS: Start the keep-alive host
val host = IosPulseKitHost(pulseKit)
host.start()
```

Location/motion data is sensitive (GDPR/CCPA-relevant). By default `createPulseKitDatabase(context)`
uses a plain, unencrypted driver -- if your app needs at-rest encryption, call the
`createPulseKitDatabase(driver: SqlDriver)` overload with your own driver instead, e.g. one
wrapping SQLCipher-for-Android's `SupportOpenHelperFactory`. PulseKit doesn't bundle SQLCipher
itself since there's no equivalent turnkey story on iOS.

### 2. (Android) Define a foreground service

`BasePulseKitService` owns the entire collection lifecycle, wake lock, foreground-service typing
and a default notification — a subclass usually just points it at the shared instance:

```kotlin
class MyTrackingService : BasePulseKitService() {
    override val pulseKit get() = MyApp.from(this).pulseKit

    // Everything below is optional — override to customize the tray notification (or override
    // createForegroundNotification() wholesale for full control):
    override val notificationContentTitle = "Tracking active"
    override val notificationSmallIcon = R.drawable.ic_tracking
}
```

Declare it (and, optionally, a `PulseKitBootReceiver` subclass to resume after reboot) in your
manifest — see [Platform setup](#android) below.

### 3. (iOS) Wrap in a Keep-Alive Host

On iOS, there is no background service. To keep the app alive for continuous sensor collection,
use `IosPulseKitHost` from `pulsekit-ui`:

```kotlin
val host = IosPulseKitHost(pulseKit)
host.start() // Requests 'Always' location and keeps process awake
```

Ensure your `Info.plist` declares the `location` UIBackgroundModes — see [Platform setup](#ios) below.

### 4. (Android) Validate your manifest/service/receiver wiring at startup

`PulseKit.Builder.validateAndroidSetup(...)` cross-checks your app's manifest against the data
sources you've added and throws one exception listing every gap found -- missing permissions, a
service/receiver class that isn't declared as a component, or a `<service>` missing a required
`foregroundServiceType` flag -- instead of letting a setup mistake surface later as an opaque
`SecurityException` from `startForegroundService()`:

```kotlin
pulseKit = PulseKit.builder(database)
    .addDataSource(LocationDataSource(applicationContext))
    .addDataSource(MotionDataSource(applicationContext))
    .addDataSource(StepCounterDataSource(applicationContext))
    .addDataSource(BluetoothDataSource(applicationContext))
    .validateAndroidSetup(
        context = applicationContext,
        serviceClass = MyTrackingService::class,       // your BasePulseKitService subclass
        bootReceiverClass = MyBootReceiver::class,      // optional; your PulseKitBootReceiver subclass
    )
    .build()
```

If your app deliberately has no `BasePulseKitService` subclass, pass `serviceClass = null` (the
default): permission/manifest checks still run, and instead of failing, PulseKit logs a warning
through the builder's configured logger that collection will only work while the app is in the
foreground -- without a foreground service the OS is free to suspend the process (and every
sensor callback with it) as soon as the app is backgrounded.

This only checks *declarations* -- it says nothing about whether the user has actually granted a
permission at runtime. That's `PermissionController`'s job, below.

### 4. Request permissions — the source describes what it needs

You don't enumerate a source's permissions yourself. Each `DataSource` declares its own
`requiredPermissions`/`optionalPermissions`, and `PermissionController` encodes the OS-mandated
staged ordering (foreground location before background, etc.). If your UI is Compose, the
`pulsekit-ui` module turns a source straight into a permission-first flow:

```kotlin
@Composable
fun SourceButton(source: DataSource, controller: PermissionController, onStart: () -> Unit) {
    // Derives the whole staged request (required -> optional -> session permissions) from the
    // source itself — no permission list in your app code.
    val permissions = rememberDataSourcePermissionState(controller, source)

    Button(onClick = {
        permissions.request { canCollect -> if (canCollect) onStart() }
    }) { Text("Collect ${source.displayName}") }

    if (permissions.missingRequired.isNotEmpty()) Text("Needs: ${permissions.missingRequired}")
}
```

`canCollect` is `true` only once every `requiredPermissions` is granted; denied *optional*
permissions (background location, notifications, battery exemption) surface via `missingOptional`
so you can show "collecting without …" instead of blocking. Render one button per
`pulseKit.dataSources` entry and you have the whole screen — see `MainActivity.kt` in `app/`.

Prefer to drive it by hand? `PermissionController.request(Permission.LOCATION_FOREGROUND)` returns
a `PermissionStatus` and handles the staged ordering; `PermissionGate` /
`rememberPermissionGateState` are the lower-level generic building blocks if you want to list
permissions explicitly.

### 5. Sync collected events to your own backend

```kotlin
// Bring your own wire format:
class MyBackendUploader(private val api: MyApiClient) : SyncUploader {
    override suspend fun upload(batch: List<SensorEventLog>): Boolean =
        api.postEvents(batch.map { it.toMyWireFormat() })
}

val syncEngine = SyncEngine(pulseKit.syncSource, MyBackendUploader(api))
syncEngine.start(applicationScope)

// Or, if a simple JSON-over-HTTP endpoint is enough:
val syncEngine = SyncEngine(pulseKit.syncSource, JsonHttpSyncUploader(HttpClient(), endpointUrl))

// Observe sync status for your own UI:
syncEngine.observeState().collect { state ->
    // state.isSyncing, state.lastSuccessTimestampMillis, state.lastError, state.consecutiveFailures
}
```

## Platform setup

### Android

PulseKit follows a **Zero-Manifest** policy. Library modules do not declare any permissions or
components in their own manifests. Your app **must** explicitly declare everything it needs in its
`AndroidManifest.xml`:

- **Infrastructure**: `FOREGROUND_SERVICE`, `WAKE_LOCK`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`.
- **Permissions**: Every permission required by your data sources (e.g., `ACCESS_FINE_LOCATION`,
  `ACTIVITY_RECOGNITION`, `FOREGROUND_SERVICE_LOCATION`).
- **Components**: Your `BasePulseKitService` subclass and `PulseKitBootReceiver` subclass.

Use `pulseKit.validateAndroidSetup(...)` from `pulsekit-ui` at startup to catch any missing
manifest declarations early with a descriptive error.

### iOS

There's no `iosApp`/`Info.plist` in this repo — those live in your consuming app. Add:

- `NSLocationWhenInUseUsageDescription` and `NSLocationAlwaysAndWhenInUseUsageDescription`
- `NSMotionUsageDescription`
- A `UIBackgroundModes` array containing `location` (this is what keeps motion/BLE sampling alive
  in the background too — there's no separate background mode for them on iOS)

## Contributing

`./gradlew build` runs the full verification suite (all KMP targets, unit tests, API compatibility
check). A pre-commit hook is provided for fast local checks before every commit:

```sh
git config core.hooksPath scripts/git-hooks
```

See `CLAUDE.md` for architecture conventions and `REFACTOR_PLAN.md` for the current roadmap.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
