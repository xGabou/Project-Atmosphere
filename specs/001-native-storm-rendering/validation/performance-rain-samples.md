# T186 - One sample is worth 6.7%, and that closes the micro-optimization series

Branch `experiment/cloud-descriptor-k`, parent `4c2941e` (T185). Nothing merged.
One campaign 09Sep2026, Ultra, raw scale 0.2500, cloud target 480x270,
framebuffer 1920x1080, 96 steps, 60 samples per cell, three anchor-bracketed
blocks per arm per pose. 62 cells, `T186_REJECTED count=0` on anchor drift.

## 1. Headline

**The best one-sample arm is `t186_one_mid` at 1.0668x SIDE**, accepted at 0.88%
ratio spread, with cloud SSIM **0.999983** and 120 changed pixels. It is real,
trivially implementable, and the largest quality-safe single-arm gain measured
since T180.

**It is not enough, and per the brief's own rule the series stops here.** The
threshold for a strong candidate was 1.10x; the strategic requirement is
1.2-1.3x. This is 1.067x, its stack composition was **rejected**, and the
rain-specific quality gate the brief demanded is **not measured** (section 5).

**A premise correction that changes what the arm actually does:** the two-sample
rule costs **0.59 support evaluations per segment test, not 2**, because the T145
height prune already rejects **70.4%** of sample opportunities before any
descriptor traversal.

## 2. Task 1 (items 1-3) - what the two-sample rule actually is

```glsl
for (int sampleIndex = 0; sampleIndex < 2; sampleIndex++) {
    float along = sampleIndex == 0 ? FIRST_SAMPLE : SECOND_SAMPLE;  // .2113 / .7887
    ...
    if (paT145RainLocality()) { ... if (p.y >= max(...)) continue; }
    float support = localRainSupportAt(p.xz, ...);
    if (support > 0.01 && p.y < attachY && p.y > attachY - 184.0) return true;
}
```

**Semantics: existence, not max and not integrated.** It is an **OR with an early
`return true`**, plus a per-sample height `continue`. The boolean becomes
`localRainSegment`, which gates both the weather-skip and - per T183 - the actual
`rainShaftDensityOverSegment` rain rendering call.

Two consequences that shaped the arms:

1. **Dropping a sample can only turn `true` into `false`.** It can miss rain,
   never invent it. So the failure mode is precisely "missed rain".
2. **The second sample is already conditional**, so "one sample halves the work"
   had to be measured rather than assumed.

**Selected arms:** midpoint (0.5) as the least biased single probe - it minimises
the greatest distance to any point in the segment - plus both existing Gauss
nodes, to bound the bias of choosing either end.

## 3. Task 3 (items 4-6) - the measured split

| SIDE | value |
|---|---|
| `rainSegmentMayContribute` calls | 3,892,479 (**30.03/px**) |
| sample opportunities (2 per call) | 7,784,958 |
| **rejected by the T145 height prune** | **5,478,395 (70.4%)** |
| support evaluations at sample 0 | 1,144,942 |
| support evaluations at sample 1 | 1,151,877 |
| **support evaluations per segment call** | **0.5901** |
| second sample runs at all | 29.59% of calls |
| segment tests returning true | 11,028 (**0.28%**) |
| **second sample is the one that finds rain** | **11.50%** |

FAR is the same shape: 0.6104 support calls per segment test, 30.70% second-sample
run rate, 8.64% second-sample decisions.

**Why "approximately half" is both wrong and right.** Per *segment test* the cost
is 0.59 evaluations, not 2 - the height prune dominates. But of the support
evaluations that do happen, sample 1 accounts for 1,151,877 of 2,296,819, so
removing it removes **49.2% of rain-support evaluations**. The structural halving
holds for the traversals; it never held for the call.

The early return is nearly irrelevant here: the test returns true on only 0.28%
of calls, which is why sample 0 and sample 1 run almost equally often.

## 4. Tasks 5 and 2 (items 16-19) - performance

| Pose | Arm | blocks | ratioMean | spread | verdict |
|---|---|---|---|---|---|
| SIDE | **`t186_one_mid`** | 3 | **1.0668** | 0.88% | **accepted** |
| SIDE | `t186_one_b` | 3 | 1.0598 | 1.16% | accepted |
| SIDE | `t186_one_a` | 3 | 1.0557 | 1.33% | accepted |
| FAR | `t186_one_a` | 3 | 1.0637 | 1.73% | accepted |
| FAR | `t186_one_mid` | 3 | 1.0586 | 2.73% | accepted |
| FAR | `t186_one_b` | 2 | 1.0521 | 0.01% | accepted |

All six arms accepted, all three positions within 1.1% of each other. Removing
49.2% of rain-support traversals returns **6.7%**.

That ratio is worth stating plainly: rain segment is 44.12% of
`directStormShape` calls, halving its traversals removes roughly 22% of them, and
the frame moves 6.7%. It is a better return than T179's, T184's or T185's -
because a compile-time trip-count change is partly uniform - but still far short
of proportional.

## 5. Task 4 (items 7-15) - quality, and what was not measured

| Arm | pose | changed px | meanAbs | cloud SSIM | edge SSIM | IoU | thin | hole |
|---|---|---|---|---|---|---|---|---|
| **`one_mid`** | SIDE | **120** | 9.310e-06 | **0.999983** | 0.999302 | 0.999015 | 0.9343 | 1.0000 |
| `one_b` | SIDE | **55** | 3.470e-06 | **0.999995** | 0.999934 | 0.999534 | 0.9689 | 1.0000 |
| `one_a` | SIDE | 197 | 1.850e-05 | 0.999952 | 0.997975 | 0.998445 | 0.8962 | 1.0000 |
| `one_mid` | FAR | 29 | 2.655e-06 | 0.999982 | 0.999943 | 0.999200 | 0.9483 | 1.0000 |

Cloud quality is essentially untouched - SSIM to five nines, hole retention
exactly 1.0. The one metric that moves is **thin retention**, 0.896 to 0.969
depending on position, which is consistent with losing thin rain wisps rather
than cloud body.

**Not measured, and I am not going to imply otherwise.** Task 4 asked for missed
rain pixels, falsely added rain pixels, rain-region overlap, shaft continuity,
onset height, termination height, gaps and extensions. **None of those exist in
this harness.** What can be said instead:

- The *direction* is proven by construction: the rule is an OR, so a dropped
  sample can only lose rain, never add it. "Falsely added rain" is structurally
  zero.
- The *magnitude* is bounded by the counters: the second sample is the deciding
  one for **11.50%** of rain-positive segments, and rain-positive segments are
  0.28% of all segment tests.
- Building the rain-specific metrics on **this** fixture would be low value: with
  11,028 rain-positive segments in a frame, the rain signal is too sparse for
  continuity or onset-height statistics to mean much. A rain-heavy fixture would
  be needed, and that is a fixture problem, not a metrics problem.

So the quality claim I will defend is narrow: **cloud quality is unchanged; rain
loses at most a small fraction of thin wisps, and the amount is not quantified in
rain-specific terms.**

## 6. Task 6 (items 20-26) - stack

| Pose | Comparison | blocks | ratioMean | spread | verdict |
|---|---|---|---|---|---|
| SIDE | `t172_stack_pre` (control) | 3 | 1.8101 | 15.98% | REJECTED_ratio_spread |
| SIDE | `t186_stack_one_mid` | 3 | 1.7821 | 2.33% | accepted |
| SIDE | within-block vs control | 3 | **0.9833** | 13.27% | **REJECTED_ratio_spread** |
| FAR | `t186_stack_one_mid` | 3 | 2.7709 | 3.03% | REJECTED_ratio_spread |
| FAR | within-block vs control | 3 | 1.5933 | 47.07% | REJECTED_ratio_spread |

**The composition is unverified.** The control spread 15.98% at SIDE and only one
block survived at FAR. The SIDE within-block mean of 0.9833 is *below one*, but at
13.27% spread it is not evidence of anything either way. Session-local
`t186_stack_one_mid` reads 10.5202 p50 at SIDE.

| Target | Status |
|---|---|
| SIDE <= 10 ms locally | **no** - 10.5202, and the composition is rejected |
| SIDE <= 8 ms locally | **no** |
| FAR <= 8 ms locally | **yes** - 4.4814, though rejected on spread |

## 7. Decision (items 27-30)

**27. Productionize one-sample rain reachability? Not on this evidence.** It
meets the "1.03-1.10x, trivial implementation, quality effectively unchanged"
bar on two of three counts, but its stack composition is rejected and the
rain-specific gate is unmeasured. **Recommendation: hold it as a banked minor
candidate**, to be re-tested for composition on a session with a stable control,
and gated on rain metrics built against a rain-heavy fixture. Do not ship it off
this run.

**28. Close rain optimization? Yes.** Five campaigns - T182 attribution, T183
specialization, T184 reuse, T185 prune, T186 sampling - have now characterised
this consumer completely. The best available result is 6.7% with an unverified
composition. Per the brief's stated rule, **the micro-optimization series is
closed.**

**29. Fresh largest workload, after everything accepted** (SIDE, this run,
`shapeAccountingClosed=true` with residual 0.0001):

| Consumer | calls/px | share of `directStormShape` |
|---|---|---|
| **rain segment reachability** | **15.39** | **44.12%** |
| light tap | 9.23 | 26.45% |
| empty-span probe | 5.47 | 15.69% |
| primary body | 3.31 | 9.48% |
| march refinement | 1.17 | 3.34% |
| rain shaft density | 0.32 | 0.93% |
| **untagged** | **0** | **0%** |

34.88 shape calls, 28.40 group walks, 284.0 lobe visits and 220.0 exact SDFs per
pixel at SIDE.

**30. Recommended architecture-level task: bake the rain-attach field once per
frame.**

This is the brief's question 5 - move work out of the march to per-frame or
per-tile - and it is the one architecture option with a **proof already in hand**.
T184 established that `localRainSupportAt` is *exactly* column-invariant within a
frame: every input is `sampleWeather(worldXZ)`, `sampleMorphology(worldXZ)`,
`directStormRainSupportAt(worldXZ, ...)` or a frame uniform, with no `Y`, no
segment, no camera and no jitter.

T184 then showed per-ray caching fails because a ray almost never revisits a
column - 0.40% exact reuse. **A precomputed field does not need revisiting.** It
is evaluated once per column at tile resolution for the whole frame, and the
march's 30.03 segment tests per pixel become texture fetches instead of
descriptor traversals.

That is uniform work removal - the only shape that has paid in this codebase -
applied to the largest remaining consumer. The open questions are the field's
spatial resolution against the 184-block attach band, whether the tile
quantisation is conservative in the safe direction, and the per-frame build cost
against 44% of the descriptor walk.

If that fails, the next candidates in the brief's list, in evidence order, are
question 3 (several consumers sharing one field evaluation - light and probe
together are another 42%) and question 1 (evaluating storm shape at lower spatial
frequency).

## 8. Harness

- `T186_REJECTED count=0` on anchor drift; rejections are ratio spread.
- **`shapeAccountingClosed=true`**, residual 515 calls of 4,521,136 (0.0001) -
  T183's fractional tolerance works as intended, and `shapeUntagged` is still 0.
- `T145_RAIN_GATE verticalFalseNegatives=0|ellipseFalseNegatives=0`.
- `T183_RAIN_RENDER programs=3|rainReachablePostSpecialization=true` - rain is
  preserved; no arm suppresses it.
- `T182_CONSUMER_TAGS assigned=9|counted=9|untaggedCounterPresent=true`.
- `T175_WORKLOAD_VIEWS declared=38|allEnabled=true` covers views 59-60.
- `T170_WIRING campaigns=30|violations=0`.
- The arms change only the loop trip count and the sample position, both
  expressions rather than braces, so the source stays balanced for the parsers
  that read this function. T185 learned that the hard way.

## 9. Status

Not merged. Productionization `4b34b12`; T170 `9239077`, T171 `aff6fc7`,
T172 `85ff4ef`, T173 `81476b3`, T174 `3af2015`, T175 `3cfd1f1`, T176 `b047bc3`,
T177 `f868325`, T178 `cb1197b`, T179 cleanup `42e655d`, T179 `655bec1`,
T180 `738b5d7`, T181 `624d7f0`, T182 `1bbda6a`, T183 `6defa18`, T184 `88cf4f4`,
T185 `4c2941e`.
