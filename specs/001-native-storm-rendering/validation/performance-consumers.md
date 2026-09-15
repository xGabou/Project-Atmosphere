# T180 - The 48% was never quadrature, and uniform removal is worth 12x more than pruning

Branch `experiment/cloud-descriptor-k`, parent `655bec1` (T179). Nothing merged.
One campaign 08Sep2026, Ultra, raw scale 0.2500, cloud target 480x270,
framebuffer 1920x1080, 96 steps, 60 samples per cell, three anchor-bracketed
blocks per arm per pose. 62 cells, `T180_REJECTED count=0` on anchor drift.

## 1. Headline

**The empty-span probe scan is worth 1.1972x at SIDE and 1.2918x at FAR**, both
accepted, and it is the largest uniform-removal ceiling measured in this series.
It clears the brief's 1.20x "major architecture target" bar at FAR and sits on it
at SIDE.

**T179's execution inference is now directly supported, not just inferred:**

| | work removed | SIDE gain |
|---|---|---|
| T179 dominance pruning, **per-lane** | 46.75% of exact SDFs | **1.0159x** |
| T180 probe scan, **uniform compile-time** | **18.6%** of exact SDFs | **1.1972x** |

Two and a half times *less* work removed, twelve times *more* speedup. Same
shader, same fixture, same protocol.

**And the category T175 named does not exist.** There is no production
quadrature, no clearance probe, and the bracket bisection runs 20 times in a
whole frame.

## 2. Tasks 1 and 2 (items 5-9) - the real call-site map

Every site reaching `directStormShape`, from the source, with production
reachability checked:

| Call site | Purpose | Production? |
|---|---|---|
| `cloudDensity` (4664) | primary body and light taps | **yes** |
| march refinement (6900) | exact union distance before committing to fine mode | **yes** |
| `directStormFinalDensity` (2911) | rain attachment support | **no** - `PA_PRECIPITATION_ABSENT` |
| `stormMaterialTraceAt` (5718) | stage trace capture | no - debug view |
| `paT162ShapePayload` (6160) | T162 diagnostic payload | no - diagnostic |

`directStormSegmentMayIntersect` deserves its own line: it returns a **bool**,
tests group bounding spheres against the segment, and **never calls
`directStormGroupField` at all**. Segment tests were already cheap. They are not
part of the 48%.

Attribution, tagged at the real call sites rather than derived by subtraction.
The buckets are disjoint and sum back: 548,439 + 1,560,504 + 951,957 + 20 =
3,060,920 against `cloudDensityCalls` 3,060,902.

| SIDE | calls/px | group walks/px | walk share | exact SDF share |
|---|---|---|---|---|
| primary body | 4.23 | - | - | - |
| light taps | 12.04 | - | - | - |
| **empty-span probes** | **7.35** | **7.34** | **21.1%** | **18.6%** |
| **march refinement** | 0 (not via cloudDensity) | **11.11** | **32.0%** | **26.4%** |
| bracket bisection | **0.0002** | 0.0002 | **0.0%** | **0.0%** |
| primary + light | - | - | 46.9% | - |

FAR: probes 29.0% of walks and 26.0% of exact SDFs; refinement 27.9%.

**So T175's "segment tests, clearance probes and quadrature, ~48%" is really the
march's union-distance refinement (32%) plus the empty-span probe scan (21%).**
The refinement is the larger of the two and had never been named.

## 3. Task 6 (items 23-26) - quadrature does not exist in production

All four optical-depth quadrature functions -
`lightMarchOpticalDepthEndpointMidpoint`, `...CappedFull`, `...RefinedPair`,
`...WithoutDetail` - are reachable only under `DebugView == 6`, `7`, `8` and `9`.
They are diagnostic comparison paths for the light march and are dead in FINAL.

**Samples per event, events per pixel and count-reduction ceilings are all not
applicable.** CASE D is excluded before measurement. No arm was built, and none
should have been.

## 4. Task 3 (items 10-12) - what each consumer actually needs

| Consumer | Needs | Classification |
|---|---|---|
| primary body | exact smooth-unioned density (it is the rendered value) | **FULL FIELD REQUIRED** |
| light taps | density integrated into optical depth | **FULL FIELD REQUIRED** |
| **empty-span probe** | one comparison, `density > 0.0008` | **REDUCIBLE** |
| **bracket bisection** | one comparison, `density > 0.0008` | **REDUCIBLE** (but 0.0% of work) |
| march refinement | the union **distance**, to advance conservatively | **REDUCIBLE to a distance query** |
| camera-inside test | one comparison, `> 0.12`, once per pixel | REDUCIBLE, negligible |

Nothing is UNKNOWN. Every production consumer's semantics are readable from its
own use of the return value.

## 5. Tasks 4 and 5 (items 13-22) - ceilings and the cheaper query

| Pose | Arm | blocks | ratioMean | spread | verdict |
|---|---|---|---|---|---|
| SIDE | **`t180_noprobe`** | 3 | **1.1972** | 1.15% | **accepted** |
| SIDE | `t180_nobracket` | 3 | 1.0047 | 1.29% | accepted |
| SIDE | `t180_probe_nodetail` | 3 | **0.9707** | 2.27% | accepted |
| SIDE | `t180_stack_probe` | 3 | 1.9350 | 0.69% | accepted |
| SIDE | `t172_stack_pre` | 3 | 1.8016 | 19.29% | REJECTED_ratio_spread |
| FAR | **`t180_noprobe`** | 3 | **1.2918** | 2.29% | **accepted** |
| FAR | `t180_nobracket` | 3 | 1.0131 | 0.55% | accepted |
| FAR | `t180_probe_nodetail` | 3 | 0.9785 | 3.84% | REJECTED_ratio_spread |
| FAR | `t172_stack_pre` | 2 | 2.5322 | 1.56% | accepted |
| FAR | `t180_stack_probe` | 3 | 2.5401 | 2.44% | accepted |

Image damage:

| Arm | pose | meanAbs | changed px |
|---|---|---|---|
| `t180_noprobe` | SIDE | 2.791e-03 | 1,623 |
| `t180_nobracket` | SIDE | **6.560e-07** | **3** |
| `t180_nobracket` | FAR | **0.000000** | **0** (passed) |
| `t180_probe_nodetail` | SIDE | 5.227e-05 | 45 |

**Bracket bisection: 20 calls in an entire frame, 1.0047x, and bit-identical at
FAR.** It is not a consumer at all. Whatever it costs is unmeasurable, and no
optimization should target it.

### The cheaper-query candidate failed, and the reason matters

`t180_probe_nodetail` drops the subtractive detail erosion from probes. The
semantics are sound: detail computes `max(body - (1-fbm)*EROSION, 0)`, so
removing it can only **raise** the tested value, and a probe that overestimates
density finds material no later than production does. The scan can never advance
over material it would have found. Image damage confirms it - 45 changed pixels
at SIDE, 11 at FAR.

It measures **0.9707x. It is slower.**

The mechanism is that the probe's *value* feeds a control decision, not just a
cost. Overestimating density makes the scan find "material" sooner, so it
advances less and the march takes more fine steps. The extra steps cost more
than the detail octaves saved. **Any conservative cheap probe biases the same
direction and will hit the same wall** - which is a real constraint on the design
space, not a defect in this arm.

## 6. Task 8 (items 30-31) - uniform versus divergent, now with evidence

T179 could only infer this from two campaigns. T180 puts both shapes in one
campaign, on one fixture, under one protocol:

- **Divergent, per-lane:** T179 removed 46.75% of exact SDFs by skipping lobes
  whose contribution was provably zero. **1.0159x.**
- **Uniform, compile-time:** T180 removed 18.6% of exact SDFs by deleting a whole
  consumer at compile time for every lane. **1.1972x.**

The ratio of work removed is 2.5:1 in favour of the pruning arm; the ratio of
speedup is 1:12 against it. This is as close to a controlled comparison of the
two shapes as this harness can produce, and it is consistent with the ten-lobe
loop executing warp-wide.

**Transformations likely to be warp-effective:** removing a consumer, reducing a
fixed iteration count, or specializing at compile time. **Unlikely:** anything
that decides per sample whether to skip work inside a loop other lanes still
execute.

## 7. Task 9 (items 32-37) - stack

| | SIDE | FAR |
|---|---|---|
| `t172_stack_pre` (control) | 1.8016, spread 19.29% - **rejected** | 2.5322 accepted, 4.945 ms |
| `t180_stack_probe` | 1.9350 accepted, **10.538 ms** p50 / 11.065 p95 | 2.5401 accepted, 4.966 ms |
| within-block vs control | 1.0830, spread 18.35% - **rejected** | **0.9933**, spread 2.52% - accepted |

The FAR within-block ratio is **0.9933** - the probe candidate is not a gain on
the stack either, consistent with its standalone 0.9707x/0.9785x.

| Target | Status |
|---|---|
| SIDE <= 10 ms locally | **no** - 10.538 session-local, and the candidate is not accepted as a gain |
| SIDE <= 8 ms locally | **no** |
| FAR <= 8 ms | **yes** - ~4.95-4.97 ms |

## 8. Task 7 (items 27-29) - duplication: NOT MEASURED

I did not instrument repeated sample positions or repeated group resolution
across probe, refinement and primary samples. Stating that rather than inferring
it.

What the attribution does show is that probes and the refinement together
generate 18.45 group walks per pixel at SIDE against the primary march's 30.83
steps, so they are sampling the same rays densely enough that duplication is
plausible - but plausible is not measured, and no caching ceiling is claimed.

## 9. Decision (items 38-41)

**38. Dominant remaining consumer: the march's union-distance refinement**, at
32.0% of group walks and 26.4% of exact SDFs - larger than the probe scan, and
never previously named or attributed. **I did not build a removal ceiling for
it**, which is the main gap in this campaign.

**39. Recommended production architecture.** Not a cheaper probe - that was
tested and is slower for a structural reason. The probe scan's cost is
`PA_EMPTY_SPAN_PROBES = 16` full density evaluations per coarse step, and the
shape that pays is uniform count reduction, not per-probe cheapening.

**40. Estimated realistic gain.** The probe class is worth 1.1972x if removed
entirely, at meanAbs 2.79e-03 and 1,623 changed pixels - which is real damage,
so outright removal is not the candidate. A uniform 16 -> 8 or 16 -> 4 reduction
would capture a fraction of that 1.20x with proportionally less damage; the
fraction is **not yet measured** and I am not going to guess at it.

**41. Recommended T181**, in priority order:

1. **Sweep `PA_EMPTY_SPAN_PROBES` uniformly** - 16, 8, 4, 2 - as compile-time
   arms with image damage at each. This is the direct follow-on and the shape
   T180 shows pays.
2. **Build the refinement removal ceiling.** It is the larger consumer and is
   unmeasured. It needs only a distance, so a cheaper distance-only query is a
   real possibility - but price the ceiling before designing it.
3. **Do not target the bracket bisection.** 20 calls per frame, 1.0047x,
   bit-identical at FAR.

Formally this is **CASE A**: one consumer has a >=1.20x uniform-removal ceiling
and does not need full density. The qualification is that the obvious cheaper
query for it is slower, so the route is count reduction rather than query
substitution.

## 10. Harness notes

- `T180_REJECTED count=0` on anchor drift; all rejections are ratio spread.
- `T175_WORKLOAD_VIEWS declared=26|allEnabled=true` covers views 46-48, so the
  consumer buckets could not have read a confident zero.
- `T170_WIRING campaigns=25|violations=0`, negative proof detects all five
  mutations.
- The consumer buckets are checked against `cloudDensityCalls` rather than
  assumed disjoint: 3,060,920 tagged against 3,060,902 counted, a difference of
  18 calls from the once-per-pixel camera test that carries no tag.

## 11. Status

Not merged. Productionization `4b34b12`; T170 `9239077`, T171 `aff6fc7`,
T172 `85ff4ef`, T173 `81476b3`, T174 `3af2015`, T175 `3cfd1f1`, T176 `b047bc3`,
T177 `f868325`, T178 `cb1197b`, T179 cleanup `42e655d`, T179 `655bec1`.
