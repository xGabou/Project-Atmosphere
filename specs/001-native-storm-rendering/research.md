# Phase 0 Research: Native Storm Rendering

**Feature**: `001-native-storm-rendering`  
**Date**: 2026-08-18

## Repository Findings

### Authority and synchronization

- `CloudRegionState` and `CloudClusterState` are the persistent, server-authoritative weather state.
- `CloudField` and immutable `CloudFieldSnapshot` objects are the render-authoritative projection of that state. `CloudFieldSyncManager` and the existing full/delta packet flow already cover login, dimension changes, removals, and resynchronization.
- `CloudMorphologyMembership` already supplies stable `groupId`, member index/count, layout version, tier, and deterministic BASE/CORE/TOWER/ANVIL stage assignment for `STORM_ANVIL` fields.
- `ClientCloudFieldCache` already interpolates and extrapolates snapshots without making the client authoritative.
- The feature therefore needs no saved-data or packet schema change. Forecast generation and orchestration are consumers of the same authoritative weather state and do not need modification.

### Native rendering

- `VolumetricCloudRenderHook` renders after weather only when `ClientCloudRenderOwnership` selects the native owner. It obtains interpolated fields from `ClientCloudFieldCache`, constructs `VolumetricRenderCell` values, renders weather/morphology support, raymarches, composites, and publishes visual density.
- `CloudWeatherMapRenderer` currently reduces severe-storm members to three two-dimensional support textures: structure, layer height, and tower. `cloud_atmosphere_volume.fsh` then chooses role support and reconstructs height inside generic field bounds.
- That reduction loses the connected three-dimensional relationship between base, core, tower, and anvil. Winner changes between overlapping role supports, common planar bounds, and generic vertical clipping explain the visible seams, slabs, and vertical walls.
- `PuffLobeSpatialIndex` already demonstrates a GL 3.2-compatible direct-lobe design: compact descriptors, a 256 by 256 conservative candidate grid, eight candidates per tile, stable signatures, reusable primitive buffers, cached uploads, direct segment intersection, and CPU diagnostics.
- The main shader currently uses texture units 0 through 14. Adding a new sampler at unit 15 would make the baseline depend on the minimum GL limit. Replacing the three storm-role samplers with two storm samplers avoids increasing the texture-unit requirement.
- The T001-T041 source audit found that the implemented direct path did not realize the planned union. `directStormShape()` and `StormLobeEvaluator.finishGroupEnvelope()` aggregate centers, second moments, extents, density, and group-wide vertical bounds, then evaluate one axis-aligned ellipse controlled by `morphologyScale`.
- That statistical reduction discards most per-role radius, orientation, vertical-span, and shear information before body evaluation. It produces no genuine tower, treats the anvil mostly as expansion of the shared envelope, derives a planar group underside, and explains mushroom silhouettes despite passing tests.
- Group composition is alpha-style accumulation rather than a smooth union on a distance-like field. It can retain intersections and creases, while tests that call the same Java function or use hard-coded fake GPU values cannot establish independent CPU/GLSL parity.

### Storm source geometry

- `CloudMorphologyGenerators.stormAnvilPlan` creates stable seven-to-eleven-member groups, and spawn applies a role envelope.
- Initial storm member placement leaves too many members near a common height, while `retargetCluster` does not reapply the storm role envelope. Rendering alone cannot guarantee stage ordering if authoritative derived geometry loses it during evolution.
- The generator can be corrected without changing identifiers, persistence, synchronization, or storm gameplay: role-specific offsets and vertical spans remain deterministic functions of the existing seed, membership, wind, lifecycle, and type definition.

### Precipitation, whiteout, and history

- `CustomPrecipitationRenderer` owns nearby Minecraft rain/snow quads and should remain separate.
- Volumetric rain shafts use local weather data but several fast paths are disabled by global `MaxPrecipitation`. Coarse sampling of narrow shafts produces screen-space stippling and distant storms can make otherwise empty rays expensive.
- `ClientCloudVisualDensity` feeds `CameraCloudDensityTracker`, but its CPU approximation does not evaluate the direct structured storm that the GPU should display. A shared mathematical contract is required for occupancy/whiteout agreement.
- Temporal history already handles camera cuts, resize, missing clouds, and material discontinuity. It needs a storm topology/render-generation input, but ordinary interpolated motion must not invalidate history every frame.

### Quality, diagnostics, and compatibility

- Existing modes are Low, Low 24, Medium, High, and Ultra through `AtmoCommonConfig.CloudRaymarchQuality` and `VolumetricQualityProfile`.
- `CloudFrameTimeGovernor` currently uses one 4.2 ms budget and only scales steps. It does not express mode-specific floors, resolution bands, or the feature's sustained-load/recovery rules.
- Existing diagnostics and commands are rooted at `/pa cloud volumetric`; `VolumetricCloudFrameDiagnostics`, stability captures, cumulus captures, PUFF index verification, render-state verification, and raymarch debug views should be extended instead of duplicated.
- `CloudBackendResolver`, `ClientCloudRenderOwnership`, and `AtmosphereCloudServices` preserve Simple Clouds ownership. When Simple Clouds owns a dimension, the native renderer is not invoked. That boundary must remain unchanged.

## Decisions

### Decision 1: Keep the current server and network model

**Decision**: Continue deriving native visuals exclusively from interpolated `CloudFieldSnapshot` data and existing morphology membership. Do not add packets, saved fields, forecast behavior, or client-originated weather state.

**Rationale**: All data needed for stable role identity and geometry is already synchronized. A rendering feature should not create a second weather truth.

**Alternatives rejected**:

- Send client-specific meshes from the server: increases bandwidth, couples rendering quality to the server, and violates client-side rendering ownership.
- Reconstruct storms directly from forecast data: forecast data is not the render-authoritative state and would bypass existing managers.

### Decision 2: Use direct analytic storm lobes with a spatial candidate texture

**Decision**: Replace the native production storm structure/height/tower textures with:

- up to 64 compact storm-lobe descriptors in a 4 by 64 `RGBA32F` texture; and
- a 256 by 256 `RGBA32F` candidate texture containing up to eight stable group-witness descriptor indices per tile, packed two base-65 digits per channel; each witness resolves the bounded descriptor range for one group.

The weather and morphology maps remain the broad occupancy/material/precipitation source and the distant-storm fallback. The shader evaluates nearby BASE, CORE, TOWER, and ANVIL descriptors analytically and combines them with the distance-field union defined in Decision 11. The candidate texture is conservative acceleration only and never defines density.

**Rationale**: This preserves actual three-dimensional role bounds and overlap, follows the proven PUFF design, keeps shader work bounded, and replaces three samplers with two rather than exceeding the existing texture-unit contract.

**Alternatives rejected**:

- Improve the three role maps: any small set of 2D maps still loses member identity and produces ambiguous overlap/height reconstruction.
- Upload triangle meshes: cloud density, erosion, lighting, camera-inside behavior, and precipitation still require volumetric evaluation; meshes would add another representation and synchronization problem.
- Loop over every storm field at every ray sample: simple but unbounded and incompatible with predictable render distance and Ultra performance.

### Decision 3: Make source morphology truly three-dimensional

**Decision**: Modify the existing storm spawn and retarget paths so BASE members share an uneven condensation region, CORE members root into it, TOWER members rise and lean with wind, and ANVIL members occupy high, wind-aligned outflow. Values evolve toward retargeted parameters rather than switching layouts.

**Rationale**: The renderer must visualize authoritative derived geometry, not conceal invalid source topology with shader-only offsets. Stable membership and seeds make smooth evolution deterministic.

**Alternatives rejected**:

- Apply all vertical separation only in the shader: CPU diagnostics, whiteout, and other consumers would disagree with visible geometry.
- Regenerate membership during evolution: causes popping, temporal-history churn, and network instability.

### Decision 4: Select complete groups and cross-fade to map LOD

**Decision**: Rank severe-storm groups by camera distance with stable UUID/member-index tie breaks. Admit complete groups only, up to 64 descriptors. Nearby groups use analytic density, a 128-block transition band cross-fades to map LOD, and farther or capacity-omitted groups use the existing weather-map representation.

`nativeStormDetailDistance` is clamped to `cloudRenderDistance`. After the T041 correction, tile candidates represent intersecting admitted groups rather than choosing which individual lobes contribute density. At most eight group slots are admitted, so the eight tile entries can retain every intersecting group; each group is conservatively bounded and its descriptors are evaluated only after a bounded group intersection passes.

**Rationale**: Partial groups create disconnected storms. Whole-group admission and a defined transition band make degradation predictable and prevent popping or holes.

**Alternatives rejected**:

- Truncate individual lobes: can remove a base or tower while retaining its anvil.
- Hard switch at detail distance: creates visible popping and can break precipitation/whiteout continuity.

### Decision 5: Cache all reusable work and build dirty CPU indices safely

**Decision**: Separate immutable `StormGeometryBuildInput`, worker-produced `StormGeometryBuild`, and render-thread-owned GPU resources. A coalescing coordinator permits at most one current build and one latest pending signature. It uses the existing client executor through a non-blocking submission API; if capacity is unavailable, it keeps the last valid build and retries later. Worker code operates only on copied primitives and never accesses `Minecraft`, `RenderSystem`, render targets, shaders, or OpenGL. The render thread validates world/dimension/generation, adopts the newest complete build, and performs uploads.

Descriptor values that change smoothly are refreshed in reusable upload buffers. The 256-square candidate grid rebuilds only when quantized conservative bounds, membership/order, map origin/extent, detail distance, or target identity changes. Stable frames perform neither allocation nor geometry rebuild.

**Rationale**: The grid is safe pure CPU work but too large to risk as a recurring render-frame spike. Coalescing prevents backlog, stale publication, and unnecessary allocation.

**Alternatives rejected**:

- Rebuild on every render frame: violates the performance and allocation constraints.
- Run GL upload on the worker: Minecraft rendering state is render-thread-only.
- Add an unrelated executor framework: the repository already owns async lifecycle and thread naming.

### Decision 6: Define analytic role profiles and one authoritative Java equation contract

**Decision**: Use continuous height-varying ellipsoid/superellipsoid profiles:

- BASE: broad, shallow, irregular condensation lens with tapered perimeter;
- CORE: rooted, vertically stretched mass bridging base and tower;
- TOWER: narrower rising body with height-dependent wind lean and taper;
- ANVIL: high, thin, horizontally extended wind-aligned outflow with curved underside and top.

Descriptor-local bounds, not generic global cloud bounds, constrain these shapes. Java `StormLobeEvaluator` is the authoritative source of storm equations and is consumed by `ClientCloudVisualDensity` and deterministic tests. GLSL independently mirrors that contract, and an independent equation fixture or real parity harness—not two Java callers of the same function and not hard-coded fake GPU values—proves parity. Per-frame density publication includes the exact adopted storm render snapshot.

**Rationale**: A shared contract keeps rendering, whiteout, camera-inside state, and diagnostics consistent while eliminating planar full-height intersections and winner-switch seams.

**Alternatives rejected**:

- Continue approximate CPU box/ellipse occupancy: fails the specification's whiteout agreement requirement.
- Duplicate unrelated constants in multiple Java classes: invites drift and violates the no-duplication principle.

### Decision 7: Integrate volumetric rain locally and independently of body stepping

**Decision**: Keep `CustomPrecipitationRenderer` unchanged. In the volumetric shader, treat `MaxPrecipitation` only as a feature-availability upper bound. Empty-ray decisions use local weather/morphology and the exact analytic storm union. Rain shafts are stable in world space, contained within the same union's horizontal support, attached at the locally contributing BASE-lobe underside, and integrated over coarse segments with deterministic sub-samples rather than forcing all rays into fine body stepping. The CPU precipitation mirror passes the actual maximum precipitation to its maximum parameter. Diagnostics can display body-only, rain-only, and combined density.

**Rationale**: This removes screen-space stipple, prevents a remote rainy cell from disabling empty-space optimization globally, and keeps rain attached to locally raining cloud support.

**Alternatives rejected**:

- Increase all ray steps whenever any rain exists: expensive and still not local.
- Fold the nearby rain-quad renderer into the cloud shader: redesigns a separate working system.

### Decision 8: Extend quality profiles with bounded adaptive state

**Decision**: Preserve all five user modes and add `adaptiveCloudQuality` (default `true`). Each mode defines nominal settings, a GPU-time target, and lower step/resolution floors:

| Mode | Nominal steps / scale | GPU target | Minimum steps / scale |
|---|---:|---:|---:|
| Low | 24 / 25% | 3.0 ms | 12 / 25% |
| Low 24 | 32 / 37.5% | 4.0 ms | 16 / 25% |
| Medium | 40 / 50% | 5.0 ms | 20 / 37.5% |
| High | 64 / 50% | 6.5 ms | 32 / 37.5% |
| Ultra | 96 / 75% | 8.0 ms | 48 / 50% |

The governor uses GPU-time EWMA, lowers one discrete band after 30 consecutive over-budget frames, raises one band after 180 frames below 80% of target, and permits at most one transition per 30 seconds in a stable scene. Resolution changes recreate targets and invalidate history once; step-only changes do not.

**Rationale**: Mode-specific budgets/floors make degradation predictable while retaining the Ultra 60 FPS goal on the reference environment.

**Alternatives rejected**:

- Keep a universal 4.2 ms target: ignores the very different nominal costs and quality expectations.
- Continuously vary resolution: causes target churn and unstable temporal history.

### Decision 9: Extend existing diagnostics and command roots

**Decision**: Add storm counters/timings to `VolumetricCloudFrameDiagnostics` and add a bounded `StormLobeDiagnostics` capture modeled on existing PUFF/cumulus diagnostics. Extend `/pa cloud volumetric diagnostics` with `storm` and extend `/pa cloud volumetric debug view` with storm body, envelope/candidates, precipitation, and combined views. Normal operation records primitive counters only; detailed dumps and logging remain on demand.

**Rationale**: Maintainers get one coherent diagnostic surface without continuous logging or per-frame object graphs.

### Decision 10: Preserve Simple Clouds and rollback ownership

**Decision**: Do not change `CloudBackendResolver`, `ClientCloudRenderOwnership`, Simple Clouds adapters/mixins/assets, or ownership of Simple Clouds managed systems. The new path initializes and renders only under native ownership. Resource/index failure falls back to the broad native weather-map storm LOD; a wider native-pipeline failure follows the existing session-disable/legacy-field-or-vanilla behavior. Keep the developer-only field-renderer rollback property.

**Rationale**: This feature is a native renderer replacement, not a compatibility or ownership migration.

### Decision 11: Evaluate descriptors independently and smooth-union distance-like fields

**Status**: Partially superseded on 2026-08-19 by Decisions 12 and 13. Decision 11's locality,
independent-evaluation, and no-statistical-envelope conclusions remain in force. Its two
implementation shortcuts do not: the "distance-like" field must now be a real geometric distance
field (Decision 13), and the union result is a bounded coverage envelope rather than the final
visible density (Decision 12).

**Decision**: The descriptor set is the evaluated nearby storm field. Evaluate each valid `StormLobeDescriptor` independently at the probe point using its role, oriented radii, local vertical span, shear, density, edge softness, and descriptor-carried morphology values. Convert the role profile to a signed or monotonic distance-like value, then apply a real polynomial or equivalently continuous smooth minimum lobe-to-lobe within a group and group-to-group across storms. Derive each blend radius relative to the smaller participating lobe radius so the union hides primitive intersections without broadening a narrow tower into the base/anvil scale. Convert the final union to density only after composition.

The result obeys locality: a descriptor outside a probe's support cannot change density there. Local storm underside and rain attachment are derived from the contributing BASE lobes in this same union. The candidate grid, broad-map coverage, and bounded per-group intersections may reject proven irrelevant work before expensive evaluation but never provide authoritative density.

**Rationale**: Independent evaluation preserves the actual semantic geometry already carried by descriptors and makes BASE, CORE, TOWER, and ANVIL visibly distinct while still forming one continuous storm. A distance-like smooth union is symmetric, continuous, testable for locality, and suitable for the same Java/GLSL contract. Scaling blend support from the smaller lobe prevents the smoothing radius from erasing narrow features.

**Alternatives rejected**:

- Statistical moment aggregation: averaging centers/extents and deriving one group ellipse makes local density depend on unrelated descriptors, loses orientation/shear/role spans, flattens the underside through `groupMinY`, and recreates the audited mushroom silhouette.
- Alpha compositing of descriptor or group densities: order-independent alpha accumulation still exposes primitive intersections/creases and is not a geometric smooth union.
- A binary `weight > 1e-8` group gate plus `morphologyScale`: presence is not geometry; the gate collapses all contributing descriptors into one envelope and cannot express a local tower or anvil.
- Making the candidate grid authoritative: acceleration coverage can be conservative, incomplete during rebuilds, or capacity-bounded; using it as density would create holes and make async/index state visible as shape changes.

### Decision 12: Descriptors bound a coverage envelope; noise forms the body

**Decision**: The descriptor union produces a bounded *coverage envelope*, not the final storm
density. Final storm density follows one ordered path:

```text
descriptor coverage envelope
  -> base volumetric noise remapping against that envelope
    -> multi-scale detail erosion
      -> final storm density
```

The base noise field is remapped against local coverage so that the visible storm body inside the
envelope is formed by noise, and multi-scale detail erosion then sculpts that body. Erosion applies
across the storm interior, not only at the envelope boundary.

The storm-specific `edgeExposure` / `erosionFloor` behavior in `cloud_atmosphere_volume.fsh` is
removed for descriptor-owned storms. That behavior computes `edgeExposure = 1 - smoothstep(0.26,
0.72, cloud)` and then clamps the eroded result to a floor (`0.42` for storms). Because a correctly
covered storm interior sits well above `0.72`, `edgeExposure` reaches zero there, and detail noise
has no effect over most of the storm body. Combined with a descriptor union that already returned a
near-solid interior, the visible result is a smooth analytic dome with detail confined to a thin
rim - the balloon failure the previous acceptance criteria could not detect.

**Rationale**: Positive morphology (FR-023) requires multi-scale billowing across the whole visible
body and surface variation at several spatial frequencies. That is only achievable if noise, not
analytic geometry, determines where cloud material exists inside the coverage region. This is also
the standard volumetric-cloud composition used by the existing PUFF and cumulus paths, so it does
not introduce a new rendering concept.

**Alternatives rejected**:

- Keep descriptor density as final density and add surface-only detail: reproduces the balloon
  result; interior stays uniform and the silhouette stays smooth.
- Raise the storm erosion strength while keeping the interior floor: increases rim contrast only,
  because the floor and edge-exposure gate still suppress interior contribution.
- Add a second decorative noise layer on top of analytic density: creates surface texture without
  changing the underlying smooth shape, so silhouette curvature stays uniform.

### Decision 13: Use a real geometric distance field per lobe

**Decision**: Each lobe exposes a real signed or consistently scaled geometric distance field
derived from its oriented, sheared, vertically profiled ellipsoid (or equivalent analytic volume),
expressed in world-space units. Replace both the GLSL `float lobeDistance = 1.0 - lobe.x` in
`directStormGroupField()` and the Java `double lobeDistance = 1.0D - lobeDensity` in
`StormLobeEvaluator.unionDensityAt()`.

Consequences:

- The field remains valid and finite outside the lobe surface, so a lobe that a probe point sits
  outside of still contributes correctly to a smooth union.
- The `if (lobeDensity <= 0.0) continue;` early-skips in both implementations are removed. Zero
  local density is not evidence that a lobe is irrelevant to the union; skipping such lobes is what
  makes smooth blends collapse into visible primitive intersections near lobe boundaries.
- Smooth-union blend radii become world-space distances (blocks). The current
  `stormLobeBlendRadius` / `stormGroupBlendRadius` and their Java mirrors operate on a density-space
  quantity where "distance" 0 means fully inside and 1 means absent; a blend radius in that domain
  has no consistent spatial meaning and varies with lobe size, orientation, and profile.
- If an exact signed distance is impractical for the sheared, vertically profiled volume, a
  consistently scaled approximation is permitted, but its scaling to world-space units must be
  documented in the density-composition contract and covered by the distance-field regression.

**Rationale**: A smooth minimum is a geometric operator. Applying it to `1 - density` makes the
blend width depend on the local density gradient rather than on distance, which is why blend
behavior differed between large base lobes and narrow towers and why the union produced ears and
seams at some scales and over-smoothing at others.

**Alternatives rejected**:

- Normalize the density-space pseudo-distance per lobe: still undefined outside the lobe support,
  which is exactly where a union needs it.
- Union in density space with alpha or max: already rejected in Decision 11; neither is a geometric
  union and both expose primitive intersections.

### Decision 14: Validate morphology positively with derived thresholds

**Decision**: Morphology acceptance requires positive evidence (FR-023) in addition to artifact
rejection (FR-024). Deterministic proxies cover interior noise influence, density variance over
occupied regions, per-band spectral contribution, structural section measurements (base width,
core density, tower narrowing, anvil spread, transition continuity), and geometric distance-field
validity.

Every threshold is derived from the rendering model and recorded in
`validation/morphology-thresholds.md` together with the constants it was derived from. When an
implementation constant changes, the derived threshold is recomputed rather than retuned to keep a
test green.

**Rationale**: The previous acceptance set was a list of negatives. A smooth balloon satisfies all
of them. Positive criteria with derived thresholds make the intended result falsifiable.

**Alternatives rejected**:

- Screenshot review only: the failure that motivated this correction passed screenshot review.
- Fixed hand-tuned numeric thresholds: unanchored thresholds drift toward whatever the current
  implementation produces, which is how the previous gate stayed green.

### Decision 15: Storm performance architecture (planned, deferred)

**Decision**: Record the following performance architecture as required plan items. Implement them
in a separate Phase 4P, not inside the correctness refactor, except where an item is inseparable
from making the corrected density model practical.

1. Precompute descriptor group topology on the CPU instead of rescanning descriptors inside every
   density sample. `stormGroupFirstIndex()` / `stormGroupEndIndex()` currently walk up to
   `MAX_STORM_LOBES` descriptors per group per sample, and each walk issues descriptor texel
   fetches.
2. Provide per-group first/end indices, or equivalent compact metadata, to the shader as uploaded
   data.
3. Replace `bool groupVisited[MAX_STORM_GROUPS]` with a compact integer bit mask or another
   GPU-friendly representation. Two functions currently allocate and clear this array per call.
4. Avoid evaluating the full cloud density function for every lighting cone tap when a cheaper
   storm lighting proxy preserves acceptable visual quality; `lightMarchOpticalDepth` and its
   endpoint/capped/refined/no-detail variants are the affected paths.
5. Audit repeated descriptor texture fetches inside `cloudDensity` and `lightMarchOpticalDepth`;
   hoist or reuse fetches that are re-issued for the same descriptor within one evaluation.
6. Maintain a bounded descriptor evaluation cost per sample and per frame, reported through the
   existing storm diagnostics.

Item 1 is the likely exception: the corrected union stops discarding zero-density lobes, so every
admitted group's full descriptor range is evaluated at each sample. Without precomputed group
topology the corrected model may not be practical, and in that case item 1 may be implemented
inside the correctness phase, in its own change, with its own before/after measurement.

**Rationale**: These are structural costs, not tuning. Deferring them entirely until after visual
validation would either block the correctness work or force it to be re-implemented afterwards.

### Decision 16: Correct the implementation ordering

**Decision**: Remove the rule that prohibited all performance work until T098/T099 completed.

- Correctness work required by the corrected density architecture may proceed before final
  morphology validation.
- Performance-specific changes remain separated from visual-correctness changes, in distinct tasks
  and distinct commits, each with its own evidence.
- Architectural changes needed to make the corrected density model practical are not blocked by the
  old Phase 4R gate.
- Final morphology validation (T098, T099) still gates release acceptance and now requires the
  revised positive criteria.

**Rationale**: The old gate assumed the density model was correct and only its tuning was in
question. With the model itself being replaced, an absolute ordering gate blocks the work that the
new model depends on while providing no protection - the gate's own acceptance criteria were the
ones found insufficient.

## Resolved Unknowns

- **Network or save migration**: none required.
- **Descriptor capacity**: 64, admitting complete groups only.
- **Tile capacity**: eight group-witness candidates, sufficient for the eight admitted group slots; individual lobe contribution is never truncated by tile policy.
- **Texture-unit impact**: replace three managed storm samplers with two; do not touch unit 15.
- **Async ownership**: pure copied CPU input/output off-thread; adoption and every Minecraft/GL call on render thread.
- **Distant behavior**: 128-block analytic/map cross-fade and weather-map-only fallback.
- **Configuration additions**: `adaptiveCloudQuality` and `nativeStormDetailDistance`; existing mode and total render distance remain authoritative user controls.
- **Storm density composition**: coverage envelope, base noise remapping, multi-scale erosion,
  final density; descriptors never emit final density.
- **Lobe distance domain**: real geometric distance in world-space units; no density-space
  pseudo-distance; no zero-density lobe skipping.
- **Morphology thresholds**: derived in `validation/morphology-thresholds.md` from the erosion
  strength, noise amplitude, and octave weights actually configured in the shader.
- **Performance architecture**: planned in Phase 4P; only precomputed group topology may enter the
  correctness phase, and only if the corrected model is otherwise impractical.
- **Forecast behavior**: unchanged.
- **Simple Clouds behavior**: unchanged and remains externally owned when selected.
