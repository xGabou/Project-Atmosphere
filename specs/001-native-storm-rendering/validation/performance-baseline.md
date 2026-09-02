# T136 — representative performance characterization

**Feature**: `001-native-storm-rendering`
**Task**: T136 [PERFORMANCE]
**Date**: 2026-09-01
**Hardware**: NVIDIA GeForce RTX 4070 Laptop GPU, driver 596.21, OpenGL 4.6
**Resolution**: 1920x1080 framebuffer (SC-006 reference)

---

---

> **Correction (T138, 2026-09-02).** Every "storm gameplay" figure in this
> document is measured at a pose whose frame contains no storm. `PLAY_NEAR`,
> `PLAY_MID` and `PLAY_HIGH` place the camera 4x, 7x and 5x the storm's
> horizontal radius away; at T134 severe scale that radius is ~668 blocks, so
> the nearest of them puts the storm's near edge 2002 blocks out — past the
> shipped `cloudRenderDistance = 2000`. The captured frames are empty sky and
> the counters agree (0.09 light-march evaluations per pixel, 99.9 % of steps
> resolved as empty space). Those cells measure the cost of marching an empty
> slab with a distant storm's descriptors resident, which is a real and
> alarming cost but is not storm gameplay.
>
> Measured on the same fixture at the same y=120 altitude with the storm in
> frame: **PLAY_VIS_NEAR (1.6x radius) costs 492.8 ms at Ultra and
> PLAY_VIS_MID (2.4x radius) 284.4 ms** — 61.6x and 35.5x over the 8 ms cloud
> budget, against the 12.8x this document reports. See
> `performance-internal-resolution.md`. T142 restates the budget contract
> against the corrected poses.


## 1. Harness corrections

T135's sweep was contaminated by fixture decay. Three fixes, all in place for
every number below:

1. **Per-sample descriptor validation.** Each cell records the descriptor count
   at its start and re-checks it on every sampled frame. A cell whose count
   ever falls is discarded, not reported — `sampled` is reset to zero so a
   partial sample cannot reach the record.
2. **Deterministic respawn and re-adoption.** A discarded cell respawns the
   storm, waits for ten descriptors, and retries, up to two attempts before the
   cell is abandoned and logged as unmeasurable.
3. **Per-pose fixture re-resolution.** `pa cloud spawn` places the new storm at
   the *player*, so poses computed from the pre-respawn fixture aimed at empty
   sky. Every pose now re-resolves the fixture before computing its camera.
   Without this, FAR/Medium measured 113.6 ms on one attempt and 15.1 ms on
   another — the same label, two different scenes.

Every cell below carries `descriptors=10` for its whole sample.

## 2. Method

Per presented frame the render hook records the cloud pass's own GPU timer
result and the wall-clock frame interval, so cloud, total and remainder come
from the same frames. 45 settle frames discarded, then 120 sampled. Each
quality mode uses its **own** resolution scale (the fixture's 0.75 pin is
released for the sweep and reapplied for captures).

## 3. Matrix — cloud GPU p50, milliseconds, and multiple of budget

| pose | Low (3.0) | Low 24 (4.0) | Medium (5.0) | High (6.5) | Ultra (8.0) |
|---|---|---|---|---|---|
| **A — severe worst case** | | | | | |
| NEAR_EDGE (1.12x) | 81.0 (27.0x) | 205.3 (51.3x) | 385.1 (77.0x) | 469.9 (72.3x) | **999.7 (125.0x)** |
| ABOVE | 25.2 (8.4x) | 106.7 (26.7x) | 211.9 (42.4x) | 294.5 (45.3x) | **678.8 (84.8x)** |
| SIDE (1.7x) | 47.3 (15.8x) | 128.9 (32.2x) | 222.0 (44.4x) | 273.7 (42.1x) | **561.5 (70.2x)** |
| FAR (2.6x) | 28.1 (9.4x) | 61.9 (15.5x) | 118.0 (23.6x) | 146.7 (22.6x) | 270.6 (33.8x) |
| BELOW | 44.0 (14.7x) | 155.1 (38.8x) | 256.1 (51.2x) | 133.6\* | 200.8\* |
| **B — storm gameplay** | | | | | |
| PLAY_NEAR (4x radius, y=120) | **7.9 (2.6x)** | 17.3 (4.3x) | 32.4 (6.5x) | 42.9 (6.6x) | **102.3 (12.8x)** |
| PLAY_MID (7x radius, y=100) | **5.2 (1.7x)** | 12.6 (3.1x) | 23.1 (4.6x) | 29.5 (4.5x) | not measured |
| **D — clear control** | 0.31 | 0.67 | 1.60 | 2.06 | 3.89 |

\* BELOW/High and BELOW/Ultra report a 960x540 cloud target where Ultra should
be 1440x810; the resolution scale had not settled for those two cells. They are
recorded but not used in any conclusion.

Clear-weather figures are from the T135 sweep, which measured that pose
uncontaminated.

**Not measured**: PLAY_MID/Ultra, all PLAY_HIGH, all CLEAR in this run. The
retry path that refuses to sample a zero-descriptor cell does not increment the
retry counter, so it looped instead of advancing. The bug is identified and the
cells are simply absent rather than wrong.

## 4. Total frame and remainder

Total frame p50 tracks cloud p50 plus the remainder in every cell. Across all
production cells the **non-cloud remainder is 0.6 to 2.9 ms** — everything
except the cloud raymarch is comfortably inside a 16.7 ms frame.

Ultra total-frame p95 against SC-006's 16.7 ms:

| pose | frame p95 | over |
|---|---|---|
| NEAR_EDGE | 1031.5 ms | 61.8x |
| ABOVE | 694.5 ms | 41.6x |
| SIDE | 580.1 ms | 34.7x |
| FAR | 283.9 ms | 17.0x |
| PLAY_NEAR | 106.2 ms | **6.4x** |

## 5. Scaling — what the cost is actually a function of

| comparison | pixels | step budget | measured cost |
|---|---|---|---|
| SIDE Low → Medium | **4.00x** | 1.67x | **4.69x** |
| ABOVE Low → Medium | **4.00x** | 1.67x | **8.41x** |
| SIDE Medium → High | 1.00x | **1.60x** | **1.23x** |
| ABOVE Medium → High | 1.00x | **1.60x** | **1.39x** |

**Cost scales with pixels, close to linearly. It scales only weakly with the
step budget.** Holding the target fixed and raising the step budget 1.6x costs
1.23–1.39x, because rays terminate on the transmittance floor long before
`MAX_STEPS` — the T098 traces measured 38–85 iterations against a 128 cap.
Raising the budget buys worst-case headroom, not average work.

Cost per (pixel x step-budget) ranges 0.9–26 ns, which is another way of saying
the step budget is not the work unit: **executed** samples are, and how many a
ray executes is set by how much material it crosses, not by the cap.

Descriptor-count scaling was not isolated: every severe cell carries exactly ten
descriptors, and varying it was outside this run.

## 6. Cost attribution — lighting

Same fixture, same poses, lighting and its light-cone march replaced by a
constant radiance (`PaDiagnosticLightingMode`, diagnostic-only, defaults off):

| mode | production | constant lighting | lighting share |
|---|---|---|---|
| Low | 47.33 | 40.41 | 6.93 ms = **14.6 %** |
| Low 24 | 128.92 | 100.39 | 28.53 ms = **22.1 %** |
| Medium | 221.98 | 174.32 | 47.66 ms = **21.5 %** |
| High | 273.70 | 215.04 | 58.66 ms = **21.4 %** |
| Ultra | 561.49 | 434.16 | 127.33 ms = **22.7 %** |

**Lighting and self-shadowing are about 21–23 % of cloud cost.** Removing them
entirely would give at most a 1.29x speedup. The primary march dominates.

## 7. Gap in this baseline

The per-cell workload counters T136 asks for — density evaluations, descriptor
evaluations, descriptor texture fetches — exist in the shader
(`paDescriptorEvaluations`, `paDescriptorTextureFetches`,
`paLightMarchDensityEvaluations`, `paPrimaryRaySteps`) and are readable through
`StormWorkloadRuntimeCapture`'s two-frame readback, but were **not wired into
this sweep**. Pixels, step budgets, executed-step behaviour, the lighting share
and the remainder are all measured; **per-sample descriptor cost is not
isolated**. T137's option 3 estimate is correspondingly the least grounded, and
this is the first thing to add before acting on it.

## 8. Findings

1. **Representative gameplay is 2–13x over budget; the parked severe worst case
   is 70–125x.** That is nearly a factor of ten between them, and it means the
   two need different answers.
2. **NEAR_EDGE is the true worst case at 999.7 ms**, not SIDE — a camera just
   outside the storm boundary fills the frame with high-occupancy rays.
3. **Cost is pixel-bound.** Internal resolution is the dominant lever; the step
   budget is not.
4. **Lighting is 21–23 %**, a useful but secondary lever.
5. **Non-cloud cost is 0.6–2.9 ms** and is not a problem.
6. **Clear weather is inside budget at every mode.** The cost is entirely
   storm-driven.
