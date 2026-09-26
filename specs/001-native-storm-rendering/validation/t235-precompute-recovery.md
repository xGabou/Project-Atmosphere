# T235 - Recovering the SIDE Cost of T234

**The goal:** recover the ~0.45 ms SIDE loss from removing `PA_ARM_DESC_PRECOMPUTE` (T234) while
keeping the shipped shader's motion stability, preferably with a small code-shape fragment that
keeps the "stabilising" compile - not by simply restoring the precompute.

**The result: the premise was false - but per the goal's fallback, production stays as T234
left it, and restoring the precompute is a recommendation awaiting a decision.**

- **No code-shape fragment exists to find**, because the precompute never affected motion.
- **The restore itself was measured:** SIDE 10.04 -> 8.90 ms, PVN 9.25 -> 8.14 ms (paired ABAB),
  with motion identical to the current shipped shader.
- **It was briefly committed** (`f292aa00`) and then reverted, because the goal said not to
  restore the precompute and to keep T234 if no smaller fragment worked.

- **T234's attribution was confounded.** The T207 temporal reconstruction runs only for
  `LEAN_FINAL`: `VolumetricCloudRenderer` requires `program.normalProductionOutput()`, which is
  true for FINAL and the T140 oracles only. The monolith and every campaign variant T234 used as
  a "bridge" rendered non-temporally, whatever the route arm requested.
- **What the "stable" programs really had in common:** they were not temporal, not a shared code
  shape.
- **Measured directly on one route, one storm:**
  - FINAL with the precompute, put on the temporal path, moves exactly like FINAL without it.
  - FINAL without the precompute, taken off the temporal path, moves exactly like the monolith.
- **Consequences:**
  - There is no code-shape fragment to find: the precompute never affected motion.
  - Restoring it does not bring flicker back.
  - The restore would meet every acceptance criterion of the goal: motion at the current level,
    SIDE ≤ 9.5 ms, no PVN regression, no driver instability.
  - It is still not applied, because the goal ruled it out explicitly.

## 1. What T234 compared (the confound)

| program | temporal reconstruction on the route? | T234 read it as |
|---|---|---|
| `LEAN_FINAL` (with or without the precompute) | **yes** | "unstable" |
| diagnostic monolith | no (`normalProductionOutput()` false) | "stable reference" |
| `t234_bridge_a/c/d`, `t235_pre_*` variants | no | "stable code shape" |

`StormT152MovingCameraFixture` sets `temporalMode(1)` for these arms. But
`VolumetricCloudRenderer` enables the phase lattice only when `temporalMode != 0 &&
profile.temporalEnabled() && program.normalProductionOutput() && ...`, so for non-FINAL programs
the setting was silently inert.

## 2. The decisive route (`t235-route-temporal-path.log`)

Arms on one storm, in order: `final` (current FINAL, no precompute), `mono`, `pre_full_temporal`
(FINAL with the precompute, temporarily added to `normalProductionOutput()`), `final_notemporal`
(current FINAL with temporal mode 0), `final_b` (repeat).

Ratios are flicker / alpha dMean / luma dLuma / ghost.

| vs current FINAL | APPROACH | ENTRY | INTERIOR | OUTSIDE |
|---|---|---|---|---|
| `pre_full_temporal` | 1.006 / 0.999 / 1.001 / 0.98 | 0.999 / 1.000 / 1.005 / 1.00 | 1.038 / 1.020 / 1.003 / 1.07 | **1.005** / 0.999 / 1.006 / 0.99 |
| `final_b` (repeat floor) | 1.007 / 1.001 / 1.001 / 0.99 | 0.997 / 0.999 / 1.001 / 1.00 | 1.037 / 1.018 / 0.998 / 1.06 | 1.063 / 1.035 / 1.006 / 1.10 |

| vs monolith | APPROACH | ENTRY | INTERIOR | OUTSIDE |
|---|---|---|---|---|
| current FINAL | 1.225 | 1.236 | 1.100 | 1.925 |
| `final_notemporal` | **1.001** | **1.000** | 1.157 | **1.037** |

- **Motion.** The precompute on the temporal path is inside the repeat floor in every segment;
  ghost is equal; the march-step and silhouette behaviour is the same program's.
- **Where the FINAL/diagnostic gap comes from.** Turning temporal reconstruction off turns
  FINAL's flicker into the monolith's in APPROACH, ENTRY and OUTSIDE. The gap is the T207 lattice
  itself (INTERIOR differs mainly in ghosting, where the monolith's non-temporal image has none
  to reproject).
- **Route cost.** Cloud GPU mean 6.21 ms (FINAL) against 5.61 (with precompute); ENTRY 8.13 ->
  7.39.

The earlier non-temporal route (`t235-route-halves-nontemporal.log`) shows the same thing from
the other side. The full precompute, the ownership-only half and the edge-only half, all run as
non-temporal variants, match the monolith (OUTSIDE 0.998-1.001), while both temporal FINAL arms
read 2.34× - regardless of the precompute.

## 3. Performance (paired ABAB, one session; T208 totals, ms)

| | A1 HEAD | B1 T235 | A2 HEAD | B2 T235 | HEAD mean | T235 mean | delta |
|---|---|---|---|---|---|---|---|
| SIDE total | 10.15 | 8.90 | 9.93 | 8.89 | 10.04 | **8.90** | **-1.14 (0.886×)** |
| SIDE raw p50 | 7.24 | 6.14 | 7.06 | 6.10 | 7.15 | 6.12 | -1.03 |
| SIDE raw p95 | 7.53 | 6.48 | 7.38 | 6.36 | 7.46 | 6.42 | -1.04 |
| PVN total | 9.32 | 7.94 | 9.18 | 8.33 | 9.25 | **8.14** | -1.11 |

- HEAD = `d8fa530f` (T234, no precompute); T235 = the precompute restored.
- **This session's storm was heavier than T234's:** HEAD 10.0 ms at SIDE against 9.3 there. In
  it the precompute is worth more than T234's +0.45 ms estimate.
- **The whole renderer is ≤ 10 ms again with margin:** SIDE 8.9, PVN 7.9-8.3.
- **No driver instability.** The restored program is byte-identical in NV assembly to the one
  measured on the route (6,775 instructions, 121 temporaries).

## 4. Production change: none (T234 kept), and the recommendation

- **What stays.** FINAL stays as T234 left it: no precompute, NV assembly byte-identical to
  T234's.
- **What changed:** only T234's comments in `build.gradle` and the T172 sandbox pin, which stated
  the disproven motion reason. They now point here.
- **Recommendation:** put `PA_ARM_DESC_PRECOMPUTE` back in `leanFinalDefines`. It is the exact
  program measured in section 2 and timed in section 3, recovering ~1.1 ms at SIDE and PVN with
  motion identical to the current shipped shader.
- **Applying it** is one define plus the pre-T234 sandbox pins (`git show f292aa00` has the
  complete change).
- **Kept from T234:** the T152 route's per-frame `stepScale` / `gpuMs` fields.
- **Temporary harness removed:** the T235 campaign, variant programs, route arms and the
  `normalProductionOutput` override.
- **No change** to morphology, rain, temporal architecture, light range or descriptor values.

## 5. What this leaves open

- **The FINAL/diagnostic motion gap is real but intentional.** The T207 temporal lattice
  flickers ~1.2× at storm edges (APPROACH/ENTRY) and ~1.9-2.3× outside the storm compared with
  non-temporal rendering.
- **Judging it** is a question about the temporal reconstruction's edge handling (T207/T208's
  domain), not about shader code shape. Any future work must compare temporal against temporal.
  The monolith is no motion reference for FINAL, because it can never run the lattice.
- **Harness rule, now proven twice:** a route arm's `temporal` field only takes effect for
  `normalProductionOutput()` programs. Variant programs must be added there - as T235 did,
  temporarily - to be compared with FINAL under motion.

## Validation

- **Gates.** campaignGate PASS (`gate-t235.log`); check build PASS (`check-build-t235.log`);
  stormMorphologySandbox PASS (10 / 0).
- **Markers.** `t235-codeshape-motion.txt` and `t208-temporal-cleanup.txt` retired.
- **git.** `PAchangelog.md` modified and uncommitted; everything else committed on
  `quality/t217-deep-interior-lighting` (not merged).

## Evidence (`t235-campaign/`)

| file | what |
|---|---|
| `t235-route-temporal-path.log` | the decisive route: FINAL / monolith / FINAL+precompute on the temporal path / FINAL non-temporal / repeat |
| `t235-route-halves-nontemporal.log` | FINAL / monolith / precompute full, owner, edge (non-temporal variants) / repeat |
| `t235-timing-abab.log` | the paired timing |
| `t235_route.py`, `t235_harness.py` | analysis, and the temporary campaign/variant installer used for both routes |
