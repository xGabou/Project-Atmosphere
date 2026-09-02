# T141 — descriptor evaluation cost: measured and rejected

**Feature**: `001-native-storm-rendering`
**Task**: T141 [PERFORMANCE]
**Follows**: `performance-descriptor-cost.md` (rank 2 fetches, rejected),
`performance-internal-resolution.md` (T138, rank 1),
`performance-pose-definitions.md` (T142, corrected poses)
**Date**: 2026-09-02
**Hardware**: NVIDIA GeForce RTX 4070 Laptop GPU, OpenGL 4.6
**Decision**: **CASE B for evaluation cost, CASE C for the conservative early-out.**
**Production render path unchanged.**

---

## 0. The answer

**Descriptor SDF evaluation is not the missing lever, and this time it is
measured rather than inferred from the fetch arm.** Doubling the exact
descriptor evaluations at unchanged fetch volume costs **9–16 %** of cloud GPU
time at every pose with a storm in frame. Evaluation elasticity is **0.15–0.31**;
removing *every* descriptor evaluation would therefore buy at most about 1.2x.

**A conservative empty-space early-out at the level where the information
already exists does not work either, and the reason is structural rather than a
tuning failure.** A strictly tighter conservative bound — horizontal and
vertical instead of vertical only — rejected **0.02 %–1.25 %** more lobes and
cost **4.3 %–7.6 %**. It is a net loss at every pose measured.

The one thing that did move is the **fetch** elasticity, and it moves the rank-2
verdict: at the corrected representative pose it is **0.37**, not the 0.10
measured at the empty-sky pose rank 2 was judged on. Descriptor *fetches* cost
roughly **2.5x more per unit of work than descriptor evaluations**. The
achievable fetch reduction is still only ~19 %, so this is worth about 1.08x,
not a rank.

---

## 1. Phase 1 — exact cost decomposition

Same fixture, ULTRA (96 steps), internal resolution pinned to the shipped
0.75 = **1440x810 = 1,166,400 marched pixels** for every cell, 30 settle and 60
sampled frames, four-stage counter readback per cell.

| pose | cloud p50 | steps/px | shape calls/px | shape calls/step | lobes visited/px | SDF evals/px | evals/step | texel fetches/px | density calls/px | zero-density share | segment tests/px | segment positive |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| PLAY_VIS_NEAR | 530.7 | 28.68 | 77.4 | 2.70 | 420 | **392** | 13.67 | 3811 | 17.80 | 20.4 % | 25.74 | 5.6 % |
| PLAY_VIS_MID | 288.8 | 26.48 | 58.3 | 2.20 | 90 | 165 | 6.24 | 1929 | 4.76 | 16.2 % | 25.61 | 1.4 % |
| SIDE | 587.4 | 30.68 | 89.0 | 2.90 | 538 | 516 | 16.83 | 4477 | 25.53 | 14.6 % | 26.43 | 6.3 % |
| FAR | 276.4 | 28.62 | 61.6 | 2.15 | 75 | 164 | 5.73 | 1972 | 3.95 | 17.5 % | 27.91 | 1.1 % |
| ABOVE | 744.0 | 17.44 | 114.3 | 6.55 | 1034 | 955 | 54.79 | 5653 | 78.89 | 23.7 % | 6.12 | 8.3 % |
| BELOW | 344.7 | 14.53 | 91.8 | 6.31 | 918 | 326 | 22.45 | 6135 | 18.53 | 0.0 % | 1.54 | 100.0 % |
| NEAR_EDGE (stress) | 1052.7 | 33.15 | 161.4 | 4.87 | 1491 | 1127 | 34.00 | 8890 | 82.57 | 24.3 % | 20.36 | 59.6 % |
| PLAY_NEAR (empty sky, descriptors resident) | **99.5** | 24.54 | 49.4 | 2.01 | 6 | 103 | 4.20 | 1408 | 0.26 | 11.4 % | 24.46 | **0.1 %** |
| CLEAR (empty sky, no descriptors) | **1.8** | 16.33 | **0** | 0 | **0** | **0** | 0 | **0** | 0 | — | **0** | — |

The T138 figure of ~397 descriptor SDF evaluations per shaded pixel is
reproduced exactly: **392 at PLAY_VIS_NEAR**.

Derived quantities the task asks for, at PLAY_VIS_NEAR:

| quantity | value |
|---|---|
| descriptor evaluations per pixel | 392 |
| descriptor evaluations per march step | 13.67 |
| descriptor evaluations per `cloudDensity` call | 22.0 |
| `cloudDensity` calls that return zero at the coverage gate | **20.4 %** |
| `directStormShape` calls per `cloudDensity` call | **4.35** |
| segment tests whose answer is "no lobe can be reached" | **94.4 %** |

---

## 2. Phase 2 — evaluation elasticity, measured directly

The arm runs each lobe's exact SDF **twice** on the texels already resident in
registers. The second call is perturbed by a uniform uploaded as exactly zero,
so the compiler cannot fold it away while the returned value stays
bit-identical and `min()` is an identity. Descriptor evaluations rise; descriptor
texel fetches do not.

Isolation check at PLAY_VIS_NEAR: evaluations **457,214,033 → 778,868,022
(+70.4 %)**, texel fetches **4,445,714,039 → 4,446,234,939 (+0.01 %)**. The
quantity under test is the only one that moved.

| pose | evaluation work | evaluation time | **evaluation elasticity** | fetch work | fetch time | **fetch elasticity** |
|---|---|---|---|---|---|---|
| PLAY_VIS_NEAR | +70.4 % | +11.4 % | **0.163** | +54.7 % | +20.1 % | **0.367** |
| PLAY_VIS_MID | +35.7 % | +9.2 % | 0.257 | +23.2 % | +16.4 % | 0.705 |
| SIDE | +76.1 % | +12.1 % | 0.159 | +95.6 % | +41.5 % | 0.434 |
| FAR | +30.0 % | +9.2 % | 0.308 | +18.9 % | +15.3 % | 0.811 |
| ABOVE | +92.7 % | +15.6 % | 0.168 | +101.8 % | +24.0 % | 0.236 |
| BELOW | +56.6 % | +2.3 % | 0.041 | +160.1 % | +184.6 % | 1.153 |
| NEAR_EDGE (stress) | +88.2 % | +12.9 % | 0.146 | +80.2 % | +25.9 % | 0.323 |
| PLAY_NEAR (empty sky) | +4.4 % | +3.1 % | — \* | +1.8 % | +4.6 % | — \* |

\* The empty-sky pose visits only 6 lobes per pixel, so the amplification arm
moves its evaluation count by 4.4 % and its fetch count by 1.8 %. Elasticity
computed on a work delta that small is not meaningful and is not quoted.

**Reported separately, as required:**

| category | evaluation elasticity | fetch elasticity |
|---|---|---|
| representative gameplay (`PLAY_VIS_NEAR`, `PLAY_VIS_MID`) | **0.16–0.26** | 0.37–0.71 |
| stress (`NEAR_EDGE`) | **0.15** | 0.32 |
| empty sky with descriptors (`PLAY_NEAR`) | not resolvable at this work delta | not resolvable |

An independent second reading agrees. The `t121_off` arm *removes* the shipped
conservative rejection, adding back 27–34 % of lobe evaluations: at
PLAY_VIS_NEAR that costs +9.9 %, implying an elasticity of 0.19 — the same
number the amplification arm gives by a completely different mechanism.

**Consequence.** Descriptor SDF evaluation is roughly a sixth of representative
cloud GPU time. Making it free would give about **1.19x**. It is not the missing
order of magnitude.

---

## 3. Phase 3 — why empty samples still evaluate

The counters classify it. At PLAY_VIS_NEAR:

| category | share | evidence |
|---|---|---|
| **A. No lobe can be reached, but the scan runs anyway** | the segment test answers "no" on **94.4 %** of calls, and still costs 25.7 calls per pixel | `segmentTestCalls` 25.74/px, `segmentTestPositive` 5.6 % |
| **B. Group may be reachable, individual lobes cannot** | **34.3 %** of visited lobes are already rejected by T121 without an exact SDF | `conservativeDescriptorRejects` / `lobesVisited` |
| **C. Envelope reaches the point but the body remaps to zero** | **20.4 %** of `cloudDensity` calls return zero at the coverage gate *after* the descriptor scan has run | `densityZeroCalls` / `cloudDensityCalls` |
| **D. The ownership path reaches `directStormShape` unnecessarily** | not separable from A with the present counters | — |
| **E. The same sample is evaluated repeatedly** | **4.35 `directStormShape` calls per `cloudDensity` call**, and 2.70 per march step | `directStormShapeCalls` / `cloudDensityCalls` |

**Category E is the largest clean redundancy in the renderer.** One march sample
produces on average more than four independent full descriptor-union
evaluations of the same world point, through the density path, the final-density
path, the structure path and the safe-advance probe. Collapsing them to one
evaluation per sample would remove roughly 77 % of descriptor evaluations —
worth 0.77 x 0.163 = **12.5 %, or about 1.14x**. Real, bounded, and still not a
rank.

---

## 4. Phases 4 and 5 — the conservative early-out, derived and rejected

### 4.1 What was built

`stormLobeDistanceLowerBound` replaces T121's vertical-only slab bound with a
horizontal-and-vertical one. The horizontal term is derived from the SDF's own
wall expression rather than from the geometry, because the SDF converts its
normalized ellipse coordinate to blocks by dividing out the gradient magnitude
and that conversion is exact only for a circular section. Writing
`u = oriented / radii`, the SDF's wall distance is
`(|u| - 1) * |u| / |u / radii|`, and `|u| / |u / radii|` is bounded below by the
smaller radius, so

    wall >= (|oriented| / maxRadius - 1) * minRadius

for every eccentricity. `maxRadius` and `minRadius` are taken over the role's
whole height profile (`stormRoleRadialProfileRange`, including the anvil's 1.56
short-axis widening), the full shear magnitude covers every shear progress in
[0,1], a 0.08 allowance covers what the domain warp can subtract from the
normalized radius, and `STORM_MIN_EDGE_BLOCKS` covers the closing fillet. The
maximum of two valid lower bounds is a valid lower bound, and the comparison is
unchanged, so the arm can only reject **more** lobes, never different ones.

### 4.2 What it achieved

| pose | lobes visited | T121 rejects | reject share | rejected by the horizontal term alone | extra reject share | evaluation change | **time change** |
|---|---|---|---|---|---|---|---|
| PLAY_VIS_NEAR | 489,819,086 | 168,227,172 | 34.3 % | 6,141,950 | **1.25 %** | −1.35 % | **+5.6 %** |
| SIDE | 627,263,210 | 169,102,146 | 27.0 % | 7,130,259 | 1.14 % | −1.18 % | +5.7 % |
| PLAY_VIS_MID | 105,531,146 | 36,809,154 | 34.9 % | 20,993 | 0.02 % | −0.01 % | +5.3 % |
| FAR | 87,520,442 | 30,150,843 | 34.5 % | 18,069 | 0.02 % | −0.02 % | +4.3 % |
| ABOVE | 1,205,803,106 | 172,660,580 | 14.3 % | 443,129 | 0.04 % | −0.04 % | +4.6 % |
| BELOW | 1,070,314,830 | 855,363,352 | 79.9 % | 0 | 0.00 % | +1.66 % | +7.6 % |
| NEAR_EDGE | 1,739,187,334 | 579,952,871 | 33.3 % | 402,249 | 0.02 % | −0.02 % | +7.1 % |

**It costs 4.3–7.6 % and saves at most 1.35 % of evaluations, whose elasticity is
0.16. Net loss at every pose. CASE C.**

### 4.3 Why — and it is structural, not a tuning failure

T121's comparison is
`bound > max(lobeSoftness + margin, groupDistance + STORM_MAX_BLEND_BLOCKS)`,
where `groupDistance` is the running union distance *within the same group*.
That test asks "can this lobe perturb the smooth union?", not "is this sample
empty?". For a sample far from the whole storm, every lobe is far — and so is
`groupDistance`, which raises the threshold by exactly as much as the bound
rises. **A per-lobe conservative bound compared against the running union can
never reject a far sample**, no matter how tight it is. That is why a
mathematically strictly better bound buys 0.02 % at the poses where the samples
really are empty (FAR, PLAY_VIS_MID) and only 1.25 % where they are not.

Skipping the whole `directStormShape` call is not available either: its output
`minDescriptorClearance` is what the march's safe advance consumes, so a sample
that skips the scan has no clearance and the ray cannot advance safely. **The
early-out has to move above the per-sample level to exist at all.**

---

## 5. Phase 8 — the empty-sky control, explained

| | cloud p50 | descriptor evals/px | texel fetches/px | segment tests/px | segment positive |
|---|---|---|---|---|---|
| `PLAY_NEAR` — empty sky, 10 descriptors resident | **99.5 ms** | 103 | 1408 | 24.46 | **0.1 %** |
| `CLEAR` — empty sky, no descriptors | **1.8 ms** | 0 | 0 | 0 | — |

**55x, on two frames that both show nothing but sky.** The whole difference is
descriptor-owned work, because that is the only thing that differs.

But the elasticity arms say that difference is **not** the marginal cost of
evaluations or fetches: at PLAY_NEAR, doubling evaluation work costs +3.1 % and
adding fetches costs +4.6 %. Only 6 lobes are visited per pixel there, and 103
of the pixel's evaluations and essentially all of its 1408 texel fetches come
from **traversal**, not from lobe mathematics — candidate-map lookups,
descriptor validity and group-slot probes, and 24.5 segment tests per pixel that
answer "no" 99.9 % of the time.

So the 97.7 ms is the **fixed cost of entering the descriptor machinery once per
march step**, which `CLEAR` avoids entirely because `StormLobeCount == 0` short
-circuits it. That is a whole-ray property, and it is the one place where an
early-out demonstrably has 55x of headroom sitting behind it.

It does not transfer to the representative pose as stated: at PLAY_VIS_NEAR the
segment test answers "yes" 5.6 % of the time and the storm really is in frame,
so a whole-ray reject would rarely fire. What the control establishes is the
*mechanism* — per-step descriptor traversal overhead — and that the mechanism is
worth 55x when it can be skipped wholesale.

---

## 6. Phases 6, 7 and 9 — decision

**No production change was made, and none is authorized by this evidence.**
Phase 7's authorization is conditional on "evaluation elasticity is meaningful"
and "a large fraction of current evaluations are proven unnecessary". Neither
holds: elasticity is 0.15–0.31, and the conservative early-out proved only
0.02–1.25 % of lobes unnecessary while costing more than it saved.

Phase 6's fail-first correctness sweep was therefore **not built**. Writing a
zero-false-negative proof for a bound that is measured to be a net performance
loss would be work spent qualifying a change that cannot ship. The bound and its
derivation are retained in the shader behind `T141_BOX_BOUND`, defaulting off,
so a future task that finds a use for a tighter conservative bound starts from a
derived expression rather than from nothing.

| stop condition | verdict |
|---|---|
| CASE A — evaluation reduction gives >= 1.5x representative | **no**; ceiling is ~1.19x |
| **CASE B — evaluations move a lot, GPU does not** | **yes.** +70 % evaluations costs +11.4 %. Stop optimizing descriptor evaluation. |
| **CASE C — a conservative early-out cannot eliminate enough** | **yes, and the reason is structural**: T121's comparison is against the running union, so it cannot reject far samples at any tightness. |
| CASE D — T098a changes | not reached; no production change was made |

---

## 7. Updated cumulative path

| lever | status | measured value |
|---|---|---|
| Rank 1 — internal resolution 0.75 → 0.25 | viable, **not adopted** | **4.75x** representative |
| Rank 2 — descriptor fetches | rejected; achievable reduction ~19 % at elasticity 0.37 | ~1.08x |
| **Rank 2b — descriptor evaluations (T141)** | **rejected** | ceiling ~1.19x if free |
| Rank 4 — lighting | measured ceiling | ~1.10x |
| Redundant `directStormShape` collapse (category E) | **not attempted**; largest clean win identified | ~1.14x |
| Rank 3 — distance LOD | not measured | — |

Representative gameplay at Ultra, corrected pose:

| stage | factor | cloud ms | vs 8 ms |
|---|---|---|---|
| shipped Ultra 0.75 | — | 492.8 | 61.6x |
| + internal resolution 0.25 | 4.75x | 103.8 | 13.0x |
| + descriptor evaluation made free | 1.19x | 87.2 | 10.9x |
| + descriptor fetches reduced 19 % | 1.08x | 80.7 | 10.1x |
| + lighting ceiling | 1.10x | 73.4 | 9.2x |
| + `directStormShape` redundancy collapsed | 1.14x | 64.4 | **8.1x** |

**Everything currently identified, stacked at its measured ceiling, is 7.7x
against ~62x required.** The remaining 8.1x is not in any lever that has been
measured.

---

## 8. Next major performance task

The measurements now rule out, with elasticities rather than estimates:
descriptor fetches (0.37, ~19 % available), descriptor evaluations (0.16, ceiling
1.19x), lighting (~1.10x), and march step budget (T136: 1.6x steps cost
1.23–1.39x). Internal resolution is the only measured lever above 2x, and it
tops out at 4.75x.

What has never been measured is the **per-step traversal overhead itself** — the
candidate-map lookup, descriptor validity and group-slot probes, and segment
test that every march step pays before any lobe mathematics happens. The
`PLAY_NEAR` / `CLEAR` pair brackets it at **55x on a stormless frame**, and the
per-pixel counters show it dominates the fetch volume at every pose (1408 of
1408 fetches per pixel at PLAY_NEAR; roughly a third at PLAY_VIS_NEAR).

**Recommended: T143 — hoist storm reachability out of the per-step loop.**
Compute, once per ray (or once per coarse span), the interval of `t` over which
any descriptor-owned lobe can be reached, from the group bounding volumes the
segment test already builds. Outside that interval the march skips the candidate
lookup, the validity probes, the segment test and `directStormShape` entirely,
exactly as `StormLobeCount == 0` already does. Measure it against the
`PLAY_NEAR`/`CLEAR` bracket first — if it does not collapse `PLAY_NEAR` toward
1.8 ms, the mechanism is misidentified and the task stops there.

Second, and independent: **collapse the 4.35 `directStormShape` calls per
density sample to one** (category E). Bounded, image-neutral if the cached value
is the same value, and worth ~1.14x.

---

## Appendix — evidence

| artefact | path |
|---|---|
| evaluation sweep, 9 poses x 5 arms with four-stage counters | `run/logs/t141-eval-sweep.log` |
| discarded first attempt (counter captures leaked across arms) | `run/logs/t141-run1-timing.log` |
| arms | `StormOptimizationDiagnosticMode.T141_EVAL_AMPLIFY`, `.T141_BOX_BOUND` — diagnostic, default off |
| counters | `VolumetricCloudRaymarchDebugView.STORM_WORKLOAD_TERTIARY` (24) and `.STORM_WORKLOAD_QUATERNARY` (25) |
| conservative bound | `stormLobeDistanceLowerBound` / `stormRoleRadialProfileRange` in `cloud_atmosphere_volume.fsh` |
