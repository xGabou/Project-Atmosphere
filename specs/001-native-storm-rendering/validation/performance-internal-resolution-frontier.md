# Rank 1 — internal resolution: the measured frontier, and the shipped ladder

**Feature**: `001-native-storm-rendering`
**Task**: T146 [PERFORMANCE] — Rank 1 production increment
**Follows**: `performance-internal-resolution.md` (T138 exploration),
`performance-rain-locality.md` (T145), `performance-pose-definitions.md` (T142)
**Date**: 2026-09-02
**Hardware**: NVIDIA GeForce RTX 4070 Laptop GPU, OpenGL 4.6
**Decision**: **banked.** The five-mode ladder is now a resolution ladder.
**Image-changing**, as authorized. T098b regrade is owed.

---

## 0. The result

| | |
|---|---|
| representative Ultra, old 0.750 → new 0.250 | **497.3 → 113.2 ms, 4.39x** |
| stress NEAR_EDGE, old → new | 902.1 → 179.4 ms, **5.03x** |
| best measured point on the frontier (0.125) | **8.98x** representative |
| T098a at every scale down to 0.125 | **pass** — share 1.0000, inner sky run 0 px |
| reconstruction cost | 0.085–0.121 ms, **flat**, never a floor |
| representative gap vs the 8 ms cloud budget | **63.2x → 14.2x** |

Rank 1 delivers the order-of-magnitude-class movement it promised. It does not
close the gap.

---

## 1. Phase 1 — the pipeline that was measured

Unchanged from `performance-internal-resolution.md` §1 and re-confirmed here from
live `cloudTarget=WxH` records, not configuration labels.

- **Display / main target** 1920x1080; **cloud target** `ceil(main * s)`.
- **History** is the other half of a two-element ping-pong of the *same*
  RGBA16F colour + depth target pair, so it is always at the cloud target's
  resolution and a scale change rebuilds both and drops history.
- **Depth target** is the cloud target's own attachment, `GL_NEAREST`.
- **Reconstruction** runs at full display resolution always:
  `cloud_field_composite.fsh`, a 2x2 `texelFetch` bilinear with per-tap
  colour/depth pairing, scene-depth rejection and a same-surface test, which
  republishes a selected low-resolution depth as `gl_FragDepth`.
- **Jitter**: `searchBlue` is a **static** screen-space blue-noise phase,
  deliberately not animated; only the along-ray integration phase advances with
  `FrameIndex`. **There is still no sub-pixel screen-space jitter**, which is
  the fact that decides §5 and §6.
- **History weighting**: `HistoryBlend * edgeFade * depthConfidence *
  transmittanceConfidence`, with history clamped to `result ± 0.25`.

**One defect found and fixed during Phase 2.** `VolumetricCloudDebugConfig.setFixedResolutionScale`
clamped to a **0.25 floor** while the renderer's own clamp is 0.10. The first
sweep's 0.1875 and 0.125 arms therefore reported their labels and silently
rendered a 480x270 image — the timings were real but described the wrong
configuration. The floor is now 0.10, matching the renderer. A diagnostic floor
tighter than the renderer's own makes the frontier it exists to explore
unreachable.

---

## 2. Phases 2 and 3 — the frontier

Seven scales, seven poses, one fixture held across every arm, ULTRA step budget
throughout, T145 in production, nothing else varied.

| scale | cloud target | marched pixels | vs display |
|---|---|---|---|
| 1.000 | 1920x1080 | 2,073,600 | 100 % |
| 0.750 | 1440x810 | 1,166,400 | 56.25 % |
| 0.500 | 960x540 | 518,400 | 25.00 % |
| 0.375 | 720x405 | 291,600 | 14.06 % |
| 0.250 | 480x270 | 129,600 | 6.25 % |
| 0.1875 | 360x203 | 73,080 | 3.52 % |
| 0.125 | 240x135 | 32,400 | 1.56 % |

### 2.1 Cloud GPU p50 / p95, milliseconds

| pose | 1.000 | 0.750 | 0.500 | 0.375 | 0.250 | 0.1875 | 0.125 |
|---|---|---|---|---|---|---|---|
| PLAY_VIS_NEAR | 855.0 / 882.9 | 497.3 / 516.1 | 291.9 / 310.0 | 199.1 / 216.6 | **113.2 / 124.7** | 79.0 / 90.8 | 55.4 / 63.2 |
| PLAY_VIS_MID | 431.4 / 449.0 | 258.6 / 277.0 | 169.9 / 187.9 | 105.2 / 115.5 | 73.6 / 81.1 | 53.9 / 58.6 | 45.4 / 45.5 |
| SIDE | 858.9 / 892.5 | 502.7 / 526.7 | 301.7 / 318.8 | 194.6 / 208.9 | 113.5 / 127.0 | 79.6 / 84.2 | 52.1 / 53.7 |
| FAR | 348.6 / 369.5 | 213.9 / 227.5 | 149.3 / 164.9 | 90.8 / 108.0 | 59.5 / 66.2 | 43.5 / 44.6 | 29.6 / 37.5 |
| ABOVE | 984.5 / 997.9 | 576.1 / 585.4 | 302.9 / 312.9 | 191.1 / 197.8 | 102.8 / 107.4 | 65.0 / 69.4 | 38.9 / 43.7 |
| NEAR_EDGE (stress) | 1467.2 / 1504.4 | 902.1 / 922.7 | 499.8 / 509.1 | 327.0 / 344.3 | 179.4 / 189.7 | 122.3 / 133.5 | 78.5 / 81.4 |

`BELOW` is absent: the 1.000 arm renders over 1.6 s per frame there and tripped
this machine's display-driver timeout, taking the run down. That is a measurement
limitation of the native arm, not of the ladder — every shipped scale is well
inside it.

### 2.2 Speedup and the scaling exponent

| pose | 0.750 ms | 0.500 | 0.375 | 0.250 | 0.1875 | 0.125 | exponent n |
|---|---|---|---|---|---|---|---|
| PLAY_VIS_NEAR | 497.3 | 1.70x | 2.50x | **4.39x** | 6.30x | **8.98x** | 0.613 |
| PLAY_VIS_MID | 258.6 | 1.52x | 2.46x | 3.51x | 4.80x | 5.70x | 0.486 |
| SIDE | 502.7 | 1.67x | 2.58x | 4.43x | 6.32x | 9.65x | 0.633 |
| FAR | 213.9 | 1.43x | 2.35x | 3.59x | 4.91x | 7.22x | 0.552 |
| ABOVE | 576.1 | 1.90x | 3.01x | 5.60x | 8.86x | 14.83x | 0.752 |
| NEAR_EDGE | 902.1 | 1.81x | 2.76x | 5.03x | 7.38x | 11.49x | 0.681 |

**Cost scales as pixels^0.49–0.75, and the exponent degrades as the target
shrinks.** T138 measured 0.69–0.90 over the 1.00–0.25 range; extending to 0.125
brings it down to 0.49–0.75. The work is still exactly linear in pixels — the
counters showed that in T138 — but a smaller target has fewer waves in flight to
hide the shader's fetch latency, and that loss compounds. **Halving the linear
scale again below 0.125 would return roughly 1.5x, not 4x.** The lever is
approaching its own floor.

### 2.3 Reconstruction cost

**0.085–0.121 ms at every scale and pose**, 0.01 %–0.29 % of cloud time. It is
flat because it always runs at display resolution, and it is *never* the floor —
not even when expanding a 240x135 source over 1920x1080.

---

## 3. Phase 4 — the quality frontier

Five poses x six scales x seven grabs, one fixture, no fixture loss. Compared
against the **shipped 0.750**, which is what a player sees today.

### 3.1 T098a, the hard gate

| scale | centre-column share | longest inner sky run | scene-rejected | silhouette columns lost | FAR coverage |
|---|---|---|---|---|---|
| 0.750 | 1.0000 | 0 px | 0.000 % | 0 | 3.12 % |
| 0.500 | 1.0000 | 0 px | 0.000 % | 0 | 3.16 % |
| 0.375 | 1.0000 \* | 0 px | 0.000 % | 0–1 | 3.18 % |
| 0.250 | 1.0000 | 0 px | 0.000 % | 0 | 3.24 % |
| 0.1875 | 1.0000 | 0 px | 0.000 % | 0 | 3.27 % |
| **0.125** | **1.0000** | **0 px** | 0.000 % | 0–1 | 3.39 % |

\* SIDE reports 0.9986 at 0.375 — one row of ~800.

**T098a passes at every scale down to 0.125.** The connected column survives, no
clean-sky waist appears, and **FAR does not disappear** — its coverage *grows*
slightly as the scale falls, the low-resolution silhouette dilating rather than
thinning. **The structural gate is not the binding constraint on this frontier.**

### 3.2 What actually degrades

| scale | SSIM vs 0.750 (SIDE / ABOVE / FAR) | silhouette mean px (SIDE / ABOVE) | cloud-region pixels changed >8/255 (SIDE) |
|---|---|---|---|
| 0.500 | 0.998 / 0.995 | 0.97 / 1.13 | 1.23 % |
| 0.375 | 0.996 / 0.993 | 1.76 / 1.94 | 2.34 % |
| 0.250 | 0.993 / 0.988 | 2.40 / 2.98 | 4.46 % |
| 0.1875 | 0.990 / 0.982 | 3.55 / 4.32 | 6.84 % |
| 0.125 | 0.981 / 0.960 | 5.92 / 7.38 | 10.58 % |

The binding constraint is **silhouette softening**, and it is smooth: no knee,
no collapse. Even at 1.56 % of display pixels marched, structural similarity is
0.96–0.98.

**Temporal shimmer is not gradeable from this fixture.** The capture protocol
freezes camera motion and material advection so poses are reproducible;
frame-to-frame change is 0.000 % at the static poses and 0.1–0.6 % at
PLAY_VIS_NEAR, which is terrain and horizon settling rather than cloud shimmer.
Shimmer and ghosting under motion need a moving-camera fixture and belong to
T098b.

---

## 4. Phases 5 and 6 — where the reconstruction becomes the limit

**Both classes of failure are present, and they separate cleanly.**

- **Class A — the source lacks the information.** At 0.125 the 240x135 target
  carries 1.56 % of the display's samples. Detail below the low-resolution
  Nyquist limit is simply not sampled. No reconstruction recovers it.
- **Class B — the source has it, the reconstruction cannot use it.** The
  composite is a 2x2 bilinear over the *current frame only*. At 0.125 one source
  texel is stretched across 8x8 display pixels, and the filter has four taps to
  do it with.

**The first scale at which the reconstructor becomes the limiting factor is
0.250.** Above it, silhouette displacement tracks the source grid closely
(0.97 px at 0.500, 1.76 at 0.375 — roughly one source texel). From 0.250 down it
grows faster than the source grid does (2.40, 3.55, 5.92 px against source texel
sizes of 4, 5.3 and 8 display pixels), which is the signature of the filter
losing the silhouette rather than the sampler losing detail.

**The reason no temporal recovery is available is structural, and unchanged from
T138.** There is no sub-pixel screen-space jitter: consecutive frames march the
*same* ray through the *same* low-resolution pixel centre. The history blend
integrates along-ray sample phase, not screen-space position, so it has no new
information to accumulate. The pipeline has an accumulation *mechanism* and
nothing to accumulate.

---

## 5. Phase 7 — candidates, and why none was built in this task

| candidate | assessment |
|---|---|
| A. improved spatial depth-aware upscale | The tap budget is the limit, not the policy. T138 already implemented and measured one bounded improvement (coverage/colour separation) at **≤0.0033 % of pixels changed** — inert. More taps would help at 0.125 but cannot invent samples. |
| B/C. jittered low-res sampling + temporal accumulation | **Would antialias each low-resolution texel, not add resolution.** Accumulating jittered samples into a low-resolution history makes each texel a better average of its own footprint; the output is still 240x135 before upscaling. |
| D. hybrid | Reduces to B plus the existing filter. |
| E. per-mode reconstruction quality | Available and cheap, but it varies a filter that is not the limit above 0.250. |
| **F. interleaved / checkerboard** | **The only class that adds resolution.** March 240x135 per frame, cycle the sample grid through a 2x2 phase pattern, accumulate into a 480x270 resolve target with reprojection. That yields 0.250-class spatial information at 0.125-class march cost — worth a further **2.0x** at equal quality — a *ceiling inferred from* two measured frontier points (113.2 ms at 0.250 versus 55.4 ms at 0.125), not a measured result of an implementation. No interleaving code exists. |

**F is the right next piece of work and was deliberately not started here.** It
needs a new resolve target pair, per-phase ray offsets, reprojection and
disocclusion handling at the resolve resolution — a new subsystem, not a tuning
change — and its failure mode is exactly the one the shader's own comment
records: animating the sample lattice made thin silhouette pixels alternate
between hit and miss. Shipping the measured 4.39x now, and building F against a
stable baseline, is the lower-risk order.

---

## 6. Phases 9 and 12 — the shipped ladder

Every scale below is a measured point on the frontier; none is interpolated.
Step counts, lighting, detail quality and weather-map size are **unchanged** —
T136 showed the step budget has weak leverage and this task does not spend it.

| mode | steps | scale before | **scale now** | cloud target | marched pixels |
|---|---|---|---|---|---|
| Low | 24 | 0.250 | **0.125** | 240x135 | 32,400 |
| Low 24 | 32 | 0.375 | **0.125** | 240x135 | 32,400 |
| Medium | 40 | 0.500 | **0.125** | 240x135 | 32,400 |
| High | 64 | 0.500 | **0.1875** | 360x203 | 73,080 |
| **Ultra** | 96 | 0.750 | **0.250** | 480x270 | 129,600 |

Confirmed live at the shipped defaults, SIDE pose, cloud GPU p50:

| mode | cloud target | p50 / p95 |
|---|---|---|
| Low | 240x135 | 20.6 / 22.9 |
| Low 24 | 240x135 | 31.4 / 44.9 |
| Medium | 240x135 | 37.0 / 54.4 |
| High | 360x203 | 67.1 / 76.2 |
| Ultra | 480x270 | 107.8 / 119.4 |

**Three modes share 240x135 and still separate 1.8x** — 20.6, 31.4, 37.0 ms —
on the step, lighting and detail differences they already had. That answers the
obvious objection to a compressed resolution ladder: the rungs remain distinct.
The shipped ladder mirrors the old one's structure, which already had Medium and
High sharing 0.500.

**Ultra is 0.250, not 0.1875.** 0.1875 measures 79.0 ms — 6.30x, and inside the
task's ≤100 ms marker where 0.250's 113.2 ms is 13 % outside it. 0.250 was
chosen because it keeps a 4x pixel spread between Ultra and Low, and because the
quality step between them is real: SSIM 0.988 → 0.982 and silhouette 2.98 →
4.32 px at ABOVE. **Moving Ultra to 0.1875 is a one-line change and is the right
call if 113 ms is judged unacceptable**; both points are measured and either can
be adopted without new work.

`denseCameraResolution`'s `min(profile, 0.50)` override is now inert — no mode
exceeds 0.25. It is retained as the hook a later adaptive policy would reuse.

---

## 7. Phases 8, 11 and 14 — the frontier, and where this leaves the budget

| scale | representative ms | speedup | SSIM (ABOVE) | silhouette mean | T098a | verdict |
|---|---|---|---|---|---|---|
| 0.750 | 497.3 | 1.00x | 1.000 | 0 px | pass | today |
| 0.500 | 291.9 | 1.70x | 0.995 | 1.13 px | pass | too little |
| 0.375 | 199.1 | 2.50x | 0.993 | 1.94 px | pass | too little |
| **0.250** | **113.2** | **4.39x** | 0.988 | 2.98 px | pass | **shipped Ultra** |
| 0.1875 | 79.0 | 6.30x | 0.982 | 4.32 px | pass | **shipped High** |
| 0.125 | 55.4 | 8.98x | 0.960 | 7.38 px | pass | **shipped Medium and below** |

Representative and stress, reported separately as required:

| | old Ultra 0.750 | new Ultra 0.250 | speedup | new total frame p95 | vs SC-006 16.7 ms |
|---|---|---|---|---|---|
| **REPRESENTATIVE** PLAY_VIS_NEAR | 497.3 / 516.1 | **113.2 / 124.7** | **4.39x** | 127.4 | **7.6x** |
| REPRESENTATIVE PLAY_VIS_MID | 258.6 / 277.0 | 73.6 / 81.1 | 3.51x | 82.6 | 4.9x |
| **STRESS** NEAR_EDGE | 902.1 / 922.7 | **179.4 / 189.7** | **5.03x** | 193.7 | **11.6x** |

**Was >= 4x achieved? Yes — 4.39x representative, 5.03x stress**, and the
frontier's best point is 8.98x. Phase 12's authorization conditions are all met:
>= 4x, T098a preserved at every scale, artefacts bounded and smooth, and a
credible remaining path.

**Is SC-006 credible now? Not yet, but the shape of the problem has changed.**
Representative Ultra is **14.2x over the 8 ms cloud budget**, down from 63.2x at
the start of this session. Total-frame p95 is 7.6x over SC-006's 16.7 ms, down
from ~31x. SC-006 stays unrescoped, per instruction.

---

## 8. Phase 13 — the model is retired, not extended

Every prior projection multiplied theoretical ceilings. **Those are now
discarded.** The renderer's cost distribution has changed: at 0.250 the cloud
pass is 113.2 ms of a 113.8 ms frame, the composite is 0.086 ms, and the
scaling exponent has fallen to 0.61 — meaning the shader is now materially
occupancy-limited in a way it was not at 0.750.

The next task is to **re-measure the cost distribution of the new renderer from
scratch** rather than assume the old attribution survives. Specifically: the
lighting share, the step-budget elasticity and the descriptor elasticities were
all measured at 1440x810 and none of them can be assumed to hold at 480x270,
where occupancy is the new variable.

---

## 9. Next recommended task

**T147 — re-measure the cost distribution at the shipped ladder**, then choose
between two levers that are now the plausible candidates:

1. **Interleaved reconstruction (candidate F)** — ceiling **2.0x** at equal
   spatial quality, by marching at 0.125 and resolving to 0.250. The figure is
   inferred from two measured frontier points, **not** from an implementation:
   none exists. It is the largest remaining lever and the only one that could
   improve quality *and* cost together.
2. **Distance and LOD policy (Rank 3)** — never measured. FAR already costs 59.5
   ms against SIDE's 113.5 at the shipped Ultra, so distance is doing work
   without a policy; an explicit one may halve the distant cases.

Both should be measured before either is built, on the discipline this track has
followed throughout: rank 2, rank 2b, T143 and T144 were all rejected by
measurement before implementation, and T145 and Rank 1 were both banked because
measurement supported them.

---

## Appendix — evidence

| artefact | path |
|---|---|
| resolution frontier, 7 scales x 7 poses | `run/logs/rank1-frontier.log` |
| quality frontier, 5 poses x 6 scales x 7 grabs | `run/logs/rank1-quality.log`, `run/screenshots/t138/22ff00c4/` |
| shipped five-mode confirmation | `run/logs/rank1-shipped-defaults.log` |
| first sweep, invalidated by the 0.25 diagnostic floor | `run/logs/rank1-clamped.log` |
| ladder | `VolumetricQualityProfile` |
