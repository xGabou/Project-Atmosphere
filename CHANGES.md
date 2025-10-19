# Project Atmosphere — Developer Change Log
This file records functionality additions/removals made during development sessions, annotated with the current version from `gradle.properties` at the time of change.

## 0.5.5.2 — Imperial Units Mode (2025-10-19)
- Added config option `display.imperialUnits` to toggle display units.
- Overlay and commands now respect units:
  - Temperature shows as °F when enabled (°C otherwise).
  - Wind speed shows as mph when enabled (m/s otherwise).
  - Pressure shows as inHg when enabled (hPa otherwise).
- In-game config screen adds an “Imperial Units” toggle under Display.

## 0.5.4.4 — Added weatherdebug cloud command (2025-10-17)
- Added command: `/weatherdebug cloud <id>`
  - Spawns the specified SimpleClouds cloud at the player’s position/biome.
  - Requires permission level 2.
  - Applies current wind sample; fails gracefully if SimpleClouds is not initialized.

