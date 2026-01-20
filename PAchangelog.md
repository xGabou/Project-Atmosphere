# Project Atmosphere

### Added
- TFC canyons, TFC salt marshes biomes support.
- Crafting recipes for dust and sand layers.
- Wind bending for realistic particles (smoke, ash, dust, snowflakes).
- Telemetry logging for cloud history and periodic region forecast/state samples.
### Changed
- Default constructor to support older versions of Forge
- Pressure/temperature limits tuned for more realistic values.
- Sunlight curve/seasonal tilt updated for stronger midday and deeper winter dimming.
- Forecast data now persists across world reloads instead of regenerating every time.
- Missing-forecast warnings now log once per biome to reduce spam.
- Storm siren behavior updated for severity 7 storm warnings and tornado proximity alerts.

### Fixed
- Avoided loading client-only Aurora/Rainbows classes on servers by switching mixins to string targets.
- Prevented client-only mixins from loading on dedicated servers.
- Thermometer recipe output corrected.
- Cloud telemetry logging aligned with updated signatures.
- Fixed wind-bent particle mixin compatibility by replacing the switch expression with explicit checks.

### Notes

## ~~AS ALWAYS UPDATING TO THIS VERSION REQUIRES DELETING YOUR OVERWORLD FOLDER (created by Project Atmosphere) TO AVOID ISSUES WITH THE NEW REGION SYSTEM.~~
- ~~Go to your worlds folder and delete the "overworld" folder to allow the new region system to generate fresh regions.~~
- ~~Note that this will reset the current forecast and could introduce desynced weather effects if the forecast isn't regenerated properly. (/temperature regenerate)~~
