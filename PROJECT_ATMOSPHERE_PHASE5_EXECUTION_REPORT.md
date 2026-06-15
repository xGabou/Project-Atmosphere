# Project Atmosphere Phase 5 Execution Report

Date: 2026-06-15

Scope: Phase 5 Base -> Phase 5A -> Phase 5B -> A+B Verification -> Phase 5C -> Final Verification.

## 1. Phase 5 Base Implementation

Implemented:

- Added `CloudMorphologyFamily` with the required families:
  - `PUFF`
  - `TOWER`
  - `STORM_ANVIL`
  - `SHEET`
  - `CELLULAR_SHEET`
  - `FILAMENT`
  - `SPIRAL_STORM`
  - `DEBUG`
- Added `morphologyFamily` to `CloudTypeDefinition`.
- Added datapack parsing for `morphologyFamily`, `morphology_family`, or `morphology`.
- Added explicit PA native mapping in `CloudTypeRegistry`:
  - `vapor_cluster` -> `PUFF`
  - `cumulus_humilis` -> `PUFF`
  - `cumulus_mediocris` -> `PUFF`
  - `cumulus_congestus` -> `TOWER`
  - `cumulonimbus_calvus` -> `STORM_ANVIL`
  - `cumulonimbus_capillatus` -> `STORM_ANVIL`
  - `stratus_nebulosus` -> `SHEET`
  - `stratocumulus` -> `CELLULAR_SHEET`
  - `nimbostratus` -> `SHEET`
  - `cirrus` -> `FILAMENT`

Verified:

- PA native cloud types now resolve through cloud type -> morphology family -> generator path.
- Existing constructors remain available for compatibility and infer morphology where older call sites do not provide it.

## 2. Phase 5A Implementation

Implemented:

- Added `CloudMorphologyGenerators`.
- Replaced old `CloudGroupSpawner.resolveMorphology` generic/id heuristic with family-specific spawn plans.
- Added dedicated generator paths:
  - `puffPlan`
  - `towerPlan`
  - `stormAnvilPlan`
  - `sheetPlan`
  - `cellularSheetPlan`
  - `filamentPlan`
  - `spiralStormPlan`
  - `debugPlan`
- Added family-specific cluster placement:
  - PUFF: separated radial puffs.
  - TOWER: vertically stacked cells close to the core.
  - STORM_ANVIL: dense core plus upper spread cells.
  - SHEET: elongated non-circular cluster line.
  - CELLULAR_SHEET: broader sheet with separated embedded cells and gaps.
  - FILAMENT: long, thin wind-aligned cluster chain.
  - SPIRAL_STORM: dormant spiral placement path only; hurricane behavior was not modified.
  - DEBUG: explicit debug generator path.
- Changed `CloudRegionTypeGeometry.apply` to delegate to `CloudMorphologyGenerators.applyToCluster`.

Verified:

- Normal PA native cloud spawning no longer uses the previous generic radial morphology method.
- Type evolution now reapplies family-specific generator geometry.
- `SPIRAL_STORM` has a dedicated generator path, but no hurricane integration was changed.

## 3. Phase 5B Implementation

Implemented:

- Added persisted `morphologyFamily` to `CloudClusterState`.
- Added legacy region-level morphology save/load support in `CloudRegionState`.
- Added morphology identity to cloud render transport:
  - `CloudRegionRenderData`
  - `CloudRegionRenderDataFactory`
  - network encode/decode
- Added morphology identity to client snapshots:
  - `CloudRenderSnapshot`
  - `CloudRenderSnapshotBuilder`
  - debug snapshot construction
- Added diagnostics visibility:
  - `CloudRegionManager.describeCloudRegions` now includes morphology.
  - `CloudRenderDiagnostics` now includes morphology in the last-cloud diagnostic string.

Verified:

- Save/load preserves morphology through `MorphologyFamily` NBT tags.
- Evolution updates morphology when cloud type changes.
- Merge preserves dominant cluster morphology, and if the absorbed cluster becomes dominant by footprint/type, its morphology moves with its cloud type.
- Region merge preserves per-cluster morphology by moving clusters intact.
- Render-data transfer preserves morphology from backend to client snapshot.

## 4. Verification Results After A+B

Checks performed:

- `rg` static check for all explicit cloud-type mappings.
- `rg` static check for all generator-family switch branches.
- `rg` static check for persistence tags and render-data transport.
- Client-safety scan over common/server cloud packages:
  - `clouds/state`
  - `clouds/simulation`
  - `clouds/type`
  - `clouds/transport`
- `.\gradlew compileJava`
- `.\gradlew build`

Results:

- Every PA native cloud type resolves to a morphology family.
- Every morphology family resolves to a generator path.
- No common/server cloud package imports client-only cloud classes or `net.minecraft.client`.
- Compile succeeded.
- Build succeeded.

## 5. Issues Discovered

Implemented/fixed during the phase:

- `SPIRAL_STORM` initially routed through the storm-anvil plan. This did not satisfy the requirement that every family resolve to a dedicated generator.
- Fixed by adding `spiralStormPlan` and `spiralCell`.

Deferred/not applicable:

- No active cloud split controller exists in the inspected cloud lifecycle. Morphology preservation was verified for existing add/remove/merge/region-transfer paths. A future split controller should copy `CloudClusterState.getMorphologyFamily()` into child clusters.
- Full in-game runtime visual verification was not performed in this non-interactive execution. Compile, test, and build verification passed.

## 6. Fixes Applied

Fixed:

- Replaced generic spawn morphology selection with `CloudMorphologyGenerators`.
- Replaced generic geometry application with family-specific application.
- Persisted morphology identity in cluster and region NBT.
- Propagated morphology identity through server -> network -> client snapshot.
- Added diagnostics so active clouds expose morphology identity.
- Added dedicated `SPIRAL_STORM` generator path without changing hurricane behavior.

No undocumented fixes were applied.

## 7. Phase 5C Tuning Work

Implemented:

- Tuned `CloudShapeProfile.defaultFor` for PA native cloud types:
  - PUFF clouds are smaller, more lobed, and more broken.
  - TOWER clouds are taller, narrower, and more vertically stacked.
  - STORM_ANVIL clouds have stronger tower/anvil separation and storm-wall traits.
  - SHEET clouds are wider, thinner, and flatter.
  - CELLULAR_SHEET clouds have more cells, higher lobe/cell split, and more gaps.
  - FILAMENT clouds are thinner, longer, more sheared, and more ragged.
- Tuned `CloudVisualProfile` defaults in `CloudTypeRegistry` for family separation:
  - Cumulus puffs reduced storm/tower identity.
  - Congestus gained stronger vertical identity.
  - Cumulonimbus calvus/capillatus gained stronger tower/anvil separation.
  - Stratus/nimbostratus became flatter and broader.
  - Cirrus became thinner, weaker density, and more wispy.

Deferred:

- No shader code was changed.
- No Distant Horizons work was started.
- No tornado work was started.
- No hurricane behavior was modified.
- No blizzard work was started.
- No render-pipeline redesign was performed.

## 8. Final Verification

Final checks performed:

- `.\gradlew compileJava`
- `.\gradlew build`
- Static verification of morphology mappings and generator branches.
- Static verification of morphology persistence/transport paths.
- Static client-safety scan for common/server cloud code.
- Diff review of Phase 5 touched files.

Results:

- Compile succeeded.
- Build succeeded.
- Gradle `test` ran as part of `build` and passed.
- No new compile errors.
- Existing warnings remain, mostly deprecation and mixin target warnings unrelated to Phase 5.

## 9. Client Safety Verification

Verified:

- No `net.minecraft.client` references were found in:
  - `src/main/java/net/Gabou/projectatmosphere/clouds/state`
  - `src/main/java/net/Gabou/projectatmosphere/clouds/simulation`
  - `src/main/java/net/Gabou/projectatmosphere/clouds/type`
  - `src/main/java/net/Gabou/projectatmosphere/clouds/transport`
- Backend state and simulation still do not depend on client render snapshot classes.
- Client snapshot/diagnostic changes remain in client packages.

## 10. Build Verification

Commands run:

- `.\gradlew compileJava`
  - Passed after Phase 5A/5B implementation.
  - Passed after Phase 5C tuning.
- `.\gradlew build`
  - Passed after Phase 5A/5B verification.
  - Passed final verification.

Build status: passed.

## 11. Remaining Known Limitations

Deferred:

- Runtime visual inspection in Minecraft was not performed here.
- True cloud split behavior cannot be fully verified because no active split controller exists.
- Simple Clouds JSON cloud types still use their existing Simple Clouds layered-noise generator. Phase 5 focused PA native cloud morphology.
- Sheet and filament non-circularity is approximated by multi-cluster placement plus existing volume profile controls. It is structurally distinct now, but future shader work can improve final silhouettes.
- `SPIRAL_STORM` has a dedicated dormant generator path, but hurricane behavior remains on the existing hurricane path as requested.

## 12. Readiness Assessment

Phase 6, Shaders:

- Ready with caveats.
- Morphology family identity now exists in definitions, backend state, render data, and client snapshots.
- Shader work can now key off stable morphology identity instead of guessing from cloud type or profile values.

Phase 7, Distant Horizons:

- Not blocked by Phase 5.
- DH work should wait until shader-side morphology sampling is finalized, but backend identity/transport is now available.

Phase 8, Tornadoes:

- Not started.
- Storm cloud morphology now has clearer `STORM_ANVIL` identity and diagnostics, which should help later tornado coupling.
- Tornado work should use morphology identity, storm visual tier, and precipitation tier instead of raw type-name checks where possible.

## Implemented / Verified / Fixed / Deferred Summary

Implemented:

- Morphology family enum.
- Explicit PA native type mapping.
- Dedicated generator paths.
- Spawn integration.
- Evolution geometry integration.
- Save/load morphology identity.
- Network/client snapshot propagation.
- Diagnostics.
- Phase 5C morphology tuning.

Verified:

- Static family mapping.
- Generator routing.
- Save/load tags.
- Evolution path updates.
- Merge path preservation.
- Network/client transport.
- Client safety.
- Compile.
- Build.

Fixed:

- `SPIRAL_STORM` no longer shares the storm-anvil spawn plan.

Deferred:

- In-game visual QA.
- Future split-controller copy verification.
- Shader refinement.
- Distant Horizons integration.
- Tornado/hurricane/blizzard feature work.
