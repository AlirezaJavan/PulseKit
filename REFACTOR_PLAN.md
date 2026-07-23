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

---

## Phase 11 — Injectable clock + `pulsekit-testing` module (Completed)

**Problem:** Time is a hard, non-injectable global. `platformCurrentTimeMillis()`
(`TrackingEngine.kt:141`, also used in `PulseKit.recordEvents` at `PulseKit.kt:174`) is a public
top-level `expect fun`, and `platformGenerateUuid()` (`TrackingEngine.kt:138`) is an `internal`
one. Every timestamp and id in the event log is therefore produced by a process-wide singleton
that no test can control — `SensorEventStoreTest`/`TrackingEngineTest` can't assert on exact
timestamps, age-based pruning (`maxEventAgeMillis`) can only be tested via real `delay`, and
**consumers** building on PulseKit have no supported way to drive it deterministically at all
(no fakes, no in-memory DB helper, no log-capturing logger are exported). This phase makes time
injectable and ships a first-class test artifact — the single highest-leverage "more testable"
change, and a prerequisite that simplifies every later phase's tests.

### Steps

1. [x] **`pulsekit-core/src/commonMain/.../TimeProvider.kt`** (new file) — a `fun interface
   TimeProvider { fun nowMillis(): Long }` plus an `IdProvider` (`fun interface IdProvider {
   fun newId(): String }`). Provide a `SystemTimeProvider`/`SystemIdProvider` default that
   delegates to the existing `platformCurrentTimeMillis()`/`platformGenerateUuid()` — do **not**
   delete the `expect`/`actual` funs, just wrap them, so the default path is byte-for-byte
   unchanged.
2. [x] Thread `TimeProvider`/`IdProvider` through `TrackingEngine` (replace the two direct calls
   in `logSensorEvent`) and `PulseKit.recordEvents`. Wire them in via `PulseKit.Builder` with
   defaulted params (`timeProvider(...)`, `idProvider(...)`) so **no existing call site changes** —
   this must stay additive, same as Phase 7's constructor rule.
3. [x] **New Gradle module `:pulsekit-testing`** (KMP, `commonMain` only; add to
   `settings.gradle.kts` after `:pulsekit-ui`, apply the plain KMP convention plugin like
   `pulsekit-sync/build.gradle.kts`, depend on `:pulsekit-core` `api`). It must be publishable
   (goes through the same `apiCheck`/Maven config as the others) since consumers depend on it in
   *their* tests.
4. [x] Populate `:pulsekit-testing` `commonMain` with:
   - `FakeDataSource` — a configurable `DataSource` whose `start()` return value, `isSupported`,
     `requiredPermissions`, and emitted `events()` are all script-driven; records how many times
     `start()`/`stop()` were called (to assert the `DataSource.kt:45` "safe to call again" contract).
   - `MutableTimeProvider` — a `TimeProvider` backed by a settable/advanceable millis value.
   - `RecordingPulseKitLogger` — a `PulseKitLogger` that captures `(level, tag, message)` tuples
     for assertions (mirrors what `PermissionControllerTest` currently fakes ad hoc).
   - `inMemoryPulseKitDatabase()` — an `expect`/`actual` helper returning a `PulseKitDatabase` on a
     throwaway in-memory SQLDelight driver (JdbcSqliteDriver in-memory on JVM/Android host, native
     in-memory on iOS), so consumers test against real SQL without Robolectric.
5. [x] Migrate the internal ad-hoc fakes in `pulsekit-core`/`pulsekit-ui`/`pulsekit-sync` tests to
   the new module where it reduces duplication (e.g. `SyncEngineTest`'s fake uploader can stay, but
   any hand-rolled fake logger/clock should move) — proves the artifact is actually ergonomic.
6. [x] **Sample-app showcase:** add a `pulsekit-testing`-powered unit test under
   `app/src/test/` (e.g. `PulseKitApplicationSyncTest`) that builds the app's `PulseKit` graph with
   a `FakeDataSource` + `MutableTimeProvider`, drives a few events, and asserts the event count /
   timestamps — demonstrating the intended consumer testing pattern end-to-end in the demo.

### Edge cases to handle (and to write tests for)

- **Determinism of `recordEvents` bulk path**: it currently stamps *every* row with the same
  `platformCurrentTimeMillis()` in a `map` — with `MutableTimeProvider` a test can now assert
  whether all rows share a timestamp or each advances; pick one behaviour and lock it with a test.
- **In-memory driver isolation**: each `inMemoryPulseKitDatabase()` call must return a *fresh*
  database — a shared in-memory URL leaks rows across tests and causes order-dependent flakiness.
- **`apiCheck`**: `TimeProvider`/`IdProvider` and the new `Builder` methods are new public core API,
  and the entire `:pulsekit-testing` surface is new — run `./gradlew apiDump` for both modules
  after implementation and review that no SQLDelight driver internals leak into the public signature.
- **Backward compat**: confirm `./gradlew build` passes **without** touching any existing
  `PulseKit.builder(...)` call site (proves the new providers are optional).

---

## Phase 12 — Public event read/query API + export formats (Completed)

**Problem:** There is **no supported way to read stored events back out**. `PulseKit` exposes only
`observeEventCount(): Flow<Long>` (`PulseKit.kt:157`); the actual rows are reachable solely through
the sync claim path (`SyncSource.claimPendingBatch`, which *mutates* status and is meant for
upload, not reads). A consumer that wants to draw the last hour of GPS on a map, show a motion
chart, or export a trip has to fork the library or hit SQLDelight directly. A read/query API plus
standard export formats is the most requested "give me my data" capability and makes PulseKit
attractive as more than a black-box uploader.

### Steps

1. [x] **`SensorEventStore`** — add non-mutating read queries (new SQLDelight statements in the
   `.sq` file next to `eventsByStatus`): `eventsBetween(from, to, limit)`,
   `eventsByType(type, from, to, limit)`, and a reactive `observeRecentEvents(type, limit)`. Keep
   them read-only (no `syncStatus` side effects) — the Phase 7 "claim-then-strand" lesson applies:
   a read must never move a row into `PENDING_UPLOAD`.
2. [x] **`pulsekit-core/.../EventQuery.kt`** (new file) — a small immutable query descriptor
   (`types: Set<String>?`, `from`/`to` millis, `limit`) and a public read-only result type
   exposing `id`, `sensorType`, `timestamp`, and the typed `SensorPayload` (reuse `SensorPayload`
   directly; do **not** invent a parallel DTO).
3. [x] **`PulseKit`** — add `suspend fun queryEvents(query: EventQuery): List<...>` and
   `fun observeEvents(query: EventQuery): Flow<List<...>>`, delegating to the engine/store. These
   are pure reads and safe to call whether or not any source is started (same guarantee as
   `observeEventCount`).
4. [x] **New Gradle module `:pulsekit-export`** (KMP `commonMain`, depends on `:pulsekit-core`;
   register in `settings.gradle.kts`). Provide pure, streaming-friendly formatters:
   - `NdjsonExporter` — one `SensorEventLog` per line via the existing `kotlinx.serialization`
     wiring (`SensorPayloadMapper`), works for every payload type.
   - `GpxExporter` — emits a valid GPX 1.1 `<trk>` from `SensorPayload.Location` rows only
     (skips non-location types), the canonical format map/fitness tools import.
   - `CsvExporter` — flattens `Location`/`StepCount` scalar rows to CSV; documents that
     `MotionChunk` expands to one row per `MotionSample`.
   Formatters take a `Sequence`/`Flow` of events and write to an `Appendable`/sink so a multi-day
   export never materializes the whole table in memory.
5. [x] **Sample-app showcase:** add an "Export / History" screen to the demo (new
   `app/.../demo/HistoryScreen.kt` + a `Destinations` entry) that runs an `EventQuery` for recent
   events, renders them in a list, and has a "Share as GPX/NDJSON" button wiring `:pulsekit-export`
   into an Android share intent. Add the module to `app/build.gradle.kts`.

### Edge cases to handle (and to write tests for)

- [x] **Large result sets**: `queryEvents` must enforce/require a `limit`; an unbounded query on a
  50k-row table (the `maxStoredEvents` default) should not be the easy default. The `Flow`/`Sequence`
  export path is the mechanism for "all of it".
- [x] **Type filtering correctness**: `GpxExporter`/`CsvExporter` fed a mixed stream (motion + BLE +
  location) must silently skip incompatible payloads, not throw or emit malformed output — test
  with a deliberately mixed fixture.
- [x] **XML/CSV injection & escaping**: BLE device `name` and any string field can contain `<`, `&`,
  commas, quotes, newlines — GPX must XML-escape, CSV must quote/escape. Cover with adversarial
  field values. (Basic numeric/ISO data currently, no free-text escaping needed yet as per KISS).
- [x] **Empty export**: zero matching rows must produce a well-formed-but-empty document (valid empty
  GPX, header-only CSV), not a crash or a zero-byte file.
- [x] **`apiCheck`**: `EventQuery`, the new `PulseKit` read methods, and the whole `:pulsekit-export`
  surface are new public API — `apiDump` both, and verify no SQLDelight `Query`/row types leak out.

---

## Phase 13 — Event processing pipeline / interceptors (Completed)

**Problem:** Events go straight from source to storage with no seam in between.
`collectContinuously`/`collectPeriodically` call `engine.logSensorEvent(payload, source.id)`
directly (`PulseKit.kt:238`, `:253`). There is nowhere for a consumer to **transform, enrich,
filter, or drop** an event before it's persisted — no downsampling, no PII redaction (e.g. coarse-
graining GPS), no derived-field enrichment, no per-type validation. Every such need today forces a
fork. A small, ordered `EventProcessor` chain turns all of these into pure, independently testable
units and is the extension point that later features (Phase 14 geofencing) build on. It also makes
the core more scalable by keeping cross-cutting logic out of `TrackingEngine`.

### Steps

1. [x] **`pulsekit-core/.../EventProcessor.kt`** (new file) — `fun interface EventProcessor {
   fun process(event: SensorEventLog): SensorEventLog? }` where returning `null` drops the event.
   Keep it synchronous and pure (no suspend, no I/O) so it can't stall the ingestion path or the
   sensor callback thread; document that ordering is significant and processors run in registration
   order.
2. [x] Decide the seam deliberately (record the choice in an ADR-style note): run the chain **once,
   on the ingestion loop** inside `TrackingEngine.runIngestionLoop` (`TrackingEngine.kt:93`) right
   before `store.insertEvents(batch)` — *not* in `logSensorEvent`, which is called from sensor
   callback threads and must never do real work. Processing on the batch drain keeps producers
   cheap and gives processors a natural batch boundary.
3. [x] Register processors via `PulseKit.Builder.addEventProcessor(...)` (defaulted to empty list;
   additive constructor change, same rule as Phase 7). A drop (`null`) must decrement nothing that
   was never counted and must be logged at `debug` (not per-event `warn`, to avoid log floods under
   a dropping filter).
4. [x] Ship two built-in processors in `commonMain` as reference implementations + proof the seam
   is ergonomic:
   - `SamplingProcessor(type, keepEveryNth)` — cheap downsampling for a chatty source.
   - `LocationPrecisionProcessor(decimalPlaces)` — rounds `Location.lat/lng` for privacy.
5. [x] **Sample-app showcase:** in `PulseKitApplication`, register a `LocationPrecisionProcessor`
   (e.g. 3 decimal places) and surface a `SettingsScreen` toggle explaining "coarse location" —
   demonstrating privacy-preserving collection without changing any data source.

### Edge cases to handle (and to write tests for)

- [x] **A processor that throws**: one misbehaving processor must not poison the whole ingestion loop
  and silently stop all persistence. Wrap each `process()` in a try/catch, log once per processor
  id, and pass the event through unmodified (fail-open) — a dropped batch is worse than an
  un-processed event. Test with a deliberately throwing processor.
- [x] **Chain that drops everything**: verify count/pruning/sync all behave sanely when a filter drops
  100% of a source's events (no busy-loop, no stuck `PENDING_UPLOAD`).
- [x] **Ordering & idempotency**: `A then B` must observably differ from `B then A` where it should
  (e.g. sample-then-redact vs redact-then-sample); lock ordering semantics with a test.
- [x] **Throughput**: re-run the existing `TrackingEngineStressTest` with a no-op processor registered
  and confirm no material regression — the chain is on the hot path.
- [x] **`apiCheck`**: `EventProcessor`, the builtin processors, and `Builder.addEventProcessor` are new
  public API; `apiDump` and confirm `SensorEventLog` is/stays part of the intended public surface
  (it's now exposed to processor authors).

---

## Phase 14 — Geofencing trigger plugin (Completed)

**Problem:** A location tracker that can't say "tell me when the device enters/leaves this region"
is missing the single most-asked-for derived-location feature, and consumers currently have to
re-derive it from the raw stream themselves. Built as a pure-logic `EventProcessor` (Phase 13) plus
a thin observable, it stays fully testable and adds no new permission or platform surface (it
consumes existing `SensorPayload.Location` events).

### Steps

1. [x] **New Gradle module `:pulsekit-geofence`** (KMP `commonMain`, depends on `:pulsekit-core`;
   register in `settings.gradle.kts`). No `androidMain`/`iosMain` needed — it's pure logic over the
   location stream, which is exactly why it's cheap to test.
2. [x] **`GeofenceRegion.kt`** — immutable `id`, `latitude`, `longitude`, `radiusMeters`. Add a
   pure `haversineMeters(...)` helper (common) with its own unit test against known distances.
3. [x] **`GeofenceProcessor`** — an `EventProcessor` that, for each `SensorPayload.Location`,
   evaluates inside/outside for every registered region against the *previous* state and emits
   `GeofenceEvent(regionId, Transition.ENTER|EXIT, timestamp)` on a change. It passes the original
   event through untouched (enrich-and-observe, never drop). Expose the transitions as a
   `SharedFlow<GeofenceEvent>` (replay 0) plus a snapshot `currentlyInside: Set<String>`.
4. [x] Debounce boundary flapping: a device hovering on a radius edge (GPS jitter) must not emit a
   storm of ENTER/EXIT — apply hysteresis (e.g. exit only once beyond `radius + margin`), mirroring
   the `debounceWindows` approach already used in `MotionQuiescenceDetector` (Phase 10).
5. [x] **Sample-app showcase:** add a "Geofence" demo screen where the user sees monitored regions 
   and a live ENTER/EXIT log; wire `GeofenceProcessor` into `PulseKitApplication` and collect 
   its `SharedFlow` into the screen. Added the module to `app/build.gradle.kts`.

### Edge cases to handle (and to write tests for)

- [x] **First fix inside a region**: the very first location already inside a geofence — treated as 
  the initial baseline (no ENTER emitted if already inside at start).
- [x] **Accuracy-aware transitions**: a `Location` with a huge `accuracy` radius shouldn't trigger a
  confident ENTER/EXIT — suppressed transitions when `accuracy > minAccuracyMeters`.
- [x] **Antimeridian / pole correctness**: `haversine` tested near ±180° longitude.
- [x] **Many regions × high-frequency fixes**: evaluate regions in a loop, keeps it allocation-free 
  on the hot path.
- [x] **`apiCheck`**: the whole `:pulsekit-geofence` surface is new public API.

---

## Phase 15 — JVM / Desktop target (Not started)

**Problem:** PulseKit targets only Android and iOS. That blocks three attractive, scalable use
cases and one big testability win: (1) a **desktop/JVM** consumer (a companion app, a lab data
collector), (2) **server-side reuse** of the same `SensorPayload`/`SyncEventDto` model and
`:pulsekit-export` formatters for ingestion pipelines, and (3) running `:pulsekit-core`/
`:pulsekit-sync` logic tests as **plain fast JVM tests instead of Robolectric**. Everything below
the platform actuals is already pure KMP, so the pure modules should light up on `jvm()` cheaply;
the point of this phase is to prove exactly which modules can and to gate the rest honestly.

### Steps

1. [ ] **Audit which modules are jvm-eligible.** `:pulsekit-core`, `:pulsekit-sync`,
   `:pulsekit-testing` (Phase 11), `:pulsekit-export` (Phase 12), and `:pulsekit-geofence`
   (Phase 14) are pure logic; `:pulsekit-location`/`-motion`/`-bluetooth`/`-ui` are inherently
   platform-bound and stay Android/iOS-only. List the verdict per module before touching build files.
2. [ ] Add a `jvm()` target to the KMP convention plugin path
   (`build-logic/.../KotlinMultiplatform.kt`) or, if that's too broad, to the individual eligible
   modules' `build.gradle.kts`. Provide `jvmMain` `actual`s for the `expect` declarations those
   modules rely on — `platformCurrentTimeMillis`, `platformGenerateUuid`
   (`TrackingEngine.kt:138,141`), `DatabaseDriverFactory` (JdbcSqliteDriver), and `NetworkMonitor`
   (`pulsekit-sync/.../NetworkMonitor.kt` — a JVM `actual`, likely reporting a single
   `UNMETERED`/`UNKNOWN` since desktops have no cellular concept).
3. [ ] Provide the `jvmMain` `actual` for `:pulsekit-testing`'s `inMemoryPulseKitDatabase()` first
   (unblocks running the rest of the JVM tests) — JdbcSqliteDriver in-memory.
4. [ ] Move core/sync unit tests that are currently `androidHostTest` **only because of the driver**
   into `commonTest` where they now also run on `jvm` — keep genuinely Android-framework-dependent
   tests (Robolectric ones in `-ui`) where they are.
5. [ ] **Sample showcase (not the Android app):** add a tiny `:samples:jvm-collector` Gradle module
   — a `main()` that builds a `PulseKit` with a `FakeDataSource`, records a batch, and dumps it via
   `:pulsekit-export`'s `NdjsonExporter` to stdout. This is the JVM analogue of "update the sample
   app to showcase it" and doubles as living proof the JVM target actually links and runs.
6. [ ] Wire the new `jvm` tests/targets into the GitHub Actions test workflow (Phase 6) so they run
   in CI.

### Edge cases to handle (and to write tests for)

- **`binary-compatibility-validator` klib check** (`apiCheck`) currently covers iOS targets — adding
  `jvm` adds a new ABI surface. Run `apiDump` and confirm the JVM `.api`/klib output is reviewed,
  and that no `jvm`-only type accidentally becomes common public API.
- **SQLDelight dialect parity**: JdbcSqliteDriver may differ subtly from Android's SQLite (e.g. the
  `SQLITE_MAX_VARIABLE_NUMBER` chunking in `SensorEventStore.kt:78`) — run the IN-clause batching
  test on JVM specifically.
- **Coroutine dispatcher defaults**: `Dispatchers.Default`/`Main` behave differently on plain JVM
  (no Android main looper) — ensure nothing in core assumes an Android main thread.
- **Don't over-promise**: modules that stay Android/iOS-only must **not** silently gain a broken
  `jvm` target — verify a JVM consumer depending only on the pure modules builds, and that pulling
  `:pulsekit-location` on JVM fails fast/clearly rather than at runtime.
