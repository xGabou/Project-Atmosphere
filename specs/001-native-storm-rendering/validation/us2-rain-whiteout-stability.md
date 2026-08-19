# US2 Rain and Whiteout Stability Validation

> **REOPENED 2026-08-19 - evidence below is superseded.** Rain support, attachment height, camera
> density, and whiteout must now be derived from the final noise-formed storm density rather than
> from the descriptor coverage envelope. This document is retained as history. Task T099 replaces it
> after T098 and T118.


**Date**: 2026-08-17  
**Feature scope**: T031-T041 only  
**Runtime**: Forge 1.20.1 development client, Java, NVIDIA GeForce RTX 4070 Laptop GPU, OpenGL 4.6.0 NVIDIA 596.21  
**Render mode**: native volumetric renderer, ULTRA, 854x480 viewport, 641x360 volumetric target

## Automated gates

The following command completed successfully before the in-game pass:

```powershell
.\gradlew.bat stormVolumetricGeometrySandbox cloudMorphologyTopologySandbox volumetricStabilityDiagnosticsSandbox materialAdvectionSandbox cloudRegionMotionSandbox cloudFieldSandbox architectureBoundaryCheck check build --no-daemon --console=plain
```

This reran the US1 geometry and morphology suites as well as the US2 precipitation, density-agreement, history-validity, and ownership assertions. The build completed with 21 actionable/up-to-date tasks and no test failure.

## Independent 60-second captures

| Scenario | Evidence | Result |
|---|---|---|
| Dry, stationary | `us2-dry-stationary-t0.png`, `us2-dry-stationary-t60.png` | Clear air remained clear. No precipitation or whiteout appeared after the authoritative cloud clear. |
| Local rain, stationary | `us2-local-rain-stationary-t0.png`, `us2-local-rain-stationary-t60.png` | Rain remained under the visible cumulonimbus footprint and became established without a rectangular mask or unsupported shaft. |
| Remote rain, stationary | `us2-remote-rain-stationary-t0.png`, `us2-remote-rain-stationary-t60.png` | The storm and its rain remained visible at distance while the remote camera stayed dry. Rain did not follow the camera or become globally authoritative. |
| Boundary, stationary | `us2-boundary-stationary-t0.png`, `us2-boundary-stationary-t60.png` | The visible footprint and rain edge remained stable while the storm advected. No hard precipitation cutoff, vertical wall, or cloud-cell seam was observed. |
| Boundary crossing, moving | `us2-boundary-moving-t0.png`, `us2-boundary-moving-t30.png`, `us2-boundary-moving-t60.png` | Continuous spectator movement approached and crossed the moving rain boundary without a screen-space discontinuity. |
| Whiteout entry/exit, moving | `us2-whiteout-moving-t0.png`, `us2-whiteout-moving-t10.png`, `us2-whiteout-moving-t20.png`, `us2-whiteout-moving-t60.png` | A vertical crossing moved from visible underside to full in-cloud occlusion, back through rain, and into clear air. The transition remained spatially tied to the storm body. |

All endpoint pairs cover at least 60 seconds. The stricter whiteout sequence additionally records intermediate transition frames.

## Density and history agreement

During the whiteout crossing, the periodic runtime status reported camera density progressing through `0.167 -> 1.000 -> 0.859 -> 0.000`, matching entry into and exit from the visually rendered cloud. Ground-level rain did not force camera density above zero merely because precipitation existed elsewhere.

Temporal history was consumed normally at blend `0.85`. A topology/effective-resolution transition invalidated history for one frame (`historyValid=false`, confidence `0.00`), after which the next periodic sample returned to `historyValid=true`, confidence `1.00`. Normal material advection did not continuously invalidate history.

Fence-gated eight-frame diagnostic runs for local rain, boundary hold, boundary movement, and whiteout movement all reported:

- `invalid=0`
- `premulViolations=0`
- boundary/interior alpha ratios near unity (`0.992`, `1.146`, `0.933`, and `1.012` respectively)
- final raymarch and final depth-guided composite paths active

These results agree with the captures: no grid seam, detached precipitation block, temporal history smear, or abrupt whiteout screen cut was observed.

## Performance note

This pass validates US2 stability, not the later release performance task. The dense local-rain capture averaged about 39 FPS at ULTRA in the small development window, with representative raymarch GPU time around 23 ms. The 60 FPS Ultra target is therefore **not claimed by T041** and remains for the dedicated RTX 4070 optimization/validation work after T041.

## Verdict

US2 independent validation passed for spatial rain ownership, remote clear-air rejection, deterministic visual stability, whiteout density agreement, and bounded temporal-history resets. US1 connected storm geometry remained technically and visually intact throughout the pass.
