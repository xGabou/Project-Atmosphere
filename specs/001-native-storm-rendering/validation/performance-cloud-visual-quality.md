# T211 - Cloud visual quality pass

A visual pass, with performance as a regression constraint rather than the
subject. The renderer architecture accepted through T210 was not reopened.

**Outcome in one line: the defect audit stands, both attempted changes were
measured and rejected, and production rendering is unchanged.** No visual
change is banked. The value T211 delivers is a grounded ranking of what is
actually wrong, two mechanisms understood well enough to aim the next
attempt, and the harness needed to judge a visual change honestly.

## BASELINE

**1. Starting HEAD.** `76e27b0` on `quality/t210-rain-temporal` (T210,
"measure rain at the lattice and the along-ray guard; bank neither"). Tree
clean apart from the user's `PAchangelog.md` edit, which is preserved and
uncommitted.

**2. Production renderer state.** Split rain architecture, shared field,
temporal reconstruction at mode 1 on the 0.25 phase grid (0.50 effective
lattice), depth-aware bilinear composite. `PRODUCTION_TEMPORAL_MODE = 1`,
`PRODUCTION_SPATIAL_UPSCALE = -1`, `PRODUCTION_RAIN_LATTICE_MODE = 0`.
Tuning: density 1.45, coverage 1.25, extinction 0.115, history blend 0.85,
ULTRA 96 march steps / 6 light steps. Morphology constants
`STORM_EROSION = 0.44`, `STORM_CORE_FILL = 0.45`, base octaves at
50 / 25 / 12.5 blocks, detail 22.7 down to 1.4 blocks - all pinned by
`stormDensityThresholdSandbox` to +-0.010 drift, so a noise-constant change
is a three-file change (shader, sandbox, `morphology-thresholds.md`), never
a one-line tweak.

**3. Reference poses.** The T098 capture set, which already covers T211's
required views: `1_FAR`, `2_SIDE`, `3_UNDER`, `4_INSIDE`, `5_ABOVE`,
`6_LATERAL_A` / `7_LATERAL_B`, `8_NEAR_EDGE`, plus a 1.20-1.80x distance
ladder. Full native 1920x1080 screenshots of ordinary production frames -
no debug view, no diagnostic optimization mode.

**Two harness gaps had to be closed before any frame could be captured.**
The capture set is reachable only after the legacy `stormPerformanceSuite`
completes, and that suite now times out in MOVE_TO_VIEW
(`required_render_samples_timeout`, 4 of 4 attempts) - it has not been on
the campaign path since T166. Rather than debug it (out of T211's scope), a
`run/t211-captures.txt` marker routes a matured storm straight to the
capture phase; it is registered as harness machinery and **always defers to
a campaign, ray-trace or motion marker**, so leaving it armed cannot hijack
a campaign. The capture driver then needs the suite fixture that the suite
would have created on its way past, so on this route the driver creates it
directly through `beginStormPerformanceBaseline` - which only resolves the
adopted storm and its poses, starts no suite session and changes no
renderer state.

**4. Baseline screenshots.** `validation/t211-campaign/baseline/`
(`2_SIDE`, `6_LATERAL_A`, `5_ABOVE`, `8_NEAR_EDGE`, `4_INSIDE`).

Two of the eight reference views were **unusable this run** and are
reported rather than quietly dropped: `1_FAR` put the camera inside jungle
foliage and `3_UNDER` inside ground fog, both showing terrain instead of
cloud. The poses are derived from the fixture's radius and base height, so
on a storm over high terrain they can land underground. That is a fixture
placement defect, not a cloud defect, and it means T211's audit rests on
the side, lateral, above, near-edge and inside views only.

## DEFECT AUDIT

Ranked by how visible each is in ordinary play, from the frames - not from
reading the shader.

**5. Defect 1 (worst): the anvil reads as a smooth geometric lens, not a
cloud.** In `2_SIDE` and `6_LATERAL_A` its silhouette is a near-perfect
ellipse. `5_ABOVE` shows the top as a clean arc with fine detail painted
flat across it and no form shadowing - texture without relief. `8_NEAR_EDGE`
shows the underside as a uniform dark sheet with a smooth edge, no
mammatus, no lumpiness. It is the largest object on screen and the most
artificial thing in the frame; the storm reads as a mushroom cap rather
than a cumulonimbus canopy.

**6. Defect 2: horizontal step terracing across the anvil flanks.** A
regular luminance ripple, strongest where the surface runs oblique to the
view. Measured on the baseline `6_LATERAL_A`: dominant period **6-11 px**,
ripple RMS 0.6-1.3, **peak-to-peak up to 12.7/255**, autocorrelation
0.32-0.49 at the period - genuine periodicity, not noise. On the later
paired frames it reaches **17-20 peak-to-peak**. Unlike defect 1 this is
not a matter of taste: it is an artifact, and it reads as stacked plates or
contour lines.

**7. Defect 3: the lower mass is over-eroded and the tower is undefined.**
In `2_SIDE` and `6_LATERAL_A` the base is speckled with holes and reads as
dirty foam rather than cloud, and the base-to-anvil connection is a thin
wispy neck rather than an updraft tower.

Runner-up, deferred: `4_INSIDE` is a flat featureless wash with essentially
no interior density variation, no shadow pockets and no bright/dark
balance. Real, but a far less commonly seen view than the exterior
silhouette, so it did not displace any of the three above.

## CHANGES

**A methodological correction that governs every result below.** The
storm's structural fingerprint differs on **every launch** (`68e58c71...`
vs `8bb0c64d...`; the T210 campaign logs show the same - three runs, three
storms). An across-run before/after therefore compares two different
storms. The first comparison run in T211 did exactly that and produced
encouraging numbers (ripple -31 to -56%) that meant nothing: silhouette IoU
between the two sets was **0.67-0.72**, far too low for a sampling-only
change. Every measurement below is instead **paired arms inside one run**,
switched per shot and held across the whole settle window - the same
discipline T208/T209/T210 used for their arms. Paired-arm IoU is
**0.945-0.976**, which is what a sampling-only change should look like.

### Change 1 - ray-origin phase widened to the coarse stride

**8. The change.** `paRayOriginJitterDistance` phases an exterior ray's
origin by one `fineStep` (`ExteriorFineStep`, 2.5-3.9 blocks). A low-density
body - the anvil above all - never promotes to the fine stride and is
integrated on `coarseStep = max(baseStep * 1.5, fineStep * 3)`, 7.5-11.6
blocks. The phase therefore covers under a third of the stride the samples
actually land on, leaving them clustered into shells one coarse step apart.
That spacing matches the measured 6-11 px banding period almost exactly.
Change 1 phases the exterior origin by `coarseStep` instead, behind
`VolumetricCloudDebugConfig.originJitterMode` and a `PaOriginJitterMode`
uniform declared in the JSON (a runtime-set uniform absent from the JSON
silently no-ops - the T203 lesson).

**9. Before/after.** `validation/t211-campaign/change1-jitter/`, three arms
on one storm (`J0` production, `J1` coarseStep, `J2` 2x fineStep):

| pose | arm | ripple RMS | peak-to-peak | periods (px) | cloud px |
|---|---|---|---|---:|---:|
| SIDE | J0 production | 2.455 | 20.09 | 11, 11, 10 | 73,491 |
| | **J1 coarseStep** | **0.812** | **8.60** | 6, 7, 2 | 73,460 |
| | J2 2x fineStep | 1.469 | 11.62 | 12, 11, 2 | 73,469 |
| LATERAL | J0 production | 2.308 | 17.37 | 15, 6, 11 | 46,193 |
| | **J1 coarseStep** | **0.816** | **5.95** | **2, 2, 2** | 46,152 |
| | J2 2x fineStep | 1.867 | 14.30 | 15, 6, 11 | 46,134 |
| ABOVE | J0 production | - | - | - | 931,685 |
| | J1 coarseStep | - | - | - | 882,269 (**-5.3%**) |
| | J2 2x fineStep | - | - | - | 928,552 (-0.3%) |

Visually J0 shows unmistakable stacked-plate terracing across the anvil
crown; J1 replaces it with soft billowing and real cauliflower lobes; J2 is
still visibly banded. The intermediate width **fails**, and for a principled
reason: to decorrelate shells spaced one `coarseStep` apart the phase must
span the full stride, and partial coverage preserves the periodicity - J2's
periods are identical to production's.

Under motion (T211M, T152 route, production 0.50 reference, 2200 frames per
arm, `t211-origin-motion.log`), using **flicker**, which is self-referential
(threshold-crossing alternation against the arm's own previous frame) and so
is not confounded by the appearance change the way ghost-vs-reference is:

| segment | fine (production) | coarse | change |
|---|---|---|---|
| OUTSIDE | 0.00027 | 0.00030 | +11.6% |
| APPROACH | 0.00066 | 0.00067 | +1.4% |
| ENTRY | 0.00130 | 0.00158 | **+21.6%** |
| INTERIOR | 0.00657 | 0.00667 | +1.5% |
| ROUTE | 0.00274 | 0.00288 | +5.1% |

And the decisive frames, in a steady in-cloud stretch:

| frame | fine flicker | coarse flicker | fine dMean | coarse dMean |
|---|---|---|---|---|
| 871 | 0.00022 | 0.00792 | 0.00365 | 0.01018 |
| 872 | **0.00003** | 0.00959 | 0.00279 | 0.01197 |
| 873 | **0.00000** | 0.00832 | 0.00268 | 0.00974 |
| 874 | **0.00000** | 0.00953 | 0.00249 | 0.01095 |
| 875 | **0.00000** | 0.00783 | 0.00257 | 0.00914 |
| 876 | **0.00000** | 0.00838 | 0.00251 | 0.01000 |

Production is **exactly still** through 873-877; the coarse phase sustains
0.0064-0.0096 with 3-4x the frame-to-frame alpha change. That is crawl, in a
region where production is silent.

A static probe alone would have passed this change: the T098 temporal pair
(same pose, 8 frames apart, camera still) shows both arms fully converged -
no pixel moving more than 1.67/255, 0.00% above 2 levels, and the coarse arm
marginally *quieter* at SIDE (0.0076 vs 0.0079). The grain in a still frame
is a settled dither, not per-frame noise. Crawl only appears once the camera
moves.

**10. Change 1: REJECT.** The cause is understood: `searchBlue` is seeded
from **screen** position and deliberately not animated (animating it made
thin silhouette pixels alternate hit/miss - the reason the current code says
so). Under camera motion a world point lands on a different pixel each
frame and draws a different phase, and the resulting depth shift scales with
the phase width. A wide phase converts that reseeding into visible crawl. So
widening alone cannot work; the phase has to be stable in **world** space
along the ray, or the sampling stratified, not merely wider. Kept as a
diagnostic (mode 1; mode 2 measured and dismissed) exactly as T209 kept its
upscale pass and T210 its lattice modes. `PRODUCTION_ORIGIN_JITTER_MODE = 0`
- production rendering unchanged.

### Change 2 - stop the anvil's coverage term saturating

**11. The change.** The cumulonimbus anvil's horizontal modulation is
`smoothstep(0.10, 0.56, lowFbm * 0.42 + footprintCoverage * 0.24 +
directionalCarrier * 0.34)`. The hypothesis for defect 1 was that this
saturates over most of the canopy, leaving a uniform slab whose boundary is
the descriptor ellipse - which would explain the perfect-ellipse
silhouette. Change 2 raised the upper bound to 0.86 so the canopy keeps the
variation its own noise already carries. Descriptor morphology, envelope and
lobe structure untouched.

**12. Before/after.** `validation/t211-campaign/change2-anvil/`, paired arms
on one storm:

| pose | area change | edge roughness (prod / arm) | mean abs diff | px > 1 level |
|---|---|---|---|---|
| SIDE | -0.1% | 31.03 / 31.15 | 0.013 | 0.19% |
| LATERAL | +0.0% | 25.96 / 25.97 | 0.004 | 0.10% |
| ABOVE | +0.0% | 26.37 / 26.36 | 0.0007 | 0.00% |

**13. Change 2: REJECT - no visible effect.** The hypothesis was simply
wrong: that term does not saturate, so it is not the lever controlling the
anvil silhouette. The knob was **removed** rather than left in as a dead
uniform. The silhouette is presumably set by where `body` falls off against
the envelope boundary - with `body` near 1 at the edge, an erosion of at
most `STORM_EROSION = 0.44` cannot cut through it, which would give exactly
the hard elliptical edge observed. That is the next place to look, and it
was not tested here.

**14-16. Change 3.** Not attempted. Two changes were measured to a decision
and the second disproved the hypothesis behind defect 1; guessing at a third
lever without evidence would have been fishing, which Task 3 rules out.

## QUALITY

**17. Silhouette.** Unchanged from baseline (no change banked). The anvil
remains a near-perfect ellipse - defect 1, unfixed. Change 1 did visibly
improve the crown's read by removing the terracing that was flattening it
into plates, but it is rejected on motion.

**18. Cloud base.** Unchanged. The anvil underside remains a uniform dark
sheet with a smooth edge; the lower mass remains over-eroded and speckled.
Not addressed.

**19. Tower / anvil.** Unchanged. The tower is still a thin wispy neck
rather than a defined updraft. Not addressed.

**20. Thin structures.** Unchanged in production. Measured as a cost of
change 1: the wide phase lost 5.3% of faint material at the top-down view
(49,310 px lost against 1,342 gained), because a deeper first sample
under-weights thin material near ray entry.

**21. Interiors.** Unchanged - a flat featureless wash with no density
variation. Documented as the deferred runner-up defect.

**22. Shadow pockets.** Not audited in isolation; the interior view shows no
pocket structure to audit. Deferred with interiors.

**23. Lighting balance.** Not changed. Task 5's audit was folded into the
defect ranking rather than run separately: the visible lighting problems in
the frames - the anvil's flat top with no form shadowing, the uniformly dark
underside, the featureless interior - are all symptoms of defects 1 and the
interior runner-up, not independent lighting faults. No lighting change was
attempted, and the old light-performance architecture was not reopened.

**24. Temporal stability.** Production unchanged, so unchanged. The T211M
route establishes the production reference for future work: route flicker
0.00274 for the temporal arm against 0.00228 for the production 0.50
reference, with long in-cloud stretches at exactly 0.00000.

## PERFORMANCE

**25/26. SIDE and PLAY_VIS_NEAR, before and after** (four bracketed
production anchors per pose, 1920x1080 display, 480x270 lattice):

| stage | SIDE T210 | SIDE T211 | PVN T210 | PVN T211 |
|---|---|---|---|---|
| field | 1.3589 | 1.3880 | 1.4111 | 1.3903 |
| gate | 0.1224 | 0.1213 | 0.1219 | 0.1224 |
| rain | 1.0468 | 1.0501 | 1.6218 | 1.5275 |
| raw cloud | 4.9290 | 5.0914 | 3.6480 | 4.1828 |
| temporal resolve | 0.1055 | 0.1065 | 0.1039 | 0.1047 |
| composite | 0.0788 | 0.0796 | 0.0778 | 0.0791 |
| **total** | **7.6414** | **7.8369** | **6.9844** | **7.4069** |

**27. Regression amount: none attributable.** Production rendering is
unchanged by construction - both modes default to 0 and the shader selects
`fineStep` exactly as before; the only added production work is one int
uniform upload per frame. The apparent +0.196 ms (SIDE) and +0.423 ms (PVN)
are **storm-realization variance**, not a regression: the difference is
accounted for almost entirely by `rawCloud` alone (+0.162 and +0.535), which
depends on the storm, and the storm differs on every launch. Timing must not
be compared across sessions here for the same reason the images must not be.

**28. <=10 ms: YES** at both poses on both measurements, with 2.16 ms and
2.59 ms of headroom. The ~7-8 ms class is preserved. No optimization was
opened, and none is warranted.

## DECISION

**29. Accepted visual changes: none.** T211 banks no visual change.

**30. Rejected changes.**
- **Change 1, coarse-stride ray-origin phase - REJECT.** Large still-frame
  win (terracing removed, ripple -28 to -65%, period 11 px to 2 px) but it
  introduces sustained crawl under motion where production is exactly still,
  plus 5.3% faint-material loss at top-down views. Task 7 rejects changes
  that add crawling; the still-frame win does not buy a pass.
- **Change 2, anvil coverage breakup - REJECT.** No visible effect
  (0.19% of pixels changed by more than one level); the hypothesis was
  wrong and the knob was removed.

**31. Deferred ideas**, in the order they should be tried:
1. **Defect 1 via the envelope falloff, not the coverage term.** Change 2
   disproved the coverage-saturation theory. The remaining candidate is how
   `body` falls off toward the envelope boundary: if it stays near 1 to the
   edge, `STORM_EROSION = 0.44` cannot cut through and the silhouette is the
   descriptor ellipse by construction. Test a role-scoped or
   boundary-proximity erosion boost in the anvil band, measured as paired
   arms, and check storm-to-storm diversity (Task 4) as well as shape.
2. **Defect 2 done properly: a world-stable ray phase.** The banding cause
   is proven and the fix direction is known - the phase must span the coarse
   stride *and* be stable in world space along the ray (or stratified), so
   camera motion does not reseed it. That is a larger change than T211 had
   room for and needs the T211M route to clear it.
3. **The flat interior** (`4_INSIDE`), with shadow pockets, as one piece of
   work.
4. **Fix the FAR and UNDER capture poses** so the audit covers all eight
   reference views; they currently land inside terrain on a storm over high
   ground.

**32. Exact next quality task.** **T212: anvil silhouette via envelope
falloff.** Take deferred item 1 above: instrument where `body` sits at the
envelope boundary in the anvil band, then test a boundary-proximity erosion
term as paired arms at SIDE / LATERAL / ABOVE, judged on silhouette
lumpiness, storm-to-storm diversity, T211M motion flicker and the <=10 ms
budget. It is the top-ranked defect and now has one hypothesis eliminated.

## VALIDATION

**33. `campaignGate`: PASS** (`gate-t211c.log`). It caught two real wiring
faults on the way, both fixed rather than worked around: the new markers had
to be registered in `StormCampaignRegistry` (`t211-origin-motion.txt` as
campaign **T211M**, `t211-captures.txt` as harness machinery), and a
`PERFORMANCE_ONLY` campaign must not be the registry's last row because the
wiring proof reads the newest row as a sweep - the T207 lesson, rediscovered
by the invariant. Final: `T170_WIRING campaigns=59|armMatrices=50|
violations=0`, with T199 and T202-T210 all still green.

**34. `check build`: PASS** (`check-build-t211.log`, BUILD SUCCESSFUL).

**35. Motion validation: run, and it decided the outcome.** T211M
(`ArmSet.ORIGIN_JITTER`: production 0.50 reference, then the fine and coarse
phases), 2200 frames per arm on one storm. This is the step that rejected
change 1.

**36. Production program scope: unchanged.** No new program was registered.
One uniform was added (`PaOriginJitterMode`, declared in
`cloud_atmosphere_volume.json`, always 0 in production); the change-2 uniform
was removed again. FINAL's baked selectors and the four production programs
are untouched, and `campaignGate` confirms the T206-T210 invariants still
hold.

**37. Git status.** T211 sources, this document, `t211-campaign/` evidence,
`launch-t211.cmd` / `launch-t211m.cmd`, and loose run logs. The user's
`PAchangelog.md` edit is preserved and deliberately uncommitted. All
historical validation evidence and unrelated logs are intact. Every edited
file is pure CRLF.

**38. Commit.** On `quality/t211-cloud-visual-quality`, not merged.

### Evidence

| path | what it is |
|---|---|
| `t211-campaign/baseline/` | the defect audit frames (SIDE, LATERAL, ABOVE, NEAR_EDGE, INSIDE) |
| `t211-campaign/change1-jitter/` | the three-arm origin-phase A/B on one storm |
| `t211-campaign/change2-anvil/` | the anvil-breakup pair (the no-op) |
| `t211-campaign/t211-origin-motion.log` | the T211M motion route that rejected change 1 |
