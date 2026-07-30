# Project Atmosphere 0.9.1 Alpha-1.21.1

## Atmospheric Continuity Update

Project Atmosphere 0.9.1 improves weather persistence, seasonal behavior, pressure logic, cyclone formation, cloud simulation, and debug tools.

This update is mostly backend focused, but it builds the foundation for future weather systems.

## Persistent Atmosphere

Weather now survives server restarts instead of resetting to forecast defaults.

Persistent systems include:

* Temperature
* Humidity
* Pressure
* Wind
* Cloud water
* Cloud cover
* Rain intensity
* Ocean state
* Cyclone state
* Seasonal drift
* WeatherCells

Weather now continues evolving between play sessions.

## Forecasts And Seasons

Forecasts now act as a climate baseline instead of being regenerated during normal season changes.

Forecast regeneration is now limited to missing data, corrupt data recovery, and admin or debug commands.

Season changes now gradually influence the live atmosphere instead of rebuilding it.

Stored forecasts are now season neutral. The current season offset is applied when forecast targets are sampled, so the atmosphere follows the current season instead of stale values.

Season compatibility was also improved through the season delegate system.

## Pressure Lifecycle

Pressure behavior was reworked so stale low pressure does not last forever, while real weather systems can still persist.

Pressure now follows a cleaner lifecycle:

* Forecast pressure remains the dynamic target
* Active low pressure systems can persist
* Unsupported stale lows recover slowly
* Pressure gradients remain meaningful
* Pressure is no longer forced back to normal

Unsupported low pressure recovery is capped at 2 hPa per Minecraft day and is resisted by rain, storms, cyclones, ocean influence, wind, humidity, cloud water, and convergence.

## Cyclone Seeds

Cyclone formation was reworked so weak low pressure disturbances can form earlier.

The new lifecycle separates weak seed formation, cyclone intensification, and severe storm development.

Cyclone seeds now depend more on low pressure anomaly, humidity, cloud water, convergence, wind organization, and moisture sources.

## WeatherCells And Rain Cells

A new WeatherCell layer was added between the persistent atmosphere and future weather effects.

Only Rain Cells are active for now, but this system is planned to support future thunderstorms, supercells, cyclones, and blizzards.

Rain Cells can now:

* Form from atmospheric support
* Move with wind
* Age and decay naturally
* Increase local rainfall
* Consume cloud water
* Persist across restarts

## Clouds

Cloud formation now better accounts for humidity, cloud water, temperature, pressure, and wind convergence.

Cloud water consumption and balancing were improved to reduce runaway humidity and excessive cloud growth.

## Dedicated Server Stability

Several cloud related client and server issues were fixed.

This includes better dedicated server safety, improved client gating for cloud sync, and removal of client cache references from common server code.

## Debug Tools

Debug tools now expose more details about seasonal targets, pressure recovery, cyclone seed eligibility, storm support, ocean influence, wind pressure mixing, and live vs forecast deltas.

New debug commands:

```mcfunction
/pa debug cyclone
/pa debug cyclone current
/pa debug cyclone region
/pa debug cyclone nearest
/pa debug cyclone list
/pa debug pressure
/pa debug pressure current
/pa debug pressure region
```

## Summary

Project Atmosphere now treats forecasts as the climate baseline and the persistent atmosphere as the live evolving weather state.

Season changes no longer rebuild the atmosphere.

Pressure, wind, rain, cloud water, cyclones, ocean influence, and seasonal drift now persist and evolve more naturally over time.

# Update 0.9.1 Alpha will be released to~~morrow~~ night.

I need to head to bed now,

Thanks for your patience.