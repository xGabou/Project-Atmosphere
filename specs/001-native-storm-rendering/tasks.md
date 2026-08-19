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
- Existing task IDs remain stable for audit history. Phase 4R uses T074-T099, Phase 4S uses T100-T118, and Phase 4P uses T119-T123. Phases are placed by dependency order, not by numeric sorting.
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

- [x] T031 [US2] Add failing local-versus-global precipitation occupancy, unsupported-shaft rejection, deterministic coarse-segment integration, and clear-air fast-path assertions in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricStabilityDiagnosticsSandbox.java` (depends on T030) [FR-007; SC-001, SC-004]
- [X] T032 [US2] Add failing GPU-equation fixture vectors and visible-boundary/camera-density agreement assertions for every storm role and overlap case in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` (depends on T017, T030) [FR-008; SC-003] — REOPENED by T041 audit
- [x] T033 [US2] Add failing history-invalidation assertions for topology generation, world, dimension, owner, resource, and resolution changes plus history-retention assertions for normal interpolation in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricStabilityDiagnosticsSandbox.java` (depends on T030) [FR-009; SC-004]

### Whiteout, Rain, and History Implementation

- [X] T034 [P] [US2] Evaluate adopted `StormRenderSnapshot` descriptors through `StormLobeEvaluator` without per-query allocation in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/ClientCloudVisualDensity.java` (depends on T007, T017, T032) [FR-008] — REOPENED by T041 audit
- [x] T035 [US2] Publish the exact successfully composited storm generation to visual-density state and keep `CameraCloudDensityTracker` on its existing interface in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/CameraCloudDensityTracker.java` (depends on T028, T034) [FR-008]
- [x] T036 [P] [US2] Change volumetric rain eligibility and empty-space pretests from global precipitation to local weather/morphology/direct-storm support in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` (depends on T026, T031) [FR-007]
- [X] T037 [US2] Implement world-anchored deterministic coarse-segment rain integration, local base attachment, and body/rain step separation in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` (depends on T036) [FR-007] — REOPENED by T041 audit
- [x] T038 [US2] Add adopted storm topology generation and effective resolution generation to history validity while preserving history during descriptor interpolation/advection in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderer.java` (depends on T033, T035) [FR-009]
- [x] T039 [US2] Clear pending/adopted storm density and temporal state on disconnect, world/dimension/owner change, resource reload, resize, and direct-path disable in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/ClientCloudVisualDensity.java` (depends on T035, T038) [FR-008-FR-009]
- [x] T040 [US2] Add regression assertions proving nearby custom rain/snow and its vanilla fallback remain independently owned in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricStabilityDiagnosticsSandbox.java`, without changing `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/CustomPrecipitationRenderer.java` (depends on T031, T037) [FR-007, FR-020]
- [x] T041 [US2] Run dry/local-rain/remote-rain/boundary-crossing stationary and moving 60-second captures and record density agreement, history resets, and artifact results in `specs/001-native-storm-rendering/validation/us2-rain-whiteout-stability.md` (depends on T031-T040) [SC-001, SC-003-SC-004]

**Checkpoint**: User Stories 1 and 2 are independently testable; structured storm occupancy, rain, whiteout, and history agree.

---

## Phase 4R: Storm Morphology Correction

**Purpose**: Correct the T041-audited density architecture without redesigning the existing descriptor, packing, build, snapshot, async, render-thread, server, networking, saved-data, forecast, Simple Clouds, custom precipitation, or camera-density ownership systems.

**Goal**: Make the descriptor set the authoritative visible storm field through descriptor-local distance-like evaluation, lobe/group smooth unions, local BASE underside and rain attachment, valid descriptor slots, safe lifecycle/history behavior, and acceleration-only group candidates.

**Independent Test**: Run the fixed complete-group silhouette, locality, independent GLSL parity, composition, rain/body, slot/fallback, async/signature, history, and acceleration regressions. *(The original "ten-item visual checklist" acceptance is superseded; T098 and T099 now use the two-part positive/negative checklist introduced by Phase 4S.)*

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

- [X] T097 Run the corrected morphology, locality, independent GLSL parity, composition, rain/body, slot/fallback, async/signature, history, precipitation, and acceleration regressions plus the US1/US2 sandboxes; record passing results in `specs/001-native-storm-rendering/validation/phase4r-automated.md` and verify each T080 expected failure is closed without weakening assertions (depends on T081-T096) [SC-001-SC-004, SC-010]
- [ ] T098 **[REOPENED 2026-08-19 - revised criteria]** Replace `specs/001-native-storm-rendering/validation/us1-readable-storms.md` evidence with new below/beside/inside/above captures and a two-part checklist. **Positive half (FR-023, all nine must be present)**: a broad continuous lower cloud base; a dense convective/core region; vertical tower development emerging naturally from the base; progressive vertical narrowing where appropriate; a broad upper anvil; multi-scale billowing across the visible storm body; surface variation at multiple spatial frequencies; irregular but coherent silhouette curvature; continuous transitions between base, tower, core, and anvil. **Negative half (FR-024, none may be present)**: large smooth balloon surfaces; large regions of visually uniform density; visible ellipsoid or sphere primitives; isolated ears or bulb protrusions; descriptor seams; rectangular or vertical walls; flat slabs; uniformly smooth silhouettes. Record the T118 measured proxy values alongside each capture, and the live `cell.density` calibration flagged as a risk by T118. The capture plan, the diagnostic command, and the two-part checklist are prepared in `specs/001-native-storm-rendering/validation/t098-manual-checklist.md`. This task is not passable while any positive item is absent, even if every negative item is clear (depends on T118; fulfills reopened T030) [FR-023, FR-024; SC-001-SC-002, SC-011]
- [ ] T099 **[REOPENED 2026-08-19 - revised criteria]** Replace `specs/001-native-storm-rendering/validation/us2-rain-whiteout-stability.md` evidence with new dry/local-rain/remote-rain/boundary stationary and moving captures proving rain remains attached to the **final noise-formed** storm density (not the coverage envelope) and whiteout remains stable, then record the final morphology pass/fail gate. Not passable until T098's positive criteria are satisfied (depends on T098, T115, T116, T118) [FR-021, FR-022; SC-001, SC-003-SC-004, SC-011]

**Checkpoint (superseded 2026-08-19)**: Phase 4R established that the descriptor set - not a statistical envelope or candidate grid - is the evaluated storm field. Phase 4S narrows that result: the descriptor union is a bounded coverage envelope, and the noise field forms the visible body. T098 and T099 are reopened under the revised positive morphology criteria and now depend on T118.

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

**Then**: T098 and T099 execute here, after T118. They are listed under Phase 4R's Revalidation Gate to keep their audit history in place; their acceptance criteria are the revised positive/negative checklist recorded there.

**Checkpoint**: The coverage envelope comes from descriptors, the visible body comes from noise, and
morphology is measured positively. T118 unblocks the reopened T098 and T099.

---

## Phase 4P: Storm Performance Architecture

**Purpose**: Keep the corrected density model practical at the supported quality modes.

**Goal**: Satisfy FR-027 and SC-017 through structural changes to descriptor evaluation cost.

**Independent Test**: Measure storm descriptor evaluation cost per sample and per frame through the
existing storm diagnostics before and after each task, and confirm the rendered result is unchanged.

**Ordering**: These tasks are **not** blocked by T098/T099. Each is a separate task and a separate
commit from visual-correctness work. No task in this phase may alter the rendered result; a
performance change that moves the image is a correctness defect. T119 is the one task permitted to
run concurrently with Phase 4S, because the corrected union no longer discards zero-density lobes
and may be impractical without it.

- [ ] T119 [US3] Precompute descriptor group topology during the existing CPU build in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeSpatialIndex.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormGeometryBuildCoordinator.java`, and supply per-group first/end indices or equivalent compact metadata to the shader, replacing the per-sample `stormGroupFirstIndex()` / `stormGroupEndIndex()` descriptor scans in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh`. The metadata is acceleration only and never defines density (may run concurrently with T108-T113 when the corrected union is otherwise impractical) [FR-027; SC-017]
- [X] T120 [US3] Replace `bool groupVisited[MAX_STORM_GROUPS]` with a compact integer bit mask or another GPU-friendly representation in `directStormShape()` and `directStormSegmentMayIntersect()` in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh`, removing the per-call array allocation and clear loop (depends on T111) [FR-027; SC-017]
- [ ] T121 [US3] Introduce a cheaper storm lighting proxy for lighting cone taps in `lightMarchOpticalDepth()` and its endpoint/capped/refined/no-detail variants in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` so the full cloud density function is not evaluated per tap; document the proxy in `contracts/storm-density-composition.md` and confirm the visual acceptance criteria still pass (depends on T113, T118) [FR-006, FR-027; SC-011, SC-017]
- [ ] T122 [US3] Audit repeated descriptor texture fetches inside `cloudDensity()` and `lightMarchOpticalDepth()` in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh`, hoist or reuse fetches re-issued for the same descriptor within one evaluation, and record the resulting fetch count in the storm diagnostics (depends on T119, T121) [FR-027; SC-017]
- [ ] T123 [US3] Define and enforce a bounded per-sample and per-frame descriptor evaluation cost, report it through `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudFrameDiagnostics.java` and the storm diagnostic capture, and document the bound in `plan.md` (depends on T119-T122) [FR-027; SC-017]

**Status 2026-08-19**: T120 is complete — it is provably free of any rendered-result change and is
verified by the production shader compile check and the full regression suite. T119, T121, T122 and
T123 are **deferred, not blocked**:

- **T119** was the candidate for being inseparable from the correctness refactor. It is not. The
  corrected union no longer discards zero-density lobes, but the per-lobe evaluation cost is
  essentially unchanged: the pre-correction path also evaluated each lobe fully and only skipped
  afterwards. The per-sample `stormGroupFirstIndex()` / `stormGroupEndIndex()` scans that T119
  removes were already present before Phase 4S, so they are a pre-existing cost rather than one the
  correction introduced. T119 changes the descriptor upload contract, and its benefit can only be
  measured on the reference hardware, so it is sequenced with the other measured work.
- **T121** substitutes a cheaper lighting proxy, which by construction changes the rendered result
  and must be judged against the visual acceptance criteria. It cannot be validated before T098.
- **T122** and **T123** need before/after GPU measurements on the reference machine to mean
  anything, and T123's bound is meaningless until T119 and T121 have set the cost profile.

**Checkpoint**: Descriptor evaluation cost is bounded and reported, with the rendered result
unchanged from the Phase 4S baseline.

---

## Phase 5: User Story 3 - Scalable Quality Modes (Priority: P3)

**Goal**: Preserve five progressively increasing modes, bounded predictable storm LOD, stable adaptive degradation/recovery, and Ultra's target performance without disconnecting storm groups.

**Independent Test**: Run the same severe-weather route in Low, Low 24, Medium, High, and Ultra, then create sustained load and recovery. Every mode renders the connected storm, reports correct effective settings, respects floors/ceilings, and avoids oscillation.

### Tests for User Story 3

- [ ] T042 [US3] Add failing preset-table, monotonic detail, target/floor, EWMA, 30-frame downgrade, 180-frame recovery, 30-second cooldown, adaptive-disable, and reset assertions in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` (depends on T099) [FR-010-FR-012; SC-005, SC-007]
- [ ] T043 [US3] Add failing detail-distance clamp, 128-block cross-fade, complete-group LOD, no-hole/no-double-weight, and capacity-to-map fallback assertions in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` (depends on T042) [FR-010-FR-011]

### Configuration, Quality, and LOD Implementation

- [ ] T044 [P] [US3] Add `adaptiveCloudQuality` defaulting true and `nativeStormDetailDistance` defaulting 1536 with range 256-4096 to `src/main/java/net/Gabou/projectatmosphere/config/AtmoCommonConfig.java` (depends on T042) [FR-010-FR-011]
- [ ] T045 [P] [US3] Extend nominal steps/resolution, lighting/detail work, GPU targets, and per-mode floors for Low, Low 24, Medium, High, and Ultra in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricQualityProfile.java` (depends on T042) [FR-010-FR-012]
- [ ] T046 [US3] Replace the scalar governor with immutable adaptive state, GPU-time EWMA, sustained thresholds, discrete bands, floor/ceiling clamps, transition generation/reason, and cooldown in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/CloudFrameTimeGovernor.java` (depends on T042, T045) [FR-011; SC-007]
- [ ] T047 [US3] Read visual config once during frame setup, clamp storm detail distance to total render distance, and apply effective quality state in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java` (depends on T044-T046) [FR-010-FR-011]
- [ ] T048 [US3] Add complete-group analytic/map LOD classification, full-detail range, 128-block transition weights, and map-only handling for distance/capacity omissions in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeSpatialIndex.java` (depends on T043, T047) [FR-001, FR-010-FR-011]
- [ ] T049 [US3] Apply analytic/map cross-fade without double density and scale only bounded refinement/lighting work—not group integrity—in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` (depends on T045, T048) [FR-001, FR-010-FR-011]
- [ ] T050 [US3] Recreate render targets and invalidate history once on discrete resolution transitions while leaving step-only changes history-valid in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderer.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderTargets.java` (depends on T038, T046-T047) [FR-009-FR-011]
- [ ] T051 [US3] Remove stable-frame list/map/descriptor diagnostic allocations and reuse bounded sort, descriptor, candidate, and upload storage in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeSpatialIndex.java` (depends on T047-T050) [FR-012, FR-019]
- [ ] T052 [US3] Run the five-mode route plus forced load/recovery and record effective settings, visual monotonicity, transitions, rebuild rate, and preliminary timings in `specs/001-native-storm-rendering/validation/us3-quality-lod.md` (depends on T042-T051) [SC-005, SC-007]

**Checkpoint**: All five modes and adaptive LOD are independently verifiable; Ultra is ready for the final controlled performance gate.

---

## Phase 6: User Story 4 - Actionable Renderer Diagnostics (Priority: P4)

**Goal**: Identify ownership, storm structure, rain, quality, capacity, caching, async state, history, and timing from bounded on-demand diagnostics without normal log/allocation overhead.

**Independent Test**: In one severe-storm session, use existing `/pa cloud volumetric` commands to identify the active renderer, direct/map group workload, role/candidate capacity, rain and camera density, effective quality, rebuild/history reasons, and timings; switch structure/rain/final views without enabling continuous logs.

### Tests for User Story 4

- [ ] T053 [US4] Add failing counter-semantic, bounded-capture, deterministic-format, no-normal-string-formatting, and fallback-reason assertions in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java` (depends on T052) [FR-013, FR-019; SC-009]

### Diagnostic Implementation

- [ ] T054 [P] [US4] Implement primitive frame counters and bounded on-demand capture for group/role/descriptor/tile/cache/async/generation/LOD/fallback/timing state in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeDiagnostics.java` (depends on T053) [FR-013, FR-019]
- [ ] T055 [US4] Publish compact storm workload, effective quality, GPU timing, rebuild frequency, history reason, and camera-density generation through `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudFrameDiagnostics.java` (depends on T035, T046, T054) [FR-013]
- [ ] T056 [P] [US4] Add `storm_body`, `storm_envelope`, `storm_candidates`, `precipitation`, and `storm_combined` IDs and safe final-view history restoration in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRaymarchDebugView.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudDebugConfig.java` (depends on T053) [FR-013]
- [ ] T057 [US4] Implement role, envelope/LOD, candidate/overflow, precipitation, and combined shader outputs in `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` (depends on T049, T056) [FR-013]
- [ ] T058 [US4] Add `/pa cloud volumetric diagnostics storm`, extend summary diagnostics, expose new debug views, and extend governor reset output in `src/main/java/net/Gabou/projectatmosphere/command/TelemetryDebugClientCommand.java` (depends on T054-T057) [FR-013; SC-009]
- [ ] T059 [US4] Verify per-frame logging remains development-only/opt-in and move all storm text/per-group enumeration behind explicit capture paths in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeDiagnostics.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudFrameDiagnostics.java` (depends on T054-T058) [FR-019]
- [ ] T060 [US4] Run one diagnostic session across final/body/envelope/candidates/precipitation/combined views and record whether all contract questions are answered in `specs/001-native-storm-rendering/validation/us4-diagnostics.md` (depends on T053-T059) [SC-009]

**Checkpoint**: All four user stories are independently functional and observable through the existing command surface.

---

## Phase 7: Compatibility, Fallback, and Release Validation

**Purpose**: Protect optional ownership, rollback, server safety, existing regressions, visual acceptance, and the controlled Ultra performance target.

### Simple Clouds and Legacy Fallback

- [ ] T061 [P] Create native/Simple-Clouds/field-fallback owner-transition assertions in `src/test/java/net/Gabou/projectatmosphere/clouds/client/render/CloudRenderOwnershipSandbox.java` [FR-016-FR-018; SC-008]
- [ ] T062 Register `cloudRenderOwnershipSandbox` under `check` in `build.gradle` (depends on T061)
- [ ] T063 Ensure Simple Clouds ownership short-circuits before native descriptor selection, worker submission, target preparation, upload, and density publication in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/ClientCloudRenderOwnership.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java` (depends on T028, T061) [FR-016-FR-017]
- [ ] T064 Implement direct-subpath failure state so missing membership, capacity, async saturation, stale builds, or descriptor/candidate allocation/upload failures retain a valid generation or broad map LOD in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormGeometryBuildCoordinator.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java` (depends on T022, T054) [FR-018]
- [ ] T065 Verify wider native failure still follows the existing session-disable and developer legacy-field-or-vanilla rollback policy without changing its property/config contract in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderHook.java` and `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/ClientCloudRenderOwnership.java` (depends on T063-T064) [FR-018]
- [ ] T066 Run default `runClient` and `runClient -PenableSimpleCloudsRuntime=true` through startup, world entry, dimension transition, resource reload, and optional-integration failure; record owner and zero-native-work evidence under SC ownership in `specs/001-native-storm-rendering/validation/compatibility-and-fallback.md` (depends on T060-T065) [FR-016-FR-018; SC-008]

### Automated and Visual Regression

- [ ] T067 Run `stormVolumetricGeometrySandbox`, `cloudMorphologyTopologySandbox`, `volumetricStabilityDiagnosticsSandbox`, `materialAdvectionSandbox`, `cloudRegionMotionSandbox`, `cloudFieldSandbox`, `cloudRenderOwnershipSandbox`, `architectureBoundaryCheck`, `check`, and `build`; record exact results in `specs/001-native-storm-rendering/validation/automated-regression.md` (depends on T030, T041, T052, T060, T062) [SC-010]
- [ ] T068 Run `runServer` and record that no client renderer, shader, Minecraft client singleton, or LWJGL class loads on the dedicated server in `specs/001-native-storm-rendering/validation/dedicated-server.md` (depends on T067) [FR-015, FR-017]
- [ ] T069 Execute the complete below/beside/inside/above, isolated/overlap, lifecycle, detail-boundary, total-distance, dry/rain/whiteout, camera-motion, resize, resource-reload, dimension, terrain-depth, and all-quality visual matrix from `quickstart.md`, attaching pass/fail evidence to `specs/001-native-storm-rendering/validation/visual-regression.md` (depends on T030, T041, T052, T060, T066-T068) [SC-001-SC-005, SC-008-SC-009]

### RTX 4070 Performance Gate

- [ ] T070 Capture a ten-minute post-convergence Ultra run on the specified plugged-in RTX 4070 laptop at 1920×1080, no external shader pack, and approximately 2000-block cloud distance; record p50/p95/p99 total frame time, cloud GPU stages, CPU build/upload, rebuild/cache/overflow, allocation, and adaptive-transition data in `specs/001-native-storm-rendering/validation/rtx4070-ultra-performance.md` (depends on T052, T055, T067, T069) [FR-012; SC-006-SC-007]
- [ ] T071 Close any measured Ultra gate failure using bounded changes only in `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricQualityProfile.java`, `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/CloudFrameTimeGovernor.java`, `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormLobeSpatialIndex.java`, `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/VolumetricCloudRenderer.java`, or `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh`, then append before/after evidence to `specs/001-native-storm-rendering/validation/rtx4070-ultra-performance.md` (depends on T070; no performance change may be made before Phase 4R completes at T099) [FR-001, FR-010-FR-012, FR-020; SC-005-SC-007]
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
                    -> Phase 4S Storm Density Architecture Correction (T100-T118)
                        |                          \
                        |                           -> Phase 4P Storm Performance Architecture
                        |                              (T119-T123; T119 may start with 4S)
                        -> T098 / T099 Morphology Validation Gates (reopened)
                            -> US3 Scalable Quality Modes
                                -> US4 Diagnostics
                                    -> Phase 7 Compatibility and Release Validation
```

- Phase 1 has no implementation dependency and establishes the baseline/harness.
- Phase 2 depends on T002-T003 for its test entry point and blocks every story.
- US1 depends on all foundational contracts and produces the direct structured-storm path.
- US2 depends on the adopted render snapshot and direct shader path from US1.
- Phase 4R depends on the T041 audit and is complete through T097. Its T081, T085, T086, T089, T090, and T097 acceptance criteria are superseded by Phase 4S; those tasks stay checked as implementation history and are not rewritten.
- Phase 4S depends on Phase 4R and owns the corrected density architecture. T100 must precede every Phase 4S regression so no threshold is set without a derivation, and T107 must record meaningful fail-first results before any Phase 4S production change.
- Phase 4P depends on Phase 4S for its correctness prerequisites, **not** on T098/T099. T119 may run concurrently with T108-T113 when the corrected union - which no longer discards zero-density lobes - is otherwise impractical. Phase 4P tasks are separate commits from visual-correctness work and may not alter the rendered result.
- T098 and T099 are reopened under the revised positive morphology criteria (FR-023, FR-024) and depend on T118. They remain release gates for the visual result.
- US3 depends on T099 for *visual* acceptance. The former rule that no quality or performance work of any kind may start before T099 is **removed**: Phase 4P structural performance work proceeds on its own dependencies. T042's dependency on T099 is retained because quality-mode scaling is judged against the accepted visual result. T050 also retains its dependency on US2 history work.
- US4 depends on US1 for workload sources and on the effective quality and visual-density state from US2/US3 for a complete report.
- Phase 7 depends on all desired stories; T070-T072 are post-correction hard release gates, not optional polish. The approximately 39 FPS / 23 ms audited Ultra result is not final evidence and must be re-measured after Phase 4S and Phase 4P.

### User Story Dependency Graph

| Story | Required predecessors | Independent completion signal |
|---|---|---|
| US1 (P1) | Setup + Foundational; reopened work closes in Phase 4S | Corrected storm passes the revised two-part T098 checklist: all nine positive features present, none of the eight rejected forms present |
| US2 (P2) | US1; reopened work closes in Phase 4S | Replacement T099 proves rain and whiteout follow final noise-formed density |
| Phase 4R | T041 audit | T097 recorded corrected union evidence; superseded in part by Phase 4S |
| Phase 4S | Phase 4R | T118 records corrected density-architecture evidence with model-derived thresholds and unblocks T098/T099 |
| Phase 4P | Phase 4S correctness prerequisites | T123 reports a bounded descriptor evaluation cost with the rendered result unchanged |
| US3 (P3) | T099 for visual acceptance; T050 also needs US2 history work | T052 proves all modes, LOD, and adaptive stability |
| US4 (P4) | US1 plus completed US2/US3 state providers | T060 answers the diagnostic contract from one session |

### Key Task Chains

- **Source geometry**: T011 -> T015 -> T016 -> T018.
- **Direct representation**: T004-T007 -> T012-T013 -> T017-T021 -> T023-T027.
- **Async/cache lifecycle**: T005-T010 -> T014 -> T021-T022 -> T028 -> T064.
- **Rain/whiteout/history**: T031-T033 -> T034-T040 -> T041 -> T078-T079 -> T089, T093-T095 -> T097 -> T115-T116 -> T099.
- **Morphology correction (Phase 4R)**: T074-T079 -> T080 -> T081-T090 -> T091-T096 -> T097.
- **Density architecture (Phase 4S)**: T100 -> T101-T106 -> T107 -> T108-T111 -> T112-T114 -> T115-T117 -> T118 -> T098 -> T099.
- **Performance architecture (Phase 4P)**: T119 (may start with T108) -> T120 -> T121 -> T122 -> T123.
- **Quality/LOD**: T099 -> T042-T043 -> T044-T051 -> T052 -> T070-T071.
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
T119 group topology (may start with T108)  ||  T120 bit-mask visitation (after T111)
```

T121, T122, and T123 follow in order because each measures the previous task's result.

### User Story 3

After T099 for quality-mode visual acceptance, and then after T042-T043:

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

Phase 4R may use only the parallelism explicitly marked in T079; its tests complete and fail meaningfully before production fixes. Phase 4S follows the same fail-first discipline through T107. Phase 4P may run alongside Phase 4S on its stated dependencies, in separate commits, and may not alter the rendered result. US3 quality-mode work remains gated on T099 for visual acceptance. Begin US4's standalone diagnostic data model only after the corrected workload/counter meanings and the test contract in T053 are stable.

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
5. **Phase 4P** makes the corrected model practical: precomputed group topology, compact group metadata, bit-mask visitation, a cheaper lighting proxy, a descriptor-fetch audit, and a bounded evaluation cost. It runs on its own dependencies rather than behind the morphology gate, in separate commits.
6. **US3** adds predictable mode scaling, LOD, and adaptive performance policy after T099 accepts the visual result.
7. **US4** exposes bounded evidence for ownership, workload, artifacts, and timing.
8. **Phase 7** proves Simple Clouds boundaries, legacy fallback, server safety, full regressions, and the post-correction RTX 4070 release gate.

## Traceability Summary

| Requirement area | Primary tasks |
|---|---|
| Connected 3D stages and overlap | T011-T030, T074-T077, T080-T090, T096-T098 |
| Coverage envelope vs. noise-formed body | T100-T102, T107, T110-T114, T118 |
| Geometric distance field and world-space unions | T104, T107-T109, T111, T118 |
| Positive morphology criteria and derived thresholds | T100, T102-T106, T118, T098-T099 |
| Interior detail erosion | T101, T103, T113, T118 |
| Bounded descriptor evaluation cost | T119-T123 |
| Rain, whiteout, temporal stability | T031-T041, T078-T079, T089, T093-T095, T097-T099 |
| Descriptor validity, fallback, async, signatures | T079, T087-T088, T091-T092, T096-T097 |
| Five modes, adaptive quality, LOD | T099 -> T042-T052 |
| Bounded diagnostics | T053-T060 |
| Server/network/save preservation | T018, T027-T030, T067-T068 |
| Simple Clouds ownership | T061-T066 |
| Legacy fallback | T064-T066 |
| Automated/visual regression | T067-T069, T072 |
| RTX 4070 performance | T119-T123 -> T070-T072 |
| Scope and no unrelated redesign | Every implementation task is limited to paths named in `plan.md`; T067-T073 enforce the boundary |

## Notes

- `[P]` is used only where file ownership and data dependencies permit parallel work after stated predecessors complete.
- Tests are intentionally placed before their implementation and must fail for the expected missing/incorrect behavior first.
- Every new geometry regression test must demonstrably fail against the audited implementation before its corresponding fix is implemented; T080 records this gate for Phase 4R and T107 records it for Phase 4S.
- Phase 4S thresholds come from `validation/morphology-thresholds.md` and are derived from the shader's configured erosion strength, noise amplitude, octave weights, and octave frequencies. Adjusting a threshold to accommodate an observed result, without a recorded model change, violates FR-026.
- **Ordering rule removed**: the previous rule that no performance work of any kind could begin before T099 no longer applies. Phase 4P runs on its own dependencies. Performance changes stay in separate tasks and commits from visual-correctness changes, and no Phase 4P change may alter the rendered result.
- T042 remains blocked by T099 because quality-mode scaling is judged against the accepted visual result. The approximately 39 FPS / 23 ms Ultra observation must be re-measured after Phase 4S and Phase 4P.
- Phase 4S and Phase 4P preserve every already validated behavior: server-authoritative weather, forecast behavior, network packets, saved weather state, Simple Clouds ownership, legacy renderer fallback, rain placement, whiteout behavior, history invalidation semantics, and the candidate texture as a scheduling/index hint rather than authoritative geometry.
- Do not change packet registration, packet encoding, saved-data schemas, forecast orchestration, Simple Clouds managed systems, or unrelated cloud families.
- Do not perform Minecraft, RenderSystem, render-target, shader, buffer-upload, or OpenGL access from the async worker.
- Preserve unrelated working-tree changes and commit/review tasks in small logical groups.
