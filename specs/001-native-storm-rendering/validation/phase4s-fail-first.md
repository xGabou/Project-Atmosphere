# Phase 4S Fail-First Record

**Date**: 2026-08-19
**Task**: T107
**Scope**: T101-T106 evaluated against the audited pre-correction density composition.

## How this is reproduced

The audited composition is not archived only in version control. `StormFieldSampler` carries it as
`Composition.AUDITED_PHASE_4R` — the descriptor union used directly as the visible body, with detail
erosion gated by `edgeExposure = 1 - smoothstep(0.26, 0.72, cloud)` and clamped to the `0.42` storm
erosion floor, and the base noise field not consulted at all. The evidence below can therefore be
regenerated at any time:

```powershell
.\gradlew.bat stormMorphologySandbox -Pphase4s.failFirst=true --console=plain
```

The corrected run is the default:

```powershell
.\gradlew.bat stormMorphologySandbox --console=plain
```

Both runs execute the **same invariant methods** with the same thresholds. Nothing is weakened for
the corrected run.

## Result: 4 of 6 invariants fail against the audited composition

| Invariant | Audited | Measured failure |
|---|---|---|
| T101 interior noise influence | **FAILED** | `0.0%` of 1573 unsaturated interior samples responded to the base noise field; weakest delta `0.0000` |
| T102 occupied-region density variance | **FAILED** | weakest region SD `0.0555` against the derived minimum `0.1148`, at (-43, 250, -3) |
| T103 multi-scale spectral contribution | passed | see below |
| T104 geometric distance field | passed | see below |
| T105 positive storm structure | **FAILED** | no dense convective core: core mean `0.9003` does not exceed base mean `0.9098` |
| T106 rejected morphology forms | **FAILED** | base silhouette wanders `4.54` blocks and core `2.32` blocks against the derived minimum `5.68`; storm underside varies `0.74` blocks across its footprint |

### T101 is the decisive one

The interior response is not merely weak, it is **exactly zero** — 0.0% of samples, weakest delta
`0.0000`. This is the predicted consequence of the audited gate rather than a marginal miss: a
properly covered storm interior sits above `cloud = 0.72`, where `edgeExposure` reaches zero and the
detail term drops out of the expression entirely. The base noise field never entered the audited
composition at all.

### T105 and T106 describe the balloon

Under the audited composition the core mean (`0.9003`) and the base mean (`0.9098`) are
indistinguishable: the storm is a near-uniform mass at ~0.9 density throughout, with no convective
core. Its silhouette, after removing the best-fit ellipse, wanders only 2.3–4.5 blocks, and its
underside varies by 0.74 blocks across the entire footprint — a flat slab under a smooth balloon.

Every one of these frames would have passed the previous acceptance criteria, which is precisely
why this correction exists.

## The two invariants the audited path satisfies, and why that is expected

**T104 geometric distance field** passes in both modes. This is correct and not a weakness: the
per-lobe distance field, its monotonicity, its world-space scaling, and the no-skip union rule are
properties of `StormLobeEvaluator`, which the `AUDITED_PHASE_4R` switch does not revert. The switch
reproduces the audited *composition* (stages 4–6), not the audited distance field. The
density-space pseudo-distance it replaced is separately covered by the independent GLSL parity
fixture, which compares a real signed distance in blocks between Java and GLSL — a comparison the
old `1 - lobeDensity` form could not even express.

**T103 multi-scale spectral contribution** passes in both modes because it measures the *relative*
share of each detail octave, not the absolute amount reaching the result. Under the audited
composition all three octaves are attenuated together by the same near-zero exposure factor, so
their ratios survive even though their magnitudes collapse. The absolute collapse is what T101 and
T102 measure, and both fail decisively. T103 is retained as a band-balance check rather than a
presence check, and its limitation is recorded here rather than papered over by tightening it into
a duplicate of T101.

## Gate

T101–T106 fail meaningfully against the audited implementation for the intended defects. Phase 4S
production changes were therefore permitted to proceed.
