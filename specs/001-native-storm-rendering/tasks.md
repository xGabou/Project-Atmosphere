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
- Existing task IDs remain stable for audit history. Phase 4R uses T074-T099, Phase 4S uses T100-T118, Phase 4P uses T119-T123, and the renderer-wide correction gate uses T127-T134. Phases are placed by dependency order, not by numeric sorting.
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
**T098 erosion hypothesis FALSIFIED 2026-08-28.** Measured per-role on real T134 geometry:
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

- [ ] T098 **[REOPENED 2026-08-19 - renderer-wide gate]** Replace `specs/001-native-storm-rendering/validation/us1-readable-storms.md` evidence with new below/beside/inside/above captures and a two-part checklist only after T133 passes. **Positive half (FR-023, all nine must be present)**: a broad continuous lower cloud base; a dense convective/core region; vertical tower development emerging naturally from the base; progressive vertical narrowing where appropriate; a broad upper anvil; multi-scale billowing across the visible storm body; surface variation at multiple spatial frequencies; irregular but coherent silhouette curvature; continuous transitions between base, tower, core, and anvil. **Negative half (FR-024, none may be present)**: large smooth balloon surfaces; large regions of visually uniform density; visible ellipsoid or sphere primitives; isolated ears or bulb protrusions; descriptor seams; rectangular or vertical walls; flat slabs; uniformly smooth silhouettes. Record T133's physical-scale, vertical-material, and performance evidence with the capture set. This task is not passable while any positive item is absent, even if every negative item is clear (depends on T133; fulfills reopened T030) [FR-023, FR-024, FR-028-FR-031; SC-001-SC-002, SC-011, SC-018-SC-020]
- [ ] T099 **[REOPENED 2026-08-19 - revised criteria]** Replace `specs/001-native-storm-rendering/validation/us2-rain-whiteout-stability.md` evidence with new dry/local-rain/remote-rain/boundary stationary and moving captures proving rain remains attached to the **final noise-formed** storm density (not the coverage envelope) and whiteout remains stable, then record the final morphology pass/fail gate. Not passable until T098's positive criteria are satisfied (depends on T098, T115, T116, T118) [FR-021, FR-022; SC-001, SC-003-SC-004, SC-011]

**Checkpoint (superseded 2026-08-19)**: Phase 4R established that the descriptor set - not a statistical envelope or candidate grid - is the evaluated storm field. Phase 4S narrows that result: the descriptor union is a bounded coverage envelope, and the noise field forms the visible body. T098 remains open behind the T127-T133 renderer-wide correction gate; T099 remains blocked by T098.

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
- [X] T128 [US4] Add fail-first deterministic and on-demand runtime vertical material-continuity diagnostics for the existing live-calibrated ten-descriptor fixture and live `3c039aa7` strengths. Sample a fixed centre X/Z at no more than 16-block Y intervals and report active descriptor roles/IDs, coverage/strength, base noise/carrier, detail erosion, final density, extinction, light optical depth, direct light, ambient light, final rendered contribution, and direct/fallback plus weather/slab height-normalization branch flags. Keep CPU/shader values independently comparable (depends on T126) [FR-029; SC-019]
- [X] T129 [US1] Run T128 against the current composition before a correction and record fail-first evidence identifying the first lower/upper discontinuity as geometry/coverage, density/noise, optical medium, lighting, or sampling/history. Rule out every earlier stage before authorizing a correction; do not substitute another role-overlap or union-radius iteration for measured attribution (depends on T127-T128) [FR-029; SC-019]
- [X] T130 [US3] Capture the reference performance architecture baseline: raymarch time, primary and lighting-cone density samples, group-range scans, descriptor fetches, envelope rejections, empty-space skips, termination behavior, and register/scratch-risk locations. Freeze comparison captures and a material-trace/image tolerance for visually-neutral optimization; classify every proposed optimization as neutral or quality-changing (depends on T126) [FR-030; SC-020]
- [X] T131 [US1] Add a deterministic fail-first regression for the measured cause from T129, then correct only that single-medium discontinuity in the responsible renderer stage. Role geometry may change only if T129 attributes the first discontinuity to geometry/coverage; preserve Phase 4S base scale, warp, erosion hierarchy, live strengths, final-density rain/whiteout, parity, and all ownership/fallback behavior (depends on T129) [FR-029; SC-019]
- [X] T134 [US1] Implement the separately derived severe-system physical scale from T127 through the source plan, role-specific lobe placement/extents, and group distribution. Reach the 1,200–1,500 footprint and 720–880 height targets without a uniform descriptor multiplier; retain the 50/25/12.5-block base and ~22.7-to-1.4-block detail wavelengths unless remeasurement proves a change is required. Record controlled SIDE/FAR/BELOW/ABOVE scale evidence before T133 (depends on T127, T129; separate from T131) [FR-028; SC-018]

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

- [X] T133 [US1] **[ACCEPTED 2026-08-27]** Revalidate physical scale, one-medium continuity, T124-T126 macro morphology, CPU/GPU noise parity, final-density rain/whiteout, retained Phase 4R invariants, production shader compilation, and T130-T132 performance evidence together. Record the result in `specs/001-native-storm-rendering/validation/renderer-wide-architecture-audit.md`; only a passing result allows T098 to resume (depends on T127-T132, T134) [FR-028-FR-031; SC-018-SC-020]

**Checkpoint**: A severe storm has an explicit physical-system target **and an accepted
implementation of it (T134)**, the lower/upper material split has an attributed cause, and
performance work has evidence of visual neutrality before T098 resumes. T132 is the only remaining
prerequisite for T133; T133 is the only remaining prerequisite for T098.

---


**Then**: T098 and T099 execute only after T133. They are listed under Phase 4R's Revalidation Gate to keep their audit history in place; their acceptance criteria are the revised positive/negative checklist recorded there.

**Checkpoint**: The coverage envelope comes from descriptors, the visible body comes from noise, and
morphology is measured positively. T118/T124-T126 are retained evidence; T133, not T118, unblocks
the reopened T098, while T099 remains blocked by T098.

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
                    -> Phase 4S Storm Density Architecture Correction (T100-T126 complete)
                        |                          \
                        |                           -> T130 -> Phase 4P Storm Performance Architecture
                        |                              (T119-T123) -> T132
                        -> Phase 4A Renderer-wide Correction Gate (T127-T131)
                                                   \
                                                    -> T133 -> T098 / T099 Morphology Validation Gates
                            -> US3 Scalable Quality Modes
                                -> US4 Diagnostics
                                    -> Phase 7 Compatibility and Release Validation
```

- Phase 1 has no implementation dependency and establishes the baseline/harness.
- Phase 2 depends on T002-T003 for its test entry point and blocks every story.
- US1 depends on all foundational contracts and produces the direct structured-storm path.
- US2 depends on the adopted render snapshot and direct shader path from US1.
- Phase 4R depends on the T041 audit and is complete through T097. Its T081, T085, T086, T089, T090, and T097 acceptance criteria are superseded by Phase 4S; those tasks stay checked as implementation history and are not rewritten.
- Phase 4S depends on Phase 4R and owns the corrected density architecture. T100 must precede every Phase 4S regression so no threshold is set without a derivation, and T107 must record meaningful fail-first results before any Phase 4S production change. T124-T126 are complete history; their morphology thresholds remain retained inputs to T133.
- Phase 4A depends on the completed Phase 4S gate. T127-T129 derive scale and attribute the first material discontinuity. T131 may change only the measured responsible stage. T130 and neutral Phase 4P work can proceed in parallel with that diagnosis, but T132 must prove equivalence before convergence at T133.
- Phase 4P is **not** blocked by T098/T099. T119, T122, and T123 run after T130's baseline; T121 remains conditional on materially equivalent optical evidence. Phase 4P tasks are separate commits from visual-correctness work and may not alter the rendered result.
- T098 and T099 are reopened under the revised positive morphology criteria (FR-023, FR-024). T098 depends on T133; T099 remains blocked by T098.
- US3 depends on T099 for *visual* acceptance. The former rule that no quality or performance work of any kind may start before T099 is **removed**: Phase 4P structural performance work proceeds on its own dependencies. T042's dependency on T099 is retained because quality-mode scaling is judged against the accepted visual result. T050 also retains its dependency on US2 history work.
- US4 depends on US1 for workload sources and on the effective quality and visual-density state from US2/US3 for a complete report.
- Phase 7 depends on all desired stories; T070-T072 are post-correction hard release gates, not optional polish. The observed roughly 80, 100, 140, and 200+ ms raymarch times are not final evidence; T130 establishes their baseline and T132/T133 re-measure the approved architecture work.

### User Story Dependency Graph

| Story | Required predecessors | Independent completion signal |
|---|---|---|
| US1 (P1) | Setup + Foundational; Phase 4S retained, Phase 4A converged at T133 | Corrected storm passes the revised two-part T098 checklist: all nine positive features present, none of the eight rejected forms present |
| US2 (P2) | US1; T099 remains blocked by T098 | Replacement T099 proves rain and whiteout follow final noise-formed density |
| Phase 4R | T041 audit | T097 recorded corrected union evidence; superseded in part by Phase 4S |
| Phase 4S | Phase 4R | T118 records corrected density-architecture evidence; T124-T126 retain macro/role evidence |
| Phase 4A | Completed Phase 4S | T133 records scale, material continuity, morphology, final-density, and performance convergence |
| Phase 4P | T130 reference baseline | T132 proves bounded cost work preserves the frozen rendered result and trace |
| US3 (P3) | T099 for visual acceptance; T050 also needs US2 history work | T052 proves all modes, LOD, and adaptive stability |
| US4 (P4) | US1 plus completed US2/US3 state providers | T060 answers the diagnostic contract from one session |

### Key Task Chains

- **Source geometry**: T011 -> T015 -> T016 -> T018.
- **Direct representation**: T004-T007 -> T012-T013 -> T017-T021 -> T023-T027.
- **Async/cache lifecycle**: T005-T010 -> T014 -> T021-T022 -> T028 -> T064.
- **Rain/whiteout/history**: T031-T033 -> T034-T040 -> T041 -> T078-T079 -> T089, T093-T095 -> T097 -> T115-T116 -> T099.
- **Morphology correction (Phase 4R)**: T074-T079 -> T080 -> T081-T090 -> T091-T096 -> T097.
- **Density architecture (Phase 4S)**: T100 -> T101-T106 -> T107 -> T108-T111 -> T112-T114 -> T115-T117 -> T118 -> T124-T126.
- **Renderer-wide correction (Phase 4A)**: T127 -> T128 -> T129 -> T131; T130 -> T119/T121/T122/T123 -> T132; T131 + T132 -> T133 -> T098 -> T099.
- **Performance architecture (Phase 4P)**: T130 -> T119 -> T121/T122 -> T123 -> T132; T121 is skipped rather than approximated if equivalent evidence is unavailable.
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
T127/T128/T129 measured continuity diagnosis  ||  T130 baseline -> T119 group topology
```

T122 may follow T119; T121 may run only if its equivalent lighting-support proof is available.
T123 and T132 converge their measured work with T131 at T133. T098 cannot run before that
convergence.

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

Phase 4R may use only the parallelism explicitly marked in T079; its tests complete and fail meaningfully before production fixes. Phase 4S follows the same fail-first discipline through T107. Phase 4A requires fail-first material attribution before correction. Phase 4P runs after T130's frozen baseline, in separate commits, and may not alter the rendered result. US3 quality-mode work remains gated on T099 for visual acceptance. Begin US4's standalone diagnostic data model only after the corrected workload/counter meanings and the test contract in T053 are stable.

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
7. **T133/T098** revalidate physical size, single-medium continuity, morphology, final-density consumers, and performance before the live visual gate resumes.
8. **US3** adds predictable mode scaling, LOD, and adaptive performance policy after T099 accepts the visual result.
9. **US4** exposes bounded evidence for ownership, workload, artifacts, and timing.
10. **Phase 7** proves Simple Clouds boundaries, legacy fallback, server safety, full regressions, and the post-correction RTX 4070 release gate.

## Traceability Summary

| Requirement area | Primary tasks |
|---|---|
| Connected 3D stages and overlap | T011-T030, T074-T077, T080-T090, T096, T124-T126, T129-T133, T098 |
| Coverage envelope vs. noise-formed body | T100-T102, T107, T110-T114, T118 |
| Geometric distance field and world-space unions | T104, T107-T109, T111, T118 |
| Physical severe-system scale and one-medium continuity | T127-T131, T133, T098 |
| Positive morphology criteria and derived thresholds | T100, T102-T106, T118, T124-T126, T133, T098-T099 |
| Interior detail erosion | T101, T103, T113, T118 |
| Bounded descriptor evaluation cost | T130, T119-T123, T132-T133 |
| Rain, whiteout, temporal stability | T031-T041, T078-T079, T089, T093-T095, T097-T099 |
| Descriptor validity, fallback, async, signatures | T079, T087-T088, T091-T092, T096-T097 |
| Five modes, adaptive quality, LOD | T099 -> T042-T052 |
| Bounded diagnostics | T053-T060 |
| Server/network/save preservation | T018, T027-T030, T067-T068 |
| Simple Clouds ownership | T061-T066 |
| Legacy fallback | T064-T066 |
| Automated/visual regression | T067-T069, T072 |
| RTX 4070 performance | T130 -> T119-T123 -> T132-T133 -> T070-T072 |
| Scope and no unrelated redesign | Every implementation task is limited to paths named in `plan.md`; T067-T073 enforce the boundary |

## Notes

- `[P]` is used only where file ownership and data dependencies permit parallel work after stated predecessors complete.
- Tests are intentionally placed before their implementation and must fail for the expected missing/incorrect behavior first.
- Every new geometry regression test must demonstrably fail against the audited implementation before its corresponding fix is implemented; T080 records this gate for Phase 4R and T107 records it for Phase 4S.
- Phase 4S thresholds come from `validation/morphology-thresholds.md` and are derived from the shader's configured erosion strength, noise amplitude, octave weights, and octave frequencies. Adjusting a threshold to accommodate an observed result, without a recorded model change, violates FR-026.
- **Renderer-wide gate**: the prior assumption that another local role-geometry iteration should follow a failed T098 is obsolete. T127-T129 must derive physical size and locate the first material discontinuity before T131 changes the responsible stage.
- **Ordering rule removed**: the previous rule that no performance work of any kind could begin before T099 no longer applies. Phase 4P runs after T130's frozen visual/trace baseline. Performance changes stay in separate tasks and commits from visual-correctness changes, and no Phase 4P change may alter the rendered result.
- T042 remains blocked by T099 because quality-mode scaling is judged against the accepted visual result. The roughly 80, 100, 140, and 200+ ms raymarch observations must be baselined at T130 and re-measured at T132/T133.
- Phase 4S and Phase 4P preserve every already validated behavior: server-authoritative weather, forecast behavior, network packets, saved weather state, Simple Clouds ownership, legacy renderer fallback, rain placement, whiteout behavior, history invalidation semantics, and the candidate texture as a scheduling/index hint rather than authoritative geometry.
- Do not change packet registration, packet encoding, saved-data schemas, forecast orchestration, Simple Clouds managed systems, or unrelated cloud families.
- Do not perform Minecraft, RenderSystem, render-target, shader, buffer-upload, or OpenGL access from the async worker.
- Preserve unrelated working-tree changes and commit/review tasks in small logical groups.
