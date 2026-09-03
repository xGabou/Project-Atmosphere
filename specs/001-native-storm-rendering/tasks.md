---

description: "Dependency-ordered implementation tasks for native storm rendering"
---

# Tasks: Native Storm Rendering

**Input**: Design documents from `/specs/001-native-storm-rendering/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Correction precedence**: For storm density composition, the 2026-08-19 Phase 4S correction is
authoritative and supersedes every earlier statement in this feature's documents. Precedence order,
highest first: `contracts/storm-density-composition.md`; the "Phase 4S Correction" section of
`plan.md`; Decisions 12-16 in `research.md`; the "Storm Density Composition" section of
`data-model.md`; then the pre-4S T041/Decision-11 material. Where an earlier document calls the
descriptor union the visible storm body, or calls `1 - lobeDensity` a distance field, read the
corrected model instead.

For candidate semantics, rain attachment, and history lifecycle, the T041 correction in `plan.md`, Decision 11 in `research.md`, and the storm-field invariants in `data-model.md` supersede pre-audit role-preserving/alpha-envelope language in `contracts/` and `quickstart.md` until T073 synchronizes those documents.

**Tests**: Required by FR-001 through FR-020 and SC-001 through SC-010. Write the specified automated checks before the implementation they cover and confirm that new assertions fail for the expected reason.

**Organization**: Tasks are grouped by user story and ordered so the direct structured storm path is the independently testable MVP. Every task names the primary files or systems affected and its earlier-task dependency where one exists.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel after its stated prerequisites because it affects different files and does not depend on another incomplete task in the same group.
- **[Story]**: Maps the task to a user story from `spec.md`.
- Requirement IDs in descriptions provide direct specification traceability.
- Existing task IDs remain stable for audit history. Phase 4R uses T074-T099, Phase 4S uses T100-T118, Phase 4P uses T119-T123, the renderer-wide correction gate uses T127-T134, and the active performance redesign uses T135-T160. Phases are placed by dependency order, not by numeric sorting.
- Completed Phase 4R implementation tasks are **not** retroactively unchecked. Where the Phase 4S architecture invalidates what a completed task built, that task is listed as superseded and a new Phase 4S task ID owns the replacement. Only validation gates whose acceptance criteria are invalidated are reopened.

## Phase 1: Setup and Baseline

**Purpose**: Establish reproducible checks and the feature-specific test entry point without changing production behavior.

- [X] T001 [P] Run `cloudMorphologyTopologySandbox`, `volumetricStabilityDiagnosticsSandbox`, `architectureBoundaryCheck`, and `build`, then record commands, results, active renderer, and the reproduced defect baseline in `specs/001-native-storm-rendering/validation/baseline.md`
- [X] T002 [P] Create the deterministic test harness and assertion/report helpers in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java`
- [X] T003 Register `stormVolumetricGeometrySandbox` and add it to `check` in `build.gradle` (depends on T002)

**Checkpoint**: The existing project baseline is recorded and the new empty feature harness runs under Gradle.

---

## Phase 2: Foundational Data and Thread Boundaries

**Purpose**: Add the bounded, client-only data contracts and lifecycle seams required by every user story.

**Critical**: Complete this phase before starting story implementation.

- [X] T004 [P] Create the immutable descriptor, stable identity/order key, role encoding, finite-value validation, and four-texel packing contract in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeDescriptor.java` [FR-001, FR-014]
- [X] T005 [P] Create the copied primitive-only async request model with world, dimension, owner, resource, map, distance, signature, and generation tokens in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormGeometryBuildInput.java` [FR-009, FR-019]
- [X] T006 [P] Create the immutable worker-result model for selected groups, descriptor upload data, packed candidate pixels, counters, and timing in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormGeometryBuild.java` [FR-013, FR-019]
- [X] T007 [P] Create the immutable adopted-frame model used by both GPU rendering and CPU visual-density consumers in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormRenderSnapshot.java` [FR-008, FR-009]
- [X] T008 Add a bounded non-blocking client submission API that never invokes rejected work inline, preserving all existing APIs, in `src/main/java/net/Gabou/projectatmosphere/util/AsyncAtmosphereService.java` (depends on T005-T006) [FR-019]
- [X] T009 Create bounded capacities, reusable primitive buffers, lifecycle reset APIs, and no-GL worker helpers in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeSpatialIndex.java` (depends on T004-T006) [FR-019]
- [X] T010 Create the one-in-flight/one-latest-pending coordinator state machine and render-thread adoption seam in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormGeometryBuildCoordinator.java` (depends on T005-T009) [FR-009, FR-019]

**Checkpoint**: Immutable CPU/GPU boundary types, capacities, reset semantics, and non-blocking scheduling are available without connecting the production renderer.

---

## Phase 3: User Story 1 - Readable Volumetric Storms (Priority: P1) — MVP

**Goal**: Render one coherent base/core/tower/anvil system with curved three-dimensional profiles, stable stage ordering, smooth same-group overlap, and no wall/slab/cutoff artifacts.

**Independent Test**: With native ownership, inspect an isolated and overlapping severe storm from below, beside, inside, and above during movement/retargeting. All stages remain connected and ordered, the anvil spreads beyond the tower, and no full-height wall, planar underside, rectangular cutoff, or overlap seam appears.

### Tests for User Story 1

- [X] T011 [P] [US1] Add failing deterministic assertions for role ordering, base-to-core-to-tower-to-anvil overlap, wind-aligned anvil extension, seed stability, and retarget continuity in `src/test/java/net/Gabou/projectatmosphere/clouds/simulation/CloudMorphologyTopologySandbox.java` (depends on T001) [FR-001, FR-003, FR-005; SC-002]
- [X] T012 [US1] Add failing descriptor validation, role pack/unpack, analytic profile continuity, permutation-invariant same-group union, and non-planar intersection assertions in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` (depends on T003-T007) [FR-001-FR-004]
- [X] T013 [US1] Add failing complete-group selection, stable ordering, base-65 candidate packing, role-preserving overflow, conservative shear bounds, and no-partial-group assertions in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` (depends on T012) [FR-004, FR-019]; historical role-preserving overflow coverage is superseded by the group-witness correction in T079
- [X] T014 [US1] Add failing cache-hit, quantized dirty-signature, coalescing, async saturation, stale-generation rejection, and render-thread adoption assertions in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` (depends on T008-T010, T013) [FR-005, FR-009, FR-019]

### Core Geometry and Data Mapping

- [X] T015 [P] [US1] Replace co-planar severe member placement with deterministic BASE/CORE/TOWER/ANVIL offsets, spans, radii, wind lean, and connected envelope constraints in `src/main/java/net/Gabou/projectatmosphere/clouds/simulation/CloudMorphologyGenerators.java` (depends on T011) [FR-001-FR-003]
- [X] T016 [US1] Apply the same role envelope contract during severe-cluster retargeting with continuous parameter interpolation and stable membership in `src/main/java/net/Gabou/projectatmosphere/clouds/simulation/CloudMorphologyGenerators.java` (depends on T015) [FR-005; SC-002]
- [X] T017 [P] [US1] Implement allocation-free Java BASE, CORE, TOWER, ANVIL density profiles, descriptor-local bounds, height-dependent shear, and order-independent group union in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeEvaluator.java` (depends on T004, T012) [FR-001-FR-004]
- [X] T018 [US1] Make synchronized morphology membership and source geometry the sole stable role/identity input when converting `VolumetricRenderCell` values into `StormLobeDescriptor` values in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricRenderCell.java` (depends on T004, T016-T017) [FR-005, FR-014-FR-015]

### Cache, Async Build, and GPU Upload

- [X] T019 [US1] Implement camera-distance ranking with UUID/member-index tie breaks and admit only complete severe groups up to the 64-descriptor capacity in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeSpatialIndex.java` (depends on T013, T018) [FR-004, FR-019]
- [X] T020 [US1] Implement conservative tile coverage and deterministic eight-slot overflow that retains one nearest candidate per represented role before distance-ranked fill in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeSpatialIndex.java` (depends on T019) [FR-001, FR-004, FR-019]; historical policy is superseded by the one-witness-per-group acceleration in T096
- [X] T021 [US1] Implement descriptor/grid signatures, quantized-bound dirty tracking, cache hits, reusable upload buffers, and reset behavior in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeSpatialIndex.java` (depends on T020) [FR-005, FR-019]
- [X] T022 [US1] Implement coalesced worker submission, pure-CPU build execution, latest-request replacement, generation validation, stale discard, and last-valid result retention in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormGeometryBuildCoordinator.java` (depends on T008, T014, T021) [FR-005, FR-009, FR-019]
- [X] T023 [US1] Replace the three storm-role render targets with render-thread-created 256×256 `RGBA32F` candidates and 4×64 `RGBA32F` descriptors, including destruction/reload handling, in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderTargets.java` (depends on T021-T022) [FR-019]
- [X] T024 [US1] Replace the three managed storm samplers with `StormCandidateMapSampler` and `StormDescriptorSampler`, document units 0-14, and enforce the unchanged fragment texture-unit ceiling in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.json` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/CloudTextureUnitContract.java` (depends on T023) [FR-017, FR-019]

### Direct Storm Shader and Native Integration

- [X] T025 [US1] Implement shader descriptor decoding and continuous BASE/CORE/TOWER/ANVIL analytic profiles matching `StormLobeEvaluator` in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` (depends on T017, T024) [FR-001-FR-003, FR-006] — REOPENED by T041 audit
- [X] T026 [US1] Implement group-aware smooth union, local descriptor bounds, candidate iteration, and conservative direct-storm segment intersection without generic global base/top clipping in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` (depends on T020, T025) [FR-002, FR-004, FR-006] — REOPENED by T041 audit
- [X] T027 [US1] Bind adopted descriptor/candidate generations and preserve broad weather/morphology material, erosion, lighting, shadow, and distant fallback inputs in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/CloudWeatherMapRenderer.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderer.java` (depends on T022-T026) [FR-006, FR-014]
- [X] T028 [US1] Connect descriptor gathering, coordinator requests, render-thread adoption/upload, successful-frame publication, and world/dimension/resource reset calls in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java` (depends on T022-T027) [FR-005, FR-009, FR-014]
- [X] T029 [US1] Remove production generation/binding of the obsolete storm structure, layer-height, and tower targets and delete `src/main/resources/assets/projectatmosphere/shaders/core/cloud_weather_storm_structure.fsh`, `src/main/resources/assets/projectatmosphere/shaders/core/cloud_weather_storm_structure.json`, `src/main/resources/assets/projectatmosphere/shaders/core/cloud_weather_storm_heights.fsh`, and `src/main/resources/assets/projectatmosphere/shaders/core/cloud_weather_storm_heights.json` only after the direct path passes T011-T028 (depends on T027-T028) [FR-001-FR-004]
- [ ] T030 [US1] Run the US1 sandboxes and capture below/beside/inside/above, overlap, movement, and retarget evidence with pass/fail notes in `specs/001-native-storm-rendering/validation/us1-readable-storms.md` (depends on T011-T029) [SC-001-SC-002] — REOPENED by T041 audit

**Checkpoint**: User Story 1 is an independently demonstrable native structured-storm MVP; broad maps still provide material/distant fallback, and no packet/save/forecast behavior changed.

---

## Phase 4: User Story 2 - Stable Cloud and Rain Experience (Priority: P2)

**Goal**: Align volumetric rain, camera density, whiteout, and temporal history with the exact visible structured storm without stipple, shimmer, ghosting, or clear-air overwork.

**Independent Test**: Move through and around a locally raining severe storm, remain stationary for 60 seconds, and repeat with remote rain but local clear air. Rain stays locally attached, visible occupancy agrees with whiteout, and invalid history is rejected without persistent ghosts.

### Tests for User Story 2

- [X] T031 [US2] Add failing local-versus-global precipitation occupancy, unsupported-shaft rejection, deterministic coarse-segment integration, and clear-air fast-path assertions in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricStabilityDiagnosticsSandbox.java` (depends on T030) [FR-007; SC-001, SC-004]
- [X] T032 [US2] Add failing GPU-equation fixture vectors and visible-boundary/camera-density agreement assertions for every storm role and overlap case in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` (depends on T017, T030) [FR-008; SC-003] — REOPENED by T041 audit
- [X] T033 [US2] Add failing history-invalidation assertions for topology generation, world, dimension, owner, resource, and resolution changes plus history-retention assertions for normal interpolation in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricStabilityDiagnosticsSandbox.java` (depends on T030) [FR-009; SC-004]

### Whiteout, Rain, and History Implementation

- [X] T034 [P] [US2] Evaluate adopted `StormRenderSnapshot` descriptors through `StormLobeEvaluator` without per-query allocation in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/ClientCloudVisualDensity.java` (depends on T007, T017, T032) [FR-008] — REOPENED by T041 audit
- [X] T035 [US2] Publish the exact successfully composited storm generation to visual-density state and keep `CameraCloudDensityTracker` on its existing interface in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/CameraCloudDensityTracker.java` (depends on T028, T034) [FR-008]
- [X] T036 [P] [US2] Change volumetric rain eligibility and empty-space pretests from global precipitation to local weather/morphology/direct-storm support in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` (depends on T026, T031) [FR-007]
- [X] T037 [US2] Implement world-anchored deterministic coarse-segment rain integration, local base attachment, and body/rain step separation in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` (depends on T036) [FR-007] — REOPENED by T041 audit
- [X] T038 [US2] Add adopted storm topology generation and effective resolution generation to history validity while preserving history during descriptor interpolation/advection in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderer.java` (depends on T033, T035) [FR-009]
- [X] T039 [US2] Clear pending/adopted storm density and temporal state on disconnect, world/dimension/owner change, resource reload, resize, and direct-path disable in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/ClientCloudVisualDensity.java` (depends on T035, T038) [FR-008-FR-009]
- [X] T040 [US2] Add regression assertions proving nearby custom rain/snow and its vanilla fallback remain independently owned in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricStabilityDiagnosticsSandbox.java`, without changing `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/CustomPrecipitationRenderer.java` (depends on T031, T037) [FR-007, FR-020]
- [X] T041 [US2] Run dry/local-rain/remote-rain/boundary-crossing stationary and moving 60-second captures and record density agreement, history resets, and artifact results in `specs/001-native-storm-rendering/validation/us2-rain-whiteout-stability.md` (depends on T031-T040) [SC-001, SC-003-SC-004]

**Checkpoint**: User Stories 1 and 2 are independently testable; structured storm occupancy, rain, whiteout, and history agree.

---

## Phase 4R: Storm Morphology Correction

**Purpose**: Correct the T041-audited density architecture without redesigning the existing descriptor, packing, build, snapshot, async, render-thread, server, networking, saved-data, forecast, Simple Clouds, custom precipitation, or camera-density ownership systems.

**Goal**: Make the descriptor set the authoritative visible storm field through descriptor-local distance-like evaluation, lobe/group smooth unions, local BASE underside and rain attachment, valid descriptor slots, safe lifecycle/history behavior, and acceleration-only group candidates.

**Independent Test**: Run the fixed complete-group silhouette, locality, independent GLSL parity, composition, rain/body, slot/fallback, async/signature, history, and acceleration regressions. *(The original "ten-item visual checklist" acceptance is superseded; T098a/T098b and T099 now use the split structural/visual gate and the two-part positive/negative checklist introduced by Phase 4S.)*

**Gate (revised 2026-08-19)**: Every new geometry regression test must demonstrably fail against the audited implementation for the intended reason before its corresponding fix is implemented. The former absolute prohibition on US3 and performance work before this phase completed has been **removed** - see Phase 4P and "Dependencies and Execution Order".

### Tests First

- [X] T074 [US1] Add a fixed synthetic complete-group silhouette regression in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` that samples horizontal sections and verifies the tower is narrower than the base, the anvil is wider than the tower, exactly one connected component exists, adjacent-height radius changes are bounded, and no vertical step discontinuity exists (depends on T041) [FR-001-FR-005; SC-001-SC-002]
- [X] T075 [US1] Add a locality regression in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` proving that adding, removing, or moving a descriptor outside a probe point's support does not change density at that point (depends on T041) [FR-002, FR-004]
- [X] T076 [US2] Replace the fake GPU values and same-function Java-call parity checks with an independent GLSL equation fixture in `src/test/resources/net/Gabou/projectatmosphere/clouds/client/render/volumetric/storm_lobe_equations.glsl` and harness assertions in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` covering every role, lobe-to-lobe union, group-to-group union, underside, and boundary case against `StormLobeEvaluator`; mark reopened T032 complete only when the independent fixture passes (depends on T041) [FR-004, FR-008; SC-003]
- [X] T077 [US1] Replace the duplicate BASE-density assertion with a geometry-composition regression that distinguishes independent descriptor evaluation and smooth lobe/group union from one statistical group ellipse in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` (depends on T041) [FR-001-FR-004]
- [X] T078 [US2] Add a rain/body agreement regression proving precipitation support remains contained within the exact rendered storm union and attachment Y follows the locally contributing BASE-lobe underside in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricStabilityDiagnosticsSandbox.java` (depends on T041) [FR-007-FR-008; SC-001, SC-003]
- [X] T079 [P] [US2] Add failing lifecycle/acceleration regressions for counted-slot validity or explicit sentinels, incomplete-group fallback, rejected-build re-request, cluster-only grid/topology signatures, independent world/dimension/owner/resource generations, same-frame history reset, one-witness-per-intersecting-group candidate coverage/non-authority, bounded per-group intersection coverage, and the `shaftDensity()` maximum-precipitation argument in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` and `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricStabilityDiagnosticsSandbox.java` (depends on T041) [FR-005, FR-007-FR-009, FR-019]
- [X] T080 Run T074-T079 against the audited implementation before production fixes, record every expected failure and defect mapping in `specs/001-native-storm-rendering/validation/phase4r-fail-first.md`, and block implementation for any new geometry test that does not fail meaningfully (depends on T074-T079)

### Descriptor Field and Composition Implementation

- [X] T081 [US1] Replace `StormLobeEvaluator.finishGroupEnvelope()` with authoritative per-descriptor distance-like evaluation and a real smooth union lobe-to-lobe followed by group-to-group in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeEvaluator.java`, scale each blend radius relative to the smaller participating lobe radius, and verify allocation-free use from `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/ClientCloudVisualDensity.java`; mark reopened T034 complete when verified (depends on T080) [FR-001-FR-004, FR-008]
- [X] T082 [US1] Remove statistical center/second-moment/spread/extent envelope calculations from `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeEvaluator.java` and `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` so unrelated descriptors cannot change local density (depends on T081) [FR-002, FR-004]
- [X] T083 [US1] Remove `morphologyScale`-based group ellipse rendering from `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeEvaluator.java` and `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh`, preserving role radii, orientation, shear, local vertical span, density, and edge softness through final body evaluation (depends on T082) [FR-001-FR-003]
- [X] T084 [US1] Remove the binary `weight > 1e-8` group gate and alpha-style geometry accumulation from `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeEvaluator.java` and `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh`; use only descriptor support and distance-like smooth unions for body composition (depends on T083) [FR-002, FR-004]
- [X] T085 [US1] Mirror the corrected authoritative Java equations independently in `directStormShape()` in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh`, including descriptor-local support and both union levels; mark reopened T025 and T026 complete only after T076 parity passes (depends on T076, T081-T084) [FR-001-FR-004, FR-006, FR-008]
- [X] T086 [US1] Derive the visible storm underside locally from contributing BASE lobes instead of one group-wide `groupMinY` in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeEvaluator.java` and `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` (depends on T081, T085) [FR-002-FR-003]
- [X] T087 [US1] Fix `StormGeometryBuildCoordinator.refreshLiveDescriptors()` in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormGeometryBuildCoordinator.java` and its `StormLobeCount` upload in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java` so missing members are compacted or explicitly sentinel-marked/skipped and never decode as fake group 0 BASE descriptors at world origin (depends on T079) [FR-004-FR-005]
- [X] T088 [US1] Fix descriptor ownership fallback in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java` so incomplete or omitted groups use `familyMacroShape`/broad-map fallback instead of disappearing while complete descriptor-owned groups remain authoritative (depends on T079, T087) [FR-001, FR-005, FR-014]
- [X] T089 [US2] Attach volumetric rain to the exact rendered storm union and its local BASE-lobe underside in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh`, `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricPrecipitationModel.java`, and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/ClientCloudVisualDensity.java`; mark reopened T037 complete when T078 passes (depends on T078, T085-T086) [FR-007-FR-008]
- [X] T090 [US1] Remove residual per-member raster modulation from descriptor-owned volume in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/CloudWeatherMapRenderer.java`, moving every still-required morphology input onto `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeDescriptor.java` within the existing four-texel packing contract (depends on T077, T085, T088) [FR-002, FR-004, FR-014]

### Lifecycle, History, and Acceleration Corrections

- [X] T091 [US1] Fix the async rebuild stall in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormGeometryBuildCoordinator.java` so rejection of a completed build cannot leave `requestedGridSignature` permanently stale and always triggers or preserves a valid request for the current signature (depends on T079) [FR-005, FR-009, FR-019]
- [X] T092 [US1] Restrict storm grid and topology signatures to cluster-sourced severe cells in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeSpatialIndex.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java` so unrelated LOD cloudlet changes neither rebuild geometry nor invalidate temporal history (depends on T079, T091) [FR-005, FR-009, FR-019]
- [X] T093 [US2] Populate world, dimension, owner, and resource generations independently in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricHistoryValidity.java` and pass them from `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderer.java` instead of copying one lifecycle generation into all four fields (depends on T079, T092) [FR-009]
- [X] T094 [US2] Close the deferred reset window in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderer.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java` by invalidating history before the first frame under a changed lifecycle key can composite against old history (depends on T079, T093) [FR-009; SC-004]
- [X] T095 [US2] Fix `VolumetricPrecipitationModel.shaftDensity()` in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricPrecipitationModel.java` where `localPrecipitation` is passed in the `maxPrecipitation` position, retaining independent ownership in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/CustomPrecipitationRenderer.java` (depends on T078-T079) [FR-007, FR-020]
- [X] T096 [US1] Restore `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeSpatialIndex.java` and `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` as conservative geometry acceleration after the corrected union is authoritative: encode one stable witness for every conservatively intersecting admitted group, resolve its bounded descriptor range, move evaluation behind coverage early-outs, and reject bounded non-intersecting groups without letting the grid define density (depends on T085, T087-T092) [FR-002, FR-004, FR-012, FR-019]

### Revalidation Gate

**Rank 2 (per-sample descriptor cost) 2026-09-01: MEASURED AND REJECTED - CASE C. Production shader
unchanged; proceed to rank 1.** T137 ranked descriptor cost second at 1.5-3x and flagged the estimate
as its weakest. Closing that gap removed the case for the optimization rather than confirming it.
*Harness:* workload counters wired per cell, the zero-descriptor refusal path now consumes a bounded
retry (the bug that lost PLAY_MID/Ultra, PLAY_HIGH and CLEAR), CLEAR scenarios accept descriptors=0
while storm scenarios still require them to hold, per-pose re-resolution preserved, and two
diagnostic-only arms added. *Structure:* descriptor data is four texels per lobe in a single **4 KB**
texture, and T122 already implemented load-once-and-reuse - it avoids 116M of what would be 351M
fetches. The remaining redundancy is texel 3 of the witness being read **five times per group pass**,
about 19 removable fetches per `directStormShape`. Measured at SIDE/Low: 13.4 steps per pixel, 13.4
descriptor evaluations per step, **10.09 fetches per evaluation, 235M fetches per frame**. *Decisive
experiment:* the existing T122 arm re-issues six fetches per lobe with identical arithmetic and
output, so +49% fetch volume measures what fetches cost. Same run, same pose: SIDE +20.6 to +27.4%,
**PLAY_NEAR/Ultra only +4.9%**. Fetch elasticity is ~0.47 at stress framing and **~0.10 in
representative gameplay** - the texture is L1-resident, so these are cache hits, not bandwidth.
Removing the achievable 19% of fetches is worth **1.10x at SIDE and 1.02x at PLAY_NEAR**, against an
estimate of 1.5-3x. Not implemented, per the instruction not to build machinery for a small
theoretical gain. *Also measured:* lighting is 18-20% at stress framing but only **6.5-11.4% in
representative gameplay**, so rank 4's ceiling is 1.10x there, not 1.29x. Together descriptor fetches
and lighting are under 20% of PLAY_NEAR/Ultra - **over 80% is the per-pixel march**, which is what
rank 1 attacks. Revised stack **10-24x** (was 27-40x). Representative gameplay needs 12.6x and still
closes; the parked stress case needs 120x and does not. SC-006 is not rescoped. Evidence in
`validation/performance-descriptor-cost.md`.

**Rank 2 (per-sample descriptor cost) 2026-09-01: MEASURED AND REJECTED - CASE C. Production shader
unchanged; proceed to rank 1.** T137 ranked descriptor cost second at 1.5-3x and flagged the estimate
as its weakest. Closing that gap removed the case for the optimization rather than confirming it.
*Harness:* workload counters wired per cell, the zero-descriptor refusal path now consumes a bounded
retry (the bug that lost PLAY_MID/Ultra, PLAY_HIGH and CLEAR), CLEAR scenarios accept descriptors=0
while storm scenarios still require them to hold, per-pose re-resolution preserved, and two
diagnostic-only arms added. *Structure:* descriptor data is four texels per lobe in a single **4 KB**
texture, and T122 already implemented load-once-and-reuse - it avoids 116M of what would be 351M
fetches. The remaining redundancy is texel 3 of the witness being read **five times per group pass**,
about 19 removable fetches per `directStormShape`. Measured at SIDE/Low: 13.4 steps per pixel, 13.4
descriptor evaluations per step, **10.09 fetches per evaluation, 235M fetches per frame**. *Decisive
experiment:* the existing T122 arm re-issues six fetches per lobe with identical arithmetic and
output, so +49% fetch volume measures what fetches cost. Same run, same pose: SIDE +20.6 to +27.4%,
**PLAY_NEAR/Ultra only +4.9%**. Fetch elasticity is ~0.47 at stress framing and **~0.10 in
representative gameplay** - the texture is L1-resident, so these are cache hits, not bandwidth.
Removing the achievable 19% of fetches is worth **1.10x at SIDE and 1.02x at PLAY_NEAR**, against an
estimate of 1.5-3x. Not implemented, per the instruction not to build machinery for a small
theoretical gain. *Also measured:* lighting is 18-20% at stress framing but only **6.5-11.4% in
representative gameplay**, so rank 4's ceiling is 1.10x there, not 1.29x. Together descriptor fetches
and lighting are under 20% of PLAY_NEAR/Ultra - **over 80% is the per-pixel march**, which is what
rank 1 attacks. Revised stack **10-24x** (was 27-40x). Representative gameplay needs 12.6x and still
closes; the parked stress case needs 120x and does not. SC-006 is not rescoped. Evidence in
`validation/performance-descriptor-cost.md`.

**T136/T137 2026-09-01: representative gameplay is 2-13x over budget; the parked severe worst case is
70-125x. Cost is pixel-bound. No implementation.**

*Harness.* T135's decay contamination is fixed three ways and every cell below held `descriptors=10`
for its whole sample: per-sample descriptor validation that discards a cell whose count ever falls,
deterministic respawn/re-adopt with bounded retries, and **per-pose fixture re-resolution** - `pa
cloud spawn` places the storm at the player, so pre-respawn poses aimed at empty sky and the same
FAR/Medium cell measured 113.6 ms on one attempt and 15.1 ms on another.

*T136.* RTX 4070 Laptop, 1920x1080, each mode on its own resolution scale. Ultra cloud p50:
**NEAR_EDGE 999.7 (125x)**, ABOVE 678.8 (84.8x), SIDE 561.5 (70.2x), FAR 270.6 (33.8x), but
**PLAY_NEAR 102.3 (12.8x)**; PLAY_MID/High 29.5 (4.5x) and PLAY_MID/Low **5.2 (1.7x)**. The true
worst case is NEAR_EDGE, not SIDE. Non-cloud remainder is **0.6-2.9 ms** everywhere and clear weather
is inside budget at every mode, so the cost is entirely storm-driven. **Scaling: 4.00x pixels cost
4.69x and 8.41x, while 1.60x step budget at fixed pixels cost only 1.23x and 1.39x** - cost is
pixel-bound and the step cap is not the work unit, because rays exit on the transmittance floor at
38-85 of 128. Lighting measured by a constant-radiance arm at **21-23%** of cloud cost (ceiling
1.29x). Gap recorded: the descriptor-evaluation/fetch counters exist but were not wired into the
sweep, so per-sample cost is not isolated.

*T137.* Ranked by contribution, not ease: 1 internal resolution + temporal reconstruction (4x alone,
8-16x with reconstruction; the only measured order-of-magnitude lever), 2 per-sample descriptor cost
(1.5-3x, **image-neutral**, weakest estimate), 3 distance/LOD (1.5-2x, nothing at NEAR_EDGE),
4 lighting (1.29x measured ceiling, conflicts with T098b's open self-shadow finding), 5 samples per
ray (1.2-1.5x, highest risk per unit reward - it is the T098a machinery). Expected cumulative stack
**27-40x**. **SC-006 is credible for representative gameplay and not for the parked worst case**:
at 30x, PLAY_NEAR lands at 3.4 ms but NEAR_EDGE is still 33 ms against 8. Proposed mode ladder moves
resolution/reconstruction rather than step counts. **Recommended first T138 increment: wire the
counters, re-measure, then implement rank 2**, because it is the only large image-neutral lever and
landing it first reduces how much image change the rest of the stack must buy. Evidence in
`validation/performance-baseline.md` and `validation/performance-architecture.md`.

**T098a 2026-09-01: PASSES on Forge-1.20.1. T135 established and FAILING by 9x-64x.**

*T098a.* `Forge-1.20.1` had advanced to 4e356c3; it was merged into the correction branch (clean,
one file) so the verified tree is the production head plus both T098 corrections and their guards.
`./gradlew check` and `./gradlew build` pass with 12 invariants including both T098 guards and the
per-descriptor advance guard. Live campaign on group `6a229682`: centre-column cloud share
**1.0000** and longest inner sky run **0 px** at FAR, SIDE, UNDER, ABOVE, CURRENT_ONLY, both
LATERALs and NEAR_EDGE; all three traced rays terminate on the transmittance floor with **zero step
caps** and ray identity AGREES to five decimals (waist composites at 0.98730); severe scale
preserved at 840 px of 900. All seven T098a criteria pass. Evidence in
`validation/t098a-structural-correctness.md`. **T099's T098a dependency is discharged.**

*T135.* Five-mode budget contract recorded in `validation/performance-budget.md`, measured on an
**RTX 4070 Laptop at 1920x1080** with each mode using its own resolution scale and the non-cloud
remainder measured rather than modelled. Budgets retained as targets (3.0/4.0/5.0/6.5/8.0 ms cloud,
SC-006 Ultra p95 16.7 ms total). Measured at the SIDE acceptance pose: Low **56.3 ms** (18.8x),
Low 24 **117.9** (29.5x), Medium **203.6** (40.7x), High **253.2** (39.0x), **Ultra 515.2 ms
(64.4x)**, with total frame **516 ms p50 / 542 ms p95 against a 16.7 ms budget - a 32x miss, about
1.9 FPS**. BELOW/Medium and BELOW/High are 55x. **The non-cloud remainder is 0.3-1.3 ms in every
cell**, so the cloud raymarch is essentially the whole frame; clear weather is inside budget at every
mode (0.31-3.89 ms), so the cost is entirely storm-driven. Cost tracks raymarch samples
(4.5-18.1 ns per step-sample), not a stall. The required saving is **one to two orders of
magnitude**, which is beyond constant-factor tuning and is the input to T136/T137. Harness
limitation recorded: the 25-cell sweep outlives the spawned fixture, which contaminated the ABOVE
cells and BELOW/Ultra; T136 must hold or re-spawn the storm.

**T098 2026-08-30: live acceptance campaign run; result CASE D, task stays OPEN.** The envelope
extent bound (0.75 x half-height) was validated live across five distinct fresh severe fixtures
(9294726d, ae4aef49, 72259f41, d266f801, 6e8e8c73). The bound is selective as designed: over 60 live
descriptors it binds 24/24 ANVIL and 0/36 BASE/CORE/TOWER, so it did not affect ordinary roles;
worst-case BASE reached 0.736 against the 0.75 bound, a 1.9% margin, never crossed. The constant was
not tuned. Visually T098 still FAILS on all five: each reads as two cleanly separated masses with no
connecting column. Criteria 5/6/7 pass on the anvil, 1/2/3/4/8/9 fail. The fix removed the anvil's
sub-canopy haze - the gap is clean sky rather than shredded confetti - but that revealed nothing else
occupies the space: CORE and TOWER produce almost no visible density (4,635 visible voxels against
BASE's 21,521), and the skirt had been masking it. The next blocker is role density/strength
composition, NOT envelope extent, NOT descriptor allocation, and NOT STORM_MAX_BLEND_BLOCKS - there
is no connected body yet for seams to appear on. Evidence in
`validation/t098-manual-checklist.md`.

- [X] T097 Run the corrected morphology, locality, independent GLSL parity, composition, rain/body, slot/fallback, async/signature, history, precipitation, and acceleration regressions plus the US1/US2 sandboxes; record passing results in `specs/001-native-storm-rendering/validation/phase4r-automated.md` and verify each T080 expected failure is closed without weakening assertions (depends on T081-T096) [SC-001-SC-004, SC-010]
**T098 2026-08-28: ANVIL/BASE falsified; structural limit reached.** Sweeping ANVIL/BASE from
1.239 down to 0.933 - an anvil narrower than the base, outside T127 entirely - moves column share
only 7.83% to 9.28% and leaves the anvil 5.1:1 over the whole column; occupied bands stay 11/19.
**No transition point exists.** The anvil was never the dominant term: BASE alone is **4.6:1** over
CORE+TOWER, matching the 4.29x area ratio implied by T127 BASE 1044 vs CORE 504. Even deleting the
anvil would leave the base 4.6x the column. Also correcting an earlier figure: the shipped TOWER
correction raises column share to **7.83%**, not the 10.14% previously reported, which came from a
proxy that scaled CORE as well. All four levers inside the current decomposition are now measured and
exhausted (carrier, erosion, TOWER proportion, ANVIL proportion). **The descriptor-role decomposition
itself appears incapable of the required silhouette without a structural morphology redesign.** No
production change made this session.

**Superseded - T098 2026-08-28: T127 proportional contract CORRECTED; silhouette still REJECTED.** The
violated relationships are fixed at midband (lower TOWER/CORE 0.625 -> 0.700, ANVIL/upper TOWER
5.917 -> 4.251) by moving the TOWER radius multiplier from lerp(0.35, 0.24) to lerp(0.392, 0.334),
with all four relationship guards now enforced deterministically. Central-column material rose
6.49% -> 10.14% and ANVIL:TOWER fell 35.1:1 -> 21.1:1. **The live silhouette did not change: 3 of 3
fresh fixtures still show the mushroom.** At full compliance the anvil alone holds 32,515
density-visible voxels against the column's 5,976 (5.4:1), and even columnScale 1.80 - beyond any
T127 range - reaches only 24.62%. The relationship violation was real and is fixed, but it was not
the cause. See `validation/t098-manual-checklist.md`.

**Superseded - T098 root cause 2026-08-28: T127's proportional contract is violated and internally
inconsistent.** Every absolute role diameter passes its T127 range (BASE 1044, CORE 504, lower
TOWER 315, upper TOWER 216, ANVIL 1270-1287), but T127's stated *relationships* were never
guarded and two fail: **lower TOWER/CORE = 0.625** against 0.65-0.75, and **ANVIL/upper TOWER =
5.917** against 3.5-5.0. That is why T134 passed while T098 failed - only the diameters were
checked. The contract is also inconsistent: upper TOWER <= 250 with the 3.5-5.0 relationship
caps the anvil at 1250, but the anvil range reaches 1450 and the generator delivers 1278, so no
admissible upper TOWER can satisfy it. Sensitivity shows widening the column is the only
effective lever (share 6.49% -> 24.62%) while anvil reduction is nearly inert (-> 7.62%), and
vertical band coverage is unchanged at 11/19 throughout - the column is thin, not broken. Two
valid resolutions remain and choosing between them is a morphology decision. **No production
change was made.** See `validation/t098-manual-checklist.md`.

**Superseded - T098 erosion hypothesis FALSIFIED 2026-08-28.** Measured per-role on real T134 geometry:
TOWER is the **least**-eroded role (mean body 0.7032, erosion/body 0.327, erosion>=body 5.7%,
density-visible 93.3%), while BASE and ANVIL lose ~44% of samples outright. Erosion is not
erasing the tower; scaling erosion by body would inflate the base and anvil instead. The real
disparity is **volume**: CORE+TOWER occupy 7,184 samples against BASE+ANVIL's 152,307 - a
**21.2:1** ratio, with ANVIL:TOWER at **46.2:1** and the convective column just **4.50%** of the
system. No production change was made. Next candidate is the T127 tower/anvil cross-section
relationship, a specification question. See `validation/t098-manual-checklist.md`.

**Superseded - T098 root cause CORRECTED 2026-08-28.** The carrier-wavelength conclusion below is
**retracted**. Direct measurement (262,144 samples through the production domain transform)
shows the shader's `carrierRaw` has p05/p50/p95 = **0.7123/0.7836/0.8452**, matching the
`STORM_CARRIER_P05/P95` constants to three decimals, with the severe column's distribution
identical to the global one and exactly **5.06%** zeroed as designed. The dominant base feature
is **109.4 blocks** (already recorded in `morphology-thresholds.md`), not the 426-block texture
repeat. There is no dead band and no stale calibration; the earlier finding over-read a single
correlated centre-line trace. The evidence instead attributes the loss to **erosion against a
small tower cross-section**: body is non-zero where baseField is zero (Y=344 baseField 0.000,
body 0.292) but a roughly constant erosion (0.285) removes it, while the same erosion barely
dents the base/anvil's much larger body. TOWER carries a **19:1** smaller visible envelope than
ANVIL. **No production change was made.** See `validation/t098-manual-checklist.md`.

**Superseded - T098 root cause 2026-08-28.** The missing CORE/TOWER body is a **material-stage** failure, not
geometry: CORE and TOWER envelopes carry the highest mean/max envelope and the highest descriptor
strengths of any role, and T131 engages correctly. The production trace shows `carrierRaw` healthy
(0.66-0.83) everywhere while **`baseField` collapses to zero** through the whole convective column.
`stormBaseField` is `smoothstep(0.7128, 0.8451, carrier)`, so carrier below p05 maps to exactly
zero. `STORM_BASE_NOISE_SCALE = 0.0025` gives the carrier a ~426-block vertical period; the T134
column is 865 blocks, so it spans **2.03 periods** and the carrier dips below p05 in a horizontal
dead band that slices the storm. Pre-T134 the column was ~284 blocks - under one period - so the
calibration was sound. `renderer-wide-architecture-audit.md:471` required these wavelengths to be
re-evaluated after a derived system scale; that remeasurement was never done. **No production
change was made** - every candidate fix is morphology-wide or a subjective visual choice. See
`validation/t098-manual-checklist.md`.

**T098 status 2026-08-27: REJECTED on live evidence.** Three fresh fixtures (`142bca36`,
`cf410ea8`, `aa731334`) captured automatically through `StormT098CaptureDriver`. All three show
the same failure: a smooth banded anvil dome, a broken neck, and a detached lower base - a
mushroom silhouette rather than a cumulonimbus. **Six of nine FR-023 positive criteria are
absent** and at least four FR-024 rejected forms are present. Surface detail is good (ABOVE
shows correct multi-scale billowing); the failure is vertical continuity and descriptor joining.
The 48-block blend cap saturates on **88.9%** of T134 descriptor pairs, delivering joins at
16-30% of the requested width, but is graded **PARTIAL** - not proven sufficient, because CORE
and TOWER descriptors are adopted yet produce no visible body. No production change was made.
See `validation/t098-manual-checklist.md`.


- [X] T098a [BLOCKING CORRECTNESS] **Structural / Correctness Acceptance.** On the actual
  production branch, integrate and re-run the verified T098 correction/evidence chain, then record
  a severe-scale SIDE/FAR/BELOW/ABOVE campaign proving: intended-distance visibility; connected
  BASE -> CORE -> TOWER -> ANVIL coverage; no renderer-caused clean-sky waist; no march starvation;
  real cloud hits surviving depth publication/composite; no catastrophic confetti/skipping; and
  preserved basic severe scale. The historical five-fixture result (`centreColumnCloudShare=1.000`,
  `longestInnerSkyRun=0 px`, zero caps on 15 traced rays) is evidence, not a substitute for
  integration verification on `Forge-1.20.1` (depends on T133; fulfills the structural portion of
  reopened T030) in `specs/001-native-storm-rendering/validation/t098a-structural-correctness.md`
  [FR-001-FR-005, FR-021-FR-022, FR-028-FR-031; SC-001, SC-011, SC-018-SC-020]
- [ ] T098b [VISUAL POLISH] **Final Visual Polish.** At the final shipping marcher,
  reconstruction, lighting, resolution, and quality-mode configurations, replace the remaining
  US1 capture evidence and grade all FR-023/FR-024 appearance criteria. Own the current excessive
  Ultra softness/fogginess and silhouette quantisation, cumulonimbus macro morphology and ANVIL
  shape, ANVIL billowing/readability and self-shadow response, reconstruction/upscale artifacts,
  inside-cloud appearance, temporal behavior, and the authoritative SIDE/FAR/ABOVE/BELOW regrade
  for Ultra plus representative lower shipped modes in
  `specs/001-native-storm-rendering/validation/t098b-final-visual-polish.md`. This gate does not
  block performance design or T099. It runs after T152 and T160 plus the terminal Phase 4Q outcome:
  T159 when the visible-volume architecture reaches resolution recovery, or the recorded stop task
  if T153/T154/T155/T156/T157 rejects the architecture (also depends on T098a, T139, T052)
  [FR-006, FR-023-FR-024, FR-031-FR-032; SC-001-SC-002, SC-011, SC-022]
  **T160 inputs (2026-09-03, commit `7169757`).** Both prerequisites are now settled: T152 and
  T160 are complete and the Phase 4Q terminal outcome is the recorded T153 stop, not T159.
  Two obligations follow.
  (a) **Upper canopy is a profile-shape defect, not clipping.** The lever is the ANVIL
  radius-growth knee at v ~= 0.62 - the endpoint 2.10 sets how wide, the knee sets where widening
  stops. Raising maximum Y, extending the upper TOWER, changing erosion, changing the density
  remap, and changing renderer bounds were each measured and falsified as the cause, and must not
  be proposed as primary fixes without evidence overturning
  `validation/t098b-upper-anvil-envelope.md`. The relaxed diagnostic values in that arm are not
  shipping candidates and must not be promoted directly.
  (b) **A rendered A/B is required before the upper canopy may be graded correct.** T160 measured
  through final `cloudDensity` only; the ABOVE footprint is roughly a 1.5:1 ellipse (about
  468 x 312 blocks) while the in-game view appears markedly more circular, so a renderer or
  reconstruction contribution is not excluded. Compare the final `cloudDensity` footprint against
  actual rendered occupancy at ABOVE and SIDE and classify: Outcome A, they agree and morphology
  is the whole fix; or Outcome B, density stays elliptical while the rendering reads circular, in
  which case reconstruction/sampling is a second independent defect that the morphology pass alone
  will not remove. Until classified, treat the canopy as possibly two causes.
- [ ] T099 [FUNCTIONAL] **[REOPENED 2026-08-19 - revised criteria]** Replace
  `specs/001-native-storm-rendering/validation/us2-rain-whiteout-stability.md` evidence with new
  dry/local-rain/remote-rain/boundary stationary and moving captures proving rain remains attached
  to the **final noise-formed** storm density (not the coverage envelope) and whiteout remains
  stable. It needs structural correctness and final-density behavior, not final ANVIL lighting or
  reconstruction polish (depends on T098a, T115, T116, T118) [FR-021-FR-022; SC-001, SC-003-SC-004]

**Checkpoint (superseded 2026-09-01)**: Phase 4R established that the descriptor set - not a
statistical envelope or candidate grid - is the evaluated storm field. Phase 4S narrows that result:
the descriptor union is a bounded coverage envelope, and the noise field forms the visible body.
T133 is accepted. T098a is the remaining structural integration gate; T099 is blocked by T098a,
while T098b visual polish, performance, and quality-mode work run in parallel.

### Superseded by Phase 4S

These Phase 4R tasks remain complete as implementation history. Their acceptance criteria are
invalidated by the corrected density architecture, and a new Phase 4S task owns the replacement.
Do not re-open or rewrite them.

| Phase 4R task | What it established | Superseding Phase 4S task |
|---|---|---|
| T081 | Per-descriptor evaluation and smooth union on a density-space distance-like field | T108, T109, T110 |
| T085 | GLSL mirror of the Phase 4R union | T111 |
| T086 | Storm underside from contributing BASE lobes in the union | T115 |
| T089 | Rain attached to the descriptor union | T115 |
| T090 | Descriptor-carried morphology replacing raster modulation | T112, T113 (envelope inputs only; density ownership moves to noise) |
| T097 | Phase 4R automated revalidation gate | T118 |

T074-T080, T082-T084, T087-T088, and T091-T096 remain valid as written: fail-first discipline,
locality, removal of statistical envelopes and the binary weight gate, descriptor slot validity,
fallback, async signatures, history identity, precipitation argument order, and acceleration-only
candidate semantics are all unaffected by the Phase 4S correction.

---

## Phase 4S: Storm Density Architecture Correction

**Purpose**: Make descriptor geometry a bounded coverage envelope and the volumetric noise field the
visible storm body, replace density-space pseudo-distance with real world-space geometric distance
fields, and hold morphology to positive measurable criteria.

**Goal**: Satisfy FR-021 through FR-026 and SC-011 through SC-016 without changing server-authoritative
weather, forecast behavior, network packets, saved weather state, Simple Clouds ownership, legacy
renderer fallback, rain placement ownership, whiteout ownership, history invalidation semantics, or
the candidate texture's role as a scheduling/index hint.

**Independent Test**: Run the interior-noise, variance, spectral, distance-field, structural, and
rejected-form regressions with thresholds derived in `validation/morphology-thresholds.md`, then
replace the US1/US2 captures against the two-part checklist in T098.

**Authoritative contract**: `contracts/storm-density-composition.md`.

**Ordering**: Correctness tasks in this phase are not blocked by T098/T099. Phase 4P performance
tasks may proceed once their prerequisite correctness tasks land, in separate commits.

### Tests First

- [X] T100 [P] [US1] Confirm every threshold derivation in `specs/001-native-storm-rendering/validation/morphology-thresholds.md` against the constants actually present in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` after the refactor lands: erosion strength, octave weights, octave frequencies in blocks, fine-octave gate, and the measured base/detail noise standard deviations; record the octave wavelengths, minimum region edge length, core-concentration margin, and transition-discontinuity bound. No threshold may be set without a recorded derivation (depends on T097) [FR-026; SC-016]
- [X] T101 [US1] Add an interior noise-influence regression in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` proving that, with the coverage envelope held fixed, perturbing the base noise field and perturbing the detail noise field each change final density at 95% or more of sampled unsaturated interior probe points, where "interior" means coverage at least 0.75 and at least one lowest-octave wavelength inside the coverage boundary (depends on T100) [FR-021, FR-022; SC-013]
- [X] T102 [US1] Add a density-variance regression in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` asserting that every sampled occupied region spanning at least three lowest-octave wavelengths meets the derived minimum variance, counting only samples with final density in (0.05, 0.95) so saturation cannot disguise a uniform interior (depends on T100) [FR-023, FR-024; SC-012]
- [X] T103 [US1] Add a multi-scale spectral regression in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` measuring band-limited variation of final density at each configured octave frequency and asserting each band meets at least half its nominal weight-squared share (depends on T100) [FR-023; SC-014]
- [X] T104 [US1] Add a geometric distance-field regression in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` asserting each lobe's field is finite, monotonic with world-space distance, correctly signed or consistently scaled at points inside, on, and outside its surface, that blend radii are world-space, and that no union result changes when a contributing lobe's local density is zero versus nonzero (depends on T100) [FR-025; SC-015]
- [X] T105 [US1] Add a positive structural morphology regression in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` measuring broad continuous lower base extent and connectivity, core concentration above the body mean, tower cross-section narrower than the base and vertically connected to it, progressive narrowing between base and anvil root, anvil cross-section wider than the tower, transition continuity along vertical transects, and silhouette curvature variance within its documented band (depends on T100) [FR-023; SC-011]
- [X] T106 [US1] Add a rejected-form regression in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` failing on balloon-smooth silhouette arcs, connected occupied regions below the minimum variance, sections that fit a single analytic ellipse within the derived noise residual, isolated protrusions or components, density discontinuities co-located with lobe boundaries, planar silhouette segments and horizontal underside planes exceeding the documented length (depends on T100) [FR-024; SC-011]
- [X] T107 Run T101-T106 against the current Phase 4R implementation before any Phase 4S production change, record every expected failure and its defect mapping in `specs/001-native-storm-rendering/validation/phase4s-fail-first.md`, and block implementation for any regression that does not fail meaningfully. The interior-noise regression is expected to fail at exactly zero response, because `edgeExposure` reaches zero above `cloud = 0.72` (depends on T101-T106)

### Distance Field and Coverage Envelope

- [X] T108 [US1] Replace density-space pseudo-distance with a real world-space geometric distance field per lobe in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeEvaluator.java`: remove `double lobeDistance = 1.0D - lobeDensity;` and the `if (lobeDensity <= 0.0D) { continue; }` skips from both `unionDensityAt()` overloads, and derive the field from each lobe's oriented, sheared, vertically profiled analytic volume so it stays valid outside the lobe surface (depends on T107) [FR-025; SC-015]
- [X] T109 [US1] Express both smooth-union levels in world-space blocks in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeEvaluator.java`: convert `smoothMinimum()`, `lobeBlendRadius()`, and `groupBlendRadius()` to operate on the T108 distance field with blend radii derived from the smaller participating lobe's world-space radius, and remove the density-space `supportFade` term (depends on T108) [FR-025]
- [X] T110 [US1] Convert the union result to a bounded coverage envelope in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeEvaluator.java` and expose it as the envelope stage rather than as density: `densityFromDistance()` becomes an envelope mapping, and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/ClientCloudVisualDensity.java` consumes final density rather than the envelope (depends on T109) [FR-021; SC-013]
- [X] T111 [US1] Mirror T108-T110 independently in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh`: remove `float lobeDistance = 1.0 - lobe.x;` and the `if (lobe.x <= 0.0) { continue; }` skip from `directStormGroupField()`, convert `stormSmoothMinimum()`, `stormLobeBlendRadius()`, `stormGroupBlendRadius()`, and `stormDensityFromDistance()` to the world-space distance and envelope contract, and keep `directStormShape()` returning a coverage envelope (depends on T108-T110) [FR-021, FR-025]

### Noise-Formed Body

- [X] T112 [US1] Remap the base volumetric noise field against the storm coverage envelope in `cloudDensity()` in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` so the visible storm body inside the envelope is formed by noise; keep the remap monotonic in both coverage and base noise, and stop using `directStormShape()` output as a final density value (depends on T111) [FR-021, FR-022; SC-013]
- [X] T113 [US1] Apply multi-scale detail erosion across the whole storm body in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` and remove the storm-specific interior exemption: for descriptor-owned storms, drop `edgeExposure = 1.0 - smoothstep(0.26, 0.72, cloud)` and the `erosionFloor = stormProfile ? 0.42 : 0.68` clamp so every configured octave reaches the interior. Non-storm profiles keep their existing behavior (depends on T112) [FR-006, FR-022; SC-012, SC-014]
- [X] T114 [US2] Add a deterministic CPU mirror of the base-noise and detail-erosion stages alongside `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeEvaluator.java` so T101-T106 and the independent GLSL parity fixture can evaluate final density without a GPU, and extend the parity fixture in `src/test/resources/net/Gabou/projectatmosphere/clouds/client/render/volumetric/storm_lobe_equations.glsl` to cover the envelope, remap, and erosion stages (depends on T111-T113) [FR-008; SC-003, SC-013]

### Downstream Consumers

- [X] T115 [US2] Re-derive the visible storm underside, volumetric precipitation support, and attachment height from **final** storm density rather than from the coverage envelope in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh`, `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricPrecipitationModel.java`, and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeEvaluator.java`, preserving the existing rain placement ownership and `CustomPrecipitationRenderer` boundary (depends on T113, T114) [FR-007, FR-008; SC-001, SC-003]
- [X] T116 [US2] Publish camera density and whiteout from final storm density in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/ClientCloudVisualDensity.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/CameraCloudDensityTracker.java`, preserving the existing whiteout behavior and interfaces (depends on T113, T114) [FR-008; SC-003]
- [X] T117 [US1] Re-verify descriptor-ownership fallback, `familyMacroShape`/broad-map LOD cross-fade, and the analytic-to-map transition band against envelope semantics in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java`, so the cross-fade blends comparable quantities and incomplete or omitted groups still fall back rather than disappear (depends on T111-T113) [FR-001, FR-005, FR-014]

### Revalidation Gate

- [X] T118 Run the Phase 4S regressions (T101-T106), the retained Phase 4R regressions, the independent GLSL parity fixture, and the US1/US2 sandboxes; record passing results and the measured proxy values in `specs/001-native-storm-rendering/validation/phase4s-automated.md`; verify each T107 expected failure is closed without weakening any assertion or retuning any threshold outside its recorded derivation (depends on T108-T117) [FR-021-FR-026; SC-010-SC-016]
- [X] T124 [US1] Add and run a deterministic macro-coherence validation in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormMorphologySandbox.java` against the measured live ten-descriptor composition (BASE 0.7832/0.8792, CORE 0.9485/1.0000, TOWER 0.9700/0.9539, ANVIL 0.8222/0.7231/0.7851/0.7992). Before accepting a morphology correction, record a fail-first result against the live-calibrated current composition. Require a connected substantial lower base; coherent base → core → tower → anvil hierarchy; bounded substantial protrusions; no long radial/finger-like macro structures; high-frequency detail subordinate to the macro silhouette; and low-frequency dominance at macro scale. The highest-frequency detail band removal must not materially move the macro silhouette (depends on T100, T118) [FR-021-FR-024, FR-026; SC-011-SC-016]
- [X] T125 [US1] Add and run a deterministic live-calibrated role-envelope transition validation in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormMorphologySandbox.java`: require substantial CORE→TOWER overlap and connected cross-sections, TOWER→ANVIL continuity, a broad upper canopy relative to tower width, sufficient upper lateral coverage across the storm height, and no narrow role-transition neck. Record a fail-first result before making the smallest role-geometry/envelope correction; preserve the T124 base-noise scale, proportional warp, erosion hierarchy, and live descriptor strengths (depends on T124) [FR-021-FR-024, FR-026; SC-011-SC-016]
- [X] T126 [US1] Add and run deterministic structural-continuity validation for the live `3c039aa7` ten-member strength fixture in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormMorphologySandbox.java`: measure progressive vertical occupied-envelope area/radius and its derivative, a substantial vertical CORE→TOWER overlap interval, TOWER→ANVIL continuity before tower support ends, geometry-derived upper-canopy span, low-frequency BASE underside variation in large highly occupied regions, and directional envelope-versus-low-frequency anisotropy. Record fail-first evidence before the smallest role-geometry correction, preserving Phase 4S noise, warp, erosion, strengths, and downstream final-density consumers (depends on T125) [FR-021-FR-024, FR-026; SC-011-SC-016]

+## Phase 4A: Renderer-wide Severe-Storm Correction Gate

**Purpose**: Replace incremental role tuning with a physical-system scale derivation, a complete
vertical material trace, measured cause attribution, and visually-neutral performance architecture.

**Gate**: Do not modify storm role geometry, density/noise composition, or lighting to address the
BASE/CORE versus TOWER/ANVIL split until T129 records the first discontinuous stage. Preserve the
Phase 4S density architecture and every retained ownership/fallback invariant.

- [X] T127 [US1] Audit and derive the severe-storm physical-scale target in `specs/001-native-storm-rendering/validation/renderer-wide-architecture-audit.md`: record BASE footprint, CORE width, TOWER width/height, ANVIL span/thickness, total height, aspect ratio, descriptor count per occupied volume, and horizon dominance from three several-hundred-block viewpoints. Trace the controlling source-plan, lobe-spec, render-scale/aspect, placement, and union inputs; then evaluate the 50/25/12.5-block base bands and approximately 22.7-to-1.4-block detail bands against the derived system dimensions. Do not accept uniform descriptor scaling as a target derivation (depends on T126) [FR-028; SC-018]
- [X] T128 [US4] Add fail-first deterministic and on-demand runtime vertical material-continuity diagnostics in `src/main/java/net/Gabou/projectatmosphere/command/TelemetryDebugClientCommand.java` for the existing live-calibrated ten-descriptor fixture and live `3c039aa7` strengths. Sample a fixed centre X/Z at no more than 16-block Y intervals and report active descriptor roles/IDs, coverage/strength, base noise/carrier, detail erosion, final density, extinction, light optical depth, direct light, ambient light, final rendered contribution, and direct/fallback plus weather/slab height-normalization branch flags. Keep CPU/shader values independently comparable in `specs/001-native-storm-rendering/validation/t128-t131-material-continuity.md` (depends on T126) [FR-029; SC-019]
- [X] T129 [US1] Run T128 against the current composition before a correction and record fail-first evidence in `specs/001-native-storm-rendering/validation/t128-t131-material-continuity.md` identifying the first lower/upper discontinuity as geometry/coverage, density/noise, optical medium, lighting, or sampling/history. Rule out every earlier stage before authorizing a correction; do not substitute another role-overlap or union-radius iteration for measured attribution (depends on T127-T128) [FR-029; SC-019]
- [X] T130 [US3] Capture the reference performance architecture baseline in `specs/001-native-storm-rendering/validation/performance-baseline.md`: raymarch time, primary and lighting-cone density samples, group-range scans, descriptor fetches, envelope rejections, empty-space skips, termination behavior, and register/scratch-risk locations. Freeze comparison captures and a material-trace/image tolerance for visually-neutral optimization; classify every proposed optimization as neutral or quality-changing (depends on T126) [FR-030; SC-020]
- [X] T131 [US1] Add a deterministic fail-first regression for the measured cause from T129, then correct only that single-medium discontinuity in the responsible renderer stage recorded in `specs/001-native-storm-rendering/validation/t128-t131-material-continuity.md`. Role geometry may change only if T129 attributes the first discontinuity to geometry/coverage; preserve Phase 4S base scale, warp, erosion hierarchy, live strengths, final-density rain/whiteout, parity, and all ownership/fallback behavior (depends on T129) [FR-029; SC-019]
- [X] T134 [US1] Implement the separately derived severe-system physical scale from T127 through the source plan, role-specific lobe placement/extents, and group distribution. Reach the 1,200–1,500 footprint and 720–880 height targets without a uniform descriptor multiplier; retain the 50/25/12.5-block base and ~22.7-to-1.4-block detail wavelengths unless remeasurement proves a change is required. Record controlled SIDE/FAR/BELOW/ABOVE scale evidence in `specs/001-native-storm-rendering/validation/renderer-wide-architecture-audit.md` before T133 (depends on T127, T129; separate from T131) [FR-028; SC-018]

**T134 accepted 2026-08-21**: the source-plan, role-envelope, and group-placement implementation
plus the seeded resolved-centre scale guard are complete, and the required controlled four-view live
evidence was collected on a freshly spawned severe system rather than the earlier compact-cloud
fixture. Accepted fixture `66a15248-6262-441d-bc42-60e2d4e6b4e5`, structural fingerprint
`16536fe1abb39ea0`, `descriptors=10`, `height=865.31018`, `footprintDiameter=1238.61042`, compact
topology with `0` group-boundary scans and `3` metadata reads per group evaluation. SIDE, FAR,
BELOW, and ABOVE PASS A/B controls matched and `structuralChanged=false` throughout. Topology
generation numbers changed during acquisition; that is permitted because the structural fingerprint
was identical at every capture and completion (see
`StormPerformanceBaseline.StructuralFingerprint`, which deliberately excludes request generation).
Deterministic backing: `cloudMorphologyTopologySandbox` reports
`T134_SCALE_CONTRACT|members=10|planRadius=450.0|groupRadius=400.0|baseDrop=120.0|topRise=780.0`
and `T134_RESOLVED_CENTRE_ENVELOPE|matureLowerMin=1265.701|matureUpperMax=1416.327`.

**Deferred to T133, not to T134**: SC-018 additionally requires the recorded footprint and
silhouette at T127's three reference viewing distances (600, 900, and 1,200 blocks). The suite's
fixture-relative SIDE/FAR/BELOW/ABOVE poses satisfy T134's own acceptance text but do not satisfy
that clause. T133 owns it. The system aspect ratio (`865.31018 / 1238.61042 = 0.699`) sits at the
upper edge of T127's 0.55-0.70 band, and neither the aspect ratio nor the ANVIL horizontal span is
asserted by `CloudMorphologyTopologySandbox.validateStormPhysicalScale()`; T133 owns closing those
guards as well.
- [X] T132 [US3] **[ACCEPTED 2026-08-27]** **[REBASED 2026-08-21 - post-T134 reference]** Revalidate each approved foundational performance change against a **fresh post-T134 controlled reference** and a fresh post-T134 material trace, not against T130's pre-T134 frozen captures: record each change's owned work reduction/bound and reject any image or material-trace movement outside the neutral tolerance. A lighting-support proxy remains blocked unless it meets the same equivalence evidence (depends on T119, T121-T123, T130, T134) [FR-030; SC-020]

**T132 revised acceptance criteria (2026-08-21)**: T134 changed every severe system's physical
dimensions, so the T130 reference fixture `ce4ffed5-14f1-4b78-bec7-059c1985cedb` (fingerprint
`b018367ca17bc7d8`) and the T121-T123 fixture `66b2c85a-aa93-4d18-b428-ac546e280c02` (fingerprint
`459873e8d8c8425a`) can no longer be reproduced. Their frozen visual references
(`bd3e82e9315cb38c`, `4afdd85d03ea68a3`, `be40dffe53e91e97`, `d4ea81479379a6db`) describe
compact-scale geometry; comparing a post-T134 render against them would report T134's intended
effect as a performance regression. They are retained as historical record only and are not a
T132 comparison basis.

T132 is accepted only when all of the following hold on **one** post-T134 severe fixture:

1. **Fresh post-T134 baseline.** One frozen fixture established after T134, resolved from a
   severe system carrying the T134 scale contract: ten descriptors, `720-880` height, and a
   `1,200-1,500` resolved-centre footprint diameter as reported by the suite's `scaleEnvelope`.
   The compact-scale fixtures above must not be reused.
2. **One identity for every comparison pass.** Identical group UUID and identical structural
   fingerprint at capture and at completion for every pass, with `structuralChanged=false`
   throughout. Topology-generation changes alone do not invalidate a capture, because
   `StormPerformanceBaseline.StructuralFingerprint` deliberately excludes request generation,
   candidate-grid origin, upload generation, material advection, history, and frame state.
3. **Identical authoritative adjacent controls in every accepted comparison.**
   **[REBASED 2026-08-27 - separated-pass control retired]** The authoritative controls are those
   of the adjacent repeated-sampling protocol, which is the protocol that supplies every piece of
   T132 evidence. They are reported as `authoritativeAdjacentControls={...}` and are the sole input
   to the T132 acceptance verdict.

   For **every** accepted comparison - both the A/A local-noise control and any optimization A/B -
   all of the following must hold:

   - same fixture UUID;
   - same accepted `StructuralFingerprint`, with `structuralChanged=false`;
   - the same exact SIDE, FAR, BELOW or ABOVE pose, held within one adjacent settled window;
   - `governorScale` exactly `0.50000`; `resolutionScale` exactly `0.75000`;
   - compact storm topology wherever compact is the required production arm;
   - identical configured primary ray steps and light steps;
   - identical target and workload dimensions;
   - projection settled (`projectionStability` stabilized);
   - effective `WorldTime` matched across the compared arms;
   - fixture daylight frozen, so `lightDirection` is controlled rather than drifting;
   - per-descriptor runtime profile matched;
   - material advection offset matched;
   - the required cloud-content signatures matched;
   - the content and projection stability gates passed for every sample;
   - fresh capture and sample identities, with no stale sample or workload reuse;
   - optimization arm identity **observed from the draw snapshot** wherever an A/B is being made,
     so `armsDistinct` proves the toggle took effect rather than recording what the suite intended;
   - diagnostic toggles restored afterwards (`topologyRestored=true`).

   Any difference in these authoritative adjacent controls is a failed capture, not a measurement.

   The retired separated-pass comparison is **not** one of these controls; see the note below.
4. **Owned-work evidence per change.** For each of T119, T121, T122, and T123, the counter it owns
   is recorded and its reduction or bound stated: group-boundary scans and metadata reads per group
   evaluation (T119), `conservativeDescriptorRejects` (T121), `avoidedDescriptorTextureFetches`
   (T122), and the primary/light/empty-space/termination counters (T123).
5. **No image or material movement.** Evaluated by the deterministic numeric comparator, **not**
   by `visualRef` equality. `visualRef` digests a FINAL frame accumulated at history blend `0.85`,
   so two passes disagree by construction on an unchanged fixture at an identical pose; it is
   retained as an informational fingerprint only. Each view instead captures a reference frame with
   temporal history bypassed - which also pins the shader jitter phase, because
   `jitterFrame` in `cloud_atmosphere_volume.fsh` is `FrameIndex` only while
   `HistoryValid == 1 && HistoryBlend > 0.001` - and compares the raw `RGBA16F` cloud buffers.
   The suite must report `maxAbsRGBA`, `meanAbsRGBA`, `rmsRGBA`,
   `changedPixelCountAboveEpsilon`, `totalComparedPixels`, `epsilon`, and `passed` for every view,
   with `epsilon` equal to one binary16 storage step at the compared magnitude
   (`2^(exponent-10)`, `2^-24` below the normal range). The fresh post-T134 material trace must
   likewise stay within the documented neutral tolerance. A lighting-support proxy stays blocked
   until it proves equivalence by this same evidence.

   **Attribution is required before a failure counts against the performance path.** Both suite
   passes run the same binary, the same shader, and the same performance paths, so the suite is
   structurally an A/A repeatability test, not an A/B optimization test. The structural fingerprint
   deliberately excludes advection state and the per-tick descriptor runtime profile, so
   `structuralChanged=false` means the topology is frozen, not that the storm is. Each view
   therefore also reports `sceneStability={...}` (`materialOffsetMatch`, `materialOffsetDeltaX/Z`,
   `runtimeProfileMatch`, `runtimeProfileDigestA/B`, `changedDescriptorCount`, and the maximum
   per-field deltas for major/minor radius, aspect, shear, density, detail weight, lifecycle, and
   vertical development) and `criterion5={...}`:

   - `sceneStable=true` with a passing comparison satisfies criterion 5;
   - a failing comparison is escalated through three levels, because a partial input set matching
     never proves the renderer moved the image:
     **A** a tracked scene input differs -> `criterion5Attributable=false
     reason=scene_evolved_between_passes`; **B** the scene held still but a named production uniform
     group or the weather-map input signature differs -> `criterion5Attributable=false
     reason=render_inputs_differ_between_passes` naming the differing groups; **C** every tracked
     scene input, every named `UniformComponentSignatures` group and the weather-map input signature
     match and the image still differs -> `criterion5Attributable=true
     reason=unexplained_deterministic_render_difference`, which **remains blocking**. Level C means
     the known deterministic inputs are exhausted and a deeper render-path investigation is due; it
     is not a proven renderer defect and not proven GPU nondeterminism;
   - `sceneStable=false` with a failing comparison is recorded as
     `criterion5Attributable=false reason=scene_evolved_between_passes differingInputs=...` and must
     **not** be claimed as evidence that the performance path moved the image.

   `sceneStable` covers four evolving inputs: the material advection offset, the per-descriptor
   runtime profile, `LightDir`, and `WorldTime`. `WorldTime` is **conditionally** render-relevant:
   the renderer computes
   `worldTimeAffectsDensity = weather.maxPrecipitation() > 0.02F || funnels > 0`, and the shader
   consumes `WorldTime` in the precipitation shaft domain
   (`p.y * 0.0014 - WorldTime * 0.0015`, `cloud_atmosphere_volume.fsh:2508`) and in the funnel
   terms. A differing clock destabilizes the comparison only when the renderer marked it relevant
   for at least one pass; when it is irrelevant it cannot move the image and must not be reported
   as instability.
6. **Fresh post-T134 material trace.** The T128 centre-line trace is re-taken on this same fixture,
   spanning the full post-T134 severe column of roughly 865 blocks (from below the BASE underside
   to above the ANVIL canopy) at no more than 16-block intervals, and it shows no new discontinuity
   beyond the T131-corrected composition. The retained
   `StormMaterialContinuityDiagnosticsSandbox` fixture is pre-T134 compact geometry (BASE radius
   `172`, ANVIL radius `206`, span Y `224..508`) and is therefore not sufficient evidence for this
   item on its own.

**Retired: `existingSeparatedPassComparison` is historical, non-authoritative evidence.**
The original protocol separated PASS A and PASS B by multiple teleports and substantial live game
time. That separation was empirically shown to admit `WorldTime` drift, projection/FOV drift,
weather-map and cloud-content change, and `lightDirection` drift - none of which are attributable to
any optimization under test. Back-to-back adjacent capture removed those systematic differences, and
the repeated-median adjacent protocol then reached zero median failures across the campaigns recorded
in `validation/t128-t131-material-continuity.md`.

`existingSeparatedPassComparison` is therefore retained in the suite output as **historical
diagnostic record only**. It is reported as `historicalSeparatedPassComparison={...}`, it is
explicitly non-authoritative, and it is **not** used for T132 acceptance. It is known to be invalid
as a neutrality or repeatability control because of that temporal separation. In particular its
`workload_capture_token_reused` difference - an artifact of `PASSES = 1`, where pass B reuses pass
A's workload capture token - its render drift, and its control differences must not feed the
authoritative criterion 3 verdict. `PASSES = 2` must not be restored merely to satisfy a retired
protocol.

This is a **protocol correction, not a relaxed control requirement**: criterion 3 above demands
strictly more of each accepted comparison than the retired wording did, including gates
(projection settling, content stability, clock pinning, daylight control, runtime-profile and
advection matching, arm-identity observation) that did not exist when the original wording was
written.

**What criterion 5 does and does not prove.** Both suite passes run the same binary, the same
shader and the same performance paths, so the suite is an **A/A repeatability test**. Passing
criterion 5 proves the deterministic capture can reproduce the same scene under the same
implementation. It does **not** by itself prove that T119, T121, T122 or T123 are image-neutral,
because neither pass has the optimization disabled. **The A/A control remains an A/A test and is
never evidence of optimization neutrality**; it measures only whether the capture can reproduce a
scene under one fixed implementation.

T119 is the one change that also has a real A/B, via
`/pa system volumetric debug stormTopology legacy_scan|compact`, and it has now been run as one:
see "T119 banked" in `validation/t128-t131-material-continuity.md`. **T121 and T122 still have no
equivalent A/B toggle**, so their neutrality remains unproven and only their execution is evidenced
by their owned counters. This limitation must not be removed or softened in T132 or T133
documentation; closing it needs a separate decision about adding an OFF branch to
`cloud_atmosphere_volume.fsh`, which is a production shader change and is not authorised here.

Reference frames additionally wait for the `projection` component signature to repeat for three
consecutive rendered frames before being accepted, because Minecraft's FOV interpolates after the
suite's teleport and a changing projection reprojects the entire image. The wait is bounded at 240
frames and reports `projection_stability_timeout` rather than capturing an unsettled frame.

Step-count reductions, reduced noise or lighting, resolution changes, and any accepted image change
remain quality work behind T098/T099 and are out of T132's scope.
**T121/T122 debt CLOSED 2026-08-27.** Diagnostic shader OFF arms were built and validated on
five fixtures. **T122 is image-neutral** (40/40 medians, 0 changed pixels, 37.4% fetch
reduction). **T121's failure was isolated and fixed**: the guard could reject a lobe that still
satisfied `lobeDistance <= lobeSoftness`, flipping a discrete `groupActiveRoleMask` bit
(15,546 witnesses found offline). A derived `STORM_T121_SOFTNESS_MARGIN_BLOCKS = 2^-10` margin
on the softness term closes it; the blend term was measured safe and left unmargined. Re-run:
**40/40 T121, 40/40 T122, 40/40 A/A, zero failures**, with ~55% descriptor-evaluation reduction
preserved. **SC-020 and FR-030 are satisfied.**

**Superseded - T121/T122 debt resolved 2026-08-27, with one failure.** Diagnostic shader OFF arms were built
and validated through the accepted adjacent repeated-median protocol on five fixtures.
**T122 is image-neutral** (40/40 medians, 0 changed pixels) and removes 37.4% of descriptor
texture fetches. **T121 is NOT image-neutral**: one of twenty distinct fixture x view
comparisons produced a deterministic 1-pixel difference at 11x the tolerance
(`maxAbsRGBA=5.371094e-03` against `epsilon=4.882813e-04`), with both arms at `dev=0` and the
A/A control clean. The vertical bound itself is provably conservative (29.3M-probe search,
worst violation 1.4e-14 blocks), so this is not a logic error; no production behaviour was
changed. **SC-020 and FR-030 therefore remain unsatisfied for T121, and T098 stays blocked.**
See `validation/renderer-wide-architecture-audit.md`.

**Superseded - original validation debt entering T133 (recorded 2026-08-27 at T132 closure)**: T121 and T122 are
**execution-evidenced only**. Their execution is proven by nonzero `conservativeDescriptorRejects`
and `avoidedDescriptorTextureFetches` on all four views, but **neither has ever been run with an OFF
arm**, so neither one's image neutrality has been independently demonstrated. Unlike T119, which
toggles through the `StormTopologyMode` uniform, both are shader-internal: building true OFF arms
requires adding pre-optimization diagnostic branches to `cloud_atmosphere_volume.fsh`, which is a
production shader change and was outside the diagnostic-only scope under which T132 was closed.
T119's banked neutrality must **not** be read as covering T121 or T122. T133 must either carry this
limitation forward explicitly or resolve it with a deliberate decision to add those shader toggles.

**T133 status 2026-08-27: CLOSED, ACCEPTED.** All six evidence areas pass, including SC-020's
optimization-neutrality clause for T119, T121, T122 and T123. **T098 is unblocked.** SC-006 /
T070 performance remains failed and open and is not closed by this. See
`validation/renderer-wide-architecture-audit.md`.

**Superseded - T133 status 2026-08-27: OPEN, one blocker.** Aspect-ratio and ANVIL-span guards implemented
and passing; SC-018 passes at 600/900/1,200 blocks; SC-019 and SC-020's recording clause pass;
no accepted T132/T134 evidence regressed. **SC-020's optimization-neutrality clause is
unsatisfied for T121 and T122**, which T133 cites and which is a written T098 entry condition.
Closing it requires pre-optimization OFF branches in `cloud_atmosphere_volume.fsh` - a
production shader change. Severe-scale storm raymarch measures 136-261 ms at `641x360` with
`governorScale=0.50000` / `resolutionScale=0.75000`; SC-006's 16.7 ms Ultra contract is owned by
T070, not T133. See `validation/renderer-wide-architecture-audit.md`.

- [X] T133 [US1] **[ACCEPTED 2026-08-27]** Revalidate physical scale, one-medium continuity, T124-T126 macro morphology, CPU/GPU noise parity, final-density rain/whiteout, retained Phase 4R invariants, production shader compilation, and T130-T132 performance evidence together. Record the result in `specs/001-native-storm-rendering/validation/renderer-wide-architecture-audit.md`; only a passing result allows the then-monolithic visual acceptance gate to resume (depends on T127-T132, T134) [FR-028-FR-031; SC-018-SC-020]

**Historical checkpoint, superseded by the accepted T132/T133/T098a state**: A severe storm has an explicit physical-system target **and an accepted
implementation of it (T134)**, the lower/upper material split has an attributed cause, and
performance work has evidence of visual neutrality before visual acceptance resumes. At that
checkpoint T132 was the remaining prerequisite for T133; both have since been accepted.

---


**Then**: T098a and T099 execute only after T133; T098b follows the settled shipping performance and
quality configuration. They are listed under Phase 4R's Revalidation Gate to keep their audit history in place; their acceptance criteria are the revised positive/negative checklist recorded there.

**Checkpoint**: The coverage envelope comes from descriptors, the visible body comes from noise, and
morphology is measured positively. T118/T124-T126 are retained evidence; T133, not T118, unblocks
the reopened T098a, while T099 remains blocked by T098a; final appearance grading remains T098b.

---

## Phase 4P: Storm Performance Architecture

**Purpose**: Keep the corrected density model practical at the supported quality modes.

**Goal**: Satisfy FR-027 and SC-017 through structural changes to descriptor evaluation cost.

**Independent Test**: Measure storm descriptor evaluation cost per sample and per frame through the
existing storm diagnostics before and after each task, and confirm the rendered result is unchanged.

**Ordering**: These tasks are **not** blocked by T098/T099, but a frozen timing, comparison-image,
and material-trace baseline must exist first. T130 froze that baseline for T119 and T121-T123 on
pre-T134 compact fixtures. Since T134 is accepted, T132 re-establishes an equivalent frozen baseline
on a post-T134 severe fixture; see the revised T132 criteria above. Each is a separate task and a separate commit
from visual-correctness work. No task in this phase may alter the rendered result; a performance
change that moves the image or trace outside the documented neutral tolerance is a correctness
defect. A lighting proxy is permitted before T098 only when it proves equivalent by that evidence.

- [X] T119 [US3] Precompute descriptor group topology during the existing CPU build in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeSpatialIndex.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormGeometryBuildCoordinator.java`, and supply per-group first/end indices or equivalent compact metadata to the shader, replacing the per-sample `stormGroupFirstIndex()` / `stormGroupEndIndex()` descriptor scans in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh`. The metadata is acceleration only and never defines density (depends on T130) [FR-027, FR-030; SC-017, SC-020]
- [X] T120 [US3] Replace `bool groupVisited[MAX_STORM_GROUPS]` with a compact integer bit mask or another GPU-friendly representation in `directStormShape()` and `directStormSegmentMayIntersect()` in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh`, removing the per-call array allocation and clear loop (depends on T111) [FR-027; SC-017]
- [X] T121 [US3] Add only conservative descriptor and empty-space rejection in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` where a mathematically derived lower bound proves a lobe cannot affect coverage, final density, active roles, or local-height output. Retain full `cloudDensity()` for lighting taps; any lighting-support proxy remains blocked until frozen-image and material-trace equivalence is proven. Record the proof and per-view workload comparison in `contracts/storm-density-composition.md` (depends on T119, T130) [FR-006, FR-027, FR-030; SC-011, SC-017, SC-020]
- [X] T122 [US3] Audit repeated descriptor texture fetches inside `cloudDensity()` and `lightMarchOpticalDepth()` in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh`, hoist or reuse fetches re-issued for the same descriptor within one evaluation, and record the resulting fetch count in the storm diagnostics (depends on T119, T130) [FR-027, FR-030; SC-017, SC-020]
- [X] T123 [US3] Define and enforce a bounded per-sample and per-frame descriptor evaluation cost, report it through `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudFrameDiagnostics.java` and the storm diagnostic capture, and document the bound in `plan.md` (depends on T119, T122, T130; incorporate T121 only if its equivalent path is implemented) [FR-027, FR-030; SC-017, SC-020]

**Status 2026-08-27 (T119 image-neutrality banked)**: T119 now has live OFF/ON evidence, not
just execution evidence. Forty median comparisons across five independent fixtures and all four
views passed at zero changed pixels, with `armA=legacy_scan armB=compact armsDistinct=true`
observed from the draw snapshot on every group and forty same-campaign A/A controls clean. See
"T119 banked" in `validation/t128-t131-material-continuity.md`. **T121 and T122 remain
execution-evidenced only**; they have no A/B toggle and adding one is a production shader change.

**Status 2026-08-21**: T119-T123 are accepted. The two-pass compact `stormPerformanceSuite`
fixture `66b2c85a-aa93-4d18-b428-ac546e280c02` (fingerprint `459873e8d8c8425a`) kept group,
structural fingerprint, poses, governor scale, resolution, topology, target, and configured controls
equal. That fixture is pre-T134 compact geometry; the acceptance stands because none of the four
arguments depends on descriptor scale (T121's lower bound holds for any radius since
`stormLobeBlendRadius()` clamps to `STORM_MAX_BLEND_BLOCKS`, T122 is exact same-register reuse,
T119 follows from group-contiguous build ordering plus `STABLE_IDENTITY_ORDER`, and T123 is gated to
`DebugView == 22 || DebugView == 23`), but T132's visual/material neutrality evidence must be
re-collected post-T134. T121 conservative rejections, T122 exact avoided descriptor texture
fetches, and T123 primary/light/termination counters executed in every controlled view. This is
runtime execution and equivalence evidence, not a historical pre/post timing percentage. An
image-changing approximation remains blocked behind T098. None of these tasks may replace the
required material-continuity correction.

**Checkpoint**: Descriptor evaluation cost is bounded and reported, with the rendered result
unchanged from the Phase 4S baseline.

---

## Phase 4Q: Adaptive Visible-Volume / Occupancy Traversal

**Classification**: PERFORMANCE
**Purpose**: Close the measured severe-storm performance risk in parallel with T098b by eliminating
large empty or optically irrelevant ray spans coherently. After T149, PLAY_VIS_NEAR is 103.9 ms at
the shipped 0.25 Ultra scale against an 8 ms cloud budget (13.0x), while NEAR_EDGE is 198.4 ms
(24.8x). SC-006 remains unrescoped.

**Architectural finding**: Approximately 83--100% of primary march steps at representative poses
resolve empty while still paying storm/safe-advance/descriptor work. T141, T151, and T149 show that
selectively reducing individual samples or lanes often fails to return proportional GPU time.
Prefer warp-coherent elimination of large neighboring spans. Do not reopen descriptor
micro-optimization, T143/T144, interleaving, or graded lighting/detail LOD without new evidence.
Do not lower Ultra below 0.25 as the primary solution; the objective is cheaper traversal, higher
internal resolution, and full volumetric interiors.

**Independent Test**: First establish a production-density oracle ceiling over all seven canonical
poses. Only a >=2x combined oracle unlocks production prototypes. Every prototype must preserve
T098a, allow occupied -> empty -> occupied re-entry, retain real camera-inside structure, and pass
T152 before production adoption. Image-changing work carries an explicit T098b regrade obligation;
do not fabricate historical before/after percentages for T119--T123.

- [X] T135 [PERFORMANCE] Establish and record the five-mode performance budget contract in
  `validation/performance-budget.md`: Low, Low 24, Medium, High, and Ultra must each have a cloud
  GPU budget, a total-frame budget, measured non-cloud remainder, fixture/resolution/hardware, and
  percentile. Start from the existing 3.0/4.0/5.0/6.5/8.0 ms cloud targets, validate or revise them
  by measurement, and retain Ultra SC-006 p95 total-frame <=16.7 ms at 1920x1080 unchanged
  (depends on T133) [FR-010-FR-012, FR-027, FR-030; SC-006-SC-007, SC-017, SC-021]
- [X] T136 [PERFORMANCE] Create the controlled same-fixture profiling baseline for severe
  SIDE/FAR/BELOW/ABOVE and useful clear-weather context. Capture GPU ms, ray iterations,
  `cloudDensity` evaluations, descriptor evaluations/fetches, lighting/shadow, reconstruction,
  history, resolution, and owned T119/T121/T122/T123 counters in
  `validation/performance-baseline.md` (depends on T135) [FR-012-FR-013, FR-027; SC-006, SC-017, SC-021]
- [X] T137 [PERFORMANCE] Produce a ranked performance architecture decision from T136 in
  `validation/performance-architecture.md`. Evaluate descriptor representation/cache/layout,
  fetch bandwidth, raymarch/adaptive stepping, shadow/light proxies, internal resolution/temporal
  reconstruction, quality-specific LOD, bounded simplification, and distance policy; select only
  contributors supported by the measured data and state whether T098b regrade is required
  (depends on T136) [FR-010-FR-012, FR-027, FR-030; SC-006, SC-017, SC-021]
- [X] T138 [PERFORMANCE] Implement one bounded, profile-selected major performance increment from
  T137 in the exact production target selected in
  `validation/performance-architecture.md`—one of
  `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeSpatialIndex.java`,
  `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderer.java`,
  `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java`,
  or `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh`—with
  its own correctness/release tests and before/after same-fixture profile in
  `validation/performance-baseline.md`. Do not create speculative microtasks before T137. A
  deliberately image-changing change is allowed, but must preserve structural correctness and be
  queued for T098b rather than claimed neutral (depends on T098a, T137) [FR-001, FR-006,
  FR-010-FR-012, FR-027, FR-030; SC-006, SC-017]
  **Discharged by measurement, with no production change.** Rank 2 was measured
  and rejected in `validation/performance-descriptor-cost.md` (1.02-1.10x, not
  1.5-3x). Rank 1 was then measured across five internal resolutions and eight
  poses in `validation/performance-internal-resolution.md`. Two results decide
  the task. (a) The `PLAY_NEAR`, `PLAY_MID` and `PLAY_HIGH` poses place the
  camera 4x, 7x and 5x the storm radius away; at T134 severe scale that is
  outside the 2000-block `cloudRenderDistance`, so every "representative
  gameplay" figure in T135/T136/T137 measures an empty sky. With the storm
  actually in frame at gameplay altitude the representative Ultra cost is
  492.8 ms, not 102 ms - 61.6x over budget, not 12.8x. (b) Internal resolution
  returns 4.75x from 0.75 to 0.25 with T098a green at every scale, not the
  assumed 4x-per-4x-pixels, because cost scales as pixels^0.69-0.90 rather than
  linearly. The measured cumulative path is 5.3x against ~62x required, so no
  resolution ladder was adopted: doing so would spend the whole image-quality
  budget and still miss by 13x. The composite reconstruction is measured
  sufficient for 0.25 (100% colour/depth pairing, 0.08-0.16 ms, flat) and
  fundamentally unable to add resolution back (no screen-space jitter, no
  accumulation). One candidate reconstruction change was implemented, measured
  inert (<=0.0033% of pixels) and rejected.
- [ ] T139 [PERFORMANCE] Integrate the T135 budget plus the terminal Phase 4Q evidence into five-mode quality policy: map
  budgets, LOD, raymarch, lighting, resolution, governor floors/ceilings, and history transitions
  to Low/Low 24/Medium/High/Ultra without disconnecting complete groups. Record the policy and
  transition rationale in `validation/us3-quality-lod.md` (depends on T045, T137) [FR-001,
  FR-009-FR-012; SC-005-SC-007, SC-021]. The final policy cannot bank until T158/T159 when the
  architecture reaches those stages, or the recorded T153--T157 stop decision if it does not
- [X] T141 [PERFORMANCE] Measure the per-pixel descriptor **evaluation** cost with a controlled
  arm before implementing anything against it, and record it in
  `validation/performance-descriptor-evaluation.md`. T138 measured 397 descriptor SDF evaluations
  per shaded pixel at the corrected representative pose, with 88% of march steps resolving as empty
  space and still paying 13.7 evaluations each. The rank-2 rejection measured elasticity against
  descriptor *fetches* and is silent about evaluations. Hold fixture, pose, resolution and step
  budget fixed and vary the evaluation count - the `directStormGroupField` candidate rank count, or
  the T121 conservative bound - then report the elasticity of GPU time against
  `paDescriptorEvaluations`. Also measure an empty-space early-out: the shipped `PLAY_NEAR` pose
  spends 90 ms of Ultra cloud time on a frame with no storm in it against 3.9 ms for the same empty
  sky with no descriptors resident (depends on T138) [FR-012-FR-013, FR-027; SC-006, SC-017]
  **Measured and rejected; no production change.** Recorded in
  `validation/performance-descriptor-evaluation.md`. A bit-neutral arm doubling the exact
  descriptor SDF evaluations at unchanged fetch volume (+70.4% evaluations, +0.01% fetches) costs
  +11.4% at the corrected representative pose: evaluation elasticity is 0.15-0.31, so free
  descriptor evaluation would buy ~1.19x. The `t121_off` arm independently agrees at 0.19. A
  strictly tighter conservative bound - horizontal and vertical instead of vertical only, derived
  from the SDF's own wall expression - rejected only 0.02-1.25% more lobes and cost 4.3-7.6%: a net
  loss at every pose, because T121's comparison is against the running union distance and therefore
  cannot reject a far sample at any tightness. The empty-sky control is explained: PLAY_NEAR's
  99.5 ms against CLEAR's 1.8 ms is per-step descriptor *traversal* overhead, not lobe mathematics -
  only 6 lobes are visited per pixel there while 24.5 segment tests answer "no" 99.9% of the time.
  Fetch elasticity is restated at 0.37 representative, not the 0.10 the rank-2 rejection was judged
  on at the empty-sky pose. CASE B for evaluation cost, CASE C for the early-out.
- [X] T142 [PERFORMANCE] Correct the gameplay pose definitions in
  `validation/performance-budget.md` and `validation/performance-baseline.md`: replace `PLAY_NEAR`,
  `PLAY_MID` and `PLAY_HIGH` with `PLAY_VIS_NEAR` (1.6x radius, y=120) and `PLAY_VIS_MID` (2.4x
  radius, y=120), which are inside `cloudRenderDistance` at T134 storm scale, and restate every
  representative budget multiple against them. The harness already implements both poses
  (depends on T138) [FR-010-FR-012, FR-027; SC-006, SC-021]
  **Done.** `validation/performance-pose-definitions.md` is the canonical pose contract: four
  categories (VISIBLE GAMEPLAY, SEVERE STRUCTURAL, STRESS, CONTROL) plus a superseded list. No
  historical measurement is deleted or rewritten; the affected cells stay valid as measurements of
  what they actually rendered, and the representative claims drawn from them are withdrawn. The old
  PLAY_NEAR framing is retained under its correct label, empty-sky-with-descriptors, because paired
  with CLEAR it is the most diagnostically valuable control in the set.
- [X] T143 [PERFORMANCE] Hoist storm reachability out of the per-step march loop and measure it
  against the `PLAY_NEAR`/`CLEAR` bracket, recording the result in
  `validation/performance-traversal-overhead.md`. T141 established that per-step descriptor
  *traversal* - the candidate-map lookup, descriptor validity and group-slot probes, and the
  segment test - is what separates an empty sky with descriptors resident (99.5 ms) from the same
  sky without them (1.8 ms), and that it dominates the per-pixel fetch volume at every pose. Compute
  once per ray, or once per coarse span, the interval of `t` over which any descriptor-owned lobe
  can be reached, from the group bounding volumes `stormGroupSegmentMayIntersect` already builds;
  outside that interval skip the descriptor path entirely, as `StormLobeCount == 0` already does.
  Measure against the control bracket before implementing anything else: if `PLAY_NEAR` does not
  collapse toward 1.8 ms the mechanism is misidentified and the task stops. Zero false negatives
  and T098a remain hard gates (depends on T141) [FR-001, FR-012-FR-013, FR-027; SC-006, SC-017]
  **Rejected; no production change.** Recorded in `validation/performance-traversal-overhead.md`.
  The traversal was located precisely - the dominant source is the rain probe, not the segment test:
  `rainSegmentMayContribute` runs every march step and evaluates `localRainSupportAt` twice, each of
  which walks every descriptor in `directStormLocalBaseAt` and performs a complete
  `directStormShape` union, giving 2.01 storm traversals per step against 0.024 from the safe
  advance. But a conservative spatial bound cannot remove it. A first, additive bound gave
  PLAY_NEAR 109.2 -> 57.8 ms (1.89x) and the fail-first sweep then found 1296 false negatives in
  388,800 probes, worst case 25.9 blocks: the exact SDF's wall term grows at only narrowest/widest
  of the geometric rate, so the guard band must divide by the narrow radius rather than add to the
  wide one. With the corrected, zero-false-negative bound the gate never fires - every pose moves by
  at most 1.6% and the traversal counters are identical to within 0.02% - because a sound reach is
  2.1x-4.9x the lobe's major radius, up to ~2722 blocks for the anvil, which exceeds the entire
  2000-block cloud render distance. Edge softness of 165-200 blocks and the anvil's 2.18x1.56 role
  profile are what make it loose, and both are morphology decisions this task may not change. The
  bound, the arm and the `T143_REACH_GUARD` sweep are retained, defaulting off.
- [X] T145 [PERFORMANCE] Gate the rain probe on precipitation locality rather than storm geometry,
  and record the result in `validation/performance-traversal-overhead.md`. T143 established that
  `rainSegmentMayContribute` is the dominant per-step traversal and that it early-outs only on the
  frame-wide `MaxPrecipitation` uniform, so a severe storm anywhere in the weather map keeps it
  running at any distance. The morphology map already carries precipitation per texel; a per-column
  or per-region bound would skip the whole probe wherever no rain can attach, without touching
  descriptor geometry and therefore without inheriting the softness and anvil-profile looseness that
  defeated T143. Measure against the same `PLAY_NEAR`/`CLEAR` bracket before implementing anything
  (depends on T143) [FR-012-FR-013, FR-027; SC-006, SC-017]
  **Banked; now production behaviour.** Recorded in
  `validation/performance-rain-locality.md`. Two conservative conditions gate the probe before it
  may enter descriptor traversal: the probe height against `max(weatherBaseY, maxBaseDescriptorY)`,
  which bounds `attachY` because `directStormLocalBaseAt` returns a convex combination of the BASE
  descriptors' own bases; and, when the raster precipitation is <= 0.02, the column against the
  union of the ownership ellipses, which are purely horizontal with no softness, blend or warp term
  and therefore bound tightly where T143's SDF bound did not. Representative 1.154x PLAY_VIS_NEAR
  and 1.285x PLAY_VIS_MID, severe 1.13x-1.32x, stress 1.202x, empty-sky-with-descriptors 2.362x,
  clear sky correctly unchanged. Descriptor evaluations -25.0%, fetches -25.6%, `directStormShape`
  calls -29.0%, while march steps, density calls, zero-density calls and light evaluations are
  unchanged to within 0.01%. Zero false negatives over 69,360 sweep probes; at the whiteout pose the
  arm-versus-production difference (0.224% of pixels) is smaller than production's own frame-to-
  frame noise floor (0.315%); at the T098a poses the frames are bit-identical and centre-column
  share stays 1.0000 with a 0 px inner sky run. The flag is inverted to `T145_OFF` per the
  T121/T122 precedent so the equivalence stays re-provable.
- [X] T144 [PERFORMANCE] Collapse the redundant per-sample descriptor evaluations. T141 measured
  **4.35 `directStormShape` calls per `cloudDensity` call** at the corrected representative pose -
  the density path, the final-density path, the structure path and the safe-advance probe each
  evaluate the same world point independently. Removing the redundancy is worth roughly 1.14x at the
  measured evaluation elasticity of 0.16 and is image-neutral when the cached value is the same
  value. Requires a deterministic equivalence test proving the cached and recomputed unions agree
  (depends on T141) [FR-012, FR-019, FR-027; SC-017]
  **Benefit recomputed after T145.** T145 removed the rain probe's share, taking
  `directStormShape` calls per `cloudDensity` call from 4.85 to **3.45** at the corrected
  representative pose. Collapsing 3.45 to 1 removes ~71% of the remaining calls, worth
  `0.65 x 0.16` (evaluations) `+ 0.65 x 0.37` (fetches) `= ~1.5x` on the elasticity model that
  predicted T145 to within 0.002 - not the ~1.14x recorded when only evaluation elasticity was
  considered. First step is a counter measuring how many of the 3.45 are genuinely at the same world
  point, since the four call sites evaluate at different points and only same-point duplicates can
  be collapsed; 1.5x is the ceiling if all are.
  **Rejected; premise invalidated.** Recorded in `validation/performance-rain-locality.md` section 6.
  Reading the call sites settles it without another run: in a production frame `directStormShape` is
  reached from `cloudDensity` at the march sample `p`, from the safe advance at the same `p`, and
  from `directStormRainSupportAt` at `vec3(worldXZ.x, supportY, worldXZ.y)` - the column's storm base
  height, not the sample's. The rain-path calls were never repetitions of one point; they are
  evaluations of different points, and T145 already stopped making the ones locality proves
  pointless. The genuinely same-point duplicate is the safe advance, 1,299,011 of 61,257,282 shape
  calls - **2.1%**, worth about 1% at the marginal rate T145 calibrated. That is below the harness's
  own measurement noise, so the change could not be verified even if made. The residual duplicate
  between `rainSegmentMayContribute`'s probe and `rainShaftDensityAt`'s first `localRainSupportAt`
  is real but occurs only on rain-carrying segments; `rainShaftDensityAt`'s own two calls are not
  duplicates, the second being at the wind-advected source column.
- [X] T146 [PERFORMANCE] Implement the Rank 1 internal-resolution ladder from the measured
  quality/performance frontier, in
  `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricQualityProfile.java`,
  and record the frontier in `validation/performance-internal-resolution-frontier.md`
  (depends on T145) [FR-001, FR-010-FR-012, FR-027, FR-030; SC-005-SC-007, SC-017, SC-021]
  **Banked; image-changing, T098b regrade owed.** Seven scales x seven poses on one fixture with
  everything else held: cost falls as pixels^0.49-0.75 and the exponent degrades as the target
  shrinks, so the lever is nearing its own floor. **T098a passes at every scale down to 0.125** -
  centre-column share 1.0000, longest inner sky run 0 px, FAR coverage growing rather than thinning -
  so the structural gate is not the binding constraint. The binding constraint is silhouette
  softening, which is smooth and has no knee: SSIM 0.998 -> 0.960 and mean displacement 1.0 -> 7.4 px
  from 0.500 to 0.125. Reconstruction cost is flat at 0.085-0.121 ms and is never a floor. Shipped
  ladder, all measured points: Low/Low 24/Medium 0.125 (240x135), High 0.1875 (360x203), Ultra
  0.250 (480x270); the three modes sharing 240x135 still separate 1.8x on their existing step,
  lighting and detail differences. **Representative Ultra 497.3 -> 113.2 ms, 4.39x; stress
  NEAR_EDGE 902.1 -> 179.4 ms, 5.03x**; the frontier's best point is 8.98x at 0.125. The
  representative gap against the 8 ms cloud budget falls from 63.2x to 14.2x. Also fixed: the
  diagnostic `setFixedResolutionScale` floor was 0.25 against the renderer's own 0.10, which
  silently pinned the first sweep's two most aggressive arms to a 480x270 target.
- [X] T147 [PERFORMANCE] Re-measure the renderer's cost distribution at the shipped Rank 1 ladder
  before choosing the next lever, and record it in `validation/performance-post-rank1.md`. Every
  earlier attribution - the lighting share, the step-budget elasticity, the descriptor evaluation and
  fetch elasticities - was measured at 1440x810 and none can be assumed to hold at 480x270, where
  the fallen scaling exponent of 0.61 says occupancy is now a materially different variable. Do not
  extend the old multiplied ceilings; measure the new distribution and pick the next bottleneck from
  it. The two plausible candidates to measure against are interleaved reconstruction - marching at
  0.125 and resolving to 0.250, worth a measured 2.0x at equal spatial quality - and an explicit
  distance/LOD policy, which has never been measured and where FAR already costs half of SIDE
  without one (depends on T146) [FR-010-FR-012, FR-027; SC-006, SC-017]
  **Done; no production change.** Recorded in `validation/performance-post-rank1.md`. Three results
  do not carry over from before Rank 1: **lighting is 16-25% at the representative and severe poses
  and 73% at ABOVE**, against the ~6.5-11.4% T137 carried; the **detail-noise octaves are 9-41%**,
  never separately measured before; and **T145's rain gate is worth more at the new ladder than the
  old** (+14% to +27% to remove, against +12% to +21% at 0.75). Reconstruction and composite remain
  negligible at 0.085-0.121 ms, so the worry that fixed costs would now dominate is measured and
  false - the pass is still overwhelmingly the march. Descriptor elasticities survive Rank 1
  unchanged (evaluation 0.15-0.20, fetch 0.20-0.42), so T141's rejections still stand. **Candidate A
  has no implementation**: the 2.0x is a ceiling inferred from two frontier points, and T146's
  wording is corrected. **Candidate B measured**: removing lighting and detail entirely is
  1.43-1.60x representative and 9.13x at ABOVE, independent at four of five poses and super-additive
  at ABOVE. Stacked at their ceilings A and B take the representative gap from 13.0x to ~4.5x - real
  progress, still not budget. **Decision: CASE A, interleaved reconstruction next**, because it has
  the larger ceiling, moves quality the right way, attacks the artefact the T098b reconnaissance
  found now leads the list (silhouette quantisation has displaced ANVIL flatness), and leaves B
  fully available behind it.
- [X] T148 [PERFORMANCE] Implement interleaved reconstruction: march 240x135 through a 2x2 phase
  pattern and resolve into a 480x270 target with reprojection and disocclusion fallback, then A/B it
  against the shipped ladder on one fixture with T098a as a hard gate. **The first milestone must be
  a moving-camera fixture and a silhouette-stability metric, before any interleaving code**, because
  static poses cannot see the hit/miss flicker that made the current shader freeze its sample
  lattice - `searchBlue` is a static screen-space phase for exactly that reason, and interleaving
  requires moving it (depends on T147) [FR-001, FR-010-FR-012, FR-027; SC-006, SC-017, SC-021]
  **Closed without implementation by T151's measured ceiling.** The 2-phase and 4-phase
  representative ceilings are approximately 1.42x and 1.67x before resolve, reprojection,
  disocclusion, or history overhead, both below the >=1.7x acceptance bar. Production remains
  unchanged; do not reopen interleaving without new evidence that invalidates that ceiling.
- [X] T149 [PERFORMANCE] Graded distance/LOD over the measured 16-25% lighting and 9-41% detail
  shares. Cheapen at distance rather than delete: T147's ceiling comes from removing both outright,
  which is not shippable, and halving the render distance is a visibility change T098a forbids
  because FAR must not disappear. Report popping and transition quality alongside GPU savings
  (depends on T148) [FR-010-FR-012, FR-027; SC-006, SC-017]
  **Rejected; no production change.** Recorded in `validation/performance-lighting-detail-lod.md`.
  The graded light cone removes **27-40% of light evaluations at every pose** and converts almost
  none of it into time: representative PLAY_VIS_NEAR is **+5.7% slower**, the mean of the two
  representative poses is ~1.02x against a 1.3x bar, and the two most expensive poses - BELOW at
  187.5 ms and NEAR_EDGE at 198.4 ms - are the two it helps least, so CASE B fails its own
  condition too. The distance-only arm isolates the cause: it removed **0.1%** of light evaluations
  and still cost **+8.9%**, because a data-dependent tap count stops the compiler unrolling the
  fixed six-iteration light cone. ABOVE is the one pose that wins (-24.2%) because there the
  reduction is both large and spatially coherent, so whole warps drop from six taps to two together.
  The bounded detail arm was also rejected: it faded low-importance lighting-probe detail toward
  the neutral mean and omitted the second near-camera lookup only near sub-pixel scale. It removed
  **45-62%** of counted detail-octave evaluations in the uncontaminated prefix, but PLAY_VIS_NEAR
  regressed **6.1%**, FAR regressed **4.0%**, ABOVE was unchanged, and its best observed result was
  only 1.06x. The expanded run then lost its descriptors during BELOW; that cell was rejected and
  every post-respawn cell is excluded rather than mixed across fixtures. This is the **third
  independent confirmation** that reducing work for some lanes or samples does not become time on
  this shader - after T141's tighter descriptor bound (+4.3 to
  +7.6%) and T151's interleaving ceiling - while the two changes that did work, Rank 1 and T145,
  both remove work uniformly. Also re-measured: lighting is 8.4% at PLAY_VIS_NEAR here against
  T147's 23.2% on a different storm, so that figure was not a stable representative number, and
  BELOW has **no** lighting cost at all because the in-cloud path replaces the cone with one
  forward probe.
- [X] T150 [PERFORMANCE] Add a storm-visibility guard to `StormT135PerformanceProfile`: after a
  storm pose settles, take a one-frame counter capture and refuse the cell unless
  `cloudDensityCalls > 0`. A pose that silently renders no storm has now corrupted three separate
  measurement runs - T142's `PLAY_NEAR`, the T138 ladder, and two of three T147 runs at
  `PLAY_VIS_NEAR` - and the existing descriptor-count check does not catch it (depends on T147)
  [FR-012-FR-013, FR-027; SC-017]
  **Banked.** Recorded in `validation/performance-interleaving.md` part 1. Two halves, both
  required: `StormFixtureVisibility.evaluate` checks descriptors, range against the storm's
  **cylinder**, frustum cone and projected footprint for free before a cell begins, and
  `renderedStormConfirmed` requires the march itself to have produced at least 1% of pixels' worth
  of density evaluations. The fail-first sweep caught a real modelling error: measuring range
  against the bounding sphere accepted `PLAY_NEAR` at 1920 blocks because the sphere bulges below
  the cloud base, while the cylinder - the shape the march can hit - is 2010.1 blocks out, exactly
  reproducing T142's hand-derived figure. Bounded retry respawns and re-resolves up to three
  attempts, then abandons the whole pose rather than recording an empty-sky cell; CLEAR is exempt.
  Live: **7 confirmations, 0 false rejections** across every storm pose, with `PLAY_VIS_NEAR` now
  proven present at 15.89 density calls per pixel.
- [X] T151 [PERFORMANCE] Interleaved reconstruction - **rejected before implementation**. Recorded
  in `validation/performance-interleaving.md` part 2. Interleaving's performance ceiling is exactly
  the cost of marching its reduced pixel count, which is measurable on the shipped ladder without
  writing any of it. Measured on one fixture with T150 confirming the storm in all seven poses:
  marching **half** the pixels (340x191) is **1.42x** representative, and marching a **quarter**
  (240x135) is **1.67x** - both below the >= 1.7x CASE A bar, and both are ceilings that assume the
  resolve pass, reprojection, disocclusion and history traffic are free and that reconstruction
  recovers the missing samples perfectly. The cause is the scaling exponent T146 measured and T147
  confirmed - cost falls as pixels^0.49-0.75, so halving marched pixels returns ~1.4x, not 2x - and
  interleaving inherits it exactly because marching half the pixels is what it does. This also
  corrects T147's inferred 2.04x, which came from a single fixture instance; re-measured it is
  1.74x/1.61x at the representative poses. The 4-phase pattern that comes closest also carries the
  worst temporal exposure available, on a renderer whose sample lattice was deliberately frozen
  because moving it made thin silhouette pixels alternate between hit and miss. CASE C.
- [X] T152 [P] [PERFORMANCE] Build the moving-camera fixture and silhouette-stability metric. Not built
  under T151 because that candidate was rejected on its performance ceiling before its quality
  precondition could matter, but now required before visible-volume traversal can be considered
  production-ready. Drive one deterministic route from outside -> approach -> cloud entry ->
  interior movement -> holes/openings -> exit and record per-frame silhouette position/width,
  alpha-edge stability, flicker, ghosting, disocclusion, column connectivity, inner-sky run, and
  occupied/empty/re-entry continuity in `validation/performance-moving-camera.md`. This may run in
  parallel with T153/T154 but must pass before T156/T157 can bank (depends on T150)
  [FR-001, FR-009-FR-012, FR-032; SC-004-SC-007, SC-021-SC-022]
  **Built and baselined.** Recorded in `validation/performance-moving-camera.md`. One
  continuous Bezier, 2200 frames per arm, both arms proven to fly one storm (0 frames where
  one arm has storm and the other does not). Uniform curve parameterisation was measured and
  rejected: it runs entry/interior/exit at 90-164 px/frame, saturating every temporal term in
  the segments the metric exists to read; each frame now advances a constant 0.00922 rad
  (~14 px/frame) against the nearest cloud surface. **Headline: temporal accumulation is not
  what keeps this renderer stable.** Route flicker is identical with history on and off
  (0.00232 both; per-segment ratios 0.932-1.015), so the frozen sample lattice is doing that
  work alone - which measures the quality half of T151's argument that a lattice-moving change
  cannot be rescued by the history blend. **Flicker is an interior phenomenon**, not a
  silhouette one: 0.00581 mean inside against 0.00086 at ENTRY, p95 2.05%, worst frame 20.7%
  of pixels, with `colRunsMax` 75 inside against 7-9 wherever the storm has an edge against
  sky. Ghosting is negligible in the mean (<=0.00055) and real in the tail (max 0.675), with
  bias within +/-0.0002 of zero - no trailing smear. Disocclusion error rises 29x across the
  route and runs ~10x the unrestricted ghost, so the isolation works. Baseline for T098b:
  0.48-0.81 empty gaps per occupied column, inner-sky runs to 224 px, on the shipped renderer.
  The first run was invalid and reported success - the storm dissipated mid-arm-2 while a
  vacuous cached-fixture check passed - and both causes are fixed with a per-frame
  `lobeCount() >= 10` guard and a bounded whole-route retry. T152's gating purpose is void
  since T153 stopped, but the fixture and baseline stand on their own.
- [X] T153 [PERFORMANCE] **Oracle empty-space / visible-volume ceiling.** Add diagnostic-only oracle
  arms that use real production `cloudDensity` as free ground truth; exclude interval-discovery and
  ground-truth construction cost from the timed oracle traversal, and do not treat the arms as
  production algorithms. For representative rays classify exact intervals containing nonzero
  density, visibly contributing density, transparent openings, later cloud re-entry, and dense
  optically irrelevant interior. Compare production against (A) perfect empty-space skip, (B)
  perfect occupied intervals, (C) perfect optical relevance beyond the current transmittance-floor
  exit, and (D) the combined
  oracle at PLAY_VIS_NEAR, PLAY_VIS_MID, SIDE, FAR, ABOVE, BELOW, and NEAR_EDGE. Record GPU p50/p95,
  steps/pixel, expensive density/descriptor/light/detail evaluations, empty steps and distance
  removed, and work after alpha 50/90/95/98% in
  `validation/performance-visible-volume-oracle.md`. Stop below approximately 2x combined; >=2x
  unlocks T154, >=3x is strong, and >=4x is very strong and may fund Ultra resolution recovery
  (depends on T149, T150) [FR-012-FR-013, FR-027, FR-030, FR-032; SC-006, SC-017, SC-021-SC-022]
  **STOP at 1.63x; T154 does not unlock.** Recorded in
  `validation/performance-visible-volume-oracle.md`. The combined oracle returns **1.633x**
  over the six valid poses and **1.570x** at the two representative gameplay poses, against
  a >=2x gate. Only FAR clears it, at 2.266x, and FAR is the cheapest pose containing a
  storm; the expensive poses return least - NEAR_EDGE 1.781x at 190.3 ms, SIDE 1.523x,
  ABOVE 1.088x. Every figure is a ceiling: the ground-truth pass runs the real production
  `cloudDensity` and is excluded from the GPU query, so a shippable design must pay what
  this harness is given free. Two further findings stand on their own. **The empty space is
  behind the storm, not inside it**: holes are 0.6-13.1 blocks per pixel, 0.05-1.2% of
  skippable distance, while 66-96% is post-cloud ray tail - which independently falsifies
  the premise of T155. **Step count is not the cost**: arm B removes up to 97% of march
  iterations and returns no more than arm A, which removes none, because both cut texture
  fetches ~4.6x and that is what buys the time. This is the fourth confirmation after T141,
  T151 and T149. Arm C is closed outright at 0.958-1.057x: a perfect optical-relevance
  oracle removes only 2.5-5.2% of density evaluations because production's
  `transmittance < 0.015` exit already takes them. BELOW's three traversal arms are excluded
  as a harness defect - they execute 8x more density calls than production while removing
  zero empty steps, defeating rather than subsetting the camera-inside-cloud early-out.
- [X] T154 [PERFORMANCE] **Single production blob feasibility.** Only if T153 reaches >=2x, build
  the smallest practical oracle approximation for one real production lobe using its real
  descriptor, `StormLobeEvaluator`, production `cloudDensity`, noise/remap/erosion, extinction,
  lighting, and raymarch; toy sphere density is prohibited. Measure outside-blob, near-blob, and
  inside-blob fixtures while comparing a low-resolution 3D occupancy/coarse-density volume,
  distance field, macrocell grid, hierarchy, or another measured representation for conservative
  safety, update/upload cost, memory, skipped distance, and warp coherence. Select no representation
  that merely repeats T143's nearly full-ray bound. Record the decision and prototype evidence in
  `validation/performance-visible-volume-single-blob.md` (depends on T153 gate)
  [FR-006, FR-012-FR-013, FR-021-FR-022, FR-027, FR-030, FR-032; SC-006, SC-017, SC-021-SC-022]
  **Closed without implementation: the T153 gate was not met.** The combined oracle ceiling
  is 1.633x against the >=2x this task is explicitly conditioned on. No representation was
  prototyped and production is unchanged.
- [X] T155 [PERFORMANCE] **Multi-lobe holes and re-entry.** Extend the selected T154 prototype to
  multiple real production lobes and prove conservative occupied -> empty -> occupied traversal for
  camera -> cloud -> opening -> deeper cloud rays. When remaining transmittance is meaningful, the
  deeper interval must render; no hollow shell, first-hit opacity, fixed shell thickness, or skipped
  role handoff is allowed. Measure coherent skip span, false negatives, GPU gain, T098a connectivity,
  and image difference in `validation/performance-visible-volume-reentry.md` (depends on T154)
  [FR-001-FR-006, FR-021-FR-027, FR-030, FR-032; SC-001-SC-002, SC-011-SC-017, SC-022]
  **Closed without implementation, and independently falsified.** T154 never unlocked, and
  T153 measured this task's own subject directly: transparent openings between lobes are
  0.6-13.1 blocks per pixel, 0.05-1.2% of the skippable distance, against 66-96% lying in
  the post-cloud ray tail. The conservative occupied -> empty -> occupied machinery specified
  here would guard about one part in two hundred of the available saving.
- [X] T156 [PERFORMANCE] **Inside-cloud validation.** Exercise outside, near, entry, fully inside,
  thin-region, hole/opening, deeper re-entry, and exit cases using the T152 route. Verify the full
  production density field still presents nearby dense cloud, thin regions, actual internal
  structure, openings, and deeper cloud, with camera density/whiteout, rain, depth, and history
  aligned. Reject any `insideCloud -> generic fog` or first-surface substitution. Record static and
  moving evidence in `validation/performance-visible-volume-inside-cloud.md` (depends on T152, T155)
  [FR-006-FR-009, FR-021-FR-022, FR-030, FR-032; SC-001-SC-004, SC-022]
  **Closed without implementation: no surviving T155 prototype to validate.** Note that
  T153's own BELOW arms failed precisely by defeating the camera-inside-cloud early-out
  rather than subsetting it, which is the failure mode this task existed to catch.
- [X] T157 [PERFORMANCE] **Severe cumulonimbus integration and bank/reject gate.** Integrate the
  surviving representation on one descriptor-owned ten-member post-T134 severe storm and validate
  PLAY_VIS_NEAR, PLAY_VIS_MID, SIDE, FAR, ABOVE, BELOW, and NEAR_EDGE. Require coherent large-span
  skipping, bounded build/update/upload work, stable topology/ownership, T098a green, rain/whiteout
  and depth correctness, and T152 temporal stability. Bank only if the practical production design
  retains a material multi-X gain without shells, lost re-entry, or catastrophic visual regression;
  otherwise reject it and record the terminal reason in
  `validation/performance-visible-volume-severe.md` (depends on T156)
  [FR-001-FR-009, FR-012-FR-019, FR-021-FR-032; SC-001-SC-010, SC-017, SC-021-SC-022]
  **Closed without implementation: nothing reached this gate to bank or reject.** The
  terminal reason is recorded in `validation/performance-visible-volume-oracle.md` section 7
  rather than in this task's own file, which was never created.
- [X] T158 [PERFORMANCE] **Post-architecture production remeasurement.** If T157 banks, rebuild the
  same-fixture production cost distribution at all seven canonical poses: cloud GPU p50/p95,
  steps/pixel, density/descriptor/fetch/light/detail work, empty distance skipped, optical-depth
  work distribution, reconstruction/history, build/upload/cache cost, representative and stress
  speedups, remaining 8 ms gaps, and the new dominant bottleneck. Do not reuse T147/T149 shares.
  Record the result in `validation/performance-post-visible-volume.md` (depends on banked T157)
  [FR-012-FR-013, FR-027, FR-030; SC-006, SC-017, SC-021-SC-022]
  **Closed without implementation: T157 did not bank.** The production cost distribution
  therefore stands unchanged at the T147/T149 shares, and T140 reprofiles against those.
- [X] T159 [PERFORMANCE] **Ultra resolution recovery.** If T158 shows multi-X headroom, compare the
  banked renderer at 0.25/480x270, 0.375/720x405, and 0.50/960x540 at 1920x1080 across the seven
  poses and T152 route. Report GPU p50/p95, silhouette quantisation, softness/fogginess, interior
  detail, reconstruction artifacts, temporal behavior, and T098a. Select a sharper candidate only
  within measured performance headroom; do not lower Ultra below 0.25 as the primary solution.
  Record the shipping input for T139/T098b in `validation/performance-ultra-resolution-recovery.md`
  (depends on T158) [FR-001, FR-006, FR-010-FR-012, FR-030-FR-032; SC-001, SC-005-SC-007, SC-021-SC-022]
  **Closed without implementation: no multi-X headroom was found to fund it.** Ultra stays at
  the Rank 1 ladder's 0.250 internal scale; T139 receives no resolution-recovery input and
  T098b regrades the shipped ladder as it stands.
- [X] T160 [P] [VISUAL POLISH] Run the bounded upper-cloud root-cause experiment independently of
  T153: in diagnostic-only arms temporarily relax upper TOWER/ANVIL height and horizontal-width
  extents, never ship those values, and classify whether (A) the existing shape broadens naturally
  into an anvil, (B) it becomes a larger dome, (C) density/remap/erosion collapses the intended
  width, or (D) final density is broad but rendering/reconstruction hides it. Record ABOVE plus
  SIDE/FAR evidence in `validation/t098b-upper-anvil-envelope.md` for later T098b use
  (depends on T098a) [FR-003, FR-006, FR-023-FR-024; SC-001-SC-002, SC-011-SC-016]
  **Root cause is (C)-adjacent but resolves to PROFILE SHAPE.** Recorded in
  `validation/t098b-upper-anvil-envelope.md`, measured headlessly on the real production
  density path via `stormT160UpperEnvelopeSandbox`. The upper morphology is **not truncated**:
  it reaches **104.6%** of its own intended width and its support extends **28 blocks above**
  the role envelope top rather than being clipped. The cap is the anvil's own
  `profileRadius`, which **peaks at height fraction 0.65 and declines** above it while
  `verticalShape` fades from 0.76 to zero - so the top third is a constant-then-narrowing
  column with fading density. Width is **not** still increasing at termination: it peaks at
  v~0.81 (480-block half-width at y=468) and falls. The stage trace excludes everything
  downstream - envelope -> body -> final preserves width to within 3% at every height, so the
  descriptor envelope is already the limiting shape and neither remap nor erosion contracts
  it. The TOWER->ANVIL handoff is **not** the cause: the tower never widens (flat 76-85
  blocks) and is fully enclosed by the anvil from y=380, so its abrupt end at y~476 is
  invisible; the silhouette stops expanding where the anvil's radius derivative collapses
  from +3.8 to +0.63 at y~476. The relaxed arm is **case A** - moving only the knee, endpoint
  and fade starts gives 1.86x half-width, 1.32x height, 4.14 width/height, still widening at
  its own termination with bounded density - so the existing profile **can** form a proper
  anvil; its constants stop it at 62% height. **T098b lever: the anvil radius knee at 0.62**,
  not any extent clamp. Nothing banked; the arm is default-off and never referenced by
  production. Caveat: rendered occupancy was not measured, so root cause 4 is not positively
  excluded - and the density field from above is a 1.5:1 ellipse, so a rendered view that
  reads *circular* would itself be evidence for it.
- [ ] T140 [PERFORMANCE] Reprofile all five modes after T139 and the terminal Phase 4Q outcome using T135's written targets in
  `specs/001-native-storm-rendering/validation/performance-baseline.md`, record per-mode pass/fail
  and representative visual checks, and prepare the evidence consumed by final T070/SC-006. This
  task does not waive final shipped visual regrading in T098b
  (depends on T052, T139, and T159 if resolution recovery proceeds, otherwise the recorded
  T153--T157 stop task) [FR-010-FR-012, FR-027, FR-030; SC-005-SC-007, SC-017, SC-021]

**Checkpoint**: Performance work has a measured budget and ranked architecture before further
implementation, while visual polish remains independently active.

---

## Phase 5: User Story 3 - Scalable Quality Modes (Priority: P3)

**Goal**: Preserve five progressively increasing modes, bounded predictable storm LOD, stable adaptive degradation/recovery, and Ultra's target performance without disconnecting storm groups.

**Independent Test**: Run the same severe-weather route in Low, Low 24, Medium, High, and Ultra, then create sustained load and recovery. Every mode renders the connected storm, reports correct effective settings, respects floors/ceilings, and avoids oscillation.

### Tests for User Story 3

- [ ] T042 [PERFORMANCE] [US3] Add failing preset-table, monotonic detail, target/floor, EWMA,
  30-frame downgrade, 180-frame recovery, 30-second cooldown, adaptive-disable, and reset
  assertions in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java`.
  Quality plumbing requires structural correctness and measured performance policy, not T098b
  visual polish (depends on T098a, T135) [FR-010-FR-012; SC-005, SC-007, SC-021]
- [ ] T043 [US3] Add failing detail-distance clamp, 128-block cross-fade, complete-group LOD, no-hole/no-double-weight, and capacity-to-map fallback assertions in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` (depends on T042) [FR-010-FR-011]

### Configuration, Quality, and LOD Implementation

- [ ] T044 [P] [US3] Add `adaptiveCloudQuality` defaulting true and `nativeStormDetailDistance` defaulting 1536 with range 256-4096 to `src/main/java/net/Gabou/projectatmosphere/config/AtmoCommonConfig.java` (depends on T042) [FR-010-FR-011]
- [ ] T045 [P] [PERFORMANCE] [US3] Extend nominal steps/resolution, lighting/detail work, GPU
  targets, and per-mode floors for Low, Low 24, Medium, High, and Ultra in
  `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricQualityProfile.java`
  from T135's budget contract (depends on T042, T135) [FR-010-FR-012; SC-021]
- [ ] T046 [US3] Replace the scalar governor with immutable adaptive state, GPU-time EWMA, sustained thresholds, discrete bands, floor/ceiling clamps, transition generation/reason, and cooldown in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/CloudFrameTimeGovernor.java` (depends on T042, T045) [FR-011; SC-007]
- [ ] T047 [US3] Read visual config once during frame setup, clamp storm detail distance to total render distance, and apply effective quality state in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java` (depends on T044-T046) [FR-010-FR-011]
- [ ] T048 [US3] Add complete-group analytic/map LOD classification, full-detail range, 128-block transition weights, and map-only handling for distance/capacity omissions in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeSpatialIndex.java` (depends on T043, T047) [FR-001, FR-010-FR-011]
- [ ] T049 [US3] Apply analytic/map cross-fade without double density and scale only bounded refinement/lighting work—not group integrity—in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` (depends on T045, T048) [FR-001, FR-010-FR-011]
- [ ] T050 [US3] Recreate render targets and invalidate history once on discrete resolution transitions while leaving step-only changes history-valid in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderer.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderTargets.java` (depends on T038, T046-T047) [FR-009-FR-011]
- [ ] T051 [US3] Remove stable-frame list/map/descriptor diagnostic allocations and reuse bounded sort, descriptor, candidate, and upload storage in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeSpatialIndex.java` (depends on T047-T050) [FR-012, FR-019]
- [ ] T052 [VALIDATION / RELEASE] [US3] Run the five-mode route plus forced load/recovery and
  record effective settings, visual monotonicity, transitions, rebuild rate, preliminary timings,
  and the T139 policy in `specs/001-native-storm-rendering/validation/us3-quality-lod.md`.
  Exhaustive final appearance grading remains T098b (depends on T042-T051, T139) [SC-005, SC-007,
  SC-021]

**Checkpoint**: All five modes and adaptive LOD are independently verifiable; Ultra is ready for the final controlled performance gate.

---

## Phase 6: User Story 4 - Actionable Renderer Diagnostics (Priority: P4)

**Goal**: Identify ownership, storm structure, rain, quality, capacity, caching, async state, history, and timing from bounded on-demand diagnostics without normal log/allocation overhead.

**Independent Test**: In one severe-storm session, use existing `/pa cloud volumetric` commands to identify the active renderer, direct/map group workload, role/candidate capacity, rain and camera density, effective quality, rebuild/history reasons, and timings; switch structure/rain/final views without enabling continuous logs.

### Tests for User Story 4

- [X] T053 [VALIDATION / RELEASE] [US4] **[SATISFIED BY EXISTING DIAGNOSTICS]** Counter
  semantics, bounded capture, deterministic formatting, fallback reasons, and fail-first guards
  are covered by the retained geometry/stability sandboxes and T119--T123/T132 diagnostics. Do not
  rebuild them merely because the original task predates that work; see
  `specs/001-native-storm-rendering/validation/renderer-wide-architecture-audit.md` (depends on T133) [FR-013,
  FR-019; SC-009]

### Diagnostic Implementation

- [X] T054 [FUNCTIONAL] [US4] **[SATISFIED BY EXISTING DIAGNOSTICS]** The bounded primitive
  counters/capture live in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudFrameDiagnostics.java`, `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormPerformanceSuite.java`,
  `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormWorkloadRuntimeCapture.java`, and the material-trace path; no duplicate
  `StormLobeDiagnostics` class is warranted (depends on T053) [FR-013, FR-019]
- [X] T055 [FUNCTIONAL] [US4] **[SATISFIED]** `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudFrameDiagnostics.java` publishes compact
  workload, effective quality, GPU timing, history, and camera-density data (depends on T054)
  [FR-013]
- [X] T056 [FUNCTIONAL] [US4] **[SATISFIED]** Existing debug-view/config infrastructure in `src/main/java/net/Gabou/projectatmosphere/command/TelemetryDebugClientCommand.java` provides
  bounded storm/body/envelope/candidate/precipitation/combined inspection and restores final-view
  history safely (depends on T053) [FR-013]
- [X] T057 [FUNCTIONAL] [US4] **[SATISFIED]** Existing shader debug outputs in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` and workload readback
  cover role/envelope/candidate/precipitation/combined attribution (depends on T056) [FR-013]
- [X] T058 [FUNCTIONAL] [US4] **[SATISFIED]** `src/main/java/net/Gabou/projectatmosphere/command/TelemetryDebugClientCommand.java` already exposes
  storm density, material trace, workload, and performance-suite diagnostics; consolidate their
  documentation rather than duplicate the command tree (depends on T054-T057) [FR-013; SC-009]
- [ ] T059 [VALIDATION / RELEASE] [US4] **[CONSOLIDATION]** Verify normal-frame logging is opt-in
  and move any remaining storm text/per-group enumeration behind explicit capture paths in
  `src/main/java/net/Gabou/projectatmosphere/command/TelemetryDebugClientCommand.java` and the
  existing diagnostics (depends on T054-T058) [FR-019]
- [ ] T060 [VALIDATION / RELEASE] [US4] **[GENUINELY MISSING EVIDENCE]** Run and document one
  diagnostic session across final/body/envelope/candidates/precipitation/combined views in
  `validation/us4-diagnostics.md`; it must show every FR-013/SC-009 question is answered without
  normal logging (depends on T059) [SC-009]

**Checkpoint**: All four user stories are independently functional and observable through the existing command surface.

---

## Phase 7: Compatibility, Fallback, and Release Validation

**Purpose**: Protect optional ownership, rollback, server safety, existing regressions, visual acceptance, and the controlled Ultra performance target.

### Simple Clouds and Legacy Fallback

- [ ] T061 [P] [FUNCTIONAL] Create native/Simple-Clouds/field-fallback owner-transition assertions
  in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/CloudRenderOwnershipSandbox.java`.
  This is independent of visual polish and may start now [FR-016-FR-018; SC-008]
- [ ] T062 [VALIDATION / RELEASE] Register `cloudRenderOwnershipSandbox` under `check` in
  `build.gradle` (depends on T061)
- [ ] T063 [FUNCTIONAL] Ensure Simple Clouds ownership short-circuits before native descriptor
  selection, worker submission, target preparation, upload, and density publication in
  `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/ClientCloudRenderOwnership.java`
  and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java` (depends on T028, T061)
  [FR-016-FR-017]
- [ ] T064 [FUNCTIONAL] Implement direct-subpath failure state so missing membership, capacity,
  async saturation, stale builds, or descriptor/candidate allocation/upload failures retain a valid
  generation or broad-map LOD in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormGeometryBuildCoordinator.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java`
  (depends on T022, T054) [FR-018]
- [ ] T065 [VALIDATION / RELEASE] Verify wider native failure follows the existing session-disable
  and developer legacy-field-or-vanilla rollback policy without changing its property/config
  contract in `specs/001-native-storm-rendering/validation/compatibility-and-fallback.md` (depends on T063-T064) [FR-018]
- [ ] T066 [VALIDATION / RELEASE] Run default `runClient` and
  `runClient -PenableSimpleCloudsRuntime=true` through startup, world entry, dimension transition,
  resource reload, and optional-integration failure; record owner and zero-native-work evidence in
  `validation/compatibility-and-fallback.md` (depends on T063-T065) [FR-016-FR-018; SC-008]

### Automated and Visual Regression

- [ ] T067 Run `stormVolumetricGeometrySandbox`, `cloudMorphologyTopologySandbox`, `volumetricStabilityDiagnosticsSandbox`, `materialAdvectionSandbox`, `cloudRegionMotionSandbox`, `cloudFieldSandbox`, `cloudRenderOwnershipSandbox`, `architectureBoundaryCheck`, `check`, and `build`; record exact results in `specs/001-native-storm-rendering/validation/automated-regression.md` (depends on T030, T041, T052, T060, T062) [SC-010]
- [ ] T068 [VALIDATION / RELEASE] Run `runServer` and record that no client renderer, shader,
  Minecraft client singleton, or LWJGL class loads on the dedicated server in
  `validation/dedicated-server.md`. This server-safety gate is independent of visual polish
  (depends on T062) [FR-015, FR-017]
- [ ] T069 [VALIDATION / RELEASE] Execute the complete below/beside/inside/above,
  isolated/overlap, lifecycle, detail-boundary, total-distance, dry/rain/whiteout, camera-motion,
  resize, resource-reload, dimension, terrain-depth, and all-quality **shipping** visual matrix
  from `quickstart.md`, attaching pass/fail evidence to `validation/visual-regression.md`
  (depends on T052, T060, T066-T068, T098b) [SC-001-SC-005, SC-008-SC-009]

### RTX 4070 Performance Gate

- [ ] T070 [VALIDATION / RELEASE] Capture a ten-minute post-convergence Ultra run on the specified
  plugged-in RTX 4070 laptop at 1920×1080, no external shader pack, and approximately 2000-block
  cloud distance; record p50/p95/p99 total frame time, cloud GPU stages, CPU build/upload,
  rebuild/cache/overflow, allocation, and adaptive-transition data in
  `validation/rtx4070-ultra-performance.md` (depends on T067, T069, T140) [FR-012; SC-006-SC-007]
- [ ] T071 [PERFORMANCE] Close a measured Ultra gate failure with an evidence-backed bounded
  change, then append before/after evidence to `validation/rtx4070-ultra-performance.md`. It is a
  final release-gap task after T070, not the first performance confrontation and not gated by
  T099/T098b; any image change returns to T098b/T069 (depends on T070) [FR-001, FR-010-FR-012,
  FR-020; SC-005-SC-007]
- [ ] T072 Re-run the full automated suite and targeted visual matrix after performance changes and record the final release-gate result in `specs/001-native-storm-rendering/validation/final-verification.md` (depends on T071) [SC-001-SC-010]
- [ ] T073 Update runnable commands, effective configuration names/defaults, diagnostic output, group-witness candidate semantics, async adoption/re-request behavior, exact-union rain attachment, and confirmed fallbacks in `specs/001-native-storm-rendering/quickstart.md`, `specs/001-native-storm-rendering/contracts/render-ownership-and-sync.md`, and `specs/001-native-storm-rendering/contracts/storm-render-diagnostics.md` to match the verified implementation (depends on T066-T072)

**Checkpoint**: Native and Simple Clouds ownership, legacy rollback, dedicated server, automated regressions, visual acceptance, and the RTX 4070 p95 gate all pass with retained evidence.

---

## Dependencies and Execution Order

### Phase Dependencies

```text
Phase 1 Setup
    -> Phase 2 Foundational
        -> US1 Readable Volumetric Storms (MVP)
            -> US2 Stable Cloud and Rain
                -> Phase 4R Storm Morphology Correction  (T074-T097 complete)
                    -> Phase 4S Storm Density Architecture Correction (T100-T126 complete)
                        |                          \
                        |                           -> T130 -> Phase 4P Storm Performance Architecture
                        |                              (T119-T123) -> T132
                        -> Phase 4A Renderer-wide Correction Gate (T127-T131)
                                                   \
                                                    -> T133 -> T098a Structural Gate -> T099
                                                               |       \
                                                               |        -> US3/US4/compatible release work
                                                               -> Phase 4Q T135-T153 oracle
                                                                    -> T154 single production blob
                                                                    -> T155 holes/re-entry
                                                                    -> T152 + T156 inside-cloud
                                                                    -> T157 full severe storm
                                                                    -> T158 remeasure -> T159 Ultra recovery
                                                                                         \
                                                                                          -> T098b -> release gates
```

- Phase 1 has no implementation dependency and establishes the baseline/harness.
- Phase 2 depends on T002-T003 for its test entry point and blocks every story.
- US1 depends on all foundational contracts and produces the direct structured-storm path.
- US2 depends on the adopted render snapshot and direct shader path from US1.
- Phase 4R depends on the T041 audit and is complete through T097. Its T081, T085, T086, T089, T090, and T097 acceptance criteria are superseded by Phase 4S; those tasks stay checked as implementation history and are not rewritten.
- Phase 4S depends on Phase 4R and owns the corrected density architecture. T100 must precede every Phase 4S regression so no threshold is set without a derivation, and T107 must record meaningful fail-first results before any Phase 4S production change. T124-T126 are complete history; their morphology thresholds remain retained inputs to T133.
- Phase 4A depends on the completed Phase 4S gate. T127-T129 derive scale and attribute the first material discontinuity. T131 may change only the measured responsible stage. T130 and neutral Phase 4P work can proceed in parallel with that diagnosis, but T132 must prove equivalence before convergence at T133.
- Phase 4P is **not** blocked by T098a/T098b/T099. T119, T121, T122, and T123 are accepted after T130's baseline under their controlled equivalence/runtime-counter evidence. Phase 4P tasks are separate commits from visual-correctness work and may not alter the rendered result.
- Phase 4Q is the post-T149 adaptive visible-volume/occupancy track. T153 is a diagnostic oracle and
  gates every production prototype. T152 and T160 may run in parallel; T152 converges before T156
  and T157, while T160 feeds T098b only. A failed oracle or prototype records a terminal stop rather
  than forcing the remaining architecture tasks.
- T098a, T098b, and T099 use the revised criteria: T098a depends on T133, T099 remains blocked by T098a, and T098b is the later final-shipping visual gate.
- US3 quality-mode plumbing depends on T098a and the written T135 budget contract, not on T099 or
  T098b. T052 validates the policy and transitions; T098b later performs authoritative shipped
  visual grading. T050 retains its real US2 history dependency.
- US4 depends on US1 for workload sources and on the effective quality and visual-density state from US2/US3 for a complete report.
- Phase 7 depends on all desired stories; T070-T072 are post-correction hard release gates, not optional polish. The observed roughly 80, 100, 140, and 200+ ms raymarch times are not final evidence; T130 establishes their baseline and T132/T133 re-measure the approved architecture work.

### User Story Dependency Graph

| Story | Required predecessors | Independent completion signal |
|---|---|---|
| US1 (P1) | Setup + Foundational; Phase 4S retained, Phase 4A converged at T133 | T098a proves connected, visible severe structure and retained renderer correctness; T098b separately grades final appearance |
| US2 (P2) | T098a plus retained final-density/history prerequisites | Replacement T099 proves rain and whiteout follow final noise-formed density |
| Phase 4R | T041 audit | T097 recorded corrected union evidence; superseded in part by Phase 4S |
| Phase 4S | Phase 4R | T118 records corrected density-architecture evidence; T124-T126 retain macro/role evidence |
| Phase 4A | Completed Phase 4S | T133 records scale, material continuity, morphology, final-density, and performance convergence |
| Phase 4P | T130 reference baseline | T132 proves bounded cost work preserves the frozen rendered result and trace |
| Phase 4Q | T098a + T149/T150 evidence | T153 proves >=2x oracle value before prototypes; T157 banks/rejects the full-storm design; T152/SC-022 prove motion, holes, re-entry, and inside-cloud safety |
| US3 (P3) | T098a + T135 for plumbing; T050 also needs US2 history work | T052 proves all modes, LOD, and adaptive stability; T098b grades the shipped visual result |
| US4 (P4) | US1 plus completed US2/US3 state providers | T060 answers the diagnostic contract from one session |

### Key Task Chains

- **Source geometry**: T011 -> T015 -> T016 -> T018.
- **Direct representation**: T004-T007 -> T012-T013 -> T017-T021 -> T023-T027.
- **Async/cache lifecycle**: T005-T010 -> T014 -> T021-T022 -> T028 -> T064.
- **Rain/whiteout/history**: T031-T033 -> T034-T040 -> T041 -> T078-T079 -> T089, T093-T095 -> T097 -> T115-T116 -> T099.
- **Morphology correction (Phase 4R)**: T074-T079 -> T080 -> T081-T090 -> T091-T096 -> T097.
- **Density architecture (Phase 4S)**: T100 -> T101-T106 -> T107 -> T108-T111 -> T112-T114 -> T115-T117 -> T118 -> T124-T126.
- **Renderer-wide correction (Phase 4A)**: T127 -> T128 -> T129 -> T131; T130 -> T119/T121/T122/T123 -> T132; T131 + T132 -> T133 -> T098a -> T099. T098b is a later shipping visual gate.
- **Performance architecture (Phase 4P)**: T130 -> T119 -> T121/T122 -> T123 -> T132; T121 is skipped rather than approximated if equivalent evidence is unavailable.
- **Adaptive visible-volume traversal (Phase 4Q)**: T149 + T150 -> T153 -> T154 -> T155;
  T150 -> T152 in parallel; T152 + T155 -> T156 -> T157 -> T158 -> T159. T160 runs in parallel
  after T098a and feeds T098b. T153/T154/T155/T156/T157 may terminate the architecture with a
  recorded rejection instead of opening their successors.
- **Quality/LOD**: T098a + T135 -> T042-T043 -> T044-T051 in parallel with Phase 4Q; T139 banks
  only after the terminal Phase 4Q result, then T052 -> T140 -> T070-T071.
- **Diagnostics**: T053 -> T054-T059 -> T060.
- **Compatibility/fallback**: T061-T065 -> T066 -> T067-T069.
- **Release**: T067-T070 -> T071 -> T072-T073.

## Parallel Opportunities

### User Story 1

After Phase 2:

```text
T011 morphology topology tests  ||  T012 descriptor/evaluator tests
T015 source role geometry       ||  T017 Java analytic evaluator
```

Converge those branches at T018, then complete selection, cache, async, GPU, and shader tasks sequentially because they share contracts and resources.

### User Story 2

After T031-T033:

```text
T034 CPU visual-density evaluator  ||  T036 local shader precipitation support
```

Converge at T035/T037 and then integrate shared history/lifecycle state.

### Phase 4S

After T100:

```text
T101 interior noise influence  ||  T102 density variance
T103 multi-scale spectral      ||  T104 geometric distance field
T105 positive structural       ||  T106 rejected forms
```

Converge at T107, then run T108-T111 sequentially because they share the distance/envelope contract
across Java and GLSL. T112-T113 follow T111 in order. T115, T116, and T117 may run in parallel after
T113/T114 because they own different consumers.

### Phase 4P

```text
T127/T128/T129 measured continuity diagnosis  ||  T130 baseline -> T119 group topology
```

T122 may follow T119; T121 may run only if its equivalent lighting-support proof is available.
T123 and T132 converge their measured work with T131 at T133. T098a cannot run before that
convergence.

### Phase 4Q

After T149/T150 and accepted T098a:

```text
T153 oracle ceiling -> T154 single-blob feasibility -> T155 multi-lobe re-entry
T152 moving-camera route -----------------------------------------------------> T156 inside-cloud
T160 upper-anvil diagnostic -------------------------------------------------> T098b
```

T153 is the first critical-path task. T152 and T160 own separate diagnostic/test files and may run
alongside it. T156 requires both the selected T155 prototype and T152. The full-storm T157 gate,
remeasurement T158, and Ultra recovery T159 are sequential because each consumes the preceding
measured decision. T042-T051 quality-mode plumbing, T099, US4 consolidation, and compatible release
work remain parallel; only final T139/T052/T098b/T140 evidence waits for the shipping architecture.

### User Story 3

After T098a and T135 for quality-mode plumbing, and then after T042-T043:

```text
T044 Forge visual configuration  ||  T045 quality preset table
```

The governor, renderer integration, LOD, and resolution transitions then follow in order.

### User Story 4

After T053:

```text
T054 storm diagnostic accumulator  ||  T056 debug-view enum/config
```

Converge at frame diagnostics, shader views, and command registration.

### Cross-Story

Phase 4R may use only the parallelism explicitly marked in T079; its tests complete and fail meaningfully before production fixes. Phase 4S follows the same fail-first discipline through T107. Phase 4A requires fail-first material attribution before correction. Phase 4P runs after T130's frozen baseline, in separate commits, and may not alter the rendered result. Phase 4Q runs from T149/T150 evidence in parallel with T099, US3 plumbing, US4 consolidation, compatible release work, and T160 visual diagnosis; every image change is returned to T098b. US3 quality-mode plumbing is gated by T098a and T135, not T099. Begin US4's standalone diagnostic data model only after the corrected workload/counter meanings and the test contract in T053 are stable.

---

## 2026-09-03 authoritative post-T149 dependency and classification update

This section supersedes conflicting dependency prose anywhere else in this document. It preserves checked historical work
and its evidence; it does not erase old acceptance/retraction records.

### Classification legend

| Classification | Work currently in that class |
|---|---|
| **BLOCKING CORRECTNESS** | T099 and retained production correctness regressions; T098a is accepted |
| **PERFORMANCE** | T139-T159, T042-T051, T071, final T070 acceptance; T135-T151 are retained measured/banked/rejected history |
| **VISUAL POLISH** | T098b, T160 upper-anvil side experiment, final shipped visual matrix T069 |
| **FUNCTIONAL** | T061, T063, T064 and compatibility/fallback behavior |
| **VALIDATION / RELEASE** | T052, T053, T059-T060, T062, T065-T070, T072-T073 |

### New critical paths

```text
Structural/rain path:       T133 -> T098a -> T099
Performance path:           T149 + T150 -> T153 oracle -> T154 single blob -> T155 holes/re-entry
                              T152 (parallel) + T155 -> T156 inside-cloud -> T157 full storm
                                             -> T158 remeasure -> T159 Ultra recovery
                                             -> T139/T052 -> T098b || T140
                                             -> T069 + T140 -> T070 -> T071 -> T072
Quality-policy path:        T098a + T135 -> T042 -> T045..T051 || Phase 4Q; terminal Phase 4Q -> T139 -> T052
Visual-polish path:         T160 || Phase 4Q; T152 + T159/terminal stop + T139 + T052 + T160 -> T098b -> T069
Compatibility/server path:  T061 -> T062 -> T063/T064 -> T065 -> T066
                              T062 -> T068
Release convergence:        T052 + T060 + T066 + T068 + T098b -> T069 -> T070..T073
```

T098a is accepted and remains a hard regression gate for Phase 4Q. T098b is not a predecessor of
T099, T042-T051, T053-T068, T152-T158, T160, or other unrelated release work. It becomes
authoritative only after the shipping traversal/resolution/quality configuration stabilizes.

### Immediate parallel execution

```text
T153 oracle ceiling                  || T152 moving-camera route || T160 upper-anvil diagnostic
T099 rain/whiteout                   || T042-T051 quality plumbing
T053-T060 diagnostic consolidation  || T061-T068 compatible release work
```

T154 begins only if T153 reports a combined oracle ceiling of at least approximately 2x. T155-T159
then follow their measured gates; a rejection records the terminal architecture result and returns
the stabilized renderer to T139/T098b rather than spawning speculative microtasks.

## Implementation Strategy

### MVP First: User Story 1

1. Complete T001-T010.
2. Write and observe expected failures for T011-T014.
3. Complete authoritative derived geometry and Java analytic representation in T015-T018.
4. Complete bounded selection, caching, async publication, targets, and shader integration in T019-T028.
5. Remove the lossy role maps only after the direct path works in T029.
6. Treat T030 as reopened after the T041 audit. It remains reopened after the 2026-08-19 correction and is replaced through the revised T098 evidence, which must satisfy the positive morphology criteria, not only artifact absence.

### Incremental Delivery

1. **US1** fixes the central visible storm failure while preserving broad-map fallback.
2. **US2** aligns rain, whiteout, and history with the completed-frame representation.
3. **Phase 4R** corrects the audited composition defect and restores the candidate index only as acceleration.
4. **Phase 4S** makes descriptors a coverage envelope, makes noise form the visible body, replaces density-space pseudo-distance with world-space geometric distance, applies erosion across the interior, and replaces US1/US2 evidence against positive morphology criteria.
5. **Phase 4A** derives the severe-system scale, measures the lower/upper material trace, and corrects only its first discontinuous stage.
6. **Phase 4P** makes the corrected model practical with visually-neutral topology, culling, fetch, reuse, and bounded-cost work measured against T130; a lighting proxy is conditional on equivalence.
7. **T133/T098a** revalidate physical size, single-medium continuity, morphology, final-density consumers, and performance before rain validation; **T098b** performs the final shipping visual regrade.
8. **US3** adds predictable mode scaling, LOD, and adaptive performance policy after T098a and the T135 budget contract; final visual grading returns to T098b.
9. **US4** exposes bounded evidence for ownership, workload, artifacts, and timing.
10. **Phase 4Q** uses T153 to measure the perfect visible-volume ceiling before implementation,
    approximates it on one production blob, proves holes/re-entry and inside-cloud behavior with
    T152, integrates a full severe storm, remeasures production, and attempts Ultra resolution
    recovery only if the measured headroom supports it.
11. **Phase 7** proves Simple Clouds boundaries, legacy fallback, server safety, full regressions, and the post-correction RTX 4070 release gate.

## Traceability Summary

| Requirement area | Primary tasks |
|---|---|
| Connected 3D stages and overlap | T011-T030, T074-T077, T080-T090, T096, T124-T126, T129-T133, T098a |
| Coverage envelope vs. noise-formed body | T100-T102, T107, T110-T114, T118 |
| Geometric distance field and world-space unions | T104, T107-T109, T111, T118 |
| Physical severe-system scale and one-medium continuity | T127-T131, T133, T098a |
| Positive morphology criteria and derived thresholds | T100, T102-T106, T118, T124-T126, T133, T098a-T098b, T160 |
| Interior detail erosion | T101, T103, T113, T118 |
| Bounded descriptor evaluation cost | T130, T119-T123, T132-T133; exhausted micro-optimization evidence T141/T143/T144/T149/T151 |
| Coherent visible-volume traversal and optical relevance | T153-T159, with T152 as the moving/inside-cloud readiness gate |
| Rain, whiteout, temporal stability | T031-T041, T078-T079, T089, T093-T095, T097, T098a, T099 |
| Descriptor validity, fallback, async, signatures | T079, T087-T088, T091-T092, T096-T097 |
| Five modes, adaptive quality, LOD | T098a + T135 -> T042-T052 + T139 |
| Bounded diagnostics | T053-T060 |
| Server/network/save preservation | T018, T027-T030, T067-T068 |
| Simple Clouds ownership | T061-T066 |
| Legacy fallback | T064-T066 |
| Automated/visual regression | T067-T069, T072, T098b |
| RTX 4070 performance | T119-T123 -> T135-T151 -> T153-T159 -> T139-T140 -> T070-T072 |
| Scope and no unrelated redesign | Every implementation task is limited to paths named in `plan.md`; T067-T073 enforce the boundary |

## Notes

- `[P]` is used only where file ownership and data dependencies permit parallel work after stated predecessors complete.
- Tests are intentionally placed before their implementation and must fail for the expected missing/incorrect behavior first.
- Every new geometry regression test must demonstrably fail against the audited implementation before its corresponding fix is implemented; T080 records this gate for Phase 4R and T107 records it for Phase 4S.
- Phase 4S thresholds come from `validation/morphology-thresholds.md` and are derived from the shader's configured erosion strength, noise amplitude, octave weights, and octave frequencies. Adjusting a threshold to accommodate an observed result, without a recorded model change, violates FR-026.
- **Renderer-wide gate**: the prior assumption that another local role-geometry iteration should follow a failed T098 is obsolete. T127-T129 must derive physical size and locate the first material discontinuity before T131 changes the responsible stage.
- **Ordering rule removed**: the previous rule that no performance work of any kind could begin before T099 no longer applies. Phase 4P runs after T130's frozen visual/trace baseline. Performance changes stay in separate tasks and commits from visual-correctness changes, and no Phase 4P change may alter the rendered result.
- T042 quality-mode plumbing is unblocked by accepted T098a and T135 and may proceed alongside
  Phase 4Q; only the final T139/T052 policy and T098b grading wait for the terminal shipping
  traversal/resolution decision.
- Phase 4Q keeps production `cloudDensity` authoritative, favors warp-coherent span elimination,
  and requires occupied -> empty -> occupied plus camera-inside validation. It does not permit toy
  density, generic inside fog, a first-hit shell, or a fixed shell thickness.
- Rank 2 descriptor micro-optimization, T143 geometric reach, T144 same-point collapse,
  T148/T151 interleaving, and T149 graded lighting/detail LOD are formally exhausted by measurement.
  Preserve their evidence and do not reopen them without new evidence.
- Phase 4S and Phase 4P preserve every already validated behavior: server-authoritative weather, forecast behavior, network packets, saved weather state, Simple Clouds ownership, legacy renderer fallback, rain placement, whiteout behavior, history invalidation semantics, and the candidate texture as a scheduling/index hint rather than authoritative geometry.
- Do not change packet registration, packet encoding, saved-data schemas, forecast orchestration, Simple Clouds managed systems, or unrelated cloud families.
- Do not perform Minecraft, RenderSystem, render-target, shader, buffer-upload, or OpenGL access from the async worker.
- Preserve unrelated working-tree changes and commit/review tasks in small logical groups.
