# Changelog

All notable changes to PulseKit are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). This project will adopt
[Semantic Versioning](https://semver.org/) starting at the first published release (`0.1.0`);
until then, everything lives under `[Unreleased]` and the public surface can still shift.

## [Unreleased]

### Fixed
- `pulsekit-location` (Android): `LocationDataSource.start()` crashed with
  `RuntimeException: Can't create handler inside thread ... that has not called Looper.prepare()`
  when started from `PulseKit.start()` (which launches sources on `Dispatchers.Default`). The
  `requestLocationUpdates` overload without an explicit `Looper` binds the listener to the calling
  thread's Looper; callbacks are now pinned to the main Looper explicitly.
- `PulseKit.start()`/`stop()` could race the engine's unsynchronized start guard when driven from
  more than one place. `BasePulseKitService` is now the single owner of the collection lifecycle
  (serialized on a single-threaded dispatcher), and the activity only sends it intents.
- `TrackingEngineStressTest` orphaned its engine coroutine scope (real single-thread executor)
  after each test, so a still-finishing loop could touch the Robolectric SQLite connection after
  it closed ("Illegal connection pointer"), intermittently failing an unrelated later test. It now
  cancels and joins the scope, and shuts the executor down, in `@After`.

### Added
- `pulsekit-core`: **periodic collection**. `PulseKit.Builder.addDataSource(source, mode)` takes a
  `CollectionMode` — `Continuous` (default) or `Periodic(intervalMillis, windowMillis)`, which
  collects in bursts (e.g. scan BLE 30s every 5min) and self-heals a skipped cycle (denied
  permission, adapter off) on the next interval.
- `pulsekit-core`: per-source collection control on `PulseKit` — `startSources(setOf("location"))`
  / `stopSources(...)` start/stop individual `DataSource`s (by id) so an app can begin collecting
  GPS the moment its permissions are granted and add Bluetooth/motion/steps later; `dataSources`
  exposes the attached sources, and `activeSourceIds: StateFlow<Set<String>>` is an observable
  single source of truth for what is actually collecting (survives UI recreation). `start()`/
  `stop()` keep their all-sources behavior.
- `pulsekit-core`: `DataSource` now describes itself — `displayName`, `requiredPermissions`,
  `optionalPermissions` — and `start()` returns `Boolean` (`false` when a precondition is missing).
  Android manifest/foreground-service-type validation and the compose permission flow are all
  derived from these, so a new source needs no per-source registration anywhere else.
- `pulsekit-core`: `BasePulseKitService` now owns the entire foreground-service lifecycle. A
  subclass only overrides `pulseKit`; collection start/stop (`startCollection`/`stopCollection`
  companion helpers), a fully customizable default notification (open `notification*` properties
  or `createForegroundNotification()`), and permission-masked foreground-service typing (only
  claims a service type whose runtime permission is currently held) are all handled in the base.
- `pulsekit-compose-permissions`: `rememberDataSourcePermissionState(controller, dataSource)`
  derives the entire staged permission flow (required → optional → session) from the source itself,
  so the client never assembles permission lists. `Permission.backgroundSessionPermissions` names
  the session-level extras (notifications + battery exemption).
- `pulsekit-core`: `validateAndroidSetup`'s `serviceClass` is now optional (`null` by default).
  Passing `null` means the app deliberately has no `BasePulseKitService` subclass: permission and
  manifest checks still run, and instead of failing, a warning is logged through the builder's
  configured logger that collection only works while the app is in the foreground.
- `pulsekit-location`/`pulsekit-motion`/`pulsekit-bluetooth`: every silent `start()` no-op path
  (missing runtime permission, disabled provider/adapter, absent sensor) now logs a `warn` naming
  exactly why the source did not start, and returns `false`, instead of failing silently.
- Demo app: permission-first, per-source UI rendered straight from `PulseKit.dataSources` — no
  hardcoded source ids, labels, or permission lists in the app. Each "Collect X" button requests
  that source's own permissions and starts only it; the "Stop collecting" button enables only
  while something is collecting; collection state is read from `activeSourceIds` so it stays
  correct across close/reopen and rotation.

### Changed
- Demo app `AndroidManifest.xml` no longer redeclares runtime permissions — `pulsekit-core`
  declares every permission it needs and manifest-merging propagates them, so a consuming app only
  declares its service/receiver/activity (plus the foreground-service-type permissions, a lint
  workaround). Demonstrates the intended minimal client setup.
- `pulsekit-compose-permissions` (new module): optional Jetpack Compose layer over
  `PermissionController`'s staged request flow. `rememberPermissionGateState` tracks per-permission
  status and drives a sequential `requestAll()` (awaiting each permission before requesting the
  next, so the order callers pass in is the staged sequence -- e.g. background location only
  requested after foreground is granted); `PermissionGate` is a ready-to-use composable built on
  top of it that renders `content` once every listed permission is granted, or a default/overridable
  rationale + "Grant permissions" button otherwise. Pure `commonMain` (compiles for Android and iOS)
  since it only depends on `pulsekit-core`'s existing cross-platform `PermissionController`/
  `Permission`/`PermissionStatus` types -- callers still construct the platform-specific
  `PermissionController` themselves, matching this library's existing IoC pattern rather than
  the module reaching for a platform `Context` on its own.
- `pulsekit-core`: `PulseKit.Builder.validateAndroidSetup(context, serviceClass, bootReceiverClass)`
  (`io.github.alirezajavan.pulsekit.core.setup`) cross-checks the app's merged manifest against
  the data sources already added to the builder -- missing `uses-permission` entries, a
  service/receiver class not declared as a component, or a `<service>` missing a required
  `foregroundServiceType` flag all throw one `PulseKitSetupException` listing every gap found.
  Catches setup mistakes at `Application.onCreate()` with a specific message instead of letting
  them surface later as an opaque `SecurityException`/`ClassNotFoundException` from
  `startForegroundService()`. Android-only (manifest introspection has no iOS equivalent); doesn't
  check runtime grant state, which stays `PermissionController`'s job.
- `pulsekit-core`: `PulseKitLogger` interface (`debug`/`warn`/`error`) + `NoOpPulseKitLogger`
  default, wired in via `PulseKit.Builder.logger(...)` -- `TrackingEngine` logs every prune pass
  that actually removes rows
- `pulsekit-sync`: `SyncEngine` now takes an optional `PulseKitLogger` and logs a `warn` on every
  failed upload attempt (previously the exception was silently swallowed); `SyncEngine
  .observeState(): StateFlow<SyncState>` exposes `isSyncing`/`lastSuccessTimestampMillis`/
  `lastError`/`consecutiveFailures` so client apps can build their own sync-status UI
- `pulsekit-location`/`pulsekit-motion`/`pulsekit-bluetooth`: every `DataSource` (`LocationDataSource`,
  `MotionDataSource`, `StepCounterDataSource`, `BluetoothDataSource`) now takes an optional
  `PulseKitLogger` and logs a `warn` whenever a sample is dropped because its internal event
  buffer is full and nothing is currently collecting fast enough (previously silent)
- `pulsekit-core`: `createPulseKitDatabase(driver: SqlDriver): PulseKitDatabase` is now public
  (previously an unreachable `private` overload) -- lets an app supply its own `SqlDriver`, e.g.
  one wrapping SQLCipher-for-Android's `SupportOpenHelperFactory`, for at-rest encryption of
  sensitive location/motion data instead of the library's default plain driver
- `pulsekit-core`: `PulseKit` builder API, `TrackingEngine`-backed batched/bounded persistence,
  `SensorPayload` sealed type (`Location`, `MotionChunk`, `BluetoothScan`, `StepCount`),
  cross-platform `PermissionController` modeling Android's staged foreground->background
  location request and iOS's when-in-use->always progression, plus `BLUETOOTH_SCAN` and
  `ACTIVITY_RECOGNITION`
- `pulsekit-core`: `Permission.IGNORE_BATTERY_OPTIMIZATIONS`, checking/requesting exemption from
  OEM/stock-Android battery-optimization deferral (`PowerManager.isIgnoringBatteryOptimizations`,
  `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) via the same `PermissionController` shape as
  every other permission -- relevant to this library's continuous-collection-for-days use case
  since a foreground service alone doesn't stop some OEM skins from throttling it. No-op
  `GRANTED` on iOS, which has no equivalent per-app exemption concept.
- `pulsekit-core`: `PulseKitConfig.maxEventAgeMillis` for age-based retention (alongside the
  existing count-based `maxStoredEvents` cap), and `PulseKit.eraseAllData()` for right-to-erasure
  (GDPR/CCPA) requests -- unconditionally removes every stored row regardless of sync status,
  unlike routine pruning which only ever removes the oldest rows
- `pulsekit-location`: GPS/network location `DataSource`
- `pulsekit-motion`: raw accelerometer `DataSource` and step-counter `DataSource`
- `pulsekit-bluetooth`: BLE scan `DataSource`
- `pulsekit-sync`: inversion-of-control `SyncUploader` interface, `SyncEngine` (retry/backoff),
  `JsonHttpSyncUploader` default implementation
- Android: foreground service + wake lock infrastructure (`BasePulseKitService`), boot-resume
  (`PulseKitBootReceiver`)
- iOS: background location continuity (`allowsBackgroundLocationUpdates`)
- `binary-compatibility-validator` klib ABI checks (`apiCheck`, wired into `check`/`build`) for
  every library module (not `app`), with baseline dumps checked in under each module's `api/`
  directory

### Fixed
- Crash: `MainActivity`'s `PermissionController` was constructed lazily, after the activity was
  already `STARTED` -- `registerForActivityResult` requires registration before that point
- Race: `MainActivity` and `PulseKitTrackingService` could both call `pulseKit.stop()`
  concurrently; the service is now the sole owner of stopping `PulseKit`
- Reliability: `PulseKitTrackingService.onDestroy()` no longer fire-and-forgets `pulseKit.stop()`
  -- it now runs to completion before the service tears down
- Race: `MotionDataSource` (Android) could corrupt/lose an in-flight sensor sample if `stop()`
  cleared its buffer concurrently with a sensor callback on another thread
- Perf: `MotionDataSource` (iOS) now delivers accelerometer samples on a dedicated serial queue
  instead of `NSOperationQueue.mainQueue`, avoiding UI-thread contention at high sampling rates
- Concurrency: `PermissionController` (Android and iOS) now serializes `request()` calls with a
  `Mutex` -- overlapping calls used to silently clobber each other's pending continuation, hanging
  the first caller forever
- Edge case: `BasePulseKitService`'s wake lock is now periodically renewed instead of a single
  flat 24h `acquire()`, so multi-day tracking sessions don't silently lose the lock partway through

### Added (tests)
- `pulsekit-core`, `pulsekit-sync`: first test source sets in the repo (`commonTest`, running
  against the Android host target via `testAndroidHostTest`) -- `SensorPayloadMapper` round-trip
  coverage for every `SensorPayload` variant, and `SyncEngine`'s retry/backoff state machine
  against fakes, using `kotlinx-coroutines-test` virtual time
- `pulsekit-core`: Robolectric-backed `SensorEventStoreTest`/`TrackingEngineTest` against a real
  in-memory `AndroidSqliteDriver` (insert/claim/prune atomicity and ordering, ingestion batching by
  size and by flush interval, prune-loop overflow math, `start()`/`stop()` idempotency)
- `pulsekit-core`: `TrackingEngineStressTest` -- high-frequency/high-volume simulation with real
  concurrent producer threads (not virtual time), verifying no data loss, no duplicate ids, no
  main-thread-equivalent blocking on `logSensorEvent` even when persistence lags far behind, and
  bounded storage under sustained load past `maxStoredEvents`
- `pulsekit-core`: `PermissionControllerTest` -- Robolectric-backed coverage of the Android actual's
  already-granted short-circuits (`Permission.LOCATION_BACKGROUND` never re-requesting once both
  foreground and background are already granted), the pre-API-29 implied-background behavior, and
  the rationale-based `DENIED` vs `DENIED_PERMANENTLY` split

### Fixed (found by the stress test above)
- `SensorEventStore.claimPendingBatch`/`updateSyncStatus`/`deleteEvents` built a single
  `WHERE id IN (...)` SQL statement from an unbounded id list, which throws a runtime
  `SQLiteException` once past SQLite's per-statement bound-parameter cap (`SQLITE_MAX_VARIABLE_
  NUMBER`, historically 999 on some Android devices) -- e.g. a large `SyncConfig.batchSize`, or
  a normal backlog built up while offline. Fixed by chunking every id-list query into batches of
  900, still inside one transaction.

### Infrastructure
- CI: `.github/workflows/test.yml` (runs on every pull request) and `.github/workflows/
  release.yml` (runs on push to `master`, version-gated by git tag, publishes to Maven Central)
- Maven Central publishing wired into every publishable module via `com.vanniktech.maven.publish`
  + Dokka; coordinates and POM metadata centralized in `gradle.properties`
- `scripts/git-hooks/pre-commit` (install with `git config core.hooksPath scripts/git-hooks`):
  runs `apiCheck` + `testAndroidHostTest` before every commit
- `README.md` and `LICENSE` (Apache License 2.0) added

### Notes
- `./gradlew build`/`ktlintCheck` currently fails on pre-existing line-length and filename
  violations across several files that predate ktlint's adoption in this repo (e.g.
  `PermissionController.kt` on both platforms, `TrackingEngineTest.kt`,
  `PulseKitService.kt`/`BasePulseKitService` filename mismatch, one line in
  `pulsekit-bluetooth/BluetoothDataSource.kt`) -- not introduced by this session's changes (verified
  via `git status` before editing), left as-is rather than folded into an unrelated diff. Worth a
  dedicated cleanup pass.
- The Android target isn't covered by `apiCheck` yet -- the klib ABI dump only validates the
  iOS (`iosArm64`/`iosSimulatorArm64`) surface, since `binary-compatibility-validator` doesn't
  support the newer `com.android.kotlin.multiplatform.library` Android target. The Android
  public surface still needs manual review until that gap closes upstream.
- No release has been published yet. The CI/publishing pipeline is wired up (see above), but
  actually cutting a release additionally needs Maven Central + signing secrets configured in the
  GitHub repo's environment, which is outside what could be done from this session.
