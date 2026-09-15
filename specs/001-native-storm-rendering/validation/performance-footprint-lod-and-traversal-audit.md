# T168 - Footprint step LOD + descriptor traversal audit

Branch `experiment/cloud-descriptor-k`, parent `a280c10`. Nothing merged.
Campaign run 05Sep2026 01:47-01:52, Ultra, raw scale 0.2500, cloud target
480x270, framebuffer 1920x1080, 96 steps, 60 samples per cell, 15 arms x 3
poses = 45 cells. `T168_REJECTED count=0`. History disabled across the matrix.

## 0. Harness verification (required before any timing)

Printed before the first cell, from the run itself rather than assumed:

```
POSE_GUARDS armed=true arrivalCheck=true radiusRebuild=true fixtureIdentity=true
            driftControl=true arrivalToleranceBlocks=2.00 radiusTolerance=0.02
T166_ARRIVAL pose=FAR reached after 49 extra frames residual=0.00
```

The guards are armed by `poseGuardsArmed() { return t141EvaluationRun; }`, not by
a per-campaign flag. T167 lost the arrival guard because `t166PoseTargetValid =
t166Run` was never extended to the new campaign; keying the predicate to the
sweep itself means a future campaign inherits the guards instead of having to
remember them.

Drift controls (anchor measured twice per pose):

| Pose | first anchor P50 | repeat anchor P50 | ratio | verdict |
|---|---|---|---|---|
| FAR | 13.8783 | 14.4527 | 1.0414 | stable |
| SIDE | 22.2331 | 21.9443 | 0.9870 | stable |
| PLAY_VIS_NEAR | 20.2629 | 22.1327 | 1.0923 | **DRIFTED** |

**PLAY_VIS_NEAR is reported but not banked.** Its anchor moved 9.2% across the
matrix, so arm-to-anchor ratios at that pose carry that much systematic error
and cannot support a fine distinction. FAR and SIDE are within tolerance.

A pre-existing cosmetic artifact: the campaign header logs
`resolutionScale=NaN`, while every cell reports `resolutionScale=0.2500`. The
same NaN header appears in the T166/T167 logs. Scale was correct.

## 1. Headline

**Task A succeeds. Task B is closed as not worth building, and this time the
measurement says so directly rather than by inference.**

The footprint LOD is the first step-LOD arm in this line that is both faster
than the shipped constant and better-behaved than T167's graded curves at the
two banked poses. The descriptor audit ends the upstream-binning question: the
group walk visits **exactly 10.000 lobes per group entered**, groups are entered
whole, and only 38-48% of visited lobes change the union result - but the
non-contributing majority is *sample-dependent*, so no static structure can
remove it.

## 2. Task A - footprint step LOD

### 2.1 The derivation

A world-space span `s` at ray distance `t` projects to a height, in cloud-target
pixels, of `pixels(s,t) = s * P11 * H / (2t)`, with `P11 = CloudProjMat[1][1] =
1/tan(fovY/2)` and `H` the cloud-target height in pixels (270 - the resolution
scale is already inside it and must not be applied twice). Solving for the span
covering exactly `P` pixels and dividing by the shipped exterior fine step:

```
growth(t) = ( 2P / (P11 * H * fineStep) ) * t
```

**Linear in `t`.** The whole reciprocal collapses to one per-fragment constant.
That is the correction to T167, which paid a per-step division for the same
criterion and lost up to 27% of a frame to it; here the inner loop is one
multiply and one clamp:

```glsl
clamp(paFootprintCoefficient * t, 1.0, PA_ARM_FOOTPRINT_MAX)
```

Verified numerically against the live configuration (fov 110 deg, P11 0.7002,
H 270): one fine step covers 0.473 px at t=500 and 0.118 px at t=2000, so
production oversamples 2.1x rising to 8.5x across the march. The build gate
asserts both that the coefficient is hoisted and that FINAL defines no
`PA_ARM_FOOTPRINT`.

**Caveat on provenance:** Codex A ran out of credits before producing an
independent derivation, so this algebra is mine alone and has not been checked
by a second party. The numeric spot-check and the measured pose behaviour are
consistent with it, which is evidence but not verification.

### 2.2 Measured (cloud pass P50, ms; speedup vs that pose's first anchor)

| Arm | FAR ms | FAR | SIDE ms | SIDE | PVN ms (unbanked) | PVN |
|---|---|---|---|---|---|---|
| `lean_final` (anchor) | 13.8783 | 1.000x | 22.2331 | 1.000x | 20.2629 | 1.000x |
| footprint P=0.35 | 10.6772 | 1.300x | 19.1529 | 1.161x | 17.3947 | 1.165x |
| footprint P=0.50 | 6.6990 | *2.072x* | 17.0639 | 1.303x | 15.1470 | 1.338x |
| footprint P=0.75 | 9.4566 | 1.468x | **12.0996** | **1.838x** | 12.7242 | 1.592x |
| T167 curve A late | 8.3794 | 1.656x | 20.7012 | 1.074x | 19.1273 | 1.059x |
| T166 distance step | 9.4454 | 1.469x | 13.9981 | 1.588x | 12.7642 | 1.587x |

**The FAR P=0.50 cell is bimodal and its P50 must not be read as a result.**
Its P95/P50 spread is 1.478 against 1.016-1.023 for its neighbours, and it
reports a *lower* time than the strictly more aggressive P=0.75 arm - an
ordering that is physically impossible for a monotone step-size parameter and
does not occur at either other pose, where the series is monotone and tight.
The same signature appears on `t168_stack_balanced` at FAR (spread 1.511).
Treat FAR P=0.50 as ~9.9 ms (its P95), not 6.70.

With that correction the honest FAR ordering is P=0.35 -> 10.68, P=0.75 -> 9.46,
and the winner at the two banked poses is **P=0.75**: 1.468x FAR, 1.838x SIDE.

### 2.3 Quality (vs `lean_final`, same pose, back-to-back capture)

| Arm | pose | IoU | cloud SSIM | edge SSIM | thin ret. | hole ret. | seam |
|---|---|---|---|---|---|---|---|
| P=0.35 | FAR | 0.9973 | 0.9914 | 0.9922 | 0.9157 | 1.0000 | 0.00132 |
| P=0.50 | FAR | 0.9961 | 0.9867 | 0.9874 | 0.9036 | 0.8750 | 0.00260 |
| P=0.75 | FAR | 0.9942 | 0.9773 | 0.9796 | 0.8434 | 0.8750 | 0.00363 |
| P=0.35 | SIDE | 0.9988 | 0.9974 | 0.9891 | 0.9637 | 1.0000 | 0.00031 |
| P=0.50 | SIDE | 0.9980 | 0.9963 | 0.9887 | 0.9190 | 0.8571 | 0.00059 |
| P=0.75 | SIDE | 0.9967 | 0.9919 | 0.9794 | 0.8631 | 0.8571 | 0.00139 |
| T167 curve A | SIDE | 0.9994 | 0.9984 | 0.9933 | 0.9777 | 1.0000 | 0.00013 |

Degradation is monotone in P at every pose and on every metric - the sign of a
criterion that is actually doing what it claims. Compare T167 nearest-K, whose
thin retention collapsed to 0.381-0.492; the footprint arms hold 0.84-0.96.

**The cost is thin material and holes, exactly where T167 predicted.** P=0.75
gives up 14-16% of thin retention and one hole in eight at both banked poses.
P=0.35 keeps holes intact (1.0000) and 0.92-0.96 of thin material for a
1.16-1.30x return.

**Pose consistency:** the footprint criterion is derived from the live
projection, so it does not need a per-pose constant - and the three poses do in
fact order identically (P=0.35 < P=0.50 < P=0.75 in both speed and degradation)
once the bimodal FAR cell is set aside. That is the property T167's
`distance/MaxRenderDistance` grading did not have.

## 3. Task B - descriptor traversal audit: **not worth building (CASE C)**

### 3.1 Measured per density call (anchor programs)

| Pose | density calls | groups / density | **lobes / group** | contributors / group | share |
|---|---|---|---|---|---|
| FAR | 522,521 | 1.387 | **10.000** | 4.257 | 42.6% |
| SIDE | 3,287,936 | 1.423 | **10.000** | 4.757 | 47.6% |
| PVN | 988,550 | 1.710 | **10.000** | 3.800 | 38.0% |
| FAR @cap8 | 419,904 | 1.451 | **8.000** | 3.358 | 42.0% |
| SIDE @cap8 | 1,601,012 | 1.800 | **8.000** | 3.180 | 39.7% |

`lobes/group` is 10.000 at full residency and 8.000 at cap 8 - not approximately,
exactly. This is the direct measurement of what the static audit predicted:
**groups are entered whole**, every member is visited, and naming one member of
a ten-member group pulls in all ten. A candidate structure therefore cannot
filter below group granularity, and with this fixture carrying its ten
descriptors in a single group there is nothing to bin.

### 3.2 The residency ceiling

| Cap | FAR | SIDE | PVN | storm rendered? |
|---|---|---|---|---|
| 1 | 7.653x | 10.596x | 8.671x | **no** - `cloudDensityCalls=0` |
| 2 | 7.484x | 10.089x | 8.034x | **no** |
| 4 | 7.410x | 9.873x | 7.751x | **no** |
| 6 | 7.314x | 9.607x | 7.397x | **no** |
| 8 | **1.134x** | **1.290x** | 1.176x | yes |

Caps 1-6 report `cloudDensityCalls=0`, `groupFieldCalls=0`, `lobesVisited=0` -
the storm is not drawn at all, so their 7-10x figures are the cost of an empty
sky and are not a filter ceiling. **The only valid ceiling point is cap 8**, and
removing 2 of 10 descriptors before the loop buys 1.13x (FAR) to 1.29x (SIDE)
while changing what is drawn.

### 3.3 Verdict

Upstream binning is **not worth building**:

1. A perfect prefilter that removed 20% of descriptor work upstream is worth
   1.13-1.29x, measured, and that version already renders a different image.
2. The 52-62% of visited lobes that do not change the union answer cannot be
   removed by a static structure, because contribution is **sample-dependent** -
   a lobe that is overridden by the smooth minimum at one sample point is the
   nearest surface at another. There is no fixed subset to bin.
3. Groups are entered whole, so the granularity a candidate structure could act
   on is the group, and this fixture has one group.

This is CASE C in the T167 sense - the ceiling is real but too low to justify
the structure - rather than CASE D, since unlike nearest-K the capped arms are
not *slower* than full evaluation.

**Counter defect, stated plainly:** `descriptorCandidateRanks` reads 0 in every
cell. The counter was declared and emitted but no increment site was ever added
- my omission, not a measurement. The "always exactly 8 candidate ranks
examined" figure remains the static reading from the code audit and is
**unmeasured**. It does not affect the verdict above, which rests on
`lobesVisited / descriptorGroupsEntered`, both of which incremented correctly.

**Also note:** the workload counters are captured on the diagnostic monolith,
which does not carry `PA_ARM_FOOTPRINT`. The footprint arms' counters are
therefore identical to the anchor's to within 0.05% and say nothing about their
stepping - their evidence is the timing and the image metrics. The residency
caps *do* move the counters, because residency is a CPU-side upload limit that
the monolith sees too.

## 4. Task C - early termination 0.045 reconfirmed

| Pose | anchor | term045 | speedup | IoU | thin ret. | alpha mass |
|---|---|---|---|---|---|---|
| FAR | 13.8783 | 12.4529 | 1.114x | 1.0000 | 1.0000 | 0.9757 |
| SIDE | 22.2331 | 19.9485 | 1.115x | 1.0000 | 1.0000 | 0.9757 |
| PVN | 20.2629 | 17.3292 | 1.169x | 1.0000 | 1.0000 | 0.9750 |

Holds against the fresh anchor: ~1.11x at both banked poses with a perfect
silhouette and perfect thin retention, costing 2.4-2.5% of alpha mass and
`maxAbsRGBA` 0.035 - the smallest image change of any arm in the matrix.

## 5. Task D - combined stack

| Arm | FAR | SIDE | PVN | thin (SIDE) | seam (SIDE) |
|---|---|---|---|---|---|
| `t168_stack` (P=0.35 + term 0.045) | 1.324x | 1.289x | 1.329x | 0.9637 | 0.00224 |
| `t168_stack_balanced` (P=0.50 + term) | *2.181x* | 1.552x | 1.354x | 0.9190 | 0.00270 |

The FAR figure for `t168_stack_balanced` inherits the same bimodality (spread
1.511) and is not banked.

**Composition is sub-additive but real.** At SIDE, P=0.35 alone is 1.161x and
termination alone 1.115x; naively 1.294x, measured 1.289x - they compose almost
exactly, because they remove different work (step count vs march length). The
best *validated* SIDE combination is not in this table: P=0.75 alone at 1.838x
beats both stacks, and stacking termination onto it was not measured this run.

## 6. Goal status

| Target | Result |
|---|---|
| FAR <= 8 ms | **No.** Every tight-variance FAR cell is >= 9.46 ms. The two cells below 8 ms are the bimodal ones. Best trustworthy FAR: **9.4566 ms** (P=0.75). |
| SIDE <= 10 ms | **No.** Best SIDE: **12.0996 ms** (P=0.75), 1.838x. |
| SIDE <= 8 ms | **No.** Would need a further 1.51x on top of the best arm measured. |

SIDE remains the binding pose at 22.23 ms and is the one to optimise; FAR is
within 18% of its target and SIDE is 51% above the 8 ms line.

## 7. Recommendation

1. **Bank the footprint LOD.** It is the correct formulation of an idea T167
   got wrong, it costs one multiply and one clamp, its degradation is monotone
   and pose-consistent, and it is the largest single validated win in the
   matrix (1.838x SIDE at P=0.75).
2. **Ship P=0.35 or P=0.50, not P=0.75, if thin material matters.** P=0.75 costs
   14-16% thin retention and a hole in eight. P=0.35 is free of hole loss at
   1.16-1.30x.
3. **Close the upstream-binning line.** The ceiling is 1.13-1.29x and the waste
   it would target is sample-dependent.
4. **Re-run FAR P=0.50 before trusting any number from it.** One pose, one arm.
5. Next candidate is not descriptor traversal: at SIDE the anchor spends
   14.83 light evaluations and 35.99 detail-octave evaluations per pixel
   against FAR's 1.74 and 4.57. SIDE's cost is lighting and detail, not the
   descriptor walk.
