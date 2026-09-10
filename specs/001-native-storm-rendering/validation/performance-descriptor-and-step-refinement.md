# T167 - Graded step curves, descriptor nearest-K, and early termination

Status: **COMPLETE** 2026-09-04

/ Feature: 001-native-storm-rendering
/ Base commit: `f2d15e0` (T166 banked; production `e4cdda9`)
/ Branch: `experiment/cloud-descriptor-k` - **not merged, not for merge**
/ Hardware: NVIDIA GeForce RTX 4070 Laptop GPU, OpenGL 4.6
/ Framebuffer 1920x1080, Ultra (96 steps), raw scale 0.25, cloud target 480x270

T166 left two questions open and one cheap win unclaimed:

1. its distance-step arm was the fastest thing measured and the only one that
   lost thin material - can a graded curve keep the speed and return the
   material?
2. descriptor geometry is 70-80% of a density call - can a per-sample cap on
   descriptor owners reduce it?
3. a tighter transmittance floor was promising - what is the safest threshold?

Nothing here is productionized. Every arm is default-off and compile-time absent
from the shipped program, asserted by the build gate
`T167 refinement arms compile and stay out of FINAL`.

---

## 1. Headline

**The descriptor experiment fails, and it fails in the most useful way: it is
both slower and worse.** Every K from 1 to 6 is slower than evaluating all
owners, monotonically so, while its seam index rises monotonically as owners are
dropped. This is **CASE D** by the task's decision list, with **CASE C**
symptoms alongside it.

The reason is specific and actionable, and it corrects a reading of T166 that
this campaign was built on: the fixed-work ladder's "shape / profile / SDF /
union" class is **not** dominated by the exact lobe SDF. It is dominated by
**per-descriptor traversal** - the four texel fetches, the role decode, the
ownership ellipse, the edge-softness computation and the conservative bound,
all of which run before any SDF is considered. A nearest-K cap skips only the
SDF and the union blend, so it cannot reach the cost that actually matters, and
the ranking pass it needs costs more than what it saves.

**The graded curves succeed**, and one of them locates where the thin material
was going.

---

## 2. Method

Identical to T166 and reusing its harness wholesale: separately generated lean
programs, one changed thing each, all carrying `PA_PRECIPITATION_ABSENT`;
per-cell fixture qualification at both ends; fixture identity pinned per pose;
the anchor measured twice per pose with the ratio evaluated as an explicit drift
verdict; history disabled across the matrix; and image comparison against the
same-pose anchor captured back to back.

### 2.1 What the nearest-K arm actually does

Ranking is by `stormVerticalDistanceLowerBound` - the same role-aware
conservative bound the shipped T121 rejection already trusts. A lower bound is
the right ordering precisely because it is conservative: a descriptor whose
bound is large provably cannot be close, so the ranking can mis-order only
descriptors that are all plausibly near.

Two properties were held fixed so the arm measures owner-count and nothing else:

- **the march stays safe.** A skipped descriptor still contributes
  `verticalLowerBound - lobeSoftness` to `groupMinClearance`, exactly as the
  T121 reject path does. That value feeds `paSafeAdvance` in the march, so a
  capped set can never let the ray step over material it owns.
- **ownership survives.** The cap is placed after the ownership-ellipse test, so
  `ownsGroup` is still computed over every descriptor and `directStormAvailable`
  cannot flip because of the cap.

**The arm is deliberately a lower bound on what a real structure could do.** It
still fetches all four texels per descriptor and it pays a ranking pass; a real
ownership structure would receive a pre-filtered candidate list and pay neither.
That framing matters for the verdict and is returned to in section 5.

### 2.2 A harness defect, found again

The arrival guard added in T166 - which holds a pose until the camera has
actually reached it - is armed by `t166PoseTargetValid = t166Run`. Wiring T167
through the rest of the T166 machinery did not extend that one assignment, so
the guard was **off** for this campaign and FAR drifted exactly as it did before
(`T167_DRIFT pose=FAR ratio=3.1127 verdict=DRIFTED`), while SIDE and
PLAY_VIS_NEAR came back stable at 1.0072 and 1.0022.

The drift control caught it, again, and no invalid cell reached a conclusion.
The guard is now `t166Run || t167Run` and FAR was re-measured.

**Rule carried forward:** a guard keyed to a campaign flag is a guard that the
next campaign silently loses. The T166 record already said "three existing
guards all passed"; this adds a fourth failure mode - a guard that exists,
works, and is simply not switched on.

---

## 3. Task 2 - descriptor nearest-K: **REJECT (CASE D)**

Anchor-relative, all three poses, from the completed run. Every cell qualified
at both ends; `T167_REJECTED count=0`; all three drift controls stable.

| Arm | FAR | SIDE | PLAY_VIS_NEAR | silhouette IoU | cloud SSIM | thin ret. | **seam index** |
|---|---|---|---|---|---|---|---|
| FULL (`lean_final`) | 1.000x | 1.000x | 1.000x | - | - | - | - |
| K = 1 | **1.005x** | **0.892x** | **0.940x** | 0.905-0.981 | **0.653-0.896** | **0.381-0.492** | **0.0086-0.0379** |
| K = 2 | 0.936x | 0.870x | 0.916x | 0.955-0.993 | 0.801-0.955 | 0.814-0.934 | 0.0045-0.0206 |
| K = 3 | 0.916x | 0.844x | 0.877x | 0.966-0.996 | 0.840-0.970 | 0.889-0.944 | 0.0017-0.0159 |
| K = 4 | 0.851x | 0.809x | 0.818x | 0.985-0.997 | 0.918-0.984 | 0.917-0.983 | 0.0005-0.0095 |
| K = 6 | 0.716x | 0.719x | 0.762x | 0.997-0.999 | 0.985-0.989 | 0.986-0.997 | 0.0004-0.0017 |

**Not one K is faster than evaluating every owner, at any pose.** The ordering is
monotonic and it runs the wrong way: the more owners the cap keeps, the slower it
gets and the better it looks. K = 6 is the slowest arm in the entire campaign
(0.716x) and also the closest to FULL on every quality metric - which is the
signature of pure overhead. The cap is not buying anything; the ranking pass is
simply being paid.

The quality side fails independently. The seam index - which exists to catch the
collapse of the ordered smooth union - rises monotonically as owners are
dropped, reaching **0.0379 at K = 1 / FAR against 0.0023 for the aggressive step
arm, sixteen times higher**. Cloud-region SSIM at K = 1 / FAR is 0.653 and thin
retention 0.403. This is exactly the failure the shader already documents at
`directStormGroupField`: dropping lobes "made blends collapse into visible
primitive intersections".

### 3.1 Why - and this corrects a reading of T166

Production already applies the exact lobe SDF to **88.1% of every lobe the loop
visits** at SIDE and 95.9% at PLAY_VIS_NEAR; T121's conservative rejection takes
only 21.8% and 27.8%. So a K = 1 cap skips roughly **86% of all exact SDF
evaluations** - and is still 8-11% slower than doing all of them.

That bounds the exact SDF at well under half of per-descriptor cost, and it
means the fixed-work ladder's "shape / profile / warp / SDF / union" class -
48.6% of a density call at SIDE - **is not the SDF equation**. It is
per-descriptor traversal: four texel fetches, the role decode, the ownership
ellipse, `stormEdgeWidthBlocksFromData`, and the conservative bound, every one
of which runs before the loop decides whether the SDF is needed at all.

A nearest-K cap cannot reach any of that. It sits inside the loop, so it still
pays the whole prologue for every descriptor and then adds a ranking pass on
top.

### 3.2 What this means for a real binning structure

**Do not build nearest-K. The question a spatial structure has to answer is not
"which owners get the exact SDF" - T121 already answers that, provably and for
free - but "which descriptors enter the loop at all."**

That is an upstream, candidate-map problem: reduce the descriptors a sample
iterates, not the ones it evaluates exactly. This experiment cannot say whether
that is worth building, because it measured the wrong end of the loop; what it
can say is that the end it measured is closed.

One honest caveat kept from section 2.1: this arm still fetches all four texels
per descriptor and pays a ranking pass, so its numbers are a lower bound on what
a structure with a pre-filtered candidate list could return. That caveat does
not rescue the result - K = 6 keeps nearly every owner and is still 0.72x, so
the overhead alone exceeds anything the cap saves.

---

## 4. Task 1 - graded step curves

Curve multipliers, computed from the shipped expressions
(`x = t / MaxRenderDistance`, `g = 1 + 2.2x` is the T166 aggressive arm):

| Curve | x=0.25 | x=0.50 | x=0.75 | x=1.00 |
|---|---|---|---|---|
| production | 1.000 | 1.000 | 1.000 | 1.000 |
| T166 aggressive | 1.550 | 2.100 | 2.650 | 3.200 |
| A late ramp | 1.000 | 1.026 | 1.937 | 3.200 |
| B smooth (x^2) | 1.034 | 1.275 | 1.928 | 3.200 |
| C capped at 1.9 | 1.550 | 1.900 | 1.900 | 1.900 |

### 4.1 Curve D is not a curve - and it cost 27% to find out

The footprint curve was meant to grade by projected pixel size. At this
fixture's field of view its unity-pixel distance lands around 240-480 blocks, so
`clamp(1.0 / footprintPixels, 1.0, g)` **saturates at `g` before the storm
begins** and the curve degenerates into the aggressive arm plus one division per
march step.

The evidence is direct: against the aggressive arm it renders the same picture -
every quality metric agrees to the third decimal or better, and at PLAY_VIS_NEAR
all six agree exactly - while the time differs by **+26.9% at FAR**, -6.0% at
SIDE, -0.2% at PLAY_VIS_NEAR.

| | FAR | SIDE | PLAY_VIS_NEAR |
|---|---|---|---|
| `t166_diststep` | 10.177 ms | 14.332 ms | 11.915 ms |
| `t167_curve_d_footprint` | **12.918 ms** | 13.466 ms | 11.892 ms |
| metrics identical? | 4th-decimal only | 4th-decimal only | **exactly** |

**Two things follow.** The curve is rejected - it grades nothing. And one extra
division inside the march loop costs up to 27% of the frame at FAR, which is a
useful measure of how tight that loop is and a caution for any future work that
adds arithmetic to it.

### 4.2 The curves that do grade

Speedup against the same-pose anchor; quality against the same-pose anchor image.

| Arm | FAR | SIDE | PVN | thin retention (F/S/P) | seam index (worst) |
|---|---|---|---|---|---|
| T166 aggressive | 1.386x | 1.603x | 1.643x | **0.792 / 0.867 / 0.712** | 0.0023 |
| **A late ramp** | 1.477x | 1.060x | 1.055x | **0.931 / 0.969 / 0.932** | 0.0015 |
| B smooth | 1.670x | 1.150x | 1.133x | 0.819 / 0.927 / 0.898 | 0.0016 |
| C capped 1.9 | 1.283x | 1.618x | 1.583x | 0.792 / 0.878 / 0.729 | 0.0017 |
| D scan-lattice fixed | 1.415x | 1.288x | 1.277x | 0.681 / 0.853 / 0.729 | 0.0030 |

**Curve A recovers the thin material the aggressive arm lost** - from
0.712-0.867 up to 0.931-0.969 - at silhouette IoU 0.998-0.999 and the lowest
seam index in the campaign. That is Task 1's stated goal met.

### 4.3 The curves are pose-dependent, and the reason is the grading variable

A is the fastest curve at FAR (1.477x) and nearly the slowest at SIDE (1.060x).
C is the reverse: 1.618x at SIDE, 1.283x at FAR. Neither is a property of the
curve shape - it is where each pose's storm happens to sit in
`t / MaxRenderDistance`.

`MaxRenderDistance` is a fixed 2000 blocks. At SIDE the storm spans roughly
x = 0.24-0.60, below A's 0.45 ramp-in for most of its depth, so A barely acts;
C's 1.9 cap is above `g` over most of that range, so C acts almost like the
aggressive arm. At FAR the storm spans x = 0.54-1.00 and the two swap roles.

**Grading by `t / MaxRenderDistance` couples the curve to the render distance
rather than to the material, so its behaviour swings by pose.** A pose-invariant
curve would have to grade by something the storm defines - projected footprint
being the obvious candidate, which is what curve D was supposed to be before its
constant swallowed it. Fixing that constant is the single most promising
follow-up in this campaign.

---

## 5. Task 3 - early termination

| Threshold | FAR | SIDE | PVN | IoU | cloud SSIM | thin retention |
|---|---|---|---|---|---|---|
| 0.015 (production) | 1.000x | 1.000x | 1.000x | - | - | - |
| 0.030 | 1.042x | 1.069x | 1.063x | not captured | | |
| **0.045** | **1.081x** | **1.118x** | **1.099x** | **1.0000** | 0.990-0.996 | **1.0000** |
| 0.060 (T166) | 1.216x | 1.164x | 1.145x | 1.0000 | 0.983-0.991 | 1.0000 |

0.045 is the recommended threshold: silhouette IoU exactly 1.0000 and thin
retention exactly 1.0000 at all three poses, for 1.08-1.12x. 0.060 is faster
still and its measured quality is also clean; 0.045 is chosen as the
conservative point because it sits a factor of three above production rather
than four, and the gain difference is under 5 percentage points.

---

## 6. Task 4 - combined stacks

Only arms that passed their own measurement were combined. **Nearest-K is absent
from both stacks: there was nothing to combine.**

| Stack | Composition | FAR | SIDE | PVN |
|---|---|---|---|---|
| **SAFE** | curve A + termination 0.045 | **10.371 ms, 1.360x** | **19.692 ms, 1.166x** | **16.942 ms, 1.156x** |
| BALANCED | curve D + fixed scan lattice + termination 0.045 | 13.981 ms, 1.009x | 15.774 ms, 1.456x | 14.406 ms, 1.359x |

Quality, SAFE: silhouette IoU 0.998-0.999, cloud SSIM 0.993-0.996, **thin
retention 0.931-0.969**, seam index 0.002-0.004.
Quality, BALANCED: IoU 0.994-0.998, SSIM 0.980-0.994, thin retention
0.681-0.853, seam index 0.004-0.006.

**SAFE is the recommended stack.** It is the only composition that holds thin
retention above 0.93 at every pose, and BALANCED inherits curve D's degeneracy -
it is worthless at FAR (1.009x) for the same reason curve D is.

### 6.1 Composition, measured not multiplied

| Pose | product of parts | measured stack | composition |
|---|---|---|---|
| FAR | 1.477 x 1.081 = 1.597x | 1.360x | 85.2% |
| SIDE | 1.060 x 1.118 = 1.185x | 1.166x | 98.4% |
| PLAY_VIS_NEAR | 1.055 x 1.099 = 1.159x | 1.156x | 99.7% |

Composition is near-perfect where both arms are small and independent (SIDE,
PLAY_VIS_NEAR) and loses 15% at FAR, where the step curve is doing enough work
that the termination arm has fewer samples left to remove. Consistent with the
T166 finding that these levers overlap where they attack the same quantity.

---

## 7. Targets

| Question | Answer |
|---|---|
| **FAR <= 8 ms?** | **No, but close.** Best measured is curve B at **8.447 ms** (1.670x), 0.45 ms over. Best safe stack is 10.371 ms. |
| **SIDE <= 10 ms?** | **No.** Best measured is curve D / the aggressive arm at **13.466 ms**; the safe stack is 19.692 ms. |
| **SIDE <= 8 ms?** | **No.** Not reachable from anything in this campaign. |

SIDE's anchor moved from 22.083 ms (T166) to 22.970 ms here - a 4% fixture
difference across runs, within the range these campaigns have shown, and it does
not change any verdict.

---

## 8. Verdict and recommendation

| Task | Verdict |
|---|---|
| 1 - graded distance step | **PARTIAL SUCCESS.** Curve A recovers thin retention from 0.71-0.87 to 0.93-0.97 and keeps 1.06-1.48x. Curve D is rejected as degenerate. |
| 2 - descriptor nearest-K | **REJECT - CASE D.** Slower at every K and every pose, and visibly worse. |
| 3 - early termination | **ACCEPT at 0.045.** 1.08-1.12x at IoU and thin retention of exactly 1.0000. |
| 4 - combined | **SAFE stack: 1.156-1.360x** at thin retention 0.93-0.97. |

### 8.1 Is a real descriptor binning / ownership structure worth building?

**Not the one this experiment tested, and not yet on this evidence.** The
measured attribution that motivated it was misread: the ladder's "shape" class
is per-descriptor traversal, not the exact SDF, and T121 already skips the SDF
wherever that is provably safe. A structure that reduces which descriptors a
sample *iterates* remains untested and is the only version still open.

### 8.2 Recommended next implementation

1. **Fix the footprint curve's constant and re-measure it.** It is the only
   pose-invariant grading variable available, the machinery is already built and
   gated, and the current constant is the single reason it degenerates. Compute
   the unity-pixel distance from the live projection rather than assuming it,
   and drop the per-step division - section 4.1 measured that division at up to
   27% of the frame on its own.
2. **Adopt termination 0.045** if anything from this campaign is productionized;
   it is the cleanest result here.
3. **Do not build nearest-K binning.** If descriptor cost is revisited, measure
   the candidate map and the group walk - the traversal - before designing
   anything.
