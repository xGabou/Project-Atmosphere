# T126 Structural-Continuity Fail-First

**Date**: 2026-08-19  
**Fixture**: Live group `3c039aa7`, with ten members and authoritative strengths: BASE
`0.7780/0.8746`, CORE `0.9504/0.9691`, TOWER `0.9138/0.8904`, ANVIL
`0.8222/0.7661/0.8137/0.7950`.

## Command

```powershell
.\gradlew.bat stormStructuralContinuitySandbox --console=plain
```

## Required fail-first result

Before this role-envelope correction, the live-strength fixture failed while retaining the Phase 4S
noise scale, proportional warp, erosion bands, and final-density composition:

```text
T126_METRICS|radiusDerivative=0.0897|areaDerivative=0.1714|
coreTowerRun=88.0000/29.5000|embeddedTowerRun=40.0000/59.0000|
towerAnvilRun=68.0000/26.2500|canopyTower=3.5398/4.2975|
undersideSd=8.1602/8.5227|anisotropyEnvelopeLow=1.4375/1.7143/1.8800
PHASE4S_RESULT|T126 live 3c039aa7 structural continuity|FAILED|
live 3c039aa7 structural continuity failed: anvil/tower span=3.5398 below
geometry-derived 4.2975; low-frequency underside variation=8.1602 below
derived 8.5227
```

The strengthened embedded-width diagnostic also showed only 40 blocks of a required 59-block broad
CORE→TOWER interval. The warped low-frequency aspect was lower than the unwarped control, so the
observed directional stretch was not evidence against the retained proportional warp.

## Role-geometry correction

The storm keeps the same descriptor positions, live densities, base-noise scale, warp, and erosion.
The world-space role envelopes now:

- extend the CORE top by 32 blocks, the TOWER root down by 28 blocks, and the ANVIL root down by
  12 blocks with a 16-block thicker top;
- use broad-root, progressively tapering TOWER and laterally spreading ANVIL profiles;
- use a 0.60 smooth-union fraction only within the CORE/TOWER convective column and among ANVIL
  lobes. BASE pairings retain the audited 0.25 fraction; and
- preserve each measured descriptor strength unchanged. The canopy requirement compares equivalent
  horizontal radii on both sides (rather than comparing an occupied-area radius with a single major
  axis), so its geometry-derived bound measures the intended quantity consistently.

Java, the production shader, and the independent GLSL fixture implement the same rules.

## Correction result

```text
T126_METRICS|radiusDerivative=0.0903|areaDerivative=0.1724|
coreTowerRun=104.0000/29.5000|embeddedTowerRun=104.0000/59.0000|
towerAnvilRun=92.0000/26.2500|canopyTower=6.1330/5.6159|
undersideSd=9.1334/8.5227|anisotropyEnvelopeLow=1.4242/1.7500/1.9200
PHASE4S_RESULT|T126 live 3c039aa7 structural continuity|PASSED|invariant satisfied
```

T126 therefore rejects shelves, narrow role handoffs, detached anvils, an insufficient lateral
canopy, smooth underside balloons, and added low-frequency directional stretch without changing the
noise architecture or weakening a morphology threshold.
