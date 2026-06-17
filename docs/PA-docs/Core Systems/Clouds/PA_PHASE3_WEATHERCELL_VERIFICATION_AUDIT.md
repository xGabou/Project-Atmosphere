# Project Atmosphere Phase 3 WeatherCell Verification Audit

Scope: verification only. No implementation, refactor, optimization, rendering work, storm work, or architecture rewrite was performed.

Validation date: 2026-06-15

## Summary Verdict

The WeatherCell system is compiled, plugged into the normal server tick path, persists through `SavedData`, and can create `RAIN_CELL` instances from live atmosphere state. The implementation is server-authoritative and the targeted WeatherCell/common PA cloud client-only reference scan found no client-only imports or references.

The main correctness issue is that a WeatherCell moves in world space but continues to use its original `sourceRegion` for wind sampling and atmosphere feedback. This means a moved rain cell can visually/positionally exist in one area while draining cloud water and raising rain intensity in the original source region. That should be fixed before Phase 4 because future thunderstorms, supercells, tornado precursors, hurricanes, and blizzards will need spatially correct feedback.

No critical build or dedicated-server classloading issue was found in the inspected WeatherCell/common PA cloud paths.

## Part A: Complete WeatherCell Runtime Flow

### Runtime Path

1. Server tick

Class: `EventHandler`

Method: `onLevelTick(TickEvent.LevelTickEvent event)`

File: `src/main/java/net/Gabou/projectatmosphere/event/EventHandler.java`

Responsibility: Runs on `TickEvent.LevelTickEvent`, END phase only, server side only, `ServerLevel` only. It exits if initial generation is not done, if no players are present, or if the level is not Overworld. When `AtmoCommonConfig.EVENTS_ENABLED` is true, it calls `AtmosphereManager.tick(serverLevel)`.

Evidence: The method checks `event.phase`, `event.level.isClientSide`, `ServerLevel`, `AtmosphereManager.isInitialGenerationDone`, `serverLevel.players().isEmpty()`, `Level.OVERWORLD`, and `AtmoCommonConfig.EVENTS_ENABLED` before calling `AtmosphereManager.tick`.

Status: PASS.

2. Atmosphere gameplay tick

Class: `AtmosphereManager`

Method: `tick(ServerLevel level)`

File: `src/main/java/net/Gabou/projectatmosphere/manager/AtmosphereManager.java`

Responsibility: Performs season transition check, then calls `ForecastOrchestrator.tick(level)` when not regenerating. The method also ticks snowstorms and Simple Clouds-only tornado/hurricane managers, then ticks the active atmosphere cloud service.

Evidence: `ForecastOrchestrator.tick(level)` is called inside the non-regenerating branch. During regeneration, `ForecastOrchestrator.tick(level)` is called but `ForecastOrchestrator.tick` immediately returns because it checks `isRegenerating()`.

Status: PASS.

3. Forecast/live-atmosphere tick

Class: `ForecastOrchestrator`

Method: `tick(ServerLevel level)`

File: `src/main/java/net/Gabou/projectatmosphere/manager/ForecastOrchestrator.java`

Responsibility: Runs the atmosphere update pipeline. The relevant order is:

`SeasonalAtmosphericDrift.tick(level)`

`AtmosphericUpdateScheduler.tick(level)`

`WeatherCellManager.tick(level)`

`getActiveRegions(level)`

`OceanBasinManager.update(level, activeRegions)`

`CycloneManager.update(level)`

`WindVector.update(level)`

`CloudManager.update(level)` when Simple Clouds is loaded

`WindEngine.tick(level, activeRegions)`

Evidence: `WeatherCellManager.tick(level)` is directly called from this normal gameplay tick path.

Status: PASS.

Ordering note: WeatherCells tick before `WindEngine.tick`. Because `ForecastOrchestrator.getWind` can read live `RegionAtmosphereState.wind`, WeatherCell motion can use the previous committed wind state for the current tick rather than the wind that `WindEngine.tick` will compute later in the same tick.

4. WeatherCell scheduling and data access

Class: `WeatherCellManager`

Method: `tick(ServerLevel level)`

File: `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellManager.java`

Responsibility: Gates WeatherCell ticking to Overworld, runs once every 20 game ticks, loads `WeatherCellSavedData`, ticks active cells, removes inactive cells, and runs formation once every 600 game ticks.

Evidence: `TICK_INTERVAL = 20`, `FORMATION_INTERVAL = 600`, `WeatherCellSavedData.get(level)`, `MOTION.tick`, `LIFECYCLE.tick`, inactive removal through `data.remove`, and formation through `FORMATION.tick`.

Status: PASS.

5. WeatherCell formation

Class: `WeatherCellFormationController`

Method: `tick(ServerLevel level, WeatherCellSavedData data, Collection<WeatherCellState> activeCells)`

File: `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellFormationController.java`

Responsibility: Collects candidate atmospheric regions, scores them from live `RegionAtmosphereState`, and creates at most one new `RAIN_CELL` per formation attempt.

Evidence: Candidate states are pulled from `AtmosphericStateRegistry.getActiveStates()` and `AtmosphericStateRegistry.getState(key)`. If no active states exist, player region keys are used as fallback. Formation creates `WeatherCellState` with `WeatherCellType.RAIN_CELL` only.

Status: PASS.

6. WeatherCell creation

Class: `WeatherCellFormationController`

Method: `createRainCell(ServerLevel level, FormationCandidate candidate, RandomSource random)`

File: `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellFormationController.java`

Responsibility: Creates a server-side `WeatherCellState` with a UUID, type, source region, center position, radius, intensity, moisture, instability, pressure anomaly, wind influence, cloud water, rain intensity, age, lifetime, and active flag.

Evidence: The constructor is called with `WeatherCellType.RAIN_CELL`, `candidate.regionKey()`, center near `candidate.state().getPosition()`, intensity from score, radius from score, and lifetime `20 * (420 + random.nextInt(420))`.

Status: PASS.

7. WeatherCell persistence marking

Class: `WeatherCellSavedData`

Method: `add(WeatherCellState cell)`

File: `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellSavedData.java`

Responsibility: Adds the new cell to a `LinkedHashMap<UUID, WeatherCellState>` and calls `setDirty()`.

Evidence: `cells.put(cell.getId(), cell); setDirty();`.

Status: PASS.

8. WeatherCell tick

Class: `WeatherCellManager`

Method: `tick(ServerLevel level)`

File: `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellManager.java`

Responsibility: For each active cell, motion runs first, lifecycle runs second.

Evidence: The loop calls `changed |= MOTION.tick(level, cell); changed |= LIFECYCLE.tick(level, cell);`.

Status: PASS.

9. WeatherCell removal

Class: `WeatherCellManager`

Method: `tick(ServerLevel level)`

File: `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellManager.java`

Responsibility: Removes cells that are no longer active.

Evidence: The manager loops over a copy of `data.getCells()` and calls `data.remove(cell.getId())` when `!cell.isActive()`.

Status: PASS.

## Part B: Formation Verification

### Formation Inputs

Class: `WeatherCellFormationController`

Method: `evaluate`

File: `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellFormationController.java`

The formation score uses these live atmosphere values:

Humidity: `state.getHumidity()`, clamped to `0.0F..1.2F`

Cloud water: `state.getCloudWater()`, clamped to `0.0F..1.2F`

Pressure: `state.getPressure()`

Wind convergence: `WeatherCellSupport.estimateWindConvergence(key, state)`

Rain intensity: `state.getRainIntensity()`, clamped to `0.0F..1.0F`

Cloud cover: `state.getCloudCover()`, clamped to `0.0F..1.0F`

Existing WeatherCell coverage: `WeatherCellSupport.estimateCellCoverage(state.getPosition(), activeCells)`

### Hard Gates

A candidate is rejected if any of these are true:

`humidity < 0.72F`

`cloudWater < 0.12F`

`coverage >= 0.70F`

`score < 0.58F`

### Score Terms

Humidity score: `(humidity - 0.68) / 0.32`

Cloud water score: `(cloudWater - 0.10) / 0.28`

Pressure score: `(1017 - pressure) / 28`

Convergence score: direct clamped convergence

Rain support: `(rain - 0.04) / 0.45`

Cloud cover score: `(cloudCover - 0.35) / 0.50`

High pressure penalty: `(pressure - 1022) / 24`

Saturation penalty: `(coverage - 0.45) / 0.35`

Weighted instability:

`humidityScore * 0.28 + cloudWaterScore * 0.28 + pressureScore * 0.18 + convergenceScore * 0.14 + cloudCoverScore * 0.08 + rainSupport * 0.04`

Final score:

`instability - saturationPenalty * 0.45 - highPressurePenalty * 0.20`

### Realistic Atmosphere Ranges Found

Class: `RegionAtmosphereState`

File: `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/RegionAtmosphereState.java`

Observed behavior: `fromForecast` initializes humidity from forecast humidity divided by 100. Pressure is initialized from forecast pressure. Cloud water, cloud cover, and rain intensity start at zero unless later runtime systems mutate them.

Class: `CloudWaterService`

File: `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CloudWaterService.java`

Observed behavior: cloud water can accumulate from supersaturation. The formula uses `supersaturation = max(0, humidity - max(targetHumidity, 0.55))`, then a condensation term based on supersaturation and cloud cover. This makes cloud water accumulation plausible but gradual when humidity is high.

Class: `AtmosphericUpdateScheduler`

File: `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericUpdateScheduler.java`

Observed behavior: atmosphere deltas are applied periodically. Cloud water delta is clamped before application, so cloud water does not jump instantly to high values through the normal atmosphere scheduler.

Class: `CloudManager`

File: `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CloudManager.java`

Observed behavior: when Simple Clouds is loaded, cloud cover and rain can be written back into `RegionAtmosphereState`. This makes WeatherCell formation easier in Simple Clouds-backed rainy/cloudy regions.

### Formation Probability Assessment

Question: Can normal regions realistically reach the required thresholds?

Answer: PASS with caveat. A normal region can reach the thresholds if humidity is high, cloud water has accumulated, pressure support exists, and wind convergence or cloud cover contributes. In PA-native-only mode this is likely rare because cloud cover and rain intensity often remain zero unless another system writes them. In Simple Clouds-influenced regions, formation is more realistic because cloud cover, rain, and cloud water can be populated by `CloudManager`.

Question: Are some thresholds effectively impossible?

Answer: PASS. None of the hard gates are mathematically impossible. Humidity and cloud water are clamped up to 1.2. Cloud water can accumulate through atmosphere updates. Pressure support can become positive below 1017 hPa. Wind convergence can contribute through neighboring wind state.

Question: Are some thresholds always true?

Answer: PASS. None of the hard gates are always true. Humidity, cloud water, coverage, and score all vary by runtime state.

Question: Could formation become extremely rare?

Answer: PASS. Yes. In PA-native-only operation, if no system raises cloud cover or rain intensity and cloud water remains below roughly 0.30, the score often fails even when humidity is high. Example:

Humidity 0.90 gives about 0.193 score contribution.

Cloud water 0.20 gives about 0.100 contribution.

Pressure 1008 gives about 0.058 contribution.

Convergence 0.40 gives about 0.056 contribution.

Cloud cover 0 and rain 0 give no contribution.

Total before penalties is about 0.407, below the required 0.58.

Question: Could formation become extremely common?

Answer: PASS. It is bounded. Formation runs every 600 ticks, creates at most one cell per attempt, requires player proximity, rejects high local WeatherCell coverage, and has a global active rain cell cap of 48. Even after a score passes, random chance is clamped to `0.12F..0.52F`.

Example pass case:

Humidity 1.00 gives about 0.280 contribution.

Cloud water 0.38 gives about 0.280 contribution.

Pressure 1000 gives about 0.109 contribution.

Convergence 0.50 gives about 0.070 contribution.

Cloud cover 0 and rain 0 still produce a total near 0.739, which passes before penalties.

Formation verdict: `RAIN_CELL` can form, but PA-native-only first rain is conservative and may be rare without strong cloud water and low pressure support.

## Part C: Feedback Loop Verification

### Trace

Atmosphere to WeatherCell:

`WeatherCellFormationController.evaluate` reads `RegionAtmosphereState` humidity, cloud water, pressure, wind convergence, rain intensity, and cloud cover.

WeatherCell to atmosphere:

`WeatherCellLifecycleController.tick` looks up `AtmosphericStateRegistry.getState(cell.getSourceRegion())`, computes support, updates cell intensity, and calls `applyConservativeRainFeedback(source, cell)`.

### Fields Modified

Class: `WeatherCellLifecycleController`

Method: `applyConservativeRainFeedback`

File: `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellLifecycleController.java`

Modified atmosphere fields:

`RegionAtmosphereState.rainIntensity`

`RegionAtmosphereState.cloudWater`

Rain behavior:

If `cell.getRainIntensity()` is greater than current atmosphere rain intensity, the atmosphere value moves 8 percent toward the cell rain target per WeatherCell tick.

Cloud water behavior:

`drain = clamp(cell.getIntensity() * 0.003F, 0.0F, 0.006F)` and cloud water is set to `max(0, current - drain)`.

Frequency:

Feedback runs once per active cell per WeatherCell tick. `WeatherCellManager.TICK_INTERVAL` is 20 ticks, so this is once per second under normal 20 TPS gameplay.

Persistence:

The atmosphere fields modified by feedback are part of mutable `RegionAtmosphereState`. Phase 2A persistence saves and restores mutable state through `AtmosphericStateSavedData`. WeatherCell state is separately persisted through `WeatherCellSavedData`.

Feedback strength:

Not negligible. A cell with intensity around 0.36 has rain target around 0.22 and drains about 0.0011 cloud water per second. Over one minute this is around 0.066 cloud water before intensity changes. The feedback is conservative, but it is real and persistent if world save occurs.

Correctness issue:

FAIL. Feedback is applied to `cell.getSourceRegion()` even after the cell moves. The moved cell center is not used to select the current atmosphere region. A moving rain cell can continue raising rain intensity and draining cloud water in its original source region instead of the region under its current center.

## Part D: Motion Verification

### Trace

WeatherCell:

Class: `WeatherCellManager`

Method: `tick`

File: `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellManager.java`

Calls `MOTION.tick(level, cell)` for each active cell.

Motion controller:

Class: `WeatherCellMotionController`

Method: `tick`

File: `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellMotionController.java`

Wind source:

`ForecastOrchestrator.getWind(windRegion, level.getGameTime())`

Position update:

`speed = wind.baseSpeed() * 0.030D`

`velocity = new Vec3(-sin(angle) * speed, 0, cos(angle) * speed)`

`cell.setCenter(cell.getCenter().add(velocity))`

### Motion Questions

Question: Does movement use forecast wind or live wind?

Answer: PASS with ordering caveat. `ForecastOrchestrator.getWind` selects live `RegionAtmosphereState.wind` when available and valid, then falls back to forecast wind, then safe default wind. However, WeatherCells tick before `WindEngine.tick` in the same `ForecastOrchestrator.tick`, so motion can use the previous committed live wind state for that tick.

Question: Are coordinates compatible?

Answer: PASS. Formation creates `Vec3` centers from world-space `BlockPos` region positions. Motion adds world-space X/Z deltas. The coordinate space is compatible.

Question: Can cells become stuck?

Answer: PASS with caveat. A cell does not move if selected wind is null or base speed is zero. `ForecastOrchestrator.getWind` has live, forecast, and safe-default fallbacks, so complete stalling is unlikely unless a valid wind vector with zero speed is selected.

Question: Can cells move unrealistically fast?

Answer: PASS. The drift scale is conservative. At 20 m/s wind, movement is `20 * 0.030 = 0.6` blocks per WeatherCell tick. Since WeatherCell ticks every 20 game ticks, this is about 0.6 blocks per second.

Question: Can cells move unrealistically slow?

Answer: PASS. Yes. If one block is treated as roughly one meter, 5 m/s wind moves the cell only 0.15 blocks per second. This is intentionally conservative but may be visually and mechanically slow for future large storm systems.

Coordinate and region correctness issue:

FAIL. The wind region is `cell.getSourceRegion()` when present. Because cells keep their original source region, a moved cell continues using source-region wind instead of wind under its current center. This is the same spatial anchoring issue found in feedback.

## Part E: Persistence Verification

### Save and Load Path

Create WeatherCell:

`WeatherCellFormationController.createRainCell` creates a `WeatherCellState`.

Add to persistence:

`WeatherCellFormationController.tick` calls `data.add(cell)`.

SavedData:

Class: `WeatherCellSavedData`

Method: `add`

File: `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellSavedData.java`

Responsibility: stores by UUID and marks dirty.

Save:

Class: `WeatherCellSavedData`

Method: `save`

File: `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellSavedData.java`

Responsibility: writes `Version` and a `Cells` list.

Cell save:

Class: `WeatherCellState`

Method: `save`

File: `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellState.java`

Responsibility: writes id, type, source region, center, radius, intensity, moisture, instability, pressure anomaly, wind influence, cloud water, rain intensity, age, lifetime, active flag, and linked cloud IDs.

Load:

Class: `WeatherCellSavedData`

Method: `get`

File: `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellSavedData.java`

Responsibility: loads from Minecraft `DataStorage` through `computeIfAbsent`.

Cell load:

Class: `WeatherCellState`

Method: `load`

File: `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellState.java`

Responsibility: reconstructs all saved cell fields.

Resume tick:

`WeatherCellManager.tick` obtains `WeatherCellSavedData.get(level)`, loops active cells, and resumes motion/lifecycle.

### Field Verification

WeatherCell survives restart: PASS, stored in `project_atmosphere_weather_cells` SavedData.

Age survives restart: PASS, `AgeTicks` is saved and loaded.

Intensity survives restart: PASS, `Intensity` is saved and loaded.

Position survives restart: PASS, `CenterX`, `CenterY`, and `CenterZ` are saved and loaded.

Type survives restart: PASS, `Type` is saved and loaded.

Source region survives restart: PASS, source region is saved when non-null and loaded from its compound tag.

Saved but not restored fields: PASS, none found in `WeatherCellState`.

Restored but not saved fields: PASS, none found in `WeatherCellState` for current saves. Defaults exist for old or missing data.

Recreated differently after restart:

Low issue. `WeatherCellManager.nextTick` and `nextFormationTick` are runtime static fields and are not saved. Existing cells resume correctly, but formation cooldown resets after restart and may run immediately on the first manager tick.

## Part F: Scheduling Verification

Formation frequency:

`WeatherCellManager.FORMATION_INTERVAL = 600`, so formation attempts run every 600 game ticks, about every 30 seconds at 20 TPS.

Lifecycle frequency:

`WeatherCellManager.TICK_INTERVAL = 20`, so lifecycle runs once per second for active cells.

Movement frequency:

Movement also runs once per second because it is called from the same WeatherCellManager active-cell loop.

Persistence update frequency:

`WeatherCellSavedData.add`, `remove`, and `markChanged` call `setDirty()`. Active cells generally mark data dirty every WeatherCell tick because motion/lifecycle usually returns changed. Actual disk write timing is handled by Minecraft `SavedData`/world save.

Can scheduling stop accidentally?

PASS with caveats. WeatherCellManager only runs when the normal Overworld server tick path reaches `ForecastOrchestrator.tick`. It does not run if `EVENTS_ENABLED` is false, if no players are present, if initial generation is incomplete, during forecast regeneration, or outside Overworld. These gates are visible in the inspected call path.

Can scheduling duplicate itself?

PASS. There is one static `WeatherCellManager.tick` call from `ForecastOrchestrator.tick`. No separate event subscriber or duplicate scheduler was found for WeatherCells.

Timing assumption issue:

Low issue. The static cooldown fields are global runtime fields. This is acceptable for Overworld-only operation, but the formation cooldown resets on process restart and is not persisted.

## Part G: WeatherCell Coverage Analysis

Maximum active WeatherCells:

`MAX_ACTIVE_RAIN_CELLS = 48`.

Is the cap global or regional?

Global. `countActiveRainCells(activeCells)` counts active `RAIN_CELL` instances across the active WeatherCell collection passed from `WeatherCellSavedData`, not per region or per player.

Could one area consume the entire WeatherCell budget?

Yes. Formation candidates are chosen from active atmosphere states near players, and the cap is global. A single active area can consume the 48-cell budget over time.

Could distant regions starve local formation?

Yes. If distant cells remain active and the global count reaches 48, `WeatherCellFormationController.tick` returns before evaluating local candidates.

Could coverage suppression permanently block new cells?

Not permanently by itself. Local coverage suppression rejects candidates at coverage `>= 0.70`. Existing cells eventually decay by lifetime/support and are removed. However, while active cells remain in or near an area, they can suppress new formation there.

Coverage correctness issue:

Medium issue. Coverage is based on cell centers, while lifecycle feedback remains source-region anchored. A moved cell can suppress formation around its current center while still modifying its source region.

## Part H: WeatherCell and Cloud Interaction

Question: Does WeatherCell creation affect cloud creation?

Answer: No direct effect found. Native cloud birth does not inspect WeatherCell state. WeatherCells modify atmosphere rain intensity and cloud water only; this can indirectly affect cloud birth if cloud spawning reads the same atmospheric fields later.

Question: Does cloud creation affect WeatherCells?

Answer: Directly, no. Indirectly, yes. Native cloud birth drains cloud water after successful cloud creation, and WeatherCell formation requires cloud water. Simple Clouds-backed `CloudManager` can write cloud cover, rain, and cloud water into `RegionAtmosphereState`, which can make WeatherCell formation more likely.

Question: Are they currently independent?

Answer: Mostly yes. WeatherCells and native cloud regions have separate managers, persistence, lifecycle, and ownership. They interact through shared live atmosphere state rather than direct links.

Question: Is there duplicated logic?

Answer: Yes. WeatherCell formation and native cloud birth both score humidity, cloud water, pressure, wind convergence, and local coverage-like conditions. This is not a correctness bug in Phase 3, but it can diverge behavior later.

Question: Is future integration straightforward or problematic?

Answer: Partly straightforward. `WeatherCellState` already supports optional linked native cloud region IDs. However, the current source-region anchoring must be corrected before future storm-cell/cloud integration because moved cells need spatially correct wind and atmospheric feedback.

## Part I: Client Safety Recheck

### Targeted Scan

Command run:

```powershell
rg -n "net\.minecraft\.client|com\.mojang\.blaze3d|Minecraft\.getInstance|RenderSystem|ShaderInstance|VertexBuffer|PoseStack|projectatmosphere\.client|clouds\.client" src/main/java/net/Gabou/projectatmosphere/clouds/simulation src/main/java/net/Gabou/projectatmosphere/clouds/state src/main/java/net/Gabou/projectatmosphere/clouds/service src/main/java/net/Gabou/projectatmosphere/clouds/network src/main/java/net/Gabou/projectatmosphere/modules/weathercell
```

Result: no matches. `rg` returned exit code 1 because no matches were found.

### Inspected Scope

WeatherCell classes:

`WeatherCellState`

`WeatherCellType`

`WeatherCellManager`

`WeatherCellSavedData`

`WeatherCellFormationController`

`WeatherCellLifecycleController`

`WeatherCellMotionController`

`WeatherCellSupport`

PA cloud common/server packages scanned:

`clouds/simulation`

`clouds/state`

`clouds/service`

`clouds/network`

Client-only symbols checked:

`net.minecraft.client`

`com.mojang.blaze3d`

`Minecraft.getInstance`

`RenderSystem`

`ShaderInstance`

`PoseStack`

`VertexBuffer`

`projectatmosphere.client`

`clouds.client`

Verdict: PASS for the scanned WeatherCell and common PA cloud packages.

Known client packages:

Broad repository scans still find expected client-only references under explicit client packages such as `client`, `clouds/client`, and client render classes. Those were not counted as WeatherCell/common PA cloud violations.

## Part J: Architecture Review

Question: Is the WeatherCell layer correctly positioned between atmosphere and weather effects?

Answer: PASS with caveat. WeatherCells are called from the atmosphere tick path after atmospheric scheduling and before later wind/cloud systems. They read live atmosphere and feed back into live atmosphere. They do not yet drive visible weather effects or native cloud morphology, which matches Task 3 scope.

Question: Does the architecture support future thunderstorms, supercells, tornadoes, hurricanes, and blizzards?

Answer: PASS as a foundation only. The enum includes future types and the state model has fields for moisture, instability, pressure anomaly, wind influence, cloud water, rain intensity, age, lifetime, active flag, and linked cloud IDs. However, current lifecycle explicitly deactivates any type that is not `RAIN_CELL`, so future types cannot be activated until lifecycle behavior is extended.

Question: Are there architectural mistakes that should be fixed now before future work begins?

Answer: YES. The source-region anchoring issue should be fixed before Phase 4. Motion, feedback, and wind sampling should be spatially consistent with the current cell center or footprint, not permanently tied to the birth region.

Question: Are there areas that will become expensive to refactor later?

Answer: YES. The global WeatherCell cap and duplicated atmosphere scoring can become expensive if severe storms, player-local effects, and regional storm budgets are layered on top. These are medium-risk architecture issues, not immediate build blockers.

## Critical Issues

None found.

No build failure, WeatherCell persistence mismatch, or client-only class reference was found in the inspected WeatherCell/common PA cloud paths.

## Medium Issues

1. Moving WeatherCells remain source-region anchored.

Evidence: `WeatherCellMotionController.tick` uses `cell.getSourceRegion()` as the wind region whenever it is non-null. `WeatherCellLifecycleController.tick` uses `AtmosphericStateRegistry.getState(cell.getSourceRegion())` for support and feedback. The current `cell.getCenter()` is not used to select the atmosphere region after movement.

Impact: A moved rain cell can use stale spatial wind and modify the wrong region's rain/cloud water. This is a correctness issue for future storm behavior.

Recommended fix before Phase 4: Sample wind and atmosphere from the current cell center or from a footprint around the current center, while preserving source region only as provenance.

2. Active WeatherCell cap is global.

Evidence: `MAX_ACTIVE_RAIN_CELLS = 48` and `countActiveRainCells` counts the active collection globally.

Impact: One area can consume the full rain-cell budget and starve distant player-local weather formation.

Recommended fix before Phase 4: Add regional or player-local budgeting while keeping the global cap as a safety limit.

3. Native-only rain formation may be rare.

Evidence: Hard gates require humidity `>= 0.72`, cloud water `>= 0.12`, and score `>= 0.58`. Cloud cover and rain intensity help formation but are often zero in PA-native-only paths unless another runtime system writes them.

Impact: `RAIN_CELL` formation is possible but may be uncommon without Simple Clouds-influenced cloud cover/rain or very strong native cloud water and pressure support.

Recommended fix before Phase 4: Verify PA-native cloud cover feedback into atmosphere before relying on WeatherCells as the owner of future rain systems.

4. WeatherCell tick order uses previous wind state for current tick.

Evidence: `ForecastOrchestrator.tick` calls `WeatherCellManager.tick(level)` before `WindEngine.tick(level, activeRegions)`.

Impact: Motion can use previous committed live wind instead of the wind calculated later in the same tick. This is likely acceptable for rain cells but could matter for severe storm motion.

Recommended fix before Phase 4: Decide whether WindEngine should update before WeatherCell motion or whether WeatherCells should intentionally use last-tick wind.

## Low Issues

1. Formation cooldown is not persisted.

Evidence: `WeatherCellManager.nextTick` and `nextFormationTick` are static runtime fields reset by process restart.

Impact: Formation can run immediately after restart. Existing WeatherCells still persist correctly.

2. `WeatherCellFormationController.evaluate` accepts a `ServerLevel level` parameter but does not use it.

Impact: No runtime correctness issue. It is minor dead parameter noise.

3. Future WeatherCell types are defined but currently deactivated by lifecycle.

Evidence: `WeatherCellLifecycleController.tick` sets `active=false` when `cell.getType() != WeatherCellType.RAIN_CELL`.

Impact: Correct for Task 3 because only `RAIN_CELL` is active. Future phases must update lifecycle before creating other types.

4. Cell center Y coordinate uses the atmosphere anchor Y.

Evidence: `createRainCell` sets center Y to `anchor.getY()`.

Impact: No current visual issue because WeatherCells are server simulation only. Future visual/audio/effects systems may need explicit altitude semantics.

## Recommended Fixes Before Phase 4

1. Fix WeatherCell spatial anchoring.

Use current cell center or cell footprint to choose wind and atmosphere feedback targets. Keep `sourceRegion` as provenance, not as the permanent physical location.

2. Add regional WeatherCell budgeting.

Keep the global cap for safety, but add per-region or per-player-local caps so distant active cells cannot starve local formation.

3. Verify or implement PA-native cloud cover feedback into atmosphere before depending on WeatherCells for native rain progression.

This is required because current formation uses cloud cover and rain as support terms, and PA-native-only paths may not populate those fields strongly enough.

4. Decide tick ordering for wind and WeatherCells.

If WeatherCells should move from current live wind, `WindEngine.tick` needs to happen before WeatherCell motion. If last-tick wind is intended, document that decision.

5. Update lifecycle before activating future WeatherCell types.

Current lifecycle intentionally removes non-RAIN_CELL types.

## Validation Results

Command:

```powershell
.\gradlew compileJava
```

Result: PASS.

Output summary:

`BUILD SUCCESSFUL in 1s`

`compileJava UP-TO-DATE`

Command:

```powershell
.\gradlew build
```

Result: PASS.

Output summary:

`BUILD SUCCESSFUL in 4s`

Tasks included `compileJava`, `jar`, `reobfJar`, `assemble`, `test`, `check`, and `build`.

Client safety scan:

Result: PASS for targeted WeatherCell and common PA cloud packages. No client-only references were found by the targeted scan.

## Final Status

Phase 3 WeatherCell foundation is plugged into the normal gameplay path and works as a conservative server-side `RAIN_CELL` system.

Persistence is implemented and field-complete for current WeatherCell state.

Client safety passed the targeted common/server package scan.

The primary blocker before Phase 4 is spatial correctness: moving cells must stop using only their birth/source region for wind and atmosphere feedback.
