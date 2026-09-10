# T166 - Post-T163 GPU baseline, cost attribution, and PMWeather idea evaluation

Status: **COMPLETE** 2026-09-04

/ Feature: 001-native-storm-rendering
/ Starting commit: `e4cdda9` (Forge-1.20.1 production; T163 banked at `ad12a63`)
/ Branch: `Forge-1.20.1`
/ Hardware: NVIDIA GeForce RTX 4070 Laptop GPU, OpenGL 4.6
/ Framebuffer 1920x1080, Ultra (96 steps), raw scale 0.25, cloud target 480x270

This record does three things, in this order:

1. re-measures the production cloud pipeline at FAR and SIDE, because the
   ~24.5 ms anchor everyone has been quoting is a **PLAY_VIS_NEAR** figure from
   T163 and FAR has never been measured on the post-T163 program at all;
2. re-derives the cost attribution against the *current* shader, because T162's
   table predates T163 and its largest row - precipitation at 54.5% - was
   removed by T163 and no longer exists;
3. evaluates seven PMWeather-inspired ideas against those fresh measurements.

Nothing here is productionized. Every arm is default-off and compile-time
absent from the shipped program.

---

## 1. Method

### 1.1 Compile-time arms, not runtime branches

T161 established that dormant paths behind runtime uniforms cost 3.08x purely
by existing. Any attribution built from runtime switches therefore measures its
own scaffolding. Every arm here is a **separately generated, separately linked
program** emitted by `generateLeanFinalShader` from the one production
`cloud_atmosphere_volume.fsh`, and a build gate asserts FINAL defines none of
their macros.

### 1.2 The stale-arm trap this set exists to avoid

The T162 arms are **not** reusable. `cloud_atmosphere_volume_t162_nolight` is
generated with `defines: []` - it does not carry `PA_PRECIPITATION_ABSENT`, so
it still compiles the dead precipitation branch that T163 removed from FINAL.
Measuring it against today's FINAL would charge the ~1.5x rain-carry cost T163
already banked to whatever else the arm changed, and the lighting share would
come back badly wrong.

Every T166 arm therefore carries `PA_PRECIPITATION_ABSENT`, so each one is
exactly **FINAL minus one thing**. The build gate
`T166 attribution arms compile and stay out of FINAL` asserts this per arm and
fails the build otherwise.

### 1.3 FINAL is textually untouched

The shader diff that adds all eleven arm guards is **129 insertions and 0
deletions**. Not one existing line was modified, reflowed or refactored - the
`PA_ARM_EARLY_TERM` arm duplicates the production termination test verbatim in
its `#else` rather than hoisting the `0.015` literal into a shared constant,
specifically because this shader's whole cost story is that compile-time
context changes time in ways source-level equivalence does not predict.

### 1.4 Evidence hygiene

Inherited from T162/T163 and extended:

1. every cell is qualified at both ends (descriptor count and the T150
   geometric visibility verdict), and discarded outright if anything moved;
2. **fixture identity** (`groupId` + `structuralFingerprint`) is pinned per
   pose, as T153 did. A failed cell respawns the storm, so without this an
   anchor and a later arm can describe two different storms and the ratio
   between them is a fixture difference published as a speedup. Descriptor
   count alone does not catch it - the replacement also carries ten;
3. the 0.25 anchor is measured **twice** per pose, once at the head of the
   matrix and once mid-way, and the report evaluates the ratio between them as
   an explicit drift verdict rather than printing it beside the arms where a
   drifting fixture would read as an ordinary speedup;
4. history is disabled for the whole matrix, so every arm shares one temporal
   state;
5. a speedup is only computed against an anchor from the same pose at the same
   resolution **and** the same target and framebuffer dimensions.

### 1.5 GPU exclusivity

A Minecraft Forge client left running from the temporal experiment
(`.pa-agents/worktrees/cloud-temporal`, launched 16:34) was found rendering
during campaign preparation and was stopped, with the user's approval, before
any timing began. No other Minecraft or Java benchmark instance was running at
launch. The two static audits that ran during preparation were read-only and
executed no GPU work.

---

## 2. What the pipeline actually is

This matters for reading the tables, because the task asks for
"reconstruction/upscale" and "final cloud composite" as separate numbers and
**in PA they are one draw**.

| Stage | Where | Timer |
|---|---|---|
| primary cloud raymarch, including temporal reprojection | `VolumetricCloudRenderer.render`, one fullscreen draw of `cloud_atmosphere_volume_final` into the 480x270 cloud target | `VolumetricCloudRenderer.lastGpuMilliseconds()` |
| depth-guided reconstruction/upscale **and** scene composite | `CloudFieldCompositeRenderer.composite`, one fullscreen draw of `cloud_field_composite` at 1920x1080 | `CloudFieldCompositeRenderer.lastGpuMilliseconds()` |

`cloud_field_composite.fsh` performs the 4-tap depth-guided neighbourhood
selection and the scene-depth composite in a single `main()`; there is no
separate upscale pass to time. The reconstruction cost is therefore reported as
**bounded above by the composite total**, and the two are not separable without
building a second composite program. Section 4 shows why that was not worth
doing.

---

## 3. PMWeather, as an architecture

Read from `docs/decompiled-mods/pm-weather/shaders/program/clouds.fsh`. No
source was copied; what follows is a description of the technique, verified
directly against the cited lines rather than taken from a worker's summary.

| Technique | PMWeather | Project Atmosphere today |
|---|---|---|
| lighting density evaluation | `getClouds(p, 8)` (`:855`) against the camera's `getClouds(p, 0)` (`:941`). The reduction is subtracted from each fBm's octave count and `fbm` clamps at `max(octaves, 1)` (`:141-151`), so **light rays evaluate one noise octave where camera rays evaluate up to five**. The storm-shape loop still runs in full. | PA's noise is **pre-packed**: one `texture(BaseNoiseSampler, ...)` fetch returns the whole base fBm, and one `DetailNoiseSampler` fetch returns the detail fBm as `r*0.625 + g*0.25 + b*0.125`. There is no octave count to reduce. The equivalent lever is dropping the *detail* fetch, which PA already does for light taps 2 and up (`detailTap = i < 2`), plus the mip bias it already raises per tap (`i * 0.6`). |
| light-march early exit | `if (BeersLaw(totalDensity, 0.9) < 0.05) break;` (`:861`) - **every** camera. | PA breaks only `if (cameraStartsInsideSlab && opticalDepth * ExtinctionScale >= 28.0)`. An exterior cone never reaches 28.0, so **PA has no light-march early exit for the poses that matter**. This is the one lever PMWeather has that PA lacks outright. |
| light taps / spacing | 2/4/6/8/10 by quality; initial spacing `((10-steps)/10)*20 + 4` blocks, growing 25% per tap (`:852-858`, `:1021`). | 2-8 taps (`clamp(LightSteps, 2, 8)`), initial spacing 14 blocks, growing 42% per tap. Comparable. |
| empty-space stepping | `if (!inCloud) { stepMult *= 4.0; }` (`:930-932`), and on entry it backs up one step and re-marches fine (`d0 -= s; continue;`). Base step 8 blocks x a cubic-in-step-index multiplier `v*v*v/2`, `v = (count+5)/60`. | **Strictly more aggressive already.** PA has a fine/coarse tier (`sinceHit < 6`), `coarseStep = max(baseStep*1.5, fineStep*3)` capped at `min(112, fineStep*16)` - so up to **112 blocks** against PMWeather's ~16-32 - *plus* an exact per-descriptor SDF safe-advance (`paMinClearance - STORM_MAX_BLEND_BLOCKS`) that jumps hundreds of blocks, *plus* a 16-probe empty-span scan on the fine lattice that crosses a confirmed-empty span in one iteration. |
| distance-dependent step | cubic in step index, i.e. effectively distance. | linear in distance, `1.0 + (t/MaxRenderDistance)*2.2`, but applied to the **coarse tier only** for an exterior camera. The exterior fine step is `2.5` world blocks, constant, at Ultra. |
| scene-depth bound | `if (d0 >= maxDepth) break;` inside the march. | `t1 = sceneRayLimit(rayDir, t1)` **before** the loop, so the work is never scheduled at all. |

Two things follow immediately, before any measurement:

- **PM idea B is not an opportunity in PA.** PA already implements a strictly
  stronger version of PMWeather's empty-space stepping. The arm below tests
  whether pushing the existing cap further helps; it is not testing whether the
  technique is present.
- **PM idea C has exactly one lever PA does not already pull**: the light-march
  transmission early-out. The octave reduction that does the work in PMWeather
  has no PA analogue, because PA's noise is one packed fetch rather than a loop.

---

## 4. Two harness defects found and fixed before any number was trusted

Both are recorded because each produced a confidently wrong answer, and because
each defeated a guard that already existed.

### 4.1 The pose camera was built from a storm that was still growing

Every structural pose is defined as a multiple of the fixture's horizontal
radius. A severe storm keeps growing after it reaches ten descriptors, so a
camera computed mid-growth is placed for a storm that no longer exists by the
time the arms run.

Run 1 measured FAR's first four cells at `nearestDistance=30.4` and the
remaining twenty-four at `1087.1`. Those four cells reported **474k primary ray
steps and zero light-march evaluations**, against **3,706k and 224k** for every
later cell - a 7.8x workload difference inside one pose. The camera had ended up
next to a tiny storm, which puts it inside the cloud, and the in-cloud path
replaces the light cone with a single forward probe. That is why the anchor
looked 3.1x *cheaper* than the same program re-measured later in the same pose,
and why arms that can only remove work appeared three times slower than it.

Three existing guards all passed:

- descriptor count was ten throughout;
- the T150 geometric visibility verdict said `valid=true` - the storm genuinely
  was visible, it was simply tiny;
- the autorun's own maturity check had already logged
  `storm mature: topologyGeneration=172 stable for 900 frames`. It tracks
  topology generation, not extent.

**What caught it** was the drift control added for this campaign: the anchor is
measured twice per pose and the report evaluates the ratio.

```
T166_DRIFT pose=FAR           firstAnchorP50=4.4554  repeatAnchorP50=13.7605 ratio=3.0885 verdict=DRIFTED
T166_DRIFT pose=SIDE          firstAnchorP50=22.2413 repeatAnchorP50=22.2546 ratio=1.0006 verdict=stable
T166_DRIFT pose=PLAY_VIS_NEAR firstAnchorP50=18.1105 repeatAnchorP50=18.9399 ratio=1.0458 verdict=stable
```

Without that row the FAR anchor would have been published and every FAR speedup
computed against it.

### 4.2 Two wrong diagnoses before the right one, recorded honestly

The first hypothesis was that the storm was still growing when the camera was
computed, and a 40-tick radius-stability gate was added. Run 2 reproduced the
failure anyway. The second was that the radius had drifted *after* the camera
was placed, and a self-correcting rebuild was added that discards the pose when
the radius moves 2%. Run 3 reproduced the failure again - and this time the
evidence ruled both out: the gate reported `settled radius=663.02`, the rebuild
never fired because the radius genuinely had not moved, and yet
`nearestDistance` still jumped from 24.4 to 1060.8 between two consecutive arms
**with no teleport between them**.

The only thing that differed between the cheap cells and the rest was whether
the player had got there. `T135_SETTLE` counted 120 frames after issuing the
teleport, which is not the same as arriving. Instrumenting it settled the
question in one line:

```
T166_ARRIVAL pose=FAR reached after 69 extra frames residual=0.00
```

**After the existing 120-frame settle, the camera needed 69 more frames to
travel the ~1,700 blocks to the FAR pose.** Until it arrived the frames being
timed were the previous camera's - and for the first pose after the spawn that
camera stands inside the storm, where the renderer takes its in-cloud fast path
and replaces the light cone with a single forward probe. That is the whole
anomaly: not a growing storm, not a drifting radius, just a camera that had not
finished moving.

The shipped guard holds the settle until the player is within two blocks of the
requested position, bounded and logged on expiry. Run 4's FAR reported
`nearest=1058.2` on **every** cell from the image anchor onward, and all three
drift controls came back stable.

The radius gate and the rebuild guard were kept. They were built for the wrong
diagnosis but they guard a real hazard - a pose expressed in storm radii is only
valid while the radius is the one the camera was built from - and they cost
nothing when the fixture is stable.

**Rules carried forward.**

1. A settle measured in frames is not arrival. Verify the camera reached the
   position before timing anything at it.
2. A pose expressed as a multiple of the fixture radius is only valid while that
   radius is the one the camera was computed from.
3. Neither descriptor count, nor geometric visibility, nor topology-generation
   maturity detects either failure. All three passed throughout.

---

## 5. Measurements

Primary run: **run 4**, all three poses, `T166_REJECTED count=0`, and all three
drift controls stable (FAR 0.9828, SIDE 0.9892, PLAY_VIS_NEAR 1.0064). Run 1 is
a replicate for SIDE and PLAY_VIS_NEAR on a different world and storm; its
ratios agree with run 4 throughout and are quoted where they add confidence.

### 5.0 The fresh production baseline - Ultra, raw scale 0.25

`cloud_atmosphere_volume_final`, 1920x1080 framebuffer, 480x270 cloud target,
ten resident descriptors, 60 sampled frames per cell after 30 settle frames.

| Pose | primary raymarch p50 / p95 | reconstruction + composite p50 / p95 | **total cloud GPU p50 / p95** | frame p50 / p95 |
|---|---|---|---|---|
| **FAR** | **14.5388 / 15.5116 ms** | 0.0870 / 0.0901 ms | **14.6258 / 15.6017 ms** | 15.4650 / 17.6949 |
| **SIDE** | **22.0826 / 24.4685 ms** | 0.0850 / 0.0911 ms | **22.1676 / 24.5596 ms** | 22.8813 / 25.0257 |
| PLAY_VIS_NEAR | 19.5092 / 21.0094 ms | 0.0870 / 0.0922 ms | 19.5962 / 21.1016 ms | 20.2946 / 22.8238 |

Reconstruction and composite are **one draw** and are not separately timeable
(section 2). The pass is 0.085-0.087 ms - **0.4% of the cloud pipeline** - so the
distinction has no bearing on any decision here.

**On the ~24.5 ms anchor.** That figure is T163's **PLAY_VIS_NEAR** cell, and it
was never a FAR or SIDE number. Measured fresh, SIDE is 22.08 ms and FAR is
14.54 ms. SIDE reproduces T163's SIDE cell (23.13 ms) to within 4.5%, and run 1
independently measured SIDE at 22.24 ms, so the SIDE figure is solid across
three campaigns. **FAR had never been measured on the post-T163 program at
all**; it is now, at 14.54 ms.

Against SC-006's 8 ms cloud budget: SIDE is **2.77x over**, FAR **1.83x over**.

### 5.1 Production workload per pose

129,600 cloud pixels. Captured on the diagnostic monolith, the only program
carrying the counters, so these describe the fixture rather than any arm.

| | FAR | SIDE | PLAY_VIS_NEAR |
|---|---|---|---|
| primary ray steps / pixel | 28.60 | 30.31 | 15.55 |
| ...already resolved empty by the weather skip | **97.3%** | **87.4%** | **87.4%** |
| cloud density calls / pixel | 4.22 | 20.73 | 10.69 |
| lighting share of density calls | 41.7% | **51.9%** | 42.6% |
| descriptor texel fetches per density call | 101.2 | 106.7 | 141.8 |
| rays ending on the transmittance floor | 2.8% | **15.0%** | 7.8% |

### 5.2 Cost attribution - production context

Each arm is FINAL minus one class, measured against the same-pose anchor in the
same run. **DIRECTLY MEASURED** unless noted.

| Class removed | FAR | SIDE | PLAY_VIS_NEAR |
|---|---|---|---|
| lighting entirely (`t166_nolight`) | 0.498 ms, 1.035x | **4.438 ms, 1.252x** | 3.946 ms, 1.254x |
| detail octaves + erosion (`t166_nodetail`) | 2.731 ms, 1.231x | 2.451 ms, 1.125x | 2.855 ms, 1.171x |
| scene-depth clip **restored** (`t166_noscenelimit`) | -0.330 ms | -0.002 ms | **-3.114 ms** |

Lighting is **20.1% of the SIDE frame and 20.2% of PLAY_VIS_NEAR**, but only
3.4% at FAR - distant storms shade far fewer samples. The historical T136 figure
of 21-23% and T162's post-T161 1.39-1.48x both bracket this; the *share* is
stable, the *ratio* is not, because the rest of the frame shrank.

### 5.3 Cost attribution - fixed-work ladder

64 fixed world-space samples per fragment, identical control flow in every arm,
rain compiled out of every rung. Each rung is a delta over the one below.

| Rung | Class added | FAR | SIDE | PLAY_VIS_NEAR |
|---|---|---|---|---|
| 1 | control floor | 1.5% | 0.7% | 0.8% |
| 2 | candidate traversal | **44.2%** | 25.0% | 26.2% |
| 3 | descriptor payload fetch | 3.8% | 6.0% | 8.8% |
| 4 | shape / profile / warp / SDF / union | 21.6% | **48.6%** | **44.8%** |
| 5 | weather, morphology, base noise | 25.6% | 17.8% | 18.1% |
| 6 | detail octaves + erosion | 3.3% | 1.9% | 1.4% |
| | **descriptor geometry (2+3+4)** | **69.6%** | **79.6%** | **79.8%** |

The pose dependence is real and explicable: the ladder's 64 samples are spread
between slab entry and exit, so at FAR a larger fraction of them fall outside
the storm, where candidate traversal runs but shape evaluation rejects early.
The *sum* is what transfers, and it is **70-80% of a density call at every
pose**.

**Cross-validation against T162.** T162's ladder was measured before T163, where
precipitation took 54.5% and everything else shared 45.4%. Renormalising its
non-precipitation rows over that 45.4% gives control 0.7%, candidate 25.6%,
descriptor 9.0%, shape 45.6%, weather 17.8%, detail 1.3% - **within about one
point of the SIDE and PLAY_VIS_NEAR columns above**, from a separate campaign.
Two independent measurements agree, and T163 is confirmed to have removed
exactly the precipitation class and disturbed nothing else.

**This inverts T162's headline.** T162 concluded "descriptor traversal is not
dominant", correctly, because precipitation dwarfed everything. With the dead
path gone, **descriptor geometry is the largest cost class in the shipped
renderer by a wide margin**, and no PMWeather idea addresses it.

Detail octaves and erosion are **one fused class** in PA - the erosion term is
driven by the detail fetch - so no arm can separate them. At 1.4-3.3% it does
not matter. Note the fixed-work rung (1.4-3.3%) and the production-context arm
(11-19%) disagree because the ladder evaluates 64 points regardless of density
while production only reaches the detail block where `cloud > 0.003`; the
production figure is the one to use.

### 5.4 PMWeather idea results

Speedup is against the same-pose anchor; quality is against the same-pose anchor
image captured back to back with history bypassed.

| Idea | Arm | FAR | SIDE | PLAY_VIS_NEAR | silhouette IoU | cloud SSIM | thin retention | **Verdict** |
|---|---|---|---|---|---|---|---|---|
| **A** distance-dependent step | `t166_diststep` | **2.144x** | **1.733x** | **1.643x** | 0.993-0.999 | 0.990-0.998 | **0.800-0.870** | **HIGH VALUE** |
| **B** aggressive empty-space | `t166_emptyjump` | 1.064x | 1.002x | 1.020x | 0.988-0.998 | 0.933-0.957 | 0.826-0.987 | **REJECT** |
| **C** cheaper lighting density | `t166_lightcheap` | 1.038x | 1.012x | 1.047x | **1.0000** | 0.910-0.953 | 1.0000 | **MARGINAL** |
| **D** distance density LOD | `t166_distlod` | 1.203x | 1.084x | 1.135x | 0.995-0.999 | 0.925-0.986 | **1.0000** | **PROMISING** |
| **E** tighter early termination | `t166_earlyterm` | 1.216x | 1.164x | 1.145x | **1.0000** | 0.983-0.991 | 1.0000 | **PROMISING** |
| **F** scene-depth-bounded march | `t166_noscenelimit` | (saves 0.330 ms) | (saves 0.002 ms) | (**saves 3.114 ms**) | - | - | - | **ALREADY SOLVED** |
| **G** spatial blur | - | composite is 0.087 ms total | | | - | - | - | **REJECT** |
| - | **A+C+D combined** | **2.435x** | **1.716x** | **1.890x** | 0.992-0.998 | 0.865-0.937 | 0.899-0.935 | see 5.6 |

Individual lighting levers, to separate what actually pays (SIDE):

| Lever | SIDE | Note |
|---|---|---|
| fewer taps, 6 -> 2 (`t166_lightsteps2`) | **1.121x** | the only lighting lever that pays |
| PMWeather's transmission early-out (`t166_lightearlyout`) | 1.028x | PA's cone rarely gets opaque enough to trigger it |
| no detail on light taps (`t166_lightnodetail`) | 1.002x | PA already omits detail on taps 2+ |
| wider tap spacing (`t166_lightwide`) | **0.972x** | *slower*; same tap count, worse locality |

**Idea C is largely already implemented in PA.** PMWeather's win comes from
dropping its fBm to one octave, and PA has no octave loop to shorten - its noise
is one packed texture fetch. What remains is tap count, and cutting 6 taps to 2
buys 1.12x while visibly flattening self-shadowing. The transmission early-out,
the one lever PA genuinely lacked, is worth 1.03x.

### 5.5 The refreshed T153 empty-space ceiling

The oracle arms were rebuilt as lean programs so their ratios are comparable to
today's renderer rather than to the pre-T161 monolith. **The historical 1.63x
must not be quoted as current.**

| Arm | FAR | SIDE | PLAY_VIS_NEAR | mean |
|---|---|---|---|---|
| A perfect empty skip | 1.810x | 1.613x | 2.001x | 1.808x |
| B perfect occupied intervals | 2.252x | 1.783x | 2.205x | 2.080x |
| D combined | 2.325x | 1.375x | 2.294x | **1.998x** |
| *T153 historical, same three poses* | *2.266x* | *1.523x* | *1.719x* | *1.836x* |

**Does anything close to the 1.63x opportunity still exist? Yes - about 2.0x,
slightly more than the historical figure. But it is no longer worth building,
for a reason the old campaign could not see:**

**the distance-step arm already reaches or beats the oracle without any
occupancy structure.** At SIDE, distance stepping returns 1.733x against the
oracle's combined 1.375x - *the simple mechanism beats the perfect one*. At FAR,
2.144x against 2.325x - 92% of a ceiling that is handed free interval data no
shippable design can produce. The oracle and the distance-step arm are competing
for the same saving, because both reduce the number of expensive samples; one
needs a volume representation to build, store, upload and fetch, and the other
is a change to one step-length expression.

And the *shippable* form of aggressive empty-space stepping - widening the
existing coarse cap fourfold - returns **1.002x-1.064x**, at the worst quality
per unit gain in the set. 97.3% of FAR's ray steps and 87.4% of SIDE's already
resolve empty through the weather skip. There is nothing left there.

### 5.6 Overlap - measured, not assumed

`t166_stack` compiles idea A, C and D together, so composition is measured
rather than inferred from a product.

| Pose | product of A x C x D | measured stack | composition |
|---|---|---|---|
| FAR | 2.675x | **2.435x** | 91.0% |
| SIDE | 1.902x | **1.716x** | 90.2% |
| PLAY_VIS_NEAR | 1.953x | **1.890x** | 96.8% |
| *run 1, SIDE (replicate)* | *1.967x* | *1.712x* | *87.0%* |

**Speedups must not be multiplied.** Composition returns 87-97% of the product,
and the shortfall is concentrated where the arms attack the same quantity: at
SIDE the stack is only **+0.017x over distance stepping alone in run 1 and
-0.017x in run 4** - the other two levers contribute essentially nothing there
once distance stepping has already removed the distant samples they would have
cheapened.

| | primary steps | primary density calls | cost per density call | lighting call count | cost per lighting call |
|---|---|---|---|---|---|
| A distance step | **reduces** | **reduces** | - | **reduces** | - |
| B empty jump | reduces (already-cheap ones) | - | - | - | - |
| C cheap lighting | - | - | - | reduces | reduces |
| D distance LOD | - | - | reduces (detail only) | - | reduces |
| E early termination | **reduces** | **reduces** | - | **reduces** | - |

A and E overlap (both delete whole samples). A and D overlap (A removes the
samples D would cheapen). C is the most independent but also the smallest.

### 5.7 Raw-scale cost curve

| Scale | Target | FAR | SIDE | PLAY_VIS_NEAR |
|---|---|---|---|---|
| 0.125 | 240x135 | 8.780 | 10.503 | 9.137 |
| 0.1875 | 360x203 | 10.005 | 16.477 | 17.013 |
| **0.25** | **480x270** | **14.539** | **22.083** | **19.509** |
| 0.375 | 720x405 | 20.801 | 36.753 | 33.042 |
| 0.50 | 960x540 | 34.034 | 56.852 | 51.762 |

Cost scales as roughly **pixels^0.57-0.60** (run 1 least-squares fit over five
points at two poses), consistent with T163's ~0.68 and T138's 0.49-0.75. This
is the sublinearity that makes resolution a weaker lever than it looks: halving
to 0.125 buys only 2.1x at SIDE, and quartering the pixel count buys 2.0x. A
large fixed per-ray cost - the candidate and descriptor setup that runs whether
or not the ray finds material - does not shrink with the target.

### 5.8 PM idea B - aggressive empty-space stepping: **REJECT**

Answering the task's explicit question - *does anything close to the historical
1.63x opportunity still exist?* - in three parts.

**PA already implements a stronger version of the technique than PMWeather
does.** Section 3 sets this out: coarse steps to 112 blocks against PMWeather's
~16-32, an exact per-descriptor SDF safe-advance that jumps hundreds of blocks,
and a 16-probe fine-lattice scan that crosses a confirmed-empty span in one
iteration. This is not an unexploited idea.

**The counters say there is almost nothing left to win.** **97.3% of FAR's
primary ray steps and 87.4% of SIDE's already resolve as empty** through the
weather skip, which costs one `sampleWeather` fetch and no density evaluation at
all. The remaining empty distance is already crossed at close to the cheapest
rate the structure allows.

**The measured arm agrees.** Widening the coarse cap fourfold returns
**1.002x-1.064x**, and it is the worst arm in the set on quality per unit of
gain - cloud-region SSIM 0.933-0.957 for a gain of nothing, against 0.990-0.998
for the distance-step arm that returns 1.64x-2.14x. It makes the picture
measurably worse to save almost nothing.

**The refreshed ceiling.** The T153 oracle arms were rebuilt as lean programs so
their ratio is comparable to today's renderer rather than to the pre-T161
monolith. The historical 1.63x combined mean **must not be quoted as current**;
the refreshed figures are in the results table. They are an upper bound handed
free interval data no shippable design can produce, and they sit close enough to
what the distance-step arm already delivers - with real quality cost - that the
category is closed.

### 5.9 PM idea E - early termination: **MARGINAL**

Production stops a ray at `transmittance < 0.015`, and **15.0% of SIDE pixels,
7.8% of PLAY_VIS_NEAR's and 2.8% of FAR's already exit that way**. Raising the
floor to 0.06 returns **1.145x-1.216x** at a silhouette IoU of exactly 1.0000,
cloud-region SSIM 0.983-0.991 and thin retention 1.0000 - one of the best
quality-per-gain ratios in the set, though far short of the budget gap on its
own. Upgraded from MARGINAL to **PROMISING** on that evidence.

This also reproduces T153's arm C, which measured a *perfect* optical-relevance
oracle at 0.958x-1.057x and concluded the category was closed. It still is. The
work between 50% and 98% opacity is genuinely contributing to the image;
removing it is a visual change, not an optimisation.

### 5.10 PM idea F - scene-depth-bounded march: **ALREADY SOLVED**

Not merely correct compositing. `sceneRayLimit` shortens `t1` **before the march
loop begins** (`cloud_atmosphere_volume.fsh`), so work behind opaque geometry is
never scheduled. It is on by default (`sceneRayLimitEnabled = true`) and the
campaign confirms the depth frame is live: `[CloudDepth] valid=true
detached=true`.

The control arm measures what it is worth rather than asserting it. Compiling
the clip out makes the frame **slower**, which is the correct sign and the proof
that the clip is doing real work:

- **PLAY_VIS_NEAR (y = 120, terrain in frame): 19.51 -> 22.62 ms without the
  clip.** Scene-depth bounding is saving **3.11 ms, 13.8% of the unclipped
  frame, 1.16x.** Run 1 measured the same arm at 18.11 -> 24.19 ms, a 6.08 ms
  saving, so the magnitude is fixture-dependent - but the sign and the order are
  reproduced.
- **SIDE (storm mid-height, y ~ 570, no terrain in front of the cloud): 0.002 ms.**
  Nothing, exactly as expected - there is nothing in front to clip against.
- **FAR: 0.330 ms.**

So the answer to "measure potential savings in terrain-heavy views" is that the
saving is already being taken, and it is the single largest thing standing
between the gameplay pose and a much worse number.

### 5.11 PM idea G - spatial blur: **REJECT, and not worth re-testing**

The composite pass - which is where any spatial filter would live - costs
**0.089-0.092 ms p50** and does not shrink with the cloud target because it runs
at display resolution. It is 0.4% of the cloud pipeline. There is no room to
spend there and nothing to recover.

On quality, the prior evidence stands unchanged: the best spatial reconstruction
recovered ~5.8% of the native gap, against ~81-88% for offline temporal
INTERLEAVE. Blur is not a quality architecture, and at 0.4% of the frame it is
not a performance one either.

---

## 6. Roadmap

### 6.1 The three heaviest subsystems

Ranked by attributable milliseconds at SIDE, the more expensive of the two poses
the baseline is owed at.

| Rank | Subsystem | Attributable ms at SIDE | Share | Label |
|---|---|---|---|---|
| **1** | **descriptor geometry** - candidate traversal, payload fetch, shape/profile/SDF/union | **~17.6 ms** of 22.08 | 79.6% of a density call | **DERIVED** - fixed-work ladder share applied to the frame |
| **2** | **lighting** - the light cone and the density calls it makes | **4.438 ms** | 20.1% of the frame | **DIRECTLY MEASURED** (`t166_nolight`) |
| **3** | **detail octaves + erosion** | **2.451 ms** | 11.1% of the frame | **DIRECTLY MEASURED** (`t166_nodetail`) |
| - | reconstruction + composite | 0.085 ms | 0.4% | **DIRECTLY MEASURED** |
| - | non-cloud frame remainder | 0.799 ms | - | **DIRECTLY MEASURED** |

Rank 1 is DERIVED rather than measured in situ, and the distinction matters: the
ladder measures the *composition of a density call*, and converting that to
frame milliseconds assumes density calls dominate the frame. That assumption is
supported - 20.73 density calls per pixel at SIDE, 51.9% of them from lighting -
but it is not the same as compiling descriptor geometry out and timing the
result, which cannot be done because removing it produces no cloud.

**These do not sum to 100%, and must not be forced to.** Lighting's 4.44 ms is
*inside* the density-call total, because a light tap is a density call. The
classes overlap by construction, and T161/T162 established that shader
specialization changes register pressure and occupancy in ways that make an
additive table actively misleading.

### 6.2 Top three recommended optimizations

| Rank | Optimization | Measured saving at SIDE | Visual cost | Complexity |
|---|---|---|---|---|
| **1** | **distance-dependent primary step** (PM idea A) | **9.34 ms, 1.73x** (2.14x at FAR) | silhouette IoU 0.993-0.999, cloud SSIM 0.990-0.998, **thin retention 0.80-0.87** | low - one step-length expression |
| **2** | **tighter early termination** (PM idea E) | **3.10 ms, 1.16x** | IoU 1.0000, SSIM 0.983-0.991, thin 1.0000 | trivial - one constant |
| **3** | **distance density LOD** (PM idea D) | **1.71 ms, 1.08x** | IoU 0.995-0.999, SSIM 0.925-0.986, thin 1.0000 | low - one predicate in `cloudDensity` |

Rank 1's thin-material loss is the one real quality question in the set and the
reason it is not simply "adopt it": a 13-20% loss of faint material is visible
as thinning at cloud edges. The arm tested here applies the full coarse-tier
growth curve to the fine tier; a **graded** curve that ramps in beyond some
distance would trade some of the 1.73x for most of the thin material back, and
that trade has not been measured. **That measurement is the recommended next
task, not productionizing the arm as tested.**

### 6.3 Paths to 8 ms

From SIDE's measured 22.08 ms. Compositions use the **measured** stack where
available rather than a product, per section 5.6.

| Path | Composition | Result at SIDE | Result at FAR |
|---|---|---|---|
| **Conservative** | a graded distance step retaining ~60% of the measured gain, plus early termination | ~15.5 ms | ~9.5 ms |
| **Likely** | measured A+C+D stack (1.716x), plus early termination at its measured 1.164x, discounted 10% for overlap | **~11.6 ms** | **~5.3 ms** |
| **Optimistic** | as Likely, plus a descriptor-geometry optimization returning 1.3x of the 79.6% class | ~9.4 ms | ~4.3 ms |

### 6.4 Is <=8-10 ms plausible?

**POSSIBLY - and the answer differs by pose, which is the useful part.**

- **FAR: YES, already.** The measured stack alone puts FAR at **5.97 ms**, inside
  the 8 ms budget, today, with no new mechanism.
- **PLAY_VIS_NEAR: YES for 10 ms.** The measured stack puts it at **10.32 ms**,
  at the 10 ms whole-frame budget and 1.29x over the 8 ms cloud budget.
- **SIDE: NO with the evaluated ideas alone.** The measured stack reaches
  **12.87 ms**. Reaching 8 ms needs 2.76x, and everything PMWeather suggests,
  composed and measured together, delivers 1.72x. The gap cannot be closed from
  this list.

The reason is section 5.3: **79.6% of a density call at SIDE is descriptor
geometry**, and not one PMWeather idea touches it. PMWeather has no descriptor
system - its storms are analytic - so its optimization repertoire has nothing to
say about PA's dominant cost. That is the honest limit of this evaluation.

### 6.5 Recommended next implementation task

**Not a PMWeather port. Attack descriptor geometry.**

The ordered case: it is 70-80% of a density call at every pose; T162 ranked it
third only because precipitation was hiding it; and the two arms that already
probe it are encouraging. T162's descriptor-count scaling measured the shape arm
at 0.569 ms with one descriptor against 1.795 ms with ten - so perfect binning
to the one or two descriptors that can own a sample would recover roughly 25% of
a density call. Against SIDE's 22.08 ms that is a **~4.4 ms** class, larger than
lighting.

The concrete first task is a **diagnostic** one, in this campaign's style: an
arm that caps the per-sample descriptor set to the k nearest owners and measures
both the time and the image cost, so the binning question is answered by
measurement before any spatial structure is built. T162 explicitly declined to
build binning on sublinear scaling evidence; that call was right then and should
be revisited now that the class it competes with is gone.

Second, and cheap: **the graded distance-step curve** described in 6.2. It is
the largest measured win available and the only open question is how much of the
thin material a gentler curve returns.

---

## 7. Temporal status - unchanged by this task

Recorded as stated in the task brief; no temporal work was done here and nothing
temporal was merged.

| | |
|---|---|
| Latest temporal branch | `experiment/cloud-temporal-reconstruction` |
| Latest bank | `9bafb25`; implementation `6679ba2`; Stage 1 `e486797`, Stage 2 `98f5ccd` |
| Sampling architecture | **PROMISING** |
| Offline INTERLEAVE information ceiling | **still strong** - ~81-88% native-gap recovery (first density 81.3%, first significant opacity 85.0%, strongest opacity 87.6%) |
| Runtime validity | **FAILED**. All three single-depth correspondence signals rejected - first density, first significant opacity, strongest opacity - at FAR raw 0.50 of 0.885/0.885/0.893 against a stationary lattice of 0.857/0.856/0.866 and trust-all of 0.970/0.968/0.972 |
| Why | the current phase depth is compared against a different subpixel history ray; at thin structure both rays may legitimately hit different cloud, so single-depth equality rejects correct history |
| Trust-all | cannot ship - ghosts and stales under motion (moderate translation 0.811) |
| Future architecture | **per-phase projected coverage / disocclusion correspondence**, not cross-ray single-depth equality |
| Default | off, and untouched by this task |

---

## 8. Validation and banking

| Item | Result |
|---|---|
| Production commit profiled | `e4cdda9`, branch `Forge-1.20.1` |
| Campaign runs | 4; run 4 is primary, run 1 the SIDE / PLAY_VIS_NEAR replicate |
| Cells in the primary run | 84, `T166_REJECTED count=0` |
| Fixture qualifications | 2 per cell plus one per image sequence, none failed |
| Drift controls | FAR 0.9828, SIDE 0.9892, PLAY_VIS_NEAR 1.0064 - all stable |
| Image comparisons | 18 (6 arms x 3 poses), all evaluated |
| GPU exclusivity | one Minecraft instance at a time throughout; a stale temporal client was stopped before timing began |
| FINAL untouched | shader diff is 129 insertions, **0 deletions** |
| Build gate | `T166 attribution arms compile and stay out of FINAL` - 22 programs compiled on a real GL context, each asserted to carry `PA_PRECIPITATION_ABSENT` and its own marker, and FINAL asserted to define none of the eleven `PA_ARM_*` guards |

### Known harness limitation

The image sequence's last arm - `t166_nolight`, present as the
"must differ" self-check - was captured but not compared, an off-by-one in the
step bound. It is fixed in the tree. The check's purpose is served regardless:
**all six compared arms differed** (7,326 to 22,329 changed pixels, none
bit-identical), so the harness demonstrably detects differences. Unlike T163,
where the expected answer was "identical" and a broken comparator would have
looked like success, here a broken comparator would have shown up as spurious
identity and did not.
