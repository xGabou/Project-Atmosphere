# Project Atmosphere — Developer Change Log
This file records functionality additions/removals made during development sessions, annotated with the current version from `gradle.properties` at the time of change.

## 0.5.5.7 – Aurora & rainbow integration (2025-11-07)
- Added optional compatibility hooks for the Auroras and Rainbows mods.
  - Aurora brightness now scales with Serene Seasons data and is boosted in freezing biomes.
  - Rainbows rely on the Project Atmosphere / Serene Seasons Plus rain helper so they only trigger after custom storms clear.
- Introduced guarded client mixins plus a rain-state tracker so these integrations activate only when the companion mods are installed.
- Refined aurora and rainbow compatibility syncing.
  - Aurora brightness now queries Project Atmosphere’s live temperature data (or active temperature mods) instead of static biome values.
  - Rainbows receive server-synchronised rainfall intensity from SimpleClouds spawns/despawns, allowing accurate rain stop triggers across dimensions and for joining players.

## 0.5.5.4 – Non-vanilla biome resolution (2025-10-24)
- BiomeTempConfig now resolves un-namespaced biome keys by scanning the biome registry.
  - Non-vanilla biomes defined without a namespace (e.g., `bog`) resolve to their mod ids when uniquely found (e.g., `biomesoplenty:bog`).
  - If multiple mods provide the same path, mappings apply to all matches and an info log is emitted.
  - If no match is found, falls back to `minecraft:<path>` and logs a warning.
  - Applies to `putAllSeasons`, `putConstSeasons`, and `mirrorBiome`.

## 0.5.5.2 — Imperial Units Mode (2025-10-19)
- Added config option `display.imperialUnits` to toggle display units.
- Overlay and commands now respect units:
  - Temperature shows as °F when enabled (°C otherwise).
  - Wind speed shows as mph when enabled (m/s otherwise).
  - Pressure shows as inHg when enabled (hPa otherwise).
- In-game config screen adds an “Imperial Units” toggle under Display.
- Regeneration safety: clearing/regenerating forecasts now pauses dependent ticks (wind physics, tornado/hurricane/snowstorm managers), and defers scheduled tornado checks until regeneration completes.

## 0.5.4.4 — Added weatherdebug cloud command (2025-10-17)
- Added command: `/weatherdebug cloud <id>`
  - Spawns the specified SimpleClouds cloud at the player’s position/biome.
  - Requires permission level 2.
  - Applies current wind sample; fails gracefully if SimpleClouds is not initialized.
