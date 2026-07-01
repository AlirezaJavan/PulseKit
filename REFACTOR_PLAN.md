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
