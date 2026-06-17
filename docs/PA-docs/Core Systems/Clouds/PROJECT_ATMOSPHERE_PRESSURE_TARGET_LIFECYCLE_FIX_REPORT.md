# Project Atmosphere Pressure Target Lifecycle Fix Report

Date: 2026-06-16

## 1. Files Modified

- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/RegionAtmosphereState.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericUpdateScheduler.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CycloneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/command/tree/service/CommandDebugService.java`
- `src/main/java/net/Gabou/projectatmosphere/telemetry/verification/VerificationReport.java`
- `src/main/java/net/Gabou/projectatmosphere/telemetry/verification/VerificationCollector.java`
- `src/main/java/net/Gabou/projectatmosphere/telemetry/verification/VerificationFormatter.java`

## 2. Root Cause Confirmation

Confirmed.

`Forecast Pressure` was sampled from the active weekly forecast path:

```text
ForecastRegion.samplePressure(gameTime)
```

The scheduler pressure target was sampled from the live atmosphere state:

```text
RegionAtmosphereState.getTargetPressure(...)
```

That live target used `forecastPressureProfile`, which was derived from `deriveDailyCurve(forecastRegion.getPressure(), basePressure)`. `deriveDailyCurve` samples `week[0]`, so the live state could keep a day-0 pressure shape even when the current weekly forecast day had moved to a different pressure range.

Result: the scheduler could push live pressure toward a stale target such as `990.6 hPa` while the current forecast sample was much higher, such as `1009.4 hPa`.

## 3. Pressure Target Sampling Fix

Implemented the target model as:

```text
raw day-0 pressure target = original copied daily profile sample
current forecast pressure = current weekly forecast day/time sample
diurnal shape offset = raw day-0 pressure target - day-0 daily mean
effective pressure target = current forecast pressure + diurnal shape offset
```

`RegionAtmosphereState.getTargetPressure(forecastTime)` now returns the effective current-forecast target instead of the raw day-0-only target.

`AtmosphericUpdateScheduler` now passes `level.getGameTime()` as the pressure forecast time when building scheduler state views and pressure diagnostics. Temperature and humidity still use day time for their existing daily curves.

## 4. Option Used

Used Option B.

The copied day-0 pressure profile is retained only as a local diurnal pressure shape. It is no longer the scheduler's absolute pressure target. The absolute center now comes from the current weekly forecast pressure sample.

## 5. Daily Pressure Variation Preservation

Daily pressure variation is preserved by subtracting the day-0 pressure profile mean from the raw profile sample and applying that offset to the current weekly forecast sample.

This keeps local pressure movement through the day without locking the region to day 0.

## 6. Stale Target Detection

`RegionAtmosphereState.PressureTargetDebug` now reports:

- Raw day-0 target
- Effective current-forecast target
- Current weekly forecast pressure sample
- Target day index
- Current forecast day index
- Whether the target uses the current forecast day
- Whether the day-0 profile is still active as a shape source
- Stale target correction delta

Stale target detection is currently flagged when the effective corrected target differs from the raw day-0 target by more than `3.0 hPa`.

Pressure anomaly classification reports one of:

- `stale unsupported target`
- `cyclone seed`
- `rain/storm system`
- `wind-imported gradient`
- `ocean-influenced low`
- `active forecast anomaly`
- `current forecast`
- `unknown`

## 7. Debug Fields Added

Updated `/pa debug pressure current` and `/pa debug verify` to expose:

```text
Forecast Pressure Current Sample
Live State Raw Pressure Target
Effective Pressure Target
Target Source
Target Day Index
Current Forecast Day Index
Target Uses Current Forecast Day
Day-0 Target Profile Active
Stale Target Detected
Stale Target Correction Delta
Pressure Anomaly Classification
```

The compact verification snapshot also includes machine-readable fields for these values.

## 8. Systems Confirmed Untouched

No intentional changes were made to:

- Forecast generation
- WeatherCell simulation or evolution
- Humidity model
- Cloud water model
- Temperature model
- Simple Clouds integration
- Cloud rendering
- Shader source files
- Cyclone thresholds
- Ocean pressure thresholds
- Wind simulation thresholds

Cyclone support diagnostics were updated only so pressure target sampling uses game time instead of stale day-time target sampling. Thresholds and lifecycle rules were not changed.

## 9. Build Results

Validation completed:

```powershell
.\gradlew compileJava
```

Result: successful.

```powershell
.\gradlew build
```

Result: successful.

Build warnings remain from existing deprecated APIs and mixin target warnings. No pressure-fix compile or build errors remain.

## 10. Manual Test Checklist

Run:

```mcfunction
/pa debug verify
/pa debug pressure current
/pa debug cyclone current
```

Expected:

- `Forecast Pressure Current Sample` matches the current weekly forecast pressure path.
- `Live State Raw Pressure Target` may still show the old day-0 profile sample for audit visibility.
- `Effective Pressure Target` follows the current forecast day plus the preserved diurnal offset.
- `Target Uses Current Forecast Day: yes` when weekly pressure data exists.
- `Day-0 Target Profile Active: yes` means day 0 is only being used as a diurnal shape source, not as the absolute target.
- `Stale Target Detected` reports whether the corrected target differs materially from the raw day-0 target.
- `Pressure Anomaly Classification` explains why a low target is retained, or reports `current forecast`.
- In the suspicious case, the scheduler should no longer push pressure toward `990.6 hPa` when the current forecast is near `1009.4 hPa` and there is no active support.

