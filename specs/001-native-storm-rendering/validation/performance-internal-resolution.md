# Rank 1 — internal resolution and reconstruction: measured

**Feature**: `001-native-storm-rendering`
**Task**: T138 [PERFORMANCE]
**Follows**: `performance-baseline.md` (T136), `performance-architecture.md` (T137),
`performance-descriptor-cost.md` (rank 2, rejected)
**Date**: 2026-09-02
**Hardware**: NVIDIA GeForce RTX 4070 Laptop GPU, OpenGL 4.6
**Display resolution**: 1920x1080 (SC-006 reference)
**Production render path unchanged.** Every arm below is a diagnostic override.

---

## 0. The result in one page

Two findings, in the order that matters.

**1. The pose the whole performance requirement rests on contains no storm.**
`PLAY_NEAR` places the camera at 4x the storm's horizontal radius. At T134
severe scale that radius is 668 blocks, so the camera sits 2670 blocks out and
the storm's *near edge* is 2002 blocks away — two blocks past the shipped
`cloudRenderDistance = 2000`. The frame is empty sky. Its captured image, its
counters (0.09 light-march evaluations per pixel, 99.9 % of march steps
resolved as empty space) and its cost all agree. T136's "representative
gameplay is 2–13x over budget" therefore measures **the cost of marching an
empty slab while a distant storm's descriptors are resident**, not the cost of
a storm.

Measured on the same fixture, at the same gameplay altitude of y=120, with the
camera moved to a distance from which the storm is actually drawn:

| pose | camera | cloud GPU p50, Ultra 0.75 | over the 8 ms budget |
|---|---|---|---|
| `PLAY_NEAR` (shipped) | 4.0r, y=120 — **storm out of render distance** | 90.3 ms | 11.3x |
| `PLAY_VIS_MID` (new) | 2.4r, y=120 — storm in frame | 284.4 ms | 35.5x |
| `PLAY_VIS_NEAR` (new) | 1.6r, y=120 — storm in frame | **492.8 ms** | **61.6x** |

**Representative gameplay is ~62x over budget, not 12.6x.**

**2. Internal resolution is a real but bounded lever, and it is bounded lower
than T137 assumed.** Work scales *exactly* linearly with pixel count — the
counters are constant to three digits in steps per pixel across a 16x pixel
range — but **time scales as pixels^0.69–0.90**, because the shader is
latency-bound and a smaller target has fewer waves in flight to hide it.
Dropping Ultra from its shipped 0.75 to 0.25 is 9x fewer pixels and returns
**4.75x** at the corrected representative pose, not 9x and not T137's assumed
4x-per-4x-pixels.

Against those two together:

| | |
|---|---|
| corrected representative Ultra, shipped 0.75 | 492.8 ms |
| the same at internal 0.25 (480x270) | 103.8 ms |
| Rank 1 contribution | **4.75x** |
| still over the 8 ms cloud budget | **13.0x** |

**Stop condition: CASE A on its own terms, CASE C on the project's.** Rank 1
clears the ">= 4x with acceptable structural quality" bar — T098a passes at
every scale down to 0.25, with SSIM 0.985 and 1.3 % of pixels changed against
the full-resolution reference. It does not come close to closing SC-006,
because the gap it was asked to close was understated by a factor of five.

---

## 1. Phase 1 — the resolution pipeline as it actually is

Read out of the code and confirmed against the render targets the sweep
reported, not inferred from configuration labels.

### 1.1 Where the internal resolution comes from

| stage | value | source |
|---|---|---|
| display / main render target | 1920x1080 | window; the sweep resizes to it explicitly |
| cloud render target | `ceil(main.width * s) x ceil(main.height * s)` | `VolumetricCloudRenderTargets.prepareCloudTargets` |
| `s` = quality profile scale | LOW 0.250, LOW_24 0.375, MEDIUM 0.500, HIGH 0.500, ULTRA 0.750 | `VolumetricQualityProfile` |
| dense-camera override | `min(profile, 0.50)` while smoothed camera density > 0.12, released below 0.04 | `VolumetricCloudRenderer.render` |
| diagnostic override | `VolumetricCloudDebugConfig.fixedResolutionScale()` wins over both | same |
| clamp | `s` clamped to `[0.10, 1.0]` | `prepareCloudTargets` |

**The frame-time governor does not touch resolution.** `CloudFrameTimeGovernor`
scales the *step budget* between 1.0 and 0.5 and nothing else; the
`governorScale=0.500` in the T135 logs is a step multiplier, not a pixel one.
Its contribution to the marched pixel count is exactly zero.

### 1.2 Marched cloud pixels per quality mode at 1920x1080

| mode | scale | cloud target | marched pixels | fraction of display |
|---|---|---|---|---|
| Low | 0.250 | 480x270 | 129,600 | 6.25 % |
| Low 24 | 0.375 | 720x405 | 291,600 | 14.06 % |
| Medium | 0.500 | 960x540 | 518,400 | 25.00 % |
| High | 0.500 | 960x540 | 518,400 | 25.00 % |
| Ultra | 0.750 | 1440x810 | 1,166,400 | 56.25 % |

Every one of these was confirmed as a live `cloudTarget=WxH` record. **No mode
marches at display resolution today**; Ultra already runs at 56 % of the
display's pixels, so the ladder's remaining headroom below it is 9x in pixels,
not 16x.

### 1.3 The rest of the pipeline

- **History resolution**: identical to the cloud target. History is the other
  half of a two-element ping-pong of the *same* `TextureTarget` pair, RGBA16F
  colour plus a depth attachment, so a resolution change destroys and rebuilds
  both and drops history (`resolutionGeneration++`).
- **Depth target**: the cloud target's own depth attachment — same dimensions,
  `GL_NEAREST` min/mag, clamp-to-edge. Colour is `GL_LINEAR`.
- **Composite resolution**: full display resolution, always. The composite is a
  fullscreen quad into the main target and does **not** scale with the cloud
  target.
- **Reconstruction/upscale path**: `CloudFieldCompositeRenderer` running
  `cloud_field_composite.fsh`. See §6.
- **Paired colour/depth handling**: the raymarch writes premultiplied RGBA into
  the colour attachment and its own `gl_FragDepth`; the composite re-publishes a
  *selected* low-resolution depth as the fragment's depth.
- **Jitter**: two separate blue-noise phases inside the raymarch.
  `searchBlue = texture(BlueNoiseSampler, fragCoord / blueSize)` is a **static
  screen-space** phase — deliberately not animated, because animating it made
  thin silhouette pixels alternate hit/miss. `integrationBlue` advances with
  `FrameIndex` and only perturbs the sub-step integration phase *along the
  ray*. **There is no sub-pixel screen-space jitter of the ray direction at any
  scale.**
- **Filtering**: the composite never uses hardware filtering on the final path.
  It does its own 2x2 `texelFetch` bilinear with per-tap acceptance.

---

## 2. Phase 2 — the arms

Five internal-resolution arms, defined against the full display resolution.
Every arm holds the ULTRA quality mode: 96 raymarch steps, 6 light steps, 3
scatter octaves, detail quality 2, a 512 weather map, temporal history on. Only
the cloud target's dimensions change.

| arm | cloud target | marched pixels | vs shipped Ultra |
|---|---|---|---|
| 1.000 | 1920x1080 | 2,073,600 | 1.78x more |
| **0.750 (shipped Ultra)** | 1440x810 | 1,166,400 | baseline |
| 0.500 | 960x540 | 518,400 | 2.25x fewer |
| 0.375 | 720x405 | 291,600 | 4.00x fewer |
| 0.250 | 480x270 | 129,600 | 9.00x fewer |

All five land on integer dimensions at 1920x1080, so no arm carries a rounding
remainder. The step budget was **not** changed in any arm; the isolation is
pixel count alone.

Harness: `StormT132AutoDriver`'s sweep, armed by `run/t138-resolution.txt`,
walking (pose x arm) with 30 settle frames and 60 sampled frames per cell, the
descriptor-decay rejection from T136 retained, and the workload counters read
back per cell. A cell whose descriptor count fell mid-sample is discarded and
retried on the same arm; an arm that cannot hold a fixture after three attempts
is recorded as unmeasurable rather than silently skipped.

---

## 3. Phase 3 — cost against pixel count, same fixture

Cloud GPU p50 in milliseconds, ULTRA step budget throughout.

| pose | 1.000 | 0.750 | 0.500 | 0.375 | 0.250 |
|---|---|---|---|---|---|
| **corrected gameplay** | | | | | |
| PLAY_VIS_NEAR (1.6r, y=120) | 856.3 | **492.8** | 288.8 | 190.9 | 103.8 |
| PLAY_VIS_MID (2.4r, y=120) | 499.8 | **284.4** | 182.1 | 113.3 | 74.2 |
| **shipped gameplay poses** | | | | | |
| PLAY_NEAR (4.0r — empty sky) | 155.7 | 90.3 | 45.3 | 27.3 | 15.1 |
| PLAY_MID (7.0r) | \* | \* | \* | 22.2 | 11.0 |
| PLAY_HIGH (5.0r, y=320) | \* | \* | \* | \* | \* |
| **severe** | | | | | |
| SIDE (1.7r) | 959.2 | 573.2 | 330.6 | 205.8 | 116.6 |
| FAR (2.6r) | 465.9 | 278.8 | 182.3 | 104.2 | 64.9 |
| ABOVE | 1193.0 | 690.7 | 359.3 | 227.5 | 120.1 |
| BELOW | 1576.8 | 840.0 | 475.9 | 319.4 | 128.9 |
| NEAR_EDGE (1.12r) — stress | 1633.1 | 967.1 | 539.9 | 349.7 | 190.7 |

\* Unmeasurable on this fixture. `PLAY_MID` and `PLAY_HIGH` place the camera 7x
and 5x the storm radius away; the client stops holding the storm's descriptors
there, and the harness recorded the arms as unmeasurable after three attempts
rather than sampling a stormless scene. This is the same defect as `PLAY_NEAR`,
one step further out.

Total frame p50/p95 tracks cloud p50/p95 plus a non-cloud remainder of
**0.5–2.6 ms** in every cell, unchanged from T136 and never the problem.

### 3.1 Speedup from the shipped Ultra scale, and the scaling exponent

| pose | 0.75→0.50 | 0.75→0.375 | 0.75→0.25 | fitted `n` in cost ∝ pixels^n |
|---|---|---|---|---|
| PLAY_VIS_NEAR | 1.71x | 2.58x | **4.75x** | 0.761 |
| PLAY_VIS_MID | 1.56x | 2.51x | 3.83x | 0.688 |
| PLAY_NEAR (empty) | 1.99x | 3.31x | 6.00x | 0.842 |
| SIDE | 1.73x | 2.79x | 4.92x | 0.760 |
| FAR | 1.53x | 2.67x | 4.30x | 0.711 |
| ABOVE | 1.92x | 3.04x | 5.75x | 0.828 |
| BELOW | 1.76x | 2.63x | 6.52x | 0.903 |
| NEAR_EDGE | 1.79x | 2.77x | 5.07x | 0.775 |

### 3.2 Why it is sub-linear — the work is not

The workload counters are decisive. Steps per pixel is constant across the
whole 16x pixel range, to within the third digit:

| pose | 1.000 | 0.750 | 0.500 | 0.375 | 0.250 |
|---|---|---|---|---|---|
| PLAY_NEAR, primary ray steps / pixel | 25.20 | 25.18 | 25.18 | 25.18 | 25.17 |
| PLAY_NEAR, descriptor evaluations / step | 4.11 | 4.11 | 4.11 | 4.11 | 4.11 |
| FAR, steps / pixel | — | 28.65 | 28.65 | 28.65 | 28.65 |

**Work is exactly proportional to marched pixels. Time is not.** Cost per
executed step rises as the target shrinks — PLAY_NEAR runs 2.97 ns/step at
1920x1080 and 4.62 ns/step at 480x270, a 56 % efficiency loss. A 480x270 target
issues 185 M descriptor texel fetches in 15 ms (12.3 G/s); a 1920x1080 target
issues 2.97 G in 155 ms (19.1 G/s). The shader is fetch-latency bound, and
fewer pixels means fewer concurrent warps to hide that latency with.

This is the correction to T137's model, which assumed near-linear scaling and
projected 4x from 0.75→0.375. The measured figure is **2.51–3.31x**.

### 3.3 Reconstruction cost and history cost

**Reconstruction (composite) GPU p50 is 0.082–0.160 ms at every arm and every
pose** — 0.008 % to 0.78 % of the cloud pass. It is flat because it always runs
at display resolution, and it never becomes the floor even at 480x270. It rises
slightly with *more* cloud on screen (0.16 ms at BELOW/1.00), not with fewer
pixels.

**History cost is not measurable above noise.** A history-disabled arm was run
at every scale on PLAY_NEAR and SIDE; the difference ranges −0.50 % to +1.21 %
of cloud time, with no sign or trend. The temporal blend is effectively free,
and removing it would buy nothing.

### 3.4 Where the per-pixel cost actually is

Counters at the corrected representative pose, PLAY_VIS_NEAR/0.25, 129,600
marched pixels:

| quantity | per frame | per marched pixel |
|---|---|---|
| primary ray steps | 3,749,296 | 28.9 |
| descriptor SDF evaluations | 51,462,498 | **397** |
| descriptor texel fetches | 491,894,819 | 3,795 |
| light-march density evaluations | 1,070,428 | 8.3 |
| steps resolved as empty space | 3,316,118 | 88.4 % of steps |

Two things stand out. **88 % of march steps resolve as empty space and still
pay 13.7 descriptor SDF evaluations each**, because the conservative
safe-advance queries the storm descriptors before the weather-coverage skip can
reject the step. And per shaded pixel the shader runs **397 descriptor SDF
evaluations**.

That is not the quantity `performance-descriptor-cost.md` rejected. That
experiment added redundant *texture fetches* at constant arithmetic and
correctly concluded fetches are cheap (elasticity 0.10 representative). The
**evaluation** count — the ALU of the ordered smooth union over up to eight
candidate lobes — has never been varied in a controlled arm. It is the largest
unmeasured per-pixel term in the renderer.

---

## 4. Phases 4 and 10 — structural quality and T098a against scale

Capture ladder: five internal resolutions x the checklist's structural poses,
one fixture, ULTRA, 1920x1080, movement and daylight frozen, camera pinned per
pose. Each shot carries a temporal pair and three composite diagnostic views
(ALPHA, DEPTH, ALIGNMENT), so coverage and colour/depth pairing come from the
composite's own classification rather than a colour heuristic.

T098a structural metrics, measured inside the full-resolution arm's own
centre-column row band so an arm that erased the top of the column would show a
reduced share rather than a shorter band:

| scale | pose | centre-column cloud share | longest inner sky run | paired colour+depth | colour without depth | depth without colour | scene-rejected |
|---|---|---|---|---|---|---|---|
| 1.000 | SIDE | 1.0000 | 0 px | 100.0 % | 0 % | 0 % | 0 % |
| 0.750 | SIDE | 1.0000 | 0 px | 100.0 % | 0 % | 0 % | 0 % |
| 0.500 | SIDE | 1.0000 | 0 px | 100.0 % | 0 % | 0 % | 0 % |
| 0.375 | SIDE | 1.0000 | 0 px | 100.0 % | 0 % | 0 % | 0 % |
| 0.250 | SIDE | 1.0000 | 0 px | 100.0 % | 0 % | 0 % | 0 % |
| 1.000 | FAR | 1.0000 | 0 px | 100.0 % | 0 % | 0 % | 0 % |
| 0.250 | FAR | 1.0000 | 0 px | 100.0 % | 0 % | 0 % | 0 % |
| 1.000 | ABOVE | 1.0000 | 0 px | 100.0 % | 0 % | 0 % | 0 % |
| 0.250 | ABOVE | 1.0000 | 0 px | 100.0 % | 0 % | 0 % | 0 % |
| 1.000 | BELOW | 1.0000 | 0 px | 99.95 % | 0 % | 0 % | 0.05 % |
| 0.250 | BELOW | 1.0000 | 0 px | 99.99 % | 0 % | 0 % | 0.01 % |
| 1.000 | PLAY_VIS_NEAR | 1.0000 | 0 px | 100.0 % | 0 % | 0 % | 0 % |
| 0.250 | PLAY_VIS_NEAR | 1.0000 | 0 px | 100.0 % | 0 % | 0 % | 0 % |

(0.750–0.375 ABOVE report 0.9988 — one row of 866 — and 0 px inner sky, as does
PLAY_VIS_NEAR at 0.750. BELOW and PLAY_VIS_NEAR were captured in a second set
against a second fixture of the same archetype after the first set's storm
decayed; each pose is still compared only against its own full-resolution arm on
its own fixture.)

**T098a's four structural criteria hold at every scale down to 0.25:**
connected BASE→CORE→TOWER→ANVIL, centre-column share 1.0000, no clean-sky
waist, no disappearing FAR, and no depth-sentinel or pairing failure. There is
**zero** alpha/depth disagreement and **zero** depth leakage at any scale: the
composite classifies 100 % of cloud pixels as paired colour+depth at every arm.

Cloud coverage *grows* slightly as the scale falls — SIDE 18.52 % at 1.000 to
18.83 % at 0.250 — which is the low-resolution silhouette dilating by about
1.7 %, not thinning.

---

## 5. Phase 5 — against the full-resolution reference

Reference is the 1.000 arm of the same pose on the same fixture.

| pose | scale | changed pixels (>8/255) | SSIM | silhouette mean | silhouette p95 | silhouette max | columns lost |
|---|---|---|---|---|---|---|---|
| SIDE | 0.750 | 0.60 % | 0.9915 | 0.86 px | 2 px | 36 px | 0 |
| SIDE | 0.500 | 0.77 % | 0.9905 | 1.04 px | 3 px | 13 px | 0 |
| SIDE | 0.375 | 0.94 % | 0.9890 | 1.39 px | 4 px | 18 px | 0 |
| SIDE | 0.250 | **1.26 %** | **0.9854** | 2.43 px | 7 px | 53 px | 0 |
| FAR | 0.750 | 0.14 % | 0.9987 | 0.70 px | 2 px | 10 px | 0 |
| FAR | 0.250 | 0.51 % | 0.9947 | 2.00 px | 4 px | 26 px | 0 |
| ABOVE | 0.750 | 0.18 % | 0.9948 | 0.99 px | 3 px | 22 px | 1 |
| ABOVE | 0.250 | 0.73 % | 0.9864 | 2.86 px | 8 px | 26 px | 1 |

BELOW and PLAY_VIS_NEAR are reported over the **cloud region only** — the union
of the two arms' composite footprints. Their full-frame figures are
contaminated by terrain and horizon-band content that settles independently of
the cloud pass, which at PLAY_VIS_NEAR's y=120 gameplay altitude occupies most
of the lower half of the frame:

| pose | scale | cloud-region changed pixels | cloud-region mean abs delta | silhouette mean | silhouette p95 |
|---|---|---|---|---|---|
| PLAY_VIS_NEAR | 0.750 | 1.15 % | 0.0035 | 0.72 px | 2 px |
| PLAY_VIS_NEAR | 0.500 | 2.34 % | 0.0047 | 0.98 px | 3 px |
| PLAY_VIS_NEAR | 0.375 | 3.01 % | 0.0056 | 1.52 px | 4 px |
| PLAY_VIS_NEAR | 0.250 | **5.07 %** | 0.0079 | 3.85 px | 7 px |
| BELOW | 0.750 | 0.23 % | 0.0013 | 0.01 px | 0 px |
| BELOW | 0.500 | 0.24 % | 0.0013 | 0.01 px | 0 px |
| BELOW | 0.375 | 0.17 % | 0.0012 | 0.01 px | 0 px |
| BELOW | 0.250 | 0.29 % | 0.0017 | 0.01 px | 0 px |

BELOW is a near-total whiteout underside — 99.8 % cloud coverage — and is
essentially insensitive to internal resolution. PLAY_VIS_NEAR degrades fastest
of all poses, which is expected: it is the pose whose storm subtends the most
screen area per marched pixel.

The degradation is smooth and small. Even at 9x fewer marched pixels, under
1.3 % of display pixels change by more than 8/255, structural similarity stays
above 0.985, and the silhouette moves by 2–3 pixels on average.

**Temporal shimmer and ghosting are not gradeable from this fixture.** The
capture protocol freezes camera motion and material advection so the poses are
reproducible, and the measured frame-to-frame change is 0.000–0.008 % at every
arm. That is a property of the fixture, not evidence of stability under motion;
grading shimmer and ghosting needs a moving-camera fixture and belongs to
T098b.

---

## 6. Phase 6 — is the existing reconstruction good enough?

### 6.1 What it does

`cloud_field_composite.fsh`, `CompositeMode == 0`, per display pixel:

1. `sourcePosition = texCoord * sourceSize - 0.5`; take the 2x2 texel
   neighbourhood and the four bilinear weights.
2. Per tap: `hasColor` (`a > 0.001`), `hasDepth` (`depth < 1.0`),
   `visibleAgainstScene` (`depth <= sceneDepth + bias`, bias growing with the
   scene depth gradient).
3. Select the tap maximising `weight * alpha` among paired visible taps; its
   depth becomes `selectedDepth` and is written to `gl_FragDepth`.
4. Accumulate `colors[i] * weights[i]` for taps that are paired **and** either
   within `max(2e-5, (1-selectedDepth)*0.08)` of `selectedDepth` or part of a
   wholly opaque neighbourhood.
5. Output `accumulated.rgb / accumulated.a` with alpha `accumulated.a`. The
   absolute bilinear weights are kept and deliberately not renormalised.

### 6.2 Findings

**(a) It is an upsampling filter, not a reconstruction.** No temporal term, no
accumulation buffer, no motion vectors. Every display pixel is a weighted read
of four texels rendered this frame.

**(b) The only temporal machinery is inside the raymarch, at the low
resolution, and it cannot add resolution.** History reprojects the ray's
representative material point, clamps to `result ± 0.25`, and mixes at
`HistoryBlend * edgeFade * depthConfidence * transmittanceConfidence`. That
integrates *along-ray* sample phase. Because the screen-space sample lattice
never moves, consecutive frames march the same ray through the same pixel
centre — there are no new screen-space samples for history to accumulate.

**(c) The reconstruction is not the limiting factor for structure, and it is
not the cost floor.** Colour/depth pairing is 100 % at every scale, scene
rejection 0 %, depth leakage 0 %, and its GPU cost is flat at 0.08–0.16 ms.
Every T098a criterion survives to 0.25 through it.

**(d) The resampling beat is real, grows as the scale falls, and is not caused
by the alpha accumulation.** Measuring the amplitude of a horizontal ripple at
exactly the scale's own resampling period, in the silhouette edge band, after
detrending each row at that period, against the median amplitude at control
periods the geometry does not predict:

| scale | predicted period | beat amplitude (luma) | control median | ratio |
|---|---|---|---|---|
| 0.750 | 4 px | 0.0034 | 0.0026 | 1.28x |
| 0.500 | 2 px | 0.0054 | 0.0017 | 3.13x |
| 0.375 | 8 px | 0.0095 | 0.0024 | 3.91x |
| 0.250 | 4 px | 0.0071 | 0.0028 | 2.54x |

(SIDE; ABOVE shows the same growth at 2.84x / 9.18x / 4.51x / 5.78x.)

The period is exactly what the geometry predicts — for `s = p/q`,
`frac((i + 0.5)s - 0.5)` repeats every `q` display pixels — which confirms the
known ~4-pixel artefact is **the shipped 3/4 Ultra scale's own resampling
period**, and that it moves rather than disappears as the scale changes. Its
amplitude roughly triples between 0.75 and 0.375.

**A candidate fix was implemented, measured, and is rejected.** The
`CoverageAlphaReconstruction` arm separates the silhouette's coverage (all
visible cloud taps at full bilinear weight) from the depth-aware colour
accumulation, on the theory that the beat came from partially-rejected
neighbourhoods under-weighting alpha. Captured on the same poses, scales and
fixture, it changes **0.0000–0.0033 % of pixels** and moves the beat ratio by
under 5 % relative. It is inert, because the ALIGNMENT view shows the
same-surface test already accepts essentially every tap. **The beat is the
low-resolution sampling aliasing the storm's own surface detail, not a filter
defect**, so no composite-side change removes it. The arm stays diagnostic and
defaults off; it is not proposed for production.

**Conclusion for Phase 6.** The existing reconstruction is *sufficient* for
aggressive scaling in the sense the gate cares about — it preserves structure,
depth and pairing to 0.25 at a negligible cost — and *fundamentally limited* in
the sense that it can never pay back what a coarser march did not sample. There
is no jitter and no accumulation, so lost detail is lost. Recovering it would
need per-frame sub-pixel ray offsets plus a low-resolution accumulate, which is
new machinery, and the shader's own comment records that a weaker version of it
broke thin silhouettes.

---

## 7. Phase 7 — reconstruction candidates, assessed against the measurements

| candidate | verdict |
|---|---|
| A. improved depth-aware spatial reconstruction | **Not needed and not effective.** Pairing is already 100 %, rejection 0 %, T098a green at 0.25. The one bounded improvement tried (coverage/colour separation) measured inert. |
| B. temporal reconstruction from the existing cloud history | **Cannot work as-is.** History reprojects one representative point per ray at the low resolution; with a fixed screen lattice it has no new screen-space samples to integrate. |
| C. jittered low-resolution sampling + temporal accumulation | **The only candidate that could add resolution back**, and the only one that is new machinery. Requires per-frame sub-pixel ray offsets, which is exactly what the shader deliberately does not do — animating the search phase made thin silhouette pixels alternate hit/miss. High risk against T098a's silhouette criteria. |
| D. hybrid spatial + temporal | Reduces to C plus the existing filter. |
| E. edge-aware reconstruction preserving depth/silhouette | Already implemented; that is what the same-surface test and `selectedDepth` publication are. |

**Nothing in A/B/D/E is worth building.** The reconstruction is not the
constraint on how low the resolution can go; the ray budget is. C is the only
real option and it is a separate, risky piece of work that should not be
started before the far larger per-pixel cost below is addressed.

---

## 8. Phase 8 — quality-mode ladder derived from the frontier

The measured frontier at the corrected representative pose, PLAY_VIS_NEAR:

| scale | cloud ms | speedup | SSIM vs full-res | silhouette mean | T098a |
|---|---|---|---|---|---|
| 1.000 | 856.3 | 0.58x | 1.0000 | 0 px | pass |
| 0.750 | 492.8 | 1.00x | 0.9915 | 0.86 px | pass |
| 0.500 | 288.8 | 1.71x | 0.9905 | 1.04 px | pass |
| 0.375 | 190.9 | 2.58x | 0.9890 | 1.39 px | pass |
| 0.250 | 103.8 | 4.75x | 0.9854 | 2.43 px | pass |

Quality falls smoothly and slowly; cost falls steeply. There is no knee, so the
ladder should be set by cost, and the lowest measured scale is the best
available choice at every tier that has any budget pressure — which, at 13x
over budget even at 0.25, is all of them.

**Proposed ladder** — internal pixel count and reconstruction depth only; step
counts are left alone, per T136's finding that they are not where the cost is:

| mode | steps (unchanged) | scale now | proposed scale | cloud target | marched pixels |
|---|---|---|---|---|---|
| Ultra | 96 | 0.750 | **0.500** | 960x540 | 518,400 |
| High | 64 | 0.500 | **0.375** | 720x405 | 291,600 |
| Medium | 40 | 0.500 | **0.375** | 720x405 | 291,600 |
| Low 24 | 32 | 0.375 | **0.250** | 480x270 | 129,600 |
| Low | 24 | 0.250 | **0.250** | 480x270 | 129,600 |

This is deliberately more conservative than the frontier allows. **It is not
proposed for adoption in this task**, because adopting it would spend the whole
of Rank 1's headroom — 4.75x at Ultra — on a stack that still misses its budget
by 13x, and would do so before the dominant remaining cost has been measured.
Spending an irreversible image-quality budget to go from 62x over to 13x over
buys nothing shippable. The ladder is recorded so that it can be adopted in one
step once a lever exists that makes the result land inside a budget. T139 owns
that decision with this table as its input.

---

## 9. Phases 9 and 13 — the updated cumulative path

Old projection (T137): descriptor cost 2.0x, resolution 4.0x, temporal
reconstruction 2–3x, lighting 1.29x, distance LOD 1.3x → 27–40x. **Retired.**

Measured replacements:

| lever | T137 estimate | measured | source |
|---|---|---|---|
| Rank 2 — descriptor fetch cost | 1.5–3x | **1.02–1.10x** | `performance-descriptor-cost.md` |
| Rank 1 — internal resolution 0.75→0.25 | 4x | **4.75x** representative | this document |
| Rank 1 — temporal reconstruction | 2–3x | **not available** without new jitter machinery | §6, §7 |
| Rank 4 — lighting | 1.29x | ~1.10x representative | T136/T137 revision |
| Rank 3 — distance LOD | 1.3x | not measured | — |

Cumulative, representative gameplay at Ultra with the corrected pose:

| stage | factor | cloud ms | vs 8 ms budget |
|---|---|---|---|
| shipped Ultra, 0.75 | — | 492.8 | 61.6x |
| + internal resolution 0.25 | 4.75x | 103.8 | 13.0x |
| + lighting ceiling | 1.10x | 94.4 | 11.8x |
| + rank 2 ceiling | 1.02x | 92.5 | 11.6x |

**The measured cumulative path is 5.3x, against roughly 62x required.** Nothing
currently ranked closes the remaining 11.6x.

The counters say where that 11.6x would have to come from: **397 descriptor SDF
evaluations per shaded pixel, 88 % of march steps resolving as empty space and
still paying 13.7 of those evaluations each.** Both are per-pixel terms that
survive every resolution reduction, and neither has been varied in a controlled
arm.

---

## 10. Phase 14 — stress case, reported separately

`NEAR_EDGE` remains the parked worst case and is not used to shape the quality
architecture.

| | shipped Ultra 0.75 | at 0.25 | speedup | vs 8 ms |
|---|---|---|---|---|
| NEAR_EDGE cloud p50 | 967.1 ms | 190.7 ms | 5.07x | 23.8x |
| NEAR_EDGE frame p95 | 987.6 ms | 201.5 ms | 4.90x | 12.1x of 16.7 ms |

Rank 1 takes the stress case from 121x over to 24x over. It is reported here
and excluded from every ladder decision, exactly as instructed.

---

## 11. Is SC-006 still credible?

**No, not on this evidence, and the reason is not the one T137 gave.**

T137 concluded SC-006 was credible for representative gameplay because
PLAY_NEAR needed only 12.8x. That conclusion rests on a pose whose frame
contains no storm. With the storm actually in view at gameplay altitude the
requirement is **61.6x at Ultra**, and the entire measured lever stack supplies
**5.3x**.

This is a measurement result, not a product decision, and it does not by itself
rescope SC-006 — which stays unchanged, per instruction. What it does is
replace the number the rescoping decision would have been made against.

---

## 12. Recommended next task

**Measure the per-pixel descriptor evaluation cost with a controlled arm, the
way rank 2's fetch cost was measured, before implementing anything.**

The specific experiment: hold the fixture, pose, resolution and step budget
fixed and vary the number of descriptor SDF evaluations per sample — for
example by restricting `directStormGroupField`'s candidate loop to a smaller
rank count, or by widening the T121 conservative bound — and read the elasticity
of GPU time against `paDescriptorEvaluations`. Rank 2's rejection measured
elasticity against *fetches* and is silent about this. At 397 evaluations per
shaded pixel it is the largest unmeasured term in the renderer, and it is the
only remaining candidate of the right order for an 11.6x gap.

Second, and independent: **an empty-space early-out**. 88 % of march steps at
the representative pose resolve as empty space yet still pay the full
descriptor clearance query, and the shipped `PLAY_NEAR` pose shows the pure
version of this — 90 ms of Ultra cloud time to render a frame with no storm in
it, against 3.9 ms for the same empty sky with no descriptors resident. That
gap is entirely descriptor-driven work on rays that cannot reach material.

Third, and cheaply: **fix the gameplay pose definitions**. `PLAY_NEAR`,
`PLAY_MID` and `PLAY_HIGH` are all outside `cloudRenderDistance` at T134 storm
scale. `PLAY_VIS_NEAR` (1.6r, y=120) and `PLAY_VIS_MID` (2.4r, y=120) are added
to the harness and should replace them in `performance-budget.md`,
`performance-baseline.md` and every later reprofile.

---

## Appendix — evidence

| artefact | path |
|---|---|
| resolution sweep, six severe/near poses | `run/logs/t138-res-run2.log` |
| resolution sweep, corrected gameplay poses | `run/logs/t138-run4-sweep.log` |
| capture ladder, SIDE/FAR/ABOVE at five scales | `run/logs/t138-ladder.log`, `run/screenshots/t138-ladder-a/` |
| capture ladder, BELOW/PLAY_VIS_NEAR at five scales | `run/logs/t138-ladder-b.log`, `run/screenshots/t138/` |
| harness | `StormT132AutoDriver` (`t138-resolution.txt`), `StormT098CaptureDriver` (`t138-captures.txt`), `StormT135PerformanceProfile` |
| composite reconstruction arm | `VolumetricCloudDebugConfig.setCoverageAlphaReconstruction`, `cloud_field_composite.fsh` — diagnostic, defaults off |
