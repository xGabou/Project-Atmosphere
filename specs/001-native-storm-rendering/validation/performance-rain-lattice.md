# T210 - Rain at the reconstructed lattice, and the along-ray temporal guard

T210 answers two questions and stops:

**A.** Is higher-resolution CURRENT-FRAME rain worth its cost?
**B.** Does a targeted along-ray temporal guard solve the remaining on-axis
near-field history failure T208 left open?

Everything else - EASU/RCAS, another upscaler, lower temporal resolution,
more phases, rain temporal history, primary/probe/light optimization - is
out of scope and was not touched.

## RECOVERY

**1. Starting release HEAD.** `dae8d8f` on `Forge-1.20.1` ("fix: per-biome
temperatures survive region aggregation"), 0.9.4 shipped. The T167-T209
performance line was already merged as `846588b`; T208 and T209 were not
redone.

**2. Branch used.** `quality/t210-rain-temporal`, cut fresh from
`Forge-1.20.1` @ `dae8d8f`. The stale `experiment/cloud-descriptor-k` was
NOT resumed.

**3. Stash application / conflicts.** `stash@{0}` ("T210 WIP: rain lattice
+ coarse mask (not for 0.9.4)") was created from `experiment/cloud-descriptor-k`
@ `46851f9`. `46851f9` is an ancestor of `dae8d8f` (through the `846588b`
merge), and the post-0.9.4 commits touch only `PAchangelog.md`,
`gradle.properties`, `BiomeClientTemperatureCache.java`,
`ForecastGenerator.java` and `ForecastRegion.java` - **zero overlap** with
the stash's 14 files. `git stash apply` therefore applied **clean, with no
conflicts**; `stash@{0}` was deliberately left in place (no `pop`) until
the result was verified.

Preserved as required: the user's `PAchangelog.md` modification ("Fixed a
biome config issue", still the only tracked edit outside T210), every loose
campaign log, and all historical validation evidence. The aborted 13Sep
natural run was renamed to `runClient-t210-aborted.out.log` rather than
overwritten.

`StormT132AutoDriver.java` carries a 26,749-line whitespace diff purely
because the stash normalized it from CRCRLF to CRLF. Verified as noise: the
working-tree file is **0 CRCRLF / 13,598 CRLF / 0 bare LF**, and against
`--ignore-cr-at-eol` the real diff is 842/395 lines - the T210 additions
only. T209 survived the reflow intact (83 `T209` references in the working
tree vs 80 at HEAD; `T209_MARKER`, `buildT209Arms`, `t209IsAnchor` all
present). No unrelated driver logic was rewritten.

**4. Current production state at recovery.**
`PRODUCTION_RAIN_LATTICE_MODE = 0` (phase-grid rain replicated over the 2x2
temporal block), `PRODUCTION_SPATIAL_UPSCALE = -1` (T209 CASE C), production
temporal mode 1. `./gradlew.bat campaignGate --offline` **PASSED** on the
merged base before any client was launched (T199/T202/T203/T204/T205/T206/
T207/T208/T209 invariants all green), so the stash needed **no integration
repair** - the experiment was not redesigned.

## RAIN STATIC

Four static campaigns on `quality/t210-rain-temporal`, all 1920x1080, with
the rain-heavy forcing applied identically to both sides of every comparison:

| log | fixture | poses |
|---|---|---|
| `t210-run1.log`, `t210-run2.log` | natural precipitation | SIDE, PLAY_VIS_NEAR |
| `t210h-run2-newmask.log` | forced (`MaxPrecipitation >= 0.85`), **new mask** | SIDE |
| `t210h-run1.log` (13Sep, pre-existing) | forced, **old mask** (`T_r < 0.9998`) | SIDE |

**5. Natural rainPct.** SIDE **0.306%** (run 1), **0.307%** (run 2) and
**0.134%** (run 3, the post-revert build); PLAY_VIS_NEAR 0.050% / 0.080% /
0.039%. The fixture is seeded (`world seed overridden by marker: 5510001`)
with cloud movement frozen, and it varies only modestly between launches -
runs 1 and 2 landed within 0.001 points of each other, run 3 at roughly
half. **Three natural relaunches never approached a rain-heavy
realization**: the natural SIDE band is 0.13-0.31%, against 0.55% and 1.00%
on the forced fixture. Relaunching further was therefore not the way to get
rain-heavy evidence here - the forced fixture was, and that is where the
rain-heavy numbers below come from. This differs from T208's motion route,
where a genuinely heavy realization did turn up about one launch in three.

**6. Forced-rain evidence.** The new-mask forced run reached **1.003%**
rain (20,788 px, 2,261 thin-rain px) - nearly twice the old-mask forced
run's 0.546% (11,316 px), so the new-mask fixture is the more demanding of
the two and the mode-2 cost it reports is not a soft one.

**7-13. Rain-only quality and mask geometry.** Every arm is judged against
the SAME native 1.0 reference captured at the head of the pose; `prod`,
`lat1`, `lat2` are modes 0, 1, 2.

| metric | fixture | prod | **lat1 (mode 1)** | lat2 (mode 2) |
|---|---|---|---|---|
| **7/8. rain-only SSIM** | natural r1 / r2 / r3 | 0.8206 / 0.8194 / 0.8504 | **0.8862 / 0.8648 / 0.9317** | 0.8574 / 0.8238 / 0.8475 |
| | forced new / old | 0.7988 / 0.8027 | **0.8663 / 0.8680** | 0.8195 / 0.7789 |
| **9. mask IoU** | natural r1 / r2 / r3 | 0.7894 / 0.7967 / 0.7278 | **0.8284 / 0.8473 / 0.7853** | 0.8024 / 0.8220 / 0.7554 |
| | forced new / old | 0.7707 / 0.6876 | **0.8271 / 0.7529** | 0.8069 / 0.7060 |
| **10. false rain %** | natural r1 / r2 | 12.72 / 14.39 | **10.46 / 8.55** | 9.59 / 9.60 |
| | forced new / old | 11.93 / 20.85 | **8.45 / 13.67** | 8.25 / 14.00 |
| **11. missed rain %** | natural r1 / r2 | 11.02 / 8.86 | **8.50 / 8.03** | 12.06 / 9.91 |
| | forced new / old | 13.74 / 16.91 | **10.30 / 14.41** | 12.66 / 19.51 |
| **12. thin rain lost %** | natural r1 / r2 | 7.66 / 3.44 | **4.24 / 3.27** | 5.79 / 4.25 |
| | forced new / old | 11.72 / 11.87 | **6.41 / 7.28** | 8.67 / 11.24 |
| **13. onset / term px** | forced new | 7.61 / 10.14 | **6.39 / 6.50** | 6.14 / 7.57 |
| | natural r1 | 5.27 / 7.35 | **3.24 / 6.96** | 3.09 / 12.11 |

Mode 1 improves **every** rain metric at **every** fixture: rain-only SSIM
**+0.045 to +0.081**, mask IoU +0.039 to +0.056, false rain down 2-6 points,
missed rain down 2-4, thin rain retained roughly twice as well (11.7% lost
-> 6.4% on the forced fixture), onset error down 1-2 px. The direction never
reverses across four campaigns.

Mode 2 sits between the two on rain-only SSIM and beats mode 1 on false rain
alone, at the cost of the worst missed-rain of the three lattice arms.

**14. Full-frame impact.** Rain is 0.3-1.0% of the frame, so full-frame SSIM
barely moves for mode 1 - and that is the point: the rain gain is not paid
for elsewhere.

| fixture | prod | lat1 | lat2 |
|---|---|---|---|
| natural r1 | 0.962217 | **0.962298** (+0.00008) | 0.950987 (**-0.0112**) |
| natural r2 | 0.937059 | **0.937188** (+0.00013) | 0.923971 (**-0.0131**) |
| natural r3 (final build) | 0.936242 | **0.936221** (-0.00002) | 0.919697 (**-0.0165**) |
| forced new | 0.934027 | **0.934910** (+0.00088) | 0.919814 (**-0.0142**) |

Mode 1 is full-frame neutral-to-positive at all three. **Mode 2 is a
consistent full-frame regression of -0.011 to -0.014** - an order of
magnitude larger than mode 1's gain, and in the wrong direction. The coarse
pass's 3x3 visible-rain test writes "no rain" over faint rain that the
production pass keeps, and that loss is spread across the whole frame
rather than confined to the rain mask.

**15. SIDE prod / prod_r2 drift - and a harness defect.** The handoff
expects ~0.01-0.015 SSIM of SIDE capture drift. Measured here it is **worse
than that, and it is a function of time-in-pose rather than a fixed noise
floor**:

| repeat | position in pose | full-frame SSIM vs its original |
|---|---|---|
| `lat2_r2` vs `lat2` | **immediately** after | 0.999987 / 0.999945 / 0.999952 |
| `prod_r2` vs `prod` | 7 arms and ~10 s later | **0.9700 / 0.9448 / 0.9432** |
| `prod_r2_body` vs `prod_body` | 7 arms and ~10 s later | 0.9659 / 0.9384 |

An immediate repeat is exact to ~5e-5; the end-of-pose repeat is 0.030-0.057
off. The pose is therefore not noisy - it **drifts monotonically**. The
capture metadata gives the mechanism: `liveWorldTime` advances 2143 -> 2342
(~199 ticks) between `prod` and `prod_r2` while
`effectiveReferenceWorldTime` stays pinned at 2103, and the cloud content is
identical across the pair (`stormContentSignature`, `weatherMapSignature`,
`puffContentSignature` all equal). Clouds are frozen; **precipitation is
not**, so the rain thickens over the pose.

That makes `prod_r2`'s rain-mask numbers unusable as a drift control:
`rainMaskArmPx` is 34,582 (run 1) / 48,100 (run 2) against a 6,351 / 6,365
reference - false rain 461% / 668%, and a termination error of **683.69 /
684.16 px** reproducing to two decimal places across independent runs. That
is a systematic harness artifact of scoring an end-of-pose rain field
against a head-of-pose reference, **not** a rendering difference, and it is
recorded here as a defect to fix in the harness rather than as a result.
The intended drift control should be re-captured adjacent to its original,
as `lat2_r2` already is.

**The consequence for the Part A reading is favourable, not adverse.**
`lat1` and `lat2` are captured *after* `prod` (arms 6 and 8 vs arm 4), so
the drift penalises them - they are scored against a reference holding less
rain than they render. Mode 1 nevertheless wins every rain metric in every
run, so its measured advantage is a **floor**, not a ceiling. No Part A
claim rests on a difference smaller than the drift: mode 1's rain-only gain
(+0.045 to +0.068) and mode 2's full-frame regression (-0.011 to -0.014)
both reproduce across four campaigns and two fixtures.

## RAIN PERFORMANCE

All figures are the campaign's bracketed anchors (four production anchors
per pose, anchor drift < 0.017 ms, every block `accepted`,
`T210_REJECTED count=0`).

**16-19. The rain clocks (ms).**

| pose / fixture | 16. production rain | 17. **mode 1** | 18. **mode 2** (coarse + lattice) | 19. mode 2 added | mode 1 added |
|---|---|---|---|---|---|
| SIDE natural r1 | 1.0373 | 2.2682 | 2.2323 (1.0368 + 1.1955) | **+1.1950** | +1.2308 |
| SIDE natural r2 | 1.0596 | 2.3132 | 2.3352 (1.0593 + 1.2759) | **+1.2756** | +1.2536 |
| PLAY_VIS_NEAR r1 | 1.4625 | 2.1709 | 3.0116 (1.4587 + 1.5529) | **+1.5491** | +0.7084 |
| PLAY_VIS_NEAR r2 | 1.6829 | 2.4689 | 3.4324 (1.6855 + 1.7469) | **+1.7495** | +0.7859 |
| **SIDE forced, new mask** | 1.2398 | 2.6839 | 3.0433 (1.2442 + 1.7992) | **+1.8035** | +1.4441 |
| SIDE forced, old mask | 1.1830 | 2.9665 | 3.0126 (1.1786 + 1.8340) | +1.8296 | +1.7836 |

**The new mask fixed mode 2's quality but not its cost.** Against the old
mask on the forced fixture it lifted mode-2 mask IoU 0.706 -> 0.807, cut
missed rain 19.51% -> 12.66% and raised rain-only SSIM 0.7789 -> 0.8195 - a
real repair. But the added cost moved only **1.8296 -> 1.8035 ms**, and that
on a fixture carrying nearly twice the rain; the masked lattice pass itself
barely moved (1.8340 -> 1.7992 ms). This is the measurement the handoff
flagged as missing, and it settles the question against mode 2.

**Why mode 2 cannot be rescued by further tuning.** The mask genuinely works
- it removes 28% (PVN) to 47% (SIDE) of the lattice pass. But mode 2's
coarse pass *is* a full production rain pass (1.04-1.69 ms), and that
overhead consumes the entire saving. Mode 2 beats mode 1 only if the mask
eliminates more than the coarse pass costs; measured, it never does. It ties
mode 1 on SIDE-natural, loses by 0.84-0.96 ms on PLAY_VIS_NEAR and by
0.36 ms on the forced fixture. The limit is structural, not a threshold, so
per the handoff no further iteration was attempted.

**20. Whole-renderer delta and the 10 ms budget.**

| pose / fixture | anchor total | mode 1 total | mode 2 total | mode 1 delta | mode 2 delta |
|---|---|---|---|---|---|
| SIDE natural r1 / r2 | 7.7348 / 7.7604 | 9.0870 / 9.0911 | 8.9866 / 9.1397 | +1.3522 / +1.3307 | +1.2518 / +1.3793 |
| PLAY_VIS_NEAR r1 / r2 | 6.7917 / 6.7087 | 7.5407 / 7.4803 | 8.3707 / 8.4526 | +0.7491 / +0.7716 | +1.5790 / +1.7439 |
| SIDE forced, new mask | 8.0120 | 9.5027 | 9.8944 | +1.4907 | +1.8824 |

Every arm stays under 10 ms, but mode 2 on the forced fixture leaves only
**0.106 ms** of headroom. (These totals are the driver's
`cloud + rainField + rainPass + rainLattice + resolve + composite`; the rain
gate, measured separately at **0.12 ms**, is not included in that sum.)

**Part A decision against the bar** (<=0.75 strong PASS; 0.75-1.25
acceptable only if the quality gain is substantial; >1.25 FAIL):

- **Mode 2: FAIL.** +1.55 to +1.80 ms at PLAY_VIS_NEAR and on the forced
  fixture - the poses where rain actually costs something - and a full-frame
  quality regression (-0.011 to -0.014) on top. It is dominated by mode 1 on
  both axes at once.
- **Mode 1: quality-positive, cost-limited.** The clear quality winner and
  full-frame neutral, but +1.44 ms on the forced fixture and +1.23/+1.25 ms
  on SIDE - at or over the FAIL line exactly where rain matters most. Only
  PLAY_VIS_NEAR (+0.71/+0.79 ms, at 0.05-0.08% rain) sits in the acceptable
  band.

Neither mode clears the bar. `PRODUCTION_RAIN_LATTICE_MODE` stays **0**.

## RAIN MOTION

One T152 route, rain-heavy forced, three arms through one storm
(`group=b21fc294`, 2200 frames each, one frame-0 retry): the **native 1.0**
render as the reference, then the 0.25 lattice with **production
phase-grid rain** and with the **masked lattice rain (mode 2)**.
Log: `t210m-rain-motion.log`.

| segment | | production rain | lattice rain (mode 2) |
|---|---|---|---|
| OUTSIDE (283) | ghost | 0.00044 | 0.00043 |
| APPROACH (420) | ghost | 0.00073 | 0.00066 |
| ENTRY (796) | ghost | 0.00052 | 0.00052 |
| INTERIOR (701) | ghost | 0.00100 | 0.00104 |
| **route (2200)** | **ghost mean** | **0.00070** | **0.00070** |
| | ghost p95 | 0.00159 | 0.00148 |
| | ghost max | 0.02574 | 0.02594 |
| | flicker | 0.00208 | 0.00208 |
| | disocclusion ghost | 0.04102 | 0.04116 |
| | bias | -0.00020 | -0.00018 |
| | frames > 0.02 | 12 | 13 |

(The reference's own flicker is 0.00197, interior 0.00430.)

**21. Rain ghosting: none, and no difference between the arms.** Route
ghost mean is **0.00070 for both** (delta +0.00001). Lattice rain neither
introduces nor removes ghosting under motion.

**22. Flicker: unchanged.** 0.00208 on both arms against the native
reference's 0.00197; interior 0.00432 vs 0.00433. The lattice rain pass
does not shimmer - halving the rain texel's footprint does not destabilise
it frame to frame, because the rain is still marched fresh every frame.

**23. Shaft continuity.** Both arms flag the same outlier window - frames
1308-1312 and 1316-1322 (the lattice arm adds 1323) - with near-identical
magnitude (max 0.02574 vs 0.02594). These are cloud-motion frames, not rain
frames: they appear in the production-rain arm too, so they are not
attributable to the lattice. Statically, shaft continuity was 1.1138
(prod) vs 1.0937 (mode 1) vs 1.0917 (mode 2) against a reference of 1.0 -
the lattice arms track the native shaft run-count slightly better.

**24. Temporal trails: none.** Disocclusion ghost is 0.04102 vs 0.04116 -
identical within 0.3% - and the signed bias stays at -0.0002 on both arms
across 2200 frames rather than drifting. A rain history that accumulated
would show as a growing bias and an inflated disocclusion ghost behind the
camera's motion; neither appears. **Rain remains current-frame**, as
`campaignGate`'s T207 invariant independently asserts
(`rainCurrentFrame=true`): only raw phases and the current rain pass enter
the resolve, and the resolved target is never rebound as history.

**25. Part A under motion: PASS for correctness, but it changes no
verdict.** The lattice rain is temporally well-behaved - no ghosting, no
shimmer, no trails, no cloud-edge interaction - so nothing about motion
argues *against* it. But it offers no motion-side benefit either
(route ghost identical, p95 better by 0.0001, one more outlier frame), so
motion adds no counterweight to the static cost finding. The Part A
decision therefore rests where the measurements put it: **quality-positive,
cost-limited, not productionized.**

## ALONG-RAY TEMPORAL

One T152 route, four arms through one storm (`group=978e0aed`, 2200 frames
each, the route's usual frame-0 `fixture_dissipated` retry then complete):
production 0.50 as the reference, then the 0.25 lattice under **mode 1**
(T208 guard + the new along-ray guard), **mode 6** (T208 guard alone - the
along-ray control) and **mode 5** (the T207 control).
Log: `t210-alongray-motion.log`. Class counters from the static route:
`t210-alongray-classes.log`.

**A clean noise floor.** The along-ray guard is gated on
`CameraDensity > 0.01`. Camera density is exactly `0.00000` for the first
866 frames of every arm and identical frame-for-frame across arms, so over
those frames the guard provably cannot act. Measured there:

| region | mode 1 | mode 6 | mode 1 - mode 6 |
|---|---|---|---|
| `cameraDensity == 0` (n=1492, guard inert) | 0.000371 | 0.000371 | **0.000000** |

The arms are deterministic to six decimal places where the guard is
switched off. **Every difference reported below is therefore causal, not
noise** - there is no error bar to hide behind, in either direction.

**26. Reproduced target episode: YES, and harder than before.** The
in-cloud opening reproduces at frames **1232-1236** and **1336-1358**, with
a peak ghost of **0.2323** - far worse than the ~0.036 peak of the T208
run-2 episode. This realization is a more severe instance of exactly the
failure T210 set out to fix.

**27-29. Ghost mean and p95.**

| region | | mode 1 | mode 6 | mode 5 |
|---|---|---|---|---|
| whole route (2200) | mean | **0.00142** | **0.00146** | 0.00149 |
| | p95 | 0.00110 | 0.00139 | 0.00145 |
| guard live (`density > 0.01`, n~547) | mean | 0.004406 | 0.004608 | 0.004686 |
| | p95 | **0.00198** | 0.00475 | 0.00547 |
| target episode 1230-1360 (n=131) | mean | 0.017888 | 0.018334 | 0.018662 |
| | **p95** | **0.11620** | **0.11592** | 0.11570 |
| | **max** | **0.23232** | **0.23228** | 0.23255 |

**30. Outlier frames** (>0.02 mean alpha error): route **28 / 27 / 28** for
modes 1 / 6 / 5 - the guard removes none; it adds one. Within the 1230-1350
window all three arms flag the same **20** frames.

**31. Flicker:** route 0.00229 / 0.00231 / 0.00232 (reference 0.00221),
interior 0.00521 / 0.00525 / 0.00527. No material increase - marginally the
best of the three. PASS.

**32. alongRay reject count: 0.** Both static poses sit outside the medium
(`cameraDensity = 0`), so the guard fires on **zero** of 388,800 candidate
sites at SIDE and PLAY_VIS_NEAR. Under motion it can only engage on the
~25% of route frames inside the medium. The guard is **targeted, not
widespread** - criterion 5 PASS. (The T152 route emits no class map, so the
interior reject count is not directly countable with today's harness; it is
bounded above by the ghost deltas in the table.)

**33. History acceptance impact: none.** SIDE 388,798 accepted vs 388,800
for the control (100.00% both); PLAY_VIS_NEAR 388,770 vs 388,778 (99.99%
both) - a spread of 8 sites in 388,800. `nearField = 0` and `alongRay = 0`
throughout. Criterion 3 PASS.

**The failure: the episode itself does not move.** Per frame, across the
opening the guard was built for:

| frame | mode 1 | mode 6 | mode 5 |
|---|---|---|---|
| 1232 | 0.02171 | 0.02153 | 0.02147 |
| 1233 | 0.02209 | 0.02203 | 0.02218 |
| 1234 | 0.02239 | 0.02246 | 0.02241 |
| 1235 | 0.02270 | 0.02261 | 0.02266 |
| 1236 | 0.02316 | 0.02322 | 0.02319 |

Mode 1 is alternately better and worse than mode 6 by **+-0.5%**, and the
episode max is identical (0.23232 vs 0.23228). Sub-episode A (1228-1245)
improves 4.3% in the mean; sub-episode B (1330-1360) gets **worse** by 0.8%.
Episode p95 is marginally worse with the guard (0.11620 vs 0.11592).

What the guard *does* do is halve small-amplitude ghosting in quiet
in-cloud frames next to the episode - 1242: 0.00035 vs 0.00124, 1243:
0.00037 vs 0.00125 - which is what drives the guard-live p95 from 0.00475
to 0.00198. That is a real, measurable effect. It is simply not the effect
T210 commissioned it for.

**34. Part B: FAIL - the along-ray guard is NOT banked.**

Against the bar:

1. **The on-axis / in-cloud opening episode materially improves - NO.** The
   outlier frames are unchanged to within +-0.5%, the peak is unchanged
   (0.2323), the outlier count is unchanged (20 of 20 in-window; 28 vs 27
   route-wide, the guard adding one), and the episode p95 is marginally
   worse. This is the criterion the guard exists to satisfy, and it fails.
2. Flicker does not materially increase - PASS (0.00229 vs 0.00231).
3. Broad history rejection does not increase - PASS (acceptance 100.00% /
   99.99%, identical to the control within 8 sites).
4. OUTSIDE and ENTRY essentially unchanged - PASS, and exactly so: delta
   0.000000 wherever `cameraDensity == 0`.
5. alongRay rejects targeted rather than widespread - PASS (zero static,
   medium-gated under motion).

The guard passes every "do no harm" criterion and fails the only one that
would justify shipping it. The handoff's decision rule applies directly -
*"if mode 1 and mode 6 are effectively identical: reject the new guard"* -
and on the target episode they are identical. Its companion warning is
precisely what the data shows: the remaining on-axis near-field error is a
**representation** error, not a history-acceptance error, and refusing more
history does not move it. No further history rejection was attempted.

**Disposition.** The along-ray guard is kept as an opt-in **diagnostic on
mode 6** and removed from production mode 1, so mode 1 is once again
exactly the T208 production validity. This mirrors T209's treatment of the
spatial upscale pass (kept, `PRODUCTION_SPATIAL_UPSCALE = -1`): the
experiment stays reproducible without shipping an unjustified behaviour
change. `CLASS_ALONGRAY = 10` and the `alongRay=` counter are retained so
the comparison can be re-run against a future fixture.

## FINAL PRODUCTION

**35. `PRODUCTION_RAIN_LATTICE_MODE = 0`** - unchanged. The phase-grid rain
pass replicated over the 2x2 temporal block stays production. Modes 1 and 2
remain compiled and campaign-reachable as diagnostics.

**36. Temporal mode: 1, and mode 1 is once again exactly the T208
validity.** The along-ray guard was moved off production onto resolve
**mode 6** as an opt-in diagnostic
(`bool alongRayReject = ResolveMode == 6`). Mode 5 remains the T207
control. `CLASS_ALONGRAY = 10`, the `CameraDensity` uniform and the
`alongRay=` counter are retained so the experiment can be re-measured
against a future fixture. `campaignGate` still reports
`productionTemporalMode=1` for both T207 and T208, confirming production
behaviour is byte-for-byte what 0.9.4 shipped.

**37/38. Fresh production timings** (post-revert build, four bracketed
production anchors per pose, 1920x1080 display -> 480x270 lattice;
`t210-run3-final.log`):

| stage | **SIDE** | **PLAY_VIS_NEAR** |
|---|---|---|
| field | 1.3589 | 1.4111 |
| gate | 0.1224 | 0.1219 |
| rain | 1.0468 | 1.6218 |
| raw cloud | 4.9290 | 3.6480 |
| temporal resolve | 0.1055 | 0.1039 |
| composite | 0.0788 | 0.0778 |
| **total** | **7.6414** | **6.9844** |

**39. <=10 ms: YES** at both poses - 2.36 ms of headroom at SIDE and 3.02 ms
at PLAY_VIS_NEAR. No broad performance optimization was reopened, and none
is needed.

**40. Final quality state.** Production rendering is unchanged by T210. The
campaign's output is two decisions and the evidence behind them:

- **Higher-resolution current-frame rain is real quality that costs too
  much today.** Mode 1 is better on every rain metric at every fixture
  (rain-only SSIM +0.045 to +0.081, mask IoU +0.039 to +0.058, thin rain
  retention roughly doubled) and full-frame neutral, but it roughly doubles
  the rain pass: +1.44 ms on the forced fixture, +1.23 to +1.25 ms at SIDE.
  It is banked as a known, measured option rather than shipped.
- **Mode 2 is the wrong shape for the problem.** The tightened mask
  repaired its quality (IoU 0.706 -> 0.807) but not its cost
  (1.830 -> 1.804 ms added), because its coarse pass is itself a full
  production rain pass. No amount of threshold tuning changes that
  arithmetic, and it regresses full-frame SSIM by 0.011-0.017.
- **The remaining on-axis near-field temporal error is not a history
  problem.** The along-ray guard is well-targeted and harmless, and it
  still moves the failure frames by +-0.5%. The residual is a
  representation error; it will not be fixed by refusing more history.

Known follow-up, recorded not actioned: the `prod_r2` drift control is
invalid on SIDE (it scores an end-of-pose rain field against a head-of-pose
reference). The fix is to capture the repeat adjacent to its original, as
`lat2_r2` already is. This is a harness defect, not a renderer one, and it
is out of T210's scope.

## VALIDATION

**41. `campaignGate`: PASS, twice.** Once on the merged base immediately
after the stash applied (proving the stash needed no integration repair),
and once after the along-ray revert and the new invariant
(`gate-t210.log`, `gate-t210b.log`). Final run reports:

```
T210_RAIN_LATTICE productionRainLattice=0|alongRayGuard=mode6Diagnostic
  |productionTemporal=t208Exact|coarseMask=visibleRainOnly
  |rainCurrentFrame=true|latticeTargetReleased=true|modesCompiled=0|1|2
```

with T199, T202-T209 all still green alongside it.

**42. `check build`: PASS** - see the run recorded at the end of this
document.

**43. Rain correctness.** Rain stays current-frame: the T207 invariant
asserts `rainCurrentFrame=true` (only raw phases and the current rain pass
enter the resolve; the resolved target is never rebound as history), and
the motion route confirms it empirically - no trails, disocclusion ghost
0.04102 vs 0.04116, signed bias flat at -0.0002 over 2200 frames. The
rain-heavy forcing is applied identically to both sides of every
comparison, so it changes what is visible, not what is compared.

**44. Temporal invariants.** T207 (four phases, non-recursive resolve,
quarter-pixel lattice jitter, targets released) and T208 (near-field guard
in mode 1 needing distance AND motion AND ambiguity together, mode 5 the
control, attribution switches never reaching a timing cell) both still
pass, with `productionTemporalMode=1` in each. The class array is 11 wide
and the `alongRay=` counter is populated.

**45. Lifecycle.** `releaseRainLatticeTarget` is called from the common
release path beside `releaseSpatialUpscaleTarget` and the temporal targets,
and on every mode change away from 2 (`modeOff`). `T210_RAIN_LATTICE_ALLOC`
/ `_RELEASE` bracket it in the logs. The gate asserts the release wiring.

**46. Program registration.** The lattice rain pass reuses the existing
rain-pass program with two new uniforms (`PaRainPassScale`,
`PaRainCoarseMask`) declared in `cloud_atmosphere_volume.json` and matching
the GLSL; the resolve gains `RainPassLattice` and `CameraDensity` in
`cloud_temporal_resolve.json`. No new program was registered, so the
FINAL/production program set is unchanged (T206's `productionPrograms=4`
still holds).

**47. Variant scope.** The rain lattice is reachable only on a temporal
frame (`int rainLatticeMode = temporal ? ... : 0`, asserted by the gate),
so it cannot arm where there is no lattice to evaluate it on. The along-ray
guard is reachable only on resolve mode 6. Neither touches FINAL's baked
selectors.

**48. Markers.** All T210 and T208 campaign markers are retired to
`.done` - `t210-rain-quality`, `t210-rain-heavy`, `t210-rain-motion`,
`t208-temporal-motion`, `t208-temporal-cleanup`, `t206-production-baseline`.
Exactly one marker was armed at a time throughout, and
`run/t132-autorun-exit.txt` was kept armed for every launch. No marker is
armed at rest.

**49. Git status.** The working tree carries the T210 sources, this
document, the new `t210-campaign` evidence, the two launchers
(`launch-t210h.cmd`, `launch-t210m.cmd`) and the loose run logs. The user's
`PAchangelog.md` edit is preserved untouched, as is all historical
validation evidence and every unrelated log. `StormT132AutoDriver.java` is
pure CRLF (0 CRCRLF, 0 bare LF); so are every file T210 edited.

**50. Commit.** On `quality/t210-rain-temporal`, not merged.

### Campaign evidence

| log | what it is |
|---|---|
| `t210-run1.log`, `t210-run2.log` | natural static, SIDE + PLAY_VIS_NEAR |
| `t210h-run1.log` | forced rain, **old** mask (pre-existing, 13Sep) |
| `t210h-run2-newmask.log` | forced rain, **new** mask - the cost answer |
| `t210-alongray-motion.log` | the 4-arm along-ray A/B route |
| `t210-alongray-classes.log` | static class counters (`alongRay=0`) |
| `t210m-rain-motion.log` | the rain motion route |
| `t210-run3-final.log` | post-revert build: final production timings |
