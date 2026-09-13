# T207 - temporal reconstruction + history validity: four raw phases, once-reprojected, validated by the current frame's evidence; the resolve costs 0.1 ms

Temporal lattice reconstruction on the T206 production renderer. Four
launches of the static/timing campaign on 13Sep2026
(`t207-campaign/t207-run1.log` 11:24-11:29, `t207-run2.log` 11:33-11:38
with repeat captures, `t207-run3.log` 11:40-11:45 with the unjittered rain
pass and the wider new-sky test, `t207-run4.log` 11:47-11:52 with the
motion-gated validity) and two launches of the moving-camera route
(`t207-campaign/t207m-run1.log` 11:53-11:55, `t207m-run2.log` 11:59-12:01
with the parallax guard - the code that ships; 2 x 2200 frames each). SIDE and PLAY_VIS_NEAR, Ultra 96 steps, the shipped ping-pong
history off, one deterministic fixture a pose (a new one each launch: quality
is compared only within a run, T199 lesson 2).

Starting point: `d8fc78c` (T206). Production at 0.25: SIDE ~7.25 ms,
PLAY_VIS_NEAR 6.2-7.1 ms. The temporal evidence this builds on is
`experiment/cloud-temporal-reconstruction` (T165, `e486797`..`9bafb25`,
`.pa-agents/worktrees/cloud-temporal/.../cloud-temporal-reconstruction.md`):
four subpixel phases, separate raw buffers, once-reprojected from their own
origin frames keep 62-84% of the stationary lattice gain while a recursive
accumulation buffer collapses it to 11%; and the blocker was correspondence -
a point comparison between the depths of two DIFFERENT subpixel rays rejected
exactly the thin structure the lattice recovers (43.5% of the gap at zero
motion against a 73.1% trust-all, -73.8% under moderate translation without
rejection). That report's own last line asked for "a correspondence model
that does not compare unrelated subpixel rays (for example, per-phase
projected coverage plus explicit disocclusion evidence)". This is that.

## RECOVERY (Task 0)

1. **Starting HEAD**: `d8fc78c`. Working tree carried the T195-T206 logs,
   preserved.
2. **Production pipeline** (T206, unchanged on a non-temporal frame): storm
   field build -> rain gate build (`rain_gate_build`) -> rain pass
   (`rain_pass`, RGBA32F cloud-sized, unit 17) -> split cloud (`lean_final`,
   composites the rain at `t_R`) -> `CloudFieldCompositeRenderer`
   depth-aware upsample to the display. Cloud target 480x270 at 0.25 of
   1920x1080; RGBA16F premultiplied + depth; the shipped in-shader history
   blend (`HistorySampler`, `mix(result, history, historyWeight)`) is a
   recursive accumulation and is never used on a temporal frame.
3. **History resources before T207**: the ping-pong cloud pair only. The
   T165 prototype's pieces (`CloudTemporalPhaseState`, phase targets with an
   MRT aux attachment, a resolve pass, the composite's jitter subtraction)
   lived on the experiment branch and its worktree, never merged.

## REPRESENTATION (Tasks 1-3, 7)

4. **Phase layout.** Four phases, one marched per frame
   (`CloudTemporalPhaseState.advance()` before the target is chosen), each
   into its own raw target at the cloud (phase) resolution, with a true
   screen-space ray offset of a quarter phase pixel: `(-,-), (+,-), (-,+),
   (+,+)`, so phase `(x&1) + 2*(y&1)` owns lattice pixel `(x,y)` of a target
   of twice the phase resolution (960x540 at 0.25). The offset is applied to
   the march's texture coordinate before the ray is built
   (`paViewTexCoord = texCoord + PaSampleJitter`, uploaded in
   texture-coordinate units), so consecutive frames trace different lines
   through the medium - not the sub-step phase along one line that the
   shipped `integrationBlue` jitter moves. Every four frames the four rays
   tile the lattice exactly; the resolve's fetch of the current phase at
   zero bilinear fraction reproduces the stationary oracle. No FSR/EASU.
5. **Stored raw channels, per phase**: colour attachment 0 the body's
   premultiplied rgb and alpha (RGBA16F) - the march with the split rain
   composite DEFERRED; the depth texture (the body's representative NDC
   depth); colour attachment 1 (RGBA16F, `fragTemporalAux`) the body's
   accumulation before the rain's depth and its transmittance there
   (`before`, `T_before`; the whole body when the ray carries no rain).
   4 x 480x270 x 20 B = 10.4 MB. The resolved lattice: RGBA16F + depth,
   960x540, 6.2 MB.
6. **Validity metadata, per phase** (CPU): the camera it was rendered from
   (`projection * viewRotation`, position), the rendered-frame index (a
   phase older than three rendered frames is invalid), the generation
   (every invalidation drops all four). Per frame the resolve receives, for
   each phase, its matrices, its camera delta from this frame's camera
   (camera-relative, so absolute coordinates never enter the shader), its
   jitter, and its validity. No stored per-pixel validity: the correspondence
   evidence is the CURRENT phase's neighbourhood, computed in the resolve.
7. **Reprojection inputs**: this frame's `InvProjMat`, `InvViewRotMat`,
   `CloudViewProjMat`, `MaxRenderDistance`, the current phase index and, for
   the current frame's rain, the rain-pass target. The resolve's JSON names
   exactly the four phase colours, the four phase depths and the rain pass;
   the four aux textures are bound manually on units 12-15 (Minecraft
   tracks twelve JSON samplers). `T207_FRAG_DATA program=lean_final
   fragColor=0 fragTemporalAux=1` - the linker's assignment, logged rather
   than assumed.

**Rain (Task 7): option B, exact.** A phase never composites rain; the
resolve composites THIS frame's rain texel onto whichever phase's body it
selected, at the point that phase split its body:
`A = before + T_before * R + T_r * (body - before)`, `T = T_body * T_r` -
the in-march equation, so a stationary frame reproduces the production
composite. Rain is never reconstructed from history (no rain ghosting by
construction), and the rain pass is drawn UNJITTERED on a temporal frame:
run 2 showed a jittered rain pass rotating each 2x2 block's rain through four
subpixel rays - a four-frame rain flicker - fixed in run 3. The cost of this
choice is that rain stays at phase resolution (block-replicated over the
lattice), which is what caps SIDE below the 0.50 reference (below).

## VALIDITY (Tasks 4-6, 10)

Each lattice pixel not owned by the current phase is a history candidate.
Its owner phase is reprojected ONCE from its own recorded camera - the
current block's own hit distance, else the nearest hit in the current 3x3,
else the volume's far end - and sampled once (jitter-corrected bilinear of
body and aux, nearest-texel depth). It is then judged against the CURRENT
phase's evidence in the 3x3 phase-texel neighbourhood of the site: the
interval of coverage (alpha min/max) and of linear ray distance the current
phase reports, carried through the same warp into the history camera's
units. Rejection classes: `offscreen` (the reprojection leaves the phase),
`age` (a phase older than the cycle or dropped by an invalidation),
`disocclusion` (substantial history cloud, alpha > 0.25, against an all-sky
neighbourhood; history sky against an all-cloud one), `coverage` (history
alpha outside the padded interval), `depth` (history distance outside the
interval, tolerance 6% + half the interval width, floor 2 blocks), `colour`
(mode 2 only: a 1.5-sigma variance clip). A rejected site falls back to the
current phase's jitter-corrected bilinear reconstruction - this frame's
answer at shipped quality - never to stale content.

**Disocclusion needs motion.** Run 2-3 found the T165 failure in miniature:
at PLAY_VIS_NEAR 191-233 sites where the history ray hit a wisp that all
nine current texels missed were classed as new sky, and trust-all - which
accepts them - scored 0.997 against the validated 0.964-0.971; every one of
those sites was correct. So where the reprojection moved the sample by less
than half a phase texel, coverage and disocclusion are not judged (the two
rays differ by the lattice offset alone and their disagreement is
sub-texel structure); the depth interval still is. Past half a texel the
tests are fully armed. Under motion, a genuinely disoccluded site has by
definition moved.

Counters (run 4, the shipping validity; a class-map capture per pose,
388,800 history candidates a frame):

| | SIDE | PLAY_VIS_NEAR |
|---|---|---|
| 8. **accepted** | **99.999% (388,795)** | **99.994% (388,777)** |
| 9. depth rejects | 5 (0.001%) | 23 (0.006%) |
| 10. coverage rejects | 0 | 0 |
| 11. disocclusion rejects | 0 | 0 |
| 12. offscreen rejects | 0 | 0 |
| age/phase rejects | 0 | 0 |
| colour rejects (mode 2, not shipped) | 271 (0.07%) | 695 (0.18%) |

Static, so these are the false-rejection floor: 0.001-0.006%. Before the
motion gate (run 3) the same poses rejected 63 / 313 sites (0.02% / 0.08%),
233 of them the disocclusion class. Under motion the rejection rate is the
route's (below).

## STATIC QUALITY (Task 8) - the resolved lattice against production at 0.50

The reference is production FINAL at 0.50 (960x540, the lattice's exact
target: four 0.25 phases perform exactly the lateral samples of one 0.50
frame), captured first at each pose; every arm is the same program at 0.25
phases resolved to 960x540. `@t3` is the no-history baseline (the current
phase alone through the resolve pass - what the composite makes of one
frame), `@t1` the shipping lattice, `@t2` the lattice plus the colour clip,
`@t4` trust-all (the stationary ceiling; ghosts by construction under
motion). Run 4, the shipping code; runs 1-3 in the campaign logs.

| | SIDE @t3 baseline | **SIDE @t1** | SIDE @t4 ceiling | PVN @t3 baseline | **PVN @t1** | PVN @t4 ceiling |
|---|---|---|---|---|---|---|
| 13. **cloud SSIM** | 0.9545 | **0.9514** | 0.9515 | 0.8128 | **0.9952** | 0.9954 |
| 14. **edge SSIM** | 0.8953 | **0.9307** | 0.9308 | 0.6925 | **0.9931** | 0.9943 |
| 15. RMS | 1.73e-2 | **1.53e-2** | 1.53e-2 | 4.56e-2 | **7.4e-3** | 6.7e-3 |
| 16. maxAbs | 0.988 | 0.988 | 0.988 | 0.992 | 0.990 | 0.989 |
| mean abs | 2.03e-3 | 1.45e-3 | 1.45e-3 | 5.49e-3 | 2.78e-4 | 2.59e-4 |
| changed pixels (of 518,400) | 68,540 (13.2%) | 63,155 (12.2%) | 63,155 | 47,275 (9.1%) | 34,288 (6.6%) | 34,211 |
| 17. thin retention | 0.949 | **0.951** | 0.951 | 0.831 | **0.874** | 0.877 |
| 18. hole retention | 0.707 | **0.850** | 0.850 | 0.603 | **0.980** | 0.980 |
| 19. dark interior | 0.964 | 0.955 | 0.955 | 0.946 | **0.984** | 0.984 |
| 20. shadow pockets | 0.341 | **0.586** | 0.586 | 0.346 | **0.711** | 0.710 |
| silhouette IoU | 0.981 | 0.9963 | 0.9963 | 0.854 | 0.9844 | 0.9844 |
| colour clip (@t2) SSIM / edge | - | 0.9306 / 0.8667 | - | - | 0.9525 / 0.9183 | - |
| repeat of the same arm (digests) | equal | equal | differs (see below) | equal | 175 px | 4 px |

**PLAY_VIS_NEAR passes the gate with margin**: the validated lattice is the
stationary ceiling (0.9952 against trust-all's 0.9954, edge 0.9931), against
0.8128 for the single frame; holes 0.60 -> 0.98, pockets 0.35 -> 0.71,
thin 0.83 -> 0.87. Across the four runs the validated lattice scored 0.965,
0.964, 0.971 and 0.995 at this pose - the last is the motion gate.

**SIDE does not reach 0.9835 against the 0.50 reference, and neither does
anything at SIDE.** The validated lattice equals trust-all to four digits
(0.9514 / 0.9515: validity costs nothing here either), so the ceiling is the
representation, not the correspondence. The lattice is 0.003 below the
single-frame baseline in cloud SSIM and ahead of it on every structural
term: edge SSIM 0.895 -> 0.931, holes 0.71 -> 0.85, pockets 0.34 -> 0.59,
silhouette IoU 0.981 -> 0.996, thin 0.949 -> 0.951; dark interiors 0.964 ->
0.955. T165 found the same ranking (SIDE is not resolution-limited; its
interleave was slightly below a single frame while FAR gained 0.108), and
this pose adds a second cause: SIDE has 1.75% rain pixels against
PLAY_VIS_NEAR's 0.64%, and the lattice's rain is the current frame's at
phase resolution, block-replicated, where the 0.50 reference marches rain
per lattice pixel - every shaft edge differs by a texel. The historical
0.9835 was a FAR number; the pose that is resolution-limited here reaches
0.995. The colour clip (mode 2) is rejected: it costs 0.02-0.04 SSIM at
both poses by clipping exactly the sub-texel detail the lattice recovers.

Two harness observations, recorded rather than hidden. The single-phase
baseline is not one image but four (each phase is a different frame): its
repeats agree only when the capture lands on the same phase of the cycle
(runs 2-4: identical at PLAY_VIS_NEAR, 80k pixels apart at SIDE in run 3).
And the trust-all diagnostic at SIDE scored worse on its second capture in
runs 2-4 (0.9716/0.9787, 0.942/0.955, 0.937/0.952) with every phase valid
(`T207_CAPTURE_PHASES validPhases=4`), while the validated lattice's two
captures were digest-identical each time; the cause is not established and
does not touch the candidate - trust-all is diagnostic and its first capture
matches the validated lattice to four digits.

## PERFORMANCE (Task 11) - 0.25 phases, resolved to 960x540, composited to 1080p

Anchor-bracketed (production 0.25 non-temporal on both sides of every
temporal cell), p50 of 60 samples, four runs. The temporal frame's cloud
draw against the anchors' (`T207_ARM`): SIDE 0.989 / 1.018 / 1.001 / 0.987,
PLAY_VIS_NEAR 0.982 / 0.979 / 1.002 / 0.983, spread 0.2-1.7% - the phase
march (jittered ray, deferred rain split, the aux write) costs the same as
the production march within the session's noise.

| ms | SIDE (runs 1 / 2 / 3 / 4) | PLAY_VIS_NEAR (1 / 2 / 3 / 4) |
|---|---|---|
| 27. **raw phase** (cloud draw at 0.25) | 5.11 / 5.18 / 5.35 / 5.02 | 4.05 / 4.00 / 4.14 / 4.41 |
| 28. field (storm field + gate build) | 1.09 / 1.52 / 1.09 / 1.55 | 1.08 / 1.29 / 1.09 / 1.47 |
| 29. rain pass | 1.04 / 1.06 / 1.07 / 1.05 | 1.53 / 1.60 / 1.51 / 1.60 |
| 30. **reconstruction** (the resolve with every history sample refused, mode 3) | **0.030 / 0.032 / 0.031 / 0.032** | 0.030 / 0.032 / 0.030 / 0.032 |
| 31. **validation / reprojection** (mode 1 minus mode 3) | **0.066 / 0.065 / 0.066 / 0.071** | 0.065 / 0.064 / 0.064 / 0.072 |
| resolve, whole (p95) | 0.095 / 0.097 / 0.096 / 0.103 (0.10-0.11) | 0.095 / 0.096 / 0.094 / 0.104 |
| 32. composite (1080p, unchanged) | 0.087 / 0.093 / 0.088 / 0.083 | 0.084 / 0.092 / 0.083 / 0.083 |
| 33./34. **total** | **7.43 / 7.95 / 7.69 / 7.81** | **6.83 / 7.08 / 6.92 / 7.67** |
| anchors' total (production 0.25, same session) | 7.39 / 7.70 / 7.61 / 7.39 | 6.82 / 6.98 / 6.82 / 7.51 |
| temporal overhead (total - anchors) | 0.04 / 0.24 / 0.08 / 0.41 | 0.02 / 0.10 / 0.09 / 0.16 |
| production 0.50 in the same session: cloud / total | 14.7 / 18.3; 18.2 / 22.5; 15.8 / 19.6; 16.9 / 20.9 | 10.7 / 14.7; 11.6 / 15.4; 11.2 / 15.1; 13.4 / 17.9 |

**The reconstruction overhead is 0.10 ms** - the resolve's own clock, 30 us
of lattice fetch and 65-70 us of reprojection and validation - in the
brief's "excellent" band (<= 0.75). The larger "overhead" figures are the
session's field-build swing landing on the temporal cells (run 4 SIDE: field
1.55 in the temporal cells against 1.31 in the anchors'; the raw phase itself
was 0.07 ms FASTER than the anchors'). Totals: SIDE 7.4-8.0, PLAY_VIS_NEAR
6.8-7.7; against the same sessions' 0.50 production (the image the lattice
reconstructs) 18.3-22.5 / 14.7-17.9 - the lattice returns the 0.50 image's
structure for 0.36-0.43 of its cost.

Raw scale (Task 12): not priced. The 0.25 phase pattern is the accepted
configuration, and it is the temporal budget rule that decides whether a
lower raw scale is wanted, not this campaign; a 0.125 phase would reconstruct
a 0.25-class lattice and would be judged against production 0.25.

## HISTORY SAFETY (Tasks 13-15)

35. **Recursive feedback absent** - the sandbox's T207 invariant: the
    resolve's JSON samplers are exactly the four phase colours, the four
    phase depths and the rain pass; its GLSL declares exactly the four aux
    beyond them and names no resolved or history source; the resolve
    renderer binds `Phase<n>Color/Depth` from `phaseTarget(n)` and the aux
    from `phaseAuxTexture(n)` only, never the resolved target, and records
    nothing; the march writes a phase only when `temporal` and records it
    only after a raw production draw; a temporal frame never blends the
    ping-pong history (`historyValid = !temporal && ...`); the resolved
    target is prepared in the resolve pass alone and consumed by the
    composite and the captures. Only raw phase textures enter the history
    set.
36. **Lifecycle clears**: `VolumetricCloudRenderer.invalidateHistory()` -
    the camera cut (> 24 blocks or > 0.22 rad), a resize, a level or
    dimension change, a resource reload, the pass disabled, a material
    advection discontinuity - drops every phase (`invalidateAll`); the
    next three frames resolve from the current phase alone (the fallback,
    never black, never stale). The probe (run 1-4, `T199_LIFECYCLE`):
    `T207_PHASES_ALLOC ... T207_RESOLVED_ALLOC` on the first temporal frame;
    disable -> `T207_PHASES_RELEASE reason=shutdown`, `T207_RESOLVED_RELEASE
    reason=shutdown`; enable + resource reload -> `temporal[phases=none
    resolved=none] phaseInvalidations=1` through the reload, the programs
    re-linked; re-allocation on the next temporal frame; every campaign
    arm switch -> `reason=temporalOff`; the client's exit -> `shutdown`.
    Both targets are in `destroyAll` with every other target; the resolve's
    clock closes with the renderer's.
37. **Resize / reload safety**: a phase-size change re-allocates the four
    phases and the resolved target and invalidates the set
    (`preparePhaseTargets`, `prepareTemporalResolvedTarget`); the capture
    harness waits for a full valid set (`T207_CAPTURE_PHASES validPhases=4`)
    so no measurement reads a partial lattice; a resolve that cannot run
    (no shader, no phases) composites the current phase directly.

Production failure mode (Task 14): a history sample that is invalid,
offscreen, aged or rejected falls back per pixel to the current phase's
reconstruction; a frame with no valid current phase, a missing resolve
program or missing targets composites the current phase target through the
existing composite - the T206 image at shipped quality. No frame is black
and none is stale by more than the three-frame cycle.

## MOTION QUALITY (Task 9) - the T152 route, production 0.50 against the temporal 0.25 lattice

The T152 moving-camera fixture drives one continuous route - outside ->
approach -> entry -> interior with openings -> exit - at a constant angular
step (~14 px/frame against the nearest cloud surface; 1.6 blocks/frame inside
the body), 2200 frames per arm on one storm, camera motion only (the fixture
freezes cloud motion). Arm 1 is production at 0.50 (960x540, the lattice's
target); arm 2 the temporal lattice from 0.25 phases, and every frame of arm
2 is compared pixel by pixel against the same frame index of arm 1: `ghost`
is the alpha difference over the frame, `disoccGhost` the same restricted to
pixels whose coverage changed in the reference between the previous frame and
this one - exactly the pixels a reprojection has no valid history for.
`t207m-run1.log` (11:53-11:55) is the validity of run 4; `t207m-run2.log`
(11:59-12:01) adds the parallax guard, the code that ships. Both routes
retried once on a fresh fixture (the first frame found no lobes) and then
completed with the storm confirmed in every frame.

| segment (n) | 0.50 flicker | temporal flicker | ghostMean (run 1 / 2) | ghost p95 | ghostMax | ghostBias | disoccFrac | disoccGhost (1 / 2) |
|---|---|---|---|---|---|---|---|---|
| 21. **OUTSIDE** (284), slow, 5.4 blocks/frame at 600+ blocks | 0.00015 | 0.00030 | 0.00035 / **0.00038** | 0.00102 | 0.75 | +0.0003 | 0.0003 | 0.055 / 0.055 |
| 22. **APPROACH** (419), fast, the storm filling the frame | 0.00064 | 0.00078 | 0.00039 / **0.00028** | 0.00076 | 0.41 | +0.00004 | 0.0015 | 0.037 / 0.020 |
| 24. **ENTRY** (795), the storm edge crossing the frame | 0.00131 | 0.00150 | 0.00055 / **0.00058** | 0.00105 | 0.73 | +0.00007 | 0.0012 | 0.041 / 0.051 |
| 25. **INTERIOR** (702), inside the body, openings, sky disocclusion | 0.00432 | 0.00442 | 0.00174 / **0.00125** | 0.00413 | 1.00 | +0.0005 | 0.0041 | 0.042 / 0.044 |
| route (2200) | 0.00199 | 0.00214 | 0.00087 / **0.00071** | 0.00139 | | | | |

23. **Rotation**: the route's angular step is a yaw/pitch of 0.0092 rad every
frame throughout - every segment above is under rotation as well as
translation; there is no rotation-only segment in this fixture.

26. **Ghosting assessment.** The temporal lattice under motion differs from
the 0.50 image it reconstructs by a mean alpha of 0.0007 over the route
(p95 0.0014), with a bias within +0.0005 of zero at every segment - no
trailing smear in the mean. Its flicker is the 0.50 reference's (route
0.00214 against 0.00199; interior 0.00442 against 0.00432), and both are
below the shipped 0.25 renderer's T152 baseline (interior 0.00581). What
remains is transient and inside the body: 11 frames of 2200 (0.5%) with a
frame-mean alpha error above 0.02 - two episodes, frames 1231-1238 and
1332-1341, the camera inside cloud (`cameraDensity` 0.08-0.17) passing
openings whose coverage changes 1-3% a frame - peaking at 0.035 with a bias
of +0.01-0.02: for those frames the lattice keeps a little more cloud than
the reference at the openings. The parallax guard halved the count of such
frames (23 -> 11) and the interior ghost mean (0.00174 -> 0.00125); the
disoccluded pixels themselves sit at 0.02-0.055 alpha error in both runs,
which is the single-phase fallback's own error at a fresh edge (a 0.25 ray
against a 0.50 pixel). Nothing persists: a history sample is at most three
frames old and is reprojected once from its own raw phase, so an episode
ends with the structure that caused it. No old-cloud silhouette survives a
cut: every invalidation drops all four phases. No recursive smearing exists
to measure: the resolved frame is never an input.

The static harness is unchanged by the parallax guard (`camDelta` is zero
at a fixed camera), so run 4's static numbers are the shipping code's.

## DECISION

38. **T207: PASS.** The temporal lattice is non-recursive by construction
    and by invariant; the validity model built on the current frame's
    neighbourhood evidence removed the T165 blocker (99.994-99.999% of
    correct history accepted statically, the validated lattice equal to
    trust-all to four digits at both poses); PLAY_VIS_NEAR reconstructs the
    0.50 production image at 0.995 SSIM / 0.993 edge from 0.25 phases; SIDE
    holds SSIM parity with the single frame and gains on edge (0.895 ->
    0.931), holes (0.71 -> 0.85) and pockets (0.34 -> 0.59); the moving
    camera shows no persistent trail, no invalid silhouette, no smearing;
    the resolve costs 0.10 ms.
39. **Production temporal candidate: YES.** `PRODUCTION_TEMPORAL_MODE = 1`:
    the lattice is the production path (one constant returns the one-frame
    T206 path). Rain stays current-frame, at phase resolution.
40. **Total vs 8 ms**: SIDE 7.43 / 7.95 / 7.69 / 7.81, PLAY_VIS_NEAR 6.83 /
    7.08 / 6.92 / 7.67 - under 8 in every session, with the temporal
    overhead 0.02-0.10 ms of it (the rest is the field build's session
    swing, which the non-temporal anchors show too: 7.39-7.70 / 6.82-7.51).
41. **Total vs 10 ms**: 2.0-3.2 ms of margin at both poses.
42. **Exact next task.** The static ceiling at SIDE is the representation:
    the rain at phase resolution (block-replicated over the lattice) and the
    interior's sub-texel point-sampling, not correspondence. The next task
    is therefore not more validity work but the two residuals the numbers
    name: a rain pass at lattice resolution (or its reconstruction from the
    current frame's phase-resolution rain with the body's own lattice as the
    guide) and the in-cloud openings episodes (a near-field fallback that
    blends rather than switches). Before either, the T200-class pixel-scaling
    question the lattice reopens: with a 0.25 phase reconstructing a 0.50
    image, whether a 0.125 phase reconstructing the current 0.25 image at a
    ~2 ms march is the better production point (Task 12, deliberately not
    priced here).

## VALIDATION

43. `campaignGate` PASS x5 (before each launch; the T207 invariant among
    them). 44. `check build` PASS. 45. Temporal invariants: `validateT207TemporalReconstruction` - the resolve's
    inputs are exactly the raw phases and the rain pass (JSON samplers, GLSL
    samplers, the renderer's bindings), the resolved target is never bound
    as a sampler and never recorded, a phase is recorded only after a raw
    production draw, a temporal frame never blends the ping-pong history,
    the jitter is the quarter-pixel lattice applied to the ray's texture
    coordinate and the resolve's lattice index matches its order, a phase
    older than the cycle is refused, the phase defers the rain composite and
    the resolve composites the current frame's rain with the in-march
    equation, FINAL keeps the two temporal uniforms live and the volume JSON
    names them, the temporal targets release with every other target and
    history invalidation drops the phases, the resolve's clock closes on
    shutdown, both campaigns are wired, the resolve compiles in GL. 46.
    Program registration: unchanged - four production programs, the resolve
    is a field-pass shader beside the composite (`CloudFieldVolumeShaders`),
    `T191_VARIANT_SCOPE registered=0` with T207 armed (every arm is FINAL).
    47. Variant scope: no new cloud variant. 48. Markers: T207 and T207M
    `.done`. 49. git status: task files only (logs preserved). 50. Commit:
    the T207 commit on `experiment/cloud-descriptor-k` (parent `d8fc78c`),
    not merged.
