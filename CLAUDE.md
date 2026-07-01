# PulseKit Development Guide

## Project Structure
Follows **Now In Android (NiA)** modular architecture adapted for **Kotlin Multiplatform (KMP)**.
- `:pulsekit-core`: Core engine, SQLDelight KMP database, and permission abstractions (`Permission`, `PermissionStatus`). Pure logic, no Android manifest.
- `:pulsekit-ui`: Android infrastructure (`BasePulseKitService`, `PulseKitBootReceiver`, `PulseKitSetupValidator`), KMP `PermissionController`, and Compose Multiplatform UI components (`PermissionGate`).
- `:pulsekit-location`: Location tracking plugin (KMP).
- `:pulsekit-motion`: Motion plugin (KMP) — raw accelerometer (`MotionDataSource`) + step counter (`StepCounterDataSource`).
- `:pulsekit-bluetooth`: BLE scan plugin (KMP).
- `:pulsekit-sync`: Network synchronization engine using Ktor (KMP), inversion-of-control `SyncUploader`.
- `:app`: Android demo application.

## Build Commands
- Gradle Sync: `./gradlew help`
- Build All: `./gradlew build`
- Run Android App: `./gradlew :app:installDebug`
- Test: `./gradlew test`

## Code Style
- **Kotlin**: Idiomatic KMP, 100 char line limit, trailing commas.
- **Compose**: Jetpack Compose for Android UI.
- **Architecture**: UDF (Unidirectional Data Flow), Offline-first, Pure Logic Core.

## Platform Specifics
- **Android**: Uses Foreground Service with `START_STICKY` and `PARTIAL_WAKE_LOCK` for continuous tracking.
  The service and permissions must be declared in the client app's `AndroidManifest.xml`.
  Location permission is a two-step flow: request foreground first, then background.
- **iOS**: Anchored to Background Location updates for raw sensor continuity. Consuming apps must
  configure `Info.plist` with necessary usage descriptions and background modes.

## Adding a new `Permission`
1. `pulsekit-core`'s `permission/Permission.kt` — add the enum entry.
2. `pulsekit-ui`'s `PermissionController` (common/android/ios) — wire `status()`/`request()` for it.
3. `pulsekit-ui`'s `PermissionAndroidMetadata.kt` — update `manifestPermissions()` and `foregroundServiceType()`.
4. `app`'s `AndroidManifest.xml` — declare the new manifest permission.
5. `README.md` — update the permission-request snippets and manifest examples.

## NiA / modular architecture conventions
- **Pure Logic Core**: `:pulsekit-core` and feature modules (`:pulsekit-location`, etc.) have no
  Android manifest. They describe their requirements; the "Host" module (`:pulsekit-ui`) or the
  client app provides the infrastructure.
- **Client Manifest Ownership**: The library declares zero permissions or components in its own
  manifests. Client apps must explicitly declare every service, receiver, and permission they use.
- **Validation**: Use `validateAndroidSetup()` at startup to catch missing manifest declarations.
- One Gradle module per capability, each depending only on `pulsekit-core`, never on each other.
- Shared build configuration lives in `build-logic` convention plugins.

## KMP-specific practices
- `expect`/`actual` visibility must be deliberate and matching.
- `binary-compatibility-validator`'s klib ABI check (`apiCheck`) covers iOS targets. Android surface needs manual review.

## Android platform practices
- Foreground service types must match runtime-held permissions. Gate each type behind its own
  `Build.VERSION.SDK_INT` check in `BasePulseKitService`.
- Multi-step runtime permission flows must be requested as separate calls in the right order.
- Periodically renew `PARTIAL_WAKE_LOCK` for multi-day sessions.
