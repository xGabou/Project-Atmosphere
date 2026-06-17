# Project Atmosphere Cloud Backend Migration Report

Date: 2026-06-15

## 1. Files Created

- `src/main/java/net/Gabou/projectatmosphere/clouds/backend/CloudVisualBackend.java`
- `src/main/java/net/Gabou/projectatmosphere/clouds/backend/CloudMigrationDirection.java`
- `src/main/java/net/Gabou/projectatmosphere/clouds/backend/CloudMigrationStatus.java`
- `src/main/java/net/Gabou/projectatmosphere/clouds/backend/CloudBackendBridgeSnapshot.java`
- `src/main/java/net/Gabou/projectatmosphere/clouds/backend/CloudBackendMigrationState.java`
- `src/main/java/net/Gabou/projectatmosphere/clouds/backend/CloudBackendMigrationSavedData.java`
- `src/main/java/net/Gabou/projectatmosphere/clouds/backend/CloudBackendResolver.java`
- `src/main/java/net/Gabou/projectatmosphere/clouds/backend/CloudBackendStatus.java`
- `src/main/java/net/Gabou/projectatmosphere/clouds/backend/CloudBackendMigrationManager.java`
- `PROJECT_ATMOSPHERE_CLOUD_BACKEND_MIGRATION_REPORT.md`

## 2. Files Modified

- `src/main/java/net/Gabou/projectatmosphere/clouds/service/AtmosphereCloudService.java`
- `src/main/java/net/Gabou/projectatmosphere/clouds/service/AtmosphereCloudServices.java`
- `src/main/java/net/Gabou/projectatmosphere/clouds/AtmosphereCloudPolicy.java`
- `src/main/java/net/Gabou/projectatmosphere/clouds/visual/CloudVisualStateManager.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/SimpleCloudsCompat.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/simpleclouds/SimpleCloudsAtmosphereCloudService.java`
- `src/main/java/net/Gabou/projectatmosphere/event/EventHandler.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/AtmosphereManager.java`
- `src/main/java/net/Gabou/projectatmosphere/telemetry/verification/VerificationReport.java`
- `src/main/java/net/Gabou/projectatmosphere/telemetry/verification/VerificationCollector.java`
- `src/main/java/net/Gabou/projectatmosphere/telemetry/verification/VerificationFormatter.java`

## 3. Backend Resolver Implementation

Implemented `CloudBackendResolver` with visual backend values:

- `DISABLED`: selected when PA cloud rendering is disabled by config or the dimension is not PA-cloud eligible.
- `SIMPLE_CLOUDS`: selected when Simple Clouds is loaded and clouds are not disabled.
- `PA_NATIVE`: selected when Simple Clouds is absent and PA clouds are enabled.

`AtmosphereCloudServices` now selects `SimpleCloudsAtmosphereCloudService` when Simple Clouds is loaded instead of returning the disabled no-op service.

## 4. Migration State Implementation

Implemented persistent saved data in `CloudBackendMigrationSavedData`.

Tracked fields:

- `lastCloudBackend`
- `currentCloudBackend`
- `lastMigrationDirection`
- `lastMigrationGameTime`
- `paCloudsMirroredToSimpleClouds`
- `simpleCloudsMirroredToPa`
- `migrationVersion`
- `lastMigrationStatus`
- neutral bridge snapshots

The migration flags prevent repeated conversion every login.

## 5. Bridge Snapshot Implementation

Implemented `CloudBackendBridgeSnapshot`, a backend-neutral cloud snapshot containing:

- source backend
- source type id
- source morphology family
- position
- radius
- height
- density
- coverage
- storm strength
- capture game time

Simple Clouds snapshots are captured periodically while Simple Clouds is loaded. PA native snapshots are captured from stored PA cloud regions while PA native is active.

## 6. PA to Simple Clouds Migration Behavior

When the backend changes from `PA_NATIVE` to `SIMPLE_CLOUDS`, and PA cloud regions exist:

- PA cloud regions are converted to neutral snapshots.
- The Simple Clouds service maps PA type and morphology intent to safe Simple Clouds fallback type lists.
- Simple Clouds clouds are spawned near the PA source positions.
- PA saved cloud data is preserved.
- PA native visual rendering and sync are gated off while Simple Clouds owns visuals.

For older worlds without a migration marker, existing PA cloud data plus loaded Simple Clouds is treated as an inferred PA-to-Simple-Clouds migration source.

## 7. Simple Clouds to PA Migration Behavior

When the backend changes from `SIMPLE_CLOUDS` to `PA_NATIVE`, and stored Simple Clouds bridge snapshots exist:

- Snapshots are converted into PA native cloud regions.
- Approximate position, radius, height, density, coverage, and storm intent are preserved.
- Source type IDs are mapped to PA cloud types such as `cumulus_mediocris`, `cumulus_congestus`, `cumulonimbus_capillatus`, `stratus_nebulosus`, `stratocumulus`, `nimbostratus`, and `cirrus`.
- No Simple Clouds classes are required for this migration path.

If Simple Clouds is removed before any snapshots were captured, migration is skipped with `skipped, no source cloud data`.

## 8. Rendering Gate Behavior

PA native rendering now checks the resolved visual backend:

- `PA_NATIVE`: PA cloud render data is exposed and PA cloud region sync runs.
- `SIMPLE_CLOUDS`: PA render data returns empty, PA region sync is skipped, and Simple Clouds owns visible clouds.
- `DISABLED`: PA render data returns empty.

This gates PA native cloud volumes, PA long-distance metadata, and PA visual state access behind the active backend.

## 9. Duplicate Prevention Behavior

Duplicate prevention is handled by:

- one active visual backend from `CloudBackendResolver`
- migration flags for both directions
- PA visual sync/render gates when Simple Clouds is active
- preserving source save data without rendering both sources

The debug report exposes `Duplicate Visual Cloud Risk`.

## 10. Debug Verify Output Changes

`/pa debug verify` now includes:

```text
Cloud Backend:
Current Visual Backend: PA_NATIVE / SIMPLE_CLOUDS / DISABLED
Last Visual Backend: ...
Simple Clouds Loaded: yes/no
PA Native Clouds Stored: X
PA Native Clouds Rendered: X
Bridge Snapshots Stored: X
Last Migration Direction: ...
Duplicate Visual Cloud Risk: yes/no
Migration Status: ...
```

Snapshot output also includes `cloudBackend.*` key/value fields.

## 11. Systems Confirmed Untouched

No intentional changes were made to:

- forecast generation
- atmosphere simulation
- WeatherCell simulation
- WeatherCell evolution
- cloud evolution rules
- cloud morphology rules
- wind simulation
- seasonal drift
- tornado systems
- hurricane systems
- blizzard systems
- shader source files
- Iris internals
- Oculus internals
- Distant Horizons internals

Note: the working tree already contained unrelated modified shader/client/cloud files before this task. They were not part of this implementation.

## 12. Build Results

- `.\gradlew compileJava`: passed.
- `.\gradlew build`: passed.

Both commands completed successfully. Existing compiler warnings remain, mostly deprecation warnings and existing mixin target warnings.

## 13. Manual Test Checklist

- Fresh world without Simple Clouds:
  - Expected backend: `PA_NATIVE`
  - Expected migration: skipped fresh world or no source data
  - Expected result: PA native clouds generate normally

- Fresh world with Simple Clouds:
  - Expected backend: `SIMPLE_CLOUDS`
  - Expected migration: skipped fresh world or no source data
  - Expected result: Simple Clouds renders, PA native visuals stay hidden

- Existing PA world, then add Simple Clouds:
  - Expected backend: `SIMPLE_CLOUDS`
  - Expected migration: `completed, PA_NATIVE -> SIMPLE_CLOUDS`
  - Expected result: Simple Clouds clouds appear near prior PA regions, PA data remains stored, no PA visual overlap

- Existing Simple Clouds world, then remove Simple Clouds:
  - Expected backend: `PA_NATIVE`
  - Expected migration: `completed, SIMPLE_CLOUDS -> PA_NATIVE` if snapshots were captured
  - Expected result: PA native clouds appear near captured Simple Clouds positions

- Existing world with neither backend data:
  - Expected migration: skipped fresh world or no source cloud data
  - Expected result: normal active backend behavior only

## 14. Known Limitations

- Simple Clouds to PA migration requires bridge snapshots captured while Simple Clouds was previously loaded.
- PA to Simple Clouds type matching uses safe fallback ID lists because Simple Clouds cloud type availability can vary by version.
- Migration is approximate by design; it preserves placement and cloud intent, not exact visual parity.
- Runtime manual world tests were not executed in this pass; validation was compile and build.
