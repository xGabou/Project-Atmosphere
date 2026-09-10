# T098a — structural / correctness acceptance on Forge-1.20.1

**Feature**: `001-native-storm-rendering`
**Task**: T098a [BLOCKING CORRECTNESS]
**Date**: 2026-09-01
**Integration**: `Forge-1.20.1` (4e356c3) merged into `worktree-t098-production-ray-trace`
**Result**: **PASS** on every structural criterion

---

## 1. Why this is integration verification, not a replay

T098a requires the correction/evidence chain to be re-run *on the production
branch*, and states explicitly that the historical five-fixture result is
evidence rather than a substitute. That is what this records.

`Forge-1.20.1` had advanced to 4e356c3 (`feat: enhance GPU detection…`) after
the correction work began at c2a2f8e. That commit was merged into the work
branch — clean, one file, `ClientSystemProfile.java` — so the tree validated
here is the production branch head plus the two T098 corrections and their
guards. Every number below was produced by that merged tree, not by the branch
the corrections were developed on.

The corrections under verification:

| commit | correction |
|---|---|
| `29dccf9` | a cloud hit may not publish the composite's 1.0 miss-depth sentinel |
| `da9d58c` | sample the empty coverage envelope without spending a march iteration per sample |

## 2. Static gate

`./gradlew check` and `./gradlew build` on the merged tree: **BUILD SUCCESSFUL**
(16m 27s), 12 invariants reported PASSED, including:

- `T098 cloud hit depth never saturates` — 8330 of 19685 swept probes discarded
  a hit under the pre-fix expression, 0 under the shipped one
- `T098 promotion reaches material within budget` — shipped policy 4/9 rays
  step-capped and 2/9 never reaching material; corrected 0/9 and 0/9, with 0
  false negatives, 0.00-block entry error and 0.00000 alpha error against a
  384-iteration truth arm
- `T098_MARCH_GUARD` per-descriptor conservative advance, 0 false negatives
- T074–T079, T111 production shader compilation, T121/T122 guards, T123
  instrumentation-only, T132 capture freshness and image neutrality, T133
  production-default-unchanged

An earlier invocation failed with a `NoClassDefFoundError` for a class that is
present and compiled on disk; it did not reproduce and is recorded as a Gradle
incremental/parallel race, not a code fault.

## 3. Live campaign on the integrated tree

Severe `cumulonimbus_capillatus`, group `6a229682`, T132 auto-generated world,
ULTRA, 1600x900 capture, movement and daylight frozen. Centre-column measured
between the storm's own projected ANVIL and BASE rows (screen rows 372..527 at
x=799, from the traced target projection).

| pose | centre-column cloud share | longest inner sky run | non-sky fraction |
|---|---|---|---|
| FAR (2.6x radius) | **1.0000** | **0 px** | 0.5354 |
| SIDE (1.7x radius) | **1.0000** | **0 px** | 0.6457 |
| UNDER | **1.0000** | **0 px** | 1.0000 |
| ABOVE | **1.0000** | **0 px** | 1.0000 |
| SIDE CURRENT_ONLY | **1.0000** | **0 px** | 0.6457 |
| LATERAL A (1.9x) | **1.0000** | **0 px** | 0.5841 |
| LATERAL B (90° around) | **1.0000** | **0 px** | 0.5727 |
| NEAR EDGE (1.12x) | **1.0000** | **0 px** | 0.8822 |

Production ray trace on the same fixture and pose, ray identity proved by
reading the production alpha out of the traced texel on an ordinary frame:

| ray | iterations | termination | final alpha | identity |
|---|---|---|---|---|
| WAIST | 72 | transmittance_floor | 0.98730 | **AGREES** |
| BASE | 39 | transmittance_floor | 0.98584 | **AGREES** |
| ANVIL | 38 | transmittance_floor | 0.98682 | **AGREES** |

Severe scale: the storm column spans screen rows 60..899, **840 px of 900**, at
the SIDE acceptance pose.

## 4. Criterion-by-criterion

| T098a criterion | verdict | evidence |
|---|---|---|
| intended-distance visibility | **PASS** | storm present and connected at FAR 2.6x, LATERAL 1.9x, SIDE 1.7x, NEAR EDGE 1.12x |
| connected BASE → CORE → TOWER → ANVIL coverage | **PASS** | centre-column share 1.0000 at every pose |
| no renderer-caused clean-sky waist | **PASS** | longest inner sky run 0 px at every pose |
| no march starvation | **PASS** | all three traced rays terminate on the transmittance floor; zero step caps |
| real cloud hits surviving depth publication / composite | **PASS** | traced alpha equals the production texel alpha to five decimals on all three rays; waist composites at 0.98730 |
| no catastrophic confetti / skipping | **PASS** | canopy and base are continuous masses; no detached lobes, no shredded skirt |
| preserved basic severe scale | **PASS** | 840 px of 900 at SIDE; base, neck and anvil all present |

## 5. Verdict

**T098a PASSES on `Forge-1.20.1`.** The structural correctness gate is met on
the production branch, with both corrections and all four guards in place.

This does not grade appearance. The anvil's canopy still reads smoother than
FR-023 wants and the ~4-pixel reconstruction beat is still present; both are
T098b's, which by its own definition does not block performance work or T099.

**T099 is unblocked by this result** (it depends on T098a, T115, T116, T118 —
this discharges the T098a dependency only).
