# T124 Macro-Coherence Fail-First

**Date**: 2026-08-19  
**Composition**: Phase 4S envelope → base-noise remap → multi-scale erosion → final density  
**Fixture**: 10 measured live descriptors — BASE `0.7832/0.8792`, CORE `0.9485/1.0000`, TOWER `0.9700/0.9539`, ANVIL `0.8222/0.7231/0.7851/0.7992`.

## Command

```powershell
.\gradlew.bat stormMacroCoherenceSandbox --console=plain
```

## Required fail-first result

The command failed, as required before accepting a morphology correction:

```text
PHASE4S_RESULT|T124 live-calibrated macro coherence|FAILED|
live-calibrated macro coherence failed: base feature=52.5721 blocks below
macro minimum=98.6000 (finger-like re-carving risk)
```

The 98.6-block minimum is 85% of the narrowest live tower's 116-block diameter.
The measured 52.5721-block base feature is therefore too small to act as a primary
billow inside that tower; it re-carves the descriptor envelope into macro fingers.
The assertion is independent of the high-frequency detail bands and must remain
unchanged for the correction run.

## Correction result

After changing the storm base scale to `0.0025`, using the proportional 8%-capped warp in GLSL as
well as Java, and applying the strength-aware fill derivation, the same command passed:

```text
PHASE4S_RESULT|T124 live-calibrated macro coherence|PASSED|invariant satisfied
```

T124 also checks the connected substantial base, role hierarchy, bounded substantial components,
radial excursion, and the macro silhouette after neutralizing only detail-B (the 5.7→1.4-block band).
It therefore rejects a correction that simply transfers macro shape control to high-frequency detail.
