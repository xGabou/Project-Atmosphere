# PA Atmosphere Stabilization Pass Report

Date: 2026-06-15

## 1. Files Modified

- `src/main/java/net/Gabou/projectatmosphere/modules    /atmosphere/AtmosphericUpdateScheduler.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/HumidityBudgetService.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CloudWaterService.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericSupportEvaluator.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CycloneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CycloneImpactApplier.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellFormationController.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/weathercell/WeatherCellLifecycleController.java`

## 2. Pressure Issue Root Cause

Live pressure could fall far below forecast because several pressure-lowering paths stacked faster than forecast recovery could counter them. The highest-impact path was cyclone feedback: each active cyclone applied a large negative pressure delta every second without checking the current live-pressure deficit against forecast target pressure. Startup also seeded multiple initial cyclones even when the local atmospheric state did not have sustained cyclone support.

Atmospheric pressure recovery was also weak relative to storm forcing. The scheduler restored toward target pressure slowly and only applied a broad pressure guard after a larger deviation.

Implemented fixes:

- Increased pressure target recovery from `0.015` to `0.026`.
- Tightened pressure guard activation from `8 hPa` excess to `6 hPa`.
- Increased pressure guard correction factor from `0.12` to `0.18`.
- Increased pressure guard maximum correction from `2.5 hPa` to `3.5 hPa`.
- Increased cyclone spawn cooldown from `2` days to `4` days.
- Changed initial cyclone spawn from `3-8` random cyclones to at most `1` cyclone with an `18%` startup chance, and only when supported by strong local atmosphere.
- Required midnight cyclone spawning to use the same strong-support candidate filter.
- Replaced per-second cyclone pressure drops with forecast-aware pulses that stop once a region reaches its allowed pressure deficit.
- Reduced cyclone maximum pressure forcing base from `12 hPa` to `8 hPa`.
- Capped cyclone-supported pressure deficit by influence from about `12 hPa` to `38 hPa`, preserving extreme behavior for strong supported systems only.

## 3. Humidity Oscillation Root Cause

Humidity frequently hit the hard `+/-0.35` net-delta clamp because active atmospheric updates combined high wind transport scaling, rain exchange, evaporation, forecast correction, and precipitation removal into large per-cycle changes. This allowed regions to overshoot, then immediately correct back down, creating saturated/lower-state bouncing.

Implemented fixes:

- Reduced humidity net-delta clamp from `+/-0.35` to `+/-0.12`.
- Reduced active transport update multiplier from `20 ticks` to `2 ticks`.
- Reduced active transport scale from `1.0` to `0.62`.
- Reduced passive transport scale from `0.45` to `0.32`.
- Increased active relaxation factor from `0.0005` to `0.0012`.
- Increased passive relaxation factor from `0.0002` to `0.00035`.
- Reduced rain exchange humidity contribution from `0.005 + evaporationStrength * 0.5` to `0.0025 + evaporationStrength * 0.22`.
- Reduced cyclone humidity pulse from up to `0.6` per second to up to `0.018` per second.

Expected result: `humidityAfter > 1.0` should occur less often, and humidity net delta should no longer hit clamp limits across most samples.

## 4. Cloud Water Saturation Root Cause

Cloud water rose above `1.0` too often because condensation started aggressively once humidity exceeded a low target, precipitation drain was mild, and cyclone visual floors could force high cloud water over broad areas. The system could create cloud water faster than it consumed it.

Implemented fixes:

- Raised condensation supersaturation floor from `0.55` to `0.62`.
- Reduced condensation rate from `0.04 + cloudCover * 0.06` to `0.018 + cloudCover * 0.032`.
- Increased precipitation draw from `rain * min(cloudWater, 0.015 + rain * 0.02)` to `rain * min(cloudWater, 0.025 + rain * 0.045)`.
- Added extra cloud-water drain above `1.0`: `(cloudWater - 1.0) * (0.035 + rain * 0.055)`.
- Reduced cyclone cloud-water floor from `cloudCeil * 0.35 + rainCeil * 0.65` to `cloudCeil * 0.26 + rainCeil * 0.46`, capped at `1.05`.
- Reduced scheduler cloud-water delta clamp from `-0.08..0.08` to `-0.035..0.030`.

Expected result: normal cloudy regions should sit lower, rain-supportive regions should remain in the middle band, and `cloudWater > 1.0` should be limited to stronger severe support.

## 5. WeatherCell Threshold Findings

WeatherCell formation and promotion thresholds were permissive for the observed live state. Rain cells could form with modest humidity and low cloud water, thunderstorms could promote quickly, and pressure/cloud-water support ramps started too early.

Implemented fixes:

- Raised rain-cell formation threshold from `0.58` to `0.68`.
- Raised thunderstorm weather threshold from `0.42` to `0.56`.
- Raised severe weather threshold from `0.70` to `0.82`.
- Raised candidate humidity gate from `0.72` to `0.78`.
- Raised candidate cloud-water gate from `0.12` to `0.22`.
- Reduced local cell coverage cap from `0.70` to `0.58`.
- Reduced formation chance from `0.12 + score * 0.45`, capped at `0.52`, to `0.08 + score * 0.28`, capped at `0.32`.
- Reduced max active cells per region from `4` to `3`.
- Reduced max active cells near player from `12` to `8`.
- Added a `90` second minimum age before rain cells can promote to thunderstorms.
- Added a `180` second minimum age before thunderstorms can promote to supercells.
- Delayed pressure, humidity, cloud-water, and rain-support ramps so normal cloudy weather contributes less thunderstorm/severe support.

Expected result: rain cells remain possible, thunderstorms require stronger sustained support, and multiple nearby thunderstorms should be less common unless a real storm system exists.

## 6. Simple Clouds Backend Interaction Findings

No direct Simple Clouds rendering or cloud-shape code was changed.

Findings:

- Simple Clouds bridge snapshots are used for backend/cloud-service integration, but this pass found no direct atmosphere forcing added purely because Simple Clouds visuals exist.
- PA native cloud-region feedback is gated by active visual backend in `EventHandler`; PA native region ticking is not active when the visual backend is `SIMPLE_CLOUDS`.
- WeatherCells and the live atmosphere still run with Simple Clouds loaded. The instability observed with `Visual Backend: SIMPLE_CLOUDS` was therefore addressed by tuning the shared atmosphere and WeatherCell support path rather than modifying Simple Clouds rendering.

Expected result: Simple Clouds loaded should no longer make the atmosphere trend extreme simply because clouds are visually present.

## 7. Ocean Flux Findings

`oceanFlux = 0.0` can occur when the ocean basin manager has not produced a nonzero basin-weighted flux for the sampled region. This is valid for inland regions, but in water biomes it left moisture recovery dependent on other sources.

Implemented fix:

- Added a conservative water-biome fallback in `HumidityBudgetService`.
- The fallback only applies when the incoming ocean flux is effectively zero and the dominant biome id indicates ocean, river, beach, shore, mangrove, or swamp.
- The fallback targets at least `0.78` humidity but is capped at `0.0025` per update, so it provides gradual moisture without runaway supersaturation.

## 8. Values Changed

- `PRESSURE_TARGET_RESTORE`: `0.015` to `0.026`
- `PRESSURE_GUARD_THRESHOLD_HPA`: `8` to `6`
- `PRESSURE_GUARD_EXCESS_FACTOR`: `0.12` to `0.18`
- `PRESSURE_GUARD_MAX_DELTA`: `2.5` to `3.5`
- Active update `relaxFactor`: `0.0005` to `0.0012`
- Active update `transportScale`: `1.0` to `0.62`
- Active update `transportMultiplier`: `20` to `2`
- Passive update `relaxFactor`: `0.0002` to `0.00035`
- Passive update `transportScale`: `0.45` to `0.32`
- Humidity net-delta clamp: `+/-0.35` to `+/-0.12`
- Cloud-water delta clamp: `-0.08..0.08` to `-0.035..0.030`
- Cloud-water supersaturation floor: `0.55` to `0.62`
- Condensation rate: `0.04 + cloudCover * 0.06` to `0.018 + cloudCover * 0.032`
- Precipitation draw: `0.015 + rain * 0.02` to `0.025 + rain * 0.045`
- Excess cloud-water drain above `1.0`: added
- Rain exchange humidity factor: `0.005 + evaporationStrength * 0.5` to `0.0025 + evaporationStrength * 0.22`
- Water-biome fallback ocean flux cap: added `0.0025`
- `WEATHER_THUNDER_THRESHOLD`: `0.42` to `0.56`
- `WEATHER_SEVERE_THRESHOLD`: `0.70` to `0.82`
- `RAIN_CELL_FORMATION_THRESHOLD`: `0.58` to `0.68`
- WeatherCell candidate humidity gate: `0.72` to `0.78`
- WeatherCell candidate cloud-water gate: `0.12` to `0.22`
- WeatherCell candidate coverage cap: `0.70` to `0.58`
- WeatherCell formation chance cap: `0.52` to `0.32`
- Max active cells per region: `4` to `3`
- Max active cells near player: `12` to `8`
- Rain-to-thunder minimum age: added `90` seconds
- Thunder-to-supercell minimum age: added `180` seconds
- Cyclone cooldown: `2` days to `4` days
- Initial cyclone spawn count: `3-8` to at most `1`, support-gated, `18%` startup chance
- Cyclone pressure pulse maximum base: `12 hPa` to `8 hPa`
- Cyclone humidity pulse cap: `0.6` to `0.018`
- Cyclone temperature pulse cap: `-8` to `-0.18`
- Cyclone cloud-water floor: `cloudCeil * 0.35 + rainCeil * 0.65` to `cloudCeil * 0.26 + rainCeil * 0.46`, capped at `1.05`

## 9. Systems Confirmed Untouched

- Cloud rendering
- Cloud morphology
- Simple Clouds rendering
- Cloud backend migration
- Shader code
- Tornado systems
- Hurricane systems
- Blizzard systems
- Distant Horizons integration
- Iris/Oculus shader integration
- Forecast command behavior

## 10. Build Results

- `.\gradlew compileJava`: passed
- `.\gradlew build`: passed

Notes:

- Build produced existing deprecation and mixin target warnings.
- No compile or test failures were observed.

## 11. Recommended Manual Test Checklist

With Simple Clouds loaded:

- Run `/pa debug verify`.
- Check pressure delta. Normal cloudy/rainy weather should usually stay around `-10` to `-25 hPa`, not casually reach `-47 hPa`.
- Check cloud water. It should not sit above `1.0` unless severe weather is active.
- Check humidity. It should not constantly exceed `1.0`.
- Check WeatherCell count. Thunderstorms should not appear everywhere.
- Check visual cloud density. Clouds should not feel overloaded solely because Simple Clouds is loaded.

Without Simple Clouds:

- Run `/pa debug verify`.
- Confirm PA native mode does not become much weaker or much stronger than Simple Clouds mode.
- Check that pressure, cloud water, humidity, and WeatherCell counts stay in similar ranges for comparable weather.

Restart test:

- Save and reload.
- Run `/pa debug verify` after reload.
- Confirm values do not reset into an extreme storm state.
- Confirm startup no longer seeds multiple unsupported cyclones.

Telemetry expectations after this pass:

- `pressureAfter` should rarely be below target by more than `30 hPa`.
- `pressureAfter` below target by more than `40 hPa` should be rare and tied to strong sustained support.
- `humidityAfter > 1.0` should be reduced.
- `cloudWaterAfter > 1.0` should be reduced.
- Humidity `netDelta` should not constantly hit the clamp.
- WeatherCells should not overproduce thunderstorms in normal cloudy weather.
