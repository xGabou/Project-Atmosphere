# Project Atmosphere Phase 4 Verification + Auto-Fix Report

Date: 2026-06-15

Scope: Phase 4 verification with immediate in-scope fixes.

No tornado, hurricane, blizzard, rendering, shader, cloud morphology, precipitation rendering, Distant Horizons, audio, lightning, or hail work was started.

## Files Changed During This Auto-Fix Pass

`src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericSupportEvaluator.java`

Added hysteresis constants derived from existing rain, thunder, and severe thresholds.

`src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellState.java`

Added persisted `SevereEvolutionScore`.

`src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellLifecycleController.java`

Fixed staged evolution, severe-support ownership, and hysteresis-based weakening.

`src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellFormationController.java`

Fixed fairness candidate collection so sorting occurs before truncation.

`src/main/java/net/Gabou/projectatmosphere/clouds/simulation/CloudRegionAtmosphereFeedbackController.java`

Fixed native cloud feedback accumulation and per-region aggregation.

## Issues Found And Fixed

### Issue 1: Evolution Could Bypass Intermediate Stages

Issue:

`RAIN_CELL` could become `SUPERCELL` directly if `EvolutionScore >= WEATHER_SEVERE_THRESHOLD`.

Root Cause:

`WeatherCellLifecycleController.updateTypeFromSupport` evaluated thresholds globally instead of by current type:

High score set `SUPERCELL`.

Medium score set `THUNDERSTORM`.

Otherwise set `RAIN_CELL`.

That allowed bypassing `THUNDERSTORM`.

Fix Applied:

Changed type transitions to staged logic:

`RAIN_CELL -> THUNDERSTORM` only.

`THUNDERSTORM -> SUPERCELL` only.

`SUPERCELL -> THUNDERSTORM` only.

`THUNDERSTORM -> RAIN_CELL` only.

Verification:

Post-fix scan shows `WeatherCellLifecycleController` now switches on the current type before changing type.

`RAIN_CELL` only calls `setType(THUNDERSTORM)`.

`THUNDERSTORM` can call `setType(SUPERCELL)` or `setType(RAIN_CELL)`.

`SUPERCELL` can call `setType(THUNDERSTORM)`.

`compileJava` passed.

`build` passed.

### Issue 2: Evolution Could Oscillate Around Thresholds

Issue:

Promotion and downgrade used the same effective threshold boundaries. A storm hovering around `0.42` or `0.70` could flip types repeatedly.

Root Cause:

Phase 4 used existing weather phase thresholds directly for all type changes and did not include hysteresis.

Fix Applied:

Added hysteresis thresholds to `AtmosphericSupportEvaluator`:

`WEATHER_THUNDER_WEAKEN_THRESHOLD = (WEATHER_RAIN_THRESHOLD + WEATHER_THUNDER_THRESHOLD) * 0.5F`

`WEATHER_SUPERCELL_WEAKEN_THRESHOLD = (WEATHER_THUNDER_THRESHOLD + WEATHER_SEVERE_THRESHOLD) * 0.5F`

Promotion still uses existing thresholds:

Thunder promotion: `0.42`.

Supercell promotion: `0.70`.

Weakening now uses midpoint thresholds:

Thunderstorm weakens below `0.30`.

Supercell weakens below `0.56`.

Verification:

Post-fix transition scan found the weaken thresholds used in `WeatherCellLifecycleController`.

Transitions are now separated by hysteresis bands.

`compileJava` passed.

`build` passed.

### Issue 3: Supercell Evolution Used General Storm Support

Issue:

`THUNDERSTORM -> SUPERCELL` could be driven by the same general `EvolutionScore` used for rain/thunder support.

Root Cause:

There was only one persisted evolution score. Thunderstorm support and severe/supercell support were not tracked separately.

This meant a very strong general thunder score could potentially promote without requiring the stronger supercell support terms.

Fix Applied:

Added `SevereEvolutionScore` to `WeatherCellState`.

Saved field:

`SevereEvolutionScore`

Restored field:

`SevereEvolutionScore`

Migration:

Old saves without the field initialize it from `EvolutionScore` only for existing `SUPERCELL` cells; otherwise they initialize to `0.0F`.

Lifecycle now tracks:

`EvolutionScore` from rain/thunder support.

`SevereEvolutionScore` from `AtmosphericSupportEvaluator.supercellSupport()`.

`THUNDERSTORM -> SUPERCELL` now requires `SevereEvolutionScore >= WEATHER_SEVERE_THRESHOLD`.

Verification:

Post-fix scan found `TAG_SEVERE_EVOLUTION_SCORE`, save path, load path, getter, setter, and lifecycle usage.

Persistence has no saved-but-not-restored or restored-but-not-saved mismatch for the new field.

`compileJava` passed.

`build` passed.

### Issue 4: Formation Fairness Could Be Defeated By Early Candidate Truncation

Issue:

Formation collected candidates from an unordered active-state set and stopped at `MAX_CANDIDATES` before fairness sorting.

Root Cause:

The loop broke once candidate count reached 24. Because active state iteration is not ordered by player-local fairness, strong or nearby candidates could be excluded before sorting.

Fix Applied:

Removed early break during collection.

Now all eligible active candidates are evaluated first, then sorted by:

Player-local active cell count ascending.

Formation score descending.

Then truncation applies using:

`min(MAX_CANDIDATES, players * MAX_CANDIDATES_PER_PLAYER)`

Verification:

Post-fix scan shows `subList` truncation happens after sorting.

Global cap remains enforced before formation.

Per-region cap remains enforced.

Player-local cap remains enforced.

Per-player spawn-per-attempt cap remains enforced.

`compileJava` passed.

`build` passed.

### Issue 5: Native Cloud Feedback Could Accumulate Above Current Support

Issue:

Native cloud feedback only increased cloud cover and cloud water when targets were higher than current values. If a weaker cloud remained in a region, cloud cover/cloud water could stay above the current active cloud support until the region became unsupported.

Root Cause:

`CloudRegionAtmosphereFeedbackController` applied each active cloud directly and only moved atmosphere values upward. It did not aggregate region targets and did not converge supported regions downward toward lower current targets.

Fix Applied:

Added per-region target aggregation:

All active native cloud regions in a weather region contribute to one strongest `FeedbackTarget`.

Atmosphere cloud cover now lerps toward the aggregated target, not only upward.

Cloud water now rises toward the target and decays toward the target when rain is not active.

Unsupported regions still decay cloud cover and cloud water conservatively.

Verification:

Post-fix scan found `Map<RegionInstanceKey, FeedbackTarget>` aggregation and one application pass per region.

Cloud cover can no longer accumulate forever while a lower supported target remains.

Cloud water can no longer accumulate forever in dry supported regions because it decays toward the current target when rain intensity is low.

`compileJava` passed.

`build` passed.

## Evolution Verification

RAIN_CELL to THUNDERSTORM:

Verified.

A `RAIN_CELL` now tracks `EvolutionScore` from rain-cell sustain support and promotes only to `THUNDERSTORM` when `EvolutionScore >= 0.42`.

Thunderstorm threshold reachability:

Reachable. Rain-cell sustain support can reach 1.0 from live humidity, cloud water, pressure, convergence, and rain support.

Thunderstorm threshold impossible:

No. The threshold is inside the existing `0.0..1.0` support range.

Thunderstorm threshold too easy:

No obvious issue found. It still requires smoothed support reaching the existing Project Atmosphere thunder phase threshold.

THUNDERSTORM to SUPERCELL:

Verified.

`THUNDERSTORM` now promotes to `SUPERCELL` only when `SevereEvolutionScore >= 0.70`.

Supercell threshold reachability:

Reachable. `supercellSupport` can reach 1.0 through thunderstorm support plus pressure, convergence, gust, and wind strength terms.

Supercell threshold impossible:

No. The score is clamped `0.0..1.0`, and the severe threshold is `0.70`.

Supercell threshold too easy:

Fixed. It no longer uses general thunder support alone. It requires the severe support score.

Evolution stuck:

No stuck upgrade path found after fixes. RAIN_CELL can reach thunder if support is sufficient. THUNDERSTORM can reach supercell if severe support is sufficient. Both scores decay when support disappears.

Evolution bypass:

Fixed. Direct `RAIN_CELL -> SUPERCELL` no longer exists.

Oscillation:

Fixed with hysteresis thresholds.

## Evolution Reversal Verification

SUPERCELL to THUNDERSTORM:

Verified.

`SUPERCELL` weakens to `THUNDERSTORM` when `SevereEvolutionScore < 0.56`.

THUNDERSTORM to RAIN_CELL:

Verified.

`THUNDERSTORM` weakens to `RAIN_CELL` when `EvolutionScore < 0.30`.

RAIN_CELL to dissipation:

Verified.

`RAIN_CELL` dissipates when intensity is very low, support is below rain threshold, and the existing minimum age guard has passed.

Instant downgrade:

Fixed. Smoothed scores plus hysteresis prevent a one-tick support collapse from immediately bouncing between phase thresholds.

Restart preservation:

Verified.

`EvolutionScore`, `SevereEvolutionScore`, type, position, intensity, age, lifetime, and support metrics are saved and restored.

## Formation Verification

Formation thresholds:

Verified.

Formation still uses the existing rain formation threshold `0.58`.

Formation probability:

Verified.

Formation chance remains bounded between `0.12` and `0.52`.

Global budgeting:

Verified.

`MAX_ACTIVE_WEATHER_CELLS = 48`.

The formation attempt cannot overshoot this cap because `maxNewThisAttempt` is bounded by remaining capacity.

Regional budgeting:

Verified.

`MAX_ACTIVE_CELLS_PER_REGION = 4`.

Counts use WeatherCell current position, not source region.

Player-local budgeting:

Verified.

`MAX_ACTIVE_CELLS_NEAR_PLAYER = 12`.

Each player can receive at most one new WeatherCell per formation attempt.

Formation starvation:

Fixed.

Candidates are now sorted by fairness before truncation.

## Native Cloud Feedback Verification

Atmosphere to clouds to atmosphere:

Verified.

Native cloud birth uses live atmosphere through `NativeAtmosphereCloudService`.

Native cloud regions feed cloud cover and cloud water back through `CloudRegionAtmosphereFeedbackController`.

WeatherCells then read cloud cover and cloud water through `AtmosphericSupportEvaluator`.

Feedback too weak:

No fix required. It is conservative but present, and it uses real native cloud region density, coverage, and visual precipitation-core strength.

Feedback too strong:

No fix required after aggregation/downward convergence. Targets are clamped and applied by lerp.

Cloud cover accumulation:

Fixed.

Cloud cover now converges toward active native cloud support and decays unsupported regions.

Cloud water accumulation:

Fixed.

Cloud water rises toward active cloud support and decays toward target when rain is not active.

Feedback instability:

No instability found after the fix. Per-region aggregation prevents multiple native clouds in the same region from fighting each other in sequential updates.

## Shared Support Evaluator Verification

Support consistency:

Verified for Phase 4 paths.

Cloud birth and WeatherCell formation both use `AtmosphericSupportEvaluator`.

WeatherCell lifecycle uses the same evaluator for rain/thunder/severe support.

Remaining duplicated calculations:

The old duplicate wind convergence math between native cloud birth and WeatherCell formation was removed.

Cloud morphology evolution still has its own structural instability model. That is outside this task because modifying cloud morphology/evolution visuals was explicitly excluded.

Weights:

No immediate fix required after severe support separation. Supercell promotion now uses the stronger severe score instead of general storm score.

## Persistence Verification

RAIN_CELL persistence:

Verified through `WeatherCellState.save/load`.

THUNDERSTORM persistence:

Verified through saved `Type`.

SUPERCELL persistence:

Verified through saved `Type`.

EvolutionScore persistence:

Verified.

SevereEvolutionScore persistence:

Added and verified.

Position persistence:

Verified through `CenterX`, `CenterY`, and `CenterZ`.

Intensity persistence:

Verified through `Intensity`.

Lifecycle persistence:

Verified through `AgeTicks`, `LifetimeTicks`, `Active`, `Type`, `EvolutionScore`, and `SevereEvolutionScore`.

Saved but not restored:

None found after fixes.

Restored but not saved:

None found after fixes.

Restart discontinuity:

Fixed for severe evolution state. Existing runtime scheduling cooldowns remain runtime-only, but that is not a WeatherCell state persistence bug.

## Runtime Stability Verification

Memory growth:

No unbounded WeatherCell collection path found. `WeatherCellSavedData` stores cells by UUID and the global cap prevents unbounded active growth.

Cell leaks:

No dead-cell retention issue found. `WeatherCellManager.tick` removes inactive cells from saved data.

Dead cells remaining in memory:

No issue found in the manager path. Inactive cells are removed after lifecycle.

Unbounded collections:

Formation candidate evaluation is bounded after sorting by `MAX_CANDIDATES` and `MAX_CANDIDATES_PER_PLAYER`.

Invalid state transitions:

Fixed. Transitions are now staged.

Broken lifecycle ownership:

Fixed. Rain, thunderstorm, and supercell lifecycle are owned by `WeatherCellLifecycleController`; future inactive enum values are passively decayed and not implemented as weather systems.

## Client Safety Verification

Command run:

```powershell
rg -n "net\.minecraft\.client|Minecraft\.getInstance|RenderSystem|ShaderInstance|PoseStack|VertexBuffer|projectatmosphere\.client|clouds\.client|com\.mojang\.blaze3d" src/main/java/net/Gabou/projectatmosphere/modules/weathercell src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericSupportEvaluator.java src/main/java/net/Gabou/projectatmosphere/clouds/simulation src/main/java/net/Gabou/projectatmosphere/clouds/service src/main/java/net/Gabou/projectatmosphere/clouds/state src/main/java/net/Gabou/projectatmosphere/clouds/network
```

Result:

No matches.

WeatherCell classes, modified cloud classes, atmosphere evaluator, persistence layers, cloud state, and cloud network paths passed the targeted client-only reference scan.

## Build Verification

Command:

```powershell
.\gradlew compileJava
```

Result:

PASS.

`BUILD SUCCESSFUL in 3s`

Command:

```powershell
.\gradlew build
```

Result:

PASS.

`BUILD SUCCESSFUL in 4s`

Diff check:

`git diff --check` found no whitespace errors in the audited paths.

Git reported line-ending warnings for two tracked files, but not whitespace errors.

## Remaining Issues That Require A Future Phase

Tornado generation from supercells is not implemented by design.

Hurricane/cyclone WeatherCell behavior is not implemented by design.

Blizzard WeatherCell behavior is not implemented by design.

WeatherCell-to-cloud morphology coupling is not implemented by design.

WeatherCell client sync/HUD/rendering remains deferred because no current client feature consumes WeatherCell state.

Cloud morphology evolution still has its own structural scoring model. Replacing that would be a cloud morphology/evolution task, not a Phase 4 WeatherCell verification fix.
