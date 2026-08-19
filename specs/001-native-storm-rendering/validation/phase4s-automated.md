# Phase 4S Automated Revalidation

**Date**: 2026-08-19
**Task**: T118
**Scope**: Corrected-gate validation for T100-T117, plus the retained Phase 4R regressions.
**Verdict**: PASS — every Phase 4S and Phase 4R invariant passes, with no assertion weakened and no
threshold retuned outside its recorded derivation.

## Command

```powershell
.\gradlew.bat check build --console=plain
```

Result: `BUILD SUCCESSFUL`. This runs `stormDensityThresholdSandbox`, `stormMorphologySandbox`,
`stormVolumetricGeometrySandbox`, `volumetricStabilityDiagnosticsSandbox`,
`cloudMorphologyTopologySandbox`, `materialAdvectionSandbox`, `cloudRegionMotionSandbox`,
`architectureBoundaryCheck`, `test`, `check`, `jar` and `reobfJar`.

## Phase 4S results

| Check | Result | Evidence |
|---|---|---|
| T100 measured noise thresholds | PASSED | Every recorded statistic and every derived constant reproduced from a fresh bake |
| T101 interior noise influence | PASSED | ≥95% of unsaturated interior samples respond to both the base and the detail field at ≥half the analytic derivative |
| T102 occupied-region density variance | PASSED | Every occupied region ≥68.2 blocks per edge meets the derived minimum SD of 0.1148 |
| T103 multi-scale spectral contribution | PASSED | All three detail octaves retain ≥half their measured variance share |
| T104 geometric distance field | PASSED | Finite, monotonic, correctly signed, world-scaled at ~1 block per block above the cap; no lobe dropped for zero local density |
| T105 positive storm structure | PASSED | Broad continuous base, denser core than base, tower narrower than base, anvil wider than tower, no empty vertical band, progressive narrowing |
| T106 rejected morphology forms | PASSED | No balloon silhouette, no uniform region, no isolated substantial component, no flat slab underside |
| T117 envelope-side LOD cross-fade | PASSED | The analytic LOD weight scales the coverage envelope monotonically and reaches zero coverage, so the broad map can take ownership |

## Retained Phase 4R results

All twelve Phase 4R invariants continue to pass against the corrected architecture, including the
ones that most directly protect already-validated behavior:

| Check | Result |
|---|---|
| T074 storm silhouette | PASSED |
| T075 descriptor locality | PASSED |
| T076 independent GLSL parity | PASSED |
| T077 descriptor smooth-union composition | PASSED |
| T078 rain and rendered-body agreement | PASSED |
| T079 descriptor slot validity | PASSED |
| T079 incomplete group fallback | PASSED |
| T079 rejected async build re-request | PASSED |
| T079 cluster-only signatures | PASSED |
| T079 independent lifecycle generations | PASSED |
| T079 same-frame history invalidation | PASSED |
| T079 `shaftDensity` maxPrecipitation argument | PASSED |
| T079 candidate group witness coverage / non-authority | PASSED |
| T079 bounded per-group intersection | PASSED |

## New check added during this phase

**T111 production storm shader compiles.** The independent parity fixture is a separate GLSL
program, so it could never catch a syntax or type error elsewhere in the production shader. Phase 4S
rewrote a large part of that shader, and without this check a break would only surface as
"clouds disappeared" at runtime. The check resolves `#moj_import` the way Minecraft's loader does
and compiles the real fragment shader in a hidden GL context. It passes.

## Closure of the T107 fail-first defects

| T107 defect | Status | Closure evidence |
|---|---|---|
| Storm interior received zero response from the base noise field (0.0% of 1573 samples) | Closed | T101 passes: ≥95% of interior samples respond to both fields |
| Occupied regions were visually uniform (SD 0.0555 against 0.1148) | Closed | T102 passes for every sampled region |
| No dense convective core (core mean 0.9003 vs base mean 0.9098) | Closed | T105 passes: core mean strictly exceeds body mean |
| Balloon silhouette (2.3–4.5 block wander against 5.68) and flat slab underside (0.74 blocks) | Closed | T106 passes: silhouette wander and underside variation both exceed the derived minimum |

No assertion was relaxed to close these. The corrected and fail-first runs execute the same
invariant methods with the same thresholds; only `StormFieldSampler.Composition` differs.

## Two assertions were changed, and why

Both are recorded here rather than left implicit.

1. **T079 bounded per-group intersection** used `function.contains("MAX_STORM_GROUPS")` as its proxy
   for "works per admitted group". T120 replaced the `bool[MAX_STORM_GROUPS]` visitation array with
   a compact integer bit mask, which is the same bounded work in a GPU-friendlier form, so the proxy
   became stale. It now requires the group-visitation state and the bounded per-group call, and the
   prohibition on scanning every descriptor is unchanged.

2. **Component counting in T105/T106** counts only components carrying at least 10% of a section's
   occupied area. A noise-formed cloud legitimately sheds small detached wisps at its boundary —
   that is the multi-scale detail FR-023 requires — and counting them would penalize the very thing
   being validated. What FR-024 rejects is an *isolated ear or bulb*: a substantial mass standing
   apart from the body. The threshold is a share of section area, not an absolute cell count, and
   the audited composition still fails T106 on the silhouette and underside measures.

## Preserved behavior

No change was made to server-authoritative weather, forecast behavior, network packets, saved
weather state, Simple Clouds ownership, legacy renderer fallback, rain placement ownership, whiteout
ownership, history invalidation semantics, or the candidate texture's role as a scheduling and index
hint. The regressions covering rain/body agreement, lifecycle generations, same-frame history
invalidation, the precipitation argument order, and candidate non-authority all still pass.

## Risk carried into T098

One item cannot be settled without a live run and is flagged rather than assumed:

**Descriptor envelope strength depends on `cell.density()`.** The coverage envelope is scaled by the
descriptor's `density * detailWeight`, so a storm whose authoritative cell density is low will
render as a sparser, wispier body — correct behaviour in principle, since descriptor density is the
authoritative measure of how much cloud there is. What the sandbox cannot confirm is the *range* of
`cell.density()` that the live severe-storm generator actually produces. If real values sit well
below the 0.92 used in the fixture, live storms will be thinner than the deterministic checks
suggest. T098 must record the observed `cell.density()` range alongside its captures. This is a
calibration question about the authoritative input, not a defect in the composition.

## Gate

T118 passes. The reopened T098 and T099 are unblocked, and must now be judged against the revised
two-part positive/negative morphology checklist.
