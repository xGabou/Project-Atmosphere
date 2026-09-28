# T241 - Cloud shape polish (storm body + all-type gallery)

Branch `quality/t217-deep-interior-lighting`, from HEAD `7123b30a` (T240). Shape only: no lighting,
motion, rain, performance or diagnostics work. Judged visually, same storm A/B, in neutral lighting
(mode 1), the region view (mode 17) and production lighting. The user's goal widened mid-task from the
storm to **all cloud types**, so the second half builds an all-type gallery and fixes the structural
defects it exposed.

## 1. Storm body - iteration log (one mechanism per step)

Harness: `t241_capture.py` (scratchpad) patches the T098 capture driver with same-storm ABBA arms
(`name, lightMode, layout, geometry, macro, rain, candidate`) over SIDE / LOWSIDE / FAR / LATERAL /
ABOVE / TOWERLOW. Candidates were a monolith-only `PaBodyCandidate` (stripped from lean programs),
cumulative (`>=`) unless rejected.

| step | worst defect seen | mechanism | verdict |
|---|---|---|---|
| C1 | saucer + stem | tower radius from the lower mass's reach | KEEP |
| C2 | base wider than everything | base scaled to the tower's 1.3 Rt foot | KEEP |
| C3 | smooth trunk | 14 % sinusoidal bulges | invisible |
| C4 | " | 28 % sinusoids | REJECT - horizontal groove stripes |
| C5 | " | positive-only rounded lumps | KEEP (modest) |
| C6 | anvil pasted on | anvil underside lowered at the root, 0.4 span | revised - swallowed the tower |
| C7 | " | root drop 0.22 over 1.2 Rt | KEEP |
| C8 | lower mass | straight lower walls | REJECT - mushroom at FAR, chimney at LATERAL |
| C9 | flat oval anvil from above | exact rounded-trapezoid wedge deck | KEEP - big win at SIDE/ABOVE |
| C10 | squat | tower 0.48 of base reach (was 0.62) | KEEP |
| C11 | anvil hollow/fragmented | anvil interior takes the structural fill | KEEP (neutral/slight) |
| C12 | no overshooting top | dome over the updraft | REJECT - invisible under the anvil |
| C13 | anvil a thick V (40 % of height) - martini glass | thin deck (0.38 -> 0.20 span), column into it | KEEP - `t241_i14.png` |
| C14 | hourglass waist above the base | column holds the root width, +0.15 into the deck | KEEP - `t241_i15.png` |
| C15 | smooth cylinder trunk | 0.6-amplitude three-lattice turrets | REJECT - shreds the trunk into shelves (`t241_i16.png`) |

C14 (with C1/C2/C5/C7/C9/C10/C11/C13) reads as a classic cumulonimbus: straight massive trunk, flat thin
anvil cap, wedge downwind from above, tower + streaming anvil laterally. No mushroom, waist, ball or slab.

## 2. Promotion to production

* **Skeleton (CPU)** - `StormBodySkeleton`: the tower radius is `max(0.62 mean tower radius, 0.48 mean
  base reach)` and the base reach is scaled to `1.3 Rt` (clamped 0.5..1.0, asymmetry kept, ownership
  reach from the unscaled base). The deck-disc row is gone: **2 rows per group** (40 floats), descriptor
  texture height 88 -> 80. AABB: column reach 1.95 Rt, deck = the wedge's own extent.
* **Shader** `stormBodyGroupField` and **mirror** `StormBodyEvaluator` (same equations):
  no-waist column `Rt (1.30 + 0.15 smoothstep(0.3, 1.2, t))` rising to 0.25 span below the deck top;
  thin deck `span (0.38 - 0.18 u)`; wedge footprint (exact rounded trapezoid, Rt root, `max(1.1 hw,
  1.2 Rt)` tip, cross drift); root drop 0.22 over 1.2 Rt; every interior takes the structural fill.
* **Lumps -> turrets.** Promoting C5 as written failed the anatomy sandbox: the sinusoidal lumps were
  divided by their *local* gradient, which oscillates with height far from the surface - Lipschitz 51
  (H) / 35 (V) against the published 1.4 / 1.7 that the march's safe advance and the field dilation
  rely on. Replaced by an exact **turret lattice**: spheres 0.42 Rt, 5 around per row, rows 0.8 Rt
  apart, staggered, the nearest four (2 rows x 2 angles) smooth-unioned at 0.12 Rt. The nearest set
  only changes where dropped and added spheres are equidistant, so the minimum is continuous.
  **Lipschitz 1.020 / 1.025**, AABB enclosure misses 0 / 48428.
* Experiment plumbing removed: `PaBodyCandidate`, `PA_BODY_EXPERIMENT`, json/gradle entries, the debug
  config field and the lean-eligibility term. Capture-driver arms restored.

## 3. All cloud types - gallery and structural fixes

`t241_gallery.py` spawns each type just in time 5000 blocks apart (`CloudGroupSpawner.spawnRequestedCloud`,
the same path natural spawns take) and shoots SIDE / UNDER / ABOVE per arm; `t241_gsheet.py` builds
the sheet. First gallery (`t241_g1.png`):

| type | seen | cause | fix |
|---|---|---|---|
| supercell | three smooth dark capsules | family `SPIRAL_STORM` - never reached the storm body | **STORM_ANVIL** (registry, `defaultFor`, `profileFor`, validation case); hurricane stays spiral. Now a full Cb in FINAL (`t241_v1_gallery.png`). |
| stratus / nimbostratus / stratocumulus | cigar chains | `sheetCell` placed members on a **line** | **Vogel sunflower fill** of a wind-oriented 5:3 oval, spacing (1 + gapBias) radii: stratus is one continuous overcast deck from below, stratocumulus a 2-D clump field (`t241_g3.png`) |
| cirrus | specks at cumulus height (~250) | one spawn height for every family | FILAMENT **+480 blocks** (y ~734, near the anvil tops) |

### Measured, not yet fixed (next task)

Non-storm clouds are drawn by the weather-map splat path, and every candidate that tried to billow
their silhouettes (N16-N21, splat S20) was invisible or near-invisible and was removed. Evidence:

* the weather map is **8 blocks per texel** (512 over 4096) and each texel carries its own base/top
  interval - small members become 8-block stair-step capsules;
* the per-member **morphology-category ellipse** gates density, so noise cannot move the outline (a
  x1.2 exaggerated carve only fuzzed edges at ~2 blocks, `t241_g9.png`);
* the baked noise is **low contrast**: base lowFbm p5/p50/p95 = 0.35/0.48/0.62, baseCarrier
  0.71/0.78/0.85, detail FBM 0.35/0.48/0.61 (sd ~0.08) - raw thresholds barely move a boundary; any
  carve must use z-scores. A candidate-99 arm (non-storm density zeroed) proved the edited path live.

Cumulus (direct puffs) and congestus (stage maps) remain smooth potatoes; stratus/stratocumulus
members still show capsule silhouettes from the side. The structural route is an analytic
(non-map) body for the non-storm families, as the storm got in T239/T240.

## 4. Validation

`campaignGate` 72/72 PASSED; `cloudMorphologyTopologySandbox` (incl. T240_BODY_ANATOMY) and
`stormVolumetricGeometrySandbox` pass. Anatomy band re-derived: base aspect 0..0.25 -> 0..0.35 (the
base is now the tower's foot, 0.284, still ~3.5 : 1). Offline NVIDIA compile: monolith, lean FINAL,
rain pass and rain-gate build all compile. Production captures: see section 5.

## 5. Production verification

See `t241_v1_gallery.png` (supercell in lean FINAL) and `t241_v2_storm.png` (storm poses, production).
