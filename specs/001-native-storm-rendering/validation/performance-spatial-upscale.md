# T209 - spatial upscale evaluation: the 960x540 temporal lattice to 1920x1080 against the native render - EASU and a depth-guided bicubic do not beat the depth-aware bilinear, which stays production (case C)

The final spatial stage of the temporal pipeline, evaluated on 13Sep2026 on
`e432108` (T208): 480x270 raw phase -> four-phase temporal -> 960x540
lattice -> spatial upscale -> 1920x1080, each candidate's display-size layer
compared against the NATIVE production render (FINAL at 1.0, one frame,
1920x1080). Static/timing campaign `T209` (`run/t209-spatial-upscale.txt`),
two launches (`t209-campaign/t209-run1.log` 19:03-19:08 with a one-sided
premultiplied guard - the launch that found the dark-halo defect;
`t209-run2.log` 19:09-19:14 with the two-sided guard, the code that is
kept); moving-camera route `T209M` (`run/t209-spatial-motion.txt`,
`t209m-run1.log` 20:22-20:32: native 1.0 as the reference, then the three
layers). SIDE and PLAY_VIS_NEAR, Ultra 96 steps, 1920x1080, one deterministic
fixture a pose per launch (a new storm each launch: compared within a run
only - T199 lesson 2). Not done, by the brief: no change to the raw scale,
the phases or the resolve; no rain or primary work; RCAS not tested (EASU
did not pass, Task 10's precondition).

## RECOVERY (Task 0)

1. **Starting HEAD `e432108`**, branch `experiment/cloud-descriptor-k`.
2. **Temporal output**: `temporalResolvedTarget`, RGBA16F premultiplied +
   depth, 960x540 at 1080p (`ceil(0.25 * display) * 2`), alpha = 1 - T with
   the current frame's rain composited; the composite's source on a temporal
   frame (`VolumetricCloudRenderHook`, step 6).
3. **Composite path**: `cloud_field_composite.fsh` - the 4-tap depth-aware
   bilinear (scene-occlusion rejection with a gradient bias, PA depth
   selection by weight x alpha, the same-surface test with the
   opaque-volume relaxation, premultiplied accumulation, straight colour
   out, `CoverageAlphaReconstruction` off in production), 0.08-0.09 ms.
4. **Depth**: the lattice's NDC depth (the body's representative depth, the
   rain's for pure rain, 1.0 for sky) and the native scene depth
   (`SceneDepthSampler`, detached copy).
5. **The old experiment**: `experiment/cloud-upscaling` (T164, `bf7e9ff`,
   4Sep2026, `.pa-agents/worktrees/cloud-upscaling/.../cloud-upscaling-experiment.md`)
   split the fused composite into reconstruct / RCAS / one-to-one composite
   and measured EASU (RGB only, straight colour inside a Reinhard-max +
   sqrt mapping, alpha by the PA rules, invalid taps substituted by the PA
   colour) and RCAS against a native reference on a raw 0.75 / 0.50 march.
   It found reconstruction free (0.09-0.13 ms) and nearly useless (+0.003
   cloud SSIM at best, 5.8% of the gap to native), RCAS after EASU worse
   everywhere, and one GL lesson - depth is written only through an enabled
   test (`GL_ALWAYS`). Its input lacked the detail; T209's input is the
   temporal lattice, and its EASU never touched the silhouette, which is
   what T209 is asked about.
6. **T152 route**: `StormT152MovingCameraFixture`, reference plus N
   candidates through one fixture (T208), the readback of `compositeSource`
   (the upscale layer when the upscale is on, 1920x1080; the native cloud
   target for the 1.0 reference, the same size).

## WHAT WAS BUILT

- **`cloud_spatial_upscale.fsh` / `CloudSpatialUpscaleRenderer`**: one pass
  from the lattice to a display-size layer (`spatialUpscaleTarget`,
  RGBA16F premultiplied + depth, `T209_UPSCALE_ALLOC`, released with every
  other target), its own GPU clock (`spatialUpscaleP50/P95` in the
  profile), inserted between the resolve and the composite by
  `VolumetricCloudDebugConfig.spatialUpscaleMethod()` (-1 off = the T207
  path; `PRODUCTION_SPATIAL_UPSCALE`). Three methods, ONE kernel and ONE set
  of weights on all four premultiplied channels each (a transparent texel is
  a legitimate zero of the field - no substitute colour), the PA depth
  selection for every method, depth written through `GL_ALWAYS`:
  - **0 PA bilinear**: the fused composite's reconstruction moved into the
    pass - the control (through the one-to-one composite it must equal
    production).
  - **1 EASU**: FSR1 EASU on the premultiplied field, the edge feature half
    luma (encoded colour, Reinhard-max + sqrt) and half coverage, the
    deringing clamp to the 2x2 centre taps on every channel.
  - **2 depth-guided Catmull-Rom**: a 4x4 bicubic on the premultiplied
    field, a tap dropped when it carries cloud on another surface than the
    selected depth (or is scene-occluded), weights renormalised over the
    accepted taps, the 2x2 clamp.
  - **The premultiplied guard** (every method): the output's straight
    colour is clamped, channel by channel, to the range of the valid
    centre taps' straight colours - no bright fringe, no dark halo, by
    construction.
- **Composite one-to-one** (`OneToOne`, Task 11): a source already at the
  destination size is fetched one texel per pixel with the scene test and
  the straight conversion only - the layer is never filtered twice. The
  renderer arms it by size and every capture logs `composite1to1=true` for
  the layers (and for the native reference, whose cloud target is the
  display size) and `false` for the 960x540 lattice.
- **Harness**: `T166Arm.upscale` (label `@uN`), `T209_IMAGE_ARMS` (native,
  native without the rain pass, native body; the lattice against native
  box-downsampled 2x; the three layers full-frame and body-only against
  native / native body; an EASU repeat), `T209_RAIN` (the rain mask =
  native with rain minus native without: mean absolute and signed alpha
  error, thin rain lost, the one-pixel halo ring, vertical shaft runs),
  `T209_CAPTURE ... edgeLumaP999/P50/P001` (the straight-luma tail over
  edge pixels, alpha 0.002-0.1 - Task 5's diagnostic), `buildT209Report`
  (T207's brackets with the upscale column), `ArmSet.UPSCALE` on the route.

## THE DEFECT THE DIAGNOSTIC FOUND (Task 5)

Run 1 bounded the guard on the bright side only. The edge-luma tail then
read, for EASU and the bicubic, **p0.1 = 0.0 at both poses and a median of
0.07 at PLAY_VIS_NEAR against the native 0.55** (SIDE 0.47 against 0.57):
the kernels' negative lobes drove premultiplied rgb to zero where alpha
stayed positive - every edge pixel black, a dark halo around every
silhouette - and the scores followed (EASU 0.912 / 0.883, the bicubic
0.909 / 0.924 against the bilinear's 0.932 / 0.924). The two-sided guard
(run 2) restores the tail to the native's: p0.1 0.399-0.403 against the
native 0.399 / 0.343 at SIDE / PLAY_VIS_NEAR, medians 0.563 / 0.516-0.531
against 0.566 / 0.564, p99.9 0.961-0.998 against 0.961 / 0.998 - no
fringe, no halo, for any method. The bilinear control's tail was the
native's in both runs (it is a convex combination of premultiplied taps).
RGB stays premultiplied throughout (the composite divides by alpha once,
one-to-one), alpha keeps its 1 - T meaning (the kernels' alpha is clamped
to the footprint's range), and the rain is inside the same texel as the
body, so no rain/cloud alpha mismatch can arise. The sandbox asserts the
two-sided clamp line and the one-weight-per-texel accumulation.

## STATIC QUALITY (Tasks 2, 6) - each layer at 1920x1080 against native 1.0

Run 2 (the shipping guard). The lattice row is the 960x540 lattice against
native box-downsampled to 960x540 - what the representation had lost before
any upscaler saw it (a different comparison plane: point-sampled 0.25 rays
against area-averaged native, hence the low thin retention there).

| SIDE (rain 0.37% of pixels) | cloud SSIM | edge SSIM | RMS | maxAbs | thin | holes | dark interior | pockets | silhouette IoU |
|---|---|---|---|---|---|---|---|---|---|
| lattice vs native/2 (960x540) | 0.9584 | 0.9264 | 1.64e-2 | 0.987 | 0.784 | 0.481 | 0.958 | 0.702 | 0.9865 |
| 5. **bilinear** (control = production) | **0.9387** | **0.8329** | **1.77e-2** | 0.986 | **0.964** | 0.063 | **0.962** | 0.139 | 0.9779 |
| 6. EASU | 0.9210 | 0.7741 | 2.43e-2 | 0.989 | 0.930 | 0.125 | 0.937 | 0.148 | 0.9793 |
| 7. depth-guided | 0.9074 | 0.7214 | 2.93e-2 | 0.989 | 0.932 | 0.125 | 0.937 | 0.111 | 0.9753 |
| 8. body-only: bilinear / EASU / depth | 0.9371 / 0.9209 / 0.9074 | 0.8123 / 0.7641 / 0.7098 | 1.90e-2 / 2.43e-2 / 2.93e-2 | | 0.993 / 0.949 / 0.969 | 0.022 / 0.065 / 0.065 | 0.959 / 0.937 / 0.937 | 0.171 / 0.142 / 0.110 | 0.977 / 0.981 / 0.977 |
| EASU repeat vs EASU | 0.99993 (10.5k px) | | | | | | | | |
| run 1 (one-sided guard): bilinear / EASU / depth | 0.9319 / 0.9121 / 0.9090 | 0.833 / 0.757 / 0.727 | | | | | | | |

| PLAY_VIS_NEAR (rain 0.10%) | cloud SSIM | edge SSIM | RMS | maxAbs | thin | holes | dark interior | pockets | silhouette IoU |
|---|---|---|---|---|---|---|---|---|---|
| lattice vs native/2 (960x540) | 0.9022 | 0.8321 | 3.30e-2 | 0.988 | 0.310 | 0.965 | 0.908 | 0.759 | 0.9335 |
| 15. **bilinear** (control = production) | 0.9370 | 0.7875 | **2.95e-2** | 0.993 | **0.944** | 0.943 | **0.973** | 0.172 | 0.9831 |
| 16. EASU | **0.9423** | **0.7896** | 3.00e-2 | 0.995 | 0.856 | 0.943 | 0.965 | **0.208** | 0.9843 |
| 17. depth-guided | 0.9373 | 0.7899 | 3.03e-2 | 0.993 | 0.896 | 0.943 | 0.969 | 0.141 | 0.9837 |
| body-only: bilinear / EASU / depth | 0.9368 / 0.9421 / 0.9364 | 0.7851 / 0.7889 / 0.7843 | 2.97e-2 / 3.01e-2 / 3.08e-2 | | 0.948 / 0.872 / 0.893 | 0.943 / 0.943 / 0.943 | 0.973 / 0.965 / 0.969 | 0.171 / 0.215 / 0.136 | 0.983 / 0.984 / 0.983 |
| EASU repeat vs EASU | 0.999995 (5 px) | | | | | | | | |
| run 1 (one-sided guard): bilinear / EASU / depth | 0.9245 / 0.8834 / 0.9242 | 0.787 / 0.691 / 0.793 | | | | | | | |

Reading: at SIDE the bilinear wins on every term but pockets and IoU
(-0.018 / -0.031 cloud SSIM, -0.06 / -0.11 edge SSIM for EASU / the
bicubic; run 1 agreed, -0.020 / -0.023, so this is not the SIDE capture
drift of ~0.015). At PLAY_VIS_NEAR EASU is +0.005 SSIM and +0.002 edge over
the bilinear and recovers pockets (0.17 -> 0.21) but loses thin structure
(0.944 -> 0.856) and dark interiors (0.973 -> 0.965); the bicubic equals the
bilinear. The volumetric silhouette in the native frame is soft - a
density falloff, not a step - and an edge-adaptive kernel that steepens it
moves away from native at the pose where the silhouette dominates (SIDE)
while the interior-heavy pose (PLAY_VIS_NEAR) gains a little contrast. The
body-only rows say the same as the full frame at both poses: the ranking is
the body's, not the rain's.

## RAIN (Task 7)

The rain mask is the native frame's difference from itself without the
rain pass; both launches' SIDE storms were rain-light (0.135% / 0.372% of
pixels; T208's rain-heavy SIDE fixture did not recur in two launches).

| SIDE run 2 (7,721 rain px) | rain mean abs alpha | signed (thickening) | thin rain lost | halo ring > 0.02 | shaft runs arm / ref |
|---|---|---|---|---|---|
| 21.-23. bilinear | **0.0372** | **+0.019** | **8.9%** | **16.5%** | 639 / 757 (1.18) |
| EASU | 0.0499 | +0.034 | 11.3% | 22.6% | 667 / 757 (1.13) |
| depth-guided | 0.0658 | +0.051 | 14.1% | 20.2% | 669 / 757 (1.13) |
| PLAY_VIS_NEAR (2,114 px): bilinear / EASU / depth | 0.151 / 0.144 / 0.137 | -0.139 / -0.128 / -0.121 | 11% / 24% / 15% | 1.4% / 2.3% / 2.1% | 1.07 / 1.12 / 1.09 |

Run 1 (rain 0.135%): thin rain lost 9.4% / 27.2% / 21.9%, halo 21.1% /
23.5% / 22.4%, thickening +0.038 / +0.048 / +0.050 for bilinear / EASU /
depth. Neither candidate makes the rain materially worse than the bilinear
at the whole-frame level (the rain error is 0.03-0.15 alpha in every arm
- the T208 representation gap, the lattice's rain being one 0.25 ray per
2x2), but both thicken it more and lose more thin rain; the bilinear is the
gentlest on rain at both poses. Shaft continuity: every arm merges more
runs than native has (ratio > 1: the upscale bridges the gaps between thin
shafts), the bilinear most at SIDE, least at PLAY_VIS_NEAR.

## MOTION (Task 8) - the T152 route, native 1.0 as the reference, one fixture, four arms

`t209m-run1.log`, storm `92a5ccde`: production at native 1.0 (1920x1080,
the reference; the route's usual frame-0 retry), then the 0.25 lattice
through each layer, 2200 frames each, every layer compared frame by frame
against the native frames at 1920x1080. Shimmer and edge crawl are read
from the fixture's own columns: `flicker` (the frame-to-frame alpha
change), `flipOn + flipOff` (pixels that cross the coverage threshold each
frame - crawl), `reEntryMean` (columns that leave and re-enter cloud).

| segment (n) | native flicker | **bilinear** flicker / ghost mean / p95 | EASU flicker / ghost / p95 | depth flicker / ghost / p95 |
|---|---|---|---|---|
| OUTSIDE (281) | 0.00018 | 0.00028 / 0.00039 / 0.00100 | 0.00029 / 0.00037 / 0.00103 | 0.00031 / 0.00037 / 0.00103 |
| APPROACH (425) | 0.00133 | 0.00145 / 0.00058 / 0.00133 | 0.00147 / 0.00041 / 0.00093 | 0.00150 / 0.00043 / 0.00110 |
| ENTRY (796) | 0.00126 | 0.00147 / 0.00052 / 0.00108 | 0.00150 / 0.00038 / 0.00068 | 0.00152 / 0.00040 / 0.00074 |
| INTERIOR (698) | 0.00393 | **0.00390 / 0.00090 / 0.00387** | 0.00404 / 0.00134 / 0.00716 | 0.00406 / 0.00131 / 0.00685 |
| route (2200) | 0.00198 | **0.00209 / 0.00064 / 0.00133** | 0.00215 / 0.00069 / 0.00121 | 0.00217 / 0.00069 / 0.00121 |
| 24.-29. frames > 0.02 mean alpha error | | **6** (1253, 1321-1325) | 19 (1253, 1321-1333) | 18 (1253, 1321-1333) |
| frames > 0.01 | | 17 | 33 | 32 |
| flip (crawl) mean, native 0.00244 | | 0.00254 | 0.00260 | 0.00262 |
| re-entry mean, native 1.118 | | 0.737 | 0.778 | 0.789 |
| disocclusion ghost, interior | | 0.040 | 0.048 | 0.047 |

The bilinear layer under motion is the native frame's flicker (0.00209
against 0.00198; interior 0.00390 against 0.00393), a ghost mean of 0.0006
with bias within 0.0003 of zero, and six outlier frames - the in-cloud
opening at 1321-1325 that T208 attributed to the representation, plus one.
EASU and the bicubic keep the far segments (their ghost means are
slightly LOWER outside the body, 0.00038-0.00043 against 0.00052-0.00058,
the sharper silhouette landing closer to native on the approach) but pay
inside the body: interior ghost 0.0013 against 0.0009, p95 0.007 against
0.004, the outlier count tripled (18-19 against 6), flicker +3% and crawl
+2-3% over the bilinear - the edge-adaptive kernels re-decide their
direction as the openings move, and the bicubic's negative lobes do the
same. No arm ghosts (nothing persists past the temporal cycle; the layers
are stateless), and no arm adds disocclusion error the lattice does not
already have (0.040-0.048 against the T208 0.034-0.047 class).

## PERFORMANCE (Task 9) - run 2 (run 1 in parentheses), p50 of 60 samples, anchor-bracketed

The anchors are production: the lattice through the fused composite, no
layer. Every layer arm adds its pass and drops the composite from 0.089 to
0.040 (a one-to-one fetch), so the net cost of a layer is pass + 0.040 -
0.089.

| ms | SIDE | PLAY_VIS_NEAR |
|---|---|---|
| raw phase (0.25 cloud draw) | 5.06-5.12 (5.03-5.14) | 4.57-4.59 (4.09-4.13) |
| field / gate / rain pass | 1.08 / 0.12 / 1.08 | 1.08 / 0.12 / 1.69 |
| temporal resolve | 0.106 | 0.105 |
| 30. **bilinear pass** (p50 / p95) | **0.109 / 0.114** (0.104 / 0.109) | **0.103 / 0.112** (0.092 / 0.103) |
| 31. **EASU pass** | **0.138 / 0.140** (0.128 / 0.129) | **0.121 / 0.128** (0.110 / 0.116) |
| 32. **depth-guided pass** | **0.143 / 0.148** (0.133 / 0.136) | **0.127 / 0.132** (0.113 / 0.121) |
| composite with a layer (one-to-one) / production | 0.040 / 0.089 | 0.038 / 0.083 |
| net over production: bilinear / EASU / depth | +0.059 / +0.089 / +0.094 | +0.058 / +0.075 / +0.082 |
| total: bilinear / EASU / depth | 7.49 / 7.57 / 7.52 (7.43 / 7.55 / 7.45) | 7.60 / 7.78 / 7.62 (7.01 / 7.07 / 7.05) |
| 34./35. **anchors (production) total** | **7.47** (7.37) | **7.64** (6.98) |
| native 1.0: cloud / total | 51.1 / 59.7 (46.1 / 54.4) | 41.0 / 50.3 (33.9 / 42.8) |
| 36. budget to 10 ms (production) | 2.53 (2.63) | 2.36 (3.02) |

Every pass is in the "excellent" band (<= 0.5 ms) by a factor of four; the
whole renderer stays where T207/T208 left it (7.4-7.8 ms in these two
light sessions, the fixture deciding the rest). Cost does not separate the
candidates; quality does.

## OUTPUT-RESOLUTION CORRECTNESS (Task 11)

Temporal input 960x540 (`T207_RESOLVED_ALLOC size=960x540`), the layer
1920x1080 (`T209_UPSCALE_ALLOC size=1920x1080`), the composite
one-to-one on it (`composite1to1=true` on every layer capture; the
composite's own cost drops to 0.038-0.040 ms - a fetch, not a filter), no
downsample/re-upsample anywhere (the lattice row is compared against a
downsampled native, never rendered through one), and the pass reads its
source with `texelFetch` on integer coordinates from `texCoord *
sourceSize - 0.5` - the same mapping the fused composite uses at any size,
verified by the control's equality with production (method 0's output
through the one-to-one composite is the fused composite's arithmetic, and
its scores against native are the production path's).

## DECISION (Task 12)

Ranked by the brief's order - motion stability, edge/silhouette,
full-frame, thin/hole/interior, cost:

- Motion stability: bilinear first (route flicker 0.00209, interior
  0.00390 = native, 6 outliers); EASU and the bicubic add +3% flicker, +2-3%
  crawl and triple the in-cloud outliers (19 / 18).
- Edge/silhouette: bilinear 0.833 / 0.788, EASU 0.774 / 0.790, bicubic
  0.721 / 0.790 - the bilinear at SIDE by 0.06-0.11, a tie at
  PLAY_VIS_NEAR.
- Full frame: bilinear 0.939 / 0.937, EASU 0.921 / 0.942, bicubic 0.907 /
  0.937 - the bilinear at SIDE by 0.018-0.031, EASU at PLAY_VIS_NEAR by
  0.005.
- Thin / holes / interior: bilinear keeps thin structure (0.964 / 0.944)
  and dark interiors (0.962 / 0.973) best; EASU loses thin (0.930 / 0.856)
  and interiors; holes are equal at PLAY_VIS_NEAR and all poor at SIDE
  (0.06-0.13 - a representation limit, T208).
- Cost: 0.10 / 0.14 / 0.14 ms, all excellent.

37. **T209: CASE C - the bilinear remains best overall.** The acceptance
    criterion 1 ("clearly improves over the current bilinear") is met by no
    candidate at both poses: EASU's only win is +0.005 SSIM at PLAY_VIS_NEAR
    against -0.018 SSIM / -0.06 edge at SIDE and a thin-structure loss at
    both; the bicubic never wins. The evaluation is complete and the
    answer is decisive (two launches agree at SIDE beyond the capture
    drift; the repeats are 5-10.5k px).
38. **Winning candidate: the depth-aware bilinear** (the fused composite,
    production since T206).
39. **Production candidate: NO** - `PRODUCTION_SPATIAL_UPSCALE = -1`; the
    composite keeps reading the lattice directly.
40. **Quality gain vs bilinear**: none at SIDE (EASU -0.018, bicubic
    -0.031); +0.005 SSIM / +0.002 edge for EASU at PLAY_VIS_NEAR, bought
    with -0.088 thin retention.
41. **Performance cost**: 0.10-0.14 ms per pass, +0.06-0.09 ms net; not
    the reason.
42. **Exact next task**: the spatial stage is closed - the residual against
    native is the representation's, in the order T208 named: the rain pass
    at phase resolution (the lattice's rain is one 0.25 ray per 2x2 block
    against native's per-pixel march; a rain pass at lattice resolution, or
    a guided reconstruction of it) and the on-axis in-cloud parallax
    residual (an along-ray term). The upscale pass stays in the tree as a
    diagnostic (off in production; the sandbox pins the default) so a
    future candidate can be judged by the same harness.

## VALIDATION

43. `campaignGate` PASS x2 (the T209 invariant among them). 44. `check
build` PASS (1m 1s warm). 45. Alpha/transmittance: `validateT209SpatialUpscale` -
the JSON names exactly the lattice colour, depth and the scene depth; no
phase or history sampler; EASU and the bicubic accumulate one weight per
vec4 texel and clamp every channel to the 2x2; the two-sided straight-colour
guard is present; depth is the PA selection through `GL_ALWAYS`; the
composite has the one-to-one fetch and arms it by size; the hook routes
the composite through the layer; the layer is released with the other
targets; the campaigns and the route set are wired; the production default
is the one this campaign chose; the shader compiles in GL. 46. Lifecycle:
the T199 probe ran in both launches; the layer is allocated on the first
upscaled frame, released on `upscaleOff` / resize / shutdown
(`T209_UPSCALE_RELEASE`). 47. Program registration: four production
programs unchanged; the upscale is a field-pass shader beside the composite
and the resolve. 48. Variant scope: `T191_VARIANT_SCOPE registered=0` with
T209 armed. 49. Markers: T209 and T209M `.done`. 50. git status: the task's
files plus the preserved logs. 51. Commit: the T209 commit on
`experiment/cloud-descriptor-k` (parent `e432108`), not merged.
