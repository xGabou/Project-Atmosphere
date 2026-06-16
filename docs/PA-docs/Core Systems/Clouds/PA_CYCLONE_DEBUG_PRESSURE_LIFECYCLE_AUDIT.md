# Project Atmosphere Cyclone Debug Commands And Pressure Lifecycle Audit

Date: 2026-06-16

## 1. Files Modified

- `src/main/java/net/Gabou/projectatmosphere/command/tree/PaDebugCommand.java`
- `src/main/java/net/Gabou/projectatmosphere/command/tree/service/CommandDebugService.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CycloneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CycloneSnapshot.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/RegionAtmosphereState.java`
- `docs/PA-docs/Core Systems/Clouds/PA_CYCLONE_DEBUG_PRESSURE_LIFECYCLE_AUDIT.md`

## 2. Commands Added

- `/pa debug cyclone`
- `/pa debug cyclone current`
- `/pa debug cyclone region`
- `/pa debug cyclone nearest`
- `/pa debug cyclone list`
- `/pa debug pressure`
- `/pa debug pressure current`
- `/pa debug pressure region`

The bare `cyclone` command aliases `cyclone current`.

The bare `pressure` command aliases `pressure current`.

## 3. Output Format For Cyclone Debug

`/pa debug cyclone current` and `/pa debug cyclone region` report:

```text
Region
Biome
Live Pressure
Forecast Pressure
Pressure Target
Pressure Anomaly To Normal
Pressure Anomaly To Forecast
Cyclone Active
Cyclone Id
Cyclone Distance
Cyclone Pressure Influence
Cyclone Seed Eligible
Cyclone Seed Support
Cyclone Intensification Support
Cyclone Severe Support
Humidity
Cloud Water
Rain Intensity
Storm Pressure Support
Thunderstorm Support
Supercell Support
Convergence
Ocean Flux
Ocean Pressure Influence
Spawn Cooldown
Regional Cyclone Cap
Nearest Cyclone Distance
Can Spawn Seed
Blocked Reason
```

`/pa debug cyclone nearest` reports:

```text
Nearest Cyclone
Distance
Influence at player
Pressure influence
Stage
Intensity
```

`/pa debug cyclone list` reports:

```text
Total Active Cyclones
Cyclone id
Center Region
Center Position
Age
Stage
Intensity
Pressure Drop
Radius
Movement Vector
Humidity Support
Cloud Water Support
Seed Support
Intensification Support
Severe Support
Remaining Lifetime
```

If no cyclone systems exist, it reports:

```text
No active cyclone systems.
```

## 4. Output Format For Pressure Debug

`/pa debug pressure current` and `/pa debug pressure region` report:

```text
Region
Live Pressure
Forecast Pressure
Pressure Target
Raw Pressure Target
Effective Pressure Target
Normal Pressure Reference
Base Pressure
Season Pressure Offset
Pressure Target Source
Target Curve Index
Target Curve Previous Point
Target Curve Next Point
Target Curve Interpolation Factor
Pressure Target Support Gated
Scheduler Pressure Delta
Recovery Pressure Delta
Pressure Guard Delta
Base Relax Pressure Delta
Rain Pressure Delta
Wind Pressure Mix Delta
Ocean Pressure Influence
Cyclone Pressure Influence
Unsupported Low Recovery Delta
Pressure Recovery Eligible
Unsupported Low Recovery Active
Support Resistance
Forecast/Target Note
```

The command intentionally separates:

- raw live-state pressure target
- effective pressure target with current season pressure offset
- displayed current weekly forecast pressure
- scheduler, guard, wind, ocean, cyclone, and unsupported-low terms

## 5. Why Pressure Target Can Differ From Forecast Pressure

`Forecast Pressure` and `Pressure Target` currently come from different sampling paths.

`Forecast Pressure` is sampled from the active forecast region:

```text
ForecastRegion.samplePressure(gameTime)
DefaultRegionCurves.sampleTwoColumn(pressureWeek, gameTime)
pressureWeek[current game day % week length]
linear interpolation from that day's pressure min/max
```

`Pressure Target` is sampled from live regional atmosphere state:

```text
RegionAtmosphereState.getTargetPressure(dayTime)
RegionAtmosphereState.pressureTargetDebug(dayTime)
forecastPressureProfile
```

That `forecastPressureProfile` is initialized when the live state is built:

```text
deriveDailyCurve(forecastRegion.getPressure(), basePressure)
```

The important detail: `deriveDailyCurve` uses `week[0]`, not the current forecast day. It builds a single daily pressure curve from the first forecast day and resamples that into the live state profile. Later, `getTargetPressure(dayTime)` samples that copied daily profile by time-of-day only.

Therefore a debug state like this is explainable:

```text
Forecast Pressure: 1009.4 hPa
Pressure Target: 990.6 hPa
```

It means the displayed forecast pressure is the current weekly forecast sample, while the scheduler target is the live state's copied day-0 daily target curve. If day 0 contained a strong low at the current time-of-day, the scheduler can still push toward that low even when the current weekly forecast sample is higher.

This also means the low target can be stale relative to the current forecast day. It is not necessarily caused by an active cyclone, rain cell, ocean effect, or season offset.

## 6. Current Low Pressure Cause Classification

For the observed case:

```text
Forecast Pressure: 1009.4 hPa
Live Pressure: 1001.8 hPa
Pressure Target: 990.6 hPa
Rain Intensity: 0.00
Cloud Water: 0.00
WeatherCells: 0
Cyclone Pressure Influence: +0.0 hPa
Storm Pressure Support: 0.15
Thunderstorm Support: 0.02
Cyclone Seed Eligible: no
```

The most likely cause is the target curve, not an active weather system.

Findings:

- Cyclone: not the cause when cyclone pressure influence is `+0.0 hPa` and no active cyclone is nearby.
- WeatherCells/rain: not the cause when WeatherCells are `0` and rain intensity is `0.00`.
- Season pressure offset: not the source of the raw `Pressure Target`; it is displayed separately as an effective target diagnostic.
- Wind: can contribute a separate pressure transport delta, but it does not explain the raw pressure target being `990.6 hPa`.
- Ocean: can contribute separate ocean pressure influence, but it does not explain the raw pressure target unless the debug command shows a negative ocean pressure term.
- Stale forecast/live-state target: likely when current weekly forecast pressure is much higher than the live state's copied daily target curve.

## 7. Unsupported Low Recovery Recommendation

Do not increase unsupported-low recovery as the primary fix.

The current weak recovery term should remain conservative for now because it prevents fully unsupported deficits from persisting forever, but it should not be treated as the lifecycle model for low pressure.

The better fix is to classify low pressure by cause:

- active cyclone or low-pressure disturbance
- active rain or storm system
- imported pressure gradient from wind or neighbors
- forecast pressure anomaly
- stale/unexplained copied live-state target

Once classified, unsupported low pressure should either become a weak disturbance when enough organization exists, or slowly decay/redistribute when no support exists.

## 8. Proposed Pressure System Lifecycle Model

Recommended next pressure model:

1. `Forecast anomaly`: forecast curve may request a low target, but it should carry time/location identity instead of acting as an unexplained passive pull.
2. `Seed disturbance`: if pressure is low and humidity/cloud water/convergence/moisture support exist, convert the anomaly into a weak low-pressure disturbance.
3. `Organized low`: if support persists, maintain or intensify the disturbance and allow pressure to remain low.
4. `Storm-producing system`: only strong thunderstorm/supercell support should drive severe organization.
5. `Gradient import`: wind and neighboring pressure gradients may move anomalies rather than erase them.
6. `Stale unsupported anomaly`: if no rain, cyclone, humidity, cloud water, convergence, ocean, or gradient support exists, weaken the anomaly slowly or resample the live target from the current forecast day.

This preserves low pressure while preventing invisible stale target curves from acting like permanent weather systems.

## 9. Systems Confirmed Untouched

This pass did not intentionally change:

- pressure recovery strength
- WeatherCell thresholds
- humidity stabilization
- cloud water stabilization
- temperature behavior
- season temperature behavior
- forecast generation
- Simple Clouds integration
- cloud rendering
- PA native cloud morphology
- tornado systems
- hurricane systems
- blizzard systems

The only non-command data shape change is diagnostic: `CycloneSnapshot` now exposes cyclone age and latest movement vector for debug output.

## 10. Build Results

Commands run:

```powershell
.\gradlew compileJava
.\gradlew build
```

Results:

```text
compileJava: passed
build: passed
```

Notes:

- Build completed successfully.
- Existing warnings remain, mostly deprecated `ResourceLocation` constructor usage and public Mixin target warnings.
- No new compile failures were introduced.

## 11. Manual Test Checklist

Run in an Overworld test world:

```mcfunction
/pa debug verify
/pa debug pressure current
/pa debug cyclone current
/pa debug cyclone nearest
/pa debug cyclone list
```

For the suspicious pressure case, compare:

```text
Forecast Pressure
Pressure Target
Raw Pressure Target
Effective Pressure Target
Target Curve Previous Point
Target Curve Next Point
Target Curve Interpolation Factor
Scheduler Pressure Delta
Recovery Pressure Delta
Pressure Guard Delta
Wind Pressure Mix Delta
Ocean Pressure Influence
Cyclone Pressure Influence
Unsupported Low Recovery Delta
```

Expected diagnostic behavior:

- If `Forecast Pressure` is much higher than `Pressure Target`, the command should show the target source as `region-daily-pressure-profile`.
- If no cyclone exists, `/pa debug cyclone nearest` should report `Nearest Cyclone: none`.
- If a low target exists without support, `/pa debug cyclone current` should explain why seed spawning is blocked.
- If a cyclone exists, `/pa debug cyclone list` should show its id, stage, pressure drop, radius, movement vector, and support scores.

Recommended next implementation prompt:

```text
Refactor pressure target lifecycle so RegionAtmosphereState does not use a stale day-0-only pressure profile as the scheduler target. Preserve forecast variation, but classify low-pressure targets as active forecast anomalies, seed disturbances, organized lows, gradient imports, or stale unsupported anomalies. Do not increase global recovery. Use /pa debug pressure current and /pa debug cyclone current outputs to validate each classification.
```
