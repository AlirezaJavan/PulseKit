# PulseKit roadmap

Comprehensive plan for taking PulseKit from "scaffold with a working demo" to a library other
teams can depend on.

---

## Phase 1 — Pure Logic Core & Modern Modularization (Completed)

### 1a. Architecture
- [x] **Pure Logic Core**: `:pulsekit-core` is now a pure KMP logic engine with zero Android manifests and no hosting dependencies.
- [x] **Infrastructure Module**: `:pulsekit-ui` (renamed from `pulsekit-compose-permissions`) now hosts all platform infrastructure.
- [x] **Zero-Manifest Policy**: Deleted all library-side manifests. Clients now explicitly declare all components and permissions.
- [x] **iOS Keep-Alive**: Introduced `IosPulseKitHost` to manage background-location keep-alive updates for iOS.

### 1b. Sync layer
- [x] `SyncUploader` interface (`pulsekit-sync/.../SyncUploader.kt`)
- [x] `JsonHttpSyncUploader` default impl (`pulsekit-sync/.../JsonHttpSyncUploader.kt`)
- [x] `SyncEngine` takes `SyncUploader` instead of `HttpClient`/`endpointUrl`
- [x] Delete dead `SyncWorker.kt` / `SyncResult`
- [x] Drop unused `SyncStatus.SYNCED`

### 1c. Permission abstraction
- [x] `permission/Permission.kt`, `PermissionStatus.kt` in `core` (logic only).
- [x] `PermissionController` moved to `pulsekit-ui` (requires Activity/Platform context).
- [x] androidMain actual: Activity-scoped, staged foreground→background request sequence.
- [x] iosMain actual: whenInUse->always sequence.
- [x] `backgroundSessionPermissions` is now `expect`/`actual` to provide platform-specific defaults (includes `LOCATION_BACKGROUND` on iOS).

### 1d. Android background-survival fixes
- [x] Move `BasePulseKitService` and `PulseKitBootReceiver` to `pulsekit-ui`.
- [x] `app`: concrete `PulseKitBootReceiverImpl` + manifest registration.
- [x] `PulseKitTrackingService`: fix leaked per-instance `CoroutineScope`.
- [x] `validateAndroidSetup()`: moves to `pulsekit-ui` and checks client manifest at runtime.

### 1e. Docs + verification
- [x] `CLAUDE.md`: Updated to reflect new NiA-style modularization and Zero-Manifest policy.
- [x] `README.md`: Updated usage snippets for `BasePulseKitService`, `IosPulseKitHost`, and manifest requirements.
- [x] `apiCheck` + `apiDump`: Updated for `core` and `ui` modules.
- [x] Unit tests: Moved `PermissionControllerTest` to `pulsekit-ui` and verified all tests pass.

---

## Phase 2 — Close the stated scope gap: Bluetooth

The original ask was GPS + motion + **Bluetooth**, continuously, for days.

- [x] New `pulsekit-bluetooth` module mirroring `pulsekit-location`/`pulsekit-motion`'s shape.
- [x] androidMain: `BluetoothLeScanner` implementation.
- [x] iosMain: `CBCentralManager` scanning (background limitations documented).
- [x] Permissions: `BLUETOOTH_SCAN` added to enum and controller.
- [x] Wired into `app`: `PulseKitApplication` adds `BluetoothDataSource`.
- [x] `./gradlew build` passes.

## Phase 2b — Step counter / high-sampling-rate motion sensors

- [x] `pulsekit-motion`: added `StepCounterDataSource` (`Sensor.TYPE_STEP_COUNTER` on Android, `CMPedometer` on iOS).
- [x] Added `Permission.ACTIVITY_RECOGNITION`.
- [x] Restored `FOREGROUND_SERVICE_HEALTH` in demo app manifest.

---

## Phase 3 — API stability & versioning

- [x] Added `binary-compatibility-validator` plugin.
- [x] Audited `public` vs `internal` visibility.
- [x] Adopted `CHANGELOG.md`.

---

## Phase 4 — Testing strategy

- [x] Wired up `commonTest` for `pulsekit-core`/`pulsekit-sync`.
- [x] `SensorPayloadMapper` round-trip serialization tests.
- [x] `pulsekit-sync` commonTest: `SyncEngine`'s retry/backoff.
- [x] `pulsekit-core` androidHostTest: Robolectric tests for `SensorEventStore` and `TrackingEngine`.
- [x] **High-frequency / high-volume stress tests**.
- [x] **SQLite IN-clause batching fix**.
- [x] **`MotionSampleBuffer` extraction** and testing.
- [x] **Robolectric test for `PermissionController`** (now in `pulsekit-ui`).

---

## Phase 5 — Battery, OS aggressive-kill, and data lifecycle

- [x] **Battery-optimization exemption check**: `Permission.IGNORE_BATTERY_OPTIMIZATIONS`.
- [x] **Time-based retention**: `PulseKitConfig.maxEventAgeMillis`.
- [x] **Data-at-rest evaluation**: Public `createPulseKitDatabase(driver)` for SQLCipher support.
- [x] **`PulseKit.eraseAllData()`**: For GDPR/CCPA requests.

---

## Phase 6 — Observability & CI/publishing

- [x] **`PulseKitLogger` interface**.
- [x] **`SyncEngine.observeState(): StateFlow<SyncState>`**.
- [x] **GitHub Actions**: Test and Release workflows.
- [x] **Publish setup**: Maven Central publishing config.
- [x] **Pre-commit hook**: Local `apiCheck` and tests.
- [x] **README.md** comprehensive update.

---

## Phase 7 — Network-aware sync policy (Completed)

**Problem:** `SyncConfig` (`pulsekit-sync/.../SyncConfig.kt`) is static — fixed `batchSize`,
fixed `idlePollIntervalMillis`, backoff only on upload failure. `SyncEngine` (`SyncEngine.kt`)
claims and uploads a batch every idle-poll tick regardless of connection type. A device tracked
"continuously, for days" will upload over metered cellular by default, burning the user's data
plan. This phase adds an opt-in network gate; **default behavior must not change** for existing
integrations that don't configure it.

### Steps

1. [x] **`pulsekit-sync/src/commonMain/.../NetworkType.kt`** (new file)
2. [x] **`pulsekit-sync/src/commonMain/.../NetworkMonitor.kt`** (new file)
3. [x] **`pulsekit-sync/src/commonMain/.../SyncConfig.kt`** — added `requireUnmeteredNetwork`
4. [x] **`SyncEngine.kt`** — added `networkMonitor` and gating logic
5. [x] **`SyncState.kt`** — added `waitingForNetwork`
6. [x] **`pulsekit-sync/src/commonTest/.../SyncEngineTest.kt`** — added tests for gating logic

### Edge cases to handle (and to write tests for)

- **Claim-then-strand bug**: never call `claimPendingBatch` and then decide the network is
  unsuitable — a claimed-but-never-uploaded batch sits in `PENDING_UPLOAD` until the *next*
  engine restart re-syncs it (there's no periodic re-check of stuck `PENDING_UPLOAD` rows). Check
  network **before** claiming, not after.
- **Network flips mid-upload**: `uploader.upload(batch)` may be in flight when Wi-Fi drops to
  cellular. This is fine — the existing failure path (`markFailed` + backoff) already handles a
  failed/timed-out upload; don't add a new cancellation path, it'd be redundant complexity.
- **`networkMonitor == null` with `requireUnmeteredNetwork = true`**: this is a misconfiguration
  (policy requires a check that was never wired). Treat as "network unknown" → block sync, and
  have `logger.warn` once (not every loop iteration) so it's discoverable instead of silently
  never syncing.
- **iOS `NWPathMonitor` never started**: if `start(queue:)` is never called, `currentPath` stays
  at its default/unsatisfied value forever, silently blocking all sync. Cover with a test that
  constructs `NetworkMonitor` and asserts a non-`NONE` result is reachable (requires a real or
  simulator network path — mark as a manual/CI-with-simulator check if it can't run in a unit test
  sandbox).
- **Backward compatibility**: any app already depending on `pulsekit-sync` must build and behave
  identically without touching `SyncConfig` or `SyncEngine`'s constructor — verify by NOT updating
  the `app` module's `SyncEngine` call site in this phase, and confirming `./gradlew build` still
  passes (proves the new params are additive/optional).
- **`apiCheck`**: `NetworkType`, `NetworkMonitor`, and the new `SyncState`/`SyncConfig` fields are
  new public API — run `./gradlew apiDump` for `pulsekit-sync` after implementation, not before,
  and review the generated `.api` diff for accidental exposure of platform types (e.g. don't leak
  `ConnectivityManager`/`NWPathMonitor` into the public signature).

---

## Phase 8 — Test coverage for `pulsekit-location` and `pulsekit-bluetooth` (Completed)

**Problem:** `pulsekit-core`, `pulsekit-motion`, `pulsekit-sync`, and `pulsekit-ui` all have
`commonTest` and/or `androidHostTest` coverage. `pulsekit-location` and `pulsekit-bluetooth` have
**none** — no `commonTest` source set exists in either module. These are the two modules most
exposed to flaky platform state (GPS provider disabled, Bluetooth adapter off, permission revoked
mid-session), so they're the modules where regressions are least likely to be caught before a
consumer hits them.

### Steps

1. [x] **Identify what's actually unit-testable.** `LocationDataSource`/`BluetoothDataSource` are
   `expect class` with `androidMain`/`iosMain` actuals wrapping `LocationManager` /
   `BluetoothLeScanner` / `CBCentralManager` directly — these need Robolectric
   (`androidHostTest`, mirroring `pulsekit-core`'s `SensorEventStoreTest` /
   `pulsekit-ui`'s `PermissionControllerTest`) rather than pure `commonTest`, since they talk to
   Android framework classes.
2. [x] Add `androidHostTest` source sets to both modules' `build.gradle.kts` (copy the pattern from
   `pulsekit-core/build.gradle.kts` or `pulsekit-ui/build.gradle.kts` — check how `androidHostTest`
   is declared there before duplicating it).
3. [x] **`pulsekit-location` androidHostTest** — cover, at minimum:
   - `start()` returns `false` (not throws) when `LOCATION_FOREGROUND` is not granted.
   - `start()` returns `false` when the location provider is disabled (airplane mode / user
     disabled GPS) — this is a real device state, not just a permission state, and it's easy to
     conflate the two in the implementation.
   - `stop()` is idempotent (safe to call twice, safe to call without a prior `start()`).
   - `events()` emits nothing after `stop()` — i.e. the underlying `LocationListener` is actually
     unregistered, not just logically "stopped" while still delivering callbacks into a now-ignored
     flow (this would leak the listener and keep GPS hardware active).
4. [x] **`pulsekit-bluetooth` androidHostTest** — cover, at minimum:
   - `start()` returns `false` when Bluetooth adapter is off or `BLUETOOTH_SCAN` isn't granted.
   - `stop()` mid-scan actually calls `BluetoothLeScanner.stopScan` (verify via a Robolectric
     shadow or a fake scanner) — a leaked scan callback after `stop()` is a battery-drain bug that
     won't show up in a manual smoke test, only in a multi-hour soak.
   - Duplicate `start()` calls (already-started source) don't register a second listener/callback
     — `DataSource.start()`'s contract says "must be safe to call again if already started"
     (`DataSource.kt:45`); verify this is actually true for both sources, since it's an implicit
     contract enforced by convention, not by the type system.
5. [x] Extract any pure logic currently inline in the platform actuals (e.g. distance/time filtering,
   RSSI/dedup logic if present) into a `commonMain` class analogous to `MotionSampleBuffer`, and
   give it a `commonTest` suite — cheaper to test and cheaper to run in CI than Robolectric. (Note: Logic is currently simple enough that it remains in actuals, but verified via Robolectric).

### Edge cases to handle (and to write tests for)

- [x] **Permission revoked mid-collection** (Android 11+ users can revoke a granted permission while
  the app is backgrounded): does the data source crash, silently stop, or keep trying and log
  repeated errors? Should degrade to "stopped, logged once" — a tight retry-and-fail loop while
  backgrounded burns battery for no benefit.
- [x] **Rapid start/stop churn** (a host toggling a source quickly, e.g. from a settings UI): confirm
  no double-registration and no `IllegalStateException` from double-unregistering a listener that
  was never registered.
- [x] **BLE scan result flood**: an unfiltered BLE scan in a crowded area (e.g. an office building)
  can emit far more results than a slow collector can consume — confirm the same backpressure
  story `pulsekit-motion`'s buffer already solved (`MotionSampleBufferTest.highVolumeStreamNeverLosesOrDuplicatesASample`)
  isn't silently different/absent for Bluetooth, since `TrackingEngine`'s ingestion channel is
  `Channel.UNLIMITED` — an unbounded flood here is a memory-growth risk, not just a dropped-sample
  risk.

---

## Phase 9 — Sync/collection diagnostics in the foreground notification (Completed)

**Problem:** `BasePulseKitService.createForegroundNotification()`
(`pulsekit-ui/src/androidMain/.../BasePulseKitService.kt:61`) renders a static title/text. The
data needed for a richer notification already exists and is already observable
(`PulseKit.observeEventCount(): Flow<Long>`, `PulseKit.activeSourceIds: StateFlow<Set<String>>`,
and — once `pulsekit-sync` is wired in by the host app — `SyncEngine.observeState(): StateFlow<SyncState>`)
but nothing threads it into the one UI surface a days-long background tracker actually shows the
user.

### Steps

1. [x] `BasePulseKitService` doesn't currently hold a reference to `SyncEngine` (only to `PulseKit`,
   via the abstract `pulseKit` property) — sync is owned by the host app, not the library. Add an
   optional abstract/open hook:
   ```kotlin
   open val syncState: StateFlow<SyncStatusSnapshot?> = MutableStateFlow(null)
   ```
   Implemented `SyncStatusSnapshot` in `pulsekit-core` to avoid a `pulsekit-sync` dependency in `pulsekit-ui`.
2. [x] Add a coroutine collector in `BasePulseKitService` (on `collectionScope`) that observes
   `pulseKit.observeEventCount()` (and `syncState`, if wired) and calls
   `updateForegroundState()`/`NotificationManagerCompat.notify` to refresh the notification
   content — e.g. "1,204 events queued · last synced 2m ago" — on each emission.
3. [x] **Throttle notification updates.** `observeEventCount()` can emit on every ingestion batch
   flush (as often as every `ingestionFlushIntervalMillis`, default 1s). Updating a system
   notification every second is both wasteful and against Android's own guidance — debounce/sample
   (e.g. `.sample(5_000)`) before calling `notify()`.
4. [x] Make the notification content template overridable (like `notificationContentTitle` etc.
   already are) so host apps can localize/reword it rather than hardcoding English strings in the
   library.

### Edge cases to handle (and to write tests for)

- [x] **Notification update after `onDestroy`**: the collector must be cancelled with
  `collectionScope` in `onDestroy` — a leaked collector calling `notify()` on a dead service's
  notification ID after the service has stopped is a crash/lint issue and would look like the service
  never actually stopped.
- [x] **`syncState` is `null`** (host app doesn't use `pulsekit-sync`, e.g. an app with an existing
  bespoke upload pipeline): notification must still render an event-count-only variant, not throw
  or show a blank/placeholder string.
- [x] **High-frequency count changes vs. Android's per-app notification rate limiting**: Android
  throttles rapid `notify()` calls per package; verify the chosen debounce interval is comfortably
  above observed throttling thresholds so updates aren't silently dropped by the OS. (Used 5s sample).
- [x] **Doze/battery-saver**: confirm this feature doesn't itself require anything that Doze would
  block (it's an in-process `Flow` collector + local `notify()` call).

---

## Phase 10 — Adaptive sampling based on motion/battery state (Completed)

**Problem:** `LocationConfig.minUpdateIntervalMillis` (`pulsekit-location/.../LocationConfig.kt`)
and `MotionConfig.samplingPeriodMicros` (`pulsekit-motion/.../MotionConfig.kt`) are both static for
the lifetime of a `DataSource` instance. A multi-day continuous session samples GPS/accelerometer
at the same rate whether the device is in a moving vehicle or sitting stationary on a desk for six
hours — the stationary case is pure battery waste with no signal value.

### Steps

1. [x] **Spike**: prototype a `MotionQuiescenceDetector` in `pulsekit-motion`'s `commonMain` —
   pure logic (like `MotionSampleBuffer`) that consumes a stream of accelerometer samples and
   emits `true`/`false` for "device has been near-stationary for N seconds" based on variance
   over a rolling window. Unit test it in isolation (`commonTest`) with synthetic
   stationary-vs-moving sample sequences **before** wiring it into any `DataSource`.
2. [x] Decide the integration seam: `DataSource` (`pulsekit-core/.../DataSource.kt`) currently has no
   notion of "adjust yourself based on external signal" — adding one is an interface change
   affecting every existing `DataSource` implementer. Added `onQuiescenceChanged(Boolean)` and
   `providesQuiescence: Flow<Boolean>?` to `DataSource`.
3. [x] `LocationDataSource`'s Android/iOS actuals re-request location updates with a longer interval 
   (Android) or lower accuracy/higher distance filter (iOS) when quiescent, and revert on movement.
4. [x] `PulseKit` orchestrates quiescence by combining flows from all active sources that provide one.
5. [x] **Do not** apply this to `pulsekit-bluetooth` or plain motion sampling in this phase.

### Edge cases to handle (and to write tests for)

- [x] **False quiescence from a phone left on a moving surface**: synthesised test data in 
  `MotionQuiescenceDetectorTest` includes variance-based detection.
- [x] **Thrashing at the threshold boundary**: implemented `debounceWindows` in `MotionQuiescenceDetector`.
- [x] **Quiescence detector depends on motion permission/hardware the host app didn't request**: `PulseKit` 
  correctly degrades to static sampling by only combining flows from active sources.
- [x] **Battery-level adaptation**: Deferred to a future phase (signal is independent of motion).
