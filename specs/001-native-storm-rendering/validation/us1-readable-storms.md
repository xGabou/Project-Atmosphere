# US1 Readable Native Storm Validation

> **REOPENED 2026-08-19 - evidence below is superseded.** The acceptance criteria this capture set
> was judged against described only the absence of artifacts. A smooth balloon-like storm satisfies
> them. The storm density architecture has been corrected (descriptors bound a coverage envelope;
> the volumetric noise field forms the visible body) and morphology acceptance now requires the
> positive criteria in FR-023 as well as the rejections in FR-024.
>
> This document is retained as history. Task T098 replaces it after T118, using the two-part
> checklist in `tasks.md` and the measured proxy values from
> [morphology-thresholds.md](./morphology-thresholds.md). Do not treat the PASS rows below as
> current evidence.


**Captured**: 2026-08-17  
**Scope**: T030 morphology-regression remediation after T041; T042 and US3 remain unstarted

## Regression and correction

The first T030 acceptance was reopened after runtime review showed identifiable member lobes, a fragmented lower mass, abrupt tower/anvil hand-offs, and an inconsistent distant underside. The corrected path now:

- evaluates every member of each complete selected storm group exactly once;
- uses the candidate texture only to order work, never to gate density;
- derives one continuous group envelope, vertical morphology curve, core concentration, and coherent edge warp from the bounded descriptors;
- prevents the broad severe-weather map from falling back to member geometry while complete descriptor groups are active;
- removes per-member weather-coverage modulation from direct-storm density so the old stamps cannot reappear inside the continuous volume; and
- retains the descriptor-local density evaluator for US2 precipitation attachment while the group evaluator owns cloud and camera density.

No broad weather-map density mask or rectangular storm region was introduced.

## Automated evidence

Final command:

```powershell
.\gradlew.bat cloudFieldSandbox check build --console=plain
```

Result: **PASS** (`BUILD SUCCESSFUL` in 54 seconds). This includes `stormVolumetricGeometrySandbox`, `cloudMorphologyTopologySandbox`, `volumetricStabilityDiagnosticsSandbox`, `materialAdvectionSandbox`, `cloudRegionMotionSandbox`, `architectureBoundaryCheck`, `test`, `check`, `jar`, and `reobfJar`.

The morphology assertions cover continuous BASE-to-TOWER-to-ANVIL support, broad lower carrier, dense center, duplicate-member invariance, complete-group selection, candidate-order independence, conservative bounds, stable retargeting, and CPU/GPU equation fixtures.

## Native runtime evidence

The Forge 1.20.1 client was rebuilt and restarted before final capture. A fresh frozen `cumulonimbus_capillatus` reported `stormDescriptors=11`, `roles[base=2,core=2,tower=2,anvil=5]`, `composited=true`, and a successful runtime shader reload. The captures therefore exercise the descriptor renderer rather than legacy fallback.

| View / condition | Result | Evidence and observation |
|---|---|---|
| Beside | PASS | [beside](us1-morphology-resolved-v3-beside.png) — one broad lower mass, continuous updraft, and progressively spreading upper anvil; no member silhouette or vertical wall. |
| Far away | PASS | [far](us1-morphology-resolved-v3-far.png) — the underside remains connected and no ellipsoid, side bulb, seam, or rectangular cutoff appears at distance. |
| Directly underneath | PASS | [underneath](us1-morphology-resolved-v3-underneath.png) — one continuous curved footprint with coherent detail instead of separate lobe intersections. |
| Inside | PASS | [inside](us1-morphology-resolved-v3-inside.png) — continuous whiteout/occupancy without a role seam or clear-air hole. |
| Above | PASS | [above](us1-morphology-resolved-v3-above.png) — one bounded anvil footprint; no isolated crown descriptors or map-edge cutoff. |
| Lateral movement | PASS | [start](us1-morphology-resolved-v3-lateral-start.png), [moving](us1-morphology-resolved-v3-lateral-moving.png), [end](us1-morphology-resolved-v3-lateral-end.png) — actual lateral player movement retains one silhouette with no role pop, member reveal, or history seam. |

Individual BASE, CORE, TOWER, and ANVIL descriptors can no longer be visually identified as primitives in any required view.

## US2 and ownership regression check

- Rain remains attached through descriptor-local storm density and appears beneath the shared visible base rather than in a detached rectangular region.
- Entering the descriptor storm still produces continuous client whiteout; the final `volumetricStabilityDiagnosticsSandbox` passed local precipitation, clear-air rejection, history invalidation/retention, and ownership assertions.
- Server weather authority, packets, saved data, forecast behavior, and synchronization were not changed.
- Simple Clouds continues to own its managed cloud renderer when present; native descriptors are not uploaded in that ownership mode.
- The legacy renderer fallback remains available when no complete native descriptor group is uploaded.
- GPU creation/upload/adoption remain render-thread only; CPU index generation remains asynchronous.

No T042 or later US3 quality, LOD, governor, performance, or release task was started.
