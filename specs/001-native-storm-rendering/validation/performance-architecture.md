# T137 — performance architecture decision

**Feature**: `001-native-storm-rendering`
**Task**: T137 [PERFORMANCE]
**Date**: 2026-09-01
**Input**: `performance-budget.md` (T135), `performance-baseline.md` (T136)
**Status**: ranked; **no implementation performed** (T138 is the implementation task)

---

## 1. What the measurements constrain

Three T136 results decide the ranking before any option is considered.

1. **Cost is pixel-bound.** Quadrupling cloud-target pixels cost 4.69x and
   8.41x on the two poses measured; raising the step budget 1.6x at fixed
   pixels cost only 1.23x and 1.39x.
2. **The step cap is not the work unit.** Rays terminate on the transmittance
   floor at 38–85 iterations against a 128 cap, so cutting the cap mostly
   removes headroom rather than work.
3. **Lighting is 21–23 %.** Its ceiling as a lever is 1.29x.

And one framing result: **representative gameplay is 2–13x over budget while
the parked severe worst case is 70–125x.** These are different problems and
should not be given one answer.

## 2. Options, ranked by contribution to an order-of-magnitude reduction

### Rank 1 — Internal resolution and temporal reconstruction

| | |
|---|---|
| estimated speedup | **4x from resolution alone; 8–16x with temporal reconstruction** |
| bottleneck addressed | the measured dominant one: per-pixel raymarch work |
| complexity | medium for a scale change; **high** for reconstruction |
| image change | **large** — softer silhouettes, reconstruction artefacts on motion |
| T098b regrade | **required** |
| risk | temporal instability, ghosting on fast camera motion; the storm silhouette is exactly where reconstruction fails most visibly |
| compatibility | the pipeline already renders to a scaled target and already keeps history, so this extends existing structure rather than replacing it |

Ultra renders at 0.75 (1440x810 of 1920x1080). Dropping to 0.375 is 4x fewer
pixels and, at measured near-linear scaling, ~4x. Adding temporal
reconstruction that amortises the march across 2–4 frames multiplies that
again. This is the only option whose ceiling is measured rather than assumed,
and it is the only one that can carry an order of magnitude by itself.

### Rank 2 — Per-sample cost (descriptor evaluation and fetches)

| | |
|---|---|
| estimated speedup | **1.5–3x (least grounded estimate)** |
| bottleneck addressed | ALU and texture-fetch cost inside `cloudDensity` |
| complexity | medium–high |
| image change | **none if done as a pure representation change** |
| T098b regrade | not required if bit-neutral |
| risk | low visually; moderate correctness risk around the T121/T122 guards |
| compatibility | good — `directStormShape` already loops candidates per sample |

Every density sample walks up to eight candidate descriptors and fetches four
texels each. T122 already avoids some refetches. Packing group data into a
smaller resident form, hoisting per-group constants out of the per-sample loop,
and caching the resolved group between adjacent samples on the same ray are all
available.

**This estimate is not yet measured.** T136 did not wire the existing
`paDescriptorEvaluations` / `paDescriptorTextureFetches` counters into the
sweep, so the fetch volume per frame is unknown. **Measure before committing to
this rank.**

Its attraction is that it is the only large lever that is **image-neutral**, so
it can land before the T098b regrade and reduce how much the regrade has to
absorb.

### Rank 3 — Distance and LOD policy

| | |
|---|---|
| estimated speedup | **1.5–2x on distant storms; ~1x at NEAR_EDGE** |
| bottleneck addressed | per-pixel work on storms that occupy few pixels but full cost |
| complexity | medium |
| image change | moderate at distance; must preserve T098a silhouette and connectivity |
| T098b regrade | required |
| risk | reintroducing the T098 distance-dependent visibility class of defect |
| compatibility | good |

FAR/Ultra already costs 270.6 ms against SIDE's 561.5 ms, so distance helps
without policy. An explicit LOD could roughly halve distant cost — but it does
nothing for the actual worst case, which is the **near** pose.

**Constraint carried forward:** T098a's structural gate was won by fixing
exactly this class of bug (a distance-dependent depth sentinel and a
distance-dependent march starvation). Any LOD policy must keep the T098a
guards green and re-run the structural campaign.

### Rank 4 — Lighting and shadow cost

| | |
|---|---|
| estimated speedup | **1.29x maximum (measured)** |
| bottleneck addressed | the eight-tap light cone per integrated sample |
| complexity | low–medium |
| image change | moderate — this is what produces the anvil's shading |
| T098b regrade | required |
| risk | the T098b anvil investigation identified self-shadow response as the *next visual blocker*; cheapening it fights that work |
| compatibility | good |

Bounded by measurement at 1.29x. Worth taking as a stack multiplier, not as a
strategy, and it directly conflicts with the open T098b finding that the
anvil's shading is already too flat.

### Rank 5 — Samples per ray and raymarch policy

| | |
|---|---|
| estimated speedup | **1.2–1.5x** |
| bottleneck addressed | executed steps through material |
| complexity | low for a cap change; high for a new integration scheme |
| image change | small to moderate |
| T098b regrade | required if the integration scheme changes |
| risk | **high relative to reward** — this is the machinery T098a's acceptance rests on |
| compatibility | good |

Measured sub-linear: 1.6x more step budget cost only 1.23–1.39x. Adaptive
stepping and early termination are already implemented and already working
(rays exit at 38–85 of 128). The remaining headroom is small and the T098
promotion/refinement logic is the most correctness-sensitive code in the
renderer.

### Rank 6 — Temporal reuse beyond reconstruction

Folded into rank 1. Independently it is the same mechanism with the same risks
and no separate ceiling.

## 3. The recommended stack

Multiplicative, in the order they should land:

| stage | lever | factor | cumulative | image-neutral? |
|---|---|---|---|---|
| 1 | per-sample descriptor cost | 2.0x | 2.0x | **yes** |
| 2 | internal resolution 0.75 → 0.375 | 4.0x | 8.0x | no |
| 3 | temporal reconstruction (2–4 frame amortisation) | 2.0–3.0x | 16–24x | no |
| 4 | lighting reduction | 1.29x | 21–31x | no |
| 5 | distance LOD | 1.3x (mixed poses) | **27–40x** | no |

**Expected cumulative speedup: roughly 27–40x**, with a plausible range of
15–50x depending on how much reconstruction is acceptable.

Against that stack:

| case | current Ultra | at 30x | budget | verdict |
|---|---|---|---|---|
| PLAY_MID | ~60 ms (est.) | ~2 ms | 8.0 | **inside** |
| PLAY_NEAR | 102.3 ms | 3.4 ms | 8.0 | **inside** |
| FAR | 270.6 ms | 9.0 ms | 8.0 | marginal |
| SIDE | 561.5 ms | 18.7 ms | 8.0 | **over** |
| ABOVE | 678.8 ms | 22.6 ms | 8.0 | **over** |
| NEAR_EDGE | 999.7 ms | 33.3 ms | 8.0 | **over** |

## 4. Is SC-006 still technically credible?

**For representative gameplay: yes.** PLAY_NEAR needs 12.8x and PLAY_MID needs
4.5x. The stack's first two stages alone (8x) cover both, and the total-frame
budget follows because the non-cloud remainder is only 0.6–2.9 ms.

**For the parked severe worst case: no, not at Ultra, not with this
architecture.** NEAR_EDGE/Ultra needs 125x. Even an optimistic 40x leaves 25 ms
of cloud time against an 8 ms budget and a 16.7 ms frame. Closing that would
need internal resolution below a quarter *and* aggressive reconstruction *and*
the per-sample work halved again — which is a different renderer, not a tuned
one.

The honest options for the worst case are to accept a documented degradation
(quality-mode-driven internal resolution that drops further when the storm
fills the frame), or to state SC-006 against a representative scenario with the
parked worst case recorded as a known excursion. **That is a product decision,
not a technical one, and T137 does not make it.**

## 5. Proposed quality-mode architecture

Given cost is pixel-bound, the mode ladder should be primarily a
resolution-and-reconstruction ladder rather than a step-count ladder — the step
count is currently doing little work for its cost.

| mode | steps now → proposed | scale now → proposed | reconstruction | expected cloud ms (PLAY_NEAR) |
|---|---|---|---|---|
| Low | 24 → 24 | 0.250 → 0.200 | 2-frame | ~1.0 |
| Low 24 | 32 → 28 | 0.375 → 0.250 | 2-frame | ~1.6 |
| Medium | 40 → 40 | 0.500 → 0.300 | 2-frame | ~2.4 |
| High | 64 → 56 | 0.500 → 0.375 | 3-frame | ~3.2 |
| Ultra | 96 → 80 | 0.750 → 0.500 | 3-frame | ~5.0 |

Step counts move only slightly, because T136 showed they are not where the cost
is. The ladder's separation comes from internal resolution and reconstruction
depth. Figures are projections from the measured pixel scaling, not
measurements.

## 6. Quality policy for the work that follows

Per the standing decision, image changes are acceptable subject to:

- **T098a stays satisfied** — connected column, no clean-sky waist, no march
  starvation, hits surviving depth publication. Its four guards stay green and
  its structural campaign is re-run after each landing increment.
- no disappearing-cloud regressions, no major temporal instability;
- **T098b is re-run once the configuration stabilises**, and it now owns an
  extra obligation: reconstruction at lower internal resolution interacts with
  the ~4-pixel beat T098b already has to resolve.

Strict pixel neutrality is replaced by measurable tolerances, except for rank 2,
which should stay bit-neutral because it can.

## 7. First implementation task recommended

**Wire the existing workload counters into the T136 sweep and re-measure**,
then implement **rank 2 (per-sample descriptor cost)** as the first T138
increment.

Reasons, in order:

1. It is the **only large lever that is image-neutral**, so it lands without a
   T098b regrade and without touching the T098a machinery.
2. Its 1.5–3x estimate is the **weakest in this document**, and it is the one
   the existing counters can settle in a single run.
3. Landing it first reduces the multiplier the visually destructive stages have
   to supply, which directly reduces how much image change the project ends up
   buying.

Rank 1 is the bigger lever and will be needed, but it is also the one that
forces the T098b regrade and carries the temporal-stability risk; it should
follow a measurement-backed rank 2 rather than precede it.
