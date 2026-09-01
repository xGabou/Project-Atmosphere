# T135 — five-mode performance budget contract

**Feature**: `001-native-storm-rendering`
**Task**: T135 [PERFORMANCE]
**Date**: 2026-09-01
**Branch**: `worktree-t098-production-ray-trace`, merged onto `Forge-1.20.1` (4e356c3)
**Status**: contract established, measured, and **failing by one to two orders of magnitude**

---

## 1. The contract

Per-mode budgets. The cloud GPU budgets are the existing FR-010–FR-012 targets;
they are **retained as targets, not revised to match measurement**, because
they are the product requirement and the measurement below is of an
implementation that does not yet meet it. SC-006's Ultra total-frame p95 of
16.7 ms at 1920x1080 is unchanged.

| mode | raymarch steps | resolution scale | cloud GPU budget | total-frame budget |
|---|---|---|---|---|
| Low | 24 | 0.250 | 3.0 ms | 16.7 ms |
| Low 24 | 32 | 0.375 | 4.0 ms | 16.7 ms |
| Medium | 40 | 0.500 | 5.0 ms | 16.7 ms |
| High | 64 | 0.500 | 6.5 ms | 16.7 ms |
| **Ultra** | 96 | 0.750 | **8.0 ms** | **16.7 ms (SC-006 p95)** |

## 2. Measurement conditions

| | |
|---|---|
| hardware | **NVIDIA GeForce RTX 4070 Laptop GPU**, driver 596.21, OpenGL 4.6 |
| resolution | **1920 x 1080** framebuffer (SC-006 reference) |
| fixture | severe `cumulonimbus_capillatus`, T132 auto-generated world, fixed seed |
| controls | movement frozen, daylight frozen at noon, adaptive resolution released so each mode uses its **own** scale |
| method | per presented frame: cloud pass GPU timer result and wall-clock frame interval, same frames |
| remainder | **measured**, not modelled: total-frame minus cloud, per frame |
| sample | 45 settle frames discarded, then 120 frames per (pose, mode) cell |
| percentiles | p50 and p95 |

The cloud GPU figure is the raymarch pass's own `CloudGpuTimer` span, begun
immediately before the fullscreen draw and ended immediately after.

## 3. Measured matrix

Cloud GPU p50 / p95 and total-frame p50, milliseconds.

| pose | mode | cloud target | cloud p50 | cloud p95 | frame p50 | frame p95 | remainder p50 | **cloud vs budget** |
|---|---|---|---|---|---|---|---|---|
| SIDE | Low | 480x270 | 56.32 | 64.54 | 56.92 | 66.32 | 0.60 | **18.8x** |
| SIDE | Low 24 | 720x405 | 117.86 | 131.20 | 118.91 | 131.35 | 1.05 | **29.5x** |
| SIDE | Medium | 960x540 | 203.59 | 219.57 | 204.68 | 221.26 | 1.09 | **40.7x** |
| SIDE | High | 960x540 | 253.24 | 265.70 | 254.50 | 267.14 | 1.26 | **39.0x** |
| **SIDE** | **Ultra** | 1440x810 | **515.20** | **536.21** | **516.45** | **541.53** | 1.25 | **64.4x** |
| FAR | Low | 480x270 | 28.85 | 34.24 | 29.78 | 34.96 | 0.93 | 9.6x |
| FAR | Low 24 | 720x405 | 61.33 | 69.69 | 62.15 | 70.70 | 0.83 | 15.3x |
| FAR | Medium | 960x540 | 120.68 | 133.13 | 121.23 | 133.91 | 0.54 | 24.1x |
| FAR | High | 960x540 | 149.41 | 164.41 | 150.13 | 165.78 | 0.72 | 23.0x |
| FAR | Ultra | 1440x810 | 274.75 | 287.57 | 275.78 | 289.39 | 1.03 | **34.3x** |
| BELOW | Low | 480x270 | 45.46 | 64.49 | 46.35 | 66.21 | 0.89 | 15.2x |
| BELOW | Low 24 | 720x405 | 154.68 | 173.82 | 155.56 | 175.44 | 0.88 | 38.7x |
| BELOW | Medium | 960x540 | 277.36 | 319.74 | 278.11 | 322.17 | 0.75 | **55.5x** |
| BELOW | High | 960x540 | 358.65 | 404.69 | 358.97 | 408.87 | 0.32 | **55.2x** |
| CLEAR | Low | 480x270 | 0.31 | 0.32 | 0.94 | 1.74 | 0.62 | ok |
| CLEAR | Low 24 | 720x405 | 0.67 | 0.69 | 0.98 | 1.48 | 0.31 | ok |
| CLEAR | Medium | 960x540 | 1.60 | 1.67 | 1.89 | 2.90 | 0.29 | ok |
| CLEAR | High | 960x540 | 2.06 | 2.12 | 2.35 | 4.23 | 0.30 | ok |
| CLEAR | Ultra | 1440x810 | 3.89 | 3.93 | 4.53 | 5.16 | 0.64 | ok |

**Excluded as contaminated.** `BELOW/Ultra` (cloud p50 37.09 against p95 883.67)
and every `ABOVE` cell were sampled after the fixture began to decay; their
bimodal distributions are the storm disappearing mid-cell, not a cost. The
harness holds cloud movement and the daylight cycle still but does not freeze
the storm lifecycle, and the full 25-cell sweep outlives the fixture. Those
cells must be re-measured against a held fixture before they are quoted.

## 4. Findings

**The cloud budget is missed by 9x to 64x whenever the storm is on screen.**

- Ultra at the SIDE acceptance pose costs **515 ms** against an 8.0 ms budget.
- Total frame at that pose is **516 ms p50 / 542 ms p95** against SC-006's
  **16.7 ms p95** — a **32x** miss, about **1.9 frames per second**.
- The cheapest shipped mode, Low at a 480x270 cloud target, still costs
  **56 ms** — **18.8x** its 3.0 ms budget and 3.4x the whole frame budget.
- **The non-cloud remainder is 0.3–1.3 ms in every cell.** Everything except
  the cloud pass is comfortably inside budget; the cloud raymarch is
  essentially the entire frame.
- Clear weather is fine at every mode (0.31–3.89 ms). The cost is entirely
  storm-driven, not a baseline overhead.

**The cost tracks raymarch work, not resolution alone.** Normalising SIDE by
cloud-target pixels times steps gives 18.1, 12.6, 9.8, 7.6 and 4.5 ns per
step-sample for Low through Ultra — the same operation getting slightly cheaper
per sample as occupancy rises, on a smoothly increasing total. Nothing here
looks like a stall or a pathological branch; it is the volume of raymarch
samples.

**Hardware is not the explanation.** An RTX 4070 Laptop is well above the
target class implied by a 3.0 ms Low budget.

## 5. Contract status

| mode | budget | measured Ultra-pose reality | verdict |
|---|---|---|---|
| Low | 3.0 ms | 56.32 ms (SIDE) | **FAIL** |
| Low 24 | 4.0 ms | 117.86 ms (SIDE) | **FAIL** |
| Medium | 5.0 ms | 203.59 ms (SIDE) | **FAIL** |
| High | 6.5 ms | 253.24 ms (SIDE) | **FAIL** |
| Ultra | 8.0 ms | 515.20 ms (SIDE) | **FAIL** |
| Ultra total frame (SC-006) | 16.7 ms p95 | 541.53 ms p95 (SIDE) | **FAIL** |

The budgets are recorded as the contract. They are not revised upward: a budget
that is redefined to whatever the implementation currently costs is not a
budget. The gap is the input to T136 (baseline attribution) and T137 (ranked
architecture decision).

## 6. What this does and does not license

It does **not** license image-changing optimisation on its own. The Phase 4P
rule stands: image-changing performance work carries an explicit T098b regrade
obligation.

It does establish that the required saving is **one to two orders of
magnitude**, which is far beyond constant-factor tuning. T137 should expect to
choose among structural options — sample count per pixel, internal resolution
and reconstruction, descriptor evaluation cost per sample, and distance/LOD
policy — rather than micro-optimisation.

## 7. Harness limitation to fix before T136

The five-pose x five-mode sweep takes roughly 25 minutes and outlives the
spawned fixture. T136's baseline must either hold the storm lifecycle for the
duration, re-spawn and re-resolve between poses, or shorten the matrix. The
contaminated cells in section 3 are the evidence that this is not optional.
