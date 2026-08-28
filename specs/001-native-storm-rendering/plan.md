# Implementation Plan: Native Storm Rendering

**Branch**: `001-native-storm-rendering` | **Date**: 2026-08-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-native-storm-rendering/spec.md`

## Summary

**2026-08-19 revision**: Phase 4S corrects the density architecture again. Storm descriptors now
bound a *coverage envelope* only; the volumetric noise field forms the visible storm body inside it.
Lobes expose real world-space geometric distance fields rather than density-space pseudo-distance.
Morphology acceptance gains positive, measurable criteria. The absolute "no performance work before
T098/T099" ordering rule is removed. See "Phase 4S Correction" and
[contracts/storm-density-composition.md](./contracts/storm-density-composition.md).

**2026-08-19 renderer-wide correction gate**: T098 role-local morphology tuning is paused. The
cloud texture and Phase 4S composition are retained, but a severe-system scale derivation and a
measured vertical material-continuity trace must identify the first lower/upper discontinuity before
any further role geometry change. Foundational performance architecture now runs alongside that
measurement only when it is visually neutral. The complete audit and diagnostic contract are in
[validation/renderer-wide-architecture-audit.md](./validation/renderer-wide-architecture-audit.md).

**2026-08-21 status**: T119 and T121--T123 are accepted from controlled two-pass compact-fixture
evidence, without claiming a historical timing percentage. **T134 is accepted**: fixture
`66a15248-6262-441d-bc42-60e2d4e6b4e5`, structural fingerprint `16536fe1abb39ea0`,
`descriptors=10`, `height=865.31018`, `footprintDiameter=1238.61042`, compact topology, matching
SIDE/FAR/BELOW/ABOVE PASS A/B controls, `structuralChanged=false` throughout. See
[validation/t134-severe-system-scale.md](./validation/t134-severe-system-scale.md).

Because T134 changed every severe system's physical dimensions, **T132 has been rebased**: the
pre-T134 T130 fixture `ce4ffed5-14f1-4b78-bec7-059c1985cedb` and the T121--T123 fixture
`66b2c85a-aa93-4d18-b428-ac546e280c02` can no longer be reproduced, so their frozen visual
references are historical record only and are not a T132 comparison basis. T132 now requires a
fresh post-T134 controlled reference plus a fresh post-T134 material trace on the same fixture;
its full criteria are in `tasks.md` under "T132 revised acceptance criteria". T133 and T098 remain
unstarted.

Correct the implemented native severe-storm path so the descriptor set itself is the evaluated storm field. Each `StormLobeDescriptor` is evaluated independently through the authoritative Java equations and the independently mirrored GLSL equations; lobe distance-like fields are smoothly unioned lobe-to-lobe and then group-to-group. The candidate grid is restored only as a conservative acceleration structure. Keep the existing synchronized `CloudFieldSnapshot` and `CloudMorphologyMembership` inputs, corrected source morphology, four-texel descriptors, stable identity/order, geometry build/snapshot lifecycle, render-thread boundaries, broad-map distant fallback, server authority, packets, saved data, forecast behavior, Simple Clouds ownership, native rollback path, precipitation ownership, and camera-density interfaces.

## Technical Context

**Language/Version**: Java 17, GLSL 1.50/Forge core shader resources  
**Primary Dependencies**: Minecraft Forge 1.20.1, Mojang Blaze3D/RenderSystem, LWJGL OpenGL; optional Simple Clouds API/runtime, Serene Seasons, GeckoLib, and existing compatibility modules  
**Storage**: Existing Minecraft saved data and synchronized `CloudFieldSnapshot` state; no schema changes. New storm descriptors and indices are ephemeral client memory/GPU textures only  
**Testing**: Gradle `check`, architecture boundary check, existing standalone Java sandboxes, new deterministic storm rendering sandbox, Forge `runClient`, optional Simple Clouds `runClient -PenableSimpleCloudsRuntime=true`, manual diagnostic captures  
**Target Platform**: Minecraft Forge 1.20.1 client and dedicated server; native volumetric rendering on the repository's GL 3.2 baseline  
**Project Type**: Existing brownfield Java Minecraft mod with server weather domain, Forge platform adapters, client renderer, resources, and optional compatibility modules  
**Performance Goals**: Ultra sustains 60 FPS at 1920x1080 on the specified plugged-in RTX 4070 laptop, no external shader pack, approximately 2000-block render distance; p95 total frame time no more than 16.7 ms over ten minutes after convergence. Current live raymarch observations of roughly 80, 100, 140, and 200+ ms depending on viewpoint are architectural alarms, not final gate evidence; T130 MUST baseline and T132/T133 MUST re-measure them before T098 resumes
**Constraints**: Server-authoritative weather; render-thread-only Minecraft/GL access; no per-frame geometry rebuild; bounded 64 storm descriptors and eight candidates per tile; every counted descriptor slot is real or explicitly skipped by sentinel; the candidate grid may reject work but never define density; no new runtime dependency; no texture-unit use beyond current units 0-14; smooth role transitions, rain, whiteout, LOD, and history  
**Scale/Scope**: Native `STORM_ANVIL` presentation and related rain/whiteout/quality/diagnostics only; a derived severe-system footprint and aspect ratio rather than a compact-cloud assumption; ten-member mature severe source groups from the accepted T127/T134 scale contract, up to 64 direct descriptors, 256-square spatial index, five quality modes

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Gate | Pre-research | Post-design | Evidence |
|---|---|---|---|
| Forge 1.20.1 and Java baseline | PASS | PASS | Java 17 and existing Forge/GL shader pipeline only; no new dependency or platform API. |
| Preserve architecture and modular ownership | PASS | PASS | Phase 4R and Phase 4S change only storm density composition and directly related correctness/lifecycle defects; Phase 4P changes only evaluation cost without altering the rendered result; it retains descriptor packing/identity, build/snapshot/async boundaries, source morphology, server/network/save/forecast ownership, Simple Clouds, custom precipitation, and camera-density interfaces. |
| Server authority and explicit synchronization | PASS | PASS | `CloudRegionState`/`CloudClusterState` remain truth; the client consumes existing immutable field snapshots. No packet, save, or forecast schema change. |
| Tick, allocation, async, and thread discipline | PASS | PASS | Dirty cluster-only signatures, valid re-request after rejection, reusable primitive buffers, coalesced CPU builds, bounded per-group intersections, and render-thread-only adoption/upload are defined. No new tick loop. |
| Compatibility and dependency restraint | PASS | PASS | Existing Simple Clouds/Serene Seasons/GeckoLib boundaries stay intact; native resources are used only under native ownership; no dependency added. |
| Regression protection | PASS | PASS | Phase 4S adds fail-first interior-noise, variance, spectral, distance-field, structural, and rejected-form tests with model-derived thresholds; Phase 4R adds fail-first silhouette/locality/composition/rain/lifecycle tests, independent Java/GLSL parity, replacement US1/US2 captures, and retains native, dedicated-server, and Simple Clouds launch matrices. |
| Configuration and diagnostics | PASS | PASS | Existing quality/render-distance controls remain; two bounded client visual options and on-demand storm diagnostics/debug views are added. |
| Focused brownfield delivery | PASS | PASS | Changes are limited to native severe storm geometry/presentation, precipitation sampling, whiteout parity, quality scaling, diagnostics, and direct supporting tests. |

No constitution exception or complexity waiver is required.

## Project Structure

### Documentation (this feature)

```text
specs/001-native-storm-rendering/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── render-ownership-and-sync.md
│   ├── storm-density-composition.md   # Phase 4S envelope -> noise -> erosion contract
│   └── storm-render-diagnostics.md
└── tasks.md                         # Created later by $speckit-tasks
```

### Source Code (repository root)

```text
src/main/java/net/Gabou/projectatmosphere/
├── clouds/
│   ├── simulation/
│   │   └── CloudMorphologyGenerators.java          # Correct storm spawn/retarget role geometry
│   ├── field/
│   │   ├── CloudFieldSnapshot.java                 # Existing render-authoritative input; no schema change
│   │   ├── CloudMorphologyMembership.java          # Existing stable group/role identity
│   │   └── network/CloudFieldSyncManager.java      # Inspected; expected unchanged
│   ├── client/
│   │   ├── ClientCloudFieldCache.java              # Existing interpolation source
│   │   └── render/
│   │       ├── ClientCloudRenderOwnership.java     # Existing owner gate; unchanged
│   │       ├── CustomPrecipitationRenderer.java    # Existing near rain/snow; unchanged
│   │       └── volumetric/
│   │           ├── VolumetricCloudRenderHook.java
│   │           ├── VolumetricCloudRenderer.java
│   │           ├── VolumetricCloudRenderTargets.java
│   │           ├── CloudWeatherMapRenderer.java
│   │           ├── VolumetricRenderCell.java
│   │           ├── VolumetricQualityProfile.java
│   │           ├── CloudFrameTimeGovernor.java
│   │           ├── ClientCloudVisualDensity.java
│   │           ├── CameraCloudDensityTracker.java
│   │           ├── VolumetricCloudFrameDiagnostics.java
│   │           ├── VolumetricCloudDebugConfig.java
│   │           ├── VolumetricCloudRaymarchDebugView.java
│   │           ├── StormLobeDescriptor.java        # New immutable render descriptor
│   │           ├── StormLobeEvaluator.java         # New shared CPU analytic contract
│   │           ├── StormLobeSpatialIndex.java      # New selection/cache/upload owner
│   │           ├── StormGeometryBuildCoordinator.java # New coalesced CPU build lifecycle
│   │           └── StormLobeDiagnostics.java       # New bounded storm-specific capture
│   ├── backend/CloudBackendResolver.java            # Inspected; unchanged
│   └── service/AtmosphereCloudServices.java         # Inspected; unchanged
├── command/TelemetryDebugClientCommand.java         # Extend existing volumetric commands
├── config/AtmoCommonConfig.java                     # Add two client visual controls
└── util/AsyncAtmosphereService.java                 # Add bounded non-blocking client submission

src/main/resources/assets/projectatmosphere/shaders/core/
├── cloud_atmosphere_volume.fsh                      # Direct storm/rain/diagnostic evaluation
├── cloud_atmosphere_volume.json                     # Replace 3 storm samplers with 2
├── cloud_weather_storm_structure.*                  # Remove after direct-path acceptance
└── cloud_weather_storm_heights.*                    # Remove after direct-path acceptance

src/test/java/net/Gabou/projectatmosphere/clouds/
├── simulation/CloudMorphologyTopologySandbox.java  # Extend storm topology checks
└── client/render/volumetric/
    ├── VolumetricStabilityDiagnosticsSandbox.java  # Extend history/precipitation checks
    └── StormVolumetricGeometrySandbox.java          # New deterministic feature checks

build.gradle                                         # Register sandbox and keep it under check
```

**Structure Decision**: Preserve the current single Forge project and package ownership. Authoritative role geometry stays in `clouds.simulation`; all descriptor/index/evaluator code is client-only under the existing volumetric renderer; optional integration ownership remains in the existing backend/service layers. The old storm structure resources are removed only after the new direct path passes comparison and fallback validation; the separate legacy CloudField renderer is retained.

## Existing Asset Disposition

| Existing asset | Disposition | Reason |
|---|---|---|
| `CloudRegionState`, `CloudClusterState`, field derivation | Reuse; modify only storm role envelope generation/retarget values | Existing authoritative ownership and stable identifiers are correct. |
| `CloudFieldSnapshot`, field packets, `CloudFieldSyncManager`, `ClientCloudFieldCache` | Reuse unchanged | Already provides every render input and smooth client interpolation; no schema is needed. |
| `CloudMorphologyMembership` | Reuse unchanged | Existing group/member/stage identity supplies stable render topology. |
| `VolumetricCloudRenderHook`, `VolumetricCloudRenderer`, render-state guard, history/composite | Extend | Correct integration point, lifecycle, depth, temporal, and ownership behavior already exist. |
| Broad weather/morphology maps | Reuse | Needed for material, precipitation, shadows, empty-space pretest, and distant LOD. |
| `PuffLobeSpatialIndex` and direct PUFF shader path | Reuse as design pattern; do not merge storm and PUFF ownership | Proves bounded descriptor/candidate indexing on the GL baseline while allowing role-specific storm behavior. |
| Three severe-storm support/height/tower targets and their two shader passes | Remove after A/B acceptance | This is the lossy reconstruction that creates role seams and generic vertical clipping. |
| `CustomPrecipitationRenderer` | Reuse unchanged | Owns nearby rain/snow quads, not volumetric shafts. |
| `ClientCloudVisualDensity`, `CameraCloudDensityTracker` | Extend behind existing interfaces | Must evaluate the same adopted structured storm for whiteout parity. |
| `VolumetricQualityProfile`, `CloudFrameTimeGovernor` | Extend | Existing quality/governor ownership is correct; table and state policy are incomplete. |
| `VolumetricCloudFrameDiagnostics`, stability/cumulus/PUFF captures, `/pa cloud volumetric` | Extend | Avoid a duplicate diagnostic or command subsystem. |
| `CloudBackendResolver`, `ClientCloudRenderOwnership`, `AtmosphereCloudServices`, Simple Clouds integration | Reuse unchanged | Existing optional-loading and ownership boundary is correct. |
| Forecast orchestration and networking behavior | Leave unchanged | The feature has no forecast or synchronization requirement beyond consuming existing fields. |
| Legacy CloudField renderer rollback | Retain | Required by the specification and useful for bounded rollback during validation. |

## T041 Audit Correction: Descriptor Field Was Not Evaluated

The source-level audit after T041 found an architectural defect in the implemented direct-storm path. `directStormShape()` in `cloud_atmosphere_volume.fsh` and the CPU mirror in `StormLobeEvaluator.finishGroupEnvelope()` do not union descriptor shapes. They accumulate descriptor centers, second moments, radii, density, and group-wide vertical limits, then render one axis-aligned statistical ellipse whose radius changes through `morphologyScale`. Descriptor role geometry, orientation, shear, vertical span, and individual support are therefore mostly aggregated away before the visible storm body is evaluated.

This explains the incorrect result despite passing internal checks: there is no real tower geometry; the anvil is mainly a radius expansion of the same group envelope; the underside is planar because it comes from one group-wide minimum Y; the silhouette becomes mushroom-shaped; and visible geometry can disagree with tests that exercise the same collapsed equations. The current group composition is also alpha-style accumulation, not a smooth union on a distance-like field, so lobe and group intersections can retain creases.

Phase 4R replaces only storm density composition. The following implemented systems remain in place and MUST NOT be redesigned:

- `StormLobeDescriptor`, its four-texel GPU packing, stable identity, and stable ordering;
- `StormGeometryBuildInput`, `StormGeometryBuild`, `StormRenderSnapshot`, the async geometry-build lifecycle, and render-thread adoption/upload boundaries;
- corrected server-side morphology generation, server authority, networking, saved weather data, and forecast behavior;
- Simple Clouds ownership, `CustomPrecipitationRenderer`, and `CameraCloudDensityTracker`.

The correction makes every real descriptor an independently evaluated local distance-like field. Descriptors are smoothly unioned within each storm group, complete group fields are then smoothly unioned with other groups, and the blend radius is derived from the smaller participating lobe radius so blending remains proportional without swallowing narrow towers. The candidate grid and bounded group intersections may conservatively reject irrelevant work before evaluation, but they are acceleration only and can never add, remove, or reshape authoritative density.

Every new geometry regression test must first be demonstrated to fail against the audited implementation for the intended reason. T025, T026, T030, T032, T034, and T037 were reopened by this audit.

**Ordering superseded 2026-08-19**: the original rule - that no US3 or performance work of any kind could begin until the final Phase 4R revalidation task completed - is removed. See "Ordering correction" under "Phase 4S Correction". Phase 4S correctness work and Phase 4P structural performance work run on their own dependencies; T098 and T099 remain visual release gates and now require the revised positive morphology criteria.

## Phase 4S Correction: Descriptor Envelope and Noise-Formed Body

Phase 4R made the descriptor set the evaluated storm field. That was necessary but not sufficient:
it made descriptor geometry the *final visible density*. Combined with a storm-specific erosion
exemption, the result was a smooth, balloon-like body with detail confined to a thin rim - a form
that satisfies every artifact-absence criterion the previous specification contained.

Three defects, in order of impact:

1. **Descriptor geometry is treated as final density.** `directStormShape()` and
   `StormLobeEvaluator.unionDensityAt()` return a density value that the raymarch uses directly as
   the storm body. The visible shape is therefore analytic, and no amount of surface detail changes
   its silhouette.
2. **Interior detail is suppressed.** In `cloud_atmosphere_volume.fsh`,
   `edgeExposure = 1.0 - smoothstep(0.26, 0.72, cloud)` reaches zero across a covered storm
   interior, and `erosionFloor = 0.42` clamps what remains. Multi-scale detail cannot reach the
   storm body.
3. **The union domain is not geometric.** Both implementations derive their union input as
   `1 - lobeDensity` and skip lobes whose local density is zero. That quantity is undefined outside
   a lobe's support, which is exactly where a smooth union needs it, and blend radii expressed in
   that domain have no consistent spatial meaning.

Phase 4S corrects all three:

```text
per-lobe world-space geometric distance field
  -> smooth union lobe-to-lobe (world-space blend radius)
    -> smooth union group-to-group (world-space blend radius)
      -> bounded coverage envelope
        -> base volumetric noise remapping
          -> multi-scale detail erosion across the interior
            -> final storm density
```

The storm-specific `edgeExposure` / `erosionFloor` assumption is removed for descriptor-owned
storms. Non-storm profiles retain their existing behavior.

Morphology acceptance is now positive as well as negative. The rendered storm must visibly contain
a broad continuous lower base, a dense convective core, tower development emerging from that base,
progressive vertical narrowing where appropriate, a broad upper anvil, multi-scale billowing across
the body, surface variation at several spatial frequencies, irregular but coherent silhouette
curvature, and continuous transitions between regions (FR-023). It must not contain balloon
surfaces, uniform-density regions, identifiable primitives, isolated ears or bulbs, descriptor
seams, walls, slabs, or uniformly smooth silhouettes (FR-024). Deterministic proxies and their
derived thresholds are recorded in
[validation/morphology-thresholds.md](./validation/morphology-thresholds.md).

### What Phase 4S does not change

Every already validated behavior is preserved: server-authoritative weather, forecast behavior,
network packets, saved weather state, Simple Clouds ownership, legacy renderer fallback, rain
placement ownership, whiteout behavior, history invalidation semantics, and the candidate texture
remaining a scheduling/index hint rather than authoritative geometry. Descriptor packing, identity,
ordering, build/snapshot lifecycle, async boundaries, and render-thread ownership are unchanged.

### Ordering correction

The rule prohibiting all performance work until T098/T099 complete is **removed**. Replacement
rules:

- Correctness work required by the corrected density model may proceed before final morphology
  validation.
- Performance-specific changes stay separated from visual-correctness changes: distinct tasks,
  distinct commits, distinct evidence.
- Architectural changes needed to make the corrected density model practical are not blocked by the
  old Phase 4R gate. Precomputed descriptor group topology is the expected case, because the
  corrected union no longer discards zero-density lobes.
- T098 and T099 remain release gates and are not passable until the revised positive morphology
  criteria are satisfied.

## Storm Performance Architecture (Phase 4P)

Planned, not implemented during the correctness refactor unless an item is inseparable from making
the corrected density model practical. Each item is its own task with its own before/after
measurement.

| Item | Current state | Requirement |
|---|---|---|
| Group topology | `stormGroupFirstIndex()` / `stormGroupEndIndex()` rescan up to `MAX_STORM_LOBES` descriptors per group **per density sample**, each scan issuing descriptor texel fetches | Precompute group topology on the CPU during the existing build |
| Group metadata upload | Derived in-shader every sample | Supply per-group first/end indices, or equivalent compact metadata, as uploaded data |
| Group visitation | `bool groupVisited[MAX_STORM_GROUPS]` allocated and cleared per call in `directStormShape()` and `directStormSegmentMayIntersect()` | Compact integer bit mask or another GPU-friendly representation |
| Lighting cone taps | `lightMarchOpticalDepth` and its endpoint/capped/refined/no-detail variants evaluate the full cloud density function per tap | Use a cheaper storm lighting proxy where it preserves acceptable visual quality; document the proxy |
| Descriptor texture fetches | Repeated fetches for the same descriptor within `cloudDensity` and `lightMarchOpticalDepth` | Audit, hoist, or reuse; record the fetch count in diagnostics |
| Evaluation cost | Unbounded in practice once zero-density lobes are no longer skipped | Maintain a documented bounded per-sample and per-frame descriptor evaluation cost (FR-027, SC-017) |

Phase 4P work must not change visible morphology. Any Phase 4P change that alters the rendered
result is a correctness change and belongs in Phase 4S.

+## Renderer-wide Severe-Storm Correction Gate

T098 is paused rather than reinterpreted as another role-envelope iteration. The next correction is
constrained by a measured cause, not visual inference. The Phase 4S sequence remains unchanged:

```text
descriptor envelope -> base-noise remap -> multi-scale erosion -> final density
```

### Scale derivation

T127 treats a severe storm as a weather system. It derives source-plan dimensions, role spans,
member density, total height, aspect ratio, and horizon dominance at three several-hundred-block
viewing distances. It then evaluates the corrected base bands (50 / 25 / 12.5 blocks) and the
subordinate erosion bands (approximately 22.7 down to 1.4 blocks) against that target. It may not
accept a uniform scaling of all descriptors as an analysis.

### One-medium trace

T128/T129 establish a deterministic centre-line trace at <=16-block Y intervals for the
live-calibrated ten-descriptor fixture and `3c039aa7`. The trace records active roles, coverage and
strength, base noise/carrier, detail erosion, final density, extinction, optical depth, direct and
ambient light, final contribution, and direct/fallback plus height-normalization flags. The first
stage that changes discontinuously classifies the correction as geometry, density/noise, medium,
lighting, or sampling/history. No local overlap, taper, union, or anvil change may be accepted
until this attribution exists.

### Visually-neutral performance architecture

T130 freezes a performance/reference-image/material-trace baseline. T119/T121/T122/T123 may then
precompute topology, bound fetches, cull conservative empty space, reject envelopes cheaply, reuse
same-sample facts, reduce scratch pressure, and terminate mathematically opaque rays earlier. A
lighting proxy is allowed only when it is equivalent within the frozen trace and image tolerance.

**Accepted 2026-08-20**: T121, T122, and T123 passed the two-pass compact
`stormPerformanceSuite` on one frozen fixture. Both passes matched group/fingerprint, exact
SIDE/FAR/BELOW/ABOVE poses, governor scale 0.50000, resolution scale 0.75000, target, configured
ray/light steps, history controls, and compact topology. `conservativeDescriptorRejects`,
`avoidedDescriptorTextureFetches`, and `earlyTerminations` were positive in every view; the complete
primary, descriptor, texture-fetch, light-density, empty-space, and termination counters were
captured. This accepts execution and equivalence only; no historical percentage is inferred because
the pre-T121 counters did not exist.
Step-count reductions, reduced noise/lighting, resolution changes, or any accepted image change
remain quality work and stay behind T098/T099.

### T123 documented descriptor evaluation bound

FR-027 and SC-017 require a stated per-sample and per-frame bound. It is structural, not tuned:

**Per storm density sample.** `directStormShape()` visits at most `MAX_STORM_GROUPS = 8` group
slots, each exactly once, guarded by the T120 group bit mask. `directStormGroupField()` then walks
one contiguous descriptor range whose endpoints come from T119 compact metadata, so the per-sample
descriptor evaluation count is bounded by `MAX_STORM_LOBES = 64` across all groups combined, never
by a per-group rescan. Each evaluated descriptor issues exactly **four** descriptor texel fetches;
T122 reuses those same four registers for both the exact lobe SDF and the edge-softness form, so no
descriptor is fetched twice within one evaluation. T119 adds **three** bounded metadata reads per
group evaluation and **zero** group-boundary scan iterations. The absolute per-sample ceiling is
therefore `64` descriptor evaluations, `256` descriptor texel fetches, and `8 * 3 = 24` metadata
reads; T121's conservative vertical-cap rejection only lowers the realised count and can never
raise it.

**Per frame.** Storm descriptor work is bounded by
`primaryRaySteps + lightMarchDensityEvaluations` multiplied by that per-sample ceiling. Both
factors are themselves bounded: the primary integration ends at `MAX_STEPS` or below transmittance
`0.015` (maximum remaining premultiplied alpha `0.015`), and the in-slab light march ends at
optical depth `28` (Beer-Lambert transmittance `exp(-28)` about `6.9e-13`). Those two exits are
the only accepted bounded-work exits and neither introduces a quality threshold or a
sample-position change.

**Reporting.** `paPrimaryRaySteps`, `paDescriptorEvaluations`, `paDescriptorTextureFetches`,
`paAvoidedDescriptorTextureFetches`, `paLightMarchDensityEvaluations`, `paEmptySpaceRejects`,
`paEarlyTerminations`, and `paConservativeDescriptorRejects` carry the realised counts. They are
emitted only under `paWorkloadCaptureActive()` (`DebugView == 22 || DebugView == 23`), read back
through `VolumetricCloudFrameDiagnostics.requestStormWorkloadCapture(...)` and the
`stormPerformanceSuite` report. FINAL frames never read them back and never composite them.

T131 applies only the measured single-medium correction. T132 proves approved performance changes
did not move the visual/trace output, measured against a fresh post-T134 reference rather than the
pre-T134 T130 captures. T133 combines physical-scale, material-continuity, morphology,
final-density rain/whiteout, and performance evidence before T098 resumes.


## Rendering Architecture

```text
Server CloudRegion/CloudCluster state
        |
        | existing CloudField derivation + existing packets
        v
ClientCloudFieldCache (interpolated immutable snapshots)
        |
        v
VolumetricCloudRenderHook / VolumetricRenderCell
        |
        +--> broad weather + morphology maps --------------------+
        |                                                        |
        +--> StormGeometryBuildCoordinator                       |
              | copied primitive input, dirty signature          |
              v                                                  |
           client CPU worker                                     |
              | immutable generation-tagged build                |
              v                                                  |
           render-thread adoption/upload                         |
              +--> descriptor texture + candidate texture -------+
                                                               raymarch
                                                                  |
                                  color/depth/history/composite <--+
                                                                  |
                                  adopted snapshot --> CPU density/whiteout
```

The broad maps remain responsible for weather material, low-frequency occupancy, precipitation potential, shadows, and distant LOD. For descriptor-owned nearby severe storms, the descriptor union is authoritative for body density, local underside, precipitation support, and attachment height. Broad maps and the candidate grid may provide conservative coverage early-outs, but neither may define the direct storm shape. Cumulus and PUFF behavior are not redesigned.

## Storm Geometry Representation

### Source geometry

`CloudMorphologyGenerators` will produce and retarget deterministic member envelopes by `CloudMorphologyStage`:

- BASE members overlap around a common condensation floor with small seeded height variation.
- CORE members begin inside the base and bridge vertically toward the tower.
- TOWER members narrow upward and receive wind-derived horizontal lean.
- ANVIL members begin within the tower crown and extend horizontally along the wind vector.

Retargeting updates offsets, spans, radii, and shear continuously while preserving `groupId`, index/count, layout version, and seed. Connectivity invariants are checked in the source topology sandbox.

### Client descriptor

Each selected member becomes four `RGBA32F` texels:

1. center X, center Z, base Y, top Y;
2. major radius, minor radius, sine orientation, cosine orientation;
3. shear X, shear Z, density, edge softness;
4. normalized seed, lifecycle stage, vertical development, packed group-slot/role.

The packed value is an exact small integer (`groupSlot * 8 + roleId`). Per-field precipitation and material remain available from the weather/morphology maps. Descriptor order is stable by group UUID then member index.

Every slot below `StormLobeCount` contains a valid descriptor that came from a live member. Missing live members are compacted before upload, or the slot is explicitly marked with a sentinel that both Java and GLSL skip. A zero-filled slot must never decode as a group 0 BASE lobe at world origin. Morphology values still needed during direct evaluation are carried on descriptors rather than reintroduced through per-member raster footprints.

### Candidate index

The 256 by 256 candidate texture covers the same snapped world extent as the weather map. Each pixel stores up to eight one-based descriptor indices, two base-65 digits per `RGBA32F` channel, used as stable group witnesses; zero is empty. Because a build has at most eight group slots, each intersecting group needs at most one tile entry. The witness resolves a bounded descriptor range for that group, whose conservative projected bounds include every member's horizontal radii, height-dependent shear, smooth-union blend support, wind alignment, and LOD cross-fade margin. A bounded group intersection rejects a group before its descriptors are evaluated. The grid may admit false positives, but it must not omit an intersecting admitted group and its values never become an authoritative density field.

### Shader density

The raymarch performs coverage early-outs, fetches only conservative local candidates, groups them by stable group slot, and applies a bounded per-group segment/intersection test before expensive evaluation. Each descriptor keeps its own oriented radii, local vertical span, height-dependent shear, role profile, envelope strength, edge softness, and morphology parameters.

Each descriptor produces a real world-space geometric distance field, valid outside its surface as
well as inside it. Lobes are smoothly unioned within a group and groups are smoothly unioned
group-to-group, with blend radii expressed in world-space blocks and scaled from the smaller
participating lobe radius. A lobe is never dropped from the union because its local density
evaluates to zero.

The union result is a **bounded coverage envelope**, not the visible body. The base volumetric noise
field is then remapped against that envelope to form the storm body, and multi-scale detail erosion
sculpts that body across its whole extent, interior included. Only that final value is the storm
density used for lighting, precipitation support, camera density, and whiteout.

Direct severe geometry is bounded by descriptor support plus the blend margin and the global render-slab safety limit, not by a statistical group ellipse, a binary group-weight gate, or the old generic field base/top clamp. Prohibited: `1 - lobeDensity` as a distance field, zero-density lobe skipping, density-space blend radii, and any storm-specific edge-exposure or erosion-floor term that suppresses interior detail.

## Lifecycle and Ownership

1. World/dimension/backend/resource changes clear pending CPU generations, GPU signatures, temporal history, and published density snapshots.
2. Each native frame reads already-interpolated snapshots and creates a stable severe-group selection signature.
3. If only continuous descriptor values changed, reusable descriptor upload storage is refreshed and compacted; if conservative quantized cluster-sourced bounds or selection changed, a coalesced CPU grid build is requested. Unrelated LOD cloudlets do not enter storm grid/topology signatures.
4. The worker receives copied primitive arrays only. It computes candidate tiles and counters, returns a generation-tagged immutable build, and never touches game or render state.
5. The render thread accepts a result only when world, dimension, ownership, origin/extent, target identity, and requested generation still match. Stale results are counted and discarded, and rejection of a completed build leaves or creates a valid request for the current signature so the coordinator cannot stall permanently stale.
6. Texture allocation and upload occur on the render thread. The last valid generation remains active until a replacement is adopted.
7. World, dimension, owner, and resource lifecycle generations advance independently. A lifecycle reset invalidates history before any frame can composite against the old key, then cancels publication and clears references; existing async service shutdown continues to own thread termination.

## Client and Server Responsibilities

| Responsibility | Server | Client |
|---|---|---|
| Weather/storm lifecycle and gameplay | Authoritative | Read-only presentation |
| Stable group membership and source envelopes | Authoritative derived state | Consume synchronized snapshot |
| Save/load and forecast | Existing behavior | No new ownership |
| Interpolation/extrapolation | N/A | Existing `ClientCloudFieldCache` |
| Quality, render distance, direct-lobe selection | N/A | Client visual policy |
| CPU index build | N/A | Copied immutable inputs on client worker |
| GPU resources, shader, history, composite | N/A | Render thread only |
| Camera density/whiteout | N/A | Evaluate adopted render snapshot |

## Synchronization Requirements

- No packet or saved-data field is added, removed, or reordered.
- Full/delta synchronization, resync, removals, join, disconnect, and dimension behavior remain owned by the existing field sync/cache pipeline.
- A client render generation is local metadata and never becomes weather truth.
- Missing or stale snapshots fade through existing presentation tracks. Missing membership uses the broad map fallback and is reported; it does not invent unstable role IDs.
- The renderer never sends geometry, quality, or camera-density state to the server.

## Caching and Invalidation Strategy

| Cache | Rebuild/invalidate when | Stable-frame behavior |
|---|---|---|
| Group selection | dimension/owner changes, groups enter/leave detail range, capacity/order changes | Reuse stable complete-group order |
| Descriptor upload | descriptor signature or target changes | Reuse texture; no allocation |
| Candidate grid | quantized conservative bounds of cluster-sourced severe cells, selection, origin/extent, detail distance, or target changes | Cache hit; unrelated LOD cloudlet changes do not rebuild it |
| Weather/morphology maps | Existing input signature policy | Existing cache behavior |
| Temporal history | independently keyed world/dimension/owner/resource generation, target resolution, camera cut, or cluster-sourced storm topology generation changes | Retain through interpolated motion and material advection; invalidate before compositing the first changed frame |
| CPU density snapshot | adopted render generation changes | Reuse immutable adopted snapshot |

Signatures use quantized geometry values sufficient to preserve conservative coverage while preventing tiny interpolation changes from rebuilding the grid. Descriptor storage, tile counts, scores, upload buffers, sorting keys, and diagnostic counters are preallocated or reused at bounded capacity.

## Quality Mode Scaling

`VolumetricQualityProfile` becomes the single preset table for nominal steps, resolution, lighting/detail work, weather-map size, storm-detail allowance, target GPU time, and floors. `CloudFrameTimeGovernor` returns an immutable adaptive state instead of a free-running scalar.

| Mode | Nominal | Floor | Other scaling |
|---|---|---|---|
| Low | 24 steps, 25% | 12, 25% | No temporal history as today; lowest light/detail settings |
| Low 24 | 32, 37.5% | 16, 25% | Temporal enabled; conservative storm refinement |
| Medium | 40, 50% | 20, 37.5% | Balanced direct-storm and lighting work |
| High | 64, 50% | 32, 37.5% | Increased lighting/refinement |
| Ultra | 96, 75% | 48, 50% | Full supported detail; 60 FPS reference target |

The direct descriptor capacity remains 64 in every mode so quality changes cannot disconnect a selected storm. Modes scale per-ray refinement, shadow cadence, lighting work, map size, and the number of fine analytic samples—not group integrity. Adaptive mode changes one discrete step/resolution band at a time using sustained-load/recovery hysteresis and a 30-second transition cooldown.

## Render Distance Behavior

- `cloudRenderDistance` remains the overall cloud cutoff.
- New `nativeStormDetailDistance` defaults to 1536 blocks, is bounded to 256-4096, and is clamped to the overall distance.
- Direct geometry is fully weighted through `detailDistance - 128`, cross-fades against the broad map across the final 128 blocks, and is map-only beyond the detail distance.
- Capacity-omitted whole groups use the same map LOD and appear in diagnostics.
- The broad map fades at the existing total render-distance boundary, so there is no second hard cutoff.
- Quality degradation never admits partial groups and never changes source weather state.

## Rain and Whiteout Integration

- Leave `CustomPrecipitationRenderer` and its vanilla fallback ownership intact.
- Make volumetric rain eligibility local: broad precipitation flags only enable the feature, while the exact direct-storm union determines body support for descriptor-owned storms. `VolumetricPrecipitationModel.shaftDensity()` receives `maxPrecipitation` in its maximum-precipitation parameter, not `localPrecipitation` twice.
- Anchor shaft pattern and integration in world space. Use deterministic sub-samples over coarse segments and keep rain refinement separate from cloud-body fine stepping.
- Derive the storm underside locally from contributing BASE lobes in the exact rendered union, attach rain to that local underside, and prevent shafts above the source or outside the same union support.
- Publish the adopted descriptor snapshot with volumetric density data and evaluate the same role profiles through `StormLobeEvaluator` for `CameraCloudDensityTracker`.
- Add body-only, precipitation-only, and combined diagnostic views to prove agreement across cloud boundaries.

## Simple Clouds Compatibility Boundaries

- `CloudBackendResolver` and `ClientCloudRenderOwnership` remain the only owner-selection policy.
- When Simple Clouds owns a dimension, Project Atmosphere does not allocate, build, upload, or render native storm resources for that frame.
- `AtmosphereCloudServices` remains an optional reflection/adaptation layer; Project Atmosphere does not assume ownership of Simple Clouds managed cloud systems.
- Existing Simple Clouds mixins, shaders, hurricane/tornado integration, dependencies, and runtime opt-in stay unchanged.
- Validate both default `runClient` and `runClient -PenableSimpleCloudsRuntime=true`, including owner transitions and optional integration failure.

## Performance Strategy

- Performance *tuning* follows correctness. Performance *architecture* (Phase 4P) does not: it may proceed once its prerequisite Phase 4S correctness tasks are in place, in separate tasks and separate commits from visual-correctness changes. The absolute pre-T099 prohibition is removed.
- The current approximately 80, 100, 140, and 200+ ms raymarch observations are diagnostic evidence only and are not final baseline or gate results.
- T130 freezes the reference cost/work/image/trace baseline before any foundational optimization; T132/T133 re-measure it after the approved work.
- Precomputed descriptor group topology may enter the correctness phase when the corrected union - which no longer discards zero-density lobes - is otherwise impractical. That case is treated as inseparable and carries its own before/after measurement.
- Bound direct descriptors (64), tile candidates (8), grid size (256), per-group intersection work, and per-sample loops.
- Select complete groups once per dirty generation with stable primitive sort keys.
- Preallocate/reuse descriptor, tile, score, packing, upload, and diagnostic storage.
- Coalesce worker builds; never queue every snapshot or block the render thread waiting for geometry.
- Keep all GL allocation/upload/uniform binding on the render thread and all worker inputs free of Minecraft objects.
- Put direct storm evaluation behind conservative coverage early-outs and bounded per-group segment intersections; the candidate grid rejects irrelevant descriptors/groups but never defines cloud density.
- Retain broad weather pretests and make precipitation rejection local.
- Query GPU timings through the existing frame diagnostics/governor path without synchronous readback stalls.
- Record CPU build time, wait time, upload time, cache hit rate, rebuild frequency, descriptor/candidate complexity, raymarch GPU time, composite time, and adaptive transitions.
- Treat the post-correction Ultra reference measurement as a release gate; tune only after morphology acceptance and only within mode floors and visual criteria rather than lowering source topology or disconnecting groups.
- The per-sample descriptor scan in `stormGroupFirstIndex()` / `stormGroupEndIndex()` is a confirmed structural contributor to GPU cost, so measurements taken before Phase 4P are not final.
- No Phase 4P change may alter the rendered result. A performance change that moves the image is a correctness change.

## Diagnostics

Extend the existing command and telemetry surface:

- `/pa cloud volumetric diagnostics storm`: one bounded storm snapshot including active/selected/omitted groups, role counts, descriptor count, active and overflow tiles, maximum candidates, cache requests/hits/uploads, worker submissions/completions/stale discards, current topology generation, rebuild rate, CPU build/upload timings, analytic/map LOD counts, and current quality state.
- `/pa cloud volumetric diagnostics`: include compact storm workload and current effective quality.
- `/pa cloud volumetric debug view <storm_body|storm_envelope|storm_candidates|precipitation|storm_combined>`: isolate geometry and rain failure modes.
- `/pa cloud volumetric debug governor reset`: retain existing reset behavior but report effective mode/band and last transition reason.

Per-frame logging remains development-only and opt-in. Normal operation maintains primitive counters/ring-buffer values without string creation.

## Configuration Changes

Add to the existing Forge performance/cloud rendering configuration:

- `adaptiveCloudQuality` (`boolean`, default `true`): permits the bounded governor to move below and recover toward the selected quality mode.
- `nativeStormDetailDistance` (`int`, default `1536`, range `256..4096`): maximum direct analytic storm distance, clamped to `cloudRenderDistance`.

Keep `cloudRaymarchQuality`, `cloudRenderDistance`, renderer kill switches, shadows, movement, custom precipitation, and shader-safe mode behavior. Read client visual values once at frame setup and pass plain values into renderer components; do not access `ForgeConfigSpec` from migrated domain packages.

## Failure and Fallback Behavior

- Missing membership, descriptor overflow, incomplete/omitted descriptor groups, group-capacity omission, async saturation, or stale worker output: keep the last valid direct generation when safe and route affected severe weather through the appropriate `familyMacroShape`/broad-map LOD fallback instead of treating the storm as descriptor-owned and making it disappear; expose the reason.
- Descriptor/candidate target allocation or upload failure: disable only the direct-storm subpath for the session/resource generation and continue native broad-map clouds.
- Shader compile or wider native pipeline failure: use the existing session-disable behavior and existing legacy-field developer rollback or vanilla fallback policy.
- World/dimension/owner/resource changes: reject stale results, clear history and published density, then rebuild without blocking.
- Simple Clouds owner: native path stays dormant; optional PA integration failure must not take Simple Clouds ownership away.
- Configuration values outside bounds: Forge bounds and runtime clamps select safe values; diagnostics report configured and effective distances.

## Testing and Regression Strategy

### Deterministic automated checks

- Extend morphology topology checks for deterministic role assignment, BASE-to-CORE-to-TOWER-to-ANVIL overlap, valid vertical ordering, wind-aligned anvil placement, retarget continuity, and stable membership.
- Add `StormVolumetricGeometrySandbox` checks for descriptor pack/unpack, exact base-65 index encoding, valid counted slots/sentinels, complete-group selection, stable ordering, one witness per intersecting group, conservative bounds, distance cross-fade, cache signatures, rejected-build re-request, and allocation-free stable updates.
- Add a fixed synthetic complete-group silhouette test proving tower cross-section is narrower than the base, anvil is wider than the tower, exactly one connected component exists, adjacent-height radius changes are bounded, and no vertical step discontinuity exists.
- Add a locality regression proving that adding, removing, or moving a descriptor outside a probe point's support cannot change density at that point.
- Replace same-function/fake-vector parity with an independent GLSL equation fixture or equivalent real shader parity harness, and compare it with the authoritative Java equations for every role and union case.
- Replace duplicate BASE-density coverage with a geometry-composition test that distinguishes independent lobe evaluation and smooth lobe/group union from a statistical envelope.
- Extend stability diagnostics with a rain/body agreement test proving precipitation support is contained within and attached to the exact rendered union and local BASE-derived underside.
- Test independent history generations and same-frame reset ordering in addition to topology/history invalidation and retention under normal interpolation.
- Test quality target/floor tables, EWMA thresholds, 30/180-frame hysteresis, 30-second cooldown, resolution-history invalidation, adaptive disable, and configuration bounds/defaults.
- Add Phase 4S density-architecture checks: an interior noise-influence regression proving final
  density at unsaturated interior points responds to base-noise and detail-noise perturbation while
  the coverage envelope is held fixed; a density-variance regression over occupied regions spanning
  several detail wavelengths; a per-band spectral regression proving every configured octave reaches
  the final density; a geometric distance-field regression proving each lobe's field is finite,
  monotonic, correctly signed or consistently scaled, valid outside the surface, and independent of
  whether the lobe's local density evaluates to zero; a structural regression measuring base width,
  core concentration, tower narrowing, anvil spread, and transition continuity; and a rejected-form
  regression covering balloon curvature, uniform regions, fitted primitives, isolated protrusions,
  seams, walls, and slabs.
- Every Phase 4S threshold is derived in `validation/morphology-thresholds.md` from the shader's
  configured erosion strength, noise amplitude, octave weights, and octave frequencies. A threshold
  may not be adjusted to accommodate an observed result without a corresponding recorded model
  change.
- Keep `architectureBoundaryCheck`, material advection, region motion, field, topology, and volumetric stability sandboxes passing; register the new sandbox under `check`.

Every new geometry regression assertion must be run against the audited implementation and recorded as failing for the intended defect before its corresponding production fix is implemented.

### Build and launch matrix

1. Run the smallest new storm sandbox and affected existing sandboxes.
2. Run `./gradlew check` and `./gradlew build`.
3. Start default `runClient` without Simple Clouds; verify native ownership and all five modes.
4. Start `runClient -PenableSimpleCloudsRuntime=true`; verify Simple Clouds ownership and no native resource activity.
5. Start a dedicated server to prove client classes/resources are not loaded server-side.

### Visual and performance matrix

- Replace the pre-audit US1 and US2 evidence with new below, beside, inside, and above captures for isolated and overlapping severe groups during growth, steady state, decay, and retargeting.
- Traverse the analytic-to-map LOD band and total render boundary while stationary, walking, sprinting, flying, and turning.
- Validate dry, local rain, remote rain, rain-entry/exit, dense whiteout, and clear-air cases with body/rain/combined debug views.
- Check camera cuts, dimension changes, resize, resource reload, owner change, and quality/resolution transitions for one intentional history reset and no persistent ghost.
- The corrected visual acceptance checklist has a **positive** half and a **negative** half. Both must pass.

  Positive (FR-023): a broad continuous lower cloud base; a dense convective/core region; vertical
  tower development emerging naturally from the base; progressive vertical narrowing where
  appropriate; a broad upper anvil; multi-scale billowing across the visible storm body; surface
  variation at multiple spatial frequencies; irregular but coherent silhouette curvature; and
  continuous transitions between base, tower, core, and anvil.

  Negative (FR-024): no large smooth balloon surfaces; no large regions of visually uniform
  density; no visible ellipsoid or sphere primitives; no isolated ears or bulb protrusions; no
  descriptor seams; no rectangular or vertical walls; no flat slabs; no uniformly smooth
  silhouettes. Rain remains attached to the rendered body and whiteout remains stable.
- After T099 passes, run Low, Low 24, Medium, High, and Ultra; only then perform the ten-minute Ultra reference capture on the specified RTX 4070 laptop and verify p95 total frame time, rebuild rate, adaptive transitions, overflow, and visual criteria from the specification.

## Implementation Sequence

1. Lock source topology invariants and correct storm spawn/retarget geometry with deterministic tests.
2. Introduce descriptor/evaluator data contracts and CPU reference tests without connecting the renderer.
3. Add complete-group selection, candidate-grid generation, dirty signatures, coalesced worker lifecycle, and diagnostics tests.
4. Allocate/bind the two replacement storm textures within the existing texture-unit contract.
5. Add direct shader evaluation, segment intersection, smooth group union, map LOD cross-fade, and temporary diagnostic comparison views.
6. Integrate local volumetric rain, adopted-snapshot whiteout parity, and storm topology history invalidation.
7. Replace the old production storm map bindings/targets/resources after comparison gates pass; retain the broad map and legacy renderer rollback.
8. Run Phase 4R test-first morphology correction: replace statistical envelopes and alpha-style composition with descriptor-local distance-like evaluation and lobe/group smooth unions; repair descriptor validity, fallback, rain attachment, async signatures/re-request, and history identity/reset ordering; then restore the candidate grid as acceleration. *(Complete; superseded in part by step 9.)*
9. Run Phase 4S test-first density-architecture correction: derive and document thresholds; write the interior-noise, variance, spectral, distance-field, structural, and rejected-form regressions and observe them fail; replace density-space pseudo-distance with real world-space geometric distance fields; stop discarding zero-density lobes; express blend radii in world-space units; convert the union result to a bounded coverage envelope; remap base noise against that envelope; apply multi-scale erosion across the interior and remove the storm-specific edge-exposure/erosion-floor exemption; re-derive underside, rain attachment, and camera density from final density.
10. Derive the physical severe-system scale and add the centre-line material trace before another role-local correction. Attribute the first BASE/CORE versus TOWER/ANVIL discontinuity to geometry, density/noise, medium, lighting, or sampling/history.
11. Freeze a reference performance/image/trace baseline, then run Phase 4P structural work in separate tasks and commits: precomputed group topology, bounded metadata/fetches, conservative culling and empty-space rejection, reuse, and a bounded evaluation-cost budget. A lighting-support proxy is conditional on demonstrated equivalence. No step 11 change may alter the rendered result.
12. Correct only the stage measured in step 10, then revalidate physical scale, one-medium continuity, Phase 4S morphology, final-density consumers, and performance together.
13. Re-run US1 and US2 against the revised positive and negative morphology criteria and replace their validation evidence.
14. Extend quality profiles/governor/configuration and existing diagnostics/commands only after T099.
15. Run automated, ownership, launch, visual, failure, and post-correction Ultra performance gates; tune only within the documented contracts.

## Complexity Tracking

No constitution violations require justification.
