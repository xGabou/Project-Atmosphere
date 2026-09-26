# T235 - Recovering the SIDE Cost of T234

**The goal:** recover the ~0.45 ms SIDE loss from removing `PA_ARM_DESC_PRECOMPUTE` (T234) while
keeping the shipped shader's motion stability. Do not simply restore the old precompute; test
small, isolated candidates and validate each on motion and on paired timing.

**The result: FINAL now precomputes only the edge width (`PA_ARM_DESC_PRECOMPUTE_EDGE`); the
ownership radii stay derived in the loop.**

- **Motion.** On the temporal path it moves exactly like T234's FINAL, within the repeat floor
  in every route segment.
- **Timing (paired ABAB).** SIDE 10.13 -> **8.98 ms** (-1.15), PVN 9.06 -> **8.54 ms** (-0.52).
- **Budget.** The whole renderer is ≤ 10 ms, and SIDE is under the 9.5 ms target again.

**On the way, T234's premise turned out to be false.**

- **The confound.** The T207 temporal reconstruction runs only for `LEAN_FINAL`:
  `VolumetricCloudRenderer` requires `program.normalProductionOutput()`. So the monolith and every
  variant T234 compared FINAL with were non-temporal.
- **What it means.** The precompute never affected motion. The FINAL/diagnostic gap is the
  temporal lattice itself.
- **The full restore, briefly committed and reverted.** Restoring the whole precompute was
  measured too (SIDE 10.04 -> 8.90 ms), committed (`f292aa00`) and reverted (`c49e1928`), because
  the goal ruled out simply restoring it.
- **What shipped instead:** the smaller subset the goal's candidate list allows ("retain only the
  subset of operations"). It carries nearly all of the saving.

## 1. The confound in T234

| program | temporal reconstruction on the route? | T234 read it as |
|---|---|---|
| `LEAN_FINAL` (with or without the precompute) | **yes** | "unstable" |
| diagnostic monolith | no (`normalProductionOutput()` false) | "stable reference" |
| `t234_bridge_a/c/d`, `t235_pre_*` variants | no | "stable code shape" |

- **What the fixture asks for.** `StormT152MovingCameraFixture` sets `temporalMode(1)` for these
  arms.
- **What the renderer does.** `VolumetricCloudRenderer` enables the phase lattice only when
  `temporalMode != 0 && profile.temporalEnabled() && program.normalProductionOutput() && ...`.
  For non-FINAL programs the setting was silently inert.
- **The fix for measuring.** To compare a variant with FINAL under motion it has to be put on the
  temporal path (added to `normalProductionOutput()`). T235's harness does that temporarily.

## 2. Motion (T152 route, one storm per run, 2200 frames per arm)

Ratios are flicker / alpha dMean / luma dLuma / ghost against the named reference; the `final_b`
row is the same run's repeat floor.

**The shipped candidate - edge-only precompute on the temporal path** (`t235-route-edge-only-temporal.log`):

| vs current FINAL | APPROACH | ENTRY | INTERIOR | OUTSIDE |
|---|---|---|---|---|
| **edge-only precompute** | 0.998 / 1.001 / 1.002 / 1.02 | 0.999 / 1.000 / 1.001 / 1.00 | 0.990 / 0.992 / 0.924 / 1.03 | **1.008** / 0.997 / 0.998 / 1.00 |
| `final_b` (floor) | 0.997 / 1.001 / 1.001 / 1.01 | 0.999 / 1.000 / 1.001 / 1.00 | 0.992 / 0.993 / 0.919 / 1.02 | 1.064 / 1.040 / 0.998 / 1.04 |

- **Result.** Inside the repeat floor in every segment and every metric: flicker, alpha, luma and
  ghost / disocclusion. The march-step and silhouette behaviour matches the current program's.
- **Route cost.** Cloud GPU mean 6.16 ms -> 5.58 ms; ENTRY 8.03 -> 7.33.

**The full precompute and the no-temporal control** (`t235-route-temporal-path.log`):

| | APPROACH | ENTRY | INTERIOR | OUTSIDE |
|---|---|---|---|---|
| full precompute on the temporal path, vs current FINAL (flicker) | 1.006 | 0.999 | 1.038 | 1.005 |
| repeat floor | 1.007 | 0.997 | 1.037 | 1.063 |
| current FINAL, temporal **off**, vs monolith (flicker) | **1.001** | **1.000** | 1.157 | **1.037** |
| current FINAL (temporal on) vs monolith (flicker) | 1.225 | 1.236 | 1.100 | 1.925 |

- **Neither precompute changes motion.** Turning temporal reconstruction off makes FINAL move like
  the monolith, so the FINAL/diagnostic gap is the T207 lattice.
- **The non-temporal route** (`t235-route-halves-nontemporal.log`) agrees. The full, ownership-
  and edge-only precompute variants, all non-temporal, match the monolith (OUTSIDE 0.998-1.001),
  while the temporal FINAL arms read 2.34× with or without the precompute.

## 3. Performance (paired ABAB, one session: HEAD, candidate, HEAD, candidate; T208 totals, ms)

**Shipped candidate:** HEAD = `c49e1928` (T234's FINAL), candidate = edge-only precompute
(`t235-timing-abab-edge.log`).

| | A1 | B1 | A2 | B2 | HEAD mean | candidate mean | delta |
|---|---|---|---|---|---|---|---|
| SIDE total | 10.00 | 9.12 | 10.26 | 8.85 | 10.13 | **8.98** | **-1.15 (0.887×)** |
| SIDE raw p50 | 7.08 | 6.34 | 7.35 | 6.06 | 7.21 | 6.20 | -1.01 |
| SIDE raw p95 | 7.44 | 6.74 | 7.56 | 6.32 | 7.50 | 6.53 | -0.97 |
| PVN total | 9.10 | 8.83 | 9.03 | 8.26 | 9.06 | **8.54** | -0.52 |

- **The full precompute, for reference** (`t235-timing-abab-full-precompute.log`, HEAD =
  `d8fa530f`): SIDE 10.04 -> 8.90, PVN 9.25 -> 8.14. The edge half keeps essentially all of the
  SIDE saving and about half of the PVN one.
- **Storm load.** These sessions' storm was heavier than T234's: HEAD reads ~10.0-10.1 ms at SIDE
  here against 9.3 there. So the recovered time exceeds T234's +0.45 ms estimate.
- **No driver instability.** The shipped FINAL is byte-identical in NV assembly to the variant
  measured on the route (6,786 instructions, 121 temporaries, 73 `CAL`s).

## 4. Production change

- **The define.** `leanFinalDefines` = `PA_PRECIPITATION_ABSENT`, **`PA_ARM_DESC_PRECOMPUTE_EDGE`**,
  `PA_ARM_NO_RAIN_SEGMENT`, `PA_RAIN_SPLIT_COMPOSITE`, with a note on why.
- **What FINAL does now:** it reads the edge width from texel 3 (T172's CPU value, one ULP from the
  derivation) and still derives the ownership radii per descriptor.
- **The sandbox:**
  - the T172 check requires FINAL to define the edge half, and forbids the combined precompute
    and the ownership half (each would need its own temporal-path motion check);
  - the T206 specialization check lists the combined define as absent;
  - the T206 reference comparison discounts the edge define.
- **Unchanged:** historical programs (T140 oracles, T199/T206 anchors, rain pass, gate build),
  morphology, rain, temporal architecture, light range and descriptor values.
- **Kept from T234:** the T152 route's per-frame `stepScale` / `gpuMs` fields.
- **Temporary harness removed:** campaign, variants, route arms and the `normalProductionOutput`
  override.

## 5. What this leaves open

- **The FINAL/diagnostic motion gap is the T207 temporal lattice itself** (~1.2× at storm edges,
  ~1.9-2.4× outside the storm, against non-temporal rendering). It is an intended architecture
  difference.
- **Any future edge-stability work** belongs to the temporal reconstruction and must compare
  temporal against temporal.
- **The ownership half of the precompute** was not shipped. Its extra saving is small (route
  5.58 -> ~5.57 ms), and the goal ruled out the full restore.

## Validation

- **Gates.** campaignGate PASS (`gate-t235.log`); check build PASS (`check-build-t235.log`);
  stormMorphologySandbox PASS (10 / 0).
- **Markers.** `t235-codeshape-motion.txt` and `t208-temporal-cleanup.txt` retired.
- **git.** `PAchangelog.md` modified and uncommitted; everything else committed on
  `quality/t217-deep-interior-lighting` (not merged).

## Evidence (`t235-campaign/`)

| file | what |
|---|---|
| `t235-route-edge-only-temporal.log` | the shipped candidate on the temporal path vs current FINAL, monolith, repeat |
| `t235-route-temporal-path.log` | full precompute on the temporal path; FINAL non-temporal vs monolith |
| `t235-route-halves-nontemporal.log` | full / owner / edge precompute as non-temporal variants (the confound, reproduced) |
| `t235-timing-abab-edge.log` | the shipped candidate's paired timing |
| `t235-timing-abab-full-precompute.log` | the full precompute's paired timing (not shipped) |
| `t235_route.py`, `t235_harness.py` | analysis, and the temporary campaign/variant installer |
