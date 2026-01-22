## Project Atmosphere

### Changed
- Reworked wind handling across the mod to remove inconsistencies and ensure all systems use the same wind source and direction convention.
- Wind particles now sample wind every tick at their own position and smoothly steer toward it, producing real bending instead of straight trajectories.
- Player wind influence now uses Weather2-style steering applied after input, with a noticeable effect only above 11.1 m/s.
- Non-player living entities use a separate steering path with optional gust blending and higher drift caps.
- Wind direction vectors are now consistent across particles, entity forces, and SimpleClouds cloud drift.
- Cloud regions now apply a stable per-cloud direction bias plus slow wobble and slight speed variance to reduce apparent stacking.
- Temperature clamping now logs a debug warning with context and stack trace to track runaway sources.
- Server commands are now registered under the `/pa` root (for example `/pa temperature` and `/pa spawnTornado`).
- Wind-driven neighbor mixing now clamps its factors and deltas to stop runaway temperature spikes from spreading.
- Added weather snapshot and world-effects hooks, including rain-driven fire/campfire extinguish, cauldron filling, and dense-cloud sunburn suppression.
- Region fallback generation now ensures biome keys are present and regenerates any regions that contain only min/max clamp values.
- Wind steering now ramps near the threshold so low winds no longer slow the player.
- World effects skip sampling when no rain is present and no custom effects are registered, reducing tick load.
- Wind drift now applies as a directional add without reducing existing player speed.
- Player wind now uses intermittent gust impulses (no conveyor steering), with configurable thresholds and caps.
- Wind particle steering now caches region wind samples per tick to reduce client overhead.
- Client cloud culling now uses a single pass per tick to avoid quadratic scans.
- Creative/spectator players are immune to wind gusts, and gust impulses no longer reduce current player speed.
- Sprinting players now ignore gusts unless winds reach extreme thresholds.
- Surface wind now comes from the low-wind layer and no longer treats gust headroom as baseline speed.
- Reduced default wind push scales and player gust caps by ~3x for gentler movement impact.
- Thermometer now syncs the client day-forecast temperature so it no longer shows the 0.5 fallback.
- Instrument readouts now use server-side values in multiplayer instead of showing default client fallbacks.
- Storm sirens now play the severe-storm sound only once per continuous storm event.

### Fixed
- Fixed a wind particle crash.
- Fixed wind speed being far too low due to a unit mismatch in the wind equation (hPa vs Pa), which was suppressing the computed wind magnitude.
- Fixed a 90° wind direction offset caused by inconsistent sin/cos mapping between systems.
- Fixed wind fallback behavior and cleaned up wind selection usage to reduce edge-case inconsistencies.
- Wind forces now respect exposure, skipping entities in water or lava, under cover, or colliding horizontally.

### Notes
- Storms have been observed spawning again in testing (cumulus clouds and a wall cloud), so storm generation appears to be working correctly.
- Please report any unusual wind behavior, particle issues, or storm spawning problems so I can continue monitoring and tuning.

