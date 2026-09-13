# T208 - temporal quality cleanup + the 0.50 production baseline: SIDE's ceiling is representation (rain at phase resolution where the frame carries rain, the body lattice's own ~0.93-0.95 elsewhere), the near-field guard ships, T209 receives a 960x540 RGBA16F+depth lattice

Cleanup of the T207 temporal lattice and the authoritative quality and
cost of the production path - 0.25 raw phases, four-phase temporal
reconstruction, a 0.50 (960x540) resolved image - on 13Sep2026, on
`73cabab` (T207). Static/timing campaign `T208` (`run/t208-temporal-cleanup.txt`),
launched three times (`t208-campaign/t208-run1.log` 15:56-16:02, clean;
`t208-run2.log` 16:04-16:12, GPU-contended - kept as corroboration only;
`t208-run3.log` 17:02-17:08, clean); moving-camera route `T208M`
(`run/t208-temporal-motion.txt`, `t208m-run1.log` 15:38-15:42 with the guard
as a candidate arm beside the T207 validity; `t208m-run2.log` 17:13-17:17
on the shipped binary, the guard in mode 1 and the T207 validity as the
mode-5 control, a second storm). SIDE and PLAY_VIS_NEAR,
Ultra 96 steps, 1920x1080, one deterministic fixture a pose per launch (a
new storm each launch: quality is compared within a run only - T199 lesson 2).

Not done, by the brief: no 0.125 raw phase, no output below 0.50, no
FSR/EASU or sharpening, no recursive TAA, no fifth phase, no rain or
primary optimization. The previous session's T208 draft had a 0.125 phase
feasibility arm (`T208_LOW_SCALE`, a `TEMPORAL_0125` route pair, box
downsampling of the references); every piece of it was removed before the
first launch and the sandbox asserts it stays out.

## RECOVERY (Task 0)

1. **HEAD `73cabab`**, branch `experiment/cloud-descriptor-k`, the T195-T207
   logs preserved. Working tree carried an uncommitted T208 draft from the
   previous session (attribution switches, a near-field guard, the 0.125
   arms); the in-scope parts were kept and re-targeted, the 0.125 parts
   stripped.
2. **Production temporal mode**: `PRODUCTION_TEMPORAL_MODE = 1`
   (`VolumetricCloudDebugConfig`); the four phase targets 480x270 (RGBA16F
   body + depth + RGBA16F aux), the resolved target 960x540 (RGBA16F +
   depth), `cloud_temporal_resolve.fsh` resolving once per frame, the rain
   pass current-frame and unjittered, composited in the resolve with the
   in-march equation; the parallax guard (`camDelta > 0.1 * distance`); the
   T152 route under `ArmSet.TEMPORAL` (production 0.50 as the reference);
   production 0.50 as the static reference (`T207_REFERENCE_SCALE`).
3. **The previous session's T207M re-run** (15:23-15:27, `runClient-t207m.out.log`)
   aborted three times - twice at frame 0 (`fixture_dissipated lobeCount=0`,
   the known first-frame retry) and once at frame 291 on a window resize
   (`[CloudDepth] size=1920x1129`, the field and rain-pass targets
   re-allocated, the lobe count transiently 0). Environmental; the route
   was re-run.

## HARNESS (what T208 added)

- **Attribution switches** (`VolumetricCloudDebugConfig.rainDisabled`,
  `deferredSplitForced`): the first skips the rain pass and clears its
  target to "no rain" (rgb 0, packed `T_r = 1`, which the split cloud
  program and the resolve both decode as nothing pending) so every consumer
  sees a rainless frame; the second uploads `PaTemporalPhase = 1` on a
  non-temporal frame so the cloud target holds the body alone (the split
  deferred, never composited) - the body-only REFERENCE. `DebugOutput = 2`
  on the resolve writes the lattice's body before the rain composite - the
  body-only ARM. Both switches are image-arm state, reset by every timing
  arm (`applyT141Arm`) and at the campaign's exit (sandbox-asserted).
- **Near-field guard** (`CLASS_NEARFIELD = 9`, `NEAR_FIELD_DISTANCE = 96`):
  a history sample is refused when all three hold - the reprojection
  distance is under 96 blocks, the reprojection moved the sample by a phase
  texel or more, and the current 3x3 interval is ambiguous (coverage span
  > 0.5, or distance span > a third of the far end). Zero motion or a
  distant sample never enters it. Judged as resolve mode 5 on the route
  beside mode 1 (T207's validity) through ONE fixture, then shipped in
  mode 1 with mode 5 kept as the no-guard control.
- **Image arms** (`T208_IMAGE_ARMS`, all 960x540): `ref050`, `ref050_norain`,
  `ref050_body` stored; `t025` (validated lattice) twice (the repeat prices
  the pipeline's own noise), `t025_single` (mode 3), `t025_trust` (mode 4),
  `t025_control` (mode 5, must equal `t025` statically), `t025_norain`
  against `ref050_norain`, `t025_body` against `ref050_body`; class maps
  under modes 1 and 5 (`T207_VALIDITY`, now with `nearField`).
- **Timing arms**: T207's brackets (`buildTemporalReport`, shared by both
  campaigns): production 0.25 anchors around the lattice x3, mode 5, mode
  3 (reconstruction without validation), production 0.50.
- **Route**: `ArmSet` is a reference plus any number of candidates
  (`TEMPORAL` = production 0.50, `temporal_025` mode 1, `temporal_025_noguard`
  mode 5), every candidate compared frame by frame against the reference's
  stored frames.
- **Window contract**: the T135_PREPARE resize is a recorded render call
  that a client launched from Bash (`powershell -Command Start-Process`)
  ignored - the first T208 launch ran every cell at 854x480 and captured
  427x240 references against 428x240 lattices (nothing evaluated); a
  second launch was killed after 180 ignored requests. Launched from the
  PowerShell tool the resize lands at once (as every T207 run did). The
  driver now verifies the framebuffer before each pose
  (`contractWindowReady`, `T135_WINDOW`: restore, show, resize, 300-frame
  bound) - run 2 needed one retry, runs 1 and 3 none.

## SIDE ATTRIBUTION (Tasks 1-2) - the 0.25 lattice against production 0.50

Every arm is the same program at 0.25 phases resolved to 960x540; the
reference is production FINAL at 0.50 (the lattice's exact target). Runs 1-3
are three different storms at SIDE (run 2's session was GPU-contended and
its repeats were not identical; the precision note below applies to every
run).

| SIDE, run 1 (rain-heavy storm) | cloud SSIM | edge SSIM | RMS | maxAbs | thin | holes | dark interior | pockets | silhouette IoU |
|---|---|---|---|---|---|---|---|---|---|
| 1. **full frame, validated** (`t025`) | **0.7963** | 0.3784 | 8.35e-2 | 0.991 | 0.966 | 0.067 | 0.861 | 0.399 | 0.9327 |
| single phase (`t025_single`, mode 3) | 0.8026 | 0.3633 | 8.25e-2 | 0.990 | 0.980 | 0.067 | 0.854 | 0.232 | 0.9265 |
| 4. trust-all ceiling (`t025_trust`) | 0.7963 | 0.3785 | 8.35e-2 | 0.991 | 0.967 | 0.067 | 0.861 | 0.399 | 0.9327 |
| control, no guard (`t025_control`) | 0.7964 | 0.3784 | 8.35e-2 | 0.991 | 0.967 | 0.067 | 0.861 | 0.400 | 0.9327 |
| 3. **rain disabled** (`t025_norain` vs `ref050_norain`) | **0.9757** | **0.9919** | 6.10e-3 | 0.989 | 0.939 | 0.769 | 0.980 | 0.520 | 0.9993 |
| 2. **body only** (`t025_body` vs `ref050_body`) | **0.9758** | **0.9915** | 6.22e-3 | 0.989 | 0.941 | 0.769 | 0.980 | 0.524 | 0.9993 |
| repeat (`t025_r2` vs `t025`) | 1.0000 (digest-identical) | | 0 | 0 | | | | | |
| control vs validated | 0.99995 (19 px; captured at a different phase of the cycle) | | 1.0e-3 | 0.49 | | | | | |

| SIDE, run 2 (rain-light storm; contended session) | cloud SSIM | edge SSIM | RMS | thin | holes | dark | pockets | IoU |
|---|---|---|---|---|---|---|---|---|
| full frame, validated | **0.9505** | 0.9436 | 1.55e-2 | 0.901 | 0.538 | 0.965 | 0.538 | 0.9951 |
| single phase | 0.9205 | 0.8338 | 2.47e-2 | 0.903 | 0.154 | 0.937 | 0.301 | 0.9849 |
| trust-all | 0.9362 | 0.9086 | 2.09e-2 | 0.888 | 0.346 | 0.957 | 0.547 | 0.9942 |
| control | 0.9361 | 0.9086 | 2.09e-2 | 0.886 | 0.346 | 0.956 | 0.550 | 0.9942 |
| rain disabled | 0.9471 | 0.9524 | 1.46e-2 | 0.955 | 0.647 | 0.965 | 0.550 | 0.9977 |
| body only | 0.9311 | 0.9169 | 1.97e-2 | 0.962 | 0.529 | 0.958 | 0.561 | 0.9969 |
| repeat vs validated | 0.99986 (9 px) | | | | | | | |
| control vs validated | 0.9472 (81,827 px) - statically impossible unless the scene drifted between captures, 130 s apart | | | | | | | |

| SIDE, run 3 (rain present, light; clean session) | cloud SSIM | edge SSIM | RMS | thin | holes | dark | pockets | IoU |
|---|---|---|---|---|---|---|---|---|
| full frame, validated | **0.9627** | 0.9502 | 1.42e-2 | 0.896 | 0.379 | 0.967 | 0.507 | 0.9953 |
| single phase | 0.9460 | 0.8675 | 2.03e-2 | 0.906 | 0.069 | 0.956 | 0.268 | 0.9860 |
| trust-all | 0.9587 | 0.9358 | 1.61e-2 | 0.894 | 0.379 | 0.963 | 0.578 | 0.9947 |
| control | 0.9493 | 0.9000 | 2.04e-2 | 0.898 | 0.345 | 0.951 | 0.517 | 0.9935 |
| rain disabled | 0.9491 | 0.8978 | 2.05e-2 | 0.964 | 0.346 | 0.951 | 0.518 | 0.9949 |
| body only | 0.9491 (digest-identical to rain-disabled) | 0.8978 | 2.05e-2 | 0.964 | 0.346 | 0.951 | 0.518 | 0.9949 |
| repeat vs validated | 1.0000 (digest-identical) | | | | | | | |
| control vs validated | 0.9601 (81,315 px) - see the precision note | | | | | | | |

**Precision note (SIDE only).** The control (mode 5) must equal the
shipping mode statically - the class maps say so (identical counters,
`nearField = 0`, in every run) and at PLAY_VIS_NEAR the two captures are
digest-identical (runs 1 and 3). At SIDE they are not (19 px in run 1,
81k px in runs 2 and 3, captured one second apart with the world time
pinned and every phase re-rendered between arms): the SIDE frame's content
itself varies between phase sets at the 0.01-0.015 SSIM level - the T207
harness observation that trust-all's second SIDE capture is always worse,
now seen on the control too. Back-to-back repeats of one arm are
digest-identical (runs 1, 3) or 9 px apart (run 2). So SIDE's within-run
differences under ~0.015 are not read; run 1's rain effect (0.18) and the
single-phase gap are far above it.

5./6. **The limiting component.** Two facts, both representation, neither
correspondence:

- **Where the frame carries rain, rain at phase resolution is the whole
  gap.** Run 1's SIDE storm is rain-heavy: the full frame scores 0.796
  (edge 0.38, holes 0.07) and the same lattice with the rain pass skipped
  - or its body compared against the reference's body - scores **0.976
  with edge 0.992, holes 0.77, silhouette IoU 0.9993**. Trust-all equals
  the validated lattice to four digits (0.7963 / 0.7963) and the control
  without the guard too; the single phase is 0.803. The lattice's rain is
  the current frame's 480x270 rain pass, block-replicated over 2x2 lattice
  pixels, against a reference that marches rain per 960x540 pixel: every
  shaft edge and every rain texel differs, and the SSIM window sees it
  everywhere the shafts are.
- **Without rain the body lattice sits at ~0.93-0.95 at SIDE, and that is
  its own ceiling.** Runs 2 and 3's SIDE storms are rain-light: full 0.9505 / 0.9627 (T207's
  0.9514 class), rain-disabled 0.947 / 0.949, body-only 0.931 / 0.949,
  single 0.920 / 0.946, trust-all 0.936 / 0.959 - the arms within 0.02 of
  each other (the SIDE precision bound) and the validated lattice at or
  above the ceiling. T207 and T165 found the same: SIDE is
  not resolution-limited (the 0.25 single frame is already 0.92-0.95
  against 0.50), so four sub-texel phases have little to add to its SSIM
  (they add to edge, holes, pockets: 0.83-0.87 -> 0.94-0.95, 0.07-0.15 ->
  0.38-0.54, 0.27-0.30 -> 0.51-0.54), and the residual is the interior's sub-texel point sampling that
  a 0.50 pixel integrates and a 0.25 ray does not.

PLAY_VIS_NEAR is not limited by either: validated **0.9959 / 0.9953**
(runs 1 / 2; 0.9940 run 3), edge 0.990 / 0.990 / 0.985, rain-disabled
0.9961 / 0.9951 / 0.9945, body-only 0.9961 / 0.9955 / 0.9944, trust-all
0.9963 / 0.9956 / 0.9941, single phase 0.815 / 0.726 / 0.850.

**Rain is not temporally filtered** (Task 2, by design): the fix for the
rain-heavy case is a rain pass at lattice resolution or a guided
reconstruction of the current frame's rain, both outside T208.

## NEAR-FIELD OUTLIERS (Tasks 3-4) - the T152 route, one fixture, three arms

`t208m-run1.log`: production 0.50 (the reference), then the lattice under
mode 1 = T207's validity (no guard), then under mode 5 = the same plus
the near-field guard, 2200 frames each through storm `d02847d6` (the
route's usual frame-0 retry, then complete). Both candidates are compared
frame by frame against the same reference frames.

| segment (n) | ref flicker | mode 1 flicker | guard flicker | mode 1 ghost mean / p95 | **guard ghost mean / p95** | mode 1 bias | guard bias | disocc ghost 1 / guard |
|---|---|---|---|---|---|---|---|---|
| OUTSIDE (287) | 0.00013 | 0.00028 | 0.00027 | 0.00033 / 0.00091 | 0.00034 / 0.00093 | +0.00025 | +0.00026 | 0.047 / 0.047 |
| APPROACH (413) | 0.00060 | 0.00076 | 0.00079 | 0.00032 / 0.00085 | 0.00041 / 0.00082 | +0.00009 | +0.00008 | 0.025 / 0.032 |
| ENTRY (794) | 0.00130 | 0.00155 | 0.00155 | 0.00059 / 0.00113 | 0.00058 / 0.00114 | +0.00006 | +0.00005 | 0.043 / 0.043 |
| INTERIOR (706) | 0.00425 | 0.00430 | 0.00438 | 0.00152 / 0.00785 | **0.00111 / 0.00540** | +0.00055 | **+0.00013** | 0.042 / **0.034** |
| route (2200) | 0.00196 | 0.00212 | 0.00215 | **0.00081** / 0.00135 | **0.00069** / 0.00138 | | | |

7. **Old outlier count**: 14 frames of 2200 above 0.02 mean alpha error
   under T207's validity in this fixture (1238-1249 and 1332-1333; T207's
   own fixture gave 11) and 34 above 0.01.
8. **Tested guard**: distance < 96 blocks AND reprojection motion >= 1
   phase texel AND an ambiguous current interval (coverage span > 0.5 or
   distance span > 0.33 x far).
9./10. **Ghost mean 0.00069** (from 0.00081), p95 0.00138 (0.00135).
11. **New outlier count: 5** (1236, 1237, 1250, 1332, 1333); 23 above 0.01.
12. **Flicker**: 0.00212 -> 0.00215 over the route, interior 0.00430 ->
    0.00438 (the reference's own is 0.00425), interior p95 0.0206 -> 0.0213.
    No new pop: the guard reproduces the reference's two coverage steps
    (below) two frames early and one frame late instead of holding one of
    them shut.
13. **Acceptance impact**: none statically (both class maps identical at
    both poses in every run: 388,788-388,790 / 388,757-388,767 of 388,800
    accepted, `nearField = 0`); under motion the far segments are
    untouched (OUTSIDE and ENTRY ghost within 1e-5 of mode 1), the
    interior gains 0.0004, APPROACH pays 0.00009 (six frames at 0.005-0.008
    where the current phase is worse than the history the guard refused,
    frames 328-342 near the surface at coverage 0.45).
14. **Guard: PASS** - shipped in mode 1; mode 5 is the control.

What the log shows the guard doing (frames 1230-1254, camera inside the
body at density 0.04-0.13, 1.67 blocks/frame): the reference's coverage
steps from 0.969 to 0.947 at frame 1238 (an opening comes into view,
reference flicker 0.027) and back to 0.995 at 1250. Under T207's validity
the lattice never shows the opening - twelve frames at coverage 0.967-0.954
with a +0.019-0.020 alpha bias: the accepted history rays were rendered
1.7-5 blocks behind the camera and integrate the medium between the two
cameras, which a point reprojection with a depth correction cannot
remove, and at these distances the parallax guard's 10%-of-distance bound
is looser than that. With the near-field guard the lattice tracks the
reference through the opening to 3e-5 (coverage 0.9471 against 0.9473)
and its three outliers are the transitions: it leads the step down by two
frames (0.024, 0.027, bias -0.018) and lags the step up by one (0.021).
The second episode (1332-1350, coverage 0.39-0.52 with openings) is
identical under both modes (0.026, 0.023, then 0.015-0.019 with bias
+0.002-0.007) - a representation difference of the 0.25 phases against
the 0.50 pixels at that configuration, not a validity one; the guard
improves its tail (1351-1352: 0.015 -> 0.004).

**Run 2 - the shipped binary through a second storm** (`t208m-run2.log`,
fixture `8f389510`; mode 1 now carries the guard, mode 5 is the T207
control):

| segment (n) | ref flicker | **shipped** flicker | control flicker | **shipped ghost mean / p95** | control ghost mean / p95 | shipped bias | control bias | disocc ghost shipped / control |
|---|---|---|---|---|---|---|---|---|
| OUTSIDE (286) | 0.00010 | 0.00025 | 0.00027 | **0.00013 / 0.00034** | 0.00025 / 0.00065 | +0.00004 | -0.00010 | 0.025 / 0.036 |
| APPROACH (414) | 0.00054 | 0.00068 | 0.00068 | 0.00030 / 0.00080 | 0.00028 / 0.00075 | +0.00006 | -0.00002 | 0.020 / 0.022 |
| ENTRY (796) | 0.00128 | 0.00158 | 0.00157 | 0.00055 / 0.00089 | 0.00054 / 0.00089 | +0.00007 | +0.00007 | 0.042 / 0.042 |
| INTERIOR (704) | 0.00430 | 0.00450 | 0.00453 | **0.00166 / 0.00555** | 0.00174 / 0.00655 | +0.00036 | +0.00039 | 0.041 / 0.044 |
| route (2200) | 0.00196 | 0.00217 | 0.00218 | **0.00080 / 0.00106** | 0.00084 / 0.00109 | | | |

In this storm the guard is neutral-to-positive (route ghost 0.00084 ->
0.00080, interior p95 0.0066 -> 0.0056, OUTSIDE ghost halved with its max
0.57 -> 0.25, flicker identical) and the outlier set is **30 frames under
BOTH modes, the same frames** (1232-1241 and 1333-1352) - so those are not
validity episodes. Frames 1232-1241 are the run-1 signature again (the
reference's coverage steps 0.975 -> 0.960 at an opening, both lattices hold
+0.014-0.021 alpha for ten frames) but on the view axis under forward
motion, where the reprojection shift is sub-texel and the guard's motion
condition never triggers; the residual is the medium between the two
camera positions, which needs an along-ray term (camera density x
displacement, or an analytic transmittance correction of the history
from the phase's aux) - the "in-cloud fallback that blends rather than
switches" T207 named, outside T208. Frames 1333-1352 (coverage 0.42-0.62,
the reference itself moving 2-4% a frame, bias alternating -0.009 to
+0.012) are the single-phase representation's own error at that
configuration, identical under both modes and under T207.

Across the two storms: **shipped 5 / 30 frames of 2200 above 0.02
against 14 / 30 for the T207 validity**, route ghost mean 0.00069 /
0.00080 against 0.00081 / 0.00084, never worse on any segment's flicker,
the far segments untouched or improved. Nothing persists past the
three-frame cycle in either run: every episode ends with the structure
that caused it.

## THE 0.50 PRODUCTION BASELINE (Task 5)

The lattice (0.25 raw -> temporal -> 960x540, the shipping code with the
guard) against production 0.50, run 1 (clean session; run 2 in
parentheses, run 3 after the semicolon - three storms):

| | SIDE | PLAY_VIS_NEAR |
|---|---|---|
| 15./19. **cloud SSIM** | **0.7963** rain-heavy (0.9505 rain-light; **0.9627** run 3) | **0.9959** (0.9953; 0.9940) |
| 16./20. **edge SSIM** | 0.3784 (0.9436; 0.9502) | **0.9899** (0.9902; 0.9850) |
| 17./21. RMS | 8.35e-2 (1.55e-2; 1.42e-2) | 8.61e-3 (8.87e-3; 1.06e-2) |
| maxAbs | 0.991 (0.989; 0.989) | 0.990 (0.993; 0.989) |
| 18./22. thin / holes / dark interior / pockets | 0.966 / 0.067 / 0.861 / 0.399 (0.901 / 0.538 / 0.965 / 0.538; 0.896 / 0.379 / 0.967 / 0.507) | 0.923 / 0.959 / 0.986 / 0.717 (0.942 / 0.936 / 0.983 / 0.713; 0.844 / 0.891 / 0.983 / 0.643) |
| silhouette IoU | 0.9327 (0.9951; 0.9953) | 0.9910 (0.9883; 0.9908) |
| body only (the lattice's own contribution) | 0.9758 / edge 0.9915 (0.9311 / 0.9169; 0.9491 / 0.8978) | 0.9961 / 0.9906 (0.9955 / 0.9931; 0.9944 / 0.9884) |
| repeat capture | identical / 9 px / identical | identical / 83 px / 61 px |
| history accepted (static) | 100.00% (1-12 depth rejects of 388,800) | 99.99% (21-43) |

The representation is stable: repeats of the validated lattice are
digest-identical or within 9-83 pixels in the clean sessions, the class
maps identical between the shipping mode and the control, and every
rejection statically is a depth reject at the 1e-5 level.

## PERFORMANCE (Task 6) - 0.25 phases resolved to 960x540, composited to 1080p

Anchor-bracketed (production 0.25 one-frame on both sides of every
temporal cell), p50 of 60 samples. Run 1 is the baseline; run 2's GPU was
contended (the field build swung 1.3-19 ms cell to cell, the 0.50
reference cloud read 80 ms against run 1's 25 and T207's 15-18, the
one-frame anchors at PLAY_VIS_NEAR were slower than the temporal cells) and
only its stable components are quoted. Run 3 is a second clean session
(field 1.6-1.7, the 0.50 reference cloud 18 / 14 ms - T207's class).

| ms | SIDE run 1 / **run 3** (run 2) | PLAY_VIS_NEAR run 1 / **run 3** (run 2) |
|---|---|---|
| 23. **raw phase** (cloud draw at 0.25) | 5.66 / **5.31** (5.42) | 4.82 / **4.52** (4.20) |
| 24. field (storm field build) | 1.69 / 1.67 (contended) | 1.95 / 1.57 (contended) |
| rain gate build | 0.125 / 0.196 (0.126) | 0.125 / 0.123 (0.124) |
| 25. rain pass | 1.07 / 1.04 (1.09) | 1.59 / 1.57 (1.77) |
| 26. **resolve** (whole; p95) | **0.109 / 0.107** (0.115; p95 0.111-0.119) | **0.109 / 0.106** (0.112; p95 0.109-0.115) |
| reconstruction (mode 3) | 0.039 / 0.033 (0.042) | 0.038 / 0.033 (0.041) |
| 27. validation / reprojection (mode 1 - mode 3) | 0.070 / 0.074 (0.073) | 0.071 / 0.073 (0.071) |
| resolve, control mode 5 | 0.109 / 0.107 (0.115) | 0.108 / 0.106 (0.111) |
| 28. composite (1080p) | 0.097 / 0.086 (0.097) | 0.094 / 0.086 (0.095) |
| 29./30. **total** | **8.63** [8.40-8.77] / **8.22** [8.01-8.33] | **8.56** [8.51-8.64] / **7.85** [7.83-7.88] |
| anchors' total (production one-frame, same session) | 8.28 / 7.81 | 9.21 / 7.78 |
| temporal overhead (total - anchors) | +0.35 / +0.40 | -0.65 / +0.08 |
| production 0.50, same session: cloud / total | 25.2 / 29.4; 18.3 / 22.4 | 14.8 / 21.8; 14.0 / 18.7 |

The temporal path costs what the one-frame path costs plus the resolve:
the lattice's cloud draw against the anchors' is 0.99-1.02x in every
block, the resolve is 0.11 ms (0.04 reconstruction + 0.07 validation; the
guard adds nothing measurable: mode 5 and mode 1 within 0.001 ms), and
the swing between the temporal cells' totals and the anchors' (-0.65 to
+0.40) is the field build's session noise, both signs. **Class: SIDE 8.0-8.8, PLAY_VIS_NEAR
7.8-8.6 over the two clean sessions** - run 3 (8.22 / 7.85) sits at the top
of T207's 7.4-8.0 / 6.8-7.7, run 1 above it because that storm was
heavier (run 1's 0.50 reference cloud 25 ms against
T207's 15-18 at SIDE; its one-frame anchors 8.3 / 9.2 against T207's
7.4-7.7 / 6.8-7.5), not because the temporal path changed: the raw phase
at 5.3-5.7 / 4.5-4.8 is T207's 5.0-5.35 / 4.0-4.4 class plus the fixture.
The guard costs nothing: the resolve under mode 1 and under the no-guard
control agree within 0.001 ms in every session.

## T209 INPUT CONTRACT (Task 7)

What the spatial upscaler receives, exactly as the composite receives it
today (`VolumetricCloudRenderHook`, step 6: `compositeSource` is the
resolved target on a temporal frame):

31. **Resolution**: 960x540 at a 1920x1080 display - twice the phase
    resolution, `ceil(0.25 * display) * 2`; it follows the display (a 2x
    integer ratio to native at every display size, the phase at 0.25).
32. **Colour format**: RGBA16F, **premultiplied** rgb (straight colour =
    rgb / alpha), alpha = cloud opacity = `1 - transmittance` (body and the
    current frame's rain composited, `T = T_body * T_r`); alpha < 0.002 is
    written as (0,0,0,0) with depth 1.0.
33. **Depth / metadata**: a depth attachment (NDC, this frame's
    projection; the body's representative depth, the rain's own depth for
    pure-rain pixels, 1.0 for sky), the same size. The scene depth
    (`SceneDepthSampler`, native) is available for occlusion, as the
    composite uses it. No edge, velocity, coverage-class or aux metadata
    is exported at lattice resolution: the per-phase aux (body
    accumulation and transmittance at the rain's depth) and the class map
    (`DebugOutput = 1`) are phase-resolution / diagnostic surfaces; the
    validity classes could be exported alongside if T209 wants an
    "accepted / fallback" mask (one extra RGBA8 attachment on the resolve).
    Rain is **already composited** into the lattice; it is not separate.
34. **Native target**: 1920x1080 (the display; the composite's
    `mainTarget`). Pipeline: 480x270 raw phase -> 4-phase temporal
    accumulation -> 960x540 reconstructed cloud image -> spatial upscale
    -> 1920x1080. The insertion point is `CloudFieldCompositeRenderer.composite(compositeSource, mainTarget, ...)`,
    which today is a 4-tap depth-aware bilinear (same-surface test against
    the cloud depth and the scene depth, premultiplied accumulation,
    `CoverageAlphaReconstruction` option) costing 0.09-0.10 ms.

## T209 QUALITY TARGET (Task 8)

T209 compares 0.50 temporal + spatial upscale against the native
production reference (FINAL at 1.0, 1920x1080, one frame) - not against
0.50 - at SIDE and PLAY_VIS_NEAR, and reports cloud SSIM, edge SSIM, RMS,
maxAbs, silhouette IoU, thin retention, holes, dark interior, pockets, and
the T152 route's flicker / ghost / disocclusion ghost under motion (the
route's reference then being production 1.0 - a 960x540 ghost grid
downsampled from native, or the fixture's alpha grid at native). The
bar it inherits: at PLAY_VIS_NEAR the lattice is 0.995 against 0.50, so
the upscaler's loss against native should be the 0.50 -> 1.0 gap of the
composite it replaces, measured first (composite arm = today's bilinear,
the control every candidate must beat). At SIDE the rain-heavy case is
bounded by the rain representation (0.80 with rain, 0.98 body) at any
upscaler - T209 should report body-only alongside full-frame so an
upscaler is not blamed for the rain pass. Static screenshots alone are
not enough: shimmer under motion is the route's flicker column.

## T209 PERFORMANCE BUDGET (Task 9)

35./36. From run 1's totals (the clean session, the heavier fixture):
**SIDE: 8.63 ms -> budget to 8 ms none (-0.63), to 10 ms 1.37**;
**PLAY_VIS_NEAR: 8.56 -> to 8 none (-0.56), to 10 ms 1.44**. Run 3: **SIDE 8.22 ->
none to 8 (-0.22), 1.78 to 10; PLAY_VIS_NEAR 7.85 -> 0.15 to 8, 2.15 to 10.**
From T207's four sessions (7.43-7.95 / 6.83-7.67) the same arithmetic gives
0.05-0.57 / 0.33-1.17 to 8 and 2.05-2.57 / 2.33-3.17 to 10. The spatial
upscaler's maximum acceptable cost is therefore **~1.4 ms at the 10 ms
line at either pose** in the heaviest clean session (1.8-2.2 in run 3), and it must displace the
0.09-0.10 ms composite it replaces (net budget 1.3 ms). A 2x EASU-class
pass at 1080p is expected well under that; the 8 ms line is decided by the
fixture's raw march, not by T209.

## DECISION

38. **T208: PASS** - the 0.50 representation is stable (repeats identical
    or within 9-83 px, the class maps identical between the shipping
    mode and its control), motion is clean (route ghost 0.0007-0.0008, p95
    0.0011-0.0014, bias <= +0.0004, flicker at the reference's, 5 / 30 of
    2200 frames above 0.02 in two storms - coverage transitions and two
    in-cloud representation episodes, identical under T207's validity,
    nothing persisting past the 3-frame cycle), SIDE's ceiling is
    explained (rain at phase resolution where the frame carries rain; the
    body lattice's own 0.93-0.95 otherwise, neither one a validity loss:
    trust-all = validated to four digits), no validity problem remains
    (static acceptance 99.99-100.00%; the near-field guard removes the
    off-axis in-cloud openings episode; the on-axis one is an along-ray
    parallax residual named for the follow-up), production stays in its class (the
    temporal overhead is the 0.11 ms resolve; the session totals follow
    the fixture), and T209's contract is written above.
39. **Production temporal remains enabled: YES** (`PRODUCTION_TEMPORAL_MODE = 1`,
    now with the near-field guard).
40. **Exact SIDE quality limiter**: the rain pass at phase resolution
    (480x270, block-replicated over the 960x540 lattice, the current frame's
    by design) wherever the frame carries rain - run 1: 0.796 with rain,
    0.976 without; and, in a rain-light SIDE frame, the body lattice's own
    ~0.93-0.95 ceiling against 0.50 (the single 0.25 frame is 0.92, four
    phases 0.95, trust-all 0.936) - the interior's sub-texel point sampling.
    Not correspondence, not the mixed rain/body ordering (the resolve's
    composite is the in-march equation and reproduces a stationary frame),
    not validity.
41. **T209 recommendation**: SPATIAL UPSCALE EVALUATION on the contract
    above - the 960x540 premultiplied RGBA16F + depth lattice to 1920x1080
    against the native production reference, arms = today's depth-aware
    bilinear composite (control), FSR1/EASU (no RCAS until quality is
    read), and one lightweight depth-guided alternative (a 2x
    edge-directed / depth-aware reconstruction on the same taps); judge
    body-only and full-frame separately at SIDE, and run the T152 route
    against native; cost cap ~1.3 ms net. Before or beside it the rain
    residual: a rain pass at lattice resolution (960x540, 1.1-1.6 ms today
    at 480x270, so ~2.5-4 ms naively - which the budget does not have) or
    a guided reconstruction of the current frame's rain from its phase-
    resolution pass with the lattice body as the guide; and the on-axis
    in-cloud residual (an along-ray parallax term: camera density x
    displacement along the ray, refusing or correcting the history).

## VALIDATION

42. `campaignGate` PASS x3 (before each build that reached a launch; the
    T208 invariant among them). 43. `check build` PASS (1m 4s warm).
44. Temporal invariants (`validateT208TemporalCleanup`, beside T207's
    `validateT207TemporalReconstruction`): the near-field guard ships in
    the production mode as the conjunction of distance, motion and
    ambiguity with mode 5 as the control, and is its own class; the resolve
    carries no phase-scale branch; the attribution switches are reset by
    every timing arm; the route is production 0.50, mode 1, mode 5 through
    one fixture; no 0.125 arm exists; `PHASES = 4`, `MAX_AGE_FRAMES = 3`,
    `PRODUCTION_TEMPORAL_MODE = 1`. 45. Lifecycle: the T199 probe ran in
    every launch (`T199_LIFECYCLE ... done`, phases and the resolved target
    released on disable, re-allocated on the next temporal frame, the
    reload re-linked the programs). 46. Program registration: unchanged
    (four production programs; the resolve is a field-pass shader); no
    JSON change. 47. Variant scope: no new cloud variant (`T191_VARIANT_SCOPE
    registered=0` with T208 armed; every arm is FINAL). 48. Markers: T208M
    `.done`; T208 `.done`. 49. git status: the task's files plus the
    preserved logs. 50. Commit: the T208 commit on `experiment/cloud-descriptor-k` (parent
    `73cabab`), not merged.
