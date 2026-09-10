# T150 storm-visibility guard, and T151 interleaved reconstruction: rejected

**Feature**: `001-native-storm-rendering`
**Tasks**: T150 [PERFORMANCE] banked, T151 [PERFORMANCE] rejected
**Follows**: `performance-post-rank1.md` (T147)
**Date**: 2026-09-03
**Hardware**: NVIDIA GeForce RTX 4070 Laptop GPU, OpenGL 4.6
**Production render path unchanged.**

---

## Part 1 — T150, the storm-visibility guard

### 1.1 Why the existing check was insufficient

`StormT135PerformanceProfile` already rejects a cell whose **descriptor count**
falls mid-sample. Three measurement runs were nonetheless corrupted by a pose
that held its descriptors for the whole sample and rendered an empty sky:

| occasion | pose | what it actually measured |
|---|---|---|
| T142 | `PLAY_NEAR` (4.0r) | the storm's near edge is 2010 blocks out, past the 2000-block `cloudRenderDistance` |
| T138 | resolution ladder | the fixture decayed mid-set; later scales recorded a stormless sky |
| T147 | `PLAY_VIS_NEAR` in two of three runs | 100 % empty-space rejects, **zero** density calls, 12.2 ms |

Every one was found by reading counters afterwards. A benchmark that can
silently measure nothing is worse than a missing cell, because it reaches the
record looking valid.

### 1.2 Design — two halves, cheap then authoritative

**`StormFixtureVisibility.evaluate(...)`** — free, runs before a cell begins:

| condition | test |
|---|---|
| descriptors present | live lobe count > 0 |
| within render distance | distance from the camera to the storm's **cylinder** ≤ `cloudRenderDistance` |
| in frustum | angle to the storm centre, minus its angular radius, ≤ the frame's half-diagonal cone |
| footprint sufficient | angular radius ≥ 2 % of the frame half-height |

**`StormFixtureVisibility.renderedStormConfirmed(...)`** — authoritative, one
counter readback per pose: the march must have produced at least
`0.01 × marchedPixels` cloud density evaluations.

Both are required. Neither substitutes for the other: the corrupted
`PLAY_VIS_NEAR` cells were inside the render distance and inside the frustum
and still marched nothing, while a pose that is obviously out of range should
not cost a GPU readback to reject.

### 1.3 The fail-first sweep found a real modelling error

The first implementation measured range against the storm's **bounding sphere**
and accepted `PLAY_NEAR`:

```
T150 accepted PLAY_NEAR, whose storm is past the cloud render distance:
  withinRenderDistance=true nearestDistance=1920.0 valid=true reason=ok
```

The sphere bulges below the cloud base, and for a camera at y=120 sitting under
the storm that bulge is what decides the test — 1920 blocks and inside the
limit. The **cylinder**, which is the shape the march can actually hit, is 2010
blocks away and outside it. Measuring range against the cylinder is both tighter
and correct:

```
T150_VISIBILITY_GUARD|playNear=beyond_render_distance|playVisNear=ok
  |playNearNearestDistance=2010.1|playVisNearNearestDistance=402.3
```

2010.1 blocks reproduces T142's hand-derived figure exactly. The regression is
retained as `T150_VISIBILITY_GUARD` in `./gradlew check`, and it pins each
condition failing on its own — no descriptors, camera facing away, and a storm
small enough in **both** axes to be a token footprint. (A first attempt at that
last case used a 4-block radius over the full 864-block column height, which the
guard correctly accepted: a thin tall column is not a small footprint.)

### 1.4 Bounded retry, and live validation

On failure the driver logs the reason, respawns and re-resolves the fixture, and
retries up to three attempts; then it abandons the **whole pose** and records no
cell for it rather than an empty-sky one. `CLEAR` is exempt — it expects no
storm.

Live across all seven storm poses:

```
T150_VISIBILITY_CONFIRMED pose=PLAY_VIS_NEAR cloudDensityCalls=2058908 perPixel=15.89
T150_VISIBILITY_CONFIRMED pose=PLAY_VIS_MID  cloudDensityCalls=763833  perPixel=5.89
```

**7 confirmations, 0 rejections** — the guard passes every valid pose without a
false rejection, and `PLAY_VIS_NEAR`, the pose that silently failed twice, is
now proven present before its cells are accepted.

---

## Part 2 — T151, interleaved reconstruction

### 2.1 The ceiling was measured before anything was built

Interleaving reduces **marched pixels per frame** while keeping the logical
output resolution. Its performance ceiling is therefore exactly the cost of
marching that many pixels — which is measurable today, on the shipped ladder,
without writing any interleaving code.

Same fixture, same poses, T150's guard confirming the storm in all seven, logical
resolution fixed at the shipped Ultra:

| pose | 480x270 (129,600 px) | 340x191 (64,940 px = **half**) | **2-phase ceiling** | 240x135 (32,400 px = **quarter**) | **4-phase ceiling** |
|---|---|---|---|---|---|
| PLAY_VIS_NEAR | 100.3 | 74.2 | **1.35x** | 57.8 | **1.74x** |
| PLAY_VIS_MID | 74.9 | 50.4 | 1.49x | 46.5 | 1.61x |
| SIDE | 103.5 | 63.5 | 1.63x | 49.2 | 2.10x |
| FAR | 63.6 | 40.5 | 1.57x | 43.1 | 1.47x |
| ABOVE | 93.4 | 56.1 | 1.67x | 39.2 | 2.38x |
| BELOW | 82.5 | 80.6 | 1.02x | 55.1 | 1.50x |
| NEAR_EDGE (stress) | 206.5 | 126.6 | 1.63x | 84.1 | 2.46x |
| **representative mean** | | | **1.42x** | | **1.67x** |

**Both patterns fail the CASE A bar of >= 1.7x at the representative poses, and
these are ceilings** — they assume the resolve pass, the reprojection, the
disocclusion fallback and the history traffic are all free, and that
reconstruction recovers the missing samples perfectly.

The reason is the scaling exponent T146 measured and T147 confirmed: cost falls
as pixels^0.49–0.75, not linearly. Halving the marched pixels returns about 1.4x,
not 2x. **Interleaving inherits that exponent exactly, because marching half the
pixels is what it does.**

### 2.2 This corrects T147's inferred figure

T147 carried a 2.04x ceiling, inferred from one fixture instance (113.2 ms at
0.250 against 55.4 ms at 0.125). Re-measured on a fresh fixture with T150
confirming storm visibility, the same quarter-pixel ratio is **1.74x** at
PLAY_VIS_NEAR and 1.61x at PLAY_VIS_MID. The 2.04x was optimistic, and it was an
inference from two points rather than a measurement of an implementation — as
T147 itself flagged.

### 2.3 Decision — CASE C, rejected

| | 2-phase | 4-phase |
|---|---|---|
| marched-pixel fraction | 50 % | 25 % |
| **measured representative ceiling** | **1.42x** | **1.67x** |
| CASE A bar (>= 1.7x) | **fails** | **fails** |
| refresh interval per logical pixel | 2 frames | 4 frames |
| temporal exposure | moderate | **maximum** |
| implementation | new resolve target pair, per-phase ray offsets, reprojection, disocclusion | same, worse |

**T151 is rejected before implementation.** Its best case lands *below* the
success threshold with every one of its own costs assumed away, and the pattern
that comes closest — 4-phase — carries the worst temporal exposure available on
a renderer whose sample lattice was deliberately frozen because moving it made
thin silhouette pixels alternate between hit and miss.

### 2.4 The moving-camera precondition was not built, and why

The brief required a moving-camera fixture and a silhouette-stability metric
*before* changing sampling. That precondition exists to **reject** a candidate
that looks good in still frames and flickers in motion. This candidate is
rejected on performance first, so the fixture would have been built to
disqualify something already disqualified.

It should be built when a temporal candidate is actually viable — and it is
still owed to T098b, which has to grade temporal artefacts on the shipped
ladder. That is recorded as T152 rather than dropped.

### 2.5 The equal-pixel-count control answers the deeper question

The brief asked whether interleaving actually beats simply rendering fewer
pixels. The controls above **are** the lower-resolution non-interleaved arms, and
T146's quality frontier already measured what they look like: at 240x135 the
SSIM against today's Ultra is 0.960–0.981 with 5.9–7.4 px of silhouette
displacement, and T098a passes. Interleaving's entire value proposition is
recovering that difference — for which it would have to pay a resolve pass out
of a ceiling that is already below the bar.

---

## 3. Where this leaves the stack

| lever | measured | status |
|---|---|---|
| Rank 1 internal resolution | 4.39x | **shipped** |
| T145 rain locality | 1.15–1.29x | **shipped** |
| **Interleaved reconstruction** | **1.42x / 1.67x ceiling** | **rejected** |
| Lighting + detail LOD | 1.43x PLAY_VIS_NEAR, 1.60x SIDE, 9.13x ABOVE | **next** |
| Descriptor evaluation / fetch | 1.19x / 1.08x ceilings | rejected (T141) |
| Distance culling | 25–92 % but deletes the storm | not adoptable (T098a) |

Representative Ultra is **100.3 ms** against the 8 ms cloud budget — **12.5x
over**. The stress pose is 206.5 ms, **25.8x over**. SC-006 remains not credible
and unrescoped.

**The lighting and detail shares are now the largest remaining measured lever**,
at 1.43–1.60x representative and 9.13x at ABOVE, and unlike interleaving they
carry no temporal exposure and need no new subsystem — only a graded policy over
two uniforms that already exist.

---

## 4. Next recommended task

**T149 — graded lighting and detail LOD.** T147 measured the ceilings by removing
each class outright; a shippable policy cheapens them with distance and camera
relevance instead. It is the only remaining lever with a ceiling above the
interleaving ceiling, and it is far cheaper to build.

**T152 — the moving-camera fixture and silhouette-stability metric**, still owed
to T098b regardless of T151's rejection, and a precondition for any future
temporal candidate.

---

## Appendix — evidence

| artefact | path |
|---|---|
| T150 live validation and the interleaving pixel-count controls | `run/logs/t150-validation.log` |
| guard | `StormFixtureVisibility`, wired into `StormT132AutoDriver`'s `T135_VERIFY` phase |
| regression | `T150_VISIBILITY_GUARD` in `StormVolumetricGeometrySandbox`, run by `./gradlew check` |
