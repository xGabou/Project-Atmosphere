# Project Atmosphere overview

## What it does
Project Atmosphere is a Minecraft Forge 1.20.1 weather and climate mod. It simulates regional temperature, humidity, pressure, wind, and cloud-driven weather instead of relying only on vanilla weather flags.

## Main features
- Season-aware temperature forecasting by biome/region.
- Regional humidity, pressure, and wind sampling.
- Simple Clouds integration for cloud-driven weather visuals.
- Weather world effects such as rain fire suppression and cauldron filling.
- Admin/debug commands for forecast inspection and weather testing.
- Optional biome temperature overrides through `config/projectatmosphere/biome_temps.json`.

## Current source features
The current source tree also contains severe-weather and fog systems such as tornadoes, hurricanes, and dynamic fog. Their exact release/support status for `0.8.0.0` is not fully documented in official release notes, so treat them as current-source behavior rather than fully documented release guarantees.

## Who it is for
- Players using climate or realism-focused Forge 1.20.1 packs.
- Pack/server admins who want weather diagnostics and configurable climate behavior.
- Mod integrators who want region-based weather data through the public API.

## High-level dependencies
- Required by `mods.toml`: Forge, Minecraft, Simple Clouds, Gabou's Libs.
- Officially documented optional integrations: Serene Seasons, Serene Seasons Extended, Pretty Rain.
- Observed current runtime note: the code will abort startup if no season provider is present. See the install and troubleshooting guides for details.

## Important compatibility notes
- Official latest note: `0.8.0.0` is temporarily incompatible with PA x TFC.
- Official latest note: Dynamic Trees integration remains work in progress and should stay disabled.
- Official loader support in repository metadata is Forge only.
