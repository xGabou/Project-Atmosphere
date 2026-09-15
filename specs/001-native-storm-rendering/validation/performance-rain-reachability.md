# T183 - The specialization would have deleted rain, so it was not built

Branch `experiment/cloud-descriptor-k`, parent `1bbda6a` (T182). Nothing merged.
**No GPU campaign was run.** The change this campaign was to implement is a
functional regression, and that is established from the source and from T182's
own counters rather than from a timing run.

## 1. Headline

**Task 1 was not implemented, and should not be.**

The brief's root cause states that because FINAL bakes `PA_PRECIPITATION_ABSENT`,
the rain-segment reachability test is dead work. **It is not.**
`PA_PRECIPITATION_ABSENT` removes precipitation from `cloudDensity`'s internal
term only. **Rain still renders in FINAL**, through
`rainShaftDensityOverSegment`, which the march calls directly and gates on
`localRainSegment` - the flag `rainSegmentMayContribute` exists to compute.

Compiling that test out under `PA_PRECIPITATION_ABSENT` would pin
`localRainSegment` to false, which sets `rainDensity = 0.0` unconditionally and
**removes rain from the shipped program**. That is precisely what the brief's own
Task 3 forbids: "no precipitation functionality is removed."

**This also corrects T182.** I wrote there that `t182_norainseg`'s 105 changed
pixels were a perturbed step pattern and that "the rain it gates cannot render in
this build". Both were wrong. Those pixels are rain being deleted, and the
1.2139x is the cost of a shipped feature, not a ceiling on dead work. The T182
document now carries three inline corrections.

## 2. The proof (items 3-5)

Three independent lines, none of them inference.

**A. The source says so, in the comment that introduced the define.** T163, at
the `PA_PRECIPITATION_ABSENT` guard inside `cloudDensity`:

> "Rain itself is unaffected: it renders through `rainShaftDensityOverSegment`,
> which the march calls directly."

**B. The generated FINAL still contains the rain render call.** Inspecting
`build/generated/leanFinalResources/.../cloud_atmosphere_volume_final.fsh` - the
program that has `PA_PRECIPITATION_ABSENT` applied:

```glsl
float rainDensity = localRainSegment
    ? rainShaftDensityOverSegment(p, segmentEnd, 0.0) * DensityMul
    : 0.0;
```

Present, and gated only by `localRainSegment`. Not inside any precipitation
guard.

**C. T182's own counters prove the path executed.** Consumer tag 7 is set only
inside `rainShaftDensityAt`. Under `PA_PRECIPITATION_ABSENT` the `cloudDensity`
call to that function is compiled out, so the **only** remaining route to it is
`rainShaftDensityOverSegment` from the march. T182 measured, in the
precipitation-absent build:

```
shapeRainShaft=43958   (SIDE)
shapeRainShaft=43133   (FAR)
```

Non-zero. Rain rendering ran, tens of thousands of times per frame.

`localRainSegment` has exactly two consumers, and the second is the one that
matters:

| Line | Use | Effect of forcing the flag false |
|---|---|---|
| 7260 | gates the weather-skip | more skips - a performance effect |
| **7370** | **gates `rainShaftDensityOverSegment`** | **rain density becomes 0 everywhere** |

## 3. What was implemented instead

### Task 0 - accounting tolerance (items 6-8)

`shapeAccountingClosed` compared the tagged sum against the total with a
half-a-call tolerance, which is stricter than the capture permits: each debug
view is a separately rendered frame, so the totals are sampled from different
frames. T182's residual was 3,301 calls of 5,739,705 - **0.058%** - and the flag
printed `false` on a correct accounting.

Replaced with a documented fractional tolerance of **0.5%**, plus explicit
reporting of both terms:

- `shapeAccountingResidual` - the absolute difference
- `shapeAccountingResidualFraction` - the same as a fraction of the total
- `shapeAccountingClosed` - now requires **`shapeUntagged == 0` AND** residual
  within 0.5%

0.5% is roughly ten times the observed cross-frame variance and roughly a tenth
of the smallest consumer ever measured (rain shaft, 0.77% of SIDE shape calls),
so it still fails if a real consumer goes missing. **`shapeUntagged == 0` remains
the strict structural requirement** - the fraction only relaxes the arithmetic
cross-check, and the static consumer-tag invariant is untouched.

### Tasks 2 and 3, inverted - an invariant that blocks the change

The brief asked for an invariant proving the rain path is compiled out of FINAL.
Since compiling it out is the regression, the invariant added asserts the
opposite: `validateRainRenderSurvivesPrecipitationSpecialization` reads the
**generated** FINAL and the T140 oracle variants and fails the build if either
`rainShaftDensityOverSegment(p, segmentEnd` or `rainSegmentMayContribute(`
disappears from them.

```
T183_RAIN_RENDER programs=3|rainReachablePostSpecialization=true
```

This is deliberately the mirror image of what was requested, and it is checked
against generated output rather than source `#ifdef` placement, which is what
Task 2 asked for in the direction that would have been correct had the premise
held.

## 4. Why no campaign ran (items 21-33)

Measuring `t183_rainseg_gated` would have measured a rain-less renderer against a
rain-rendering control. The speedup would have reproduced T182's 1.2139x and the
image comparison would have shown rain missing - a result already available from
T182's `norainseg` arm, which is the same program.

Spending a GPU campaign to re-derive a known regression is not a use of the
harness. **Items 9-15 (correctness), 16-20 (workload before/after) and 21-33
(performance and stack) are therefore not reported**: they describe a build that
should not exist.

The stack numbers stand where T182 left them: FAR control 4.967 ms p50 with the
quality-approved stack, and cap 4 rejected at 0.9900x.

## 5. What is actually available here (item 36)

The 36%/57% is real work, and some of it is genuinely reducible - but the target
is the *cost of the test*, not its existence.

**1. The query is over-powered for its question.** `rainSegmentMayContribute`
needs a boolean - "could rain attach in this segment". It obtains it by calling
`localRainSupportAt` twice, and each of those runs `directStormLocalBaseAt`
(walks every descriptor) **and** `directStormFinalDensity` (a complete
candidate/group union). It uses `support > 0.01` and `attachY` from that. This is
the same "REDUCIBLE to a threshold query" classification T180 gave the probe
scan - with the same caveat that T180 then measured: a cheaper query that feeds a
control decision can cost more than it saves.

**2. Its granularity is per coarse step.** The attach height is a property of the
*column*, not the segment. Two samples per step re-derive it continuously along a
ray that moves mostly vertically through the same columns. A column-level bound
computed once per ray - or a coarser cache keyed on the weather tile - would
answer the same question far less often. This is the strongest structural
candidate.

**3. The T145 locality check already prunes by height** and is the model for
this: it rejects a sample above `max(paWeatherBaseY, paRainAttachTop)` using only
a weather fetch, "without entering the descriptor traversal to find out." Extending
that idea, rather than deleting the test, is the line worth taking.

**Recommended T184: reduce the frequency and the cost of the rain reachability
query while preserving rain.** Concretely - measure a column-bound arm that
computes the attach envelope once per ray and reuses it, against the current
per-step two-sample form, with rain rendering intact and image equality as the
gate. The uniform-work lesson from T179/T180 applies: prefer a change that
removes the call for every lane over one that skips it per lane.

## 6. Validation (items 37-43)

- `./gradlew.bat check build` - see section 7.
- `T183_RAIN_RENDER programs=3|rainReachablePostSpecialization=true` - new.
- `T182_CONSUMER_TAGS assigned=9|counted=9|untaggedCounterPresent=true` - the
  static consumer-tag invariant is unweakened.
- `T175_WORKLOAD_VIEWS declared=32|allEnabled=true`.
- `T170_WIRING campaigns=27|violations=0`.

No campaign registry entry was added, because no campaign was run.

## 7. Status

Not merged. Productionization `4b34b12`; T170 `9239077`, T171 `aff6fc7`,
T172 `85ff4ef`, T173 `81476b3`, T174 `3af2015`, T175 `3cfd1f1`, T176 `b047bc3`,
T177 `f868325`, T178 `cb1197b`, T179 cleanup `42e655d`, T179 `655bec1`,
T180 `738b5d7`, T181 `624d7f0`, T182 `1bbda6a`.
