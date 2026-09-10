# T185 - The envelope was far too coarse, and fixing it is worth 1.4%

Branch `experiment/cloud-descriptor-k`, parent `88cf4f4` (T184). Nothing merged.
One campaign 09Sep2026, Ultra, raw scale 0.2500, cloud target 480x270,
framebuffer 1920x1080, 96 steps, 60 samples per cell, three anchor-bracketed
blocks per arm per pose. 50 cells, `T185_REJECTED count=0` on anchor drift.

## 1. Headline

**The envelope really was too coarse - by a lot - and it does not matter.**

At SIDE the shipped circle rejects **14.47%** of rain-support queries. A tightened
box rejects **49.36%**. The exact per-ellipse test rejects **71.50%**.

Tripling the rejection rate is worth **1.0135x**. The exact ceiling, rejecting
five times as many columns as production, is worth **1.0155x**.

Task 3's own gate - close below 1.05x - is met by the *ceiling*. **Closed.**

Both prunes are **bit-identical**: 0 changed pixels, meanAbs 0.000000, at both
poses. The conservativeness claim is verified, not assumed.

**And one premise in the brief had to be corrected before building anything**
(section 3): Task 6's proposed safe form is unsound.

## 2. Task 1 (item 3) - the T145 ownership geometry

From `paBuildRainLocality`, the envelope is built by **stacking three
conservative steps**:

| Step | What it does | Inflation |
|---|---|---|
| 1 | each ownership ellipse -> a **square** of side `2*max(semi-axis)` | up to the major/minor ratio, on the short axis |
| 2 | union of squares -> one **AABB** | none |
| 3 | AABB -> its **circumscribed circle**, `radius = length(maximum - centre)` | **root two** on a square box |

```glsl
vec2 ownershipRadii = max(vec2(extentX, extentZ) * 1.85, vec2(1.0));
float ownershipReach = max(ownershipRadii.x, ownershipRadii.y);   // step 1
minimum = min(minimum, centre - vec2(ownershipReach));            // step 2
paRainOwnRadius = length(maximum - paRainOwnCentre);              // step 3
```

It also accumulates over **every role**, not only the ones that can anchor rain,
so the wide ANVIL lobes inflate an envelope whose purpose is bounded by BASE
descriptors' attachment.

**Why SIDE passes it so often:** the circle is centred on the storm and inflated
by steps 1 and 3. At SIDE the camera is close, so the ray's columns sit near the
storm and inside the inflated disc; at FAR most columns are well outside it.
That is the measured 14.47% against 83.04%.

## 3. Task 6 - the proposed safe form is unsound

The brief proposed:

> outside all rain-owning support => rain impossible regardless of local
> precipitation

**That is false**, and `localRainSupportAt` says so directly:

```glsl
if (directStormOwned) { precipitation = MaxPrecipitation; ... }
else if (precipitation <= 0.02) { return 0.0; }
float localSupport = directStormOwned ? directSupport : weatherCoverage;
```

A column **outside every descriptor's ownership** but with raster
`precipitation > 0.02` returns `weatherCoverage`-based support. Rain exists there
from the raster map, with no descriptor involved. **The
`precipitation <= 0.02` conjunct is load-bearing and was kept.**

So only the geometric half was tightened - which the measurement then showed was
the right half anyway.

## 4. Tasks 1 and 4 (items 5-8) - which conjunct binds

The prune is a conjunct, so each half was counted separately before any arm was
trusted:

| Per rain-support query | SIDE | FAR |
|---|---|---|
| calls | 2,345,714 | 2,275,980 |
| **`precipitation <= 0.02` holds** | **100.00%** | **100.00%** |
| outside the shipped circle | **14.47%** | 83.04% |
| outside the tightened box | **49.36%** | 92.49% |
| outside all exact ellipses | **71.50%** | 94.24% |
| production prune rate | 14.47% | 83.04% |
| accepted, then zero support anyway | **27.08%** | 22.91% |

**`rainPrecipLowFraction` is exactly 1.0000 at both poses.** The precipitation
half never fails on this fixture, so the geometry is the entire binding
constraint - which is what made tightening it worth testing rather than
assuming.

**False positives (items 7-8):** of the columns the prune accepts, 27.08% at SIDE
and 22.91% at FAR carry no support at all. The exact test removes most of that
gap - 71.50% rejection against 14.47% - so the envelope, not the semantics, was
the source.

## 5. Tasks 3, 5 and 9 (items 9-21) - the ceiling and the candidate

| Pose | Arm | blocks | ratioMean | spread | verdict |
|---|---|---|---|---|---|
| SIDE | **`t185_tight_prune`** | 3 | **1.0135** | 1.10% | **accepted** |
| SIDE | **`t185_exact_prune`** (ceiling) | 3 | **1.0155** | 1.83% | **accepted** |
| FAR | `t185_tight_prune` | 3 | 1.0163 | 2.12% | accepted |
| FAR | `t185_exact_prune` | 2 | 1.0171 | 5.17% | REJECTED_ratio_spread |

**Image equality (items 22-27):**

| Arm | pose | passed | meanAbs | changed px |
|---|---|---|---|---|
| **`t185_tight_prune`** | SIDE / FAR | **true** | **0.000000** | **0** |
| **`t185_exact_prune`** | SIDE / FAR | **true** | **0.000000** | **0** |

Zero changed pixels at both poses for both arms. Rain coverage, onset height,
termination height and shaft continuity are unchanged by construction - a prune
that only rejects columns provably outside every ownership ellipse cannot alter
what the accepted columns compute - and the comparison confirms it rather than
taking it on trust. `t185_stack_tight`'s 21,305 changed pixels at SIDE are the
stack's footprint LOD, which the control does not carry.

**The candidate's arithmetic (items 16-17):** two `min`/`max` accumulations per
descriptor at fragment setup, then a box test - `any(lessThan())` plus
`any(greaterThan())` - replacing a `distance()`. It is **cheaper** than what it
replaces and uses only the ownership radii T172 already precomputed into texel 3.
No new payload, no walk, no sqrt in the hot test.

## 6. Task 7 - warp shape, and why 71.5% buys 1.6%

The prune decides per **column**, and adjacent pixels look at different columns,
so rejection is a per-lane decision inside a warp-wide traversal. That is the
shape T179 and T180 established does not pay:

| Campaign | mechanism | work removed | gain |
|---|---|---|---|
| T179 dominance | per-lane | 46.75% of exact SDFs | 1.0159x |
| T184 rain reuse | per-lane | 0.40% of support calls | 0.9973x |
| **T185 exact prune** | **per-lane** | **71.50% of support calls** | **1.0155x** |
| T180 probe removal | **uniform, compile-time** | 18.6% of exact SDFs | **1.1972x** |

**T185 is the strongest version of the per-lane experiment yet run** - the largest
logical fraction removed, exactly correct, at negative arithmetic cost - and it
returns 1.6%. That is now four independent confirmations of the same effect.

A second, smaller reason: the prune sits **after** `sampleWeather` and
`sampleMorphology`, so a rejected column still pays two texture fetches. Only
`directStormRainSupportAt` is skipped.

## 7. Task 10 (items 28-34) - stack

| Pose | Comparison | blocks | ratioMean | spread | verdict |
|---|---|---|---|---|---|
| SIDE | `t172_stack_pre` (control) | 3 | 1.5634 | 15.19% | REJECTED_ratio_spread |
| SIDE | `t185_stack_tight` | 3 | 1.9255 | 16.76% | REJECTED_ratio_spread |
| SIDE | within-block vs control | 3 | 1.2336 | 3.50% | REJECTED_ratio_spread |
| FAR | `t185_stack_tight` | 2 | 1.5260 | 0.14% | accepted |
| FAR | within-block vs control | 3 | 0.8530 | 49.21% | REJECTED_ratio_spread |

**No usable stack figure this session.** The control itself spread 15.19% at SIDE
and 52.82% at FAR. The SIDE within-block ratio of 1.2336 missed acceptance at
3.50% against a 3% rule - close, but rejected, and it is not banked. Session-local
`t185_stack_tight` reads 10.2315 p50 at SIDE.

| Target | Status |
|---|---|
| SIDE <= 10 ms locally | **no** - 10.2315 session-local, and the composition is rejected |
| SIDE <= 8 ms locally | **no** |
| FAR <= 8 ms locally | **yes** - 8.4946, though its control was rejected |

## 8. Decision (items 35-37)

**35. Rain ownership pruning: CLOSED.** The ceiling is 1.0155x at SIDE against a
1.05x gate. The envelope diagnosis was correct - three stacked inflations, 14.47%
rejection where 71.50% was available - and correcting it is worth 1.4%.

**36. Remaining dominant workload: the rain-segment reachability test, unchanged
at roughly 43-57% of `directStormShape` calls.** T182 named it, T183 showed
removing it deletes rain, T184 showed it cannot be cached, T185 shows it cannot
be pruned. What has not been tried is reducing *how often it runs*.

**37. Recommended T186.** Per the brief's own "IF T185 FAILS" branch, choose from
uniform consumers rather than continuing on rain by inertia. Two candidates, in
order:

1. **`rainSegmentMayContribute` samples two points per coarse step.** A uniform
   compile-time reduction to one is the shape that has actually paid twice in
   this series. It is not free semantically - one sample can miss rain entering
   mid-segment - so it needs the rain-coverage metrics as its gate, not just
   timing. But it halves the call count for every lane, which is the property
   the last four campaigns say matters.
2. **If that fails, stop optimising rain.** Five campaigns have now established
   that this consumer is large, exactly characterised, and resistant to every
   selective technique. A fresh profile of the post-T185 renderer should pick the
   next target on measured share, not on the fact that rain was once the biggest
   number.

**A note on the pattern.** The tightened prune is correct, bit-identical, cheaper
than what it replaces, and triples the rejection rate. On the arithmetic it
should have been worth roughly 20% of the rain-support traversal. It returns
1.4%. Any future proposal in this codebase that removes work *per lane* should be
priced against that expectation, not against the fraction of work it removes.

## 9. Harness

- `T185_REJECTED count=0` on anchor drift; the rejections are ratio spread.
- `T145_RAIN_GATE verticalFalseNegatives=0|ellipseFalseNegatives=0` - the
  existing rain-gate invariant still proves the shipped bound sound.
- **The T145 invariant caught a real defect in this campaign's first patch.** The
  arm was written as `#ifdef ... if (A) { #elif ... if (B) { #else ... if (C) {
  #endif`, which leaves the source text with three opening braces and one
  closing; `functionBlock` could not parse `localRainSupportAt` and the build
  failed. Restructured so the preprocessor selects only the predicate and there
  is exactly one `if` block.
- `T183_RAIN_RENDER programs=3|rainReachablePostSpecialization=true`.
- `T182_CONSUMER_TAGS assigned=9|counted=9|untaggedCounterPresent=true`.
- `T175_WORKLOAD_VIEWS declared=36|allEnabled=true` covers views 57-58.
- `T170_WIRING campaigns=29|violations=0`.

## 10. Status

Not merged. Productionization `4b34b12`; T170 `9239077`, T171 `aff6fc7`,
T172 `85ff4ef`, T173 `81476b3`, T174 `3af2015`, T175 `3cfd1f1`, T176 `b047bc3`,
T177 `f868325`, T178 `cb1197b`, T179 cleanup `42e655d`, T179 `655bec1`,
T180 `738b5d7`, T181 `624d7f0`, T182 `1bbda6a`, T183 `6defa18`, T184 `88cf4f4`.
