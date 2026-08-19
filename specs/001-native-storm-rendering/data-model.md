# Data Model: Native Storm Rendering

**Feature**: `001-native-storm-rendering`  
**Date**: 2026-08-18

This feature adds no persisted or networked schema. All new entities are ephemeral client rendering data derived from the existing server-authoritative model.

**2026-08-19 semantic correction**: `StormLobeDescriptor` values describe a bounded *coverage
envelope*, not final visible density. The GPU packing, field names, identity, and ordering are
unchanged; their meaning is narrowed. Final storm density is produced by remapping the base
volumetric noise field against that envelope and then applying multi-scale detail erosion. See
"Storm Density Composition" below, which supersedes the parts of "Storm Field Evaluation
Invariants" that treated the descriptor union as the visible body.

## Existing Authoritative Entities

### CloudRegionState / CloudClusterState

**Owner**: logical server  
**Lifetime**: world saved data  
**Purpose**: authoritative weather lifecycle, cluster membership, location, geometry inputs, and gameplay state.

Relevant invariants:

- The server is the only writer of persistent storm state.
- A structured storm retains stable member identity during normal evolution.
- Retargeting changes geometry continuously without replacing group membership.
- Save/load behavior and serialized fields remain unchanged by this feature.

### CloudFieldSnapshot

**Owner**: server-derived state; immutable synchronized copy on client  
**Lifetime**: full/delta sync and client presentation tracks  
**Purpose**: render-authoritative input containing field identity, current/previous center, radius, base/top, density, coverage, lifecycle, humidity, wind, vertical development, storm potential, morphology family, membership, anvil strength, precipitation, source kind, LOD/hydration, and timestamps.

Validation:

- Non-finite or invalid dimensions continue through existing packet/cache validation.
- This feature adds no field to the snapshot.
- Renderer code treats a missing or unknown morphology membership as broad-map fallback.

### CloudMorphologyMembership

**Owner**: server-derived stable morphology identity  
**Fields used**:

| Field | Meaning |
|---|---|
| `groupId` | Stable identity shared by members of one structure |
| `memberIndex` | Stable position in the group |
| `memberCount` | Total expected members |
| `layoutVersion` | Geometry/layout contract version |
| `memberTier` | Existing tier metadata |
| derived stage | BASE, CORE, TOWER, or ANVIL for `STORM_ANVIL` |

Invariants:

- `0 <= memberIndex < memberCount` for valid structured membership.
- Stage derivation is deterministic for a membership and family.
- A client must not rewrite membership to satisfy capacity or quality limits.

## Corrected Source Geometry Contract

The existing cluster/member fields remain the serialized representation. Their generated values gain these invariants for `STORM_ANVIL`:

| Role | Vertical invariant | Horizontal invariant |
|---|---|---|
| BASE | Contains the condensation floor and overlaps CORE roots | Broad connected footprint with seeded non-planar variation |
| CORE | Starts within BASE support and reaches TOWER roots | Remains inside or overlaps the central base region |
| TOWER | Starts within CORE and reaches ANVIL root | Narrows upward and leans continuously with wind |
| ANVIL | Starts within TOWER crown; top is above lower stages | Major axis aligns with wind and extends beyond tower crown |

Transitions interpolate parameters; they do not switch role identity or regenerate layout. Connectivity tolerance used by tests must account for ellipsoid overlap rather than center coincidence.

## New Client Entities

### StormLobeDescriptor

**Owner**: client volumetric renderer  
**Mutability**: immutable record/value  
**Cardinality**: zero to 64 per adopted render generation

| Field | Type | Validation / derivation |
|---|---|---|
| `fieldId` | UUID | Existing field identity; never null |
| `groupId` | UUID | Existing membership identity; never null |
| `groupSlot` | int | `0..7` for one build; stable within generation |
| `memberIndex` | int | Existing valid member index |
| `role` | enum | BASE, CORE, TOWER, ANVIL |
| `centerX`, `centerZ` | double | Interpolated snapshot center plus existing geometry offset |
| `baseY`, `topY` | float | finite, `topY > baseY`; role-local bounds |
| `majorRadius`, `minorRadius` | float | finite and at least one block |
| `sinOrientation`, `cosOrientation` | float | normalized wind/role orientation |
| `shearX`, `shearZ` | float | finite top-relative displacement |
| `density` | float | clamped **coverage envelope strength**, not final visible density; scales how much of the local envelope the noise field is allowed to fill |
| `edgeSoftness` | float | clamped nonzero **envelope** boundary transition width in world-space units |
| `seed01` | float | deterministic normalized field seed |
| `lifecycleStage` | float | clamped interpolated lifecycle |
| `verticalDevelopment` | float | clamped interpolated development |
| `detailWeight` | float | analytic side of the distance cross-fade |

Identity and ordering key: `(groupId, memberIndex, fieldId)`. Continuous values may change without changing topology identity.

Slot validity:

- Every slot with index less than `StormLobeCount` contains a valid real descriptor, never default zero data.
- When a selected member is absent during live refresh, its slot is compacted out or marked with an explicit sentinel that Java and GLSL both skip.
- A missing member must never decode as group 0, role BASE, centered at world origin.
- Morphology values needed by the direct field are descriptor data; per-member raster modulation is not allowed to redefine geometry inside descriptor-owned support.
- Descriptor values bound coverage only. No descriptor field, alone or in combination, may be used as the final visible density of a sample.

GPU layout:

| Texel | RGBA |
|---|---|
| 0 | center X, center Z, base Y, top Y |
| 1 | major radius, minor radius, sin orientation, cos orientation |
| 2 | shear X, shear Z, density, edge softness |
| 3 | seed01, lifecycle stage, vertical development, `groupSlot * 8 + roleId` |

### StormGeometryBuildInput

**Owner**: created on render thread, consumed by client CPU worker  
**Mutability**: immutable copied primitives  
**Purpose**: safe async boundary.

Fields:

- world/dimension/backend generation tokens;
- request generation and geometry signature;
- snapped map origin X/Z and extent;
- configured/effective detail distance and transition width;
- compact descriptor primitives and stable identity keys;
- target grid size and candidate capacity constants.

It contains no `Minecraft`, level, entity, shader, render-target, native buffer, or mutable snapshot reference.

### StormGeometryBuild

**Owner**: CPU worker until atomically published; render thread after validated adoption  
**Mutability**: immutable publication object with exclusively owned primitive arrays/buffer  
**Purpose**: result of complete-group selection and candidate-grid generation.

Fields:

- all generation/signature tokens copied from input;
- selected group/member metadata and descriptor upload values;
- packed candidate pixels;
- active-tile, overflow-tile, maximum-candidate, and omitted-group counts;
- CPU build duration and completion timestamp.

Validation before adoption:

- world, dimension, backend, origin/extent, requested signature, and generation still match;
- capacities and array lengths are exact;
- all packed indices decode to zero or a valid descriptor index;
- result was not previously adopted or released.

### StormRenderSnapshot

**Owner**: render thread; immutable reference published for CPU visual-density readers  
**Lifetime**: last successfully composited/adopted frame until invalidation  
**Purpose**: exact descriptor set used by GPU and Java density evaluator.

Fields:

- adopted topology/render generation;
- descriptor count and read-only primitive descriptor storage;
- group/detail weights;
- map origin/extent and frame interpolation time;
- quality/detail-distance values used for the frame.

Invariants:

- Published only after successful descriptor/candidate binding and cloud composite.
- Cleared on owner/world/dimension/resource invalidation.
- Never mutated after publication.
- Contains the exact compacted/sentinel-valid descriptor set whose union produced the visible body.

## LobeDistanceField

**Owner**: `StormLobeEvaluator` (authoritative) and its independent GLSL mirror
**Mutability**: pure function of one descriptor and one world-space probe point
**Purpose**: the geometric domain in which lobes and groups are unioned.

| Property | Requirement |
|---|---|
| Domain | World space, in blocks |
| Sign / scale | Signed distance to the lobe surface, or a consistently scaled monotonic approximation whose world-space scaling is documented in `contracts/storm-density-composition.md` |
| Validity | Finite and correct **outside** the lobe surface as well as inside and on it |
| Source geometry | The lobe's oriented, sheared, vertically profiled analytic volume, using its own radii, orientation, local vertical span, shear, and role profile |
| Monotonicity | Non-decreasing as the probe moves away from the lobe surface along any ray |
| Prohibited derivation | `1 - lobeDensity`, or any other density-space pseudo-distance |
| Prohibited optimization | Skipping a lobe because its local density evaluates to zero |

Union operators consume this field directly:

- lobe-to-lobe smooth union within a group, then group-to-group smooth union;
- blend radii are world-space distances in blocks, derived from the smaller participating lobe's
  world-space radius so blending stays proportional without swallowing a narrow tower;
- the union result is converted to a coverage envelope, never straight to visible density.

## StormDetailModel

**Owner**: `cloud_atmosphere_volume.fsh` constants, mirrored deterministically for CPU tests
**Mutability**: compile-time constants plus quality-mode selection
**Purpose**: the documented source of every derived morphology threshold.

| Field | Meaning |
|---|---|
| `baseNoiseAmplitude` | Standard deviation of the base noise field over the sampled band |
| `coverageRemapForm` | How base noise is remapped against local coverage to produce body density |
| `octaveWeights` | Relative weight of each detail FBM octave |
| `octaveFrequencies` | World-space frequency of each detail octave, in blocks per cycle |
| `stormErosionStrength` | Erosion amplitude applied to descriptor-owned storm samples |
| `interiorErosionEnabled` | MUST be true for descriptor-owned storms; no interior exemption |
| `fineOctaveQualityGate` | Quality mode at and above which the additional fine octave is sampled |

`validation/morphology-thresholds.md` derives every SC-012, SC-013, and SC-014 threshold from these
values and records the derivation. When one of these constants changes, the thresholds are
recomputed from the model, not retuned to keep a test green.

## Storm Density Composition

The ordered composition is authoritative for descriptor-owned storms:

```text
per-lobe LobeDistanceField (world-space)
  -> smooth union lobe-to-lobe within group (world-space blend radius)
    -> smooth union group-to-group (world-space blend radius)
      -> bounded coverage envelope in [0, 1]
        -> base volumetric noise remapped against local coverage  = storm body
          -> multi-scale detail erosion across the whole body
            -> final storm density
```

Invariants:

1. The coverage envelope never leaves stage 4 as a final density value.
2. Detail erosion applies across the storm interior. No edge-exposure gate, erosion floor, or
   equivalent may reduce interior noise contribution to zero for descriptor-owned storms.
3. At any probe point strictly inside coverage and not saturated at 0 or 1, final density responds
   to a perturbation of the base noise field and to a perturbation of the detail field.
4. Every configured detail octave contributes measurable variation to the final density.
5. Storm underside, precipitation support, precipitation attachment height, camera density, and
   whiteout are derived from the **final** storm density, not from the coverage envelope.
6. The Java and GLSL implementations perform the same ordered composition and are compared by an
   independent parity harness, including a deterministic CPU mirror of the noise stages.

## Storm Field Evaluation Invariants

`StormLobeEvaluator` is the authoritative source of the storm equations. The shader independently mirrors those equations; parity is established with an independent GLSL equation fixture or equivalent real harness rather than hard-coded fake GPU values or two Java callers delegating to the same implementation.

The evaluated field obeys all of the following:

1. Storm body density at a point depends only on descriptors whose support affects that point.
2. Moving, adding, or removing an unrelated descriptor outside the probe support does not alter local density.
3. The storm underside is derived locally from contributing BASE lobes rather than one group-wide `groupMinY`.
4. Every descriptor slot inside `StormLobeCount` contains a valid real descriptor.
5. Missing descriptor slots are compacted or explicitly skipped using a sentinel.
6. Precipitation support and attachment height come from the exact same storm union used for visible geometry.
7. `StormLobeEvaluator` is the authoritative source of storm equations.
8. GLSL behavior has an independent parity test against the Java equations.

Additionally, each descriptor is evaluated independently into a distance-like field; lobes are smoothly unioned within a group, then group fields are smoothly unioned group-to-group, with blend radius proportional to the smaller participating lobe radius. The candidate grid and coverage tests are conservative accelerators only and never define authoritative cloud density.

Statistical group moments, one group-wide ellipse, `morphologyScale`-based group rendering, alpha compositing as geometric composition, and a binary group-weight gate are invalid representations of this field.

**Superseded by the 2026-08-19 correction**: where this section refers to the descriptor union as
the visible storm body or to a "distance-like" field, read the union as producing the bounded
coverage envelope of "Storm Density Composition", and read "distance-like" as the real world-space
`LobeDistanceField`. Also invalid: density-space pseudo-distance (`1 - lobeDensity`), skipping
zero-density lobes during union, blend radii expressed in density units, and any storm-specific
suppression of interior detail erosion.

### StormCandidateGrid

**Owner**: `StormLobeSpatialIndex` / adopted build  
**Dimensions**: 256 by 256 tiles, eight candidates per tile  
**Encoding**: four `RGBA32F` channels, each packing two one-based base-65 descriptor indices used as stable group witnesses.

Invariants:

- `0` decodes as empty; `1..64` decode to descriptor slots `0..63`.
- Each nonzero entry identifies one admitted group and resolves that group's bounded contiguous/stable descriptor range; individual candidate entries do not select which lobes contribute density.
- With at most eight admitted group slots and eight entries per tile, every conservatively intersecting admitted group is retained.
- Conservative bounds include smooth-union support; they may add false positives but must not omit a shape intersection.
- Candidate membership may skip expensive group/descriptor work only after conservative coverage and bounded group intersection checks; it never supplies density.

### VolumetricHistoryValidity.Key

**Owner**: native volumetric renderer lifecycle  
**Purpose**: reject temporal history produced for incompatible render identity.

Fields remain world, dimension, owner, resource, topology, and resolution generations. World, dimension, owner, and resource generations are populated independently; one lifecycle generation must not be copied into all four positions. Topology signatures include cluster-sourced severe cells only where appropriate so unrelated LOD cloudlet churn does not invalidate storm history. A pending reset is applied before the first frame under the new key can composite, closing the one-frame deferred-reset window.

### AdaptiveQualityState

**Owner**: `CloudFrameTimeGovernor` on render thread  
**Mutability**: immutable value returned to frame setup

| Field | Meaning |
|---|---|
| `selectedMode` | User-selected maximum preset |
| `effectiveBand` | Current bounded adaptive band |
| `stepScale` | Effective step multiplier within preset floor |
| `resolutionScale` | One allowed discrete resolution band |
| `gpuTimeEwmaMs` | Smoothed measured GPU cloud cost |
| `overBudgetFrames` | Consecutive sustained-load counter |
| `underBudgetFrames` | Consecutive sustained-recovery counter |
| `lastTransitionNanos` | Cooldown anchor |
| `transitionGeneration` | Increments only when effective band changes |
| `lastReason` | NONE, OVER_BUDGET, RECOVERY, RESET, CONFIG_CHANGE |

State transitions:

```text
selected nominal
      |
      | 30 consecutive frames above target and cooldown satisfied
      v
one lower band ... down to mode floor
      |
      | 180 consecutive frames below 80% target and cooldown satisfied
      v
one higher band ... up to selected nominal
```

Changing resolution increments transition generation and invalidates render targets/history. Step-only change does not invalidate topology/history.

### StormLobeDiagnosticsSnapshot

**Owner**: bounded diagnostic accumulator; formatted on demand  
**Mutability**: immutable capture

Contains owner, selected/effective quality, configured/effective distances, active/selected/omitted groups, counts by role, descriptor count, tile activity/overflow/max candidates, cache requests/hits/uploads, build submissions/completions/stale discards, generation/signature, rebuild frequency, CPU build/wait/upload timing, analytic/map LOD counts, and fallback/error reason.

Normal frame recording uses primitive fields and existing bounded telemetry behavior. Text is created only for status/capture/log requests.

## Relationships

```text
CloudClusterState 1 ---- * CloudFieldSnapshot
                              |
                              | membership + interpolated render values
                              v
                         StormLobeDescriptor * ---- 1 storm group
                              |
                              +---- StormGeometryBuildInput
                                           |
                                           v async pure CPU
                                   StormGeometryBuild
                                           |
                                           v render-thread validation/upload
                                   StormRenderSnapshot
                                      |              |
                                      v              v
                              GPU raymarch      Java density/whiteout
```

## No-Migration Statement

- No NBT/saved-data migration.
- No packet registration, discriminator, encoding, decoding, or protocol change.
- No forecast model or server tick-state change beyond corrected values produced by the existing morphology generator/retarget code.
- Old worlds retain their identifiers and load through the existing path; corrected role envelopes are applied through normal evolution/retarget behavior.
