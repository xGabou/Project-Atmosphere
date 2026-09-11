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
  **T160 inputs (2026-09-03, commit `9b8ccc5`).** Both prerequisites are now settled: T152 and
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
- [X] T139 [PERFORMANCE] Integrate the T135 budget plus the terminal Phase 4Q evidence into five-mode quality policy: map
  budgets, LOD, raymarch, lighting, resolution, governor floors/ceilings, and history transitions
  to Low/Low 24/Medium/High/Ultra without disconnecting complete groups. Record the policy and
  transition rationale in `validation/us3-quality-lod.md` (depends on T045, T137) [FR-001,
  FR-009-FR-012; SC-005-SC-007, SC-021]
  **CURRENT ACCEPTED POLICY recorded** in `validation/us3-quality-lod.md`, deliberately separated
  from desired final quality. Shipped ladder verified from source and runtime, not documentation:
  Low/Low 24/Medium **0.125** (240x135), High **0.1875** (**360x203** - `Mth.ceil` of 202.5, not
  202), Ultra **0.250** (480x270, confirmed by 260 runtime cells). Render distance is **not**
  mode-dependent (single `CLOUD_RENDER_DISTANCE`, default 2000, 300-block floor). The governor is
  a scalar **step-budget** governor (4.2 ms, 40/400 frames, +/-0.125, clamped [0.5, 1.0]) that
  never changes resolution; T046's adaptive state machine is still unimplemented, as are T044's
  `adaptiveCloudQuality` and `nativeStormDetailDistance`. **Monotonicity audit passes** - no
  inversion on any dimension; intentional plateaus are light steps High=Ultra=6, scatter octaves
  Medium/High/Ultra=3, detail Low24/Medium/High=1, weather map 512, and Low alone disabling
  temporal. **Measured evidence is Ultra-only**: 260 cells at 0.250, and **zero** cells for
  Low/Low 24/Medium/High at their shipped scales - every non-Ultra cell on record is at the
  superseded pre-Rank-1 ladder and is not evidence for this one. **SC-006 is not met and is not
  claimed**: representative Ultra is 100.28 ms cloud p50 against an 8.0 ms budget (12.5x) and
  113.32 ms frame p95 against 16.7 ms (6.8x). Current Ultra is explicitly **not** the desired
  final visual target - it is 4.39x faster than the old path but visibly soft with 2.40 px
  silhouette displacement; a higher rung is to be tested only if the core-renderer experiment
  creates headroom, with 0.375/0.50 named as candidates and not promised. T152's temporal finding
  is recorded as policy: accumulation is **not** a safety net for aggressive sampling changes.
  T160's morphology findings are recorded as T098b requirements only; no profile value changed.
  **Policy defect found and corrected**: `AtmoCommonConfig.CloudRaymarchQuality` still carried the
  pre-Rank-1 ladder, and `ProjectAtmosphereQuickOptionsScreen` renders it, so the in-game quality
  label advertised resolutions **2x to 4x too high on every mode**; the config comment did the
  same. Enum and comment corrected to the shipped values. No rendering behaviour reads those
  fields, and recorded measurements were never contaminated - they use
  `effectiveResolutionScale`.. The final policy cannot bank until T158/T159 when the
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
- [X] T161 [PERFORMANCE] [US3] Productionize compile-time FINAL shader specialization without
  cherry-picking the experimental branch blindly: make FINAL frames select a separately compiled
  lean production program in `build.gradle`,
  `src/main/java/net/Gabou/projectatmosphere/client/render/shader/VolumetricCloudShaders.java`,
  `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/CoreCostDiagnosticProgram.java`,
  `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderer.java`,
  `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudDebugConfig.java`,
  and `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh`.
  Preserve density, morphology, rain, lighting, extinction, depth, temporal history, reconstruction,
  and FINAL output semantics; compile diagnostic/oracle/trace/legacy/alternate-output and
  experimental-control paths into explicitly selected specialized diagnostic programs instead of
  retaining dormant runtime-uniform branches in FINAL. FINAL must session-disable through the
  established native fallback policy on lean-program failure and must never silently select the
  bloated diagnostic program. Extend the controlled A/B and diagnostic-campaign harness in
  `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormT132AutoDriver.java`
  and record source linkage, image, history, T098a, diagnostic-campaign, and performance evidence
  in `specs/001-native-storm-rendering/validation/production-shader-specialization.md`
  (depends on T139; uses `e301494` as evidence, not an implementation to merge) [FR-001,
  FR-006-FR-013, FR-027, FR-030; SC-004-SC-007, SC-017, SC-020-SC-021]
  **[BANKED 2026-09-03, commit 6106c99]** FINAL frames link a separately generated `cloud_atmosphere_volume_final` program in
  which 19 diagnostic selectors are compile-time constants; the unmodified `cloud_atmosphere_volume`
  remains the diagnostic program and nothing was deleted from it. Same-fixture A/B on
  PLAY_VIS_NEAR / Ultra / 1920x1080 / 480x270 / 10 descriptors, 60 samples per arm:
  **0 changed pixels of 129,600, maximum error exactly 0.0, identical digest `d90e60c8881dec9b`**;
  cloud p50 **110.99 ms -> 36.07 ms (3.0775x)**, p95 119.88 -> 38.47 (3.1163x), frame p50 3.038x.
  That **retains the whole of `e301494`'s 2.984x** (103% of it). Selection is automatic and
  per frame: ordinary frames report `cloudProgram=lean_final`, and any active debug view, trace,
  oracle, optimization arm, legacy arm, stage/tier cut, ray trace or step budget falls through to
  the monolith, so **no campaign source changed**. Verified end to end: T121-T123 suite, T128
  trace, T098 production ray trace and the 29-shot T098 capture set (including the
  `LEGACY_HIT_DEPTH` and `LEGACY_PROMOTION` arms) all completed. Fallback proven by fault
  injection: a corrupted lean program session-disables with
  `LeanFinalProgramUnavailableException` and the monolith is bound **zero** times. A build-time
  gate (`T161 lean FINAL shader specializes and compiles`) asserts the substitution and compiles
  the generated program on a real GL context, so a generator that silently stopped specializing is
  a build failure rather than a silent 3x regression. `./gradlew check build` passes. Evidence in
  `validation/production-shader-specialization.md`. Implemented on
  `worktree-t098-production-ray-trace`, where T139/T160 and the whole harness live; `Forge-1.20.1`
  carries only 6 of the 19 uniform groups and none of the fixture/profiler, so the criteria could
  not have been produced there. Establishes execution-context specialization as a dominant cost
  class; the hardware mechanism (register pressure, occupancy, latency hiding) remains unproven
  without counters.
- [X] T140 [PERFORMANCE] Reprofile all five modes only after T161's productionized FINAL program, using T135's written targets in
  `specs/001-native-storm-rendering/validation/performance-baseline.md`, record per-mode pass/fail
  and representative visual checks, and prepare the evidence consumed by final T070/SC-006. This
  task does not waive final shipped visual regrading in T098b
  (depends on T052, T139, T161, and T159 if resolution recovery proceeds, otherwise the recorded
  T153--T157 stop task) [FR-010-FR-012, FR-027, FR-030; SC-005-SC-007, SC-017, SC-021]

**Checkpoint**: Performance work has a measured budget and ranked architecture before further
implementation, while visual polish remains independently active.

---

## Phase 5: User Story 3 - Scalable Quality Modes (Priority: P3)

**Goal**: Preserve five progressively increasing modes, bounded predictable storm LOD, stable adaptive degradation/recovery, and Ultra's target performance without disconnecting storm groups.

**Independent Test**: Run the same severe-weather route in Low, Low 24, Medium, High, and Ultra, then create sustained load and recovery. Every mode renders the connected storm, reports correct effective settings, respects floors/ceilings, and avoids oscillation.

### Tests for User Story 3

  **[ACCEPTED 2026-09-04, commit 9dabab8]** Diagnostic only; no ladder value, morphology or rendering semantic changed.
  Five-mode post-T161 baseline at PLAY_VIS_NEAR on the banked lean FINAL program, each mode at
  its own shipped scale, 120 frames per cell: Low 8.798/9.158, Low 24 11.371/12.194,
  Medium 12.101/21.470 (240x135); High 24.620/26.639 (360x203); Ultra 38.394/41.525 (480x270)
  cloud p50/p95 ms. **No mode meets SC-006; Ultra is 4.8x the 8 ms budget. SC-006 is recorded
  unmet, not rescoped.** A new screen-coverage campaign held the target at 480x270 and varied
  only camera aim: contributing coverage 13.9% / 23.2% / 30.6% / 0% / 0% cost
  37.12 / 46.39 / 39.94 / **3.59** / **2.77** ms. **The zero-cloud floor is 3.59 ms - 9.7% of a
  storm-heavy frame - and looking away still marches 92% as many primary steps while performing
  ZERO density evaluations.** A diagnostic whole-pixel rejection oracle (separate generated
  programs; FINAL never defines `PA_T140_ORACLE`) rendered bit-identically to lean FINAL at all
  five poses in both runs and produced **no gain: 0.97x-1.04x** at pixel, 8x8 and 16x16
  granularity. A conservative descriptor bound proved unable to reject anything (0.10% at one
  pose, 0% elsewhere) because at gameplay range the camera stands inside the storm footprint, so
  **projected conservative descriptor bounds would cull nothing where it matters**. Independent
  of the oracle, the floor caps perfect free whole-pixel rejection at **1.09x** (PLAY_VIS_NEAR),
  1.06x (PARTIAL/EDGE). **OPPORTUNITY 1 (whole-pixel/screen-space culling) is measured CLOSED
  and must not be built.** Cost follows neither target area (sublinear, exponent ~0.76 across the
  resolution ladder) nor covered pixels (EDGE has 2.2x the coverage of PLAY_VIS_NEAR and costs
  less); it follows density evaluations weighted by the light march they drive. **Dominant
  remaining class: density evaluation inside cloud-relevant rays (~92% of the frame).** Standout
  counter: 390.6M descriptor texture fetches for 2.50M density calls with ten resident
  descriptors (~156 texels per density evaluation). T153's 1.63x within-ray ceiling is kept
  separate and flagged as a **pre-T161 measurement that must be re-derived** on the lean program.
  The two opportunities act on disjoint pixels, so they add rather than multiply. **Recommended
  next: an attribution experiment isolating per-sample descriptor traversal cost inside a density
  call (T136-shaped), before committing to a spatial descriptor-binning architecture.** Ceiling
  for that class is bounded by the empty-march floor at ~10x. Do NOT retest 0.375 after culling
  (1.09x leaves it at 55.9 ms); retest 0.375 only after a measured >=1.6x, and 0.50 after ~2.7x.
  Evidence in `validation/performance-screen-coverage.md`. Also recorded there: five cells of
  `ultra-recovery-primary-run.out.log` are contaminated (the sweep verifies pose visibility once
  and the fixture stopped contributing after the 0.500 arm); the shipped ladder is unaffected
  because it draws only from valid cells.
- [X] T162 [PERFORMANCE] [US3] Attribute the remaining post-T161 cloud GPU time to a cost class
  before any further architecture is chosen. Diagnostic only: change no ladder value, no
  morphology, and no FINAL rendering semantic. Build a compile-time attribution ladder of
  separately linked, lean-specialized programs in `build.gradle` and
  `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` -
  a fixed-work rung per cost class (control floor, candidate traversal, descriptor payload fetch,
  shape/profile/SDF, weather plus base noise and erosion, detail octaves, precipitation) plus
  production-context arms with lighting and with rain compiled out - so no runtime diagnostic
  branch can distort the timing, which is the distortion T161 measured. Register and select them
  through `CoreCostDiagnosticProgram.java` and `VolumetricCloudShaders.java`; drive the campaign
  from `StormT132AutoDriver.java` with a descriptor-count scaling sweep gated by a diagnostic
  `descriptorCountLimit` in `VolumetricCloudDebugConfig.java`. Every timing cell must be
  qualified independently - descriptor count and T150 geometric visibility re-verified
  immediately before and immediately after the cell, and the cell discarded outright if the
  fixture moved - because the Ultra recovery sweep qualified its pose once and silently kept
  measuring after the storm stopped contributing. Record the ladder, the deltas, the
  descriptor-count scaling, the lighting and rain shares, and a ranked next-architecture
  recommendation in `specs/001-native-storm-rendering/validation/performance-cost-attribution.md`
  (depends on T161, T140; T153's 1.63x is pre-T161 and may not be quoted as current)
  [FR-010-FR-012, FR-027, FR-030; SC-006-SC-007, SC-017, SC-021]
  **[ACCEPTED 2026-09-04, commit 330e786]** Diagnostic only; no ladder value,
  morphology or FINAL semantic changed.
  Fresh Ultra/PLAY_VIS_NEAR/480x270 baseline 33.62-37.32 ms cloud p50 across three runs.
  **CASE C: descriptor traversal is NOT dominant.** The fixed-work ladder (64 fixed samples per
  fragment, identical control flow, deltas reproducible within 3% over three runs) attributes a
  density evaluation as control floor 0.3%, candidate traversal 11.6%, descriptor payload fetch
  4.1%, shape/profile/SDF 20.7%, weather+base noise+erosion 8.1%, detail octaves 0.6%,
  **precipitation 54.5%**. Descriptor work end to end is **15.7%** of a density call - the T140
  fetch-count clue pointed at the wrong thing, because 186 fetches per density call is 3.58
  shape calls x 5.71 lobes x 9.12 texels of re-entry, not one sample touching many descriptors,
  and T122 already avoids 185.2M of 551M fetches. **The largest single cost is code that never
  runs**: `cloudDensity` takes an `includePrecipitation` parameter that **all 19 production call
  sites pass false** (rain renders through a separate `rainShaftDensityOverSegment` integrator),
  yet compiling that branch out is measured at **1.486x-1.581x (mean ~1.53x, ~35% of the frame)**
  and is **bit-identical - 0 changed pixels of 129,600, maxAbsRGBA exactly 0.0** - while the
  lighting arm captured the same way changes 18,062 pixels, which is what makes the zero
  credible. This is T161 one level down: a dormant path inside a single function rather than
  across programs. Lighting is second at **1.391x-1.476x (~30%)** and is real executed work
  (38.8% of all density calls are light-march probes), so it can be reduced but not deleted;
  **the historical T136 ~1.10x representative lighting share no longer holds** post-T161.
  Descriptor-count scaling is flat from 1 to 6 descriptors then steps: 10x the descriptors costs
  1.48x on the fetch arm and 3.15x on the shape arm - strongly sublinear, so perfect binning is
  worth only ~25% of a density call and ranks **third**. **Recommended next: compile the
  unreachable precipitation path out of `cloudDensity`** (preserving the capability behind a
  compile-time variant rather than deleting it, per the T161 pattern) - trivial complexity, zero
  visual risk, ~1.53x. **0.375 should be retested immediately after it** (60.91/1.53 = ~39.8 ms,
  about what 0.25 costs today); 0.50 should not (~64.7 ms) unless rank 1 and lighting stack.
  Composition of the two was **not** measured - a combined arm was dropped to save shader compile
  time, and building it is a cheap follow-up. T153 re-derivation was **not attempted** and is left
  as an explicit follow-up; its 1.63x remains stale. SC-006 stays 8 ms and unmet; rank 1 alone
  would move Ultra from ~4.4x to ~2.9x the budget. Evidence hygiene: every cell qualified at both
  ends (44 checks over 22 cells), **0 rejected**. Two harness defects were found and fixed before
  any number was trusted - non-adjacent captures that let the fixture drift, and a capture that
  ran after the per-arm program re-pin so every frame silently rendered the anchor; the lighting
  arm showing a real difference is now the harness self-check. Evidence in
  `validation/performance-cost-attribution.md`.
- [X] T163 [PERFORMANCE] [US3] Productionize the T162 dead-precipitation finding: make the
  unreachable precipitation path compile-time absent from the lean FINAL cloud program while
  every diagnostic program that genuinely needs precipitation-capable `cloudDensity` keeps it.
  Guard the two precipitation-only expressions in
  `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` behind
  `PA_PRECIPITATION_ABSENT`, define it for FINAL and for the T140 oracle family that must stay
  bit-identical to FINAL in `build.gradle`, and keep a `cloud_atmosphere_volume_t163_withrain`
  baseline so old and new FINAL can be measured back to back rather than against a remembered
  number. Extend the existing T161 generation architecture rather than adding a second mechanism;
  one source of truth for the cloud equations must remain. Add a build invariant in
  `StormVolumetricGeometrySandbox.java` asserting both directions - FINAL specialized, capability
  retained where required - because restoring the dead path to FINAL would change no pixel and
  pass every image check while silently losing ~1.5x. Drive the old/new comparison from
  `StormT132AutoDriver.java` with adjacent captures, the lighting arm as the known-non-identical
  harness self-check, and every cell qualified immediately before and after. Require exact
  equivalence, not epsilon. Re-measure the 0.25/0.375/0.50 resolution frontier afterwards without
  changing the shipping scale, and record everything in
  `specs/001-native-storm-rendering/validation/performance-precipitation-specialization.md`
  (depends on T162, T161) [FR-010-FR-012, FR-027, FR-030; SC-006-SC-007, SC-017, SC-021]
  **[BANKED 2026-09-04, commit ad12a63]** FINAL no longer compiles the precipitation branch that
  no production call site can reach. Both precipitation-only expressions in `cloudDensity` are
  guarded by `PA_PRECIPITATION_ABSENT`, defined for FINAL and the T140 oracle family and withheld
  from the monolith, the rain-capable T162 arm and a new `t163_withrain` baseline kept so old and
  new FINAL are measured back to back. Nothing was deleted from the source; this extends the T161
  generator, so one shader file is still the single source of truth. **Nine within-run pairs over
  three poses and three internal resolutions: mean p50 speedup 1.517x, range 1.458x-1.589x**
  (PLAY_VIS_NEAR 0.25 37.208 -> 24.517; 0.375 63.689 -> 42.660; 0.50 99.867 -> 64.799;
  PLAY_VIS_MID 0.25 25.468 -> 16.697; SIDE 0.25 34.590 -> 23.127 ms), retaining essentially all of
  the 1.486x-1.581x T162 predicted. **Image is exactly unchanged: 0 changed pixels of 129,600 and
  maximum error exactly 0.0 at all three poses**, with the lighting arm differing at all three as
  the harness self-check - the check that would have caught the T162 capture defects. Rain is
  untouched: it renders through `rainShaftDensityOverSegment`, called directly by the march, and
  the fixture is rain-bearing, so any lost shaft, intensity or T145 locality would have shown as
  changed pixels. Production counters confirm **0 rain density calls**. A new build invariant,
  `T163 FINAL is specialized against the dead precipitation path`, asserts both directions and is
  the only thing that would catch a regression, because restoring the dead path changes no pixel
  and passes every image check while silently losing ~1.5x. Resolution frontier re-measured on
  fresh qualified cells without changing the shipping scale: **0.25 = 24.517, 0.375 = 42.660,
  0.50 = 64.799 ms cloud p50**. **0.375 is materially closer but not yet shippable** - it now sits
  roughly where 0.25 sat before (1.15x the old 0.25, down from 1.71x), though still 1.74x the
  current 0.25 and 5.3x SC-006. **0.50 remains impractical** at 2.64x the current 0.25. SC-006
  stays 8 ms, unmet and unrescoped; Ultra 0.25 moves from **4.65x to 3.06x** the budget, closing
  the absolute gap from 29.2 to 16.5 ms - the largest single step since T161, at no image cost.
  Evidence hygiene: 42 fixture qualifications over 18 timing cells and 3 image sequences,
  **0 rejected**; no other Minecraft or Java benchmark instance running at launch. Evidence in
  `validation/performance-precipitation-specialization.md`.
- [X] T166 [PERFORMANCE] [US3] Re-measure the production cloud pipeline at FAR and SIDE on the
  post-T163 program, re-derive the cost attribution against the current shader rather than
  quoting T162's table, and evaluate the seven PMWeather-inspired ideas against those fresh
  measurements. Build every arm as a separately generated compile-time program carrying
  `PA_PRECIPITATION_ABSENT`, because the T162 arms predate T163 and still compile the dead
  precipitation branch - measuring one of them against today's FINAL would charge the rain-carry
  cost T163 already banked to whatever else the arm changed. Re-derive the T153 empty-space
  oracle on lean programs so its ratio is comparable to today's renderer. Measure composition
  with a combined arm rather than multiplying speedups. Report image quality, not just timing,
  for every arm that deliberately changes the picture. Productionize nothing; keep every arm
  default-off and compile-time absent from FINAL, and assert that in a build gate. Record in
  `specs/001-native-storm-rendering/validation/performance-pmweather-evaluation.md`
  (depends on T163, T162, T161, T153) [FR-010-FR-012, FR-027, FR-030; SC-006-SC-007, SC-021]
  **[BANKED 2026-09-04]** **Fresh baseline, Ultra 0.25, 480x270 of 1920x1080: SIDE raymarch
  22.0826/24.4685 ms p50/p95, FAR 14.5388/15.5116, PLAY_VIS_NEAR 19.5092/21.0094**; the
  reconstruction and composite are one draw at 0.085-0.087 ms p50, 0.4% of the pipeline, and are
  not separately timeable. The ~24.5 ms anchor in circulation was T163's **PLAY_VIS_NEAR** cell;
  SIDE reproduces T163's SIDE figure within 4.5% across three campaigns and **FAR had never been
  measured post-T163 at all**. SIDE is 2.77x over the 8 ms budget, FAR 1.83x. **The attribution
  inverts T162's headline**: with precipitation gone, the fixed-work ladder puts candidate
  traversal plus descriptor payload plus shape/profile/SDF at **69.6% (FAR), 79.6% (SIDE), 79.8%
  (PLAY_VIS_NEAR) of a density call**, and T162's non-precipitation rows renormalise onto the
  fresh ladder within about one point - two independent campaigns agreeing, and confirmation that
  T163 removed exactly the precipitation class. Lighting is 20.1% of the SIDE frame (4.438 ms,
  1.252x), detail+erosion 11.1% (2.451 ms). **PMWeather verdicts: A distance-dependent step
  HIGH VALUE (1.643x-2.144x, silhouette IoU 0.993-0.999, but thin-material retention 0.80-0.87);
  B aggressive empty-space REJECT (1.002x-1.064x - 87-97% of ray steps already resolve empty, and
  PA's existing coarse tier, SDF safe-advance and 16-probe empty-span scan are strictly more
  aggressive than PMWeather's); C cheaper lighting MARGINAL (1.012x-1.047x - PA already omits
  detail on light taps 2+ and its noise is one packed fetch with no octave loop to shorten; only
  cutting 6 taps to 2 pays, at 1.121x); D distance density LOD PROMISING (1.084x-1.203x at thin
  retention 1.0000); E early termination PROMISING (1.145x-1.216x at IoU 1.0000 and SSIM
  0.983-0.991); F scene-depth march ALREADY SOLVED and worth 3.11 ms at PLAY_VIS_NEAR - `t1` is
  clipped before the loop, so the work is never scheduled; G spatial blur REJECT - the composite
  is 0.087 ms.** **Refreshed T153 ceiling: 1.998x mean combined, slightly above the historical
  1.836x on the same three poses, so the opportunity still exists - but the distance-step arm
  already reaches or beats it (1.733x against the oracle's 1.375x at SIDE) without any occupancy
  structure**, which closes the category on grounds the old campaign could not see. **Composition
  is measured, not multiplied: the combined arm returns 87-97% of the product of its parts**, and
  at SIDE it adds essentially nothing over distance stepping alone. Cost scales as
  pixels^0.57-0.60. **Path to 8 ms: FAR already reaches 5.97 ms with the measured stack and
  PLAY_VIS_NEAR 10.32 ms, but SIDE reaches only 12.87 ms and 8 ms is NOT attainable from this
  list** - because 79.6% of a SIDE density call is descriptor geometry and no PMWeather idea
  touches it (PMWeather's storms are analytic; it has no descriptor system). **Recommended next
  task: a diagnostic descriptor-binning arm, not a PMWeather port**, plus a graded
  distance-step curve to recover the thin material the tested curve loses. Four campaign runs
  were needed: **three existing guards - descriptor count, the T150 visibility verdict, and the
  autorun's topology-generation maturity check - all passed a pose whose camera had not finished
  teleporting**, so its first cells timed the previous camera, which for the first pose stands
  inside the storm and takes the in-cloud fast path. `T166_ARRIVAL pose=FAR reached after 69
  extra frames` isolated it; the drift control added for this campaign is what caught it
  (`ratio=3.0885 verdict=DRIFTED`). Primary run: 84 cells, **0 rejected**, all three drift
  controls stable, 18 image comparisons. FINAL is textually untouched - the shader diff is 129
  insertions and 0 deletions. Evidence in `validation/performance-pmweather-evaluation.md`.
- [X] T167 [PERFORMANCE] [US3] Refine the two arms T166 left open and take the cheap win it
  identified: graded replacements for the over-aggressive distance step, a nearest-K cap on
  per-sample descriptor owners, and a safer early-termination threshold. Measure all three
  independently before combining anything, and combine only what passes. Build every arm as a
  compile-time program carrying `PA_PRECIPITATION_ABSENT`, keep the march conservative under the
  K cap by having skipped descriptors still feed `groupMinClearance`, and report image quality -
  not only timing - for every arm, because the descriptor question cannot be decided on speed.
  Productionize nothing; assert the arms stay out of FINAL in a build gate. Record in
  `specs/001-native-storm-rendering/validation/performance-descriptor-and-step-refinement.md`
  (depends on T166, T163, T162, T121) [FR-010-FR-012, FR-027, FR-030; SC-006-SC-007, SC-021]
  **[BANKED 2026-09-04, branch `experiment/cloud-descriptor-k`, not merged]**
  **Descriptor nearest-K is REJECTED - CASE D.** No K is faster than evaluating every owner at
  any pose: K1 1.005x/0.892x/0.940x, K2 0.936/0.870/0.916, K3 0.916/0.844/0.877, K4
  0.851/0.809/0.818, K6 0.716/0.719/0.762 (FAR/SIDE/PVN). The ordering runs the wrong way -
  **K6 is the slowest arm in the campaign and also the closest to FULL on every quality metric**,
  which is the signature of pure overhead. Quality fails independently: the new seam index,
  added specifically to catch collapse of the ordered smooth union, rises monotonically as owners
  are dropped and reaches **0.0379 at K1/FAR against 0.0023 for the aggressive step arm**, with
  cloud SSIM 0.653 and thin retention 0.403 - exactly the failure `directStormGroupField` already
  documents. **The mechanism corrects a reading of T166**: production already applies the exact
  lobe SDF to 88.1% of visited lobes at SIDE (T121 rejects only 21.8%), so K1 skipped ~86% of all
  exact SDF evaluations and was still 8-11% slower. **The ladder's 48.6% "shape/profile/SDF/union"
  class is per-descriptor traversal - four texel fetches, role decode, ownership ellipse, edge
  softness, conservative bound - not the SDF equation.** A cap inside the loop cannot reach it.
  **Do not build nearest-K binning**; the only version still open is one that reduces which
  descriptors a sample *iterates*, upstream in the candidate map. **Graded curves: PARTIAL
  SUCCESS.** Curve A (late ramp) recovers thin retention from the aggressive arm's 0.712-0.867 to
  **0.931-0.969** at silhouette IoU 0.998-0.999 and the lowest seam index measured, for
  1.055x-1.477x. **Curve D (footprint) is rejected as degenerate**: its unity-pixel constant makes
  it saturate before the storm begins, so it renders the same picture as the aggressive arm -
  metrics agree to the third decimal, exactly at PLAY_VIS_NEAR - while costing **+26.9% at FAR**,
  which measures one extra division in the march loop at up to a quarter of the frame. **The
  curves are pose-dependent because `t / MaxRenderDistance` couples them to the render distance
  rather than the material**; a pose-invariant curve needs a footprint variable, which makes
  fixing curve D's constant the most promising follow-up. **Early termination 0.045 ACCEPTED**:
  1.081x/1.118x/1.099x at silhouette IoU and thin retention of exactly 1.0000. **Combined SAFE
  stack (curve A + 0.045): FAR 10.371 ms 1.360x, SIDE 19.692 ms 1.166x, PVN 16.942 ms 1.156x** at
  thin retention 0.931-0.969; composition returns 85-100% of the product, so speedups still must
  not be multiplied. **Targets: FAR <=8 ms NOT met (best 8.447 ms, curve B), SIDE <=10 ms NOT met
  (best 13.466 ms), SIDE <=8 ms NOT met.** Two runs; run 1's FAR was invalidated because the T166
  arrival guard is armed by `t166PoseTargetValid = t166Run` and wiring T167 through the rest of
  the machinery did not extend that one assignment - **a guard that exists, works, and is simply
  not switched on**. The drift control caught it again (`ratio=3.1127 DRIFTED`). Final run: 51
  cells, **0 rejected**, all three drift controls stable, 42 image comparisons. Evidence in
  `validation/performance-descriptor-and-step-refinement.md`.
- [X] T168 [PERFORMANCE] [US3] Build a pose-invariant footprint step LOD from the live
  projection and audit the descriptor candidate/group walk upstream of the loop prologue.
  **Task A succeeds.** Deriving the criterion algebraically first shows the growth is
  **linear in `t`** - `growth(t) = (2P / (P11 * H * fineStep)) * t` - so the whole reciprocal
  hoists to one per-fragment constant and the inner loop is one multiply plus one clamp,
  against the per-step division that cost T167 up to 27% of a frame. Best banked arm P=0.75:
  **1.468x FAR, 1.838x SIDE**, degradation monotone in P at every pose (thin retention
  0.84-0.96 vs nearest-K's 0.38-0.49). **Task B closes the upstream-binning line (CASE C).**
  The walk visits **exactly 10.000 lobes per group entered** (8.000 at cap 8) - groups are
  entered whole, so a candidate structure cannot filter below group granularity - and only
  38-48% of visited lobes change the union answer, but that majority is *sample-dependent*
  and no static structure can remove it. Measured ceiling for removing 2 of 10 descriptors
  upstream: 1.13x FAR / 1.29x SIDE, already rendering a different image; caps 1-6 are
  degenerate (`cloudDensityCalls=0`, empty sky). Termination 0.045 reconfirmed at ~1.11x with
  IoU 1.0000 and thin retention 1.0000. **No target met**: best trustworthy FAR 9.4566 ms
  (goal 8), best SIDE 12.0996 ms (goals 10 and 8). 45 cells, **0 rejected**; FAR and SIDE
  drift controls stable, **PLAY_VIS_NEAR drifted 1.0923 and is reported but not banked**.
  Two defects recorded rather than hidden: a static-initializer ordering bug
  (`T168_ARMS` read `T168_DESCRIPTOR_CAPS` before it was declared) killed two launches at
  class init - invisible to the build gate, which sees only compile-time invariants - and
  `descriptorCandidateRanks` reads 0 everywhere because the counter was emitted but never
  incremented, leaving the "8 candidate ranks" figure a static reading. Evidence in
  `validation/performance-footprint-lod-and-traversal-audit.md`.
- [X] T169 [PERFORMANCE] [US3] Reduce lighting and detail cost, and bound what either can
  return. **The task's premise was wrong.** Conservative tap reduction buys nothing: 6 -> 5 and
  6 -> 4 return 0.5-0.8% at both poses, and at SIDE they are **bit-identical to the anchor**
  (`maxAbsRGBA=0.0`, 0 changed pixels) - direct proof that the cone early-out already terminates
  before tap 5, so capping there is a no-op. Only the early-out floor returns anything (3.3-3.5%).
  Ceilings: lighting **16.8% FAR / 19.5% SIDE**, detail **17.5% FAR / 15.2% SIDE**. The
  footprint-gated detail cut captures 84.7% of its ceiling at FAR but 21.2% at SIDE, which is the
  mechanism working - close range genuinely resolves the detail. Stacks: FAR 5.3371 ms
  (**<=8 ms met, P50 and P95**), SIDE 10.7305 ms. **SIDE misses both budgets, and the ceilings
  prove lighting and detail cannot close it**: with both at zero SIDE would still cost ~14.7 ms.
  36 cells, **0 rejected**, both drift controls stable. Three defects recorded: two driver wiring
  gaps left the campaign unreachable for three launches (`lightingDetailRunRequested()` missing
  from `performanceRunRequested()`; `t169Run` missing from `activeEvaluationArms()`), and
  `T166Arm.label()` omitted the optimization mode, so the five T149 arms collided with the anchor
  and **were never measured**. Evidence in `validation/performance-lighting-and-detail.md`.
- [X] T170 [PERFORMANCE] [US3] Build a harness wiring invariant, then attribute the primary march
  and its per-sample descriptor cost. **Task 0 done and proven**: `StormCampaignRegistry` is the
  single declaration of all 15 campaigns, `performanceRunRequested()` is now derived from it, and
  `programArmCampaign()` replaces a four-way chain that had been duplicated at six sites. The
  sandbox invariant checks marker/predicate/flag/arm-table/pose-guard linkage and **rejects five
  deliberately mutated copies of the real driver source**, one per historical defect
  (`T170_WIRING campaigns=15|armMatrices=11|violations=0`,
  `T170_WIRING_NEGATIVE mutations=5|allDetected=true`). **Task 1 is not answerable and its premise
  is false**: `fpmax5/6/8` render byte-identical images in both runs, so the clamp stops binding
  above ~5 and there is no headroom to release; and five SIDE arms rendering the *same image* span
  **13% in measured time**, while run 1 and run 2 invert the FAR ordering entirely (`fpmax4`
  2.591x then 1.533x; `fpmax6` 1.537x then 2.884x). No clamp conclusion is banked. **Tasks 2/3/4
  succeed.** Descriptor texture fetches are **not** the bottleneck - 2148 fetches per pixel at
  SIDE, and removing essentially all of them returns 1.003x/0.986x. The smooth union returns
  nothing (1.011x). What dominates is per-sample arithmetic, and a third of it is **provably
  invariant**: `stormEdgeWidthBlocksFromData` takes no sample position yet is evaluated 259 times
  per pixel at SIDE (1.655x), as are the ownership ellipse's rotated extents (1.035x); combined
  hoist ceiling **1.731x SIDE**. **Task 5: C, at two scopes** - SIDE takes only 1.08x the primary
  steps of FAR but evaluates density on 81% of them against 15%, while the per-call profile is
  nearly identical (11.85 vs 13.10 descriptor evaluations per density call). Lobes per group
  entered is 10.000, reconfirming T168. **Task 6: no shippable improvement on T169.** Every
  shippable stack sits at 12.06-12.64 ms SIDE, inside the noise floor. The ceiling arm
  `t170_stack_hoist` reaches **SIDE 7.3052 / 7.7558 and FAR 4.0724 / 4.2885 - the first
  configuration in this line to clear 8 ms at the binding pose** - but it is a ceiling, not a
  candidate (constant edge width, meanAbs 1.606e-02 vs the stack's 2.210e-03). **CASE C.**
  Recommended production candidate remains `t169_stack_fast`; recommended next architecture is a
  per-frame descriptor precompute of edge width and ownership extents, which is image-identical
  and trades arithmetic for a fetch that this campaign showed is free. 64 cells across two runs,
  **0 rejected**, all four drift controls stable. Evidence in
  `validation/performance-primary-march.md`.
- [X] T171 [PERFORMANCE] [US3] Explain T170's 13% spread across identical-output arms and
  establish a measurement floor before any architecture change. **The audit found two real
  sampler defects before a single frame was measured**: `observeFrame()` recorded the async GPU
  timer once per frame with no freshness test, so a cell could re-record one GPU interval many
  times, and `VolumetricCloudRenderer.lastGpuTimingSample()` - written for exactly that and
  documented as such - **was called from nowhere**, a fourth instance of the omission class the
  T170 registry invariant catches, in a file it does not cover. Both fixed; **neither was the
  cause** (`duplicatesRejected=0` in all 140 cell records). **The measurement floor**: same GL
  program back-to-back CV **0.50%**; separately linked byte-identical programs differ by at most
  **1.07%**, inside the same-program spread, so there is **no program-identity effect**; pooled
  across 19 identical-output cells CV **0.97% SIDE / 1.23% FAR**. Ordering, warm-up and sample
  count are all excluded - 60/120/240 frames do not converge, so more frames cannot help.
  GPU telemetry rules out the leading hardware hypothesis: an RTX 4070 **Laptop** GPU with a
  210-3105 MHz range held **2340 MHz pinned**, 44-63 C, no throttle, including through the
  anomalous cell. **What remains is occasional bimodality, not wide distributions**: 2 cells of
  70 (2.9%) measured an internally tight state disjoint from their own twin - SIDE `fpmax5`
  12.5880 vs 11.0971 (13.4%, T170's spread reproduced exactly, and it is a *cell* property not an
  arm property) and FAR `desc_nosdf` 12.7887 vs 8.1859 (56.2%, no frame of one overlapping the
  other). Cause not identified; eleven hypotheses excluded. **Threshold adopted: minimum
  measurable speedup 3%, every banked arm measured at least twice, repeats disagreeing by more
  than 3% rejected rather than averaged, anything else BELOW MEASUREMENT FLOOR.** **Task 7
  re-measurement**: all six T170 points re-measured with both repeats agreeing within 1.31%.
  `t169_stack_fast` 1.774x, `desc_nosdf` 1.778x, `desc_constedge` 1.395x, `desc_hoist` **1.421x**
  - the descriptor findings survive, with the hoist smaller than T170 claimed. **But
  `t170_stack_hoist` re-measures at 8.7532 ms SIDE against T170's 7.3052 - 19.8% higher and far
  outside the floor - so SIDE <=10 ms is met and SIDE <=8 ms is NOT, and T170's claim to be the
  first configuration in the line to clear 8 ms at the binding pose is withdrawn.** 70 cells,
  **0 rejected**. Descriptor-invariant precompute authorized as the next architecture with its
  target revised to the 10 ms goal. Evidence in `validation/performance-harness-stability.md`.
- [X] T172 [PERFORMANCE] [US3] Implement the real descriptor-invariant precompute authorized by
  T170/T171 and measure what the architecture actually retains. **The implementation succeeds
  completely and is bit-exact.** Edge width and the ownership radii are computed once when the
  descriptor is built and carried in texel 3's channels, which the shader already fetched for the
  role: **16 bytes added per descriptor, zero additional fetches per density call.** All three
  isolated precompute arms render images **bit-identical** to the anchor at both poses
  (`maxAbsRGBA=0.0`, 0 changed pixels) - no SSIM or IoU is reported because there is no
  difference to characterise. Density-call counts are unchanged (primary steps 3,907,150 against
  T170's 3,907,935), confirming the gain is cost per call. **But it retains only 19.3% of the
  T170 ceiling (36.8% on the stack), far under the 80% Task 10 threshold, and the cause is a
  correction to T170 rather than a shortfall here.** T170's `desc_constedge`/`desc_hoist`
  substituted `STORM_MIN_EDGE_BLOCKS`, the *floor* of the edge-width distribution; a smaller
  `lobeSoftness` makes the T121 conservative rejection easier to satisfy, so those arms culled far
  more descriptors before the exact SDF. They measured cheaper arithmetic **plus a much more
  aggressive cull**, and the cull was ~4x the larger term. The proof is that the real precompute
  is bit-identical - it provably cannot change the cull - and captures 19%, while the ceiling
  changes the image an order of magnitude more than the shipped stack's own error and captured
  the rest. **T170's "1.421x image-identical hoist" is withdrawn**, the second T170 headline to
  fall to a proper control after T171 withdrew the 7.31 ms figure. **Banked at FAR** (8 of 9
  pairs accepted, all within 1.45%): anchor 13.0883, edge precompute **1.0496x**, ownership alone
  1.0291x = **BELOW MEASUREMENT FLOOR**, combined **1.0952x**, and on the shipped stack
  `t169_stack_fast` 5.0785 -> `t172_stack_pre` 4.7698 = **1.0647x**. **SIDE produced no usable
  measurement**: 8 of 9 pairs REJECTED with disagreements to 56%, T171's unexplained bimodal mode
  taking most of a block, with the GPU pinned at 2340 MHz throughout - and the one accepted SIDE
  pair is still unbankable because its anchor was rejected. **Decision: productionize the combined
  precompute** (free, bit-exact, 6% on the shipping stack), but not in this commit - the invariant
  asserts FINAL does not define it, and flipping that belongs with a campaign that covers SIDE.
  **New lever identified**: the ceilings accidentally showed that loosening the T121 rejection is
  worth ~4x what removing the arithmetic is, so the recommended next work is a **tighter,
  still-conservative lower bound** - image-preserving by construction, as T141's box bound already
  is. Evidence in `validation/performance-descriptor-precompute.md`.
- [X] T173 [PERFORMANCE] [US3] Explain the SIDE bimodality, then audit and test the T121
  conservative bound. **SIDE was never unstable - the harness protocol was.** Twenty identical
  cells with one program and no arm switching agree to **CV 0.57%**, 19/19 consecutive pairs
  accepted, with the per-cell state byte-constant (`descriptorSignature=08328807b4eb6fed`, camera,
  targets, scale, governor, history all identical; only `gameTime` advancing) and workload
  counters varying **<0.02%**. The same descriptor signature appears in T172 and is constant
  across its transition, so the fixture is deterministic across runs and never evolved. T172's
  apparent 31% workload drop is explained: its anchor's **timing** moved 6.2% while its reported
  **counters** moved 31%, which cannot both describe one scene - the counter capture is itself
  contaminated by arm switching, so neither figure measured what it claimed. GPU state was not
  re-investigated, deliberately: no GPU execution state can change how many density calls a
  fragment program makes. **Rule adopted (not a widened floor)**: every arm is bracketed by anchor
  cells and divided by their mean; an arm whose anchors disagree >3% is `REJECTED_anchor_drift`.
  Switching still doubles anchor variance (CV 0.57% -> 1.22%) and broke 2 of 10 pairs, so the
  strict verdict stays `SIDE_NOT_MEASURABLE`, but **both rejections landed on arms whose other
  repeat was accepted**, so every program carries a trustworthy number and the accepted repeats
  agree to 0.5-2.6%. **T121 audit**: FINAL bakes `PaDiagnosticOptimizationMode` to 0, so
  `paT141BoxBound()` is compile-time false and **the shipped renderer has always used the
  vertical-only bound** - T141's tighter `stormLobeDistanceLowerBound` is dead code in production.
  Current bound rejects **22.6% of visited lobes** (78.7/pixel) against 302 descriptor
  evaluations/pixel at SIDE. **The tighter bound is a net loss**: 0.9540 / 0.9287, i.e. **4.6-7.1%
  slower**, both repeats accepted. Its horizontal term costs a `length()` and a division per
  descriptor per sample but only binds when a sample lies outside a lobe's horizontal extent, and
  at SIDE the camera looks through the storm. Correctness holds - **0 changed pixels**,
  `maxAbsRGBA=1.53e-05`, 32x below the storage epsilon, a float-ordering shift in
  `groupMinClearance` rather than a false cull (which would give large localised errors, not
  1.5e-05 everywhere). **First trustworthy SIDE stack numbers**: anchor 23.50, `t169_stack_fast`
  14.11/14.33, **`t172_stack_pre` 12.59/12.51 (1.878x)**, `t173_stack_boxbound_pre` 13.85/13.70.
  **The T172 precompute is worth 1.133x at SIDE**, double its 1.065x at FAR, as expected from
  SIDE's 5.9x density calls. SIDE <=10 ms NOT met (best 12.51, gap 1.25x); FAR <=8 ms met.
  **Decisions: productionize the T172 precompute (yes, with more confidence than T172 had);
  productionize the tighter T121 bound (no).** Next lever is groups entered per sample - SIDE
  visits 348 lobes across 23.8 density calls, 14.6 per call against a group size of 10 - which is
  a different question from the below-group binning T168 closed. Evidence in
  `validation/performance-side-stability-and-t121-bound.md`.
- [X] T174 [PERFORMANCE] [US3] Productionize the T172 precompute, then price the group-entry
  line. **Productionization banked separately as `4b34b12`**: FINAL and the four T140 oracle
  variants now define `PA_ARM_DESC_PRECOMPUTE`, and because the precompute and the old derivation
  are `#if`/`#else` branches the recomputation is **not compiled into FINAL** rather than left
  live beside it. The T172 gate is inverted rather than deleted - FINAL must now define it, must
  not define either isolated half, and every oracle variant must carry it. **Proof of consumption
  is runtime, not a define check**: `t174_no_precompute` is FINAL minus the precompute, renders
  **bit-identically** (`maxAbsRGBA=0.0`, 0 changed pixels, both poses) and is measurably slower -
  **1.079-1.104x SIDE, 1.090-1.096x FAR**. Anchors moved SIDE 23.50 -> ~20.6 and FAR 13.09 ->
  ~12.4. **Group entry passes its gate and still cannot close SIDE.** `first_group_only` -
  skipping the candidate scan, ten-descriptor walk, bounds, exact SDFs and union for every group
  beyond the first - is worth **1.136x SIDE / 1.125x FAR**, above the 1.10x gate but below the
  1.255x the 10 ms target needs. **It is exact**: bit-identical to the anchor, because on this
  fixture groups beyond the first are entered ~44% of the time and never change the result.
  **`group2_no_sdf` returns 0.99x** - the exact SDFs inside groups 2+ cost nothing, T121 already
  culls them, so the 1.136x is pure walk overhead on ~4.4 lobes per density call that contribute
  nothing. **Task 3 audit: there is no group metadata to reject on** - before the descriptor loop
  the shader has only a witness index, a group slot and a 2D XZ candidate tile, so every candidate
  bound would have to be invented and maintained. Counters: SIDE 1.443 groups and 14.43 lobes per
  density call, **10.000 lobes per group entered** (T168 holds), 18.5% T121-rejected, 25.37 density
  calls/pixel against FAR's 4.16 on nearly equal step counts (30.83 vs 28.61) - SIDE evaluates
  density on **82% of primary steps**, FAR on 15%. The 1/2/3+ histogram was **not obtained** and is
  reported as a gap; the ceiling answers the decision without it. **Stack REJECTED**: repeats
  disagreed 15.56% SIDE and 51.96% FAR, so no stack number is banked, and r2 was not cherry-picked
  despite matching T173/T172 to within 0.2%. **Verdict: the group-entry line is real, exact and
  insufficient** - a perfect filter takes 12.5 -> ~11.0 ms and still misses 10 ms. Closed for the
  target; recorded as a 1.136x ceiling. **Remaining dominant cost is the number of density calls at
  SIDE, not per-call cost**, which T170/T172/T173/T174 have now bounded from every side. Next
  recommended line is empty-space skipping / clearance quality, possibly at group granularity where
  the missing group metadata would actually pay. Evidence in
  `validation/performance-group-entry.md`.
- [X] T175 [PERFORMANCE] [US3] Establish whether the primary march's density calls are necessary.
  **Both oracles are at the measurement floor, and the framing three campaigns have used was
  wrong.** `density_every2` halves primary body density calls and returns **1.0146 / 0.9977 SIDE**;
  `clearance_first_group` frees the safe advance from irrelevant groups and returns **1.0080 /
  0.9903** - all four results BELOW MEASUREMENT FLOOR across both runs and both poses. The reason
  is that **the primary march makes 4.99 body density calls per pixel at SIDE, not the 25.37 T174
  reported**, while the descriptor walk runs **38.25 times per pixel**: the primary body call is
  **13%** of it, the light march is **39%** (14.98 taps/pixel), and segment tests, clearance probes
  and quadrature are the remaining 48%. **Histogram** (primary-only by construction, bins anchored
  on the shader's own 0.0008 threshold): **85.8% of primary calls return material**, 65.7% return
  high density, only 14.2% exactly zero; mean material run 14.90 samples, mean empty run 9.34. That
  formally selects CASE B, but CASE B's direction is empty too - the occupied-sampling ceiling is
  1.006x - so **neither case applies: the primary march's density calls simply are not a
  significant share of the work.** Perfect empty-skip is DERIVED at <1.02x and did not warrant an
  arm. **A dead instrumentation gate was found by building on it**: `paWorkloadCaptureActive()`
  accepted views 22-26 and 28-34, so **T169's light and detail attribution views 35/36 have been
  emitting zeros since T169** (`lightConeMarches=0 tapsPerConeMarch=n/a` is in T169's own log) -
  the registry invariant's omission class in a file it does not cover. Fixed, and closed by
  `validateWorkloadViewsAreEnabled`, which parses the predicate and every `STORM_WORKLOAD_*` id and
  fails the build on any unenabled view (`declared=16|allEnabled=true`). No banked T169 conclusion
  depended on those counters. **Every primary-march lever is now measured and at the floor** - group
  entry 1.136x (T174), tighter per-lobe bound 0.95x (T173), occupied sampling 1.006x, clearance
  0.999x, empty skip <1.02x - so the 1.25x needed for SIDE <=10 ms cannot come from there. FAR stack
  ~5.32 ms accepted (2.406x); SIDE stack pair REJECTED on 5.08% repeat disagreement, working figure
  stays ~12.5 ms. **Recommended T176: attribute and attack the light march**, which is 39% of the
  descriptor walk, three times the primary march's, and has never been profiled at that level
  because its counters were dead - specifically whether a light tap can reuse the primary sample's
  group resolution, and whether the 3.75 cone marches per pixel can be reduced given T169 showed the
  taps within a march already terminate early. Evidence in
  `validation/performance-density-necessity.md`.
- [X] T176 [PERFORMANCE] [US3] Attribute the light march and establish what is recoverable from it.
  **The light march is 26% of SIDE frame time, and two of the three things T175 recommended
  attacking turned out not to exist.** With T175's instrumentation gate fixed, the counters finally
  read: `lightConeEarlyOuts=0`, `tapsPerConeMarch=4.0000` exactly at both poses, and
  `detailFetchLight=0`. **Four taps is a hard cap reached every single march, not an early-out** -
  `min(steps, 4)` under `cameraStartsInsideSlab`, with the only in-loop `break` requiring T149's
  graded mode that FINAL bakes off. **This corrects a banked T175 sentence** ("the taps within a
  march already terminate early"), which was inferred from T169's bit-identical 6/5/4 arms - arms
  that were identical because the cap clamped all three to the same program. T169's conclusion
  survives; its stated mechanism does not. Light taps also already skip every detail octave, so
  **Task 6's cheaper-shadow-density idea was largely pre-spent**. Attribution at SIDE: **3.6155 cone
  marches/pixel, 4.0000 taps each, 14.4622 light taps/pixel, 1.0000 density evaluation per tap, 1
  group and exactly 10.00 lobes per tap** (`lobesVisited/groupFieldCalls = 10.0000`), **39.53% of
  all descriptor group walks** - reproducing T175's 39% independently; per-tap SDF and fetch counts
  are DERIVED from that share and labelled, since the shader does not split them by consumer.
  **Ceilings** (campaign A, anchor-relative, 34 cells, `T176_REJECTED count=0`): `nolight` **1.349x**
  SIDE / 1.203x FAR, `light3` 1.075x, `light2` 1.139x. **Tap reduction captures only 40% of what
  lighting costs**; the other 60% is the ten-lobe walk, which tap reduction cannot reach. `light2`
  is rejected on quality (meanAbs 1.83e-02, 3.5x `light3`, flattened self-shadowing, matching T169's
  worst arm). **Campaign B measured the light arms ON the stack** - the stack bakes
  `PA_ARM_LIGHT_STEPS 4`, which the inside-slab cap already forces, so it carried no light reduction
  and anchor ratios could not be multiplied onto it. **`t176_stack_light3` = 9.62 ms p50 at SIDE
  (9.6348/9.6000, 0.36%, p95 9.98/10.12), the first sub-10 ms SIDE reading in this series**, worth
  **1.0701x** over the stack's 10.29 ms and corroborated to within 1% by the independent
  anchor-relative measurement of the same change. Reported as **met in this run, not yet
  established**: the same stack measured ~12.5 ms in T173/T175 against a ~6% different anchor, so
  absolute SIDE ms is not stable across sessions. **A methodological finding: two repeats that agree
  can both be wrong.** The FAR stack family lands in two modes (~4.3-5.0 and ~8.4-8.6 ms) and
  `stack_nolight` (8.38/4.26) and `stack_light2` (4.63/8.56) each straddle both, proving the same
  program reaches both - which makes `stack_light3`'s tight 0.13% pair at 8.54 ms **agreement within
  a sticky mode, not a measurement**, since it would otherwise mean the stack got 70% slower given
  strictly less work. No FAR stack figure from run B is banked; FAR rests on accepted
  `t172_stack_pre` (4.96/5.01, and T175's ~5.32 ms). T171's repeat rule is necessary but not
  sufficient; a cross-arm mode check is the harness follow-up. **Task 3 (primary-to-light group
  reuse) is NOT MEASURED** - the oracle was not built, and reuse is only bounded above by `nolight`'s
  1.349x. Verdict **CASE B, qualified**: `light3` is the only accepted light lever, decisive at the
  margin but not a large ceiling, and it is a flat 4->3, not the adaptive scheme CASE B describes.
  **Remaining dominant cost: the ten-lobe group walk** - 36.58 walks/pixel at SIDE, all ten lobes
  visited every time while only 5.03 contribute. Recommended next: close the reuse oracle, prune the
  lobe walk, and use the **already-written** T149 graded adaptive-tap path (baked off in FINAL),
  given 73.6% of light taps land after the primary ray passes 50% alpha. Evidence in
  `validation/performance-light-march.md`.
- [X] T177 [PERFORMANCE] [US3] Measure primary-to-light group reuse and re-measure the tap arms
  under a paired-ratio protocol.
  **Group reuse is closed, and the reason is that there is nothing to reuse.** The hard ceiling -
  every light tap forced onto the primary sample's group - measures **0.6873x SIDE / 0.6708x FAR**,
  i.e. 45% *slower*, both stable (ratio spread 1.24% / 0.85% over three blocks). The load-bearing
  number is not the regression but **0.00 group walks avoided per pixel**: production already
  performs **0.9997 group walks per light tap**, which is the floor, so reuse can only remove one
  `stormCandidatesAt` texelFetch and a four-rank scan - and T170 already priced fetches at ~1.0x.
  Part of the 45% is an artifact of the arm adding a second inlined `directStormGroupField` call
  site, so it is reported as such rather than as the cost of reuse. Task 4 (reuse predicate) is moot
  and was not built. **Reuse validity measured 100.00%** (1,458,641/1,458,641 taps SIDE;
  215,205/215,205 FAR), zero partial, zero wrong, flat across all four tap ordinals - **but the
  measurement is not usable**: `lobesVisited/groupFieldCalls = 10.0000` against `StormLobeCount=10`
  means **this fixture contains exactly one descriptor group**, so every sample resolves the same
  group and validity is true by construction. The interesting case - a tap crossing into a
  neighbouring storm - does not occur on this fixture and was not tested. Reported as measured and
  as unable to carry its conclusion. **Paired-ratio protocol** (three anchor-bracketed blocks per
  arm; acceptance on local-ratio agreement, not absolute p50): **local anchor variance is small -
  SIDE CV 0.84% over 36 anchor cells, FAR 2.97% - while the stack arms swing 11-14%, so the sticky
  mode is program-specific, not machine-wide**, which refines rather than confirms T176's reading.
  Anchor-relative pairing did not rescue the stack arms, it **rejected all of them**; but comparing
  an arm against `t172_stack_pre` **within the same block** cancels the drift and yields
  **light3-on-stack = 1.0634x, spread 0.48%** across three blocks, corroborating T176's 1.0701x.
  That works at SIDE, where both arms move together (block 2 is slow for both by nearly the same
  factor), and fails at FAR, where switching is per-cell and the two arms land in opposite modes
  inside one block. **Within-block pairing cancels slow drift, not per-cell mode switching;
  bimodality is still not solved.** **light3** is now measured three ways within 1.1% (1.0588
  anchor-relative, 1.0634 within-block, 1.0701 in T176) with meanAbs 3.19e-03 - but **the quality
  metrics the brief named (cloud/edge SSIM, silhouette IoU, thin and hole retention, self-shadow
  contrast, dark-interior retention, shadow pockets, puff separation) do not exist in this harness
  and were NOT measured**, so performance qualifies and quality is unproven; not productionized.
  **light2** reproduces at 1.1402x SIDE (0.54%) but costs 3.9x light3's error for 7.7% more speed,
  and is REJECTED at FAR (14.03%) - not a candidate. **Stack: every anchor-relative stack figure
  this session is REJECTED on ratio spread**, so T176's 9.62 ms does not survive the stricter
  protocol; session-local `stack_light3` reads 9.71/10.85/9.68 across blocks, reaching <=10 ms in
  two of three and missing in the third. **SIDE <=10 ms is not established**, SIDE <=8 ms not met,
  FAR not measurable this session (stack arms straddle 4.9 and 8.3 ms). The only session-independent
  claim: light3 adds 1.0634x on the validated stack. **Remaining dominant workload: the ten-lobe
  group walk** - 32.88 walks/pixel at SIDE, all ten lobes visited, ~5 contributing. Recommended
  next: since the fixture's storm *is* one group of ten lobes, ask whether the group representation
  can carry precomputed spatial information in the spare T172 texel channels to skip provably
  non-contributing lobes without ranking in the hot loop - explicitly not nearest-K, which T168
  closed. Evidence in `validation/performance-light-reuse.md`.
- [X] T178 [PERFORMANCE] [US3] Measure light3's visual quality and test a precomputed lobe support
  bound.
  **Two decisive negatives.** **light3 must not ship.** With the missing metrics finally in place,
  flat 4 -> 3 taps returns **cloud SSIM 0.884**, **dark-interior retention 0.0715** and
  **shadow-pocket retention 0.663** at SIDE, while silhouette IoU, thin retention and hole
  retention are all a perfect 1.000. **The failure mode is brightening, not the predicted
  flattening**: interior contrast is preserved (ratio 1.018) and valleys are 21% deeper, yet only
  7.2% of the darkest interior quartile stays at or below its reference luminance - dropping the
  fourth tap removes 14 units of optical path from every light march, so extinction falls and the
  whole interior lifts, keeping relief but losing absolute darkness. Two of Task 7's four criteria
  fail, and they are the two that describe self-shadowing; the ~1.06x stays unspent.
  **A correction: five of the twelve requested metrics already existed.** `StormArmQualityMetrics`
  has provided silhouette IoU, cloud/edge SSIM and thin/hole retention since T167 `a280c10`, and
  **was printing in the T177 log** (`cloudRegionSSIM=0.9347` for light3) when T176 and T177 both
  claimed "none of those metrics exist in this harness" - the same read-past that T175 found for
  T169's dead counters. T178 added the four that genuinely did not exist - self-shadow contrast,
  dark-interior retention, shadow-pocket retention, valley depth - and those four are exactly what
  produced the verdict; the five pre-existing ones would have passed light3. Puff separation
  remains `not_implemented_needs_segmentation` rather than silently proxied.
  **Lobe support is CLOSED, but not for CASE C's stated reason.** Attribution per group walk at
  SIDE: **10.0000 visits, 2.0182 cheap pre-SDF rejects, 7.9819 exact SDFs, 4.2033 contributors,
  3.7784 wasted** - so **47.34% of every exact SDF is discarded by the union**, and most
  non-contributing lobes are *not* already cheaply rejected (the reverse of CASE C's premise).
  Per pixel: 341.31 visits, 272.43 exact SDFs, 128.96 wasted. **The light march carries the
  expensive half**: 46.26% of exact SDFs from 37.66% of visits, because a light tap is cheaply
  rejected only **1.94%** of the time against **31.20%** for everything else - it marches from
  inside the cloud where the conservative bound cannot reject. The ceiling is real: `t178_nosdf`
  measures **1.7428x FAR** (accepted) and 1.5328x SIDE, so exact SDFs are ~34.8% of SIDE frame
  time and removing just the wasted 47.34% would be worth **~1.197x**, above CASE A's gate. **But
  the precomputed bound rejected exactly zero lobes** (`lobeSupportRejects=0`, both poses) and
  measured **0.9410x FAR** (accepted, 2.49% spread) - image-exact (every metric 1.000000, FAR
  `digestsEqual=true`), so the implementation is right and simply never binding. **That is also the
  real explanation for T173**: it was slow not because its arithmetic was expensive but because it
  bought nothing. Payload: texel 5 carries `1/maxRadius, minRadius, shearLength`, +16 bytes and
  +1 fetch per lobe visit, removing a role-profile branch, two scaled-radius pairs, a `length()`
  and a division - and it still lost. **The structural reason: the wasted lobes are not far away,
  they are close but dominated**, so no conservative spatial envelope can identify them, which
  disqualifies CASE B's remedy too. **I should have caught this before benchmarking** - one counter
  asking whether T141's horizontal term ever binds would have closed the line without a campaign.
  Stack: FAR ~5.05 ms accepted (2.5494x) but within-block support gain 1.0033 (rejected); SIDE
  11.05 rejected. SIDE <=10 ms **no**, <=8 ms **no**, FAR <=8 ms **yes**. **Remaining dominant
  workload: the exact lobe SDF**, 272.43/pixel at SIDE, 46.3% of it from light taps. Recommended
  next: a tighter incoming-distance estimate against the running union minimum rather than a
  spatial envelope; and a bound designed for rays that start *inside* the medium, which no campaign
  has treated as its own problem. **Texel 5 is dead weight while this line stays closed and should
  return TEXELS_PER_DESCRIPTOR to 5 before any merge.** Evidence in
  `validation/performance-lobe-support.md`.
- [X] T179 [PERFORMANCE] [US3] Test whether a lobe can be proven unable to move the smooth union
  before its exact SDF is paid.
  **Closed: the candidate is exact, free, and worth nothing.** From `stormSmoothMinimum` itself,
  `h = saturate(0.5 + 0.5*(d_new - d_cur)/blend)` reaches 1 exactly when `d_new >= d_cur + blend`,
  and there the result is `d_cur` with the polynomial term vanishing and `stormBlendFactor`'s mix
  an identity - so **a lobe at or beyond `d_cur + blend` cannot alter any component of the union**.
  Production already tests that, against the 48-block global cap while the pair's real blend is
  `clamp(0.25*smaller radius, 4, 48)`, typically 8-20. `t179_dom_exact` substitutes the exact value,
  hoisted from the union below and reused there, and is **bit-identical** (0 changed pixels, maxAbs
  exactly 0.000000, both poses) - **but measures 0.9999x SIDE / 0.9955x FAR.** **My slack hypothesis
  was wrong**: the tightening rejects only **0.076%** more lobes (25,109 of 32,941,121), because
  the lobes surviving T121 are far closer than the 8-48 block band - **the bound is the binding
  constraint, not the threshold.** **Strict ceiling 1.0159x SIDE** (`t179_dom_aggressive`, no blend
  margin, accepted at 1.34% spread) against Task 8's 1.10x gate; it genuinely discards contributors
  (424 changed pixels), so the ceiling is real work skipped rather than T178's accidental no-op.
  **Histogram** (non-first exact SDFs, 28.67M at SIDE): **46.75% change exactly zero**, 1.80% below
  storage epsilon, 0.39% tiny, 51.05% meaningful - 3.14 zero-change SDFs per group walk. **By
  consumer, dominance pruning is half as effective on light taps** (25.0% zero-change) as on primary
  (52.8%), answering T178's question the other way round: a light tap marches from *inside* the
  cloud, where the vertical lower bound returns ~0 and cannot exceed `groupDistance` under any
  margin, and where several lobes genuinely are near. **The structural finding: removing 46.75% of
  exact SDFs is worth 1.6%, while T170's compile-time `desc_nosdf` removing 100% of them uniformly
  was worth 1.53x.** Those cannot be reconciled by work-removed arithmetic; the consistent
  explanation is execution shape - the ten-lobe loop is warp-wide, so a lobe skipped by one lane is
  still evaluated whenever any other lane needs it. Labelled an **inference from two measurements**,
  not instrumented, but it fits the whole series: T167 nearest-K slower, T174 group entry 1.136x and
  insufficient, T178 support bound 0.941x. **Every per-lane pruning scheme has underdelivered
  against its own arithmetic; both shipped wins (T168 footprint LOD, T172 precompute) were uniform
  compile-time reductions.** Task 6's ordering oracle **NOT MEASURED** - a better order changes how
  many lobes are rejected, not what a rejection is worth, and the union is order-sensitive so
  best-first is not image-neutral. Stack: every figure rejected on ratio spread including the
  control (SIDE 10.90%, FAR 54.48%); nothing to compose at 0.9999x. SIDE <=10 ms **no**, <=8 ms
  **no**. **Recommended T180: branch B - fresh-profile the segment/probe/quadrature workload**,
  ~48% of descriptor walks per T175 and never attributed since; representation redesign is not
  indicated because the flat walk already identifies dominated lobes correctly and finding them is
  what fails to pay. Weight uniform reductions over selective ones. The T123 invariant caught this
  campaign's counter below a multi-line guard, the same class it caught in T174 and T175. Evidence
  in `validation/performance-dominance.md`; texel cleanup banked separately as `42e655d`.
- [X] T180 [PERFORMANCE] [US3] Decompose the segment/probe/quadrature workload class and price its
  consumers by uniform removal.
  **The category T175 named does not exist, and the largest ceiling of the series is here.**
  `t180_noprobe` - the empty-span probe scan removed uniformly at compile time - measures
  **1.1972x SIDE (1.15% spread) and 1.2918x FAR (2.29%)**, both accepted, clearing the brief's
  1.20x major-target bar at FAR. **T179's execution inference is now directly supported**: uniform
  removal of **18.6%** of exact SDFs returns **1.1972x**, while T179's per-lane removal of
  **46.75%** returned **1.0159x** - 2.5x less work removed for 12x more speedup, same shader,
  fixture and protocol. **There is no production quadrature**: all four
  `lightMarchOpticalDepth*` functions are reachable only under `DebugView == 6..9` and are dead in
  FINAL, so CASE D is excluded before measurement. **`directStormSegmentMayIntersect` returns a
  bool and never calls `directStormGroupField`** - segment tests were already cheap and are not
  part of the class. The real decomposition, tagged at call sites rather than derived by
  subtraction (buckets sum back: 548,439 + 1,560,504 + 951,957 + 20 vs `cloudDensityCalls`
  3,060,902): **march union-distance refinement 32.0% of group walks and 26.4% of exact SDFs**
  (11.11 walks/px, never previously named), **empty-span probes 21.1% / 18.6%** (7.35 calls/px),
  **bracket bisection 0.0%** - 20 calls in an entire frame, 1.0047x, and **bit-identical at FAR**,
  so it is not a consumer at all. **Semantics**: primary and light need the full field; probe and
  bracket need only `density > 0.0008`; refinement needs only the union *distance*. **The cheaper
  query failed and the reason is structural**: `t180_probe_nodetail` drops subtractive detail
  erosion, which can only raise the tested value and so can never let the scan advance over
  material (45 changed pixels), yet measures **0.9707x - slower**, because the probe's value feeds
  a control decision: overestimating density makes the scan advance less and the march take more
  fine steps. **Any conservative cheap probe biases the same way**, so the route is uniform count
  reduction, not query substitution. Stack: `t180_stack_probe` 10.538 ms p50 session-local but its
  within-block ratio vs the control is 1.0830 (rejected, 18.35%) at SIDE and **0.9933** (accepted)
  at FAR - not a gain. SIDE <=10 ms **no**, <=8 ms **no**, FAR <=8 ms **yes** (~4.95 ms).
  **Gaps stated rather than inferred**: Task 7 duplication is **NOT MEASURED**, and **no removal
  ceiling was built for the refinement** - the larger of the two consumers. Verdict **CASE A**,
  qualified: one consumer has a >=1.20x uniform ceiling and does not need full density, but its
  obvious cheaper query is slower. **Recommended T181**: sweep `PA_EMPTY_SPAN_PROBES` (16 -> 8 -> 4
  -> 2) as uniform compile-time arms with damage at each; then build the refinement removal
  ceiling; do not target the bracket. Evidence in `validation/performance-consumers.md`.
- [X] T181 [PERFORMANCE] [US3] Sweep the empty-span probe cap and price the march's union-distance
  refinement.
  **T180's refinement attribution was wrong, and this campaign corrects it.** T180 identified the
  refinement's call site by elimination and assigned it the entire untagged residual - 32.0% of
  group walks. Direct tagging shows **1.50 events/pixel and ~1.62 group walks/pixel, about 4.7%**.
  **It is also not removable**: `t181_norefine` measures **0.9454x SIDE** (accepted, 2.36% spread)
  with 7,607 changed pixels - removing 4.7% of walks costs 5.5% of frame time, because the
  clearance it computes is what permits the coarse stride at all. Semantically it is a **single**
  `directStormShape` call per event, not an iterative solve, so **Task 6's iteration sweep has no
  premise**; it is a clearance correction needing a conservative distance, never a density.
  **The probe cap does bind, contrary to my stated prediction**: 10.91 probes per scan at SIDE,
  **53.1% of scans reach the cap without finding material**, and **64.8% land in the 9-16 bucket**
  (FAR: 12.38, 61.9%, 75.7%). **The sweep is non-monotonic and peaks at 4 at both poses** - 16->8
  gives 1.0605, **16->4 gives 1.0850 SIDE / 1.1056 FAR**, 16->2 falls back to 1.0704 - because
  fewer probes is less work but a shorter scan advances the ray less and costs march iterations.
  **Capping captures at most 43% of T180's 1.1972x no-probe ceiling**; the rest is probes that
  genuinely earn their advance. **Quality does not collapse**: cap 4 holds cloud SSIM **0.9969**,
  edge SSIM 0.9843, silhouette IoU 0.9978, hole retention 0.9937, thin 0.9355, 454 changed pixels -
  against light3's rejected 0.884 cloud SSIM. **Duplication measured** (T180 left it open): the scan
  samples the same lattice the fine march then re-walks, so probes in scans that find material are
  duplicated by construction - **20.4% of probe work at SIDE**, which is only ~3.8% of exact SDFs,
  so **caching is closed**. Stack: `t181_stack_probe8` reads 10.097 ms p50 session-local but its
  within-block gain of 1.1913x is **rejected on 5.31% spread** with the control itself spreading
  4.65% SIDE and 60.31% FAR; the stack arm was fixed at cap 8 before the sweep ran, so **the best
  cap was never composed with the stack**. SIDE <=10 ms **marginal and unsound**, <=8 ms **no**,
  FAR <=8 ms **yes** (4.982). **Verdict: cap 4 is a minor stackable candidate** (1.03-1.10x band),
  refinement **closed**. **Remaining dominant workload is unattributed**: after tagging primary,
  light, probe, bracket and refinement, **27.7% of group walks still carry no tag** and
  `directStormShapeCalls` exceeds `cloudDensityCalls` by 2.30M. **Recommended T182: close the
  buckets against `directStormShapeCalls`, tagging every remaining entry including the conditional
  path inside `cloudDensity` - and do not infer a call site by elimination again.** Evidence in
  `validation/performance-probe-refine.md`.
- [X] T182 [PERFORMANCE] [US3] Close the descriptor-walk accounting and name the residual.
  **The accounting closes - `shapeUntagged = 0` at both poses - and the residual T180 mis-named and
  T181 could not identify is the rain-segment reachability test**, which is **the largest single
  consumer of descriptor work in the shader: 36.26% of `directStormShape` calls at SIDE and 56.67%
  at FAR**, more than the light march. `main` runs `rainSegmentMayContribute` on **every coarse
  step**; past its gate it evaluates `localRainSupportAt` twice, each walking every descriptor and
  then performing a full candidate/group union - two complete storm traversals per step. The
  shader's own header has documented this since T098 and no campaign had measured it. Its gate is
  `MaxPrecipitation <= 0.02`, a **runtime uniform**, not the compile-time `PA_PRECIPITATION_ABSENT`
  FINAL bakes, so it runs at full cost for a feature this build cannot render. **Uniform removal
  measures 1.2139x FAR** (accepted, 2.64% spread) with **105 changed pixels, meanAbs 1.05e-05** -
  the largest clean ceiling of this phase and nearly free of image cost. SIDE was **not measurable**:
  the anchor swung 20.34-22.01 and drifted 4.3-7.2% in eleven of twelve blocks, rejecting nearly
  every arm; `norainseg`'s three SIDE ratios nonetheless agree to 1.9% (1.1848/1.2074/1.1988,
  centring 1.198) while each was rejected because the *anchor* moved - recorded as corroborating,
  **not banked**. **Count reconciliation**: `directStormShapeCalls` 5,739,705 exceeds
  `cloudDensityCalls` 3,370,120 by 2,369,585 because tags 5, 6 and 7 reach `directStormShape`
  without passing through `cloudDensity`; their sum is 2,368,447. The 0.058% gap between tagged sum
  and total is **cross-frame capture variance** (each debug view is a separate rendered frame), not
  an unattributed consumer. **SIDE/FAR contrast**: every rendering consumer shrinks with distance
  (light 33.8%->16.6%, primary 11.0%->7.1%) while rain reachability **grows to 57%**, because it is
  paid per coarse step at any distance. **Cap 4 does NOT survive composition: 0.9900x on the stack
  at FAR** (accepted, 0.46% spread) - a null result, not an inconclusive one - so it fails Task 9's
  >=3% bar and **must not be productionized**, despite T181's standalone 1.0850x/1.1056x and cloud
  SSIM 0.9969. **New invariant `validateConsumerTagsAreCounted`** fails the build if a tag is
  assigned with no counter reading it, if nothing reads tag 0, or if `paShapeUntagged` is removed -
  the structural answer to two campaigns of attribution by elimination
  (`assigned=9|counted=9|untaggedCounterPresent=true`). **Recommended T183: gate
  `rainSegmentMayContribute` on the same compile-time condition that already removes precipitation**
  - exact for the shipping build, unlike `t182_norainseg` which removes it unconditionally and would
  break a precipitation build - and check whether the runtime `MaxPrecipitation` gate can be hoisted
  out of the per-step call for builds that do render rain. Evidence in
  `validation/performance-accounting.md`.
- [X] T183 [PERFORMANCE] [US3] Specialize rain reachability for the precipitation-absent program.
  **Not implemented - the change is a functional regression, and this is established from source and
  from T182's own counters, not from a timing run. No GPU campaign was spent.** The premise was that
  because FINAL bakes `PA_PRECIPITATION_ABSENT`, the rain-segment reachability test is dead work.
  **It is not.** That define removes precipitation from `cloudDensity`'s internal term only; **rain
  still renders in FINAL** through `rainShaftDensityOverSegment`, which the march calls directly and
  gates on `localRainSegment` - the flag `rainSegmentMayContribute` computes. Gating the test on the
  define would pin that flag false, set `rainDensity = 0.0` unconditionally and **delete rain from
  the shipped program**, which the brief's own Task 3 forbids. **Three proofs**: (a) T163's comment
  at the define says "Rain itself is unaffected: it renders through `rainShaftDensityOverSegment`,
  which the march calls directly"; (b) the **generated** FINAL still contains that call, gated only
  by `localRainSegment`; (c) consumer tag 7 is reachable only inside `rainShaftDensityAt`, whose
  `cloudDensity` route is compiled out, yet T182 measured `shapeRainShaft=43958` SIDE / 43133 FAR -
  rain rendering ran tens of thousands of times per frame. **This corrects T182**, which claimed
  `t182_norainseg`'s 105 changed pixels were a perturbed step pattern and that "the rain it gates
  cannot render in this build"; those pixels are rain being deleted, and the 1.2139x is the cost of
  a shipped feature rather than a ceiling on dead work. Three inline corrections added to
  `performance-accounting.md`. **Task 0 done**: `shapeAccountingClosed` compared against half a call,
  stricter than cross-frame capture variance (each debug view is a separate rendered frame), so it
  printed `false` on a correct 0.058% residual; replaced with a documented **0.5% fractional
  tolerance** plus `shapeAccountingResidual` and `shapeAccountingResidualFraction`, and it now
  **requires `shapeUntagged == 0` as well** - the strict structural check is unweakened, and the
  static consumer-tag invariant is untouched. **Tasks 2/3 inverted**:
  `validateRainRenderSurvivesPrecipitationSpecialization` reads the **generated** FINAL and T140
  variants and fails the build if `rainShaftDensityOverSegment` or `rainSegmentMayContribute`
  disappears - an invariant blocking the requested change rather than proving it
  (`T183_RAIN_RENDER programs=3|rainReachablePostSpecialization=true`). Items 9-20 and 21-33 are
  **not reported**: they describe a build that should not exist. **Recommended T184: reduce the
  frequency and cost of the rain reachability query while preserving rain** - it answers a boolean
  with two full descriptor traversals per coarse step, and the attach height is a property of the
  *column*, not the segment, so a per-ray or weather-tile-keyed bound would answer it far less often;
  T145's existing height prune is the model. Evidence in
  `validation/performance-rain-reachability.md`.
- [X] T184 [PERFORMANCE] [US3] Measure rain-support recomputation and price exact column reuse.
  **The hypothesis is exactly right about the function and exactly wrong about the ray.**
  `localRainSupportAt` is **provably column-invariant within a frame** - every input is
  `sampleWeather(worldXZ)`, `sampleMorphology(worldXZ)`, `directStormRainSupportAt(worldXZ, ...)`
  or a frame uniform; **no Y, no segment endpoints, no camera, no jitter** - so reuse keyed on XZ
  equality is exact, not approximate. The one-entry cache built on that proof is **bit-identical:
  0 changed pixels, meanAbs 0.000000, at both poses**, which is empirical confirmation of the
  dependency audit across 2.3M calls. **And it is worth nothing**: exact reuse fires **0.40% SIDE /
  0.17% FAR**, so the arm measures **0.9973x SIDE / 0.9911x FAR** (both accepted) and the stack
  composition is **0.9983x FAR** (accepted). Task 3's own gate - close below 1.05x - is met with
  room to spare. **CLOSED.** **Task 7's premise also fails, determinable from source without a
  campaign**: both `rainSegmentMayContribute` and `rainShaftDensityOverSegment` sample at the
  two-point **Gauss-Legendre nodes 0.2113/0.7887** - interior points, not endpoints - so
  consecutive segments share **no** sample position and the hoped-for `support(B)` carry-forward
  never occurs; exact shared-endpoint rate is **0%, structurally**. **Reuse distance is the whole
  answer**: even at an 8-block tile (already an approximation) only 7.16% SIDE / 3.00% FAR repeat;
  at exact XZ it is four tenths of one percent, because the ray advances a coarse step between
  calls and the two Gauss points sit 57% of a segment apart. **The observation that matters for
  next time**: support calls barely change with distance (18.06/px SIDE vs 17.54/px FAR - the test
  runs once per coarse step regardless) while the **T145 prune rate does: 12.12% SIDE vs 76.74%
  FAR**. At FAR most columns fall outside the rain ownership circle and are rejected on a weather
  fetch; at SIDE the camera sits near the storm, almost every column is inside it, and the full
  descriptor traversal is paid. **T145 is a rejection test, not a reusable representation** - it
  can prove absence but never produce `attachY`/`localSupport`, so it is not semantically
  sufficient to share, and that is stated rather than forced. **Recommended T185: widen the prune,
  do not cache the result** - ask whether one bounding circle over all rain-owning descriptors is
  too coarse (T172 already precomputed per-descriptor ownership radii into texel 3), and whether
  the `precipitation <= 0.02` conjunct can be relaxed for columns provably outside every ownership
  ellipse. Both preserve rain exactly, per T183. Rain-render invariant passes throughout
  (`T183_RAIN_RENDER programs=3`). SIDE <=10 ms **no** (10.920 session-local, composition
  rejected), FAR <=8 ms **yes** (4.815). Evidence in `validation/performance-rain-reuse.md`.
- [X] T185 [PERFORMANCE] [US3] Test whether the rain ownership envelope is too coarse.
  **The envelope really was too coarse - by a lot - and it does not matter.** At SIDE the shipped
  circle rejects **14.47%** of rain-support queries, a tightened box **49.36%**, and the exact
  per-ellipse test **71.50%**. Tripling the rejection rate is worth **1.0135x**; the exact ceiling,
  rejecting five times as many columns as production, is worth **1.0155x** - so Task 3's own gate
  (close below 1.05x) is met by the *ceiling*. **CLOSED.** Both prunes are **bit-identical: 0
  changed pixels, meanAbs 0.000000, both poses**, verifying conservativeness rather than assuming
  it. **The envelope diagnosis (Task 1) was correct**: `paBuildRainLocality` stacks three
  conservative steps - each ownership ellipse becomes a **square** of side `2*max(semi-axis)`, the
  squares become one **AABB**, and the AABB becomes its **circumscribed circle** (root two on the
  diagonal) - and accumulates over **every role**, so wide ANVIL lobes inflate a bound whose purpose
  is set by BASE attachment. That is why SIDE, close to the storm, sits inside the inflated disc.
  **Task 6's proposed safe form is unsound and was not implemented**: `localRainSupportAt` returns
  `weatherCoverage`-based support for a column outside all descriptor ownership when raster
  `precipitation > 0.02`, so "outside all rain-owning support => rain impossible regardless of
  precipitation" is false; the conjunct is load-bearing and was kept. **The conjunct diagnostic
  decided the campaign**: `rainPrecipLowFraction` is exactly **1.0000** at both poses, so the
  precipitation half never fails and the geometry is the entire binding constraint - which is what
  justified testing the tightening instead of assuming it. False positives: 27.08% SIDE / 22.91%
  FAR of accepted columns carry no support. **Task 7 explains the outcome**: the prune decides per
  **column**, so rejection is per-lane inside a warp-wide traversal - the shape T179/T180 showed
  does not pay. **T185 is the strongest per-lane experiment yet run** (largest logical fraction
  removed, exactly correct, at *negative* arithmetic cost - a box test replacing a `distance()`,
  using only T172's precomputed radii) and it returns 1.6%, a fourth independent confirmation.
  Secondary: the prune sits *after* `sampleWeather`/`sampleMorphology`, so rejected columns still
  pay two fetches. **No usable stack figure** - the control itself spread 15.19% SIDE / 52.82% FAR;
  the SIDE within-block ratio of 1.2336 missed the 3% rule at 3.50% and is **not banked**. SIDE
  <=10 ms **no** (10.2315 session-local, rejected), FAR <=8 ms yes. **Recommended T186**: per the
  brief's own failure branch, `rainSegmentMayContribute` samples **two** points per coarse step - a
  uniform compile-time reduction to one is the shape that has paid twice in this series, gated on
  rain-coverage metrics rather than timing alone; **if that fails, stop optimising rain** and pick
  the next target on measured share. **The transferable lesson**: this prune should have been worth
  ~20% of the rain-support traversal on arithmetic and returned 1.4% - price per-lane proposals
  against that, not against the fraction of work removed. The T145 invariant caught a real defect in
  the first patch (preprocessor branches each opening a brace left the source unbalanced for
  `functionBlock`). Evidence in `validation/performance-rain-prune.md`.
- [X] T186 [PERFORMANCE] [US3] Reduce rain reachability from two samples per segment to one, and
  decide whether the micro-optimization series continues.
  **Best arm `t186_one_mid`: 1.0668x SIDE** (accepted, 0.88% spread) with **cloud SSIM 0.999983**
  and **120 changed pixels** - real, trivially implementable, and the largest quality-safe
  single-arm gain since T180. **It is not enough, and per the brief's own rule the series stops
  here**: the strong-candidate bar was 1.10x, the strategic requirement is 1.2-1.3x, the stack
  composition was **rejected** (within-block 0.9833 at 13.27% spread, control itself 15.98%), and
  the rain-specific quality gate is **unmeasured**. **Premise correction**: the two-sample rule
  costs **0.59 support evaluations per segment test, not 2**, because the T145 height prune already
  rejects **70.4%** of sample opportunities (5,478,395 of 7,784,958) before any traversal. "Halves
  the work" is false per call and true per traversal - sample 1 is 1,151,877 of 2,296,819 support
  evaluations, so removing it removes **49.2%** of them. **Semantics (Task 1)**: the rule is an
  **OR with early return** - existence, not max or integrated - so dropping a sample can only turn
  true into false, making **"falsely added rain" structurally zero** and "missed rain" the only
  failure mode. The early return is nearly irrelevant: the test returns true on just **0.28%** of
  calls, so both samples run almost equally often. The second sample is the deciding one for
  **11.50%** of rain-positive segments. **All three positions accepted within 1.1% of each other**
  (mid 1.0668, one_b 1.0598, one_a 1.0557 at SIDE); quality is essentially untouched - hole
  retention exactly 1.0, IoU 0.999 - with **thin retention** the only metric that moves
  (0.896-0.969), consistent with losing thin rain wisps rather than cloud body. **Not measured and
  not implied**: missed-rain pixels, rain-region overlap, shaft continuity, onset/termination
  height - none exist in this harness, and with 11,028 rain-positive segments per frame this
  fixture is too rain-sparse for them to mean much; that is a fixture problem, not a metrics one.
  **Verdict: hold `one_mid` as a banked minor candidate** pending a composition re-test on a stable
  control and a rain-heavy fixture - do not ship it off this run - and **close rain optimization**
  after five campaigns (T182 attribution, T183 specialization, T184 reuse, T185 prune, T186
  sampling). **Fresh post-everything SIDE attribution**, `shapeAccountingClosed=true` with residual
  0.0001 and `shapeUntagged` 0: rain segment **44.12%**, light tap 26.45%, empty-span probe 15.69%,
  primary body 9.48%, refinement 3.34%, rain shaft 0.93% - 34.88 shape calls, 28.40 group walks,
  284.0 lobe visits, 220.0 exact SDFs per pixel. **Recommended architecture task: bake the
  rain-attach field once per frame.** T184 already proved `localRainSupportAt` is *exactly*
  column-invariant within a frame, and showed per-ray caching fails only because a ray never
  revisits a column (0.40%) - **a precomputed field does not need revisiting**. It converts 30.03
  segment tests per pixel from descriptor traversals into texture fetches: uniform work removal, the
  only shape that has paid here, applied to the largest remaining consumer. Open questions are field
  resolution against the 184-block attach band, conservative tile quantisation, and per-frame build
  cost. Evidence in `validation/performance-rain-samples.md`.
- [X] T187 [PERFORMANCE] [US3] Establish whether a precomputed 2D rain-support field is worth
  building, before building one.
  **Feasibility pass, not an implementation - no field was built.** **The ceiling clears the gate:
  `t187_field_oracle` measures 1.1756x SIDE** (accepted, 2.28% spread) and **1.1129x FAR**
  (accepted, 2.93%), against the brief's 1.10x stop condition, by making
  `directStormRainSupportAt` cost nothing while every other part of `localRainSupportAt` computes
  exactly as production does. That removes **1,994,647 rain-segment traversals per frame** (15.39
  per pixel, 44.12% of `directStormShape` calls). **The build arithmetic is the enabling number**:
  ULTRA's existing weather domain is **512x512 = 262,144 cells**, so the field costs **1 evaluation
  per 7.61 traversals removed** - about 13.1% of the work it eliminates, leaving ~86.9% of the
  ceiling before lookup cost. **Realistic estimate 1.13-1.15x SIDE, DERIVED and labelled as such**,
  not measured: a strong candidate, not the 1.2-1.3x structural win the target needs, so it should
  not be treated as the thing that closes the 10 ms gap by itself. **Semantics (Task 1)**: the field
  is exactly **three values** - `directSupport`, `stormBaseY`, `ownsDescriptorGroup` - because
  everything else in `localRainSupportAt` is two texture fetches plus scalar arithmetic that costs
  less than storing it. **One field serves both Gauss positions with no approximation** (they differ
  only in XZ, and T184 proved the function is exactly XZ-invariant); **spatial quantisation is the
  only approximation a field introduces**. **Domain (Task 2)**: reuse `WeatherOrigin`/`WeatherExtent`,
  already shared by `sampleWeather` and `stormCandidatesAt` - **no scrolling, recentering or edge
  policy to invent**, all inherited - at **1-2 MB**, which is not a constraint at any useful
  resolution. **Generation (Task 6): GPU**, because `VolumetricCloudRenderTargets.prepare*Target`
  already builds weather, morphology and cumulus-stage maps per frame over the *identical* domain,
  so this is one more target in an existing pass rather than a new architecture; CPU generation is
  rejected on evidence - the field must reproduce `directStormFinalDensity`'s union bit-exactly, and
  T172 exists precisely because a CPU precompute had to be proven identical to the shader. **Stack
  at the ceiling: `t187_stack_field` reads 7.7483 ms / 8.4357 ms session-local at SIDE** with its own
  ratio accepted at an unusually tight **0.26%** spread - clearing both the 10 ms target and the 8 ms
  stretch - **but the composition against the control is rejected** (within-block 1.3988 at 15.41%,
  control itself 15.08%), the same instability that has blocked every stack comparison since T181;
  and this is a ceiling, so a real field lands above 7.75 ms. **Tasks 3, 4, 8, 9 and 10 are
  deliberately not done**: the brief gates everything on the ceiling clearing 1.10x, so building a
  rain-metrics harness and a rain-heavy fixture for an architecture that might be dead on cost would
  have been wasted - both are now justified work, and the metrics need a **rain-only capture path**
  (rain and cloud are composited in the reference frames) that cannot be retrofitted after
  acceptance. **Verdict: proceed - build the GPU rain-support field**, in order: rain-only view plus
  rain IoU / missed / false / onset / termination / continuity metrics and a rain-heavy fixture
  first, then the 512x512 field target, then measure against the derived 1.13-1.15x, then sweep
  resolution downward while the metrics hold; update frequency and scrolling are optimisations of a
  field that must first be shown to pay. **If it were rejected**, the next targets are light tap
  (26.45%) and empty-span probe (15.69%), 42% together. **Infrastructure note**: the first campaign
  launch hard-crashed the client during shader-variant loading (exit -805306369) after six minutes
  of compilation with **127 declared variants**; a clean relaunch completed all 38 cells, so it was
  transient - but compiling 127 full-size fragment programs at every startup is close enough to a
  cliff to prune before it becomes a recurring blocker, and roughly 90 are referenced by invariants,
  so that prune is its own task. Evidence in `validation/performance-rain-field.md`.
- [X] T188 [PERFORMANCE] [US3] Build the real GPU rain-support field, price it against the
  traversal it replaces, and gate it on rain-specific quality.
  **The field is real, it works, and T187's derivation was right.** Net including the build,
  measured on two campaigns: **SIDE 1.1158x accepted** (three blocks, 2.24% spread), corroborated
  by a second run's 1.1307x; FAR 1.1450x accepted in one run and 1.0776x rejected in the other.
  T187 derived 1.13-1.15x from build arithmetic alone, so this is that prediction **confirmed**,
  not merely not contradicted, and it lands in the brief's **1.10-1.15 strong-candidate** band. **No
  stop condition fired.** **The build is nearly free: 0.2266-0.2458 ms** for 262,144 cells,
  RGBA32F, **4 MB**, stable to within 8% across every arm and both poses - and
  `t188_field_generate_only`, which pays the whole build and still marches the exact descriptor
  path, returns **0.9967x and 0.9900x net at FAR (both accepted)**, proving the build costs 0.3-1.0%
  and that the gain is genuinely the lookup replacing traversal. **Structural proof (Task 3): the
  rain path does not shrink, it leaves the ray.** Tagged descriptor-shape calls fall
  **1,358,167 -> 575,422 (-57.6%)** with `shapeAccountingClosed=true` and `shapeUntagged=0` on both
  sides; **rain segment 750,507 -> 0** and rain shaft 17,314 -> 0; `rainFieldFetches` 2,098,025 with
  **`rainFieldFallbacks=0`**, so every rain-relevant column is inside the inherited weather domain
  and the exact-traversal edge path never fires. Build cost per cell measured at 1.000 shape calls,
  0.180 group walks, 1.800 lobe visits, 0.786 exact SDFs, giving **8.00 ray fetches per cell built**
  against T187's derived 7.61. **Generated by the production function itself** - an untimed
  fullscreen pass in the same program calling the same `directStormRainSupportAt` - so there is no
  second implementation to drift, which is the proof burden T172 had to discharge the hard way.
  **And the field invents rain.** On the rain-only capture built for this task: **missed rain
  0.75-1.02%, false rain 10.6-15.6%**, rain IoU 0.858-0.895, thin retention 0.969-0.983, mean onset
  error 3.06-5.58 blocks, shaft continuity 0.935-0.965 with 11-42 newly broken shafts - the
  asymmetry holding across four independent measurements. **The mechanism is identified**: ownership
  is a boolean the bilinear filter returns as a fraction, and thresholding at half a cell still
  **dilates** the union outward by up to four world blocks, which produces exactly this signature -
  a ring of new rain around every shaft and no lost interior. **Verdict: accepted on performance,
  not yet on quality; do not ship.** **Next: stop interpolating ownership** (a `NEAREST` ownership
  channel, or a conservative threshold that erodes rather than dilates), re-run the rain-mask pair
  targeting false rain at or below missed rain, and only then sweep resolution - **Task 8 is
  deliberately withheld** because a coarser field makes half-texel dilation strictly worse, so the
  sweep would produce a table dominated by an error with a cheaper fix. Tasks 9 and 10 likewise
  wait: at 0.23 ms, rebuild frequency is not where the cost is. **Post-field attribution (Task 11)**
  makes T187's shares obsolete as anticipated - **light tap 38.41% and empty-span probe 37.96%, now
  co-dominant at 76.4% together**, primary body 17.04%, refinement 6.58%, rain **0%**. **Two defects
  found and fixed, both of which would have silently corrupted the result**: a thirteenth shader-JSON
  sampler threw `ArrayIndexOutOfBoundsException: Index 12 out of bounds for length 12` inside
  `ShaderInstance.apply` and disabled the volumetric pass for a whole session (Minecraft tracks
  twelve texture units; the field is now bound manually on PA-owned unit 15, as the puff candidate
  map and both noise volumes already are), and the field GPU timer was sticky, reporting
  `rainFieldP50=0.2109` on a `lean_final` anchor that ran no field pass - which would have charged
  every control for the candidate's build. Both are now asserted by invariants. **FINAL is unchanged
  by T188**: it bakes both field uniforms to 0, so neither the generation pass nor the lookup branch
  is compiled into it. Variants **128 -> 133**, all five campaign-scoped; the T187 report's "127" was
  one short, and the prune it recommended is still outstanding. Evidence in
  `validation/performance-rain-field-real.md`.
- [X] T189 [PERFORMANCE] [US3] Make rain-field ownership sampling discrete, and find out whether
  interpolation was the quality failure.
  **It was not, and this entry corrects T188.** T188 concluded that bilinear interpolation of a
  boolean ownership channel dilated the union and caused its false rain, and stated that mechanism
  as identified. Three arms - bilinear at 0.5, `texelFetch`, and bilinear at 0.99 - render
  **byte-identical rain masks, same digest, at both poses** (SIDE `1321e341dd510683`, FAR
  `9dc3104c50ba6ab7`), with `captureFresh=true` on every row. **Changing how ownership is resolved
  changes the rendered rain by exactly zero pixels.** **The premise was measured rather than
  assumed this time**: the texture *is* filtered (`fieldTextureIsFiltered=true`, filtered-minus-
  texelFetch delta 42-92 on ownership and 249-306 on support), but **fractional ownership occurs on
  only 0.19-0.32% of columns** - far too few to produce a 12% error. **The field is nearly exact**:
  ownership disagrees with `directStormRainSupportAt` on **32 of 129,600 columns (0.02%)**, mean
  support error 0.0014, **mean attach-height error 0.0275 blocks**. **The real mechanism is an OR
  over ~60 column samples per ray**, and it is verified numerically rather than fitted: rain
  presence is an existence test, T186/T188 measured ~30 segment tests x 2 Gauss nodes and 15.39
  surviving support calls per pixel, and `1-(1-0.00224)^60 = 12.6%` against a **measured 12.36%**
  false rain at SIDE. It also explains the asymmetry T188 misattributed - under an OR a wrongly
  owned column propagates to the whole ray while a wrongly unowned one is masked by the other 59,
  so false rain is amplified (12.36%) and missed rain suppressed (0.45%). **Resolution cannot fix
  it**: 1% ray-level error needs ~13x lower per-column error, so ~169x the cells (44M vs 262,144),
  which inverts the build ratio from 8.0:1 in the field's favour to about 1:21 against - Task 9's
  sweep stays withheld, now for a firmer reason than T188 had. **Performance: the fix is free.**
  `T189_OWNERSHIP_COST` measures nearest against bilinear in the same blocks at **0.9981 and 1.0030
  (both accepted)** - zero within noise - and net SIDE holds at **1.1228 accepted at 0.15% spread**
  (1.1368 in run 1), FAR 1.1341 accepted, all above the 1.10 band; field build 0.2350-0.2468 ms
  unchanged. **Verdict: keep the discrete fetch, do not approve the field.** Ownership is a boolean
  and asking a filter for it is a type error whatever it currently costs; the fetch is free and
  removes a class of future error. But 8.5-12.4% false rain stands and the brief's bar excludes it.
  **Recommended next: conservative ownership (Task 6 option C)** - store per cell whether it is
  entirely owned, entirely unowned, or mixed, and fall back to the exact traversal on mixed cells
  only. Mixed cells are the 0.2-0.3% measured here, so false rain goes to zero **by construction
  rather than by tuning**, the 8:1 build ratio is preserved, no resolution change is needed, and the
  fallback path already exists and is already exercised. Option B (signed ownership margin) is the
  approximate alternative; **options A and D are ruled out** by the arithmetic above. **Not
  measured**: the direction of the 32 disagreements is summed into one figure - splitting it is two
  lines and belongs with the conservative-ownership work, where it sizes the mixed-cell fallback.
  **Harness**: capture freshness is now checked against the request that produced it, because three
  back-to-back mask captures made a stale read indistinguishable from the genuine result;
  `WorkloadResult` hit the JVM's 255-parameter ceiling so T189's counters travel as a nested
  `FieldProbe`; the coordinate mapping is asserted rather than assumed, with the build failing if a
  half-texel term appears on either side of it. Stack **not banked** - SIDE composition rejected in
  both runs on a control that spread 4.62-12.13%. Variants **133 -> 137**. Evidence in
  `validation/performance-rain-field-ownership.md`.
- [X] T190 [PERFORMANCE] [US3] Make the rain field conservative: classify cells at build time and
  fall back to the exact evaluation where the field cannot be trusted.
  **The fallback does what it was designed to do, and it costs the gain it was protecting.**
  **Quality improved on every metric**: false rain **15.22% -> 2.99%** at SIDE and 10.75% -> 2.51%
  at FAR, rain IoU **0.8632 -> 0.9675** and 0.9029 -> 0.9755, mean onset error **4.12 -> 0.79
  blocks**, newly broken shafts 39 -> 20, shaft continuity 0.964 -> 0.982. **But net gain fell from
  1.1575 to 1.0230 at SIDE** (1 block; the better-supported figure is **1.047**, from applying the
  3-block `T190_CORRECTNESS_COST` of 0.9042 to T189's accepted 1.1575) and 1.1451 -> **1.0360** at
  FAR (accepted). SIDE is under the brief's `<1.03x architecture no longer worthwhile` line and
  **false rain did not reach zero**, which was the acceptance target. **Task 1 was measured before
  the classifier was designed**, because T188 designed against an assumed cause and its fix changed
  zero pixels: of 129,600 columns, **ownership disagreements are exactly 0** (view 63 confirms
  independently, against T189's 32), and the entire residual is **support-cutoff crossing (111) and
  attach-height variation (37)** - 0.114% total, down from T189's 0.224%, which is what turned 15%
  false rain into 3%. **The classifier eliminated the ownership component completely**; what remains
  is variation strictly inside a cell that five sample points cannot see. **Rates (Task 5)**: mixed
  cells **1.42-2.04%**, fallback lookups 113,002 against 2,002,113 safe hits - a **2.91% fallback
  fraction**, 0.184 per pixel - so 262,144 cells plus 113,002 fallbacks against 2,115,115 lookups
  still amortizes **5.6:1**, down from 8.0:1 but nowhere near breaking the trade. **The fallback
  rate is not what killed the gain: classification is.** Field build **0.2437 -> 0.8684 ms, 3.6x**,
  because proving a cell uniform means five full `directStormRainSupportAt` calls instead of one;
  the fallbacks add a further ~2.5 ms to the march. **Representation unchanged (Task 9)**: RGBA32F,
  16 B/texel, 4 MB, one texture - T188 wrote alpha as a constant marker nothing read, so the
  certainty flag cost **no extra channel, texture or byte**, and the hot lookup stays one
  `texelFetch`, one comparison, a rare exact fallback. **Stack**: SIDE `t190_stack_safe` vs
  `t172_stack_pre` **1.0761 accepted at 1.49%** - the first accepted SIDE stack composition since
  T181, on a control that finally held still at 0.45% - 15.02 ms session-local; FAR rejected at
  45.96% control spread. **Verdict: do not approve, and do not iterate on this classifier.** Both
  failures share one cause and it is the instrument, not the architecture: **sampling five points is
  at once too expensive to be cheap and too weak to be a proof.** **Next: bound ownership instead of
  sampling it.** The scaling in `paRainColumnOwnedExact` is per-axis, so a cell's axis-aligned box
  stays axis-aligned in scaled space and the min/max of `dot(scaled, scaled)` over it are
  closed-form - `max < 1` proves the cell owned, `min > 1` proves it unowned, anything else is
  genuinely mixed. That is **provably** conservative rather than sampled and costs one cheap loop
  over ten lobes instead of four union evaluations, which should return most of the 0.62 ms while
  keeping ownership exact by construction. The 148-column support/attach residual is then a named,
  measured problem to bound or accept. If that does not restore net SIDE to ~1.12 with false rain
  near zero, close the field: three campaigns have shown the performance is real and the correctness
  is expensive. **Task 10 (resolution sweep) stays withheld** - it was gated on 512 being
  quality-correct and it is not. **Task 12 not re-run**: the field is not accepted, so T188's light
  38.41% / probe 37.96% stands as the last valid attribution. Variants **137 -> 140**; the prune is
  overdue. Evidence in `validation/performance-rain-field-conservative.md`.
- [X] T191 [INFRASTRUCTURE] Stop compiling every historical campaign arm at every startup.
  **Shader registration fell from 6m 46s to 5s, and from 140 live fragment programs to 2.** The
  before figure is measured, not estimated: the T190 log registers shaders 20:58:08 -> 21:04:54 and
  T189 19:01:41 -> 19:08:13, **6m 46s and 6m 32s** - and that window is exactly where the T187
  launch died, six minutes into shader loading. **Inventory**: all 141 enum programs classify
  without a remainder - **2 production** and **139 campaign arms** across 26 prefixes (T166 alone
  has 22, T170 15, T167 14) - with **zero orphans**, which is what makes scoping a complete answer
  rather than a partial one. None of the 139 is referenced at runtime outside its campaign:
  `setFinalProgramOverride` is called only by the driver and `CoreCostDiagnosticProgram.parse` has
  no caller at all. **No invariant needed a live program** - every one validates generated source,
  define tables, enum declarations or registry entries, and the sandbox has never had a GL context,
  so **nothing was weakened**; the compile and link work was serving only the possibility that a
  campaign might later select an arm. **Scoped rather than pruned**: registration filters on the
  active campaign marker, and `campaignId()` derives from the arm's own `tNNN_` prefix resolved
  against `StormCampaignRegistry`, so **a new arm inherits its campaign from its own name** - no
  second table to keep in step and no way for the count to creep back as it did through T186-T190.
  Nothing was deleted: every variant is still declared, still generated, and every validation
  document still stands - only the `ShaderInstance` is conditional. **Three launches, both code
  paths**: ordinary 5s and 4s with `registered=0 skipped=139`, and **T190 armed with
  `registered=3 skipped=136` in 5s and no failed loads** - that second one is the counter-test that
  matters, because a cleanup that quietly stopped campaigns working would look identical to a
  successful one on an ordinary launch. **Peak shader-load memory is not separately instrumented
  and is not claimed**; what is claimed is 2 programs instead of 140 over 5 seconds instead of 406.
  **Production equality**: `git diff 1deb75f` over `shaders/` and `build.gradle` is **empty**,
  `leanFinalConstants` and `leanProgramVariants` byte-identical, so no image or performance campaign
  was required and none was run. Production programs register unconditionally ahead of the filter,
  and `T191_VARIANT_SCOPE productionAlwaysLoaded=2|campaignScoped=139|unreachable=0` fails the build
  if an arm ever becomes unreachable, if a production program acquires a campaign id, if the loop
  stops filtering, or if a campaign's marker name drifts from the one the driver looks for.
  Evidence in `validation/startup-variant-scope.md`.
- [X] T192 [PERFORMANCE] [US3] Final rain-field attempt: the closed-form ownership bound, under a
  hard stop.
  **HARD GATE: FAIL. The rain-field line is CLOSED.** Quality passes - false rain 4.32-5.30%, well
  clear of T188's 10-16% failure class - but **net SIDE is 1.0792 against a required 1.10x**, so by
  the brief's own rule rain-field optimization closes completely and there is no sixth campaign.
  **A correction that framed the task**: T190's report was headed "what killed it was the
  classification", while its own body gave fallbacks at +2.52 ms against classification at +0.62 ms
  - **the fallback is 4x the classification**, and this brief inherited the error. That set the
  budget before any code was written: SIDE anchor ~32.7 ms, net 1.10x needs <=29.7 ms, T189's
  field-only total was 28.25 ms, so classification **plus** fallback had ~1.47 ms to fit into and
  T190 spent 3.14 - **eliminating classification entirely still leaves 2.52 ms, i.e. ~1.063**. The
  bound attacks the 0.62 and could never reach 1.10 alone. **The derivation**: ownership is
  `dot((worldXZ-centre)/radii, itself) <= 1`; the scaling is per-axis and strictly positive, so an
  axis-aligned cell stays an axis-aligned box after scaling and the minimum of `u2+v2` over it is
  closed form - zero per axis if the interval spans zero, the nearer endpoint squared otherwise. So
  `min > 1` for every descriptor **proves** the cell unowned in one squared inequality per lobe: no
  sampling, no exact SDF, no noise, no sqrt. **Only the dry direction is used** - "inside the
  ellipse" is a *superset* of owned, since ownership also needs group coverage, so claiming SAFE
  OWNED would be unsound in precisely the direction that invents rain. **Proven, not inspected**:
  40,000 random ellipse/cell pairs straddling the boundary against a 25x25 grid - **12,699 proven
  dry, 27,301 not provable, falseSafe=0**, both directions asserted non-vacuous, obtained *before*
  any timing. In the campaign the bound proves **82.97-87.58%** of cells dry. **It is genuinely
  cheaper and not enough**: field build **0.8684 -> 0.6021 ms (-31%)**, `T192_CLASSIFIER_COST`
  **1.0313 accepted at 2.45%** - the field is 3.1% cheaper overall - but the fallback rate is
  unchanged (3.67% vs 2.91%), because **the bound changes what classification costs, not what it
  decides**, which is what it was designed to do and why it cannot reach the gate. The triage clears
  ~85% of cells yet cuts the build only 31%, because the bound itself costs about one evaluation
  pass: ten lobes at three texel fetches each over 262,144 cells. **Quality is byte-identical to
  T190 at both poses** (IoU 0.942487 / 0.955959, same false and missed rain, ownership disagreement
  0) - a direct confirmation of the design claim. **Stack recorded, not banked**: SIDE 1.1802
  accepted at 2.50%, the cleanest stack number of the series, but the brief gates the stack on the
  hard gate. **A T191 defect this campaign found**: two 57-minute runs died as
  `world_entry_lost_during_T135_SAMPLE` with zero cells, and I first blamed memory. T191 scoped
  shader registration by each program's *own* name prefix, assuming a campaign only selects its own
  arms - false when written, since arm matrices reuse earlier campaigns' programs as controls (T190's
  own matrix used `T172_STACK_PRE`). T192 selects `T190_FIELD_SAFE` and `T172_STACK_PRE`; neither
  campaign was armed, so neither loaded, `volumeShader` returned null and the renderer
  session-disabled. T190 ran before scoping existed, so **T192 was the first campaign under it and
  broke immediately** - loudly and totally rather than as a silent wrong number. Registration now
  takes the **union** with the active campaign's arm tables, resolved by reflection rather than a
  hand-maintained map, and the invariant fails the build if it returns to prefix-only
  (`crossCampaignControls=8`). **Why the line closes**: across five campaigns - T188 1.1158 at
  12.36% false rain, T189 1.1228 unchanged, T190 1.0437 at 2.99%, T192 1.0792 at 5.30% - the problem
  is no single classifier. **The field is cheap where it does not matter and expensive where it
  does**: away from storms it is free and provably exact, but on owned columns its support value is
  the one quantity with no cheap bound, because the union is eroded by noise - so correctness there
  costs a fallback and fallbacks there are frequent enough to consume the gain. The closed form
  removed the last avoidable cost; the remainder is intrinsic. **Variants removed from active
  campaign use**: the marker is cleared and T191 scoping makes every rain-field arm inert on an
  ordinary startup (`registered=0 of 142`) without deleting declarations or evidence. **Next task's
  first action is a fresh production-path SIDE attribution** - every counter capture here ran with
  the field forced onto the monolith, so all of them are post-field by construction, and spending
  another 40 minutes on a closing line was the wrong trade. The most recent production-path capture
  is T190 run C, one task ago on the same fixture: rain segment 55.26%, light tap 17.10%, empty-span
  probe 15.77%, primary body 7.30%, refinement 3.29%, rain shaft 1.27%. **Rain stays the largest
  single consumer and is now out of scope** - both the micro-optimization line (T186) and the field
  line close here - so the next architecture target is the largest non-rain uniform consumer: light
  tap plus empty-span probe, together about a third of the descriptor work. Evidence in
  `validation/performance-rain-field-bound.md`.
- [X] T193 [PERFORMANCE] [US3] Fresh production-path attribution, and pick the next architecture
  from it rather than from history.
  **Both remaining non-rain consumers price in the major band, and unlike rain both are
  structurally suited to precomputation.** **Ceilings**: SIDE light removal **1.2490 accepted**
  (2.22% spread), SIDE probe removal 1.2424 (4.33%, rejected on spread), FAR light **1.2302
  accepted**, FAR probe 1.2771 (4.69%, rejected). Every rain-field campaign topped out at 1.12x net,
  so **each of these on its own is worth more than the entire rain architecture was**. **Fresh
  attribution** - the capture T192 could not provide, since no rain-field campaign is armed and the
  monolith is therefore not forced to build a field: SIDE with `shapeUntagged=0` and residual
  fraction 0.0001, rain segment **41.49%** (3.09/px), **light tap 21.96%** (1.64/px), **empty-span
  probe 21.17%** (1.58/px), primary body 9.78%, refinement 3.89%, rain shaft 1.71%, total 7.46 shape
  calls/px. The named consumers sum to `shapeTagged` exactly - **nothing attributed by subtraction,
  no residual bucket**. `exactSdfPerGroupWalk` is 7.04 throughout, so ranking by group walks is also
  ranking by exact-SDF work; two captures agree to 0.03%. **Rain remains the largest single consumer
  at 41.49% and stays out of scope** - both its lines closed on measurement. **The ceilings are
  ceilings**: `t176_nolight` leaves silhouette IoU at exactly 1.0000 while dark-interior retention
  falls to **0.0032** (shape untouched, shading gone), `t180_noprobe` keeps dark-interior at 0.9996
  and moves the silhouette instead (sample placement, not lighting) - two clean, diagnostic damage
  signatures. **The semantics decide the architecture**: the probe is a *threshold* test
  (`paProbeDensity > 0.0008`) whose only output is how far the ray may advance, and **its error is
  one-sided and benign** - "material may be present" costs march time, never correctness; the light
  tap is *integrated* over four taps into extinction, so errors **average** rather than compound.
  **Neither has rain's structure**, and that is the whole argument: rain reachability is an
  existence test ORed ~60 times per ray, which turned a 0.22% per-column error into 12% false rain
  (`1-(1-0.00224)^60 = 12.6%`, measured). **The failure that closed the rain field does not
  transfer**; what transfers is the part that worked - moving descriptor traversal out of the ray
  gave a real 1.15x class gain on a 0.23 ms build. **Selected: a shared coarse 3D
  storm-occupancy/density field serving both probe and light tap** - they are within 0.8 points of
  each other in share and 0.007 in ceiling, so choosing one would be arbitrary, and they consume the
  same quantity at different precisions, amortising one build across **43.13%** of shape calls
  instead of 21%. **This is not the per-lane pruning that failed**: T179 removed 46.75% of exact SDFs
  per lane for 1.0159x and T185 removed 71.50% for 1.0155x, because both changed what a thread
  decided without changing what the shader executed; this removes the traversal from the ray for
  whole coherent workloads, the shape that produced T180's 1.1972x and T188's 1.1158x. **Expected
  gain deliberately not guessed**: the combined ceiling is **not measured** and neither is the 3D
  build cost. **The open risk is dimensionality** - the rain field was 2D and cheap only because
  T184 proved rain support XZ-invariant; storm density is not, so 262,144 cells at 0.23 ms becomes
  1.05M-4.2M voxels, and whether the build leaves any of the 1.24x standing is exactly what T187
  asked before T188 built anything. **Next task is semantics and pricing, not implementation**:
  measure the combined ceiling in one arm, re-measure the probe ceiling to protocol (both its arms
  were rejected on spread), establish what resolution each consumer needs, then price the 3D build
  against the 43% it would remove. **Both lines stay open**; if the shared field does not survive
  pricing they separate cleanly and light is the better-established of the two. **No new shader
  variants** - both ceilings already existed, and T193 owning no programs of its own makes it the
  clean test of T192's cross-campaign registration fix (`registered=2 crossCampaignControls=3`).
  Evidence in `validation/performance-production-attribution.md`.
- [X] T194 [PERFORMANCE] [US3] Price the shared 3D storm field before building it: combined
  light+probe ceiling, semantics audit, build oracle.
  **Pricing only - no field was built and nothing was wired into the renderer.** **Combined
  ceiling measured, not inferred**: FAR `t194_noboth` **1.7450 accepted** (2.92% spread,
  removable 8.73 ms of 20.45); SIDE 1.9981 on one block (1.9351 / 1.9518 on the two others,
  rejected on anchor drift of 3.01% and 3.21% against a 3.00% tolerance - so
  `REJECTED_insufficient_blocks`, not established to protocol). T193's two single-consumer
  ratios multiply to 1.551; the measured combined ratio is 1.745-1.998, so the consumers are
  **superadditive** and inferring would have understated the budget by 13-29%. **Build oracle**:
  each fragment of the 512x512 weather-domain pass sweeps N slab heights through production
  `cloudDensity` (no detail, no precipitation) on the field clock - 16/32/64 slices measured
  **1.6835 / 3.2683 / 6.4679 ms** at SIDE, 0.4014-0.3855 ns/voxel, linear over a 4x range, march
  ratio 1.01 throughout so the build lands entirely in its own column. **Semantics**: light taps
  accumulate `density * stepLength * tapWeight` over four taps with detail on the first two only
  and mip bias `i*0.6` - they need an unbiased estimate; the probe is `density > 0.0008` with
  one-sided error - it needs a conservative upper bound. One field, two channels: R mean, G max,
  RG16F, 4 bytes/voxel. **Domain**: `WeatherExtent` 4096 blocks, slab ~866 blocks for the fixture;
  candidates 256x256x32 (2.1M voxels, 8.4 MB, 0.84 ms derived) to 512x512x64 (16.8M, 67 MB, 6.72
  ms). Break-even before lookup **1.904x SIDE / 1.645x FAR** at 256x256x32. Left open: lookup cost
  (estimated, not measured), update frequency (not audited), the SIDE ceiling to protocol, and
  whether the max channel can be a true bound at one evaluation per voxel. Four variants added,
  all campaign-scoped (`declared=146 registered=4 skipped=142`). Evidence in
  `validation/performance-shared-field-pricing.md`.
- [X] T195 [PERFORMANCE] [US3] Finish the shared-field price: SIDE ceiling to protocol, lookup
  measured, update frequency audited, gate decided.
  **Recovery, not restart**: T194's variants, matrix, registry row and evidence were intact at
  `47fd756`; its `tasks.md` entry, gate log and evidence directory were missing, its marker was
  still armed, and its report had left the SIDE ceiling on one block, the lookup estimated, and
  the rebuild cadence unaudited. Branch merged with `origin/Forge-1.20.1` (`8e22da4`, PR #101/#102)
  as `21758ee` first. **Combined ceiling established**: SIDE `t194_noboth` **1.8595 accepted**
  (four blocks, 1.02% spread, removable **14.04 ms** of 30.36); FAR 1.6353 rejected on 7.00%
  spread (arm cells wandered, anchors held), so T194's accepted FAR 1.7450 stands. **Lookup
  measured**: `t195_lookup` pays one trilinear fetch per light tap into the production scatter
  chain and 13 fetches per scan event from a stand-in 8 MB volume (the 128^3 base noise addressed
  over the weather domain and slab) - **1.8178 accepted** at SIDE (1.94%), so reading the field
  back costs **0.41 ms** at SIDE and 0.27 ms at FAR, 2.9% of what it removes, and that includes
  the scatter chain the no-light ceiling had been generous by. **Update frequency audited in
  code**: `WorldTime` reaches only the funnel swirl and the rain shaft; the body noise is a static
  function of `p - MaterialOffset`, so the field needs no clock, only dirty state - which, because
  the weather bake, descriptor upload and material advection all refresh every frame while a storm
  moves, is every frame in practice, and is what the break-even already charges. **Semantics
  re-verified** and one gap closed: a single evaluation per voxel is a point sample, not a bound,
  so the probe's max channel needs a closed-form bound or a directional super-sample, and the
  probe must read it `NEAREST`. **Break-even on measured values**: net SIDE = 30.43 / (16.74 +
  build): **1.731x at 256x256x32** (0.84 ms, 8.4 MB), 1.513x at 512x512x32, 1.296x at 512x512x64;
  a full 2x2x2 max super-sample at 256x256x32 (16.8M evaluations, the 512x512x64 arm's count,
  6.47 ms measured) still gives 1.296x SIDE but **1.081x FAR** - the only term that can still
  close the architecture, and the first decision of the implementation. Break-even voxel count
  27.2M SIDE / 15.9M FAR. **GATE: PASS.** No real field exists; the next task is the max-channel
  bound, then the quality harness, then a 256x256x32 `(mean, max)` RG16F field. One variant
  added (147 -> 148), campaign-scoped (`registered=2 skipped=145 crossCampaignControls=3`).
  **Campaign infrastructure**: the T194 client idled 30 of its 45 minutes after
  `T132_AUTORUN_FINISHED`, the unused T098 capture set took 3.5 minutes, and the 44-stage
  production-context counter capture took 7-9 s of every 11 s cell while being identical for every
  cell of a pose; the driver now stops the client on `run/t132-autorun-exit.txt`, finishes
  program-arm campaigns after their report, and captures counters once per pose. Evidence in
  `validation/performance-shared-field-lookup.md`.
- [X] T196 [PERFORMANCE] [US3] Build the shared 3D storm field for light taps and empty-span
  probes, prove the probe floor sound, and measure it net of its build.
  **Built, sound, and 2.06x net at SIDE.** One 256x256x32 field over `WeatherExtent` and the slab,
  an 8x4 atlas in a single 2048x1024 **RG16F** target (8.4 MB), one fullscreen draw before the
  timed march on the field clock: R the detail-free production density (the light cone reads it
  bilinearly across two slices, four taps unchanged), G the **probe floor** - a lower bound on the
  storm body's noise threshold over the node's dual cell, from `L >= 1 - 1.65 E` (the
  strength-fill product is bounded) and `E` at the node's union distance dilated by the largest
  drop the distance can take across the cell: horizontal half-diagonal times the lobes' ellipse
  aspect plus vertical half-extent times the profile's **cell-local** height slope, both published
  by the walk `cloudDensity` already does, so **one walk per node** serves both channels against
  the rejected 2x2x2's eight. The probe fetches the nearest node and tests the exact base noise at
  its point: one texel and one noise fetch. **Soundness oracle** (pass 2, 2,097,152 nodes x 43
  production-exact reference samples, CPU readback): six launches took the floor from 11,667
  underestimates (isotropic dilation) to **0 at both poses** - the lobe pseudo-distance's vertical
  Lipschitz constant is `|dR/dy|` and reaches **8.6** on the anvil flare, the T121 vertical
  rejection needs the cell's half-extent as margin, and the last "misses" were the oracle
  sampling the tile boundary that belongs to the neighbouring node. Useful empty retained
  **98.5% / 99.0%**. On the ray: `stormFieldProbeFalseEmpty=0` at both poses, false-occupied
  69,245 against 11,498 true (the bound's price, paid in fine steps). **Build 1.77 ms SIDE /
  1.65 FAR** (twice T195's projection: no reach early-out, vertical margin, nine weather texels
  per node). **Net, three accepted blocks each**: SIDE `field_both` **2.0570x** (0.11% spread,
  29.42 -> 12.69 + 1.61 ms), FAR **1.5489x** (1.22%); light alone 1.14x / 1.05x, probe alone
  1.10x / 1.06x; the pair beats T195's 1.8595x removal ceiling because the field probe keeps the
  scan's empty-span skipping that the ceiling arm lost, and fewer primary steps mean fewer light
  cones. **Workload**: `shapeLight` 1,181,752 -> 0, `shapeProbe` 745,951 -> 0, exact SDFs
  **-45.2%**, shape calls -26.6%, `shapeUntagged=0`, residual 274 of 3.4M; the new largest
  consumer is the rain segment at 67.7%, then the primary march at 22.6% (grown by the probe's
  conservativeness). **Quality**: the light channel keeps what rejected light3 - dark-interior
  retention **0.956** (light3 0.072), self-shadow contrast 0.938, silhouette IoU 1.000 - and loses
  a third of the shadow pockets (**0.704**) and a tenth of valley depth, which is what a 16-block
  node does to an envelope edge; the probe channel is near-lossless (SSIM 0.966, IoU 0.992, 0.6%
  changed pixels). A hybrid arm (first two taps exact) costs half the gain (1.32x) and does not
  restore the pockets (0.723) - closed. **Verdict: performance and soundness pass; light quality
  is a conditional pass on the pocket metric, and the resolution question is now about quality,
  not economics** - 512x512x32 would still net about 1.48x. Next: XZ resolution for the pockets, a
  tighter floor (86% of "possible" verdicts are false), dirty-state reuse measured on a moving
  fixture (the frozen fixture would have hidden the build), then production wiring. Five variants
  (147 -> 152), three workload views (47), a T196 sandbox invariant that compiles every field
  program and proves FINAL bakes the field out; `mods.toml` expands `simpleclouds_mandatory` so
  the dev client boots without Simple Clouds after Forge-1.20.1 made it mandatory. Each launch
  six minutes, self-exiting. Evidence in `validation/performance-shared-field.md`.
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
