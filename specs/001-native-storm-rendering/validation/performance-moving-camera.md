# T152 - Moving-camera fixture and silhouette-stability metric

**Built, validated, and baselined on the shipped renderer.** Run:
`runclient-t152.out.log`, 2026-09-03, outcome `t152_complete`, fixture group
`981d9ca6-af25-4ea8-8b18-b39300acfebc`, 2200 frames per arm, two arms, both flying one storm.

The headline finding is not about the fixture: **temporal accumulation is not what keeps this
renderer's silhouette stable.** Route-level flicker is identical with history on and off
(0.00232 both), so the frozen sample lattice is doing that work alone, and no traversal change
that moves the lattice can expect history to rescue it.

---

## 1. What the fixture is

Every performance measurement banked so far was taken at a static pose. That is correct for
cost and blind to quality: `searchBlue` is a static screen-space phase precisely because
moving it made thin silhouette pixels alternate between hit and miss, and a static pose cannot
see that alternation. This drives one deterministic route and measures the rendered cloud
buffer on every frame of it.

Diagnostic-only and marker-gated (`t152-moving-camera.txt`). No production equation,
descriptor, sample position or quality profile is touched. The only production state it
changes is the temporal-history switch - the measured variable - restored on every terminal
path.

**Route.** One cubic Bezier scaled by the live fixture's own radius and height, running
outside -> approach -> entry -> interior -> openings -> exit without stopping, camera along
the analytic tangent. A stitched route would step the heading at segment boundaries, and that
spike would be indistinguishable from a real instability at exactly the frames the metric
reads. Segments below are reporting bands over one continuous curve.

**Parameterisation is the part that had to be measured, not assumed.** Uniform curve
parameterisation was rejected on evidence: on the live R=663-block fixture an evenly spaced
route advances ~8.8 blocks/frame everywhere, which is ~12 px/frame against distant material
but **90-164 px/frame through entry, interior and exit**. At that rate reprojection fails
everywhere in those segments, every temporal term saturates, and the fixture cannot
discriminate one renderer from another - the only thing it exists to do. Each frame therefore
advances a constant *angular* step against the nearest cloud surface: 0.00922 rad, about
14 px/frame at the reference resolution, with world steps ranging 0.405 to 5.555 blocks.
Distance to the bounding cylinder is the wrong scale inside the system - it is largest at the
deepest point, where the camera is in fact enveloped - so the interior takes its own
180-block ceiling.

**Two arms.** Ghosting and disocclusion are not measurable inside one arm; a single
accumulated sequence has nothing to compare against. The route runs twice, history off then
on, compared frame index to frame index on a 160x90 alpha downsample. The history-off arm is
**not** an image-quality reference - it is noisier by construction, being the un-accumulated
jittered march. It is the reference for *what accumulation changes under motion*, and is used
only that way.

Measured at 1920x1080, ULTRA, at the shipped Rank 1 internal scale (480x270 marched pixels).

---

## 2. Fixture validity

| | arm 1 (history off) | arm 2 (history on) |
|---|---|---|
| frames | 2200 | 2200 |
| `stormDescriptors` observed | {10} | {10} |
| storm-in-frame frames | 1885 | 1885 |
| full-envelopment frames (coverage >= 0.999) | 306 | 308 |
| empty-sky frames | 315 | 315 |
| peak `cameraDensity` | 0.792 | - |

Frames where one arm has storm and the other does not: **0**. Frames with
`|coverage_off - coverage_on| > 0.05`: **3**, maximum difference 0.125. The two arms flew the
same storm, which is the precondition for every cross-arm number in section 4.

The 315 empty-sky frames are the exit leg, where the camera faces along travel and the storm
leaves the view. That is a real exit and its disocclusion is measured, but it yields no
silhouette data, so all silhouette and connectivity aggregates below are over storm-in-frame
frames only.

### The first run was invalid and said it had succeeded

Recorded because the failure is instructive, not to pad the record. Run 1
(`runclient-t152-run1-invalid.out.log`, 3600 frames/arm) reported `t152_complete` while the
storm **dissipated during arm 2** - `stormDescriptors` fell 10 to 0 - so from about frame 1400
arm 2 flew the route through empty sky against an arm 1 at full envelopment. Its interior
`disoccGhost` of 0.083 was an artifact of absence, not ghosting.

Two causes, both fixed:

1. The between-arms identity check called `StormPerformanceBaseline.suiteFixture()`, which
   returns a fixture cached once when the driver resolves it. The check therefore compared the
   stored value against itself and passed however far the live storm had drifted. It now
   re-resolves before comparing, and **every frame** requires `lobeCount() >= 10` before it is
   recorded - the same discipline T150 imposed on the pose sweep after empty cells corrupted
   three separate measurement runs.
2. A lifetime mismatch. The storm lived 9.0 minutes from route start; a two-arm 3600-frame
   route needs 13.3 at the observed ~9 fps. The route is now 2200 frames per arm, about 8.1
   minutes for both, and dissipation triggers a bounded retry that restarts the *whole* route
   on a fresh fixture rather than salvaging a half-empty arm.

The retry fired once in this run, at arm 1 frame 0, on a transient `lobeCount=0` in the same
instant the driver declared the storm mature; it respawned and re-adopted 10 seconds later.
**Known refinement:** the guard aborts on a single sub-threshold frame, so a transient costs
one of three attempts. Requiring consecutive sub-threshold frames would be more robust.

---

## 3. Baseline: shipped renderer under motion (history off)

Storm-in-frame frames. Fractions are of all marched pixels; runs and gaps are per occupied
column; inner-sky runs are in pixels.

| segment | n | coverage | edge | silW | silH | flicker | flk p95 | flk max | dMean | colRuns | colMax | reEntry | skyMax | skyMean | camDens |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| OUTSIDE | 150 | 0.0372 | 0.0039 | 85 | 80 | 0.00022 | 0.00054 | 0.00083 | 0.00070 | 1.811 | 7 | 0.811 | 37 | 5.74 | 0.000 |
| APPROACH | 237 | 0.3098 | 0.0262 | 305 | 211 | 0.00120 | 0.00185 | 0.00414 | 0.00362 | 1.770 | 9 | 0.770 | 224 | 14.43 | 0.000 |
| ENTRY | 794 | 0.4432 | 0.0282 | 438 | 173 | 0.00086 | 0.00156 | 0.00948 | 0.00157 | 1.476 | 9 | 0.476 | 223 | 27.15 | 0.000 |
| INTERIOR | 704 | 0.8545 | 0.0655 | 480 | 257 | **0.00581** | **0.02048** | **0.20729** | 0.00686 | 1.559 | **75** | 0.559 | 214 | 17.50 | 0.305 |

**Flicker is an interior phenomenon, not a silhouette one.** Interior flicker is 6.8x ENTRY
and 26x OUTSIDE, its p95 reaches 2.0% of all pixels, and its worst frame alternates **20.7%**
of the frame. Column runs tell the same story from another direction: `colRunsMax` is 7-9
everywhere the storm has an edge against sky and **75** inside it. Inside the cloud the image
breaks into many disconnected covered runs per column and those runs flip between frames.

This relocates the concern the fixture was built for. The hit/miss alternation that froze the
sample lattice is assumed to live on thin silhouette pixels; measured, the silhouette segments
are the *quiet* ones and the in-cloud regime is where alternation actually concentrates.

Softness scales as expected - `edgeFraction` 0.39% outside to 6.55% inside - and connectivity
gives T098b a baseline it did not have: **0.48 to 0.81 empty gaps per occupied column**, with
inner-sky runs up to 224 px, present on the shipped renderer before any traversal change.

---

## 4. What temporal accumulation actually does

Arm 2 against arm 1, same frame indices, storm-in-frame frames.

| segment | n | flicker ON | flicker OFF | ratio | ghostMean | ghostMax | ghostBias | disoccFrac | **disoccGhost** |
|---|---|---|---|---|---|---|---|---|---|
| OUTSIDE | 150 | 0.00022 | 0.00022 | 1.003 | 0.00000 | 0.184 | -0.00000 | 0.00056 | 0.00023 |
| APPROACH | 237 | 0.00117 | 0.00120 | 0.975 | 0.00020 | 0.188 | -0.00007 | 0.00298 | 0.00389 |
| ENTRY | 794 | 0.00080 | 0.00086 | 0.932 | 0.00015 | 0.125 | -0.00012 | 0.00104 | 0.00490 |
| INTERIOR | 704 | 0.00590 | 0.00581 | 1.015 | 0.00055 | 0.675 | +0.00016 | 0.00564 | 0.00665 |

Route totals: flicker mean 0.00232 both arms; p95 0.00923 off against 0.00889 on.

**History barely touches flicker.** Ratios span 0.932 to 1.015 - it helps a little at ENTRY,
a little at APPROACH, and very slightly *hurts* in the interior. At route level the two arms
are identical to three significant figures. The stability of this renderer under motion comes
from the frozen sample lattice, not from temporal accumulation.

That has a direct consequence for any future traversal work. T151 rejected interleaved
reconstruction on its performance ceiling and noted that the 4-phase pattern carried the worst
temporal exposure available. This measures the other half of that argument: a change that
moves or subsamples the lattice cannot be rescued by the history blend, because the history
blend is not currently buying flicker suppression.

**Ghosting is negligible in the mean and real in the tail.** `ghostMean` never exceeds 0.00055,
but `ghostMax` reaches 0.675 in the interior and ~0.18 outside: a small number of pixels
disagree strongly. `ghostBias` sits within +/-0.0002 of zero at every segment, so there is no
systematic trailing smear - history is not retaining cloud where the current frame has none.

**Disocclusion is where accumulation costs.** Restricted to pixels whose coverage state
changed in the reference arm - the pixels reprojection has no valid history for -
`disoccGhost` rises 0.00023 -> 0.00389 -> 0.00490 -> 0.00665, a 29x spread across the route,
and runs about an order of magnitude above the unrestricted `ghostMean` at every segment. The
isolation works: the error concentrates exactly where the metric predicts it should.

---

## 5. Status against T152's requirements

| required | status |
|---|---|
| deterministic outside -> approach -> entry -> interior -> openings -> exit route | yes, one continuous Bezier, angular-step parameterised |
| per-frame silhouette position/width | yes, `silCx/silCy/silW/silH` |
| alpha-edge stability | yes, `edgeFraction` |
| flicker | yes, direction-reversing threshold crossings, distinguished from ordinary sweep flips |
| ghosting | yes, cross-arm `ghostMean/Max/Bias` |
| disocclusion | yes, `disoccFraction/disoccGhost` |
| column connectivity | yes, `colRunsMean/Max` |
| inner-sky run | yes, `innerSkyRunMax/Mean` |
| occupied/empty/re-entry continuity | yes, `reEntryMean` |

T152's stated gating purpose - "must pass before T156/T157 can bank" - no longer applies:
T153 stopped at 1.63x and T154-T157 closed behind it. The fixture and this baseline stand on
their own as the shipped renderer's motion behaviour, and section 3's connectivity and
section 4's flicker finding are inputs T098b and T139 can use.

**Not claimed.** This is one route on one fixture at ULTRA. It is a baseline, not a pass/fail
threshold - no acceptance bound is proposed here, because none can be justified from a single
storm. The exit leg measures the storm leaving frame rather than a silhouette, and a future
run wanting exit-silhouette evidence would need a heading that keeps the storm partly in view.
