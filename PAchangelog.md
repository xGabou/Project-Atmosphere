# Project Atmosphere

### Added
- **Terralith and Nature’s Spirits biome support**, enabling the forecast, humidity, and sunlight models to recognise these worldgen packs out of the box.

### Fixed
- Improved weather stability logic to avoid rapid cloud cycling or “rave” behavior between biomes.
- Corrected humidity-to-rain intensity mapping to produce smoother transitions.
- Cloud region spawning no longer stalls behind stuck async workers, preventing empty skies on fresh worlds.

### Changed / Removed
- Forecast updates now occur asynchronously, ensuring seamless regional transitions and no TPS impact during major weather changes.
- Forecast curves were rebalanced to keep daytime highs/lows anchored to realistic seasonal envelopes before wind, humidity, and storm modifiers are applied.
