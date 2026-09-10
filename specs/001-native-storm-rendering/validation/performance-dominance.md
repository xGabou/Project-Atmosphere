# T179 - Dominance pruning is exact, free, and worth nothing

Branch `experiment/cloud-descriptor-k`, cleanup `42e655d`, parent `cb1197b`
(T178). Nothing merged. One campaign 07Sep2026, Ultra, raw scale 0.2500, cloud
target 480x270, framebuffer 1920x1080, 96 steps, 60 samples per cell, three
anchor-bracketed blocks per arm per pose. 50 cells, `T179_REJECTED count=0` on
anchor drift.

## 1. Headline

**Close the line.** The strict ceiling - `t179_dom_aggressive`, which drops the
blend margin entirely and rejects more than any conservative dominance test ever
could - measures **1.0159x at SIDE**. Task 8's threshold to continue was 1.10x.

The candidate itself, `t179_dom_exact`, is **bit-identical** (0 changed pixels,
maxAbs exactly 0.000000, at both poses) and measures **0.9999x SIDE / 0.9955x
FAR**. It is correct, it costs nothing, and it gains nothing.

**Two separate reasons, and the second is the important one:**

1. My slack hypothesis was wrong. Tightening the threshold from the 48-block cap
   to the exact per-pair blend rejects **0.076%** more lobes - 25,109 of
   32,941,121.
2. Even rejecting far more than that barely moves the clock. The aggressive arm
   really does discard contributors (424 changed pixels) and still returns 1.6%.

**46.75% of exact SDFs change the union by exactly nothing, and removing them is
not worth 3%.** That is a statement about how the loop executes, not about how
many lobes are wasted.

## 2. Task 1 (items 3-4) - the exact dominance condition

From `stormSmoothMinimum` itself:

```glsl
float h = saturate(0.5 + 0.5 * (second - first) / radius);
return mix(second, first, h) - radius * h * (1.0 - h);
```

With `first = groupDistance` (accumulated) and `second = lobeDistance`
(incoming), `h` reaches 1 exactly when `lobeDistance >= groupDistance + blend`.
At `h == 1` the expression is `mix(second, first, 1) - blend*1*0 = groupDistance`
- unchanged. `stormBlendFactor` is the same saturate, so the `mix` that updates
`groupStrength` and `groupSoftness` is likewise the identity.

**A lobe at or beyond `groupDistance + blend` cannot alter any component of the
union.** That is the exact contributor definition, and the operation is *not*
order-independent: `groupDistance` depends on which lobes came before, so
"contributor" is a property of a lobe *and* its position in the walk. Floating
point aside, the ordered smooth minimum is genuinely order-sensitive.

Production already tests this condition, against
`groupDistance + STORM_MAX_BLEND_BLOCKS` where that constant is 48, while the
pair's actual blend is `clamp(0.25 * smaller radius, 4, 48)` - typically 8 to 20.
The T179 candidate substitutes the exact value, hoisted from the union below and
reused there, so contributors pay nothing extra.

**Zero false negatives, proved:** `verticalLowerBound <= lobeDistance` always, and
the threshold is the same value the union will use, computed from the same
arguments. The `lobeSoftness` term is untouched so role-mask and height-weight
effects stay guarded as production guards them. Empirically confirmed: 0 changed
pixels at both poses.

## 3. Task 2 (items 5-9) - the dominance histogram

Per exact SDF that reaches the ordered union (the first admitted lobe of each
walk takes the initialising branch and is excluded: 4,268,178 of 32,941,121).

| Bin, SIDE | count | share |
|---|---|---|
| **change exactly zero** | 13,405,848 | **46.75%** |
| change below storage epsilon | 515,762 | 1.80% |
| change tiny (<= 0.01) | 112,814 | 0.39% |
| **meaningful** | 14,638,519 | **51.05%** |

Per group walk at SIDE: 10.0 lobe visits, 2.28 cheap rejects, 7.72 exact SDFs,
**3.14 of them changing nothing at all**. FAR is the same shape at 38.34% zero.

### By consumer (item 9, Task 7)

| | SIDE |
|---|---|
| light share of exact SDFs | 43.7% |
| **light zero-change rate** | **25.0%** |
| **primary zero-change rate** | **52.8%** |

**Dominance pruning is half as effective on light taps, not more.** This answers
why light samples defeat the cheap bound, and it is the same reason twice: a
light tap marches from *inside* the cloud along `LightDir`. The vertical lower
bound is a lower bound built from the lobe's vertical extent, so for a sample
sitting inside that extent it returns approximately zero and cannot exceed
`groupDistance` under any margin. And a sample inside the body genuinely has
several lobes near it, so its SDFs really are meaningful three times out of four.

Light taps are not badly served by the threshold. They are badly served by the
bound, and they are also the samples where there is least to win.

## 4. Tasks 3, 5 and 8 (items 10-22) - oracle, candidate, ceiling

| Pose | Arm | blocks | ratioMean | spread | verdict |
|---|---|---|---|---|---|
| SIDE | `t179_dom_exact` | 3 | **0.9999** | 3.09% | REJECTED_ratio_spread |
| SIDE | **`t179_dom_aggressive`** | 3 | **1.0159** | 1.34% | **accepted** |
| FAR | `t179_dom_exact` | 2 | **0.9955** | 0.36% | **accepted** |
| FAR | `t179_dom_aggressive` | 3 | 1.0241 | 4.56% | REJECTED_ratio_spread |
| SIDE | `t172_stack_pre` | 3 | 1.8012 | 10.90% | REJECTED_ratio_spread |
| SIDE | `t179_stack_dom` | 3 | 1.8497 | 15.14% | REJECTED_ratio_spread |
| FAR | `t172_stack_pre` | 3 | 2.5469 | 6.09% | REJECTED_ratio_spread |
| FAR | `t179_stack_dom` | 3 | 2.2252 | 54.48% | REJECTED_ratio_spread |

Image equality:

| Arm | pose | passed | maxAbs | changed px |
|---|---|---|---|---|
| **`t179_dom_exact`** | SIDE | **true** | **0.000000** | **0** |
| **`t179_dom_exact`** | FAR | **true** | **0.000000** | **0** |
| `t179_dom_aggressive` | SIDE | false | 9.86e-01 | 424 |
| `t179_dom_aggressive` | FAR | false | 9.76e-01 | 135 |

The aggressive arm changing 424 pixels matters: it proves the ceiling is real
work being skipped, not an accidental no-op like T178's support bound.

**Real bound reject rate (item 17): 0.076%.** `domWouldRejectWithExactBlend` is
25,109 against 32,941,121 exact SDFs - 0.0059 extra rejects per group walk. The
lobes surviving T121 are not sitting in the 8-to-48 block band I predicted; they
are much closer than that, so the threshold was never the binding constraint.
**The bound is.**

**Hot-loop arithmetic (items 19-20):** one `stormLobeBlendRadius` - a min, two
role comparisons, a multiply and a clamp - hoisted above the test and reused by
the union, so contributors pay zero net. No division, no `length()`, no ranking,
no new payload. Exact-SDF reduction: 0.076%.

### Why 46.75% waste is worth 1.6%

T170 measured `desc_nosdf` - every exact SDF replaced by the bound at compile
time, for every lane - at **1.53x**. T179 measures removing roughly half of them,
per lane and divergently, at **1.016x**.

Uniform removal of 100% returns 1.53x; divergent removal of ~47% returns 1.6%.
Those two numbers are not reconcilable by arithmetic on work removed, and the
most consistent explanation is execution shape: the ten-lobe loop is warp-wide,
and a lobe skipped by one lane is still evaluated by the warp whenever any other
lane needs it. With ten lobes and thirty-two-plus lanes, almost every lobe is
needed by somebody.

I am labelling that an **inference from two measurements**, not an instrumented
finding - I did not measure warp occupancy or divergence directly. But it is the
explanation that fits, and it retroactively fits the whole series: T167's
nearest-K slower, T174's exact group entry at 1.136x and insufficient, T178's
support bound at 0.941x. **Every per-lane pruning scheme this project has tried
has underdelivered against its own work-removed arithmetic. Every uniform,
compile-time reduction has delivered.**

## 5. Task 6 (items 23-25) - ordering: NOT MEASURED

I did not build the best-first ordering oracle. Stating that plainly.

What the campaign does bound: a better order would lower `groupDistance` earlier
and so make more later lobes provably dominated - but every one of those extra
rejections is still a *divergent per-lane skip in the same loop*, which is the
mechanism the aggressive arm prices at 1.6%. Ordering changes how many lobes are
rejected; it does not change what a rejection is worth.

Reordering would also change output: the smooth union is order-sensitive by
construction (section 2), so best-first is not image-neutral and could never have
been a candidate without a quality campaign of its own.

## 6. Task 9 (items 29-35) - stack

**Every stack figure this session is rejected on ratio spread**, including the
control. SIDE `t172_stack_pre` spreads 10.90% and `t179_stack_dom` 15.14%; the
within-block comparison spreads 23.89%. FAR is worse - `t179_stack_dom` spreads
54.48% and its within-block ratio 48.15%.

| Target | Status |
|---|---|
| SIDE <= 10 ms locally | **no** - session-local 11.23, and rejected |
| SIDE <= 8 ms locally | **no** |
| FAR <= 8 ms | inconclusive this session; T178's accepted ~5.05 ms stands |

There is nothing to compose anyway: the candidate is 0.9999x.

## 7. Decision (items 36-38)

**36. Dominance pruning: CLOSED.** Strict ceiling 1.0159x against a 1.10x gate.
The candidate is exact and free and returns nothing; the ceiling that over-rejects
returns 1.6%.

**37. Remaining dominant workload.** Unchanged from T178 in composition - the
exact lobe SDF is ~35% of SIDE frame time - but T179 changes what can be done
about it. It cannot be pruned per-lane. Anything that removes it must remove it
for the whole warp.

**38. Recommended T180.** The brief's own fork applies, and the measurement
selects branch **B**:

- Representation redesign (branch A) is **not** indicated. The flat ten-lobe walk
  does encode enough to identify dominated lobes - 46.75% of them are provably
  dominated and the exact test finds them correctly. The problem is that finding
  them is not worth anything, which no representation change fixes.
- **Fresh-profile the segment/probe/quadrature workload** (branch B). T175 put it
  at ~48% of descriptor walks and no campaign has attributed it since. It is now
  the only large block that has never been profiled at the level the light march
  and the lobe walk have.
- When evaluating whatever that profile turns up, weight **uniform** reductions
  over selective ones. That is the single most transferable thing this campaign
  produced: five separate per-lane pruning schemes have now underdelivered, and
  the compile-time reductions - T168's footprint step LOD, T172's precompute -
  are the two that shipped.

## 8. Cleanup (items 1-2)

Committed separately as `42e655d`, before any T179 measurement.

`TEXELS_PER_DESCRIPTOR` is back to **5**. Removed: texel 5, its writer, the
`PA_ARM_LOBE_SUPPORT` shader path, its three generated variants, their program
entries, and the T178 arm-table rows selecting them.

**Proof no production read remains:** no `stormDescriptorTexel(descriptorIndex, 5)`
and no `offset + 20..23` write anywhere in `src/main`; the only surviving
references to `TEXELS_PER_DESCRIPTOR` are its own declaration, `FLOATS_PER_DESCRIPTOR`,
and `StormLobeSpatialIndex.DESCRIPTOR_WIDTH`. The T172 precompute is untouched and
still shipped - FINAL carries `PA_ARM_DESC_PRECOMPUTE` and still reads
`lifecycleRole.yz`. Invariant confirms `T172_PRECOMPUTE texels=5|channelsPinned=true`.

## 9. Harness notes

- `T179_REJECTED count=0` on anchor drift; all rejections are ratio spread.
- **The T123 instrumentation invariant caught this campaign's counter**, exactly
  as it caught T174's and T175's: `paDomWouldRejectWithExactBlend++` sat below a
  multi-line guard condition, outside the three-line window. Restructured into
  two guarded blocks so the increment is one line from its guard and production
  pays only a `bool = false`.
- `T175_WORKLOAD_VIEWS declared=23|allEnabled=true` covers views 44 and 45, so
  the histogram could not have read a confident zero.
- `T170_WIRING campaigns=24|violations=0`, negative proof detects all five
  mutations.
- The background-task manager stopped three watcher processes under host memory
  pressure; the detached client was unaffected each time and the campaign
  completed all 50 cells. Not an OS watchdog - the same manager identified in
  T175.

## 10. Status

Not merged. Productionization `4b34b12`; T170 `9239077`, T171 `aff6fc7`,
T172 `85ff4ef`, T173 `81476b3`, T174 `3af2015`, T175 `3cfd1f1`, T176 `b047bc3`,
T177 `f868325`, T178 `cb1197b`, cleanup `42e655d`.
