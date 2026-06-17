# 026 Runtime Lifecycle Manager Restructure

Target files reviewed
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CycloneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoInstance.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneInstance.java`

Files changed
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CycloneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CycloneImpactApplier.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneEnvironmentAnalyzer.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoClientSnapshotLogger.java`

Classes split
- `CycloneManager` -> `CycloneImpactApplier`
- `HurricaneManager` -> `HurricaneEnvironmentAnalyzer`
- `TornadoManager` -> `TornadoClientSnapshotLogger`

New helper classes created
- `CycloneImpactApplier`
- `HurricaneEnvironmentAnalyzer`
- `TornadoClientSnapshotLogger`

Classes deleted because empty
- None

Files resolved by category
- `TRUE_HANDLED_RESTRUCTURED`
  - `CycloneManager`
  - `HurricaneManager`
  - `TornadoManager`
- `TRUE_HANDLED_GOOD_AS_IS`
  - `TornadoInstance`
  - `HurricaneInstance`

Files still risky
- `58` risky manual-review files remain in the matrix after this pass
- The remaining manual-only set still includes broad atmosphere state classes, compat handler, and some render/mixin-sensitive surfaces

Total risky files before this pass
- `63`

Total risky files resolved in this pass
- `5`

Total risky files remaining after this pass
- `58`

Build checkpoints run
- `2`

Build result
- `.\gradlew.bat build` succeeded

Remaining manual-only files
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericStateRegistry.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericUpdateScheduler.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/RegionAtmosphereState.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/AtmosphereManager.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/CompatHandler.java`

Why those files remain manual-only
- They are central lifecycle or startup coordination surfaces where the next safe step depends on a narrower helper seam than was available in this pass.

Next recommended target
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericStateRegistry.java`
