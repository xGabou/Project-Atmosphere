# T218 - Buried-Interior Structure / Diffusion Limit

**The question:** is the structureless buried view a sampling problem we can fix cheaply, or a
real visibility limit we should accept?

**The answer: a real visibility limit. DISPROVE (CASE B).** The premise that more local sampling
would reveal structure is not merely unsupported - it is wrong with its sign reversed. The
production two-sample estimator is the *noisiest* of the four tested, and removing its noise makes
the buried view smoother still.

## RECOVERY

1. Starting HEAD: `f36cf29` (T217, "restore indirect light to deep cloud interiors, gated on
   camera embedding").
2. Branch: `quality/t217-deep-interior-lighting`, continued.
3. Production state verified at entry and unchanged by this task:
   `PRODUCTION_TEMPORAL_MODE=1`, `PRODUCTION_SPATIAL_UPSCALE=-1`, `PRODUCTION_RAIN_LATTICE_MODE=0`,
   `PRODUCTION_ORIGIN_JITTER_MODE=0`, `PRODUCTION_MACRO_ENVELOPE_MODE=3` (T213),
   `PRODUCTION_LOWER_BODY_MODE=1` (T215), `PRODUCTION_DEEP_SCATTER_MODE=1` (T217).
   New `PRODUCTION_INSIDE_STRUCTURE_MODE=0`, which is the production estimator.
   Working tree carried only the preserved `PAchangelog.md` user modification.
- `campaignGate` PASS before any client work (`gate-t218.log`); the full four-run record is
  item 32.
- Live tuning read from the run: `densityMul=1.45 coverage=1.25 extinction=0.115`,
   `resolutionScale=0.750`, slab 130.0..1013.4, 10 descriptors,
   fixture `5a245619` fingerprint `90edba20aa99b2a2`, horizontal radius 670.1.

### The inside estimator, as it actually is

```glsl
if (cameraInsideCloud) {
    float forwardDensity = cloudDensity(p + LightDir * 28.0, 1.2, false, false, false);
    return localDensity * 18.0 + forwardDensity * 82.0;
}
```

4. Two samples, no march. `cameraInsideCloud = cameraStartsInsideSlab && CameraCloudDensity > 0.08`.
5. Sample positions and what each one is:

| | s0 | s1 |
|---|---|---|
| offset along `LightDir` | 0 | **28 blocks** |
| weight | 18 (**18%**) | 82 (**82%**) |
| LOD | the primary sample's own | **1.2** |
| detail octaves | yes | **no** - `useDetail = false` |
| mean density at the pose (sun 45 deg) | 0.6547 | 0.4829 |

   The dominant term is a mip-biased probe 28 blocks up-sun that **does not consult the detail
   octaves at all**, so 82% of the buried camera's optical depth is computed from a field with the
   erosion band removed. That is the estimator's real shape, and it is not what "two samples
   averaging the neighbourhood" would suggest.

6. Local density variance around each sample, mean SD in a ball of radius 4 blocks over 250
   shaded points: **s0 0.03220, s1 0.04645**. World-space separation **28 blocks**.
   Total optical depth at the pose: raw 51.4, scaled `x ExtinctionScale` **5.91**, direct
   transmittance `exp(-5.91) =` **2.7e-3**.

## TASK 1 - ATTRIBUTION: DOES THE ESTIMATOR AVERAGE LOCAL VARIATION AWAY?

**No. It injects variation that is not physically there, and none of it reaches the frame anyway.**

Measured on the production density path (`StormT218InteriorStructureSandbox`, STEP D), over 2000
points a buried view ray actually shades, against a 1-block-step integral of the same 100-block
path:

| sun elev | estimator | od mean | od SD | corr vs full | RMS err vs full |
|---|---|---|---|---|---|
| 20 | full (100 taps) | 41.31 | 6.74 | 1.000 | 0.00 |
| 20 | **2 (production)** | 51.83 | **11.43** | **0.284** | **15.59** |
| 20 | 4 (diagnostic) | 41.60 | 6.83 | 0.877 | 3.38 |
| 20 | 8 (diagnostic) | 41.27 | 6.63 | 0.991 | 0.90 |
| 45 | full | 52.02 | 7.93 | 1.000 | 0.00 |
| 45 | **2 (production)** | 51.41 | **12.65** | **0.410** | **11.88** |
| 45 | 4 | 51.31 | 8.53 | 0.933 | 3.16 |
| 45 | 8 | 51.86 | 7.94 | 0.992 | 1.00 |
| 70 | full | 67.75 | 6.79 | 1.000 | 0.00 |
| 70 | **2 (production)** | 63.76 | **11.28** | 0.813 | 8.04 |
| 70 | 4 | 66.88 | 6.91 | 0.931 | 2.69 |
| 70 | 8 | 67.66 | 6.74 | 0.995 | 0.65 |

The production estimator's optical depth has **60% more spread than the truth** and correlates
with it at only 0.28-0.41 away from a high sun. More taps *reduce* the spread toward the true
value. So the smoothing hypothesis is backwards.

**Why 28 blocks is the wrong offset.** Density autocorrelation along the light direction, measured
over the same point set:

| separation (blocks) | 2 | 4 | 8 | 14 | 20 | **28** | 40 | 60 | 100 |
|---|---|---|---|---|---|---|---|---|---|
| correlation | 0.972 | 0.902 | 0.682 | 0.335 | 0.092 | **-0.201** | -0.396 | 0.059 | 0.080 |

The probe sits past the zero crossing (~20 blocks) and on the way down to the anti-correlation
trough at 40. An 82%-weighted sample at an anti-correlated offset is a noise source by
construction.

**And it does not matter, because the lighting no longer responds to optical depth at that
depth.** Swept through the shader's own `evaluateLightingComponents` over the same 27-point
`h01`/`combinedStorm`/`materialDarkness` grid T217 used:

| od scaled | od raw | d(8bit)/d(od) | d(8bit)/d(density) | 8-bit span over density 0.30-0.95 |
|---|---|---|---|---|
| 0.5 | 4 | **-51.88** | -8.14 | 5.26 |
| 1.0 | 9 | -42.17 | -7.87 | 5.09 |
| 2.0 | 17 | -18.44 | -6.92 | 4.48 |
| 3.0 | 26 | -4.03 | -5.71 | 3.70 |
| 5.0 | 43 | -0.25 | -4.47 | 2.90 |
| **5.9 (the buried pose)** | **51** | **-0.065** | **-4.18** | **2.71** |
| 8.0 | 70 | -0.14 | -3.76 | 2.44 |
| 20.0 | 174 | -0.11 | -3.25 | 2.11 |

**The optical-depth response collapses by a factor of 800 between od 0.5 and od 5.9.** Once
`exp(-od)` is 2.7e-3 the direct term and all three scatter octaves are gone and od reaches the
frame only through an ambient-retention factor already at its floor. Converting:

| input | SD as measured | per-sample 8-bit | after the view-ray integral (x0.111) |
|---|---|---|---|
| od, 2-sample estimator | 1.455 scaled | 0.092 | **0.010** |
| od, full march | 0.912 scaled | 0.058 | 0.006 |
| od, 8-sample | 0.913 scaled | 0.058 | 0.006 |
| density, visible (transmittance-weighted) | 0.0327 | 0.136 | **0.015** |

Every one of these is one to two orders of magnitude below a single 8-bit level. **The estimator
cannot move the buried frame whatever it does.** That is a pre-registered prediction, and Task 3
below is the test of it.

*The CPU mirror's absolute level is not load-bearing*, exactly as T217 established: it predicts
~86 8-bit where the shader measures 121.8. The direction of that error makes the conclusion
conservative - the tone curve `1 - exp(-r*1.30)` is flatter at 122 than at 86, so the real slopes
are about 0.79x these, not larger.

## TASK 2 - THE VISIBILITY LIMIT

Measured on the real baked noise and the real ten-member severe fixture
(`stormT218InteriorStructureSandbox`).

### Attenuation actually in force

| pose | mean density over 60 blocks | sigma /block | 1/e blocks | 95% blocks | to alpha 0.99 |
|---|---|---|---|---|---|
| `4_INSIDE` | 0.5671 | 0.09457 | **10.57** | 31.72 | 48.70 |
| `4B_THROUGH_BODY` | 0.3263 | 0.05442 | 18.38 | 55.13 | 84.63 |

**Correction to the number this program has been quoting.** T216 and T217 both cite 14.7
   blocks, derived from a single point density of 0.407. Along the directions a ray actually
   travels the mean is 0.567, so the true 1/e visibility at the buried pose is **10.6 blocks**,
   not 14.7. The buried view is ~28% *more* confined than the program assumed.

### Density variance by window size

7. Density SD inside a window, bands isolated by holding the other noise input at its
   window-centre value:

| window (blocks) | sd total | sd base band | sd detail band | detail share of banded variance |
|---|---|---|---|---|
| 5 | 0.03715 | 0.03443 | 0.01355 | 13.4% |
| **10 (~one attenuation length)** | **0.07081** | 0.06638 | 0.02190 | 9.8% |
| 15 | 0.09767 | 0.09296 | 0.02711 | 7.8% |
| 30 | 0.14836 | 0.14629 | 0.03177 | 4.5% |
| 60 | 0.19347 | 0.19126 | 0.05943 | 8.8% |
| 120 | 0.24029 | 0.23723 | 0.11155 | 18.1% |
| 240 | 0.24961 | 0.24774 | 0.13152 | 22.0% |

The field's full SD is 0.250. Inside one attenuation length only 0.071 of it exists - **28% of the
amplitude, 8% of the variance**. This is a property of the field's own design, recorded in
`StormMorphologyThresholds`: the base carrier's measured feature size is **109.4 blocks** and it
carries 99% of the density SD, while the detail octaves that live at 5.7-22.7 blocks carry
`STORM_EROSION * DETAIL_FBM_SD = 0.44 * 0.0742 = 0.033`. The amplitude is at 109 blocks; the
visibility ends at 10.6. **The overlap is empty, and that is the whole finding.**

### What survives the ray integral

A view ray sees `sum(T_i * (1-exp(-sigma_i ds)) * d_i) / sum(weights)`. The spread of that across
rays is the hard ceiling on image contrast for any shading term that reads local density:

| pose | band | point SD | visible mean | visible SD | **retention** | effective depth |
|---|---|---|---|---|---|---|
| `4_INSIDE` | FULL | 0.29423 | 0.68902 | **0.03265** | **0.1110** | 8.82 |
| `4_INSIDE` | base only | 0.28756 | 0.67266 | 0.02733 | 0.0950 | 9.04 |
| `4_INSIDE` | detail only | 0.03152 | 0.69621 | 0.01464 | 0.4644 | 8.58 |
| `4B_THROUGH_BODY` | FULL | 0.23788 | 0.42689 | **0.08710** | **0.3662** | 16.23 |
| `4B_THROUGH_BODY` | base only | 0.22874 | 0.40304 | 0.08920 | 0.3900 | 18.03 |
| `4B_THROUGH_BODY` | detail only | 0.21298 | 0.43199 | 0.01445 | 0.0679 | 13.87 |

**The buried view throws away 89% of the field's contrast before any shading runs; the
through-body view throws away 63%.** That 3.3x difference in retention, on the same storm with the
same shader, is the mechanism behind the 18x difference in their measured local contrast.

The reason a 109-block feature does not save the buried view is geometric rather than spectral: a
ray integral along one direction passes low frequencies happily, but **pixel-to-pixel contrast is
the variation *between* directions inside the visible ball**, and a 109-block feature is
essentially constant across a ball of radius 10.

## TASK 3 - NEGATIVE CONTROL: MORE LOCAL SAMPLES

A live uniform `PaInsideStructure` replaces the two-sample estimate with trapezoid quadrature over
the same 100-block nominal path, so the total weight, extinction, every lighting constant and the
T217 compensation are untouched and **only the sample count changes**. Density is not modified.

- S0 = production, 2 samples.
- S1 = 4 taps, detached taps at the production probe's LOD 1.2.
- S2 = 8 taps, same LOD.
- S3 = 8 taps at **full detail (LOD 0, `useDetail = true`)** - deliberately confounding tap count
  with detail level, as the most generous possible version of the hypothesis.

All four captured **back to back at the same pose in one run** (the T217 lesson), with
`deepScatter=1` throughout, confirmed per frame in `T098_CAPTURE_CONTROLS`.

### `4_INSIDE`, camera density 0.70003

| arm | mean | sd | localSD | sd low | sd mid | sd high | dark% | pockets | acLen | gradE |
|---|---|---|---|---|---|---|---|---|---|---|
| **S0 (2, production)** | **121.81** | 3.535 | **0.2856** | 3.483 | **0.2309** | 0.2731 | 0.00 | **0** | 12 px | 0.1056 |
| S1 (4) | 121.92 | 2.731 | 0.2791 | 2.697 | 0.1592 | 0.2831 | 0.00 | 0 | 4 px | 0.1105 |
| S2 (8) | 121.79 | 2.580 | 0.2826 | 2.547 | 0.1562 | 0.2870 | 0.00 | 0 | 4 px | 0.1125 |
| S3 (8, full detail) | 121.47 | 5.157 | 0.2539 | 5.128 | 0.1653 | 0.2276 | 0.00 | 0 | 9 px | 0.0936 |

**Does increased local sampling reveal coherent structure? No.**

8. Two-sample baseline structure: localSD **0.2856**, mid-band SD 0.2309, high-band SD 0.2731,
   pocket fraction **0.00%**, zero pockets, gradient energy 0.1056.
9. Four-sample result: localSD **0.2791** (-0.0065), mid-band **0.1592 (-31%)**, pockets still
   0.00%, autocorrelation length **12 px -> 4 px**.
10. Eight-sample result: localSD **0.2826**, mid-band **0.1562 (-32%)**, pockets 0.00%,
    autocorrelation 4 px. The full-detail arm S3: localSD **0.2539 (-11%)**, mid **0.1653**,
    high **0.2276 (-17%)**, gradient energy **0.0936 (-11%)**.

Every arm is **smoother** than production on local contrast, and the mid-frequency band - the one
the estimator's own noise lives in - drops by about a third, which is exactly the measured removal
of the 2-sample estimator's spurious od spread (12.65 -> 7.94 raw). The autocorrelation length
falling from 12 px to 4 px means the residual became *less* spatially organised, not more.

S3's whole-frame `sd` rises to 5.157, and that is not structure: its `sd low` rises in lockstep
(3.483 -> 5.128) while localSD, mid and high all fall. Eight full-detail taps along `LightDir`
produce a smooth large-scale luminance ramp across the frame. The pocket count is zero in all four
arms.

**Measurement floor.** At the six poses where the arms are provably unreachable
(`CameraCloudDensity = 0.00000`, so `cameraInsideCloud` is false), the S0-vs-S3 deltas give the
harness's own floor: `|d mean| <= 0.029` and `|d localSD| <= 0.0032`. The `4_INSIDE` mid-band drop
of -0.072 is well above that floor and is a real effect - in the smoothing direction. The localSD
deltas sit at or just above it.

**Visual check.** `T218_S0_INSIDE` and `T218_S3_INSIDE` are both a uniform grey-blue fog with one
faint wisp near the top; the wisp is if anything slightly weaker in S3. The same run's
`T218_S0_THROUGH` - the same storm, the same shader, a camera in clear air - shows billows,
dark pockets, rain shafts and a hard silhouette. The metric and the eye agree.

**Sampling is not the missing piece.** The prediction in Task 1 (an estimator change is worth
<=0.01 8-bit at this pose) is confirmed: every arm's mean sits within 0.33 of production and no
contrast metric rises.

## TASK 4 - SPATIAL SCALE ANALYSIS

More samples revealed no new variation, so this measures the variation that *is* there, as an
angular quantity - which is what a frame shows. A 96x96 patch of directions is traced like a
camera frustum and the autocorrelation of visible density across it gives the angular scale;
multiplying by the effective depth converts it to world space.

| pose | visible mean | visible SD | relative SD | effective depth | 1/e angular lag | **world scale** |
|---|---|---|---|---|---|---|
| `4_INSIDE` | 0.71184 | 0.02046 | **2.87%** | 8.51 | 23.3 deg | **3.47 blocks** |
| `4B_THROUGH_BODY` | 0.43931 | 0.05123 | **11.66%** | 16.66 | never within 23.3 deg | **> 6.8 blocks, coherent past the window** |

11. **Dominant visible spatial scale at the buried pose: 3.5 blocks - sub-5-block noise**, at 2.9%
    relative contrast. The through-body pose, by contrast, stays correlated across the entire
    measurement window, which is what coherent macro structure looks like.

This is the band Task 4 names as the one not to add: sub-5-block variation that exists in the
density field but is not what a viewer reads as cloud structure. Adding a frequency band merely
because it exists in the field is precisely what would be happening.

## TASK 5 - CANDIDATE

**None is proposed, and attribution does not support one.**

The ceiling for any term that reads local density at this pose:

```
visible density SD 0.03265  x  d(8bit)/d(density) 4.169  =  0.136 8-bit
```

and the entire 8-bit budget across the full density span 0.30-0.95 at that optical depth is
**2.71 levels**. To reach a local contrast of 2.0 8-bit - still a quarter of what `4B` shows - the
density-to-luminance response would have to be **14.7x** the physical one; to match `4B`'s measured
5.1, **36.7x**.

That is not a physical fix. It is amplifying a 2.9% signal at a 3.5-block scale by an order of
magnitude, which is the "fake detail" the task forbids under a different name. The four candidate
shapes the task lists fare as follows, each ruled out by a measurement above rather than by
preference:

- **A, a better analytic local-density estimator** - ruled out by Task 3: the better estimators
  were built and measured, and all three are smoother than production.
- **B, bounded local variance preservation** - there is 0.0327 of visible density SD to preserve
  and it is worth 0.136 8-bit; preserving it perfectly changes nothing.
- **C, local directional lighting variation** - `d(8bit)/d(od)` is -0.065 here, an 800-fold
  collapse from od 0.5. Directional variation has no channel to the frame.
- **D, a low-cost multi-sample approximation** - measured at 24-62 ms (below), and it removes
  contrast rather than adding it.

## TASK 6 - CAMERA-INSIDE LOCALIZATION

The arms live entirely inside the existing `if (cameraInsideCloud)` branch, so they are
unreachable anywhere else by construction. Confirmed per frame: `CameraCloudDensity` logged with
every capture reads **0.70003 at `4_INSIDE`** and **0.00000 at all six other poses**.

**A pose that did not work, reported as a negative result.** A static camera-density boundary pose
was added at 0.98 of the fixture radius, intended to sit in the T217 gate's 0.05-0.16 band. It
reads `CameraCloudDensity = 0.00000` - on this fixture the occupied body at that height ends well
inside 0.98 R, so the pose is in clear air. Picking a gate-band pose by radius fraction does not
work; the band has to be swept, which is what the T218M route below does.

The same measurement corrects a description this program has carried: **`4B_THROUGH_BODY` is not a
camera-inside-cloud pose on this fixture** (`CameraCloudDensity = 0.00000`). It has high view-ray
optical depth from outside. That is consistent with T217's negative control, where the uncapped
light cone moved `4B` but was bit-identical at `4_INSIDE`.

## TASK 7 - SAME-STORM ARMS

All four arms captured adjacent at `4_INSIDE`; S0 and S3 adjacent at each of the six other poses.
One fixture (`5a245619`, fingerprint `90edba20aa99b2a2`, 10 descriptors throughout - no
`T098_CAPTURE_FIXTURE_LOST`), one run, one storm.

## TASK 8 - REQUIRED VIEWS AND COLLATERAL

19. `4B_THROUGH_BODY` collateral, S0 -> S3: mean **148.43 -> 148.43**, localSD **5.1030 ->
    5.1031**, mid-band 8.4264 -> 8.4277, dark pockets 3.44% -> 3.44%, bright 3.31% -> 3.31%,
    autocorrelation 13 px unchanged, gradient energy 1.2019 -> 1.2021.
20. Exterior collateral, S0 -> S3:

| view | mean | d mean | localSD | d localSD | dark% | d dark% |
|---|---|---|---|---|---|---|
| SIDE | 137.37 | **-0.029** | 6.9120 | +0.0007 | 8.05 | +0.003 |
| LATERAL | 191.60 | **+0.001** | 1.3973 | -0.0001 | 2.25 | +0.001 |
| NEAR_EDGE | 133.70 | **-0.002** | 4.2410 | -0.0008 | 4.05 | -0.003 |
| ABOVE | 108.85 | **-0.000** | 1.0774 | -0.0001 | 0.29 | -0.000 |
| GATE_EDGE (0.98 R) | 141.67 | **+0.015** | 3.9764 | +0.0032 | 2.66 | -0.002 |
| 4B_THROUGH_BODY | 148.43 | **+0.001** | 5.1030 | +0.0002 | 3.44 | +0.005 |

Every exterior view is unchanged below quantization. These are not bit-identical only because the
temporal reconstruction is not frame-deterministic; the spread across six provably-unreachable
poses is what establishes the 0.03 8-bit floor used above.

## TASK 9 - STRUCTURE METRICS

Reported in the Task 3 table. In summary, for `4_INSIDE`:

12. Luminance mean: **121.81** (S0), 121.92 / 121.79 / 121.47.
13. Luminance SD: **3.535** (S0); 2.731 / 2.580 / 5.157, the last being a low-frequency ramp.
14. Local contrast (`localSD`, 17x17): **0.2856** (S0), never higher in any arm.
15. Pocket fraction: **0.00% dark, 0.00% bright, in every arm.** Zero connected components.
16. Pocket scale: undefined - there are no pockets to size.
17. Spatial autocorrelation of the mid+high residual: **12 px** (S0), falling to 4 px with more
    taps; the world-space angular measurement gives **3.47 blocks**.
18. Gradient energy: **0.1056** (S0), 0.1105 / 0.1125 / 0.0936.

Low-frequency contrast 3.483, mid 0.2309, high 0.2731 in production. For scale: the same metrics
at `4B_THROUGH_BODY` are low 18.31, mid **8.43**, high 3.18, with 265 dark pockets of mean
diameter 7.1 px. The buried pose's mid-band contrast is **36x smaller**.

None of the arms produced coherent spatial organisation. The goal was never a higher SD, and no
arm delivered even that.

## TASK 10 - GATE-EDGE STRESS TEST

### A harness defect found first, which changes how T217's motion result should be read

`StormT152MovingCameraFixture` reads back the cloud target and keeps **only the alpha channel**
(`alpha[index] = readbackBuffer.get(index * 4 + 3)`). `flicker`, `dMean`, `dMax`, `flipOn` and
`flipOff` are all computed from it.

**The T217 term adds radiance only and cannot move alpha.** Its motion validation - "route flicker
0.0023 -> 0.0023, ENTRY flicker 0.0017 -> 0.0017" - was therefore identical *by construction*,
not by measurement. That metric could not have detected a brightness pop if there had been one.
T217's conclusion may still be right; it was not tested.

This task adds straight-luminance metrics beside the alpha ones: `lumaMean`, `dLumaMean`,
`dLumaMax`, `lumaPop` (fraction of pixels moving more than 0.010, about 2.5 8-bit levels in one
frame) and `dLuma2Mean` / `dLuma2Max`, the second difference, which is the derivative
discontinuity the task asks for. Colour is un-premultiplied (`rgb / alpha`) and every difference is
restricted to pixels covered in **all** the frames it spans, so a disocclusion cannot masquerade as
a brightness pop.

### The route

`T218M`, a new `GATE_EDGE` route shape: a slow orbit whose radius sweeps 0.50-1.34 R across the
body boundary **14 times**, so the camera enters and leaves cloud repeatedly instead of once. It
uses the same constant-angular-step reparameterisation as the production route, so per-frame screen
motion is comparable and the difference is the number of crossings, not the speed of one.

`T218M`, 2200 frames per arm, three arms on one fixture (`c6011c9f`, fingerprint
`8e133c5ab4856b9e`), route steps 3.30-16.42 blocks per frame. The unconfounded comparison is
`deep_scatter_off` vs `deep_scatter_on`, identical in every temporal setting and differing only in
the T217 term; `production_050` runs at a different internal scale and is the ghost reference only.

**The route genuinely stresses the gate**, far harder than T217M's single entry:

| arm | frames | camDensity mean | max | below 0.05 | **IN GATE** | above 0.16 | lower-edge crossings | max per-frame `d camDensity` |
|---|---|---|---|---|---|---|---|---|
| production_050 | 2200 | 0.0687 | 0.6474 | 1492 | 373 | 335 | 25 | **0.1776** |
| deep_scatter_off | 2200 | 0.0691 | 0.5980 | 1397 | 477 | 326 | 28 | 0.1109 |
| deep_scatter_on | 2200 | 0.0696 | 0.5819 | 1367 | 513 | 320 | 26 | 0.1109 |

The largest single-frame camera-density step is **0.111-0.178**, which equals or exceeds the whole
width of the gate band (0.16 - 0.05 = 0.11): at the fastest crossings the gate goes from fully
closed to fully open **within one frame**. T217M, by comparison, entered cloud once over hundreds
of frames.

21. **Gate-edge stress result: the gate does not pop.** Per-frame luminance change binned by
    camera density, `off` -> `on`:

| camDensity bin | n | dLumaMean off | dLumaMean on | delta | dLuma2Mean off | on | delta |
|---|---|---|---|---|---|---|---|
| 0.00-0.02 (gate closed) | ~1030 | 0.01875 | 0.01874 | **-0.00001** | 0.03350 | 0.03236 | -0.00114 |
| 0.02-0.05 (gate closed) | ~350 | 0.01484 | 0.01581 | +0.00097 | 0.03487 | 0.03987 | +0.00501 |
| **0.05-0.08 (gate opening)** | ~205 | 0.02445 | 0.02292 | **-0.00152** | 0.05932 | 0.05450 | -0.00482 |
| **0.08-0.11 (gate mid)** | ~144 | 0.02267 | 0.01938 | **-0.00329** | 0.05469 | 0.05338 | -0.00131 |
| **0.11-0.16 (gate closing to 1)** | ~146 | 0.02618 | 0.01882 | **-0.00736** | 0.05261 | 0.04800 | -0.00461 |
| 0.16-0.25 (gate open) | ~150 | 0.02255 | 0.01186 | **-0.01070** | 0.04998 | 0.03598 | -0.01400 |
| 0.25-0.45 | ~150 | 0.03087 | 0.01518 | **-0.01569** | 0.05355 | 0.03159 | -0.02196 |
| 0.45-1.01 | ~22 | 0.05558 | 0.02562 | **-0.02996** | 0.10220 | 0.05402 | -0.04817 |

    Two things to read here. First, **below the gate the delta is -0.00001**, which is the
    localization proving itself: where `camGate` is exactly 0 the term changes nothing. (The
    0.02-0.05 bin's +0.001 / +0.005, also below the gate where the term is off, is therefore a
    direct read of the arm-to-arm noise floor.) Second, above the gate the term **reduces**
    frame-to-frame luminance change, monotonically with camera density, to -54% at the deepest
    embedding. It stabilises the interior rather than destabilising it - which is what a bounded,
    saturating fill that lifts dark pixels toward a ceiling should do.

**At the crossing frames specifically** (every frame within +/-3 of an edge crossing):

| edge | arm | crossings | frames | dLumaMean | dLuma2Mean | flicker (alpha) | lumaMean |
|---|---|---|---|---|---|---|---|
| lower 0.05 | off | 28 | 188 | 0.04318 | 0.07607 | 0.02948 | 0.37732 |
| lower 0.05 | **on** | 26 | 182 | **0.03816** | **0.06795** | 0.03185 | 0.40697 |
| upper 0.16 | off | 22 | 134 | 0.04275 | 0.07035 | 0.03767 | 0.41637 |
| upper 0.16 | **on** | 18 | 122 | **0.02869** | **0.05431** | 0.03507 | 0.51563 |

    And the excess a pop would produce - crossing frames against that arm's own route average:

| arm | dLumaMean crossing / all | dLuma2Mean crossing / all |
|---|---|---|
| deep_scatter_off | **1.766** | **1.564** |
| deep_scatter_on | **1.644** | **1.481** |

    **The arm that has the gate shows a smaller crossing excess than the arm that does not.** The
    ~1.7x elevation is present in both arms and is a property of the route - crossing a cloud
    boundary is a large geometric change - not of the gate. Derivative discontinuity, measured as
    the second difference, is likewise lower with the term on at both edges.

The alpha metric behaves exactly as predicted: `flicker` at the crossings moves 0.02948 ->
    0.03185 at one edge and 0.03767 -> 0.03507 at the other, i.e. +8% and -7% in opposite
    directions, which is noise around a quantity the term cannot affect. Over the whole route it
    is 0.02475 -> 0.02478. **That is the metric T217 used to clear its gate.**

**No CASE D.** The T217 gate is stable under a camera that crosses it 26-28 times per arm, at up to
a full band width per frame, and the instrument used to say so is one that responds to brightness.


## TASK 11 - MOTION VALIDATION

Both routes required by the task were run on the same candidate-free production shader.

22. **Normal-route flicker:** the T217M route's own numbers stand (route flicker 0.0023 off ->
    0.0023 on, ENTRY 0.0017 -> 0.0017) but are now known to be **alpha-only and therefore
    uninformative about a radiance-only term**. Rather than re-report them as evidence, T218
    replaced the instrument and re-ran the same arms on a harder route. The normal route's
    contribution to this task is the 0.00-0.05 bins above, where the camera is outside cloud and
    the term is provably off: `dLumaMean` delta **-0.00001**.

23. **Gate-route flicker:** item 21. Self-referential, every metric measured against the arm's
    own previous frame, never against the other arm's.

24. **Structure crawl: not applicable and not measurable, because no structure was added.** T218
    productionizes nothing; the buried view's mid-band contrast is 0.16-0.23 8-bit in every arm
    tested and its pocket count is zero, so there is no feature that could crawl. The nearest
    quantity that exists - `lumaPop`, the fraction of pixels moving more than 2.5 8-bit levels in
    one frame - falls from 37.48% to 34.78% over the route and from 35.69% to 34.34% inside the
    gate band. Its absolute level is high because the route moves the camera 3.3-16.4 blocks per
    frame at 0.25 internal scale with history disabled; only the arm delta is meaningful, and it
    is favourable.
- **Interior shimmer / brightness popping:** covered by item 21. Both metrics down.
- **Ghost / disocclusion:** not reported. The reference arm runs at a different internal scale,
    so a ghost comparison against it carries the usual appearance confound with no offsetting
    value here - there is no candidate whose reprojection behaviour is in question.


## TASK 12 - PERFORMANCE

Capture-pin values (spectator, resized window, ULTRA capture quality) - **not shipping frame
times**. Only matched-pose deltas within this run are meaningful, as T217 established.

| pose | S0 | S1 (4 taps) | S2 (8 taps) | S3 (8, full detail) |
|---|---|---|---|---|
| **`4_INSIDE`** | **17.41 ms** | **41.83 (+24.42)** | **71.21 (+53.80)** | **79.07 (+61.66)** |
| `4B_THROUGH_BODY` | 54.34 | - | - | 54.03 (-0.32) |
| GATE_EDGE | 52.14 | - | - | 52.74 (+0.60) |
| SIDE | 29.42 | - | - | 28.19 (-1.23) |
| LATERAL | 23.19 | - | - | 22.81 (-0.38) |
| NEAR_EDGE | 45.62 | - | - | 45.51 (-0.10) |
| ABOVE | 25.58 | - | - | 25.48 (-0.10) |

25. SIDE cost: **-1.23 ms**, i.e. noise - the arms are unreachable there (`CameraCloudDensity`
    0.00000), so the true delta is zero and this is the pose-to-pose measurement spread.
26. PLAY_VIS_NEAR is a member of the resolution-ladder shot set, not this one, so no paired
    PVN arm exists. The equivalent production number from this run's own gameplay phase
    (spectator at y=108.6, storm in frame at `cloudCover=1.000`, `resolutionScale=0.750`) is
    **gpuMs 1.507-1.588, p50 1.541** over 15 status samples. The arm delta there is zero by
    construction: `cameraInsideCloud` is false at gameplay altitude below the slab.
27. `4_INSIDE` cost: **+24.4 / +53.8 / +61.7 ms**, i.e. **2.4x to 4.5x the whole frame**. The
    guidance band was <=0.30 ms easy, 0.30-0.60 acceptable for a clear structure win, >0.60
    requiring strong justification. This is 40x to 100x past the last of those, for a measured
    *loss* of contrast.

    The arm also pays one redundant probe: the production `forwardDensity` fetch runs before the
    `PaInsideStructure` branch and its result is discarded, so the 8-tap arms perform 9 density
    evaluations. The reported arm cost is therefore an upper bound by roughly one tap in eight -
    which changes nothing about the conclusion.
28. Whole renderer <=10 ms: **yes**. Gameplay-phase `gpuMs` p50 **1.541**, max 1.588. Production
    is untouched by this task in any case - `PRODUCTION_INSIDE_STRUCTURE_MODE = 0` and the arms
    do not survive into FINAL.

## DECISION

29. **DISPROVE (CASE B).** No candidate is productionized. `PRODUCTION_INSIDE_STRUCTURE_MODE`
    stays 0; the `PaInsideStructure` arms are removed from the shader, as T217's ten exploratory
    modes were, and the durable evidence is this report plus
    `stormT218InteriorStructureSandbox`, which reproduces the estimator comparison offline with
    no client.

30. **Mechanism result.** The buried view is structureless for three measured reasons that
    compound, none of which is sampling:

    1. **The amplitude is at the wrong scale.** The density field's SD is 0.250, of which the base
       carrier at a 109-block feature size carries 99%. Inside one attenuation length only 0.071
       exists - 8% of the variance.
    2. **The visible ball is 10.6 blocks across.** Pixel contrast is variation *between*
       directions inside that ball, and a 109-block feature is constant across it. The ray
       integral retains **11.1%** of the field's point SD at `4_INSIDE` against 36.6% at
       `4B_THROUGH_BODY` - a 3.3x difference that produces the 18x difference in measured local
       contrast.
    3. **The lighting has stopped responding.** `d(8bit)/d(od)` is -0.065 at the buried operating
       point against -51.9 at od 0.5, an 800-fold collapse, and the entire 8-bit budget across the
       full density span is 2.71 levels.

    The negative control confirms it from the other side: four estimators spanning 2 to 8 taps and
    two detail levels, at up to 4.5x the frame cost, all produce **less** local contrast than
    production and **zero** pockets.

    A secondary finding, true but immaterial: the production two-sample estimator is a poor
    estimator of its own quantity - od SD 12.65 against a true 7.93, correlation 0.41 - because
    its 82%-weighted probe sits 28 blocks up-sun where the density autocorrelation is **-0.20**.
    It is not worth fixing. Fixing it costs 24 ms and buys 0.01 8-bit.

31. **Exact next visual target.** Not the interior. T217 already named the alternative and this
    task makes the case for it: the **stair-stepped puff edges in the mid-field** (primary march
    step size), noted and deferred in T216. That defect lives where `4B_THROUGH_BODY` lives -
    36x the mid-band contrast, 265 resolvable pockets, retention 0.37 - which is the regime where
    a rendering change can still reach the frame. Attribute before building.

    The interior is closed on the following basis: a buried camera should look like fog because it
    is in fog, and the remaining 2.9% contrast at a 3.5-block scale is below what the display can
    show without a 15x amplification that no physics supports.

## VALIDATION

32. `campaignGate` PASS four times: before any client work (`gate-t218.log`), after the shader
    arm compiled (`gate-t218b.log`), after the T218M campaign registration (`gate-t218c.log`) and
    after the arms were collapsed out (`gate-t218d.log`).
33. `check build` PASS (`check-build-t218.log`).
34. Morphology sandbox PASS - 10 `PHASE4S_RESULT ... PASSED`, run standalone after the collapse as
    well as inside `check`.
35. Markers: `run/t132-autorun-exit.txt` armed throughout, so both clients stopped themselves.
    Exactly one campaign marker at a time - `run/t211-captures.txt` for the static arms, retired
    and replaced by `run/t218-gate-motion.txt` for the route. `T218M` is registered in
    `StormCampaignRegistry` above the sweeps, and the motion campaign's **three** edits were all
    made (the registry row, the `ArmSet` selection, and the OR-chain gating `Phase.T152_BEGIN`);
    `grep -c T152_FRAME` returns 6600, so the route ran rather than falling through to the default
    autorun.
36. `git status`: `PAchangelog.md` left modified and uncommitted as required. No validation
    evidence, campaign log or unrelated log was deleted.
37. Committed on `quality/t217-deep-interior-lighting`. **Not merged.**

### Program scope

`PaInsideStructure` was a negative control, not a candidate, and it is **removed** - uniform,
JSON entry, shader block, debug-config mode, renderer upload and capture shots - exactly as T217
collapsed its ten exploratory modes. `grep -rn PaInsideStructure src/` returns nothing. The
production inside estimator is byte-for-byte what it was at `f36cf29`, with a comment recording
what was measured about it and why it was deliberately left alone.

What T218 does leave in the tree, because each is a harness fix rather than an arm:

- **`StormT218InteriorStructureSandbox`** and its Gradle task - the durable reproduction. It
  compares 2 / 4 / 8 / full sampling, the windowed variance, the transmittance retention and the
  angular structure offline, on the production density path, with no client and no GPU.
- **The T152 straight-luminance metrics.** An alpha-only metric cannot judge a radiance-only
  term; this is the defect that left T217's gate unvalidated, and any future radiance-only
  candidate would have hit it too.
- **The `T218M` gate-edge route**, so the gate can be re-validated whenever it or
  `CameraCloudDensityTracker` changes.
- **`cameraCloudDensity` and `deepScatter` on every capture's `T098_CAPTURE_CONTROLS` line.**
  This is what caught both the failed static boundary pose and the fact that `4B_THROUGH_BODY` is
  not a camera-inside-cloud pose at all.


## HONEST LIMITS

- **The offline measurements use the CPU density mirror**, which carries the descriptor envelope,
  the base-noise remap and the multi-scale erosion, but not the shader-side T213 macro envelope or
  T215 lower-body fill. Both are large-scale vertical shape terms; neither introduces structure at
  the 5-30 block scales that decide this. The sandbox's fixture (radius 164) is also smaller than
  the live capture fixture (radius 670), which makes the sandbox's buried pose the *less* buried
  of the two - conservative in the right direction.
- **The forward probe is mirrored at LOD 0 with detail on**, where the shader fetches it at LOD 1.2
  with `useDetail = false`. That overstates the production estimator's available variance, so it
  biases against the "the estimator smooths it away" conclusion rather than towards it - and the
  measurement still came back saying the estimator adds noise rather than removing it.
- **Absolute 8-bit levels from the lighting mirror are not used.** It predicts 86 where the shader
  measures 121.8. Only slopes and ratios are load-bearing, and the direction of the level error
  makes the slopes conservative.
- **One fixture, one storm.** Every A/B is paired inside that run, which is the only comparison
  this harness supports; but the scale numbers (109-block carrier, 10.6-block visibility) are
  properties of the noise design and the tuning, not of this storm.
- **The luminance metrics are new and have no historical baseline.** They are reported against the
  paired arms in the same run, which is what they are for, but no earlier task's motion result can
  be re-read through them without re-running it.
