## T133 CLOSED - SC-020 and FR-030 satisfied - 2026-08-27

**T133 is ACCEPTED. T098 is unblocked.** The T121 neutrality failure recorded in the section below
was isolated to a specific, provable cause, corrected by the narrowest change that fixes it, and
re-validated on five fresh fixtures. **The performance debt is NOT closed - see the standing debt at
the end of this section.**

### The first hypothesis was wrong, and was reverted

The recorded hypothesis was that T121's guard had zero numerical margin against the smooth-union
boundary, because `stormLobeBlendRadius` clamps to `STORM_MAX_BLEND_BLOCKS = 48` at every T134-scale
radius, so `groupDistance + STORM_MAX_BLEND_BLOCKS` is the exact blend rather than an upper bound.

That hypothesis is **FALSIFIED**. `validateT121GuardAdmitsNoUnionContribution()` simulates the guard
and the union together in float32 across 194,724 probes of the rejection surface - union distances
from adjacent to far beyond the SC-018 ranges, blend radii in both the clamped and unclamped regimes,
and the worst measured SDF shortfall - and finds **zero** cases where a rejected lobe still produced a
non-zero smooth-minimum contribution, with or without an added margin. The reason is that one float32
ULP at those union distances (2.4e-4 blocks at 2,448) is far *coarser* than the 3.815e-6-block SDF
shortfall, so the shortfall vanishes in the representation and `h` lands on exactly `1.0`.

A margin had been applied to the blend term on the strength of that hypothesis. It was **reverted**
once the proof came back negative, and the test now records the union path's safety as the invariant
it turned out to be.

### The actual cause: a discrete role-mask flip at the softness term

The guard fires on `verticalLowerBound > max(lobeSoftness, groupDistance + STORM_MAX_BLEND_BLOCKS)`.
When **softness** is the binding term the magnitudes are 11-100 blocks, where one float32 ULP is about
1e-6 - *comparable to* the SDF shortfall rather than far coarser than it. In that window a rejected
lobe can still satisfy `lobeDistance <= lobeSoftness`, which is the test that sets a
`groupActiveRoleMask` bit. The mask is **discrete**, so the divergence is categorical rather than an
epsilon, which is exactly the rare single-pixel, large-delta signature observed.

`validateT121SoftnessBoundary()` finds **15,546 role-mask flips in 71,982 probes** of that boundary.
Witness:

```
lobeSoftness        = 11.363636      (STORM_MIN_EDGE_BLOCKS)
verticalLowerBound  = 11.363637      guard fires, lobe rejected
lobeDistance        = 11.363633      <= lobeSoftness, so OFF sets the role-mask bit
ulp(lobeSoftness)   = 9.536743e-7
```

The height-weighting path is clean: `smoothstep` clamps to exactly `1.0`, so the skipped
`heightContribution` is exactly zero in all 71,982 probes.

This also explains *where* the failure appeared. Softness binds over `groupDistance + 48` only when
`groupDistance < lobeSoftness - 48`, i.e. very close to the lobe surface - and the one observed
failure was in the `BELOW` view, the pose that puts the camera nearest the storm base.

### The correction

One constant and one term, in `cloud_atmosphere_volume.fsh`:

```glsl
const float STORM_T121_SOFTNESS_MARGIN_BLOCKS = 0.0009765625;   // 2^-10

if (started && !paT121Off() && verticalLowerBound > max(
        lobeSoftness + STORM_T121_SOFTNESS_MARGIN_BLOCKS,
        groupDistance + STORM_MAX_BLEND_BLOCKS)) {
```

**Derivation, not a chosen number.** The margin must exceed the measured float32 SDF shortfall plus
the ULP of the softness comparison itself: `3.815e-6` (worst over 5,473,368 probes in
`validateT121Float32BoundaryMargin()`) plus up to `7.6e-6` at the top of the softness range. `2^-10 =
9.766e-4` covers that by roughly 80x while remaining `8.6e-5` relative to the 11.36-block minimum
softness it guards. The test asserts the margin closes every one of the 15,546 flips, and asserts the
unmargined guard is genuinely unsafe - so the margin cannot be kept if the defect it fixes ever
disappears.

**The blend term is deliberately left unmargined**, and a regression check pins that, because it was
measured safe and an unnecessary margin would only cost work.

**Authorization boundary.** The change only raises the rejection threshold, so it can only reject
*fewer* lobes; OFF remains the semantic reference. `STORM_MAX_BLEND_BLOCKS` is still `48`. No density,
morphology, geometry, blend, softness, noise, erosion, lighting or smooth-union equation was touched.

### Post-fix campaign

Five fresh auto-bootstrapped fixtures (`d1f56fe4`, `0a41b742`, `8e4a3d08`, `e08db922`, `850134cd`),
four primary views, both passes, five samples per arm, unchanged one-binary16-storage-ULP comparator:

| Comparison | Medians | Passed | Failed |
|---|---:|---:|---:|
| A/A local control | 40 | 40 | **0** |
| T121 OFF/ON | 40 | 40 | **0** |
| T122 OFF/ON | 40 | 40 | **0** |

`armsDistinct=true` on all 40 T121 comparisons from the draw snapshot; production state restored
after every arm.

### T121 owned work is preserved

BELOW-view descriptor evaluations, OFF versus ON:

| Fixture | OFF | ON | Reduction |
|---|---:|---:|---:|
| `d1f56fe4` | 233,560,570 | 105,285,356 | 54.9% |
| `0a41b742` | 230,366,710 | 104,384,182 | 54.7% |
| `8e4a3d08` | 201,029,666 | 86,430,412 | 57.0% |
| pre-fix `9cb11d26` | 242,000,684 | 104,281,764 | 56.9% |

The margin removes only a 9.766e-4-block-thick shell, so the reduction is unchanged within
fixture-to-fixture variation. T121 still owns and removes roughly 55% of descriptor evaluations.

### SC-020 / FR-030 verdict

| Optimization | Bounds owned work | Preserves comparison image | SC-020 |
|---|:--:|:--:|---|
| T119 group topology | yes | yes - 40 medians, byte-identical | **satisfied** |
| T121 conservative rejection | yes - ~55% of evaluations | yes - 40/40 medians, 0 changed pixels | **satisfied** |
| T122 descriptor-fetch reuse | yes - 37.4% of fetches | yes - 40/40 medians, 0 changed pixels | **satisfied** |
| T123 bounded evaluation cost | yes | not reachable in production frames | **satisfied** |

**SC-020 and FR-030 are satisfied. T133 is closed. T098 is unblocked.**

### Standing debt, explicitly NOT closed by this

- **SC-006 / T070 remains FAILED and open.** Severe-scale storm raymarch measures 63-261 ms at
  `641x360` with `governorScale=0.50000` / `resolutionScale=0.75000`, against a 16.7 ms Ultra target.
  Closing T133 does **not** make the renderer performance-ready. Descriptor texture fetches remain
  the dominant measured cost.
- **Future optimization must preserve the now-proven T119, T121 and T122 neutrality contracts**, all
  of which have live OFF/ON evidence and automated guards.
- **`STORM_MAX_BLEND_BLOCKS` remains `48`**, untouched, and remains a T098 visual-seam risk
  hypothesis.
- **The `BELOW`/`A1` raw within-arm dispersion remains unattributed** (recurred at `chg=15790 dev=2`
  and `chg=3173 dev=4`). It is absorbed by the robust median and is unrelated to the T121 defect
  fixed here, which showed `dev=0` on both arms.

## SUPERSEDED - T133 SC-020: T122 PASSES, T121 FAILS - 2026-08-27

**T133 remains OPEN.** T122 is now proven image-neutral. T121 is **not**: it produces a real,
deterministic, configuration-dependent image difference. SC-020 and FR-030 stay unsatisfied, so T098
stays blocked.

### Diagnostic optimization-mode framework

`cloud_atmosphere_volume.fsh` gained `uniform int PaDiagnosticOptimizationMode` with
`PA_OPT_NORMAL_PRODUCTION = 0`, `PA_OPT_T121_OFF = 1`, `PA_OPT_T122_OFF = 2`, decoded by
`paT121Off()` / `paT122Off()`. Zero is the only value the renderer uploads outside a deliberate
capture.

**Production default is provably unchanged.** The normalised diff is 61 lines, and every functional
change is either a new declaration or a `paT12xOff() ? <OFF> : <original expression>` ternary whose
false branch is textually identical to the original. At mode `0` both predicates are false, so every
expression reduces to the pre-T133 code. `StormVolumetricGeometrySandbox`
`validateT133ProductionDefaultUnchanged()` pins the default at five layers - shader constant, JSON
uniform default `0`, config field initialiser, `resetDefaults()`, and the renderer upload - and the
new `production_optimization_mode_not_normal` adjacent control fails any capture whose baseline draw
was not in `NORMAL_PRODUCTION`.

**Arm identity is observed, not asserted.** `RenderInputs` carries the mode from the draw snapshot;
`observedOptimization()` folds a group's five samples and reports `mixed`/`unknown` rather than a
usable arm. A/A controls report `armsDistinct=false` with both arms `normal_production`, which is
the check that the field reflects uploaded state rather than the group label.

**Per-arm owned work.** The view-level workload capture runs in `NORMAL_PRODUCTION` before sampling
and therefore could never show what an OFF arm cost. A `GROUP_WORKLOAD` state now captures counters
per arm while that arm's mode is still applied.

### Campaign

Five completed fixtures, four primary views, five samples per arm, unchanged one-binary16-storage-ULP
comparator.

| Comparison | Medians | Passed | Failed |
|---|---:|---:|---:|
| A/A local control | 40 | 40 | 0 |
| T122 OFF/ON | 40 | 40 | **0** |
| T121 OFF/ON | 40 | 38 | **2** |

The two T121 failures are one distinct observation - fixture `9cb11d26`, `BELOW` view, reported on
both passes because `PASSES = 1` - so **one of twenty distinct fixture x view T121 comparisons
failed**.

### T121 owned work (BELOW)

| Counter | T121 OFF | T121 ON | T121 removes |
|---|---:|---:|---:|
| `conservativeDescriptorRejects` | 0 | 137,672,515 | - |
| `descriptorEvaluations` | 242,000,684 | 104,281,764 | **137,718,920 (56.9%)** |
| `primaryRaySteps` | 7,707,313 | 7,706,188 | - |

The evaluation delta matches the reject count, so T121 demonstrably owns and removes real work.

### T122 owned work (SIDE)

| Counter | T122 OFF | T122 ON | T122 removes |
|---|---:|---:|---:|
| `descriptorTextureFetches` | 6,291,288,992 | 3,939,338,922 | **2,351,950,070 (37.4%)** |
| `avoidedDescriptorTextureFetches` | 0 | 2,736,030,628 | - |
| `descriptorEvaluations` | 466,262,524 | 466,264,038 | unchanged, as intended |

**Counter accuracy note.** The `avoidedDescriptorTextureFetches` counter reports ~6 fetches per
descriptor evaluation (`+= 2` and `+= 4`), but the measured OFF/ON delta is ~5. The `+= 4` site
counts four texels while `directStormLobeDistanceFromData` consumes three (`positionHeight`,
`radiusRotation`, `shearMedia`); the fourth, `lifecycleRole`, is used for topology decode, not by
the SDF. The counter therefore over-states by one fetch per evaluation. **The measured delta
(2.352 billion, 37.4%) is the trustworthy figure**, not the counter.

### T121 FAILURE - genuine, deterministic, not capture noise

Fixture `9cb11d26`, `BELOW`:

```
medianComparison passed=false
maxAbsRGBA=5.371094e-03   epsilon=4.882813e-04   epsilonBasis=rgba16f_storage_ulp
changedPixelCountAboveEpsilon=1   totalComparedPixels=230760
maxComparedMagnitude=9.907227e-01
```

The difference is **11x the tolerance** at one pixel, and it is not measurement noise:

- both arms reported `dev=0` - all five samples within each arm were identical;
- that fixture's `BELOW` A/A control passed cleanly at `chg=0 dev=0`;
- `armsDistinct=true` from the draw snapshot, `restored=true`.

**It is not a logic error in the bound.** A deterministic search over the role, eccentricity, shear
and orientation space (`validateT121VerticalBoundIsConservative()`, 29,297,664 probes) found the
worst violation of `lobeDistance >= verticalLowerBound` to be `1.4e-14` blocks - pure double
rounding (`bound=124.0` vs `sdf=123.99999999999999`). The vertical slab bound *is* conservative.

The other skipped effects are exact by construction: the role mask and the height contribution are
both zero whenever `lobeDistance > lobeSoftness`, and `mixFactor` saturates to exactly `1.0`,
leaving `mix(lobeStrength, groupStrength, 1.0) = groupStrength` and the smooth-minimum correction
`radius * h * (1 - h) = 0`.

**Leading hypothesis, not proven.** `stormLobeBlendRadius` clamps to
`STORM_MAX_BLEND_BLOCKS = 48`, and the cap binds whenever `min(major, minor) >= 192` (or `>= 80`
for core/tower). At T134 severe scale every lobe radius clears that, so `blend = 48` is the normal
case and the guard's margin against `groupDistance + STORM_MAX_BLEND_BLOCKS` is **exactly zero**
rather than generous. The guard tests `verticalLowerBound`, but the union consumes `lobeDistance`,
computed by a different expression; in float32 at ~1,000-block magnitudes the two can differ by
~1e-4 blocks. Where `verticalLowerBound` exceeds `groupDistance + 48` by less than that, the actual
`lobeDistance` can fall marginally below `groupDistance + blend`, making `h` land just under `1.0`
and reviving a small `48 * h * (1 - h)` correction. That is consistent with the observed magnitude
and rarity, but it has **not** been proven to be the cause.

**No production behaviour was changed to address this.** Fixing it would alter production density
composition, which is outside the authorised scope.

### Verdicts

| Optimization | Bounds owned work | Preserves comparison image | SC-020 |
|---|:--:|:--:|---|
| T119 group topology | yes | yes - 40 medians, byte-identical | **satisfied** |
| T121 conservative rejection | yes - 56.9% of evaluations | **no** - 1 pixel at 11x tolerance | **FAILS** |
| T122 descriptor-fetch reuse | yes - 37.4% of fetches | yes - 40/40 medians, 0 changed pixels | **satisfied** |
| T123 bounded evaluation cost | yes | not applicable - see below | **satisfied** |

**T123 needs no OFF arm, and building one would have been fake.** T123 owns a documented structural
bound plus reporting, not an image transformation (`plan.md`, "T123 documented descriptor evaluation
bound"). The only candidate switches - the `transmittance < 0.015` and optical-depth-28 exits -
predate T123 (the transmittance exit dates to `cc4dec1`, 2026-07-04, against T123's 2026-08-20
acceptance) and belong to the density integration, so disabling them would change integration
semantics rather than disable T123. What SC-020 needs is instead checked directly by
`validateT123InstrumentationOnly()`: all ten counter mutations sit behind `paWorkloadCaptureActive()`,
which is restricted to `DebugView == 22 || DebugView == 23`, so no T123 code is reachable in a
production FINAL frame.

### Regression gate

`./gradlew check` and `./gradlew build` both BUILD SUCCESSFUL. `stormVolumetricGeometrySandbox`
24 passed / 0 failed, `stormMorphologySandbox` 10 / 0, `stormDensityThresholdSandbox` 1 / 0,
`cloudMorphologyTopologySandbox` emitted all T133/T134 contract records. No accepted T132, T134 or
SC-018 evidence regressed.

### Still open, unchanged

`STORM_MAX_BLEND_BLOCKS` remains `48`. The `BELOW`/`A1` raw within-arm dispersion recurred
(`chg=2325 dev=2`, `chg=14139 dev=2`) and remains **unattributed**; it is absorbed by the robust
median and is separate from the T121 finding above, which showed `dev=0` on both arms.
SC-006 / T070 remains failed and open: severe-scale storm raymarch measures 63-261 ms at `641x360`
with `governorScale=0.50000` / `resolutionScale=0.75000`, against a 16.7 ms Ultra target, with
descriptor texture fetches the dominant cost. Any future optimization must preserve the now-proven
T122 and T119 neutrality contracts.

## T133 combined revalidation - 2026-08-27 - OPEN, one blocker

**Status: T133 is NOT closed.** Five of its six evidence areas pass. One written requirement that
T133 itself cites - SC-020's optimization-neutrality clause - is not satisfied for T121 and T122,
and satisfying it requires a production shader change that is outside the authorised scope. T098
therefore remains blocked.

### Requirements this task is measured against

T133 cites **FR-028-FR-031** and **SC-018-SC-020**. SC-006 (Ultra 95th-percentile total frame time
of 16.7 ms at 1920x1080 on the RTX 4070 laptop) is **owned by T070**, not by T133, and is reported
below as context rather than as a T133 gate.

### Deterministic morphology guards (evidence fixture: 128 deterministic samples)

Both guards that `validation/t134-severe-system-scale.md` deferred into T133 are now implemented in
`CloudMorphologyTopologySandbox.validateStormPhysicalScale()`, with fail-first coverage in
`validateT133GuardsRejectKnownViolations()`.

**Aspect-ratio guard: PASS.** T127's "Height / footprint" row records 0.55-0.70. Measured across all
128 samples: plan footprint `0.6698-0.6818`, resolved-centre lower `0.6558-0.6825`, resolved-centre
upper `0.6100-0.6330`. All three readings are asserted, which closes the hole T134 recorded - the
old guards asserted footprint and height independently, permitting the combination `880 / 1200 =
0.733`. Fail-first proves the band rejects both `0.7333` and `0.4800`.

**ANVIL-span guard: PASS**, after correcting the metric. T127 records the canopy as 1,150-1,450
blocks *and*, in the same row, as 1.20-1.35 of BASE. Measured:

| Reading | Span | Over BASE | 1,150-1,450? | 1.20-1.35 of BASE? |
|---|---:|---:|:--:|:--:|
| Widest single ANVIL lobe | 990.000 | 0.9483 | no | no |
| Union of the 4 ANVIL lobes | 1268.710-1286.527 | 1.2152-1.2323 | **yes** | **yes** |

The canopy is a union of four ANVIL lobes at different centres, so its structural span is the union
extent. The widest-single-lobe reading satisfies neither half of the contract row; the union reading
satisfies both. The two halves cross-validate each other, so the interpretation is not a choice made
to fit the fixture. Both halves are asserted so it cannot drift. Fail-first proves the band rejects
`990.0` and `1500.0`. **No morphology was changed, and no morphology violation exists.**

### SC-018 three reference viewing distances: PASS

Automated through the existing auto-driver: `Viewpoint` gained `DISTANCE600/900/1200` at absolute
lateral distances from the resolved group centre at system mid-height, and the suite samples one
settled arm for them. Evidence fixture `35bc0ba7-353b-4431-baad-a268c7ae3b7b`, fingerprint
`770ba77ee2876554`, identical at all three distances.

| Distance | Measured | Descriptors | Storm groups | Lobes | Coherent | Marched at range | structuralChanged |
|---:|---:|---:|---:|---:|:--:|:--:|:--:|
| 600 | 600.00000 | 10/10 | 1 | 10 | yes | yes | false |
| 900 | 900.00000 | 10/10 | 1 | 10 | yes | yes | false |
| 1,200 | 1200.00000 | 10/10 | 1 | 10 | yes | yes | false |

`sc018Passed=true` at all three. One coherent severe system throughout: exactly one storm group, the
fixture's own ten descriptors, no foreign storm content, no ownership or fallback transition, and no
distance-dependent disappearance - the system was demonstrably marched at every range
(`primaryRaySteps` 10.1M-23.8M, `descriptorEvaluations` 163M-555M).

### Severe-scale performance characterization

Measured on the accepted post-T134 geometry. **The pre-T134 T130 baseline is historical only and is
not compared against these numbers - it measured a different, smaller workload.**

All views: `target=641x360`, `governorScale=0.50000`, `resolutionScale=0.75000`,
`rayStepsConfigured=96`, `lightStepsConfigured=6`.

| View | GPU min | GPU median | GPU mean | GPU max | n | Primary ray steps | Descriptor evals | Descriptor fetches | Avoided fetches | Conservative rejects | Light-march density | Empty-space rejects | Early terminations |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| SIDE | 201.480 | 206.602 | 206.729 | 211.006 | 18 | 22,103,011 | 524,050,626 | 4,251,269,050 | 3,041,801,060 | 215,797,314 | 2,539,808 | 2,489,020 | 32,624 |
| FAR | 59.500 | 62.804 | 63.367 | 69.852 | 19 | 6,893,926 | 38,251,290 | 441,041,480 | 76,932,784 | 6,622,561 | 167,136 | 6,415,448 | 2,624 |
| BELOW | 138.950 | 143.959 | 144.056 | 149.681 | 17 | 6,737,769 | 88,676,808 | 1,180,682,974 | 542,373,844 | 102,858,600 | 3,612 | 2,950,244 | 25,137 |
| ABOVE | 253.552 | 260.616 | 260.389 | 267.659 | 17 | 6,770,232 | 331,583,019 | 2,050,385,829 | 1,968,179,032 | 70,704,321 | 21,660,402 | 1,471,860 | 150,227 |
| 600 | 222.978 | 228.043 | 228.489 | 234.577 | 17 | 23,788,327 | 506,642,396 | 4,867,848,616 | 3,247,128,772 | 389,161,613 | 8,709,824 | 0 | 127,587 |
| 900 | 211.126 | 215.369 | 215.449 | 220.082 | 17 | 23,491,543 | 555,143,180 | 4,543,725,216 | 3,242,481,900 | 239,860,773 | 2,435,716 | 2,034,857 | 36,189 |
| 1,200 | 134.216 | 136.476 | 136.827 | 144.379 | 17 | 10,052,588 | 163,246,086 | 1,401,471,838 | 848,833,736 | 56,292,859 | 2,432,920 | 5,674,993 | 31,859 |

**Honest reading.** Storm raymarch costs **136-261 ms** at `641x360` with the governor already
reduced to 0.5 and resolution to 0.75. This is not close to SC-006's 16.7 ms, and the gap cannot be
explained away by the reduced controls - the reduced controls make these numbers *better* than an
Ultra full-resolution measurement would be, not worse. The renderer is **not** performance-ready for
SC-006 today.

These numbers are **not an SC-006 measurement** and must not be quoted as one. SC-006 specifies
95th-percentile *total frame time* over a ten-minute post-convergence Ultra run at 1920x1080 on
specific hardware; the table above is storm raymarch GPU time at a reduced fixed target. That
measurement belongs to T070.

**Dominant cost source.** `descriptorTextureFetches` at 4.25-4.87 billion per frame in the
expensive views - roughly 8 fetches per descriptor evaluation, with 506-555 million descriptor
evaluations per frame. Descriptor texture bandwidth inside density evaluation, not ray step count,
is the bottleneck: FAR has 6.9M primary steps at 63 ms while distance600 has 23.8M steps at 228 ms,
and the fetch counts track the cost far more closely than the step counts do. The safest next
optimization class is therefore further descriptor-fetch reduction of the T122 kind (hoisting and
reuse within one evaluation), which is bounded, mathematically checkable, and does not touch the
density composition - **but see the blocker below: T122's own neutrality is currently unproven, so
extending that class before closing the debt would compound it.**

### BLOCKER - SC-020 optimization neutrality for T121 and T122

SC-020 reads, in part: *"Before T098 resumes, ... every approved foundational optimization preserves
the comparison image within its documented visually-neutral tolerance and reduces or bounds the
measured work it owns."* FR-030 likewise permits foundational performance architecture before
T098/T099 *"only when it is visually neutral."*

| Optimization | Reduces/bounds owned work | Preserves comparison image | Status |
|---|:--:|:--:|---|
| T119 group topology | yes (`scans=0`, `metadataReadsPerGroup=3`) | **yes** - 40 OFF/ON medians, byte-identical | satisfied |
| T121 conservative rejection | yes (`conservativeDescriptorRejects` 6.6M-389M) | **not demonstrated** | **blocked** |
| T122 descriptor-fetch reuse | yes (`avoidedDescriptorTextureFetches` 76.9M-3.25B) | **not demonstrated** | **blocked** |
| T123 bounded evaluation cost | yes (`earlyTerminations` 2.6K-150K) | inherits T121/T122 | dependent |

T121 and T122 have never been run with an OFF arm. Unlike T119, which toggles through the
`StormTopologyMode` uniform, both are shader-internal, so a true OFF arm requires adding
pre-optimization branches to `cloud_atmosphere_volume.fsh`. That is a production shader change and
was **not** made. The debt is therefore neither closed nor waived: SC-020's second clause is
unsatisfied for two of the four approved optimizations, and SC-020 is explicitly a T098 entry
condition.

Reducing measured work is **not** a substitute for image preservation, and T119's banked neutrality
does not transfer to T121 or T122 - they are different code paths with different failure modes.

### BELOW/A1 residual dispersion

Recurred on this fixture at `chg=526 dev=1`, in the same place it always appears: the first sampled
group of the BELOW view. It does not affect T133's objective evidence. The SC-018 distance views and
the performance table are workload, GPU-timing and coherence measurements, none of which depend on
raw per-frame image identity; and the T132 image evidence is accepted under the robust-median
protocol, which the written criteria endorse. Nothing in FR-028-FR-031 or SC-018-SC-020 requires
raw-frame determinism. It is **not fixed**, its cause remains **unattributed**, and it is retained
here rather than closed.

### Regression gate

`./gradlew check` BUILD SUCCESSFUL; `./gradlew build` BUILD SUCCESSFUL. Sandboxes:
`stormVolumetricGeometrySandbox` 21 passed / 0 failed (including all 9 T132 diagnostics),
`stormMorphologySandbox` 10 passed / 0 failed, `stormDensityThresholdSandbox` 1 passed / 0 failed,
`cloudMorphologyTopologySandbox` completed with all contract records emitted (T133 aspect, T133
anvil, T134 scale, T134 resolved-centre envelope). The same live suite re-confirmed the accepted
T132/T119 evidence on a fresh fixture: 8/8 A/A medians and 8/8 T119 medians passed at zero changed
pixels with `armsDistinct=true`. **No accepted T132 or T134 evidence regressed.**

### T133 scorecard

| Area | Requirement | Verdict |
|---|---|---|
| Physical scale - aspect ratio | FR-028 / T127 | **PASS** |
| Physical scale - ANVIL span | FR-028 / T127 | **PASS** |
| Three reference viewing distances | SC-018 | **PASS** |
| Material continuity trace | SC-019, FR-029 | **PASS** (16-block interval) |
| Reference viewpoint matrix recorded | SC-020 clause 1 | **PASS** |
| Optimization image neutrality | SC-020 clause 2, FR-030 | **FAIL** - T121, T122 |
| Retained regressions | FR-031 | **PASS** |

**T133 remains OPEN.** **T098 is NOT unblocked**, because SC-020 is a written T098 entry condition
and its neutrality clause is unsatisfied for T121 and T122.

`STORM_MAX_BLEND_BLOCKS` remains `48`, untouched; nothing in T133's requirements proved it must
change.

# Renderer-wide Native Storm Architecture Audit

**Date**: 2026-08-19
**Status**: T098 paused; documentation and measurement gate only. No Java or GLSL change is authorized by this audit.

## Scope and retained architecture

The Phase 4S composition remains authoritative:

```text
descriptor envelope -> base volumetric noise remap -> multi-scale erosion -> final density
```

The recently corrected storm base scale, proportional capped warp, CPU/GPU noise-pixel parity,
final-density rain, and final-density camera/whiteout behavior are retained. This audit does not
authorize a density/noise redesign, descriptor-density normalization, a global density increase,
or a quality reduction.

## Finding A: the severe storm has no severe-system scale contract

The observed live examples occupy roughly Y 222..502 and only a few hundred blocks horizontally.
That is consistent with the current generator deriving the group from one `CloudShapeProfile` and
then placing only seven to eleven role lobes inside a compact fraction of that plan. It is not yet a
derived severe-system footprint.

| Dimension input | Current controlling stage | Why it controls apparent system size |
|---|---|---|
| Source base footprint and total height | `CloudShapeProfile.baseRadius`, `baseOffset`, `topOffset` | These seed every generated role bound. |
| Group footprint | `stormAnvilPlan` / `stormAnvilPlanForCount`: `radius`, `groupRadius`, descriptor count | They limit the horizontal placement range and member count. |
| Role extents and vertical span | `stormLobeSpec` BASE/CORE/TOWER/ANVIL radius and base/top fractions | They determine the actual role envelopes within the group plan. |
| Role placement | `stormCell` longitudinal/cross offsets and role Y offsets | They determine whether available footprint becomes a connected system or compact local lobes. |
| Rendered lobe dimensions | `VolumetricRenderCell` role radius-scale/aspect and `StormLobeEvaluator` / shader role profiles and bounds | They transform source descriptors into real rendered world-space distance fields. |
| Connected mass at joins | world-space smooth-union blend radius and per-role edge width/softness | They can reduce useful span or make a large source plan read as disconnected. |
| Detail relative to the system | base wavelengths 50 / 25 / 12.5 blocks; erosion detail about 22.7 down to 1.4 blocks | These were calibrated for the current envelope. They must be re-evaluated after a derived system scale so billows remain primary/secondary structure and erosion remains surface breakup. |

T127 must derive target footprint, base/core/tower/anvil spans, height, aspect ratio, member count
per occupied volume, and three several-hundred-block viewing distances before changing any one of
these inputs. Uniformly multiplying descriptor coordinates is not a valid derivation.

## T127 measured severe-system target

The representative live group is approximately Y 222..502 (280 blocks) with a roughly 500-block
lateral envelope. At 600 blocks it occupies only about 45 degrees in its broadest view, which
explains the compact isolated-cloud reading. The following target is derived from horizon presence,
role relationships, and the fixed ten-member budget rather than a uniform descriptor multiplier.

| Metric | Current representative | Derived target | Relationship preserved |
|---|---:|---:|---|
| Overall horizontal footprint | ~500 blocks | 1,200–1,500 blocks | 90–103° at 600 blocks; 53–67° at 1,200 blocks |
| Storm height | ~280 blocks | 720–880 blocks | horizontal footprint / height = 1.5–1.9 |
| BASE width | ~340 blocks | 900–1,100 blocks | 0.75–0.80 of full footprint |
| CORE width | ~190 blocks | 420–520 blocks | 0.45–0.50 of BASE |
| Lower / upper TOWER width | ~120 / ~80 blocks | 280–360 / 180–250 blocks | 0.65–0.75 of CORE at its embedded base, then progressive narrowing |
| ANVIL span / thickness | ~520 / ~110 blocks | 1,150–1,450 / 150–220 blocks | 1.20–1.35 of BASE and 3.5–5.0 of upper TOWER |
| Height / footprint | ~0.56 | 0.55–0.70 | laterally dominant but still convective |

At this scale, the retained 50 / 25 / 12.5-block base bands provide approximately 18–30 / 36–60 /
72–120 features across the footprint and 5–7 / 10–14 / 20–29 across a lower tower. The retained
22.7-to-1.4-block detail field stays below the primary billow scale and therefore remains surface
breakup. The physical-dimension implementation is deliberately not folded into T131: T131 is
restricted to the separately measured material-continuity cause. The target remains a required
input to the later physical-scale implementation and T133 revalidation.

## Finding B: envelope membership does not prove one visual medium

The lower/upper horizontal separation can be introduced after geometry, so more local overlap
tuning is not evidence-based. The following complete pipeline must be traced for the same
world-space vertical line through the representative group centre.

| Rendering stage | Inputs that can differ by altitude or role | Separation it could create |
|---|---|---|
| Descriptor geometry | role profile, base/top bounds, radius/aspect, orientation, offsets, placement | A real gap, neck, shelf, or insufficient anvil span. |
| Coverage envelope | signed lobe distance, world-space smooth unions, blend radius, edge width/softness, live descriptor strength | A connected geometry can lose occupied coverage at a role boundary. |
| Body and density remap | role-specific envelope/body handling, `CORE_FILL`, coverage remap, density multiplier | A coverage-continuous storm can change opaque mass abruptly. |
| Noise coordinates | base-noise domain, detail-noise domain, proportional warp, height/world normalization | Different carrier phase or directional behavior can make adjoining areas look like separate materials. |
| Erosion | base-band contribution, detail erosion, role/height branches, clamping/interpolation boundaries | A locally different breakup rate can create a shelf or a smooth lower balloon. |
| Cloud-density integration | direct-storm availability, profile/material fallback branch, weather/slab base-top, height normalization, precipitation darkening | A direct/fallback or normalization boundary can change material independently of geometry. |
| Optical medium | final density, extinction scale, local optical-depth accumulation | A continuous density field can change transmission abruptly. |
| Lighting | cone sampling, shadow density source, direct light, ambient gradient, phase function, underside/rain terms | Different illumination or optical-depth behavior can make one medium read as two. |
| Sampling and history | ray step domain, early termination, temporal accumulation/reprojection, texture-domain transitions | A sampling boundary can imitate a material seam without a field discontinuity. |

## Required vertical material-continuity diagnostic

T128 adds an on-demand, deterministic trace for the live `3c039aa7` fixture and the existing
live-calibrated ten-descriptor fixture. It samples a fixed X/Z through the group centre at no more
than 16-block Y intervals, extending below BASE through above ANVIL. At every interval it records
the following table; inactive roles and branch flags are recorded explicitly rather than omitted.

| Y | Active descriptor roles / IDs | Coverage and envelope strength | Base noise / carrier | Detail erosion | Final density | Extinction | Light optical depth | Direct light | Ambient light | Final rendered contribution | Branch / normalization flags |
|---:|---|---|---|---|---:|---:|---:|---:|---:|---:|---|
| `<sample>` | `<roles>` | `<coverage, strength>` | `<value, coordinates>` | `<value>` | `<value>` | `<value>` | `<value>` | `<luma>` | `<luma>` | `<luma>` | `<direct/fallback, h01, weather/slab>` |

The trace is evaluated in stages. The first discontinuity classifies the cause:

1. distance/coverage changes first: geometry or union/edge treatment;
2. coverage is continuous but base carrier, erosion, or final density changes first: density/noise
   composition;
3. density is continuous but extinction or optical depth changes first: medium integration;
4. optical values are continuous but direct/ambient/final contribution changes first: lighting,
   phase, shadow, precipitation, or sampling/history.

No role-envelope correction may start before T129 records this first differing stage and rules out
earlier stages. The trace is also the baseline for a correction: a role is morphology only, never a
separate visual material.

## Finding C: the current cost path is architectural

Live raymarch measurements of approximately 80, 100, 140, and 200+ ms make performance a
correctness-adjacent constraint. The direct storm field is repeatedly evaluated in primary samples
and light-cone/optical-depth samples. Current per-sample group-range scans, descriptor texture
fetches, repeated envelope work, and full `cloudDensity` work in lighting paths can multiply cost
well beyond the primary raymarch.

The following can proceed before T098 only with frozen comparison captures, trace parity, and a
documented visually-neutral tolerance:

- precomputed, compact group topology and bounded group bit masks;
- conservative descriptor/group culling and a cheap envelope rejection before expensive body work;
- conservative empty-space skipping from complete group bounds;
- bounded descriptor fetches, distance-aware early rejection, and reuse of facts already computed
  for the same sample;
- reduced register/scratch pressure and removal of duplicate local work;
- earlier ray termination when it is mathematically equivalent; and
- a lighting-support proxy only when its optical contribution is demonstrated equivalent within the
  frozen visual and material-trace tolerance.

Changing step counts, reducing bands, weakening lighting, lowering resolution, or accepting an
image change is not foundational performance work. Those remain separately validated quality work
after T098/T099.

## Correction gate and evidence sequence

1. T127 derives severe-system physical-scale targets and re-evaluates all noise wavelengths against
   them.
2. T128 implements the vertical trace and records fail-first diagnostic evidence.
3. T129 attributes the lower/upper split to the first discontinuous stage.
4. T130 records reference cost/work counters and defines the visual-neutral comparison contract.
5. T131 corrects only the measured single-medium cause; it may alter role geometry only if geometry
   is measured first.
6. T119, T121, T122, and T123 execute only the approved visually-neutral performance primitives;
   T132 validates their trace/image/cost evidence.
7. T133 revalidates scale, material continuity, Phase 4S morphology, final-density consumers, and
   performance together. Only then can T098 resume.

T099 remains blocked by T098. T042 remains blocked by T099. Networking, server authority, saves,
forecasts, Simple Clouds ownership, candidate non-authority, history behavior, and legacy fallback
remain non-negotiable invariants throughout this sequence.
