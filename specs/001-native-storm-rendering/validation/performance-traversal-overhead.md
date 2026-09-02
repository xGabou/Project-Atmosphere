# T143 — hoisting storm reachability out of the march loop: rejected

**Feature**: `001-native-storm-rendering`
**Task**: T143 [PERFORMANCE]
**Follows**: `performance-descriptor-evaluation.md` (T141),
`performance-pose-definitions.md` (T142)
**Date**: 2026-09-02
**Hardware**: NVIDIA GeForce RTX 4070 Laptop GPU, OpenGL 4.6
**Decision**: **REJECTED.** The control does not move once the bound is sound.
**Production render path unchanged.**

---

## 0. The result

The hypothesis was right about *where* the work is and wrong about whether a
conservative bound can remove it.

An unsound first bound produced a spectacular result — PLAY_NEAR 109.2 → 57.8 ms,
**1.89x**, with storm traversal down 80 % — and the fail-first sweep then found
**1296 false negatives in 388,800 probes**, worst case 25.9 blocks of real
material outside the bound. Correcting the derivation made the bound sound and
**the entire gain disappeared**: every pose moves by at most 1.6 %, and the
traversal counters are identical to within 0.02 %, because the sound bound
admits every column the march ever visits.

The reason is structural and worth carrying forward: **a conservative horizontal
reach bound cannot be tight for this storm archetype.** The descriptors' own
edge softness is 165–200 blocks and the anvil's role profile multiplies its radii
by 2.18 and then widens the short axis by 1.56, so a sound reach is 2.1x–4.9x the
lobe's major radius — up to about **2722 blocks for the anvil, which is larger
than the entire 2000-block cloud render distance.**

---

## 1. Phase 1 — the per-step reachability path as it actually is

Three separate call sites re-derive storm reachability inside the march loop.
Only the first was in the hypothesis; the second is much larger.

### 1.1 The segment test — 25.7 calls per pixel

`directStormSegmentMayIntersect(p, segmentEnd)` runs on every coarse step where
`!fine && StormLobeCount > 0`. It takes **3** candidate-map lookups (at 0, 0.5
and 1 along the segment in XZ), and for each of up to 8 ranks fetches the
witness's validity and group slot, then `stormGroupSegmentMayIntersect` fetches
the group's first/end index and **3 texels per descriptor** before a
point-segment-versus-sphere test.

| | PLAY_VIS_NEAR | PLAY_NEAR |
|---|---|---|
| segment tests per pixel | 25.7 | 24.5 |
| share returning "may intersect" | 5.6 % | **0.1 %** |

**Ray-invariant**: the per-descriptor bounding sphere — centre from
`positionHeight` plus half the shear, radius from `max(radius.x, radius.y) *
1.24 + |shear| + 2`, combined with the half-height. Nothing in it depends on the
segment.
**Segment-dependent**: only the closest-point clamp `along` and the final
distance comparison.

### 1.2 The rain probe — two full storm traversals per step

This is the dominant one and it was not in the hypothesis.
`rainSegmentMayContribute(p, segmentEnd)` is called on **every** march step,
before the weather-coverage skip. It early-outs only on
`MaxPrecipitation <= 0.02`, which is a whole-frame uniform, so a severe storm
anywhere in the weather map keeps it running at any distance. It evaluates
`localRainSupportAt` at **two** points along the segment, and each of those:

- samples the weather and morphology maps;
- calls `directStormRainSupportAt`, which calls **`directStormLocalBaseAt`** —
  a loop over *every* resident descriptor running the exact lobe SDF and edge
  width for each BASE member — and then **`directStormFinalDensity`**, which
  performs a complete `directStormShape` candidate-and-group union.

That is two complete storm traversals plus two all-descriptor loops **per march
step**, paid unconditionally.

The counters confirm it exactly. At PLAY_NEAR, 24.54 steps per pixel and
**49.4 `directStormShape` calls per pixel** — 2.01 per step — while the density
path itself accounts for only 0.26 calls per pixel and the safe advance for
0.024. The rain probe is essentially all of it.

### 1.3 The safe advance — 0.024 calls per pixel

`directStormShape` from the march's promotion branch, gated by the segment test.
Negligible at the empty-sky control, material only where the storm is in frame.

---

## 2. Phase 2 — the conservative reach bound

One ray-invariant horizontal disc over every resident descriptor, built once per
fragment in `paBuildStormReachability`, tested per sample by
`paStormColumnOutside`. A column outside it skips the candidate walk, the group
unions, the segment test and the all-descriptor base loop.

The per-descriptor reach is derived in
`StormLobeEvaluator.horizontalReachBlocks`, which the shader mirrors and a
shader-shape invariant pins to it. Every term bounds something the exact
evaluation can do: the widest radius the role's height profile can reach
including the anvil widening; ×1.08 for the most the domain warp can subtract
from the normalized radius; the full shear magnitude, covering every shear
progress in [0,1]; the cap-rounding fillet; the lobe's own edge softness; and
`STORM_MAX_BLEND_BLOCKS` for the smooth union's webbing.

**The gate publishes the distance to the bound as `minDescriptorClearance`, not
the 1.0e9 miss sentinel.** That distance is a genuine lower bound on the distance
to any lobe surface, so the march's safe advance stays conservative; returning
the sentinel would have let it step over material lying just past the bound.

### 2.1 Phase 3's fail-first sweep found the first derivation unsound

The first form was additive — widest radius **plus** softness plus blend plus
fillet. The sweep falsified it immediately:

```
T143_REACH_GUARD|probes=388800|falseNegatives=1296
  |worstMargin=-25.907 blocks at BASE major=24.0 minor=4.8 radius=98.25 y=302.5
```

The cause is the same ellipse-to-blocks property T141 had to reason about. The
exact SDF converts its normalized ellipse coordinate to blocks by dividing out
the gradient magnitude, which is exact only for a circular section. Writing
`u = oriented / radii`, the wall term is `(|u| - 1) * |u| / |u / radii|`, and
`|u| / |u / radii|` is bounded below by the **smaller** radius. An eccentric
lobe therefore reports a distance that grows at only `narrowest / widest` of the
geometric rate, so the guard band must **divide** by the narrow radius rather
than add to the wide one:

    reach = widest * (1.08 + (softness + blend + fillet) / (0.9 * narrowest))
            + |shear|

With that form:

```
T143_REACH_GUARD|probes=388800|falseNegatives=0
  |worstMargin=+88.090 blocks|nonVacuousCentres=360
```

**Zero false negatives across 388,800 probes** spanning four roles, six radii,
eccentricities from circular to 5:1, three height regimes, 24 azimuths, five
distances beyond the bound and nine heights from below the base to above the
top. The sweep is retained as `T143_REACH_GUARD` and runs in `./gradlew check`.

---

## 3. Phases 3 and 5 — what the sound bound is worth

Same fixture, same poses, ULTRA at the shipped 0.75 internal scale, production
arm against the reachability arm, 30 settle and 60 sampled frames per cell.

| pose | production | T143 reachability | change |
|---|---|---|---|
| PLAY_VIS_NEAR | 521.8 | 521.2 | **−0.1 %** |
| PLAY_VIS_MID | 307.5 | 309.6 | +0.7 % |
| SIDE | 543.5 | 546.8 | +0.6 % |
| FAR | 289.2 | 291.3 | +0.7 % |
| NEAR_EDGE (stress) | 1078.3 | 1078.5 | +0.0 % |
| **PLAY_NEAR (the control)** | **105.2** | **106.9** | **+1.6 %** |

Traversal counters at PLAY_VIS_NEAR, production against arm:

| counter | production | T143 | change |
|---|---|---|---|
| `directStormShapeCalls` | 89,674,838 | 89,660,987 | −0.02 % |
| `segmentTestCalls` | 30,066,580 | 30,067,783 | +0.00 % |
| `primaryRaySteps` | 33,674,477 | 33,670,191 | −0.01 % |
| `descriptorEvaluations` | 468,708,666 | 468,644,530 | −0.01 % |

**The gate never fires.** Not "fires rarely" — the counters are identical to
measurement noise, because the sound disc contains every column the march
visits.

### 3.1 Why the bound is too loose to gate anything

Evaluated on the T134 severe archetype's descriptor scale:

| role | major | minor | edge softness | sound reach | multiple of major |
|---|---|---|---|---|---|
| BASE | 500 | 480 | 165 | 1047 | 2.09x |
| CORE | 380 | 360 | 110 | 780 | 2.05x |
| TOWER | 300 | 280 | 50 | 774 | 2.58x |
| **ANVIL** | 560 | 360 | 200 | **2722** | **4.86x** |

The anvil alone reaches further than `cloudRenderDistance = 2000`. Two
properties drive it, and both are morphology decisions this task is not
permitted to change:

1. **Edge softness is a third of the lobe radius.** The coverage envelope fades
   over ±softness, so material-capable space genuinely extends 165–200 blocks
   past every lobe surface.
2. **The anvil's role profile scales its radii by up to 2.18 and then widens the
   short axis by 1.56**, which both enlarges `widest` and, through the
   eccentricity term, multiplies the guard band by `widest / narrowest ≈ 4.9`.

### 3.2 What this says about the earlier 1.89x

The unsound bound's 109.2 → 57.8 ms was measured, reproducible, and wrong: it was
skipping 80 % of storm traversal on columns where the exact evaluation would
have found material up to 25.9 blocks inside the boundary. It is recorded here
only because it is the reason the fail-first sweep exists, and because it is a
clean example of a performance number that would have shipped a correctness
defect had the sweep not been written first.

---

## 4. Phase 6 — decision

| requirement | result |
|---|---|
| PLAY_NEAR meaningfully approaches CLEAR | **no** — +1.6 %, in the wrong direction |
| visible storm poses also improve | **no** — −0.1 % to +0.7 %, all noise |
| zero false negatives | yes, 0 of 388,800 |
| T098a stays green | not reached; no production change was made |
| no image-quality change | yes, the arm defaults off and never fires anyway |

**T143 is rejected.** The user's stop condition is explicit and it fired at
Phase 3: the control did not move, so the mechanism as a *gate* is
misidentified, and the task was not polished further.

The bound, its derivation, the diagnostic arm and the zero-false-negative sweep
are retained — the arm defaults off, and `T143_REACH_GUARD` runs in `check` — so
that any future task reaching for a conservative descriptor bound starts from a
proven expression and inherits the sweep that falsified the naive one.

---

## 5. What is now known about the PLAY_NEAR / CLEAR gap

T141 bracketed it at 55x and attributed it to per-step descriptor traversal.
T143 refines that: the traversal is real and is dominated by the **rain probe**,
not the segment test — 2.01 `directStormShape` calls per march step against
0.024 from the safe advance — but **it cannot be removed by a conservative
spatial bound**, because the storm's own softness and anvil profile make any
sound bound larger than the render distance.

Two routes remain, and neither is a bounding-volume problem:

1. **Gate the rain probe on precipitation locality rather than geometry.**
   `rainSegmentMayContribute` early-outs only on the frame-wide
   `MaxPrecipitation`. A per-column or per-region precipitation bound — the
   morphology map already carries precipitation per texel — would skip the whole
   probe wherever no rain can attach, without touching descriptor geometry at
   all. This is the natural successor and it is cheap to test.
2. **Reduce the number of storm evaluations per sample** — T144, already queued,
   which collapses the 4.35 `directStormShape` calls per density sample and is
   unaffected by this rejection.

---

## 6. Cumulative path — unchanged

T143 contributes nothing, so the stack is exactly as T141 left it:

| lever | measured value |
|---|---|
| Rank 1 — internal resolution 0.75 → 0.25 | 4.75x (viable, deferred) |
| Rank 2 — descriptor fetches | ~1.08x |
| Rank 2b — descriptor evaluations | ceiling ~1.19x |
| Rank 4 — lighting | ~1.10x |
| T144 — redundant `directStormShape` collapse | ~1.14x (not yet attempted) |
| **T143 — reachability hoist** | **1.00x, rejected** |

Representative gameplay at Ultra remains **492.8 ms against an 8 ms budget**, and
everything identified, stacked at its measured ceiling, remains **7.7x against
~62x required**.

**Rank 1 stays deferred**: T143 produced no structural gain, so there is no
reason to recompute the stack or to spend the image-quality budget now.

---

## Appendix — evidence

| artefact | path |
|---|---|
| A/B with the sound bound, 9 poses x 2 arms | `run/logs/t143-ab-sound.log` |
| bracket run with the unsound bound (retained as the falsified arm) | `run/logs/t143-bracket-unsound.log` |
| arm | `StormOptimizationDiagnosticMode.T143_REACHABILITY` — diagnostic, default off |
| bound | `StormLobeEvaluator.horizontalReachBlocks`, mirrored in `paBuildStormReachability` |
| guard | `T143_REACH_GUARD` in `StormVolumetricGeometrySandbox`, run by `./gradlew check` |
