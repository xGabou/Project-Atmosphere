# Project Atmosphere

### Added
- **Cyclone and low-pressure systems** that dynamically reshape the world’s weather patterns, replacing static forecast regeneration.
- **Sunlight-based temperature system** where each biome has its own solar energy absorption rate, and cloud cover now realistically reduces local heating, creating cooler or warmer zones over time.
- **Rainbow system** that appears dynamically after rainfall events based on humidity, sunlight angle, and biome type.
- **Aurora Borealis integration**, adding naturally shifting lights at high latitudes during clear and cold nights, fully synchronized with temperature and pressure data.
- **Dynamic forecast persistence**, where only major geographical displacement or manual commands regenerate the world’s base forecast.

### Fixed
- Improved weather stability logic to avoid rapid cloud cycling or “rave” behavior between biomes.
- Corrected humidity-to-rain intensity mapping to produce smoother transitions.

### Changed / Removed
- Removed daily forecast regeneration in favor of continuous, cyclone-driven evolution across the world.
- Temperature is now influenced by **solar energy and atmospheric pressure** instead of relying solely on biome constants.
- Forecast updates now occur asynchronously, ensuring seamless regional transitions and no TPS impact during major weather changes.  
