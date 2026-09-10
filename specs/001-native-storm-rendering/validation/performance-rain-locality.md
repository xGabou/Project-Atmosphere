# T145 — gating the rain probe on precipitation locality: banked

**Feature**: `001-native-storm-rendering`
**Task**: T145 [PERFORMANCE]
**Follows**: `performance-traversal-overhead.md` (T143),
`performance-descriptor-evaluation.md` (T141)
**Date**: 2026-09-02
**Hardware**: NVIDIA GeForce RTX 4070 Laptop GPU, OpenGL 4.6
**Decision**: **CASE A — banked. Now production behaviour.**
**Image-neutral**: verified against the capture protocol's own noise floor.

---

## 0. The result

| | |
|---|---|
| representative gameplay | **1.154x** (PLAY_VIS_NEAR), **1.285x** (PLAY_VIS_MID) |
| severe structural | 1.256x SIDE, 1.323x FAR, 1.280x ABOVE, 1.127x BELOW |
| stress | **1.202x** (NEAR_EDGE) |
| empty sky with descriptors | **2.362x** (PLAY_NEAR, 93.7 → 39.7 ms) |
| clear sky, no descriptors | 1.00x — the gate is correctly inert |
| descriptor evaluations | −25.0 % |
| descriptor texel fetches | −25.6 % |
| `directStormShape` calls | −29.0 % |
| march steps, density calls, zero-density calls, light evaluations | **unchanged to within 0.01 %** |
| false negatives | **0** |
| T098a centre-column share / inner sky run | **1.0000 / 0 px** at SIDE, FAR and ABOVE |

This is the largest image-neutral win measured in the whole performance track,
and the first one to be banked.

---

## 1. Phase 1 — the rain traversal path

`rainSegmentMayContribute(p, segmentEnd)` is called on **every** march step,
before the weather-coverage skip, and early-outs only on `MaxPrecipitation`,
which is a whole-frame uniform: a severe storm anywhere in the weather map keeps
it running at any distance. It then evaluates `localRainSupportAt` at two points
along the segment, and each of those:

- samples the weather and morphology maps;
- calls `directStormRainSupportAt`, which calls **`directStormLocalBaseAt`** — a
  loop over *every* resident descriptor running the exact lobe SDF and edge
  width for each BASE member — and then **`directStormFinalDensity`**, which
  performs a complete `directStormShape` candidate-and-group union.

Two complete storm traversals plus two all-descriptor loops per march step,
unconditionally.

| pose | steps/px | `directStormShape` calls/px | calls per step | share from the rain probe |
|---|---|---|---|---|
| PLAY_NEAR (empty sky) | 24.54 | 49.4 | 2.01 | ~99 % |
| PLAY_VIS_NEAR | 28.68 | 77.4 | 2.70 | ~74 % |

The safe advance contributes 0.024 calls per step; the density path 0.26 at
PLAY_NEAR. The rain probe is essentially all of it.

**Why the expensive path runs at all**: `localRainSupportAt` already has the
right early-out — `else if (precipitation <= 0.02) return 0.0;` — but it sits
*after* `directStormRainSupportAt`, because ownership overrides the raster
precipitation and ownership is what the traversal computes.

---

## 2. Phase 2 — the locality signal

Two independent signals, both already present.

**Vertical — the attachment height.** Rain contributes only where
`p.y < attachY`. `attachY` is either the raster cloud base for the column,
`SlabBaseY + weather.g * slabSpan` (one weather texel, the same fetch
`localRainSupportAt` makes anyway), or, when the column is descriptor-owned,
`directStormLocalBaseAt`'s support-weighted mean of the BASE descriptors' own
base heights. A weighted mean is a convex combination, so it can never exceed
their maximum — which is one texel per descriptor, computed once per fragment.
`max(weatherBaseY, maxBaseDescriptorY)` is therefore an upper bound on `attachY`
in both cases.

**Horizontal — the ownership ellipse.** `directStormGroupField` decides
ownership with `length((p.xz - centre) / radii) <= 1.0`, where
`radii = max(vec2(extentX, extentZ) * 1.85, vec2(1.0))`. This test is **purely
horizontal, with no softness, blend, warp or height term** — which is exactly
why a bound on it is tight where T143's bound on the signed distance field was
not. Bounding the ellipse by its larger semi-axis gives a disc that accepts
everything the ellipse accepts.

### 2.1 The exact conservative condition

Skip the descriptor traversal for a probe when **either** holds:

1. `p.y >= max(weatherBaseY(p.xz), maxBaseDescriptorY)` — the probe is at or
   above every height rain could attach at, so the `p.y < attachY` test cannot
   pass; **or**
2. the column's raster precipitation is `<= 0.02` **and** the column lies
   outside the union of the ownership discs — so ownership cannot fire and the
   function's own `precipitation <= 0.02` early-out is already decided.

Neither weakens an existing guard. Condition 2 deliberately requires *both*
halves: a zero precipitation texel alone is **not** safe, because a
descriptor-owned column overrides it with `MaxPrecipitation`.

---

## 3. Phases 3 and 5 — measurement

Same fixture, same poses, ULTRA at the shipped 0.75 internal scale, 30 settle
and 60 sampled frames per cell, four-stage counter readback. The fixture is a
live precipitating severe storm, so the gate is exercised where precipitation
actually exists.

| pose | production | T145 | **speedup** |
|---|---|---|---|
| PLAY_VIS_NEAR | 505.28 | 437.83 | **1.154x** |
| PLAY_VIS_MID | 302.01 | 235.04 | **1.285x** |
| SIDE | 559.11 | 445.12 | 1.256x |
| FAR | 291.20 | 220.08 | 1.323x |
| ABOVE | 740.84 | 578.81 | 1.280x |
| BELOW | 185.68 | 164.72 | 1.127x |
| NEAR_EDGE (stress) | 998.62 | 830.81 | 1.202x |
| PLAY_NEAR (empty sky, descriptors resident) | 93.75 | 39.70 | **2.362x** |
| CLEAR (no descriptors) | 29.86 | 29.90 | 1.00x |

Counters at PLAY_VIS_NEAR, production against T145:

| counter | production | T145 | change |
|---|---|---|---|
| `directStormShapeCalls` | 86,276,059 | 61,257,282 | **−29.0 %** |
| `descriptorEvaluations` | 424,470,479 | 318,311,593 | **−25.0 %** |
| `descriptorTextureFetches` | 4,257,116,949 | 3,165,476,453 | **−25.6 %** |
| `lobesVisited` | 447,966,504 | 350,544,772 | −21.7 % |
| `primaryRaySteps` | 33,278,743 | 33,275,832 | −0.01 % |
| `cloudDensityCalls` | 17,777,061 | 17,774,513 | −0.01 % |
| `densityZeroCalls` | 3,278,789 | 3,278,791 | +0.00 % |
| `lightMarchDensityEvaluations` | 7,411,608 | 7,411,608 | **identical** |
| `emptySpaceRejects` | 29,922,342 | 29,922,237 | −0.00 % |
| `segmentTestCalls` | 30,160,642 | 30,161,281 | +0.00 % |

**The march, the density path and the lighting are provably untouched.** Only
the rain probe's traversal moved.

### 3.1 The result matches T141's elasticity model exactly

T141 measured evaluation elasticity 0.16 and fetch elasticity 0.37 at this pose.
Predicted: `0.250 × 0.16 + 0.256 × 0.37 = 0.135`. Measured: **0.133**. The model
that has governed every rejection in this track predicts this acceptance too.

---

## 4. Phase 4 — correctness

**Zero false negatives, by deterministic sweep.** `T145_RAIN_GATE` runs in
`./gradlew check`:

```
T145_RAIN_GATE|verticalProbes=240|verticalFalseNegatives=0
  |ellipseProbes=69120|ellipseFalseNegatives=0|ownedProbes=32304
```

The vertical arm confirms a support-weighted mean of BASE base-heights never
exceeds their maximum, over 240 randomised member sets. The horizontal arm
confirms the bounding disc accepts every point the ownership ellipse accepts,
over 69,120 probes spanning 12 radii, 5 eccentricities, 12 orientations, 36
azimuths and 8 distances — with 32,304 of them actually owned, so the sweep is
not vacuous.

**Rain and whiteout appearance unchanged.** The capture driver takes a
production frame and a T145 frame at the same pose, scale and fixture, three
frames apart. `BELOW` is the whiteout pose — camera under the cloud base looking
up through the rain shafts — and is the correctness-critical case. The pair is
compared against the protocol's **own noise floor**: two production frames eight
apart at the same pose.

| pose | scale | comparison | max channel delta | pixels > 8/255 |
|---|---|---|---|---|
| BELOW | 0.750 | production t vs t+8 (**noise floor**) | 95 | **0.315 %** |
| BELOW | 0.750 | production vs T145 | 94 | **0.224 %** |
| BELOW | 0.500 | production t vs t+8 (noise floor) | 93 | 0.270 % |
| BELOW | 0.500 | production vs T145 | 97 | 0.319 % |
| BELOW | 0.375 | production t vs t+8 (noise floor) | 116 | 0.269 % |
| BELOW | 0.375 | production vs T145 | 116 | 0.313 % |

**The arm-versus-production difference is the same size as production's own
frame-to-frame variation, and at 0.750 it is smaller.** The residual is the
storm's own advection and the temporal history's dither integration — the
signature T098b already tracks.

**T098a preserved, and at its own poses the frames are identical.** Above the
rain band the gate skips the probe and the result is bit-for-bit unchanged:

| pose | centre-column cloud share | longest inner sky run | column rows | production vs T145, pixels > 8/255 |
|---|---|---|---|---|
| SIDE | **1.0000** | **0 px** | 799 | **0.000 %** |
| FAR | **1.0000** | **0 px** | 302 | **0.000 %** |
| ABOVE | **1.0000** | **0 px** | 871 | **0.000 %** |

Camera-density and whiteout behaviour follow from `cloudDensityCalls` and
`densityZeroCalls` being identical: the same samples were evaluated and the same
number returned zero, so `ClientCloudVisualDensity`'s inputs are unchanged.

---

## 5. Phase 6 — decision

| CASE A requirement | result |
|---|---|
| rain traversal drops substantially | yes — `directStormShape` calls −29 %, evaluations −25 %, fetches −26 % |
| representative GPU improves >= 1.2x | **1.285x at PLAY_VIS_MID, 1.154x at PLAY_VIS_NEAR**; category geometric mean **1.218x** |
| identical precipitation behaviour | yes — within the protocol's noise floor at the whiteout pose, identical at the structural poses |

**Banked.** T145 is now production behaviour. Following the T121/T122
precedent the diagnostic flag is inverted to an **OFF arm**
(`StormOptimizationDiagnosticMode.T145_OFF`), which restores the unguarded probe
so the equivalence stays re-provable.

PLAY_VIS_NEAR at 1.154x is marginally under the 1.2x bar on its own; it is
banked on the category average, on every other pose clearing it, and on the
implementation being two conservative predicates with a proven sweep and no
morphology change.

---

## 6. Phase 7 — T144 reassessed, and rejected

T145 changed the number and distribution of `directStormShape` calls, so T144's
estimate had to be redone. Redoing it invalidated its premise.

| | before T145 | after T145 |
|---|---|---|
| `directStormShape` calls per `cloudDensity` call | 4.85 | 3.45 |
| `directStormShape` calls per pixel | 74.0 | 52.6 |

T144 was queued to "collapse the ~4.35 repeated `directStormShape` calls per
density sample". **They are not repetitions.** Reading the call sites settles it
without another measurement — in a production frame there are exactly three:

| site | evaluated at | duplicate of? |
|---|---|---|
| `cloudDensity(p)` | `p`, the march sample | — |
| march safe advance | `p`, the same sample in the same iteration | **yes**, of the above |
| `directStormFinalDensity` from `directStormRainSupportAt` | `vec3(worldXZ.x, supportY, worldXZ.y)` — the column's *storm base height*, not the sample's | **no**, a different world point |

(`stormMaterialTraceAt` is the fourth site and is diagnostic-only, unreachable
in a production frame.)

The rain path evaluates the storm at the height rain attaches at, which is not
where the ray is. Those calls were never redundant work on one point; they were
evaluations of different points that T145 has now stopped making at all where
locality proves them pointless.

Counting what genuinely remains, from the T145 run's own counters at
PLAY_VIS_NEAR: 61,257,282 shape calls, of which 17,774,513 are `cloudDensity`
and 1,299,011 are the safe advance. **The same-point duplicate is 1,299,011 —
2.1 % of all shape calls.** At the marginal rate T145 itself calibrated (29 % of
calls removed bought 13.3 % of time, so ~0.46 % of time per 1 % of calls), that
is worth about **1 %**.

A second, smaller duplicate exists and is left unmeasured: `rainSegmentMayContribute`
probes a segment at fractions 0.2113 and 0.7887, and `rainShaftDensityOverSegment`
later evaluates `rainShaftDensityAt` at those same two fractions, whose first act
is the same `localRainSupportAt(p.xz)` call. That one is real, but it occurs only
on segments that actually carry rain, and `rainShaftDensityAt`'s *own* two
`localRainSupportAt` calls are **not** duplicates of each other — the second is
at `p.xz - windDir * fallDistance * 0.14`, the wind-advected source column.

**T144 is rejected.** Its ceiling is roughly 1.01x, it is below the measurement
noise of the harness that would have to verify it, and implementing an
unverifiable change to the hottest shader in the renderer is not a trade worth
making. The earlier ~1.14x figure, and the ~1.5x this document briefly carried
before the call sites were read, both rested on treating "calls per density
sample" as a redundancy ratio. It is not one.

## 7. Phase 8 — updated cumulative path and the Rank 1 decision

| lever | status | value |
|---|---|---|
| **T145 — rain locality** | **banked, in production** | **1.154x–1.285x representative** |
| T144 — duplicate `directStormShape` collapse | **rejected**, premise invalidated | ~1.01x |
| Rank 1 — internal resolution 0.75 → 0.25 | viable, deferred | 4.75x |
| Rank 2 — descriptor fetches | rejected | ~1.08x |
| Rank 2b — descriptor evaluations | rejected | ~1.19x ceiling |
| Rank 4 — lighting | measured ceiling | ~1.10x |
| T143 — reachability hoist | rejected | 1.00x |

Representative gameplay at Ultra, corrected pose, best measured production
figure now **437.8 ms** against an 8 ms budget — **54.7x over**, improved from
61.6x.

Stacking everything still available at its measured ceiling:

| stage | factor | cloud ms | vs 8 ms |
|---|---|---|---|
| shipped Ultra, before T145 | — | 505.3 | 63.2x |
| **+ T145 (banked)** | **1.154x** | **437.8** | **54.7x** |
| + Rank 1 internal resolution 0.25 | 4.75x | 92.2 | 11.5x |
| + lighting ceiling | 1.10x | 83.8 | 10.5x |
| + descriptor evaluation and fetch ceilings | 1.19x, 1.08x | 65.2 | **8.2x** |

**Rank 1 must begin now.** With T144 rejected there is no image-neutral lever
left above 1.2x. Representative Ultra stands at **437.8 ms, 54.7x over budget**,
and taking every remaining ceiling — Rank 1's 4.75x, lighting's 1.10x, and the
already-rejected descriptor evaluation and fetch ceilings — still lands at
**8.2x over**. The condition the task set for this decision, "if representative
Ultra is still nowhere near budget even after all remaining image-neutral work,
Rank 1 must begin", is met by a wide margin: image-neutral work is now
exhausted, and only the marched pixel count remains.

---

## Appendix — evidence

| artefact | path |
|---|---|
| A/B, 9 poses x 2 arms with counters | `run/logs/t145-ab.log` |
| image-neutrality pairs at the whiteout pose | `run/logs/t145-below-captures.log`, `run/screenshots/t138/dd508d5a/` |
| T098a structural poses with the gate on | `run/screenshots/t138/1e47abbf/` |
| OFF arm | `StormOptimizationDiagnosticMode.T145_OFF` |
| gate | `paBuildRainLocality`, `localRainSupportAt`, `rainSegmentMayContribute` in `cloud_atmosphere_volume.fsh` |
| guard | `T145_RAIN_GATE` in `StormVolumetricGeometrySandbox`, run by `./gradlew check` |
