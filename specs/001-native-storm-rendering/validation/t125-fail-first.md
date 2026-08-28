# T125 Role-Envelope Transition Fail-First

**Date**: 2026-08-19  
**Fixture**: The measured live ten-descriptor composition: BASE `0.7832/0.8792`,
CORE `0.9485/1.0000`, TOWER `0.9700/0.9539`, and ANVIL
`0.8222/0.7231/0.7851/0.7992`.

## Command

```powershell
.\gradlew.bat stormRoleEnvelopeSandbox --console=plain
```

## Required fail-first result

Before the role-geometry correction, the current T124-corrected composition failed
without changing its noise scale, warp, erosion bands, descriptor positions, or
authoritative descriptor strengths:

```text
PHASE4S_RESULT|T125 live-calibrated role-envelope transitions|FAILED|
live-calibrated role-envelope transitions failed:
upper lateral/base radius=0.8475 below 0.900000;
role-transition neck ratio=0.6323 below 0.650000
[core/tower radius=86.3192/53.2614, tower/anvil radius=48.2967/79.5327,
canopy=132.7081/40.5589, upper/base=0.8475, neck=54.5837]
```

The CORE-to-TOWER and TOWER-to-ANVIL substantial-overlap and one-component checks
already passed. The failing measurements therefore isolate the live report to a
narrow lower tower root and insufficient upper lateral anvil coverage, rather than
to a density, noise, or descriptor-strength defect.

## Smallest role-geometry correction

No descriptor was moved, resized, renormalized, or given more density. The role
profiles used by the existing world-space lobe distance fields changed only as
follows, in both `StormLobeEvaluator` and the production fragment shader:

- TOWER lower profile radius: `0.74` → `0.88`; its upper taper remains `0.48`.
- ANVIL joining radius: `0.32` → `0.42`; its broad outer profile endpoint:
  `1.00` → `1.10`.

This makes the tower begin inside the core's envelope and lets the four legitimate
ANVIL descriptors form one tower-fed lateral canopy. Descriptor density remains
coverage authority, so the `0.7231`–`0.8222` ANVIL strengths are not flattened or
compensated by a global density increase.

## Correction result

The same live-strength fixture passes the final invariant:

```text
T125_METRICS|coreTowerOverlap=1.0000|towerAnvilOverlap=1.0000|
canopyTower=3.5314|upperBase=0.9232|neckRatio=0.6671
PHASE4S_RESULT|T125 live-calibrated role-envelope transitions|PASSED|
invariant satisfied
```

T125 asserts connected CORE→TOWER and TOWER→ANVIL cross-sections, substantial
pairwise overlap, canopy span relative to its tower, upper lateral coverage relative
to the lower base, and the absence of a narrow cross-role neck. Its thresholds are
unchanged between the fail-first and correction runs.
