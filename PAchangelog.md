# Project Atmosphere

### Added
- Pluggable season bridge (Serene Seasons by default, PA-for-TFC placeholder otherwise) with a neutral fallback.
- Aurora/rainbow shader flags and positions on the client so shader packs can react.

### Fixed / Behavior
- Auroras render only on cold nights; rainbows only when rain stops and rain level is zero.
- Tornado shader binds live SimpleClouds clouds and densifies alpha/color to remove holes; `/spawnTornado` spawns the shader funnel even without the CloudTornadoes SSBO (unless legacy fallback is enabled).

### Changed
- Season-dependent systems (auroras, leaves, hurricanes, temperature generation) now use the new season helper instead of hard Serene Seasons calls.


PS - Tornadoes might fail to spawn if no cumulonimbus clouds are present yet; the command queues retries until one appears.
Tornadoes also might fail and tell you that your shaders are out of date if the CloudTornadoes SSBO is missing; enable the legacy fallback in the config in that case. I will continue to work on improving these behaviors in future releases.