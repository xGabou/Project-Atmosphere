# Quickstart: Validate Native Storm Rendering

**Feature**: `001-native-storm-rendering`  
**Purpose**: implementation and review runbook; commands assume PowerShell at the repository root

## Prerequisites

- Java 17 selected for Gradle.
- A Forge 1.20.1 client development environment that already launches Project Atmosphere.
- The reference Ultra performance run uses the plugged-in RTX 4070 laptop, 1920 by 1080, no external shader pack, and approximately 2000-block cloud render distance.
- Do not enable the optional Simple Clouds runtime for the native visual validation sections.

## Fast Automated Validation

Run the smallest feature checks first:

```powershell
.\gradlew.bat stormVolumetricGeometrySandbox
.\gradlew.bat cloudMorphologyTopologySandbox
.\gradlew.bat volumetricStabilityDiagnosticsSandbox
```

Then run all project checks and the build:

```powershell
.\gradlew.bat check
.\gradlew.bat build
```

Expected results:

- storm role ordering/connectivity and retarget continuity pass;
- descriptor/index packing, complete-group capacity, candidate overflow, evaluator continuity, LOD transition, and cache-generation checks pass;
- existing material advection, cloud motion, architecture, and stability checks remain green;
- no client rendering class leaks into dedicated-server code.

## Native Client Validation

Launch without the optional Simple Clouds runtime:

```powershell
.\gradlew.bat runClient
```

In game, confirm ownership and compact workload:

```text
/pa system cloudStatus
/pa cloud volumetric status
/pa cloud volumetric diagnostics
/pa cloud volumetric diagnostics storm
```

The owner must be Project Atmosphere native, direct storm rendering must be active for eligible nearby severe groups, and the detailed capture must show complete selected groups with no unexpected fallback.

## Geometry Review Route

> **Revised 2026-08-19.** Morphology review has two halves and both must pass. Absence of artifacts
> alone is not acceptance - that is what allowed a smooth balloon-shaped storm through the previous
> gate.
>
> **Positive (FR-023) - all nine must be visibly present:**
>
> 1. broad continuous lower cloud base
> 2. dense convective/core region
> 3. vertical tower development emerging naturally from the base
> 4. progressive vertical narrowing where appropriate
> 5. broad upper anvil
> 6. multi-scale billowing across the visible storm body
> 7. surface variation at multiple spatial frequencies
> 8. irregular but coherent silhouette curvature
> 9. continuous transitions between base, tower, core, and anvil
>
> **Negative (FR-024) - none may be present:**
>
> large smooth balloon surfaces; large regions of visually uniform density; visible ellipsoid or
> sphere primitives; isolated ears or bulb protrusions; descriptor seams; rectangular or vertical
> walls; flat slabs; uniformly smooth silhouettes.
>
> Record the measured proxy values from `validation/morphology-thresholds.md` alongside each
> capture.

Create or locate a severe `STORM_ANVIL` group, then review from below, beside, inside, and above. Repeat while stationary and while crossing the structure.

Use these views to isolate failures:

```text
/pa cloud volumetric debug view storm_body
/pa cloud volumetric debug view storm_envelope
/pa cloud volumetric debug view storm_candidates
/pa cloud volumetric debug view final
```

Acceptance checks:

- BASE, CORE, TOWER, and ANVIL form one readable connected structure.
- The tower narrows/rises and the anvil extends beyond it along wind.
- No full-height vertical wall, planar slab, hard role boundary, disconnected anvil, or abrupt generic-height clipping appears.
- Overlapping same-group members do not show winner-switch seams.
- Movement, growth, decay, and retargeting remain continuous.
- Candidate overflow, if intentionally forced, retains complete groups and reports the affected tiles/groups.

## Render-Distance and LOD Review

Set the overall cloud distance and storm detail distance to the intended test values in the Forge config, then move through:

1. full analytic range;
2. the final 128-block analytic/map transition band;
3. map-only severe-cloud range;
4. total cloud render-distance boundary.

Use `storm_envelope`, `storm_body`, and `final` views. There must be no hole, double-density flash, disconnected formation, hard pop, vertical wall, or abrupt precipitation cutoff. `/pa cloud volumetric diagnostics storm` must report the same effective distances and group LOD counts observed in the scene.

## Rain and Whiteout Review

Exercise dry local weather, nearby rain, a remote raining storm with clear air around the camera, entry into rain, entry into the cloud, exit, and overlapping rainy storms.

```text
/pa cloud volumetric debug view precipitation
/pa cloud volumetric debug view storm_combined
/pa cloud volumetric debug view final
```

Acceptance checks:

- Rain shafts occur only below locally raining support.
- A distant raining field does not create local shafts or disable empty-space behavior everywhere.
- Shafts are stable in world space and do not become persistent dotted vertical screen bands.
- Camera density/whiteout rises and falls at the same visible cloud boundary as the adopted GPU structure.
- Existing nearby custom rain/snow quads and vanilla fallback still operate independently.

## Quality and Adaptive Review

Exercise every existing mode:

```text
/pa cloud render quality low
/pa cloud render quality low_24
/pa cloud render quality medium
/pa cloud render quality high
/pa cloud render quality ultra
```

For each mode, repeat a short severe-storm route and inspect diagnostics. Confirm no role disappears because of quality mode and that configured mode, effective band, steps, and resolution agree.

For adaptive behavior:

1. Enable `adaptiveCloudQuality`.
2. Apply sustained GPU load long enough to cross the over-budget threshold.
3. Remove the load and observe sustained recovery.
4. Run `/pa cloud volumetric debug governor reset` when a clean baseline is required.

The governor must lower only after 30 consecutive over-target frames, recover only after 180 frames below 80% of target, respect the per-mode floor, and perform no more than one transition in any 30-second stable interval. A resolution transition may reset history once; normal descriptor interpolation must not.

## Ultra Performance Gate

On the defined RTX 4070 laptop environment:

1. Plug in AC power and use the normal high-performance GPU profile.
2. Set 1920 by 1080, no external shader pack, `cloudRenderDistance=2000`, quality Ultra, and adaptive quality enabled.
3. Use the specification's representative severe-storm route.
4. Allow adaptive state and shader/resource warmup to converge.
5. Capture ten continuous minutes with existing volumetric diagnostics/telemetry.

Pass conditions:

- at least 60 FPS target after convergence;
- p95 total frame time at most 16.7 ms;
- no persistent temporal artifact or visual acceptance failure;
- at most one adaptive transition per 30-second stable interval;
- stable scenes show cache hits rather than recurring grid rebuild/upload;
- no unbounded worker backlog, repeated stale discard, or tile/group overflow hidden from diagnostics.

## Simple Clouds Compatibility

Stop the native client and launch the optional runtime explicitly:

```powershell
.\gradlew.bat runClient -PenableSimpleCloudsRuntime=true
```

Validate:

- existing ownership policy selects Simple Clouds where supported;
- Simple Clouds continues managing and rendering its own systems;
- Project Atmosphere creates no active native storm build/upload workload while Simple Clouds owns clouds;
- existing PA-to-Simple-Clouds weather, tornado, hurricane, and compatibility behavior is unchanged;
- optional integration failure is reported without Project Atmosphere taking Simple Clouds ownership.

## Dedicated Server and Failure Paths

Launch the existing dedicated-server development task:

```powershell
.\gradlew.bat runServer
```

Confirm startup and weather simulation without client-class loading errors.

In the client, also validate resource reload, resize, dimension change, disconnect/reconnect, owner transition, intentionally unavailable direct-storm texture/resources, missing membership, and forced capacity/overflow cases. Expected behavior is last-valid generation or broad native map LOD for direct-path failures, followed by the existing legacy-field/vanilla fallback only for a wider native pipeline failure.

## Review Evidence to Retain

- Output from the three targeted sandboxes, `check`, and `build`.
- One detailed storm diagnostic capture per quality mode.
- Below/beside/inside/above screenshots or short captures for body, candidates/envelope, precipitation, combined, and final views.
- Ownership status from native and Simple Clouds launches.
- The ten-minute Ultra timing capture with p95 total frame time and rebuild/adaptive counters.
- A note confirming packet/save/forecast behavior was not changed.
