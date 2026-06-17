# FAQ

## Which Minecraft version and loader are supported?
Repository metadata currently targets Minecraft `1.20.1` on Forge.

## Does Project Atmosphere require Simple Clouds?
Yes. `simpleclouds` is a mandatory dependency in `mods.toml`.

## Does it require Serene Seasons?
Official docs describe Serene Seasons support, but current code actually requires some season provider at startup. The code names Serene Seasons, PA x TFC, and Ecliptic Seasons as accepted providers. Official support for the non-Serene options still needs confirmation.

## Does `0.8.0.0` work with PA x TFC?
Not officially. The `0.8.0.0` changelog says it is temporarily incompatible.

## Does it support Dynamic Trees?
There is integration code, but the latest official note says the module is still work in progress and should always be disabled.

## How do I inspect current weather data?
Use the `/pa` commands. Start here:
- `/pa help`
- `/pa status`
- `/pa forecast current`
- `/pa forecast week`
- `/pa temperature current`
- `/pa humidity current`
- `/pa pressure current`
- `/pa wind current`
- `/pa fog info`

Legacy command paths still exist for compatibility, but they are listed here only as fallback:
- `/pa weatherdebug forecast`
- `/pa weatherdebug fog`
- `/pa windSpeed get`

## How do I force fog for testing?
In the current source tree, use `/pa fog spawn [strength] [seconds]` and `/pa fog clear`.

## How do I override biome temperatures?
Edit `config/projectatmosphere/biome_temps.json`. You can set either one `all` range for a biome or explicit `winter`, `spring`, `summer`, and `autumn` min/max ranges.

## Does it support Fabric or NeoForge?
No official support for Fabric or NeoForge is documented in this bundle.
