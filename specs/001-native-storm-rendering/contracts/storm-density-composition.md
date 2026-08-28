# Contract: Storm Density Composition

**Feature**: `001-native-storm-rendering`
**Created**: 2026-08-19
**Audience**: renderer implementers (Java `StormLobeEvaluator`, GLSL `cloud_atmosphere_volume.fsh`)
**Status**: authoritative for descriptor-owned severe storms; supersedes any earlier statement that
the descriptor union is the visible storm body

This contract exists because the previous descriptor/density contract allowed descriptor geometry to
be the final visible density. That produced a smooth, balloon-like storm that satisfied every
artifact-absence criterion while failing the intended result.

## C1. Stages

Descriptor-owned storm density is produced by exactly this ordered composition. No stage may be
skipped, reordered, or short-circuited into a later stage's output.

| Stage | Input | Output | Owner |
|---|---|---|---|
| 1 | one descriptor + world probe point | `LobeDistanceField` (world-space blocks) | `StormLobeEvaluator`, GLSL mirror |
| 2 | lobe distances of one group | group distance field (world-space blocks) | smooth union, lobe-to-lobe |
| 3 | group distance fields | storm distance field (world-space blocks) | smooth union, group-to-group |
| 4 | storm distance field | bounded coverage envelope in `[0, 1]` | envelope mapping |
| 5 | coverage envelope + base noise | storm body density | base noise remapping |
| 6 | storm body density + detail noise | final storm density | multi-scale detail erosion |

Stage 4 output is a **coverage envelope**. It is never returned as final density, never used for
lighting, never used for precipitation support, and never published as camera density.

## C2. Lobe distance field (stage 1)

Required:

- derived from the lobe's own oriented, sheared, vertically profiled ellipsoid or equivalent
  analytic volume;
- signed, or consistently scaled and monotonic, with the scaling to world-space blocks documented
  here by the implementer when an exact signed distance is impractical;
- finite and correct **outside** the lobe surface, not only within its support;
- non-decreasing as the probe moves away from the surface along any ray.

Prohibited:

- `1.0 - lobeDensity` or any other density-space pseudo-distance;
- returning a sentinel "absent" value for probe points outside the lobe;
- skipping a lobe because its local density evaluates to zero.

Replaced on 2026-08-19:

- GLSL `directStormGroupField()`: `float lobeDistance = 1.0 - lobe.x;` and the preceding
  `if (lobe.x <= 0.0) { continue; }`. Now `directStormLobeDistance()`.
- Java `StormLobeEvaluator.unionDensityAt()` (both overloads): `double lobeDistance = 1.0D -
  lobeDensity;` and the preceding `if (lobeDensity <= 0.0D) { continue; }`. Now
  `StormLobeEvaluator.signedDistanceAt()` and `coverageEnvelopeAt()`.

### As-built construction (the C2 documentation requirement)

An exact signed distance to an oriented, sheared, height-varying ellipse intersected with a vertical
span has no closed form, so the implemented field is the documented consistently-scaled equivalent:

1. **Horizontal.** The normalized ellipse coordinate `radial = |oriented / radii(v)|` is converted
   to blocks by dividing out the magnitude of its gradient:
   `effectiveRadius = radial / |oriented / radii²|`. This is *exact* for a circular section and a
   well-behaved first-order distance at the eccentricities the role profiles produce. The wall
   distance is `(radial - 1) * effectiveRadius`.
2. **Vertical.** `capDistance = |y - centreY| - halfHeight`, already in blocks.
3. **Combination.** The two are combined by the standard rounded-box construction in the
   `(radial, height)` half-plane:
   `length(max(q, 0)) + min(max(q.x, q.y), 0) - r`, with `q = (wall + r, cap + r)`.

The fillet radius `r` is `min(0.35 × min(effectiveRadius, halfHeight), 11.36)` blocks. Both bounds
are derived: the wavelength bound stops a cap from reading as a **flat slab** (the noise has no room
to break a boundary sharper than its own coarsest cycle), and the extent bound stops a small lobe
from rounding away into a recognizable **sphere**. Both forms are rejected by FR-024.

**Why not fold the vertical taper into the radius.** The obvious alternative — scaling the profile
radius by the role's vertical shape function — was implemented first and rejected. `verticalShape`
ramps over as little as 8% of a lobe's height, so the surface position moved by tens of blocks per
block of vertical travel near the caps. That is both a false distance (unbounded gradient, so the
field is not world-scaled) and a geometrically flat cap. The rounded-box form has a bounded gradient
everywhere. `verticalShape` consequently no longer participates in the envelope geometry; it remains
only in the legacy `densityAt` used by descriptor packing regressions and the fail-first
reproduction.

## C3. Smooth unions (stages 2 and 3)

- Both union levels operate on the world-space distance field from C2.
- Blend radii are world-space distances in blocks. A blend radius derived from a density-space
  quantity is a contract violation.
- Each blend radius is derived from the smaller participating lobe's world-space radius, so blending
  hides primitive intersections without widening a narrow tower to base or anvil scale.
- The union is symmetric and order-independent within numerical tolerance.
- Locality holds: a lobe whose distance field cannot reach the probe's blend support does not change
  the result there.

## C4. Coverage envelope (stage 4)

- Output is bounded in `[0, 1]` and continuous.
- Descriptor `density` scales envelope strength: how much of the local envelope the noise field is
  permitted to fill. It is not a visible density value.
- Descriptor `edgeSoftness` is the envelope boundary transition width, expressed in world-space
  blocks.
- The envelope is allowed to be near-uniform in the storm interior. Interior uniformity is expected
  at this stage and must be broken by stages 5 and 6, not by stage 4.

## C5. Base noise remapping (stage 5)

- The base volumetric noise field is remapped against local coverage so that the visible storm body
  inside the envelope is formed by noise.
- The remap is monotonic in both coverage and base noise.
- At any point with coverage strictly between 0 and 1 and an unsaturated result, body density
  responds to a perturbation of the base noise field.

## C6. Multi-scale detail erosion (stage 6)

- Detail erosion applies across the whole visible storm body, including its interior.
- Every configured detail octave contributes measurable variation to the final density.
- Prohibited for descriptor-owned storms: any edge-exposure factor that decays to zero in the
  interior, any erosion floor that clamps interior erosion away, and any equivalent
  interior-protection term.

Current code to replace, in `cloud_atmosphere_volume.fsh`:

```glsl
float edgeExposure = 1.0 - smoothstep(0.26, 0.72, cloud);
...
float edgeRetention = 1.0 - (1.0 - detailFbm) * erosion * edgeExposure;
float erosionFloor = stormProfile ? 0.42 : 0.68;
cloud *= clamp(edgeRetention, erosionFloor, 1.0);
```

A correctly covered storm interior sits above `0.72`, so `edgeExposure` is zero there and detail
noise has no effect across most of the body. Non-storm profiles may retain the existing behavior;
this contract governs descriptor-owned storms only.

## C6b. Material continuity through the union

Envelope strength and boundary softness are per-descriptor, so the union must carry them too. They
are blended by the **same interpolation factor the smooth minimum uses**
(`StormLobeEvaluator.blendFactor` / `stormBlendFactor`), which keeps them continuous wherever the
distance field is continuous.

A nearest-lobe-wins selection here is a contract violation: it was implemented first, and produced a
0.21 discontinuity in coverage across a single block wherever the winner changed — exactly the
winner-switch seam the descriptor union exists to remove.

## C6c. The analytic LOD cross-fade acts on the envelope

`StormLobeDescriptor.detailWeight` is the analytic side of the distance cross-fade. It scales the
**coverage envelope**, never the final density. Fading a distant group therefore admits less of the
noise field, dissolving the body into the broad map, rather than uniformly dimming a body that is
still fully shaped. At `detailWeight = 0` the group contributes zero coverage, so the broad map can
take ownership cleanly.

## C7. Downstream consumers

The following are derived from the **final** storm density of stage 6, never from the coverage
envelope:

- visible storm underside;
- volumetric precipitation support and attachment height;
- `ClientCloudVisualDensity` publication and `CameraCloudDensityTracker` input;
- whiteout and inside-cloud state;
- lighting and optical depth, subject to the lighting-proxy allowance in C9.

## C8. Java / GLSL parity

- `StormLobeEvaluator` remains the authoritative source of stages 1 through 4.
- Stages 5 and 6 require a deterministic CPU mirror of the noise stages so that parity, variance,
  and spectral assertions can be evaluated without a GPU.
- Parity is established by the independent GLSL equation fixture, not by two Java callers of the
  same function and not by hard-coded fake GPU values.

## C9. Cost bounds

- Descriptor evaluation cost per sample and per frame stays within the bound recorded in the storm
  diagnostics.
- A cheaper storm lighting proxy may be substituted for the full density function on lighting cone
  taps, provided the visual acceptance criteria still pass. The proxy must be documented and must
  not be used for stage 6 output that feeds C7 consumers.
- Descriptor group topology may be precomputed and supplied to the shader as compact metadata; that
  metadata is acceleration only and never defines density.

## C9a. T121 conservative descriptor rejection: proof and workload comparison

T121 permits skipping a descriptor's exact SDF **only** where a mathematically derived lower bound
proves the lobe cannot affect coverage, final density, the active role mask, or local-height
output. This section records that proof, as required by T121. It adds no approximation: every
skipped descriptor is one whose exact contribution is provably the identity.

### The lower bound

`stormVerticalDistanceLowerBound(p, positionHeight, role)` in
`src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh` returns

```text
capDistance = abs(p.y - centreY) - (roleTopY - roleBaseY) * 0.5
```

Its `roleBaseY` / `roleTopY` come from `stormDescriptorVerticalBounds()`, the **same** helper the
exact SDF uses, so the bound is taken against the identical role-handoff-extended vertical span
rather than a second approximate height profile.

`directStormLobeDistanceFromData()` returns

```text
length(max(vec2(roundedWall, roundedCap), 0.0)) + min(max(roundedWall, roundedCap), 0.0) - rounding
```

with `roundedCap = capDistance + rounding`. When the point lies vertically outside the span,
`capDistance > 0`, hence `roundedCap > rounding > 0`, hence

```text
SDF >= length(vec2(max(roundedWall, 0.0), roundedCap)) - rounding >= roundedCap - rounding = capDistance
```

The rounded-box fillet moves the surface inward, and the `morphologyWarp` term perturbs only
`radial`, which reaches the result through `max(roundedWall, 0.0)` and can therefore only increase
it. So `SDF >= stormVerticalDistanceLowerBound(...)` holds unconditionally. **The bound is
independent of descriptor size and remains valid at the accepted T134 severe-system scale.**

### The rejection guard and why each consumer is unaffected

The sole rejection branch in `directStormGroupField()` is

```glsl
if (started && verticalLowerBound > max(lobeSoftness, groupDistance + STORM_MAX_BLEND_BLOCKS))
```

Write `d` for the lobe's exact distance, so `d >= verticalLowerBound`.

| Consumer | Why the skip is exact |
|---|---|
| Ordered smooth union | `stormLobeBlendRadius()` is `clamp(smaller * f, STORM_MIN_BLEND_BLOCKS, STORM_MAX_BLEND_BLOCKS)`, so `blend <= STORM_MAX_BLEND_BLOCKS = 48` **for any radius**. With `d > groupDistance + 48 >= groupDistance + blend`, the polynomial smooth minimum's `h = saturate(0.5 + 0.5 * (d - groupDistance) / blend)` saturates to exactly `1`, and `mix(d, groupDistance, 1) - blend * 1 * (1 - 1) = groupDistance`. Bit-exact identity. |
| `groupStrength`, `groupSoftness` | Both are mixed by `stormBlendFactor(groupDistance, d, blend)`, which is the same saturating expression and is therefore exactly `1`; `mix(lobeStrength, groupStrength, 1) = groupStrength`. |
| `groupActiveRoleMask` | The mask is set only when `d <= lobeSoftness`. The guard requires `verticalLowerBound > lobeSoftness`, so `d > lobeSoftness` and the mask cannot change. |
| Weighted local height | `heightContribution = 1.0 - smoothstep(0.0, lobeSoftness, d)`, which is exactly `0` for `d > lobeSoftness`. Neither `heightWeight` nor `heightAccum` moves. |
| `groupMinimumRadius` | Updated **before** the branch, so the later group-level blend radius is unchanged. |
| `ownsGroup` | Computed **before** the branch from the descriptor's ownership ellipse, so descriptor-ownership fallback is unchanged. |
| Ordered chain state | `previousRadius` and `previousRole` are assigned **inside** the branch, so the next descriptor's blend radius is chosen exactly as it would have been. |

The guard also requires `started`, so the first descriptor of a group is never skipped and
`groupDistance` is always a real accumulated distance when the comparison is made.

### Retained full evaluation

`cloudDensity()` is retained in full for lighting taps. No lighting-support proxy is implemented,
and any future proxy stays blocked until it proves frozen-image and material-trace equivalence.

### Per-view workload comparison

Executed under `stormPerformanceSuite` with the fixture, poses, governor scale `0.50000`,
resolution scale `0.75000`, compact topology, and configured ray/light steps held equal across
PASS A and PASS B. `conservativeDescriptorRejects` was positive in every one of SIDE, FAR, BELOW,
and ABOVE in both passes, with `structuralChanged=false` throughout; the complete primary-ray,
descriptor-evaluation, descriptor-fetch, light-density, empty-space, and termination counters were
read back from the diagnostic shader frames. The counter increments solely at this vertical-cap
branch and is deliberately distinct from the primary-ray `emptySpaceRejects` counter.

No historical pre-T121 percentage is asserted, because those counters did not exist before T123.

**Comparison basis notice (2026-08-21)**: the accepted T121--T123 execution evidence was collected
on the pre-T134 compact fixture `66b2c85a-aa93-4d18-b428-ac546e280c02`
(fingerprint `459873e8d8c8425a`). The proof above does not depend on descriptor scale, so the
acceptance stands, but T132's visual and material neutrality evidence must be re-collected on a
fresh post-T134 fixture. See `validation/t128-t131-material-continuity.md`, section "T132 rebase
notice", and the revised T132 criteria in `tasks.md`.

## C9b. Shader validation

The independent parity fixture is a separate GLSL program and cannot catch an error elsewhere in the
production shader. The production fragment shader is therefore compiled standalone in a hidden GL
context by `StormVolumetricGeometrySandbox` (`#moj_import` resolved the way Minecraft's loader does
it), so a break surfaces as a compile error in `check` rather than as "clouds disappeared" at
runtime.

## C10. Preserved behavior

This contract changes storm density composition only. It does not change server-authoritative
weather, forecast behavior, network packets, saved weather state, Simple Clouds ownership, legacy
renderer fallback, rain placement ownership, whiteout ownership, history invalidation semantics, or
the candidate texture's role as a scheduling and index hint rather than authoritative geometry.
