# Native Storm Rendering Baseline

**Captured**: 2026-08-17  
**Scope**: Before T004 production changes

## Commands

```powershell
.\gradlew.bat cloudMorphologyTopologySandbox volumetricStabilityDiagnosticsSandbox architectureBoundaryCheck build --console=plain
```

## Results

- Build: PASS (`BUILD SUCCESSFUL`, 34 seconds)
- `cloudMorphologyTopologySandbox`: PASS
- `volumetricStabilityDiagnosticsSandbox`: PASS
- `materialAdvectionSandbox`: PASS (transitively through `check`)
- `cloudRegionMotionSandbox`: PASS (transitively through `check`)
- `architectureBoundaryCheck`: PASS

## Reproduced Visual Defect

The 2026-08-17 in-game native-renderer capture remains the regression fixture. The severe cloud is represented by the existing storm structure/layer-height/tower map path and shows the reported planar underside, vertical wall/cutoff, disconnected pointed masses, and stippled vertical precipitation bands. This pass addresses the storm body through T030; precipitation-specific correction remains out of scope until T031 and later.

## Ownership and Compatibility Baseline

- Native advanced volumetric rendering is the intended owner when Simple Clouds is absent.
- `ClientCloudRenderOwnership` remains the owner gate before `VolumetricCloudRenderHook` runs.
- Simple Clouds remains the owner when selected by the existing resolver.
- No packet, saved-data, forecast, or compatibility schema is changed by the T001-T030 pass.
