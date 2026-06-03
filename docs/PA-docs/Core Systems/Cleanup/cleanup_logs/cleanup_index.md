# Cleanup Index

## 001_whole_tree_unused_import_cleanup
- Batch number: 001
- Target modules: Whole Java source tree (`src/main/java/`)
- Files reviewed: 358
- Files changed: 41
- Cleanup type: Verified unused import cleanup
- Build result: `.\gradlew.bat build` succeeded
- Short notes: Conservative whole-tree import removal; warnings left unchanged.

## 002_safe_api_network_humidity_pressure_comments
- Batch number: 002
- Target modules: `api/`, `network/`, `modules/humidity/`, `modules/pressure/`
- Files reviewed: Not recorded in original batch summary.
- Files changed: 8
- Cleanup type: Readability-only comments and section markers
- Build result: `.\gradlew.bat build` succeeded
- Short notes: Added ownership comments and section markers; packet behavior unchanged.

## 003_api_network_structure
- Batch number: 003
- Target modules: `api/`, `network/`, `modules/humidity/`, `modules/pressure/`
- Files reviewed: 8
- Files changed: 2
- Cleanup type: Small structure/readability cleanup
- Build result: `.\gradlew.bat build` succeeded
- Short notes: Reorganized `AtmoApi` and `NetworkHandler` into clearer sections.

## 004_region_temperature_structure
- Batch number: 004
- Target modules: `modules/region/`, `modules/temperature/`
- Files reviewed: 33
- Files changed: 3
- Cleanup type: Structure/readability cleanup
- Build result: `.\gradlew.bat build` succeeded
- Short notes: Small section/grouping improvements in temperature commands and spike storage.

## 005_region_dedicated_structure
- Batch number: 005
- Target modules: `modules/region/`
- Files reviewed: 18
- Files changed: 1
- Cleanup type: Dedicated region architecture pass
- Build result: `.\gradlew.bat build` succeeded
- Short notes: Moved legacy aggregation methods in `ForecastRegion` to a bottom legacy section.

## 006_wind_weather_storm_structure
- Batch number: 006
- Target modules: `modules/wind/`, `modules/weather/`, `modules/storm/`
- Files reviewed: 25
- Files changed: 2
- Cleanup type: Backend weather logic organization
- Build result: `.\gradlew.bat build` succeeded
- Short notes: Added section comments in `WindCommand` and `GlobalStormHistoryData`.

## 007_atmosphere_structure
- Batch number: 007
- Target modules: `modules/atmosphere/`
- Files reviewed: 14
- Files changed: 1
- Cleanup type: Atmosphere runtime structure cleanup
- Build result: `.\gradlew.bat build` succeeded
- Short notes: Moved deprecated compatibility methods in `RegionAtmosphereState` to a legacy section.

## 008_client_core_structure
- Batch number: 008
- Target modules: `client/` excluding render and rendering folders
- Files reviewed: 20
- Files changed: None
- Cleanup type: Client core architecture review
- Build result: `.\gradlew.bat build` succeeded
- Short notes: No safe structural edits were made; classes were classified and left untouched.

## 009_client_loading_structure
- Batch number: 009
- Target modules: `client/loading/`
- Files reviewed: 6
- Files changed: None
- Cleanup type: Client loading architecture review
- Build result: `.\gradlew.bat build` succeeded
- Short notes: Loading subsystem was already well organized; no safe structure edits were made.

## 010_client_non_render_structure
- Batch number: 010
- Target modules: `client/` excluding render and rendering folders
- Files reviewed: Not recorded in original batch summary.
- Files changed: 2
- Cleanup type: Client non-render structure cleanup
- Build result: `.\gradlew.bat build` succeeded
- Short notes: Added section comments to `ClientSyncLock` and `BiomeClientTemperatureCache`; no behavior changed.

## 011_support_classes_structure
- Batch number: 011
- Target modules: `client/screen/`, `client/crash/`, `registry/`, `config/`, `event/`, `blocks/`, `items/`, `tools/debug/`
- Files reviewed: Not recorded in original batch summary.
- Files changed: 6
- Cleanup type: Support classes structure/readability cleanup
- Build result: `.\gradlew.bat build` succeeded
- Short notes: Added section comments to registry classes and `ClientOnlyRegistrar`; no behavior changed.

## 012_support_compat_manager_structure
- Batch number: 012
- Target modules: `util/`, `tools/debug/`, `async/`, `compat/`, `manager/`
- Files reviewed: Not recorded in original batch summary.
- Files changed: 6
- Cleanup type: Support utilities / compat structure cleanup
- Build result: `.\gradlew.bat build` succeeded
- Short notes: Added section markers to utility and compat helpers; manager classes were reviewed but left unchanged.

## 013_final_remaining_source_structure
- Batch number: 013
- Target modules: Remaining unreviewed Java source areas under `src/main/java/`
- Files reviewed: Not recorded in original batch summary.
- Files changed: 9
- Cleanup type: Final broad class organization pass
- Build result: `.\gradlew.bat build` succeeded
- Short notes: Added section markers to a small set of safe core and snapshot classes; major runtime, render, mixin, and storm classes were reviewed but mostly left unchanged for safety.

## 016_real_restructure_step_1
- Batch number: 016
- Target modules: `util/` -> `tools/debug/`
- Files reviewed: 1
- Files changed: 1
- Cleanup type: Real architecture restructure, Step 1
- Build result: `.\gradlew.bat build` succeeded
- Short notes: Moved the debug particle atlas logger into the debug tools package as the first low-risk real refactor.

## 017_full_codebase_restructure
- Batch number: 017
- Target modules: Full real source tree except protected `clouds/`
- Files reviewed: Not recorded in original batch summary.
- Files changed: 15
- Cleanup type: Full codebase restructure pass
- Build result: `.\gradlew.bat build` succeeded after two intermediate fixes
- Short notes: Moved debug utilities out of render/util, moved weather and cloud queue helpers into more specific owner packages, and fixed one typo in the existing cloud scaffold so the codebase stayed buildable.

## 018_remaining_files_restructure
- Batch number: 018
- Target modules: Remaining unhandled Java source files under `src/main/java/` excluding protected `clouds/`
- Files reviewed: 332 remaining files after the handled registry was applied
- Files changed: 6
- Cleanup type: Remaining-files architectural restructure
- Build result: `.\gradlew.bat build` succeeded
- Short notes: Moved the whiteout fog handler, tornado client effects, and todo tools into better packages, deleted an empty client hook, updated the client tick import, and classified the remaining set with explicit handled statuses.

## 019_risky_files_cluster_restructure
- Batch number: 019
- Target modules: Risky remaining files clustered from `03_remaining_files_handling_matrix.md`
- Files reviewed: 76 risky files clustered into 10 groups
- Files changed: 4 source files plus handled registry and log updates
- Cleanup type: Risk-cluster refactor
- Build result: `.\gradlew.bat build` succeeded
- Short notes: Moved the shared render mesh helpers into a tighter render mesh package, updated renderer imports, deleted an empty client hook, and recorded the remaining risky classes for manual review.

## 020_risky_client_state_cluster_restructure
- Batch number: 020
- Target modules: Client state or tick lifecycle risk cluster
- Files reviewed: 5 cluster files from the risky manual review set
- Files changed: 2 source files plus docs/log updates
- Cleanup type: Client state helper extraction
- Build result: `.\gradlew.bat build` succeeded
- Short notes: Extracted internal target-update helpers from the client atmosphere and fog state classes without changing tick order or smoothing behavior.
