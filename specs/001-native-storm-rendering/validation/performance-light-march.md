# T176 - The light march: attribution, and why four taps is not an early-out

Branch `experiment/cloud-descriptor-k`, parent `3cfd1f1` (T175). Nothing merged.
Two campaigns 05-06Sep2026, Ultra, raw scale 0.2500, cloud target 480x270,
framebuffer 1920x1080, 96 steps, 60 samples per cell, anchor-bracketed.
Campaign A: 34 cells, the anchor-relative light arms. Campaign B: 58 cells, the
same light reductions applied **on the stack**, because the brief forbids
inferring stack speedups by multiplication and the stack turns out to carry no
light reduction at all.

## 1. Headline

**The light march is 26% of SIDE frame time and the single largest attributable
block left.** Removing it entirely returns **1.349x** at SIDE - above the 1.25x
the 10 ms target needs. But every *shippable* reduction inside it is small,
because the cost is not where two campaigns of commentary assumed.

**One accepted result: `stack_light3` measures 9.62 ms p50 at SIDE**, the first
sub-10 ms SIDE reading in this series, worth **1.070x** over the stack and
corroborated by an independent anchor-relative measurement of the same change.
It is reported as *met in this run, not yet established*; section 7 says why.

Three structural facts, all newly measurable because T175 fixed the dead
instrumentation gate:

| Fact | Value | Consequence |
|---|---|---|
| `lightConeEarlyOuts` | **0** | there is no early-out; four taps is a hard cap hit every time |
| `tapsPerConeMarch` | **4.0000** | exactly, at both poses, every march |
| `detailFetchLight` / `detailFetchLightShare` | **0** / **0.0000** | light taps already do zero detail work |

**Two of the three things T175 recommended attacking were already spent.**

## 2. Correction to a banked T175 statement

T175's report and tasks entry both say the taps within a cone march "already
terminate early", attributing `tapsPerConeMarch=4.0` to an early-out and
concluding the cost must therefore be the *number* of marches. That is wrong,
and it was wrong for an understandable reason: it was inferred from T169's
bit-identical 6 -> 5 -> 4 tap arms, whose own counters were dead at the time.

The real mechanism, from the code and now confirmed by the counter:

```glsl
int steps = clamp(LightSteps, 2, MAX_LIGHT_STEPS);   // MAX_LIGHT_STEPS = 8
if (cameraStartsInsideSlab) { steps = min(steps, 4); }   // <- this is the 4
...
for (int i = 0; i < MAX_LIGHT_STEPS; i++) {
    if (i >= steps) break;
    if (gradedLight && float(i + 1) > desiredSteps) break;   // T149 only, off in FINAL
```

`tapsPerConeMarch` is 4.0000 because **4 is the ceiling and it is reached every
single time**, not because anything terminates. The only in-loop `break` requires
the T149 graded mode, which FINAL bakes off. T169's 6/5/4 arms were bit-identical
because the inside-slab cap already clamped all three to 4 - they were the same
program. **T169's conclusion survives; its stated mechanism does not.**

This does not invalidate T175's numbers, which came from timing arms and the
histogram - only the sentence about where the light cost lives.

## 3. Task 1 - light-march attribution

Production counters, 129600 cloud pixels. The workload view renders its own
production pass, so these describe FINAL, not the arm under test.

| Per pixel | SIDE | FAR |
|---|---|---|
| primary ray steps | 30.83 | 28.61 |
| **cone marches** | **3.6155** | 0.4528 |
| **taps per cone march** | **4.0000** | **4.0000** |
| **light taps** | **14.4622** | 1.8110 |
| density evaluations per tap | **1.0000** | 1.0000 |
| primary body density calls | 4.7918 | 0.7814 |

Per-tap descriptor work. `lobesVisited / groupFieldCalls` is exactly 10.0000, so
every group walk visits all ten lobes unconditionally:

| Per light tap | SIDE | FAR |
|---|---|---|
| descriptor groups entered | 1.00 | 1.00 |
| lobes visited | **10.00** | 10.00 |
| union contributors | 5.03 | 4.36 |
| descriptor texture fetches | 67.35 DERIVED | 88.08 DERIVED |
| exact SDF evaluations | ~1.20 DERIVED | ~1.91 DERIVED |
| detail/erosion octaves | **0** | **0** |

**Light share of all descriptor group walks: 39.53% at SIDE**, 30.34% at FAR -
which reproduces T175's 39% independently.

Exact-SDF and fetch counts per tap are **DERIVED**, not measured: the shader's
`directStormShapeCalls` and `descriptorTextureFetches` are not split by consumer,
so these apportion the total by the light march's measured 39.53% share of group
walks. Splitting them properly needs another debug view and another flag; it is a
real gap and it is labelled as one rather than presented as measured.

### Termination reason distribution (item 12)

| Reason | SIDE | FAR |
|---|---|---|
| hard tap cap reached (4 of 4) | **100.00%** | **100.00%** |
| transmittance/density early-out | **0.00%** | 0.00% |
| cone distance exhaustion | 0.00% | 0.00% |
| descriptor exit | 0.00% | 0.00% |

Separately, **17.30% of candidate cone marches at SIDE never start at all**
(`lightMarchBelowFloor` 98012 against 468575 executed; 17.73% at FAR). The
existing contribution floor is doing real work *before* the march and nothing
inside it.

`lightAfterAlpha[50] = 1379588` - **73.6% of light taps happen after the primary
ray has already accumulated 50% alpha.**

## 4. Tasks 4 and 7 - tap-count and early-out ceilings

Campaign A, anchor-relative. SIDE anchor ~21.6 ms, FAR anchor ~13.1 ms.
Two repeats, T171 rules, `T176_REJECTED count=0`, all 16 arms accepted on drift.

| Pose | Arm | r1 | r2 | repeat | speedup | verdict |
|---|---|---|---|---|---|---|
| SIDE | `nolight` | 16.0522 | 15.9990 | 0.33% | **1.3462 / 1.3522** | accepted |
| SIDE | `light3` (4->3) | 19.9496 | 20.4134 | 2.30% | 1.0841 / 1.0655 | accepted |
| SIDE | `light2` (4->2) | 18.9880 | 19.0392 | 0.27% | 1.1389 / 1.1394 | accepted |
| FAR | `nolight` | 10.9466 | 10.9087 | 0.35% | 1.1935 / 1.2120 | accepted |
| FAR | `light3` | 12.5164 | 12.6484 | 1.05% | 1.0464 / 1.0436 | accepted |
| FAR | `light2` | 12.0545 | 11.7893 | 2.22% | 1.0946 / 1.1151 | accepted |

Image damage against FINAL:

| Arm | pose | meanAbs | maxAbs | changed px |
|---|---|---|---|---|
| `nolight` | SIDE | 3.198e-02 | 4.14e-01 | 28737 |
| `light3` | SIDE | **5.223e-03** | 3.78e-01 | 22095 |
| `light2` | SIDE | 1.829e-02 | 4.58e-01 | 23619 |
| `nolight` | FAR | 4.546e-03 | 4.21e-01 | 4425 |
| `light3` | FAR | 5.325e-04 | 3.76e-01 | 2443 |
| `light2` | FAR | 1.725e-03 | 4.05e-01 | 2809 |

**Tap reduction captures only 40% of what lighting costs.** Removing lighting is
worth 1.349x; dropping to two taps is worth 1.139x, i.e. `(1.139-1)/(1.349-1)` =
40% of the available gain. The remaining 60% is the per-tap group walk itself -
ten lobes and ~67 fetches on every tap - which tap reduction cannot touch below
two taps.

`light2` costs 3.5x the mean error of `light3` for an extra 6% of speed; it
flattens self-shadowing, consistent with T169 recording `lightsteps2` as its
worst arm on meanAbs at SIDE. **`light3` is the only tap arm whose damage is
arguably tolerable, and it is worth 1.075x.**

### Task 7 - a more aggressive light floor

Not built as a separate arm, and by reasoning rather than omission: the floor's
effect is bounded by what it can skip, and the counters now say it already
rejects 17.3% of candidate marches *before* they start while terminating exactly
0% of taps *inside* them. Since a march is all-or-nothing at four taps, a more
aggressive floor is arithmetically a coarser `nolight` scaled by the extra
marches it rejects. Its ceiling is bounded above by `nolight`'s 1.349x and, for
any floor conservative enough to preserve self-shadowing, far below it.
**DERIVED, not measured, and labelled.**

## 5. Task 6 - light-density simplification

**Largely pre-spent.** `detailFetchLight = 0` and `detailFetchLightShare =
0.0000`: production light taps already skip every detail and erosion octave. The
remaining per-tap cost is:

| Class | Per light tap | Already cheap? |
|---|---|---|
| candidate/group traversal | 1 group, 10 lobes | no - **this is the cost** |
| descriptor invariant data | precomputed | yes, since `4b34b12` (T172/T174) |
| exact SDF | ~1.20 DERIVED | partly |
| weather/base density | 1 evaluation | no |
| erosion/detail | **0** | **yes, entirely** |
| union | 5.03 contributors | no |
| texture fetches | 67.35 DERIVED | no |

A cheaper shadow-density function would have to attack the ten-lobe group walk,
which is exactly what Task 3's reuse idea proposes. **They are the same lever**,
and neither is measured.

## 6. Task 3 - primary-to-light group reuse: NOT MEASURED

**I did not build the reuse oracle, so its ceiling is bounded, not measured.**
Stating that plainly because the brief asked for it specifically and a bound is
not the answer it asked for.

What can be said without it: reuse replaces the light tap's group walk with the
primary sample's already-resolved groups. It cannot be worth more than removing
the light march's descriptor work entirely, and `nolight` removes strictly more
than that - the walk *and* the density math *and* the accumulation. So **reuse is
bounded above by 1.349x SIDE / 1.203x FAR**, and realistically well below, since
it keeps the density evaluation and the accumulation.

That bound is loose enough to be worth closing properly, and it is now the most
valuable unmeasured quantity in the project: it is the only candidate that
attacks the ten-lobe walk - 60% of light cost - without touching morphology. Whether
the group resolved at a primary sample is still the right group 28 units along
`LightDir` is a genuine empirical question, and the brief is right that it must
not be assumed in either direction.

## 7. Task 8 - stack

**The validated stack carries no light reduction at all.** It bakes
`PA_ARM_LIGHT_STEPS 4`, and the inside-slab cap already forces 4, so that define
is a no-op. Anchor-relative light ratios cannot be multiplied onto it, so
campaign B measured three new arms - `t176_stack_nolight`, `t176_stack_light3`,
`t176_stack_light2` - on the stack directly.

Campaign B, SIDE anchor ~20.1-20.7 ms, FAR anchor ~12.5-13.3 ms.
58 cells, `T176_REJECTED count=0` on anchor drift for 24 of 28 arms.

### SIDE - coherent, and the target is crossed

| Arm | r1 p50 | r2 p50 | p95 | repeat | verdict |
|---|---|---|---|---|---|
| `t172_stack_pre` | 10.2871 | 10.2953 | 10.64 / 11.87 | 0.08% | accepted |
| `t176_stack_nolight` | 10.2134 | 7.7332 | - | 27.64% | **REJECTED_repeat** |
| **`t176_stack_light3`** | **9.6348** | **9.6000** | **9.98 / 10.12** | **0.36%** | **accepted** |
| `t176_stack_light2` | 10.8073 | 9.1085 | - | 17.06% | **REJECTED_repeat** |

**`stack_light3` measures 9.62 ms p50 at SIDE - the first sub-10 ms SIDE result
in this campaign series.** Its gain over the stack is **1.0701x**, computed from
the two accepted stack cells rather than multiplied in, and it corroborates the
independent anchor-relative measurement of the same change (1.0738 / 1.0832) to
within 1%. Two different routes to the same number is real evidence.

### FAR - bimodal, and not banked

| Arm | r1 p50 | r2 p50 | repeat | verdict |
|---|---|---|---|---|
| `t172_stack_pre` | 4.9592 | 5.0053 | 0.93% | accepted (one repeat drifted) |
| `t176_stack_nolight` | **8.3835** | **4.2557** | 65.32% | **REJECTED_repeat** |
| `t176_stack_light3` | 8.5463 | 8.5350 | 0.13% | see below |
| `t176_stack_light2` | **4.6295** | **8.5565** | 59.56% | **REJECTED_repeat** |

**The FAR stack family lands in two distinct modes, ~4.3-5.0 ms and ~8.4-8.6 ms,
and two of the four arms straddle both.** `stack_nolight` returns 8.38 then 4.26;
`stack_light2` returns 4.63 then 8.56. Since the same program reaches both
clusters, the clusters are not program differences.

That makes `stack_light3`'s 0.13% agreement at 8.54 ms **agreement within a
sticky mode, not a valid measurement** - it would otherwise read as the stack
becoming 70% slower when given strictly less work to do, which is not physical.

**Methodological consequence, which generalises beyond this campaign: two repeats
that agree can both be wrong.** T171's repeat rule is necessary but not
sufficient, because mode stickiness survives a repeat. It is caught here only
because sibling arms straddled and made the mode visible. A cross-arm mode check
- flagging any arm whose accepted pair sits in a cluster that a sibling arm has
also visited at a very different value - would catch it without needing that
sibling accident, and is the obvious harness follow-up.

No FAR stack figure from this run is banked. **FAR rests on the accepted
`t172_stack_pre` cells: 4.96 / 5.01 here, and T175's 2.406x (~5.32 ms).**

### Targets

| Target | Status |
|---|---|
| SIDE <= 10 ms | **met in this run** at 9.62 p50 (p95 9.98/10.12) - **not yet established**, see below |
| SIDE <= 8 ms | **not met**. `stack_nolight`'s 7.73 fast mode is rejected, and unshippable regardless |
| FAR <= 8 ms | **met** on accepted evidence (~4.96-5.32 ms); this run's 8.5 cluster is a mode artifact |

**Why "met in this run" and not "met".** The same stack measured ~12.5 ms in T173
and T175 and 10.29 ms here, against anchors differing by only ~6%, so the
absolute SIDE figure is not stable across sessions even when it is stable within
one. And this run's own SIDE stack arms demonstrably straddle modes. The
session-independent claim is the **ratio**: light3 on the stack is worth 1.070x,
corroborated two ways. Whether that ratio lands under 10 ms needs a confirmation
campaign, not a second reading of this one.

### Stack image damage

| Arm | pose | meanAbs | maxAbs | changed px |
|---|---|---|---|---|
| `stack_light3` | SIDE | 4.345e-03 | 9.87e-01 | 22582 |
| `stack_light2` | SIDE | 1.261e-02 | 9.87e-01 | 22586 |
| `stack_light3` | FAR | 8.687e-04 | 8.04e-01 | 4276 |
| `stack_light2` | FAR | 1.979e-03 | 8.04e-01 | 4276 |

These are **cumulative**: the stack's footprint-step LOD supplies the 9.87e-01
maxAbs, which is present with or without the tap change. The isolated cost of
4 -> 3 taps is the campaign A figure, meanAbs **5.223e-03** at SIDE.

## 8. Decision

**CASE B, qualified - and it is the only light lever that survived.**

The brief's CASE B asks for a large tap-count ceiling *and* an adaptive 4/3/2
scheme that preserves self-shadowing. Half of that holds:

- **The ceiling is not large.** 4 -> 3 is worth 1.075x anchor-relative and 1.070x
  on the stack; 4 -> 2 is worth 1.139x but is rejected on both quality (meanAbs
  1.83e-02, 3.5x light3's error, visibly flattened self-shadowing, matching
  T169's finding that `lightsteps2` was its worst arm) and on repeat
  disagreement once stacked.
- **But it is decisive at the margin.** On the stack it moves SIDE from 10.29 to
  9.62 ms, the difference between missing and meeting the primary target in this
  run.
- **No adaptive scheme was built.** `light3` is a flat 4 -> 3, not the graded
  4/3/2 CASE B describes.

Answering the remaining decision items directly:

**36. Light-march verdict.** The light march is **26% of SIDE frame time** and the
largest attributable block, but it is *not* soft. Two of its three suspected
inefficiencies do not exist: there is no wasteful early-out to tighten
(`lightConeEarlyOuts=0`) and no detail work to strip (`detailFetchLight=0`).
Roughly 60% of its cost is the ten-lobe group walk executed once per tap, which
tap reduction cannot reach. **Accepted candidate: `light3` only.**

**37. Remaining dominant cost.** The ten-lobe descriptor group walk - now the
single largest identified cost across *both* marches: 36.58 walks per pixel at
SIDE, each visiting all ten lobes unconditionally, with the light march
responsible for 39.53% of them. `lobesVisited / groupFieldCalls = 10.0000`
exactly, at both poses. Nothing prunes a lobe today.

**38. Recommended next architecture.** In priority order:

1. **Close Task 3 properly.** Build the reuse oracle this campaign bounded but did
   not measure. It is the only candidate that attacks the ten-lobe walk without
   touching morphology, and it is currently bounded only by 1.349x.
2. **Prune the lobe walk itself.** All ten lobes are visited on every walk while
   only 5.03 contribute to the union. That ratio has never been attacked and now
   looks like the largest structural inefficiency in the shader.
3. **Use the adaptive tap machinery that already exists.** The T149 graded path
   (`paT149LightContribution/Distance/Vertical`) is written, in-tree, and baked
   off in FINAL. Spending 4 taps on near/opaque material and 3 elsewhere is a
   configuration change to existing code, not new code, and should recover most
   of light3's 1.070x at less than its 5.22e-03 error - especially given **73.6%
   of light taps happen after the primary ray has already passed 50% alpha**,
   where contribution is provably small.
4. **Add the cross-arm mode check** described in section 7 before trusting another
   absolute stack figure.

## 9. Harness notes

- T171 rules throughout: anchor bracketing, two repeats per arm, 3% floor,
  rejection on >3% repeat disagreement, duplicate GPU interval rejection.
- The T175 workload-view invariant is kept and passes. Without it this campaign
  would have reported `lightConeMarches=0`, exactly as T169 did.
- Campaign A's FAR `t172_stack_pre` pair is **REJECTED_repeat** at 42.59%
  (5.3135 / 8.1889) and is neither banked nor cherry-picked; FAR stack figures
  come from campaign B and T175's accepted 2.406x.
- Switch-induced timing bimodality is **not** claimed solved. It is contained by
  rejection, not eliminated.

## 10. Status

Not merged. Productionization `4b34b12`; T170 `9239077`, T171 `aff6fc7`,
T172 `85ff4ef`, T173 `81476b3`, T174 `3af2015`, T175 `3cfd1f1`.
