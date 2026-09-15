# T171 - Harness timing stability

Branch `experiment/cloud-descriptor-k`, parent `9239077`. Nothing merged.
Campaign run 05Sep2026 17:42-17:44, Ultra, raw scale 0.2500, cloud target
480x270, framebuffer 1920x1080, 96 steps. 35 arms x 2 poses = 70 cells,
`T171_REJECTED count=0`. SIDE leads, FAR secondary. History disabled.

Every arm in blocks A-E renders the same image. The campaign exists to measure
how much the harness disagrees with itself about arms that are the same.

## 1. Two defects found by audit, before any measurement

### 1.1 The sampler could record one GPU interval many times (fixed, not the cause)

`StormT135PerformanceProfile.observeFrame()` recorded
`VolumetricCloudRenderer.lastGpuMilliseconds()` once per sampled frame with no
test for whether a new timestamp pair had completed. The timer resolves
asynchronously, so when none had, the previous frame's value was recorded again.
A cell's sample array could therefore mix distinct GPU intervals with duplicates
at a rate depending on the phase between the frame loop and query resolution -
a per-arm, per-run quantity, which is the shape of an unexplained spread.

`VolumetricCloudRenderer.lastGpuTimingSample()` was written for exactly this,
documented as *"Identifies a fresh completed GPU timestamp result without using a
frame-time proxy"*, and **was called from nowhere**. That is the fourth instance
of the omission class the T170 registry invariant exists to catch, in a file the
invariant does not cover.

Fixed: the profile now gates on the timing serial and counts what it rejects.

**It was not the cause.** All 140 cell records report `duplicatesRejected=0`: at
this frame rate a fresh result was available on every sampled frame. The fix is
correct and the defect was real, but latent. Reported as a fix, not as an
explanation.

A related weakness is recorded and not fixed: `CloudGpuTimer.poll()` loops over
all eight query pairs and overwrites `lastMilliseconds` each time one is ready,
so when several resolve in the same poll only the last survives. Results can be
dropped as well as duplicated. It did not fire here either.

### 1.2 Timer-query boundary audit - clean

The query brackets only `FullscreenQuad.draw(shader)`. Outside it:
`shader.apply()`, `PuffLobeSpatialIndex.uploadDescriptors()`,
`bindManualTextures()`, the oracle pre-pass draw, and all framebuffer binding.
No shader link, no texture upload, no descriptor rebuild and no fixture update
occurs inside a measured interval.

Every cell in this campaign reports `descriptors=10`, `mode=ULTRA`, `steps=96`,
`cloudTarget=480x270` and `effectiveResolutionScale=0.2500`. The measured
interval contains the same work in every cell, by construction and by the log.

## 2. The measurement floor

### 2.1 Same GL program, consecutive cells, no program switch (block A, SIDE)

| Arm | cloudP50 | within-cell sd | within-cell CV |
|---|---|---|---|
| `lean_final#a1` | 22.5290 | 1.0914 | 4.84% |
| `lean_final#a2` | 22.2628 | 1.0961 | 4.90% |
| `lean_final#a3` | 22.4010 | 0.9205 | 4.12% |
| `lean_final#a4` | 22.4502 | 1.0937 | 4.85% |

**Cell-to-cell CV of p50: 0.50%. Peak-to-peak spread: 1.19%.**

Note the two different dispersions. *Within* a cell, frame-to-frame CV is
4.1-4.9% - that is the renderer's own frame variation and it is not a harness
defect. *Between* cells of the same program, p50 varies by only 0.50%, because
the median absorbs the frame variation. The percentile is doing its job.

### 2.2 Separately linked, logically identical programs

`t171_dup_a` and `t171_dup_b` are generated with FINAL's exact defines and
`keepUniforms`, and a sandbox invariant asserts their generated GLSL is
**byte-identical** to `cloud_atmosphere_volume_final.fsh` - string equality, not a
heuristic. Without that the comparison would be about shader source rather than
about program identity.

Per-program mean of p50 across blocks A-D, SIDE:

| Program | cells | mean cloudP50 |
|---|---|---|
| `lean_final` | 11 | 22.3708 |
| `t171_dup_a` | 4 | 22.4579 |
| `t171_dup_b` | 4 | 22.2201 |

**Largest program-to-program difference: 1.07%**, against a same-program spread
of 1.19%. **There is no detectable program-identity effect.** Separately linked
GL programs compiled from identical source do not measurably differ.

Pooled across all 19 identical-output cells:

| Pose | n | mean | sd | **CV** | range |
|---|---|---|---|---|---|
| SIDE | 19 | 22.3574 | 0.2172 | **0.97%** | 3.71% |
| FAR | 19 | 14.0137 | 0.1730 | **1.23%** | 4.55% |

### 2.3 Ordering and warm-up

Block B runs anchor/dup_a/dup_b in fixed order; block C runs the same three
reversed. Both are inside the pooled figures above, and no program's mean depends
on whether it ran first or last in its block. **No ordering or warm-up bias is
detectable at this resolution.**

### 2.4 Sample count (block D, SIDE, same program)

| Cell | samples | cloudP50 | within-cell CV |
|---|---|---|---|
| `lean_final#d060` | 60 | 22.5413 | 6.23% |
| `lean_final#d120` | 120 | 22.7553 | 4.20% |
| `lean_final#d240` | 240 | 22.2034 | 5.03% |

Quadrupling the sample count does not reduce cell-to-cell disagreement: the
three p50s span 2.4%, which is *wider* than block A's 1.19% at 60 samples each.
**60 frames is sufficient. The residual disagreement is not a sampling-noise
problem, so more frames cannot fix it** - which is what Task 5 existed to
determine.

### 2.5 GPU state

Per-second `nvidia-smi` telemetry, 641 samples. **NVIDIA GeForce RTX 4070 Laptop
GPU**, SM clock range 210-3105 MHz.

| Metric | Value during the campaign |
|---|---|
| SM clock | p25 = p50 = p75 = **2340 MHz**, max 2355 |
| pstate | P0 for 496 samples; P8/P3/P4/P5 only while idle |
| temperature | 44-63 C |
| power | mean 39.0 W |
| throttle reasons | `0x0` active, `0x1` (GpuIdle) only between campaigns |

A laptop part with a 14.8x clock range was the leading hardware hypothesis. It is
**ruled out**: the clock is pinned at 2340 MHz for the whole campaign, the part
never thermally throttles, and the anomalous cell in 3.1 ran at 2340 MHz and
53-55 C like every other cell.

## 3. What the residual actually is

### 3.1 It is not a wide distribution. It is occasional bimodality.

Two cells in 70 measured a different, internally consistent performance state
from their own twin:

| Pose | Arm | repeat 1 | repeat 2 | difference |
|---|---|---|---|---|
| SIDE | `t170_fpmax5` | 12.5880 | 11.0971 | **13.4%** |
| FAR | `t170_desc_nosdf` | 12.7887 | 8.1859 | **56.2%** |

The 13.4% is T170's unexplained spread, reproduced exactly - and it is one
*cell*, not a property of the arm. `t170_fpmax6` measured 11.1636 in both
repeats, to four decimal places.

The FAR `desc_nosdf` pair is the clearest specimen. Both cells are internally
tight and their distributions are **disjoint**:

```
#f1  samples=60  cloudMin=12.5870  cloudP50=12.7887  cloudMax=13.0591  sd=0.1122
#f2  samples=60  cloudMin= 7.9421  cloudP50= 8.1859  cloudMax= 9.6563  sd=0.3016
```

No frame in `#f1` was faster than any frame in `#f2`. Both report
`descriptors=10`, `mode=ULTRA`, `steps=96`, `cloudTarget=480x270`,
`effectiveResolutionScale=0.2500`, `duplicatesRejected=0`. This is not noise
around a mean; it is two states.

### 3.2 What has been excluded

| Hypothesis | Verdict | Evidence |
|---|---|---|
| duplicate GPU samples | excluded | `duplicatesRejected=0` in all 140 records |
| GPU clock ramp | excluded | 2340 MHz pinned, p25=p50=p75 |
| thermal throttle | excluded | 44-63 C, throttle reasons `0x0` |
| resolution scale drift | excluded | `0.2500` in every cell |
| render-target size | excluded | `480x270` in every cell |
| descriptor count / fixture decay | excluded | `descriptors=10` in every cell |
| program identity / linking | excluded | 1.07% between byte-identical programs |
| ordering / warm-up | excluded | blocks B and C agree |
| sample count | excluded | 60/120/240 do not converge |
| work inside the timed interval | excluded | boundary audit, section 1.2 |
| GC | excluded | 0 Full GCs |

### 3.3 Remaining unexplained

**The bimodal outlier mode is not explained.** Rate: 2 of 70 cells (2.9%), or 2
of 16 repeat pairs. Magnitude: +13% to +56%, always slower. Signature: an
internally tight cell whose distribution is disjoint from its twin's.

Everything cheap to exclude has been excluded. What remains untested is
driver-side: a shader variant recompiled against a different state vector, a
different memory residency for the descriptor texture, or a scheduling mode the
driver enters and leaves. Distinguishing those needs either program-binary
inspection or vendor tooling, and the task explicitly rules out depending on
vendor-private tools.

## 4. Acceptance threshold

```
same-program repeat, no program switch:   CV = 0.50%   (spread 1.19%)
separate-identical-program, pooled:       CV = 0.97%  SIDE
                                          CV = 1.23%  FAR
outlier-cell rate:                        2.9% of cells, +13% to +56%
```

**Recommended rule.**

1. **Minimum measurable speedup: 3%.** Two repeats, both agreeing within 3%.
   That is 2.5 sigma on the worst pooled CV.
2. **Every banked arm must be measured at least twice.** A single cell cannot be
   banked at any threshold, because the failure mode is an outlier cell rather
   than a wide distribution, and an outlier cell looks perfectly tight from the
   inside.
3. **Repeats disagreeing by more than 3% are rejected, not averaged.** Averaging
   an outlier with a good cell hides exactly the thing that made T170's Task 1
   unanswerable.
4. Anything under 3%, or measured once, is labelled **BELOW MEASUREMENT FLOOR**
   and is neither a win nor a loss.

Applied retroactively, T170's clamp sweep fails rule 2 outright: every fpmax arm
was a single cell, and one of them was an outlier.

## 5. Task 7 - re-measurement of the T170 points

Two repeats each, SIDE, against the pooled anchor of 22.3574.

| Arm | repeat 1 | repeat 2 | mean | agreement | speedup | T170 said |
|---|---|---|---|---|---|---|
| `lean_final` (19 cells) | - | - | 22.3574 | CV 0.97% | 1.000 | - |
| `t169_stack_fast` | 12.5932 | 12.6136 | 12.6034 | 0.16% | **1.774** | 1.753 |
| `t170_desc_nosdf` | 12.5737 | 12.5727 | 12.5732 | 0.01% | **1.778** | 1.582 |
| `t170_desc_constedge` | 16.0573 | 15.9898 | 16.0236 | 0.42% | **1.395** | 1.655 |
| `t170_desc_hoist` | 15.6344 | 15.8413 | 15.7379 | 1.31% | **1.421** | 1.731 |
| `t170_stack_hoist` | 8.7757 | 8.7306 | 8.7532 | 0.51% | **2.554** | 2.930 |

**Every pair agrees within 1.31%, all well inside the 3% floor.** These six are
banked.

### 5.1 Does 7.31 ms reproduce? No.

**`t170_stack_hoist` re-measures at 8.7532 ms SIDE, against T170's 7.3052 - 19.8%
higher, far outside the measurement floor.** The two repeats agree with each
other to 0.51%, so this is a trustworthy number and T170's was not.

Consequences:

- **SIDE <= 10 ms: met** (8.75, and P95 9.11-9.19).
- **SIDE <= 8 ms: NOT met.** T170's claim that this was the first configuration
  in the line to clear 8 ms at the binding pose **is withdrawn.**

The direction survives and the magnitude does not. `t169_stack_fast` also
re-measures slightly better than T170 reported (1.774 against 1.753), so the
anchor itself is consistent; it is the hoist ceiling specifically that was
overstated.

### 5.2 The descriptor findings survive

| Finding | T170 | T171 | verdict |
|---|---|---|---|
| exact SDF is a large class | 1.582 | 1.778 | **confirmed** |
| edge width is a large class | 1.655 | 1.395 | **confirmed**, smaller |
| edge width + ownership hoist | 1.731 | 1.421 | **confirmed**, smaller |
| hoist ceiling clears 8 ms | yes | **no** | **withdrawn** |

The hoist is worth **1.421x at SIDE**, not 1.731x. That is still far above the
3% floor and still the largest image-identical opportunity identified in this
line.

## 6. Decision

**The descriptor-invariant precompute is authorized as the next architecture**,
with its expected return revised down.

The reasoning that led to it is unchanged and was independently reconfirmed
here: `stormEdgeWidthBlocksFromData` takes no sample position, the ownership
extents do not either, and removing both is worth 1.421x at the binding pose -
about fourteen times the measurement floor. T170's separate finding that
descriptor fetches are approximately free is what makes trading arithmetic for a
slightly larger payload favourable, and nothing here contradicts it.

What changed is the target. The stack plus a perfect hoist lands at **8.75 ms
SIDE, not 7.31** - inside the 10 ms budget and outside the 8 ms one. The
precompute should be built expecting to close the 10 ms goal, not the 8 ms goal.

**Two conditions on the work:**

1. Every arm measured at least twice, per section 4. This campaign would have
   reported 7.31 ms as fact under T170's single-cell protocol.
2. The 2.9% outlier-cell mode is still unexplained. It is now *detectable* -
   repeats disagreeing by more than 3% - which is enough to keep it out of banked
   results, but it is not understood, and a campaign that ever needs to resolve
   an effect below ~3% will have to close it first.

## 7. Harness notes

- Peak benchmark-owned memory 6,606 MB; minimum availability 4,251 MB; 0 Full GCs.
- `cloudP50`/`cloudP95`/`cloudSd`/`cloudCv` come from GPU timer queries.
  `frameP95` remains unbanked.
- Every cell now reports `samples`, `duplicatesRejected`, `cloudSd`, `cloudCv`,
  `cloudMin`, `cloudMax`, so a future campaign can apply section 4 without
  re-deriving it.

## 8. Status

Not merged. T170 banked as `9239077`.
