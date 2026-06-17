# Runtime Lifecycle Manager Resolution

Step goal
- Split narrowly bounded lifecycle helpers out of cyclone, tornado, and hurricane manager code while preserving tick, spawn, snapshot, and persistence behavior.

Files changed
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CycloneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CycloneImpactApplier.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneEnvironmentAnalyzer.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoClientSnapshotLogger.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CycloneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoManager.java`

Files moved
- None

Classes renamed
- None

Classes split
- `CycloneManager` split cyclone delta application into `CycloneImpactApplier`
- `HurricaneManager` split cyclone environment analysis into `HurricaneEnvironmentAnalyzer`
- `TornadoManager` split client snapshot logging into `TornadoClientSnapshotLogger`

New helper classes created
- `CycloneImpactApplier`
- `HurricaneEnvironmentAnalyzer`
- `TornadoClientSnapshotLogger`

Classes deleted because empty
- None

Facades kept
- None

Methods reordered
- None

Legacy/debug code moved
- Client snapshot logging was moved out of `TornadoManager` into `TornadoClientSnapshotLogger`

Call sites updated
- `CycloneManager` now delegates cyclone delta application to `CycloneImpactApplier`
- `HurricaneManager` now delegates cyclone environment analysis to `HurricaneEnvironmentAnalyzer`
- `TornadoManager` now delegates client snapshot logging to `TornadoClientSnapshotLogger`

Imports updated
- Yes, in the three manager classes and the new helper classes

Build checkpoints run
- 2

Build result
- `.\gradlew.bat build` succeeded

Behavior risk review
- Tick order preserved
- Spawn rates unchanged
- Destruction behavior unchanged
- Sync behavior unchanged
- Persistence unchanged
- Snapshot formats unchanged

Rollback notes
- If any helper extraction had changed behavior, the rollback boundary would have been the helper split for that manager only. No rollback was needed.

Remaining manual-only files
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericStateRegistry.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericUpdateScheduler.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/RegionAtmosphereState.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/AtmosphereManager.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/CompatHandler.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoInstance.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneInstance.java`

Why remaining files were not refactored
- They still require manual inspection for broader lifecycle, snapshot, or startup coupling that is not a clean helper split yet.

Next recommended step
- Continue with the remaining manual-only files only where a similarly narrow helper boundary exists.
