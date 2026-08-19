# Phase 4R Automated Revalidation

> **PARTIALLY SUPERSEDED 2026-08-19.** The results below remain a valid record of the Phase 4R
> correction: the descriptor set, not a statistical envelope or the candidate grid, is the evaluated
> storm field. What they do not establish is the Phase 4S requirement that descriptors bound only a
> coverage envelope while the volumetric noise field forms the visible body, that lobe unions run on
> a real world-space geometric distance field, and that morphology meets positive measurable
> criteria. Task T118 records the Phase 4S evidence in `phase4s-automated.md`.
>
> Retained as valid: T074-T080, T082-T084, T087-T088, T091-T096. Superseded: the T081, T085, T086,
> T089, T090, and T097 acceptance criteria.


**Date**: 2026-08-19  
**Scope**: T097 corrected-gate validation for T074-T096.  
**Verdict**: PASS — all Phase 4R automated invariants and required Gradle gates passed.

## Commands

```powershell
.\gradlew.bat testClasses stormVolumetricGeometrySandbox cloudMorphologyTopologySandbox volumetricStabilityDiagnosticsSandbox architectureBoundaryCheck check build --console=plain -q
```

Result: `BUILD SUCCESSFUL` (exit code 0). This runs `testClasses`, both corrected Phase 4R aggregate harnesses, the existing morphology topology sandbox, `architectureBoundaryCheck`, `check`, and `build`.

The normal sandbox entry points now use a corrected-pass aggregate: they invoke the *same invariant methods* as the historical fail-first collector and fail on an invariant failure. The original collector is retained behind `-Dphase4r.failFirst=true` for audit reproduction. No invariant threshold or assertion was weakened.

## Corrected regression results

| Historical T080 defect | Status | Closure evidence |
|---|---|---|
| Statistical envelope lost the narrow tower / silhouette structure | Closed | `T074 storm silhouette` passed: narrow tower, wider anvil, one component, bounded radius change, and no vertical step. |
| Unrelated descriptors changed local density | Closed | `T075 descriptor locality` passed for add, move, and removal outside support. |
| Java and independently executed GLSL disagreed on union, underside, and boundary cases | Closed | `T076 independent GLSL parity` passed for all role, lobe-union, group-union, underside, and boundary vectors. |
| Group ellipse filled unsupported gaps instead of a descriptor smooth union | Closed | `T077 descriptor smooth-union composition` passed. |
| Rendered body and rain support/attachment came from different fields | Closed | `T078 rain and rendered-body agreement` passed. |
| Counted zero descriptor slots decoded as real group-0 BASE lobes | Closed | `T079 descriptor slot validity` passed. |
| Incomplete or omitted groups disappeared instead of using fallback | Closed | `T079 incomplete group fallback` passed. |
| Rejected completed build left the requested signature stale | Closed | `T079 rejected async build re-request` passed. |
| Macro/LOD cells churned storm grid and topology signatures | Closed | `T079 cluster-only signatures` passed. |
| History key collapsed independent lifecycle generations | Closed | `T079 independent lifecycle generations` passed. |
| Resource lifecycle left a same-frame stale-history window | Closed | `T079 same-frame history invalidation` passed. |
| Candidate capacity stored duplicate member/role entries rather than every group | Closed | `T079 candidate group witness coverage` passed. |
| Candidate/index coverage could define direct-storm ownership or fallback | Closed | `T079 candidate non-authority` passed. |
| Segment coverage scanned every descriptor instead of bounded groups | Closed | `T079 bounded per-group intersection` passed. |
| `shaftDensity()` passed local precipitation in the maximum-precipitation position | Closed | `T079 shaftDensity maxPrecipitation argument` passed. |

## Existing US1 and US2 sandbox coverage

- `stormVolumetricGeometrySandbox`: passed, including T074-T077 and descriptor/fallback/async/signature/candidate regressions.
- `cloudMorphologyTopologySandbox`: passed, including connected structured tower and PUFF topology checks.
- `volumetricStabilityDiagnosticsSandbox`: passed, including T078 rain/body agreement and lifecycle/history/precipitation regressions.
- `architectureBoundaryCheck`, `check`, and `build`: passed.

This is an automated gate only. T098/T099 remain subject to fresh live visual validation; no performance result is claimed here.
