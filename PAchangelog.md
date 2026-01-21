# Project Atmosphere

### Changed
- Player wind now steers horizontal velocity toward a capped target (Weather2-style) after input, using base wind only above 11.1 m/s.
- Non-player living entities use a separate steering path with optional gust blending and higher drift caps.
- Wind direction vectors for particles, entity forces, and SimpleClouds drift now match the Weather2 angle convention.

### Fixed
- Wind forces now respect exposure, skipping entities in water/lava, under cover, or colliding horizontally.
