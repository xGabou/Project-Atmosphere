# Project Atmosphere Phase 4 WeatherCell Evolution Report

Date: 2026-06-15

Scope completed:

Phase 3 audit fixes were implemented first, then Phase 4 WeatherCell evolution was implemented.

No tornado, hurricane, blizzard, rendering, shader, cloud morphology, or precipitation rendering work was started.

## Modified Files

`src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericSupportEvaluator.java`

Added shared server-side atmospheric support scoring for cloud birth, WeatherCell formation, Rain Cell sustain, Thunderstorm evolution, and Supercell evolution.

`src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellSupport.java`

Changed WeatherCell runtime location helpers so active simulation resolves atmosphere by current cell position instead of source region.

`src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellMotionController.java`

Changed wind sampling to use the WeatherCell current position.

`src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellLifecycleController.java`

Implemented current-position support lookup, current-position feedback, RAIN_CELL lifecycle, THUNDERSTORM lifecycle, SUPERCELL lifecycle, evolution, weakening, and passive handling for future inactive types.

`src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellFormationController.java`

Added global, regional, and player-local formation budgets. Replaced local duplicated scoring with `AtmosphericSupportEvaluator`.

`src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellState.java`

Added persisted `EvolutionScore` so evolution smoothing survives restart.

`src/main/java/net/Gabou/projectatmosphere/clouds/service/NativeAtmosphereCloudService.java`

Reused `AtmosphericSupportEvaluator` for native cloud birth scoring.

`src/main/java/net/Gabou/projectatmosphere/clouds/simulation/CloudRegionAtmosphereFeedbackController.java`

Added native cloud-region feedback into live atmosphere cloud cover and cloud water.

`src/main/java/net/Gabou/projectatmosphere/clouds/simulation/CloudRegionManager.java`

Wired native cloud atmosphere feedback into the existing native cloud region tick.

## Audit Fixes Implemented

### Spatial Anchoring

Previous behavior:

WeatherCells moved, but wind, support, and atmosphere feedback still used `sourceRegion`.

New behavior:

`sourceRegion` remains saved historical provenance only.

Active simulation uses the WeatherCell current center:

`WeatherCellMotionController.tick`

`WeatherCellSupport.currentRegionKey(cell)`

`ForecastOrchestrator.getWind(currentRegionKey, gameTime)`

`WeatherCellLifecycleController.tick`

`WeatherCellSupport.currentAtmosphere(cell)`

`AtmosphericSupportEvaluator.evaluate(currentRegionKey, currentState)`

`applyConservativeRainFeedback(currentState, cell, profile)`

Result:

Future storms can migrate across atmospheric regions naturally. Wind sampling, support calculations, and feedback now follow the current WeatherCell position.

### Regional Budgeting

Global safety cap:

`MAX_ACTIVE_WEATHER_CELLS = 48`

Regional cap:

`MAX_ACTIVE_CELLS_PER_REGION = 4`

Player-local cap:

`MAX_ACTIVE_CELLS_NEAR_PLAYER = 12`

Formation fairness:

Formation candidates are associated with the nearest player inside `PLAYER_FORMATION_RADIUS`.

Candidates are sorted by lower local active WeatherCell count first, then by score.

Each formation attempt can create at most `MAX_NEW_CELLS_PER_ATTEMPT = 2`.

Each player can receive at most `MAX_NEW_CELLS_PER_PLAYER_ATTEMPT = 1` new WeatherCell per attempt.

The global cap is enforced before formation and per attempt so multiple spawns cannot overshoot 48.

Result:

One area can no longer consume the whole WeatherCell budget by itself. Distant activity is constrained by regional and player-local caps, while the global cap remains as a safety limit.

### Future Type Lifecycle Refactor

Previous behavior:

Any WeatherCell that was not `RAIN_CELL` was immediately deactivated.

New behavior:

`RAIN_CELL`, `THUNDERSTORM`, and `SUPERCELL` are active Phase 4 evolution types.

`CYCLONE` and `BLIZZARD` are not implemented, not formed, and not simulated as severe systems. If old or future save data contains them, lifecycle no longer deletes them immediately; they decay passively without rain feedback.

Result:

Future types can exist in save data without immediate removal, but this task does not implement their behavior.

## Atmosphere Integration Findings

Inspected existing live atmosphere behavior:

Humidity is normalized and clamped by `RegionAtmosphereState` to the existing `0.0..1.2` model.

Cloud water is clamped by `RegionAtmosphereState` to `0.0..1.2`.

Cloud cover is clamped by `RegionAtmosphereState` to `0.0..1.0`.

Pressure is clamped by `RegionAtmosphereState` to `900..1080 hPa`.

Rain intensity is non-negative and read by weather state resolution.

`CloudWaterService` already produces cloud water from humidity supersaturation.

`AtmosphericUpdateScheduler` already applies humidity, pressure, temperature, cloud water, and rain fade deltas.

Simple Clouds-backed `CloudManager` writes cloud cover, rain intensity, and cloud water into `RegionAtmosphereState`.

Cyclone and hurricane systems can write cloud cover, rain, and cloud water, but those systems were not modified.

Gap found:

PA-native cloud regions did not have an equivalent native cloud-region-to-atmosphere feedback path.

Integration added:

`CloudRegionAtmosphereFeedbackController` projects active PA-native cloud regions into live atmosphere cloud cover and cloud water.

It does not create rain directly.

It does not alter cloud morphology.

It does not touch rendering.

It decays unsupported native cloud cover/cloud water conservatively for active regions without current native cloud support.

Reasoning:

Cloud cover and cloud water are atmosphere state. Native cloud regions are the concrete cloud layer. Feeding them back into the atmosphere avoids WeatherCell formation depending only on Simple Clouds, cyclone, hurricane, or artificial boosts.

## Shared Support System Design

New class:

`AtmosphericSupportEvaluator`

Responsibilities:

Normalize live atmospheric values.

Estimate wind convergence.

Calculate reusable supports for humidity, cloud water, pressure, wind convergence, cloud cover, rain, wind strength, gust strength, and humidity transport.

Provide composite scores for:

Cloud birth.

Rain Cell formation.

Rain Cell sustain.

Thunderstorm support.

Supercell support.

Current users:

`NativeAtmosphereCloudService`

`WeatherCellFormationController`

`WeatherCellLifecycleController`

Future users:

Cloud birth refinements.

Thunderstorm behavior.

Supercell behavior.

Severe weather precursors.

## Evolution Thresholds And Justification

Thresholds were derived from existing Project Atmosphere scales:

`RAIN_CELL_FORMATION_THRESHOLD = 0.58`

Source: existing WeatherCell rain formation gate from Phase 3.

`WEATHER_RAIN_THRESHOLD = 0.18`

Source: existing `ServerWeatherStateResolver` transition to `RegionalWeatherPhase.RAIN`.

`WEATHER_THUNDER_THRESHOLD = 0.42`

Source: existing `ServerWeatherStateResolver` transition to `RegionalWeatherPhase.THUNDER`.

`WEATHER_SEVERE_THRESHOLD = 0.70`

Source: existing `ServerWeatherStateResolver` transition to `RegionalWeatherPhase.SEVERE`.

Atmospheric range basis:

Humidity: `0.0..1.2`.

Cloud water: `0.0..1.2`.

Cloud cover: `0.0..1.0`.

Pressure: `900..1080 hPa`.

Rain intensity: non-negative, sampled as `0.0..1.0` for support.

Wind strength: sampled from `RegionAtmosphereState.getWindStrength()`.

Wind convergence: derived from neighboring live wind vectors, clamped `0.0..1.0`.

No random promotions were added.

No arbitrary promotion timers were added.

Evolution uses a persisted smoothed `EvolutionScore` driven by current atmospheric support.

## RAIN_CELL Implementation

Formation:

Automatic RAIN_CELL formation still starts from live atmosphere.

Formation reads humidity, cloud water, pressure, wind convergence, rain intensity, cloud cover, and active WeatherCell coverage through the shared evaluator.

Lifecycle:

RAIN_CELL intensity follows rain-cell sustain support.

RAIN_CELL feeds back rain intensity and drains cloud water at conservative rates.

RAIN_CELL can strengthen into THUNDERSTORM when the persisted evolution score crosses the existing thunder threshold.

Weakening:

If support drops below rain threshold and intensity becomes very low, the cell dissipates.

## THUNDERSTORM Implementation

Creation:

THUNDERSTORM is produced only by RAIN_CELL evolution.

No command path or random promotion was added.

Support:

Thunderstorm support comes from live humidity, cloud water, rain, cloud cover, low pressure, wind convergence, and wind strength through `AtmosphericSupportEvaluator`.

Lifecycle:

THUNDERSTORM persists, moves with current-position wind, saves/loads as `WeatherCellType.THUNDERSTORM`, and continues atmosphere feedback.

Weakening:

If atmospheric support falls below the thunder threshold after smoothing, the cell becomes RAIN_CELL.

No lightning, audio, rendering, particles, or precipitation rendering was added.

## SUPERCELL Implementation

Creation:

SUPERCELL is produced only by THUNDERSTORM evolution when support reaches the existing severe threshold.

Support:

Supercell support uses thunderstorm support plus stronger pressure, convergence, gust, and wind strength terms.

Lifecycle:

SUPERCELL persists, moves with current-position wind, saves/loads as `WeatherCellType.SUPERCELL`, and continues conservative atmosphere feedback.

Weakening:

If support falls below the severe threshold after smoothing, the cell becomes THUNDERSTORM.

No tornado spawning, hail, visual effects, rendering, or severe-weather effects were added.

## Evolution Reversal

Supported path:

`RAIN_CELL -> THUNDERSTORM -> SUPERCELL`

Weakening path:

`SUPERCELL -> THUNDERSTORM -> RAIN_CELL -> dissipation`

Mechanism:

The `EvolutionScore` moves toward current atmospheric support each lifecycle tick.

Type changes are based on the smoothed score crossing existing Project Atmosphere weather phase thresholds.

Because the score is persisted, restart does not reset the evolution state.

## Persistence Verification

WeatherCell persistence remains in:

`WeatherCellSavedData`

Cell fields saved and restored include:

Type.

Position.

Radius.

Intensity.

Moisture.

Instability.

Pressure anomaly.

Wind influence.

Cloud water.

Rain intensity.

Age ticks.

Lifetime ticks.

Active flag.

Source region.

Linked native cloud region IDs.

New field:

`EvolutionScore`

Migration behavior:

Old saves without `EvolutionScore` initialize it from existing `Instability`, preserving the closest available evolution signal.

Forecast data is not duplicated.

Atmosphere state is not duplicated.

RAIN_CELL, THUNDERSTORM, and SUPERCELL all persist through the same `WeatherCellState.Type` and `EvolutionScore` path.

## Client Safety Verification

Command run:

```powershell
rg -n "net\.minecraft\.client|Minecraft\.getInstance|RenderSystem|ShaderInstance|PoseStack|VertexBuffer|projectatmosphere\.client|clouds\.client|com\.mojang\.blaze3d" src/main/java/net/Gabou/projectatmosphere/modules/weathercell src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericSupportEvaluator.java src/main/java/net/Gabou/projectatmosphere/clouds/simulation src/main/java/net/Gabou/projectatmosphere/clouds/service
```

Result:

No matches.

WeatherCell simulation remains server/common safe.

The new support evaluator and native cloud atmosphere feedback controller do not import client-only classes.

## Build Results

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

## What Was Not Started

Tornado implementation was not started.

Hurricane implementation was not started.

Blizzard implementation was not started.

Cloud rendering was not modified.

Shaders were not modified.

Cloud morphology was not modified.

Precipitation rendering was not modified.

Lightning, hail, audio, particles, and visual storm effects were not implemented.

## Remaining Blockers Before Future Tornado/Hurricane Work

Future severe weather still needs a dedicated severe-weather owner above SUPERCELL.

Supercell-to-tornado logic is intentionally absent.

Cyclone and hurricane WeatherCell behavior is intentionally absent.

Blizzard WeatherCell behavior is intentionally absent.

WeatherCell-to-native-cloud linking remains optional and is not yet used for morphology.

WeatherCell sync remains minimal/deferred because no current client feature owns WeatherCell visualization or HUD behavior.

Native cloud rain feedback remains intentionally owned by WeatherCells, not by cloud regions, to avoid duplicate rain ownership.
