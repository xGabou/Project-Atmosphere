# T149 — graded lighting and detail LOD: rejected

**Feature**: `001-native-storm-rendering`
**Task**: T149 [PERFORMANCE]
**Follows**: `performance-post-rank1.md` (T147), `performance-interleaving.md` (T150/T151)
**Date**: 2026-09-03
**Hardware**: NVIDIA GeForce RTX 4070 Laptop GPU, OpenGL 4.6
**Decision**: **rejected.** Representative gameplay gets slower, not faster.
**Production render path unchanged.**

---

## 0. The result

A graded light cone removes **27–40 % of light evaluations at every pose** and
converts almost none of it into time. At the representative pose it is
**5.7 % slower** than production.

| pose | production | graded policy | change |
|---|---|---|---|
| **PLAY_VIS_NEAR** | 103.9 | 109.8 | **+5.7 %** |
| PLAY_VIS_MID | 71.2 | 64.6 | −9.3 % |
| SIDE | 104.6 | 100.9 | −3.6 % |
| FAR | 64.9 | 59.6 | −8.2 % |
| ABOVE | 100.7 | 76.3 | **−24.2 %** |
| BELOW | 187.5 | 188.9 | +0.8 % |
| NEAR_EDGE (stress) | 198.4 | 189.9 | −4.3 % |

Representative mean is about **1.02x** against a CASE A bar of 1.3x, and the
two most expensive poses — BELOW at 187.5 ms and NEAR_EDGE at 198.4 ms — are
the two it helps least. **CASE C, and CASE B fails its own condition**: the
policy does not materially improve the severe worst views, it improves ABOVE,
which is mid-pack.

All eight poses in this run were confirmed storm-visible by T150's guard, with
zero rejections, so none of these cells is an empty-sky artefact.

---

## 1. Phase 1 — lighting and detail, measured independently at the shipped ladder

Re-measured rather than reused from T147, whose NEAR_EDGE and BELOW rows were
taken before T150 existed and showed impossible positive deltas.

| pose | production p50 | **lighting share** | **detail share** | march remainder |
|---|---|---|---|---|
| PLAY_VIS_NEAR | 103.9 | 8.4 % | 9.7 % | ~82 % |
| PLAY_VIS_MID | 71.2 | 18.6 % | 16.5 % | ~65 % |
| SIDE | 104.6 | 21.0 % | 15.8 % | ~63 % |
| FAR | 64.9 | 18.0 % | 11.1 % | ~71 % |
| **ABOVE** | 100.7 | **68.9 %** | 44.2 % | ~10 % |
| BELOW | 187.5 | **0 %** | 39.3 % | ~61 % |
| NEAR_EDGE | 198.4 | 29.2 % | 12.3 % | ~58 % |

Two things this corrects. **The lighting share swings widely with the fixture**
— 8.4 % at PLAY_VIS_NEAR here against T147's 23.2 % on a different storm — so
the T147 figure was not a stable representative number. And **BELOW has no
lighting cost at all**: with the camera inside the whiteout the light cone is
replaced by a single forward probe, so `constant_lighting` changes nothing
there.

---

## 2. Phase 5 — why ABOVE is expensive, and whether it generalises

From the counters: ABOVE runs **66.5 light evaluations per pixel against SIDE's
15.6**, and 83 % of *all* its density evaluations are light-cone taps. Per
primary sample that is 5.07 taps at ABOVE against 1.14 at SIDE.

The cause is not view geometry as such, it is **what fraction of primary
samples carry material**. Looking down from above, nearly every ray enters
dense cloud immediately and nearly every sample is lit, so nearly every sample
pays the full six-tap cone. At SIDE most samples are empty and never light.

That is genuinely general — any high-coverage view pays it, and NEAR_EDGE is
the same phenomenon at 33.0 light evaluations per pixel. So the lever was
**not** an ABOVE-only hack, which is why it was worth building. It simply does
not pay.

---

## 3. Phases 2, 3, 4 and 6 — what was built

### 3.1 Detail: the principled cutoff cannot fire

The intended criterion was "reduce high-frequency detail when projected below
pixel scale". It was computed before implementing and it rules the arm out.

The detail domain scale is 0.022 per block over an FBM period of 2, so the
coarsest detail octave has a **22.7-block wavelength**. At the shipped Ultra
target of 480x270 over a 70° vertical field, one low-resolution pixel subtends
0.00452 rad, giving a world footprint of:

| distance | 480x270 | 360x203 | 240x135 |
|---|---|---|---|
| 400 blocks | 1.8 | 2.4 | 3.6 |
| 800 | 3.6 | 4.8 | 7.2 |
| 1200 | 5.4 | 7.2 | 10.9 |
| 2000 | 9.0 | 12.0 | 18.1 |

**The coarsest octave stays above Nyquist at every distance inside the
2000-block render distance at the shipped Ultra**, and only goes sub-Nyquist
beyond ~1250 blocks at the 240x135 modes. The three FBM octaves also arrive in
a single texture fetch — they are the r, g and b channels of one sample — so
the finer ones cannot be dropped individually.

**A footprint-based detail LOD therefore has almost nothing to remove**, and
removing detail earlier would be arbitrary quality loss rather than level of
detail. The correct treatment of the sub-Nyquist octaves is a mip bias, which
the fetch already accepts and which costs the same fetch. The arm was not built.

### 3.2 Lighting: graded by contribution and by distance

`lightMarchOpticalDepth` grades its tap count on two continuous signals, both
published by the primary march immediately before each lighting sample:

- **contribution** — the transmittance the ray still has before this step is
  integrated, which is exactly the weight this sample's radiance carries into
  the frame. `smoothstep(0.08, 0.45, transmittance)`.
- **distance** — `1 - smoothstep(0.35, 0.85, t / MaxRenderDistance)`.

Taps interpolate between the mode's `LightSteps` and a floor of **2**, so the
cone always keeps a real direction and never collapses to a single shadow ray.
Neither signal is keyed to a descriptor, role or other discrete boundary, so
transitions are smooth by construction — the popping the brief warns about
cannot arise from this policy.

Three arms: each signal alone, and both together.

---

## 4. Phase 7 — the measurement, and why it fails

| pose | light evals removed | time change | **conversion** |
|---|---|---|---|
| PLAY_VIS_NEAR | −28.3 % | **+5.7 %** | **−0.20** |
| PLAY_VIS_MID | −40.4 % | −9.3 % | 0.23 |
| SIDE | −29.7 % | −3.6 % | 0.12 |
| FAR | −36.7 % | −8.2 % | 0.22 |
| **ABOVE** | −39.6 % | −24.2 % | **0.61** |
| NEAR_EDGE | −27.6 % | −4.3 % | 0.16 |

Removing between a quarter and two fifths of all light-cone work returns
**12–23 %** of it as time at five of six poses, and **less than nothing** at the
representative one.

### 4.1 The distance arm isolates the cause

The distance-only arm at PLAY_VIS_NEAR removed **0.1 %** of light evaluations —
the storm sits at 0.2–0.5 of the render distance, below the grading knee, so the
signal essentially never fires — and still cost **+8.9 %**.

**Work that was never removed still cost nine percent.** That is not divergence
and it is not the grading arithmetic, which runs once per lit sample and is a
few instructions against a loop of texture-fetching `cloudDensity` calls. It is
that the loop bound stopped being uniform: production derives `steps` from the
`LightSteps` uniform, so the compiler can unroll a fixed six-iteration light
cone, and a data-dependent bound forces a dynamic loop with per-iteration
bounds checks.

ABOVE is the exception that confirms it: there the reduction is both large
(−39.6 %) and spatially coherent — almost every pixel is deep inside cloud, so
whole warps drop from six taps to two together — and it is the one pose where
the saving outruns the lost unrolling.

### 4.2 This is the third independent confirmation of the same rule

| task | change | light/work removed | time |
|---|---|---|---|
| T141 | tighter conservative descriptor bound | −1.25 % lobes | **+4.3 to +7.6 %** |
| T151 | interleaved sampling (per-pixel masking) | −50 % pixels | ceiling **1.42x**, below the bar |
| **T149** | graded light cone (per-sample masking) | −27 to −40 % evals | **+5.7 % representative** |

Every optimization that reduces work for *some* lanes or *some* samples has
failed on this renderer. The two that succeeded — Rank 1 and T145 — both remove
work **uniformly**: Rank 1 marches fewer pixels outright, and T145 skips a whole
path behind a branch that is coherent across large screen regions.

**That is the design rule this track has now earned: on this shader, savings
must be uniform across the warp to become time.**

---

## 5. Phases 8 and 9 — decision

No visual validation was run and no transition behaviour was captured, because
the candidate is rejected on performance before quality could matter — it makes
the representative pose slower. The policy's transitions are continuous by
construction, so nothing about popping is left unresolved by not measuring it.

| case | verdict |
|---|---|
| A — representative ≥ 1.3x | **no**, ~1.02x and PLAY_VIS_NEAR regresses |
| B — ABOVE improves, bank if it materially improves severe worst views | **no**, it does not touch BELOW (+0.8 %) or NEAR_EDGE (−4.3 %), the two most expensive poses |
| **C — savings materially below the measured ceilings** | **yes** |
| D — visual degradation | not reached |

**T149 is rejected.** The arms are retained as diagnostics, defaulting off, so
the conversion measurement is reproducible.

---

## 6. Phase 10 — the cost distribution, and the new bottleneck

Even at their ceilings — removing lighting and detail *entirely* — the two
classes are 8.4 % and 9.7 % at PLAY_VIS_NEAR. **About 82 % of representative
cloud time is the primary march itself**, and neither of T149's targets was ever
going to reach 1.3x there.

| lever | measured | status |
|---|---|---|
| Rank 1 internal resolution | 4.39x | **shipped** |
| T145 rain locality | 1.15–1.29x | **shipped** |
| Graded lighting/detail LOD | **1.02x representative** | **rejected (T149)** |
| Interleaved reconstruction | 1.42x / 1.67x ceiling | rejected (T151) |
| Descriptor evaluation / fetch | 1.19x / 1.08x ceilings | rejected (T141) |
| Distance culling | deletes the storm | not adoptable (T098a) |

Representative Ultra is **103.9 ms** against the 8 ms budget — **13.0x over**.
Stress NEAR_EDGE is 198.4 ms, **24.8x over**. SC-006 remains not credible and
unrescoped.

---

## 7. Next recommended task

**T153 — a coarse empty-space acceleration structure.**

The counters point at it and the design rule above says it is the right *shape*
of change. At the representative poses **83–100 % of march steps resolve as
empty space**, and each still pays the conservative safe-advance query before
the weather skip can reject it. A coarse occupancy volume — a low-resolution 3D
grid, or a distance field, published alongside the descriptors — would let an
empty span be crossed in **one** step instead of marched.

Crucially it is a **uniform** reduction of the kind that has actually converted
to time here: rays in a warp are spatially coherent, so neighbouring pixels
traverse the same empty regions and skip them together, unlike the per-sample
grading T149 just measured.

Its ceiling should be measured before it is built, on the discipline this track
has followed throughout: an oracle arm that skips all empty-space steps outright
bounds what any occupancy structure could return.

**T152 — the moving-camera fixture** remains queued and still owed to T098b.

---

## Appendix — evidence

| artefact | path |
|---|---|
| T149 sweep, 7 poses x 7 arms, all guard-confirmed | `run/logs/t149-lighting-lod.log` |
| arms | `T149_LIGHT_CONTRIBUTION`, `T149_LIGHT_DISTANCE`, `T149_LIGHT_GRADED` — diagnostic, default off |
| grading | `lightMarchOpticalDepth` in `cloud_atmosphere_volume.fsh`, driven by `paLodTransmittance` / `paLodDistance01` |
