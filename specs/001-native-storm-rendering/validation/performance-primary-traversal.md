# T202 - the primary body density and the refinement on the shared field

Architecture campaign, two body consumers moved from the per-call descriptor
traversal to the accepted shared field, measured against the FINAL of T201.
Four launches on 12Sep2026: `t202-campaign/t202-run1.log` (12:05-12:11,
4,977 frames), `t202-run2.log` (12:15-12:21, 5,025 frames, the
representation oracle added), `t202b-run1.log` and `t202b-run2.log` (the
post-T202 baseline and the rain-segment ceiling, twice). SIDE and PLAY_VIS_NEAR at 0.25 (480x270), Ultra 96
steps, history off, one deterministic fixture a pose, every timed arm
bracketed by a fresh FINAL anchor, three repeats, 60 samples a cell, anchor
drift <= 3% or the block is dropped (4 blocks dropped a run, none of them a
candidate block that mattered - the refinement arm lost one of six).

No rain implementation, no probe-floor, light, temporal, resolution,
every-N-step or per-lane pruning change. The accepted light/probe field
(T199) is read exactly as before: R, G, B and the light's uncovered fallback
on A are untouched; only what A carries on a *covered* node changes, and only
under a consumer bit the light never reads A under.

## RECOVERY

1. **Starting HEAD**: `19da178` (T201). Working tree carried the T195-T201
   logs, preserved.
2. **Current FINAL baseline** (the anchors of run 1, 19 cells a pose,
   bits 11): SIDE ray **13.39 ms** p50 / 14.46 p95, field build 1.96,
   composite 0.09, combined **15.44 ms**; PLAY_VIS_NEAR ray **12.76** /
   13.62, field 1.94, combined **14.78**. Run 2: 13.07 / 1.84 / 15.00 and
   12.33 / 2.09 / 14.51. T201's 12.98 / 12.35 is inside the session spread;
   the added dead T202 code in FINAL's source moved nothing measurable
   (T199 lesson re-checked).

## SEMANTICS (Task 1)

A primary body sample is `cloudDensity(p, mip 0, useDetail = DetailQuality >
0, nearCamera = t < 220 && !cameraInsideCloud, precipitation false)`. For a
descriptor-owned storm it requires:

| term | what production computes | class |
|---|---|---|
| 3/5. A descriptor envelope | `directStormShape(p)`: candidate tile, group walk, lobe SDFs, ordered smooth union -> coverage `E(d, softness) * strength`, strength, active role mask | **in the field**: R = `stormCoverageLowerBound(coverage, strength, overlap)` at the node, the exact threshold STAGE 5 forms the body against |
| B. smooth-union result | folded into A (the union distance `d` and the blended softness) | in the field only through R (see Task 3: this is the limit) |
| C. weather raster | `sampleWeather(p.xz)`: coverage, energy, base/top | **reconstructable**: irrelevant once a descriptor group owns the sample (envelopeCoverage = 1, storm constants); the node's *uncovered* flag encodes the one case it matters (raster painted, funnel) |
| D. morphology raster | profile id / role | reconstructable, same reason (storm profile forced, erosion `STORM_EROSION`, fixed material constants) |
| E. base noise | one 3D fetch at `baseNoiseDomain(p - MaterialOffset, 0.0025)`, mip 0 | **exact per sample** |
| F. detail | one 3D fetch at the warped detail domain, a second octave when `nearCamera && DetailQuality >= 2` | exact per sample |
| G. erosion | `max(cloud - (1 - detailFbm) * 0.44, 0)` | exact per sample (local) |
| H. vertical profile | the nearest group's lobe-relative height01, three material terms | **in the field**: B (linear in y inside a lobe, so trilinear reproduces it) |
| I. position terms | MaterialOffset, DensityMul, funnel (uncovered -> exact) | uniforms / fallback |

4. **Refinement** requires one thing: `minDescriptorClearance(p)` - the
   minimum over every lobe of the tile's groups of (lobe pseudo-distance or
   its vertical lower bound) minus that lobe's softness - and subtracts
   `STORM_MAX_BLEND_BLOCKS` (48) for the safe advance, capped at the coarse
   step. Class A only; nothing exact remains.
5. **Descriptor-derived terms**: A, B, H, and the refinement's clearance.
6. **Local exact terms**: E, F, G (and I through uniforms).
7. **Chosen shared representation**: the accepted atlas unchanged for the
   light and probe - R threshold, G floor, B height01 - with A carrying the
   **refinement's clearance floor** on covered nodes (bit 32:
   `minClearance(node) - dilation`, the same axis-split Lipschitz dilation
   the probe floor uses, clamped to +-4096 for the half float; a tile with
   no candidate reads 4096, which production's walk at any point of that
   tile also treats as unbounded); uncovered nodes keep the node density
   the light's fallback reads. The primary (bit 16) reads R and B
   trilinearly and evaluates E, F, G at the sample - the T198 light lookup
   at mip 0 with the primary's detail stages (`paStormFieldPrimaryDensityAt`,
   sharing `paStormFieldBodyFromThreshold`). An uncovered node hands the
   sample back to the exact evaluation. No second field, no extra channel
   for the primary: the question was whether the accepted channels suffice.

## QUALITY (Tasks 3, 6) - MEASURED, run 1; run 2 in parentheses

Against the FINAL anchor at the same pose, `t178Quality` and the T197 pocket
diagnostic (pockets are lattice- and fixture-bound: compare within a run).

| arm | pose | cloud SSIM | edge SSIM | IoU | thin | hole | dark interior | pockets | changed px | RMS | maxAbs |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **t202_refine_field** | SIDE | **0.995** | **0.982** | **0.998** | **0.977** | **0.983** | 0.983 | 0.84 | 10,366 | 0.0095 | 0.99 |
| | PVN | **0.995** | **0.996** | **0.998** | **1.000** | **1.000** | 0.982 | 0.75 | 7,466 | 0.0082 | 0.99 |
| t202_primary_field | SIDE | 0.936 (0.917) | 0.694 (0.683) | 0.975 (0.968) | 0.840 (0.876) | 0.890 (0.865) | 0.948 | 0.73 | 16,616 | 0.045 | 0.99 |
| | PVN | 0.962 (0.950) | 0.881 (0.889) | 0.971 (0.958) | 0.738 (0.704) | 0.998 | 0.945 | 0.83 | 7,516 | 0.035 | 0.99 |
| t202_both_field | SIDE | 0.927 | 0.677 | 0.973 | 0.832 | 0.882 | 0.932 | 0.68 | 20,184 | 0.046 | 0.99 |
| | PVN | 0.955 | 0.876 | 0.969 | 0.738 | 0.997 | 0.928 | 0.67 | 10,500 | 0.036 | 0.99 |
| t202_dinterp_oracle (run 2) | SIDE | 0.927 | 0.712 | 0.968 | 0.906 | 0.976 | 0.985 | 0.81 | 11,513 | 0.055 | 0.99 |
| | PVN | 0.960 | 0.897 | 0.959 | 0.704 | 1.000 | 0.986 | 0.89 | 4,160 | 0.037 | 0.99 |

For scale, the T199 FINAL-versus-pre-field frame (the light moving to the
field) scored 0.866 / 0.896 / 0.986 / 0.972 / 0.986 on the first five
columns: the primary-from-field arm is worse than that on every boundary
metric while better on cloud SSIM (it changes the body, not the lighting).

The primary-sample audit (`t202_primary_audit`: the exact density evaluated
beside the field's at every primary sample, per pixel class of the anchor):

| lookup | pose | samples | 8. mean err / mean abs | RMS | maxAbs | 9. zero/nonzero disagree | of which field 0, exact > 0 | material (> 0.0008) disagree | body mass bias | edge-band mean abs |
|---|---|---|---|---|---|---|---|---|---|---|
| threshold (R) | SIDE | 953,478 | -0.0083 / 0.032 | 0.140 | 1.29 | **4.75%** | 28,838 (3.0%) | 4.74% | **-2.8%** | 0.022 |
| | PVN | 488,370 | -0.0104 / 0.031 | 0.147 | 1.29 | 4.47% | 14,890 | 4.46% | -3.9% | 0.019 |
| distance oracle | SIDE | 856,791 | -0.0057 / 0.024 | 0.131 | 1.29 | 3.45% | 17,836 | 3.43% | -2.1% | 0.012 |
| | PVN | 412,082 | -0.0127 / 0.022 | 0.130 | 1.28 | 3.23% | 10,253 | 3.21% | -5.0% | n/a |

Uncovered fallbacks: 0 (the fixture has no raster family; `T196_SOUNDNESS
uncovered=0`). Sign of the error: the threshold lookup under-estimates
inside the body (interior region: 48% of samples under, 21% over, -4.1%)
and over-estimates just outside it (the off-cloud region: 963 samples with
material the exact side has none; the edge class +3%). That is the chord of
a smoothstep: R is `1 - (1 + fill) * E(d)` with E a smoothstep of the union
distance over +-softness, and across one slab cell the distance moves by
up to eight blocks a block vertically (T196's Lipschitz measurement), so the
threshold turns over inside a single Y cell and its linear interpolation
lands above the curve on the material side and below it outside. The pocket
difference images (`t202-campaign/SIDE_t202_primary_field@x256_diff.run1.png`)
put the loss on the base lobe's underside and the anvil-tower waist - the
horizontal surfaces.

**The representation oracle (run 2)** tested the fix a second atlas would
carry: the union distance (clamped far), the blended softness, the strength
and the height interpolated over the eight nodes of the dual cell, the
envelope and threshold formed at the sample (eight build-mode walks a
sample, quality only). It recovers the interior - hole retention 0.86 ->
0.98, dark interior 0.95 -> 0.99, mean abs error 0.032 -> 0.024, mass bias
-2.8% -> -2.1% - and **not the boundary**: edge SSIM 0.71 / 0.90, IoU 0.968
/ 0.959, thin retention 0.91 / 0.70, still 3.4 / 3.2% of samples disagreeing
on zero versus nonzero, and now an over-estimate at the edge class (+73%
relative, 3,079 off-cloud samples with ghost material at SIDE). The
residual is the distance field's own curvature at the lobe rims and the
smooth-min ridges across a 16 x ~20-block cell - second-order in the cell
size, so a finer field would need 8x the nodes for a 4x error reduction,
and the field-resolution line is closed. **No 256 x 32 representation of
the descriptor geometry reproduces the body's boundary at body accuracy;
the field serves the integrated light and the conservative probe because
those tolerate it.**

10-15. Refinement-only quality: cloud SSIM 0.995 / 0.995, edge SSIM 0.982
/ 0.996, IoU 0.998 / 0.998, thin 0.977 / 1.000, hole 0.983 / 1.000,
interior contrast ratio 1.011 / 1.009, dark interior 0.983 / 0.982. The
refinement never changes a density; it changes the safe advance and so the
sample lattice, which is what production's own refinement also does, and
the residual (10k / 7.5k pixels above the rgba16f ulp, RMS 0.009) is that
lattice. Pocket retention 0.84 / 0.75 against the anchor is lattice noise of
the kind T199 documented (its own FINAL scored 0.77 against the pre-field
FINAL on a different lattice); the pocket luminance error is 0.0013, wall
displacement 0.18 px at SIDE.

## WORKLOAD (Task 7) - MEASURED on the monolith, SIDE, per frame at 480x270

Counters at production's bits (`|final`) and at the candidate's (`|t202both`,
bits 59) on the same pose and fixture (`T136_COUNTERS`, run 1):

| counter | FINAL bits 11 | both consumers on the field | PLAY_VIS_NEAR (11 -> 59) |
|---|---|---|---|
| 16. primary density calls / pixel | 944,721 = 7.29 | 1,079,485 = 8.33 (+14%: the floored advance sends more rays through the scan) | 480,134 -> 544,697 |
| 17. refinement events | 267,894 | 275,149 | 146,560 -> 147,599 |
| shape calls tagged primary | 944,714 | **0** | 480,150 -> 0 |
| shape calls tagged refinement | 268,041 | **24,969** (the out-of-slab fallbacks) | 146,486 -> 13,085 |
| `directStormShapeCalls` | 3,662,477 | 2,509,510 (the residual is the rain segment test's 2.33M) | 3,609,623 -> 3,035,346 |
| 18. group walks (`groupFieldCalls`) | 2,708,073 = 20.9 / px | 1,570,685 = 12.1 / px | 1,553,315 -> 980,019 |
| 19. lobe visits | 27,080,590 = 209 / px | 15,706,818 = 121 / px | 15.5M -> 9.8M |
| 20. exact SDFs (`lobeExactSdf`) | 17,596,877 = 136 / px | 8,151,515 = 63 / px | 9.2M -> 4.6M |
| `descriptorEvaluations` | 22,497,241 | 13,120,766 | 15.2M -> 10.7M |
| 21. field fetches: primary / refinement / probe / light | 0 / 0 / 457,195 / 1,621,925 | **970,658 / 250,097** / 850,105 / 1,619,229 | 0/0/312k/703k -> 486k/135k/555k/703k |
| refinement fallbacks (outside the slab) | - | 24,948 | 13,515 |
| noise: detail fetches primary / detail octaves | 567,241 / 4,271,409 | 570,827 / 4,270,278 | 261k / 1.84M -> 262k / 1.84M |
| scan events / cap reached | 108,264 / 17,852 | 151,486 / 37,308 | 62k / 12.9k -> 85k / 25.7k |

Call counts stay of the same order; the traversal behind each primary call
collapses to zero and behind each refinement to the 9% that leaves the slab.
The noise work is unchanged by construction. What remains of the walk is
the rain segment test (2.33M shape calls, 59% before and 93% after) - the
constraint T201 named. The T196 soundness oracle ran on the candidate build
(`underestimates=1 worst=0.00244`, the same class as T199's 1 / 0.00118):
writing A does not move the floor.

## PERFORMANCE (Tasks 4, 8) - MEASURED, paired against bracketing anchors

Every line is control (mean of the two anchors) minus the arm, in ray
milliseconds; the net delta also charges the arm any change in its field
build. Run 1 first, run 2 in parentheses.

| consumer | arm | pose | 22-23. delta ms | 24. ratio | 25. spread | net delta |
|---|---|---|---|---|---|---|
| refinement | `t202_refine_field` | SIDE | **0.54** (0.63) | 1.042 (1.050) | 0.7% (0.4%) | 0.47 (0.86) |
| | | PLAY_VIS_NEAR | **0.86** (0.67) | 1.072 (1.057) | 1.4% (5.5%) | 0.79 (0.68) |
| primary | `t202_primary_field` | SIDE | 1.82 (1.62) | 1.157 (1.141) | 0.7% (1.4%) | 1.86 (1.52) |
| | | PLAY_VIS_NEAR | 2.12 (1.54) | 1.198 (1.144) | 1.4% (1.1%) | 2.12 (1.57) |
| primary + refinement (measured, not summed) | `t202_both_field` | SIDE | 1.93 (1.82) | 1.173 (1.162) | 3.3% (0.6%) | 2.17 (1.81) |
| | | PLAY_VIS_NEAR | 2.82 (2.28) | 1.283 (1.225) | 1.3% (0.8%) | 2.63 (2.25) |
| T201 primary ceiling, re-timed | `t201_primary_field` | SIDE | 2.26 (2.01) | 1.207 | 3.2% | |
| | | PLAY_VIS_NEAR | 3.03 (2.24) | 1.312 | 4.7% | |
| T181 refinement ceiling, re-timed | `t181_norefine` | SIDE | 0.75 (1.00) | 1.061 | 1.4% | |
| | | PLAY_VIS_NEAR | 1.07 (0.84) | 1.093 | 3.5% | |
| T201 pair ceiling, re-timed | `t201_primary_norefine` | SIDE | 2.96 (2.96) | 1.295 | 0.8% | |
| | | PLAY_VIS_NEAR | 3.72 (2.89) | 1.413 | 1.9% | |

The primary-from-field takes 80% of its own ceiling (1.6-2.1 of 2.0-3.0 ms)
- the noise it keeps is the difference - and the refinement 60-80% of its
(0.5-0.9 of 0.75-1.1), with 14% more primary calls and 40% more scans
because the floored clearance advances less than the exact one. The pair
is sub-additive as T201 found.

26. **Field build impact: none.** The build writes A from the walk it
already does (`paLastStormMinClearance` is published by the walk, the
dilation is the probe's); the arm and control builds differ by the
session's own build noise (1.65-2.26 ms across anchors, the 20% T199
noted).

## POST-T202 (Task 10) - MEASURED, `t202b-run1.log` / `t202b-run2.log`, FINAL at bits 43

Two launches of the same seven-arm campaign (FINAL anchors bracketing
`t182_norainseg`, three repeats), because the first one's untimed build
read 4.3-4.6 ms on FINAL against 1.65-2.3 in the T202 runs for a
byte-identical program. Four FINAL anchors a pose a run:

| pose | run | 27-28. field build | ray p50 / p95 | composite | **combined** | anchor spread |
|---|---|---|---|---|---|---|
| SIDE | 1 | 4.50 | 12.72 / 13.46 | 0.08 | 17.22 | 1.9% |
| SIDE | 2 | 2.26 | 13.30 / 14.12 | 0.08 | **15.56** | 5.1% |
| PLAY_VIS_NEAR | 1 | 4.45 | 12.62 / 13.29 | 0.08 | 17.06 | 8.2% |
| PLAY_VIS_NEAR | 2 | 2.07 | 13.77 / 14.85 | 0.08 | **15.84** | 4.7% |

The ray moved 5-9% between the two sessions and the build 2x, both in the
same direction as the whole frame (the frame remainder outside the ray was
1.2 ms in run 1 against 0.4 in the T202 runs): a session state, not the
program. Run 1's ray (12.72 / 12.62) is the T202 anchors (13.07-13.39 /
12.33-12.76) less the refinement's paired delta, as it should be; run 2's
session was slower for every arm, the rain arm included (5.28 vs 5.01,
4.41 vs 4.15). The banked saving is the paired ratio (1.042-1.072, spreads
under 1.5%), not a difference of absolute baselines across sessions, which
this campaign shows is a 5-9% number on the ray and a 2x number on the
build. **Raw 0.25 combined after T202: ~15.5-15.8 ms in a normal session,
~17 in a slow one; the distance to <= 8 ms raw is unchanged in kind - 7-8
ms, half the frame.** The build's 2x swing between programs in one session
(FINAL 2.2-2.3 against the rain arm's 0.9-1.4 in run 2, 4.5 against
2.1-2.8 in run 1) is the T199 compiler-artifact class on the build pass;
it is untimed in every paired number here and is noted, not chased.

## RAIN MEASUREMENT (Task 11) - MEASURED, `t182_norainseg` on the post-T202 FINAL

Measurement only: `rainSegmentMayContribute` compiled out uniformly (the
segment test and the two support walks it drives, and with them every rain
shaft sample - the arm is image-invalid wherever rain contributes and is a
ceiling on the rain family, not a design).

| pose | run | control ray | arm ray | **29-30. delta ms** | ratio | spread | blocks |
|---|---|---|---|---|---|---|---|
| SIDE | 1 | 12.69 | 5.01 | **7.68** | 2.53x | 1.3% | 3 |
| SIDE | 2 | 13.30 | 5.28 | **8.01** | 2.52x | 2.0% | 2 |
| PLAY_VIS_NEAR | 1 | 12.39 | 4.15 | **8.23** | 2.98x | 1.8% | 2 |
| PLAY_VIS_NEAR | 2 | 13.62 | 4.41 | **9.21** | 3.09x | 0.2% | 2 |

T201 priced the rain shaft alone at 2.5 ms and estimated the segment test
inside a 6.7 ms residual; the whole rain family measures **7.7-8.0 ms at
SIDE and 8.2-9.2 at PLAY_VIS_NEAR - 60-67% of the ray**. Without it the ray
is 4.2-5.4 ms and the combined frame ~6.5-7.5 ms: the temporal reopen
class, from one arm. (The T202B counter captures are the pre-field monolith
workload - the campaign has no counter phase, so they inherited the bits-0
reset T201 documented - and are not used here.)

## DECISION

31. **T202: PARTIAL PASS.** The refinement on the field (bit 32) is
    accepted: exact-class image (IoU 0.998, edge SSIM 0.98-1.00, thin
    0.98-1.00), a sound floor (the probe's dilation on the probe's
    constants, the oracle re-run on the candidate build), 0.54-0.86 ms at
    SIDE / PLAY_VIS_NEAR (0.63 / 0.67 on the repeat) - the **minor band**
    (0.5-1.0). The primary body on the field (bit 16) is **rejected on
    quality**: 1.6-2.1 ms - the excellent band on milliseconds - but thin
    retention 0.70-0.84, edge SSIM 0.68-0.89, IoU 0.96-0.97, 4.5-4.7% of
    primary samples disagreeing on whether material exists and a 3-4% loss
    of body mass, with the same boundary failure under the distance
    representation the oracle tested. Quality requirements for the body are
    stricter than for the probe, and the brief said not to approve from
    performance alone; this is that case.
32. **Realistic gain: 0.5-0.9 ms** banked (FINAL bits 43); the 1.6-2.1 ms
    of the primary is measured and documented as unreachable through the
    256 x 32 field at body accuracy. The primary architectural question -
    can the primary consume precomputed descriptor information instead of
    walking - is answered **no for this field**, twice: neither the
    threshold nor the distance survives interpolation at the boundary.
    What would reach it is exactness-preserving per-call work (fewer wasted
    exact SDFs: `wastedSdfFraction` is 0.53, half of every group walk's
    SDFs do not move the union), which is the T174-T179 line and not a
    field.
33. **Rain worth reopening strategically: YES.** The rain family is the
    frame: 7.7-9.2 ms of a 12.7-13.8 ms ray, three to four times every
    non-rain consumer together (T201: 3.8-4.0 ms), and the only line whose
    ceiling reaches the 6-8 ms class. The T182-T192 rain work (locality,
    reuse, pruning, the rain field, the closed-form bound) never priced the
    segment test as a whole; this number says the segment test's structure
    - two support walks a coarse step on every ray that passes the height
    gate - is where the frame is, and a rain architecture (a rain-owning
    column field, a segment classification per tile, or the field the T190
    line built, applied to the segment test rather than the shaft) is the
    next architecture worth a campaign. That is a strategic decision the
    brief reserved; the number is now measured on the shipped FINAL, twice.
34. **Temporal ready: NO.** Raw 0.25 combined is 15.5-15.8 ms (17 in a slow
    session) against the <= 8 ms reopen condition; non-rain work at zero
    would leave ~11 ms (T201), so nothing outside rain reaches it.
35. **Exact next task: T203, the rain segment test's architecture** -
    price and remove the per-step support walks behind
    `rainSegmentMayContribute` without changing rain's placement (the US2
    attachment path): a descriptor-derived rain-support field over columns
    (the T190 conservative field already classifies 83% of columns provably
    dry - `censusProvablyDryCells=107,878 of 129,600` in the T199 capture)
    consumed by the segment test per coarse step, with the exact walk only
    on mixed cells; quality harness on the rain mask (`t188_rain_mask_*`)
    and the cloud frame; target band >= 4 ms of the 7.7-9.2 ms ceiling. If
    the user keeps rain closed, the fallback is T174-T179's exactness-
    preserving walk (wasted exact SDFs, 53% of every group walk) for a
    sub-millisecond primary gain.

## VALIDATION

36. `./gradlew.bat campaignGate`: PASS three times (`gate-t202-campaign.log`
    after the arms, `gate-t202-campaign-2.log` after the oracle and T202B,
    `gate-t202b-campaign.log` after the FINAL bake: `T170_WIRING
    campaigns=45 violations=0`, T191 PASS, `T199_FINAL_FIELD bits=43
    comparatorIdentical=true`, `T202_BODY_FIELD arms=6`).
37. `./gradlew.bat check build`: PASS (`gate-t202.log`, 48 invariants,
    jar).
38. **Consumer accounting**: `shapeAccountingClosed=true` on every capture;
    `stormFieldPrimaryFetches` / `stormFieldRefineFetches` and their
    fallbacks are new counters on view 70 (`STORM_WORKLOAD_STORM_FIELD_D`,
    capture stage 47), and the capture guard covers it (the T175 invariant
    failed the build until it did).
39. **Workload views**: 22-26, 28-70 enabled; the T123 instrumentation
    invariant extended to mask `#ifdef PA_PRIMARY_AUDIT` the way it masks
    the rain-mask and slice oracles (the accumulators exist only in the
    audit programs, which the T202 invariant asserts FINAL does not define).
40. **Campaign registry**: `T202` (`t202-primary-traversal.txt`,
    `primaryTraversalRunRequested`, `t202Run`, `T202_ARMS`,
    `T202_OPTIMIZATION_ARMS`, `T202_IMAGE_ARMS`) and `T202B`
    (`t202b-post-baseline.txt`, `postBaselineRunRequested`, `t202bRun`,
    `T202B_ARMS`, no image arms).
41. **Variant scope**: six campaign-scoped programs (`t202_primary_field`
    27, `t202_refine_field` 43, `t202_both_field` 59, `t202_primary_audit`
    27 + `PA_PRIMARY_AUDIT`, `t202_dinterp_oracle` and `t202_dinterp_audit`
    on production's bits + `PA_PRIMARY_DINTERP`); 165 declared, T202
    registered 7 (run 1) / 9 (run 2), T202B registered 1
    (`t182_norainseg`), FINAL and the monolith always loaded. FINAL bakes
    43 and is byte-identical to `t202_refine_field` (the sandbox asserts
    it); `t198_field_both_exact` (bits 11) is the T199-T201 FINAL and still
    compiles.
42. **Markers**: `run/t202-primary-traversal.txt` and
    `run/t202b-post-baseline.txt` retired to `.done`; `run/t132-autorun.txt`
    and `run/t132-autorun-exit.txt` remain.
43. `git status`: `build.gradle`, `cloud_atmosphere_volume.fsh`,
    `CoreCostDiagnosticProgram.java`, `StormCampaignRegistry.java`,
    `StormT132AutoDriver.java`, `StormWorkloadRuntimeCapture.java`,
    `VolumetricCloudRaymarchDebugView.java`, `VolumetricCloudRenderer.java`
    (the build draw now sees the consumer bits), `VolumetricCloudDebugConfig.java`,
    `StormFieldGeometry.java`, `StormVolumetricGeometrySandbox.java`,
    `tasks.md` modified; `StormPrimaryFieldAuditReport.java`, this report,
    `validation/t202-campaign/` and the two launcher `.cmd` files new;
    unrelated untracked logs preserved.
44. Committed on `experiment/cloud-descriptor-k`; not merged.

Harness notes: the detached campaign launch needs the full path to
`gradlew.bat` inside the hidden cmd (a bare name is "not recognized" even
with the right working directory); the launcher scripts in the tree are
that recipe. The T202 pocket images are written per run and were archived
with a `.run1` / `.run2` suffix.
