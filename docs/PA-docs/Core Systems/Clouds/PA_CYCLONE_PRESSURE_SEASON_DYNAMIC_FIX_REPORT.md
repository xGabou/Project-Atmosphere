# PA Cyclone Seed, Unsupported Low Recovery, And Dynamic Seasonal Forecast Offset Fix

Date: 2026-06-16

## 1. Files Modified

- `src/main/java/net/Gabou/projectatmosphere/manager/ForecastOrchestrator.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericUpdateScheduler.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CycloneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/RegionAtmosphereState.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/SeasonalAtmosphericDrift.java`
- `src/main/java/net/Gabou/projectatmosphere/telemetry/verification/VerificationCollector.java`
- `src/main/java/net/Gabou/projectatmosphere/telemetry/verification/VerificationFormatter.java`
- `src/main/java/net/Gabou/projectatmosphere/telemetry/verification/VerificationReport.java`
- `docs/PA-docs/Core Systems/Clouds/PA_CYCLONE_PRESSURE_SEASON_DYNAMIC_FIX_REPORT.md`

The worktree already contained unrelated uncommitted changes from earlier stabilization and cloud passes. This report lists the files touched by this implementation pass.

## 2. Old Cyclone Gate And Why It Blocked Formation

The previous cyclone candidate gate required mature severe-storm conditions before a cyclone could spawn:

```java
support.humidity() >= 0.88F
support.cloudWater() >= 0.65F
support.thunderstormSupport() >= 0.72F
support.supercellSupport() >= 0.48F
```

That made cyclone formation backwards. Cyclone-scale low-pressure organization could not appear until the environment was already a strong thunderstorm/supercell environment. In practice, weak lows had no path to organize into a cyclone seed, so unsupported low pressure could linger without becoming a system.

## 3. New Cyclone Seed Gate

Cyclone seed eligibility is now evaluated by `CycloneManager.evaluateCycloneSupport(...)`.

Seed support uses:

- humidity support from `0.70..0.88`;
- cloud water support from `0.25..0.65`;
- pressure anomaly support from `6..18 hPa`;
- existing storm pressure support;
- convergence/wind organization;
- ocean/moisture bonus from positive ocean humidity flux or low-pressure ocean influence.

Seed eligibility requires:

- humidity at least `0.70`;
- cloud water at least `0.25`;
- pressure anomaly at least `8 hPa`;
- seed support at least `0.52`;
- at least one organizing source: convergence, storm-pressure support, or ocean/moisture bonus.

Thunderstorm and supercell support are no longer mandatory for seed formation.

## 4. New Cyclone Intensification Logic

Each cyclone now evaluates nearby environmental support during its tick.

Intensity tendency:

- weak unsupported seeds decay;
- seed support above the seed threshold can maintain or slowly grow the system;
- intensification support above `0.62` grows it more;
- severe support above `0.70` grows it further.

This separates initial disturbance formation from later organization.

## 5. New Severe Cyclone Logic

Severe support now uses thunderstorm/supercell support as an intensification path instead of a spawn blocker.

Severe support is weighted from:

- thunderstorm support;
- supercell support;
- pressure anomaly;
- convergence support.

Only this severe support contributes to the strongest intensification tendency. Weak cyclone seeds can exist without being severe.

## 6. Unsupported-Low Recovery Formula

Unsupported low recovery was added in `AtmosphericUpdateScheduler`.

The extra pressure term is computed after the existing rain, forecast-recovery, and guard pressure terms:

```text
deficitToNormal = max(0, 1013.25 - livePressure)
supportResistance = max(rain, stormPressure, thunderstorm, supercell, cyclone, cycloneSeed, oceanLowPressure, windImport, cloudWater, humidity, convergence)
recoveryFactor = clamp(1 - supportResistance, 0, 1)
recovery = deficitToNormal * 0.002 * recoveryFactor
recovery = clamp(recovery, 0, maxRecoveryThisUpdate)
```

Recovery only applies when the calculated support resistance is low enough and the capped recovery delta is positive.

## 7. Recovery Rate In hPa Per Minecraft Day

The cap is:

```text
2.0 hPa per Minecraft day
```

The per-update cap is scaled by update cadence:

- active regions: `2.0 * 20 / 24000 hPa` per active update;
- passive regions: `2.0 * 100 / 24000 hPa` per passive update.

This is intentionally conservative and does not force pressure directly to `1013.25 hPa`.

## 8. Where Dynamic Season Offset Is Applied

Dynamic season temperature offset is now applied at forecast target access:

- `RegionAtmosphereState.getTargetTemperature(...)` returns base target plus current seasonal temperature offset.
- `ForecastOrchestrator.getCurrentTemperature(ServerLevel, BlockPos, long)` returns sampled forecast plus current seasonal temperature offset.
- `ForecastOrchestrator.getCurrentTemperature(RegionInstanceKey, long)` applies the same offset when falling back to stored forecast data.
- `RegionAtmosphereState.getBaselineMinTemperature()` and `getBaselineMaxTemperature()` return seasonal effective baselines for scheduler sunlight-driven temperature targeting.
- `RegionAtmosphereState.relaxTowardBase(...)` and `relaxTemperatureAndPressureTowardBase(...)` now relax temperature toward `baseTemperature + currentSeasonOffset`.

## 9. Whether Stored Forecast Remains Season-Neutral

Stored forecast data remains season-neutral.

No forecast arrays are rewritten. No forecast regeneration was added. The season-adjusted temperature is computed dynamically at read time.

## 10. Effective Forecast Temperature Formula

The effective target is:

```text
effectiveForecastTemperature = baseForecastTemperature + SeasonalAtmosphericDrift.currentTemperatureOffsetC()
```

`RegionAtmosphereState.getBaseTargetTemperature(...)` exposes the raw stored forecast target for diagnostics.

## 11. How Double Application Was Avoided

Before this pass, `SeasonalAtmosphericDrift` separately added the season temperature offset while the scheduler still chased raw non-seasonal targets.

After this pass:

- `RegionAtmosphereState.getTargetTemperature(...)` includes the seasonal offset.
- `SeasonalAtmosphericDrift.applyToState(...)` uses `state.getTargetTemperature(...)` directly.
- The explicit `+ currentSnapshot.temperatureOffset()` path was removed from drift temperature targeting.

The offset is applied once through the effective target accessor.

## 12. Whether Base Relaxation Was Corrected

Yes.

Base temperature relaxation now targets:

```text
baseTemperature + currentSeasonOffset
```

Pressure base relaxation is unchanged.

## 13. Debug Verify Fields Added

Added or expanded `/pa debug verify` fields:

- `Cyclone Seed Eligible`
- `Cyclone Seed Support`
- `Cyclone Intensification Support`
- `Cyclone Severe Support`
- `Unsupported Low Recovery Active`
- `Unsupported Low Recovery Delta`
- `Unsupported Low Recovery Cap`
- `Support Resistance`
- `Base Forecast Temperature`
- `Season Temperature Offset`
- `Effective Forecast Temperature`
- `Delta To Base Forecast`
- `Delta To Effective Forecast`
- `Scheduler Temperature Delta`
- `Base Relax Temperature Delta`
- `Seasonal Drift Temperature Delta`

Existing pressure fields were kept.

## 14. Systems Confirmed Untouched

No intentional changes were made to:

- cloud rendering;
- cloud morphology;
- Simple Clouds integration;
- forecast generation;
- humidity stabilization;
- cloud water stabilization;
- WeatherCell thresholds;
- wind system behavior;
- tornado systems;
- hurricane systems;
- blizzard systems.

## 15. Build Results

Commands run:

```powershell
.\gradlew compileJava
.\gradlew build
```

Results:

- `compileJava`: passed.
- `build`: passed.

Warnings remain from existing deprecated `ResourceLocation` constructors and mixin target declarations.

## 16. Manual Test Checklist

### Test 1: Weak Supported Low

- Find a low-pressure region with moderate humidity/cloud water and weak thunderstorm/supercell support.
- Expected:
  - `Cyclone Seed Eligible: yes`
  - weak disturbance may form
  - `Cyclone Severe Support` remains low

### Test 2: Unsupported Low

- Find a low-pressure region with no rain, low cloud water, low humidity, no cyclone seed support, and low thunderstorm support.
- Expected:
  - `Unsupported Low Recovery Active: yes`
  - pressure rises slowly
  - recovery delta is small and capped

### Test 3: Mature Storm

- Find active rain/thunderstorm/severe support.
- Expected:
  - unsupported recovery disabled or heavily resisted
  - low pressure can remain
  - cyclone can intensify if support persists

### Test 4: Long-Term World

- Advance several in-game days.
- Expected:
  - pressure does not globally flatten
  - low-pressure systems still form
  - unsupported stale lows do not persist forever

### Test 5: Dynamic Seasonal Forecast Offset

- Pick a savanna region.
- Record in winter:
  - `Base Forecast Temperature`
  - `Season Temperature Offset`
  - `Effective Forecast Temperature`
  - `Live Temperature`
  - `Delta To Effective Forecast`
- Switch to summer and wait enough ticks for live drift.
- Expected:
  - effective forecast changes immediately;
  - live temperature trends gradually toward it;
  - savanna winter and summer differ meaningfully.

Repeat in forest and cold biomes.
