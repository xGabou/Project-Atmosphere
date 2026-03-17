# Project Atmosphere 0.8.0.0

Project Atmosphere `0.8.0.0` continues the large internal refactor of the forecast and runtime systems, focusing on simulation stability, improved diagnostics, and better atmosphere state correctness.

This update stabilizes the new region-first runtime architecture while introducing deeper telemetry tools and improved safeguards for live atmospheric evolution.

> **Note:**  
> This update is **temporarily incompatible with PA x TFC**.  
> A compatibility update is already planned and will restore support soon.

---

## Changes

### Forecast and Runtime
- Continued the transition to a **region-first forecast runtime architecture**.
- Reduced reliance on legacy biome-key runtime systems.
- Forecast baselines have been stabilized as part of the ongoing refactor.

### Atmosphere Simulation
- Added **humidity budget instrumentation**, including ocean and wind integration.
- Completed the **cloud-water coupling system** for humidity simulation.
- Added **pressure restore toward forecast targets** with soft deviation guards and anomaly reporting.
- Added **temperature restore toward forecast targets** with soft deviation guards and anomaly reporting.
- Reworked **cyclone and cloud ownership logic** so cyclone-driven storm visuals are no longer immediately overwritten by cloud sampling.

### Telemetry and Debugging
- Added new telemetry exports for **atmosphere coupling**, including target vs live values for:
    - Temperature
    - Pressure
    - Humidity
- Added **humidity budget diagnostics** and improved tracking of cloud-water interactions.
- Added anomaly markers to help diagnose **pressure and temperature drift** during runtime.

### Compatibility
- **Dynamic Trees compatibility remains optional**, allowing the mod to load normally even if Dynamic Trees is not installed.(This module is a work in progress you should always disable it)

### Fixes
- Fixed a **telemetry export crash on JDK 17** caused by Gson reflective access to `java.time.Instant`.  
  Explicit serialization for `Instant` is now used.

---

## Notes
- This release contains **no client or HUD changes**.
- Build verified successfully on **JDK 17**.

---

## Ongoing Work
- Forecast generation is now in a stable state.
- Current tuning work focuses on **live runtime evolution**, particularly temperature and humidity oscillations observed in some active regions.