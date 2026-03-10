# Project Atmosphere — Developer Change Log
This file records functionality additions/removals made during development sessions, annotated with the current version from `gradle.properties` at the time of change.
## Unreleased - NeoForge 1.21.1 build fixes
- Added a Gradle wrapper pinned to 8.8 so IntelliJ/Gradle does not default to Gradle 9 (incompatible with the current NeoForge toolchain).
- Updated the build artifact name to include the MC version and `-neoforge` suffix (for example `projectatmosphere-1.21.1-neoforge-<version>.jar`) so it is distinct from Forge jars.
- Updated a handful of 1.21.1 API breaks uncovered during the Dynamic-Forge merge (Storm Siren `playerWillDestroy` return type, `ClipContext` overload ambiguity, `ResourceLocation` factory usage).
- Removed stale command registration listeners; commands are registered via `RegisterCommandsEvent` and the `/pa` command root.
- Updated `/pa weatherdebug debugmode` to toggle `AtmoCommonConfig.DEBUG_MODE` at runtime.
- Replaced wind forecast/runtime state to match the new high/low layer wind model and removed the unused legacy gust manager.
## Unreleased - Wind force tuning
- Apply Weather2-style wind steering (velocity targeting) after player input, using base wind above 11.1 m/s with capped drift.
- Apply exposure checks (sky visibility, water/lava, horizontal collision) before influencing players or other living entities.
- Use the canonical wind selector at the entity position and align wind direction vectors across particles, clouds, and forces.
- Added a ramp factor so near-threshold winds apply negligible steering and no noticeable slow-down.
- Wind drift now adds a directional component without slowing existing player movement.
- Players now receive intermittent gust impulses instead of continuous wind steering, preserving control in normal winds.
- Creative/spectator players are immune to wind gusts, and gust impulses no longer reduce current player speed.
- Sprinting players now ignore gusts unless winds reach extreme thresholds.
- Surface wind sampling now uses the low-wind layer (not high aloft) and no longer treats gust headroom as always-on speed.
- Reduced default wind push scales and player gust caps by ~3x for gentler movement impact.
## Unreleased - Stability and sync fixes
- Active region detection now uses region membership (plus accurate radius checks) so player-owned regions are always marked active.
- Instruments now read live atmospheric state values on the server to keep temperature, humidity, and pressure consistent with wind and effects.
- Client temperature cache updates are now atomic to prevent transient stale reads.
- Season changes now regenerate forecasts without wiping cloud entities, and server ticks detect season transitions across providers.
- Freezing and snow placement now follow Project Atmosphere temperatures so ice/snow match displayed readings.
## Unreleased - Weatherdebug readout alignment
- /pa weatherdebug forecast now uses the region-sampled temperature, humidity, and pressure so values match the thermometer readout.
## Unreleased - Weather world effects
- Added a weather snapshot API plus world-effects manager that samples Project Atmosphere conditions near players.
- Dense cloud cover now suppresses sun-burning mob ignition and speeds fire cooldown under heavy overcast.
- Rain intensity sampling can extinguish fire/campfires and fill cauldrons (snow or water) without chunk-wide scans.
- Added modder hooks via `AtmosphereWorldEffect` registration and `AtmosphereWeatherTickEvent`.
- Skips per-tick world-effect sampling when no rain is present near players and no custom effects are registered.
## Unreleased - Forecast region fallback recovery
- Guarded region slice generation to ensure biome keys are present on forecasts.
- Detect fallback regions containing only min/max clamp values and regenerate them from the initial biome forecasts.
## Unreleased - Thermometer temperature sync
- Restored client temperature day forecast sync so thermometer displays real values instead of the fallback 0.5.
## Unreleased - Instrument readouts on servers
- Instrument items/blocks now send server-side readouts to the client overlay so multiplayer no longer shows default values.
## Unreleased - Storm siren throttle
- Storm sirens now play the severe-storm sound only once per continuous storm event instead of looping every cooldown tick.
## Unreleased - Cloud drift divergence
- Added per-cloud direction bias, slow wobble, and slight speed variance during CloudRegion ticks to prevent visual stacking of parallel cloud trajectories.
## Unreleased - Temperature clamp diagnostics
- Log a debug warning with a stack trace when temperature clamps hit the safety ceiling to pinpoint runaway sources.
## Unreleased - Command namespace
- Moved Project Atmosphere server commands under the `/pa` root (for example `/pa temperature`, `/pa windSpeed`, `/pa spawnTornado`).
## Unreleased - Wind mixing clamp
- Clamped wind-driven neighbor mixing factors and deltas to prevent extreme temperature spikes from propagating.
## Unreleased - Async random safety
- Replaced off-thread uses of server-level random state with local RandomSource instances in storm/sand/tornado/cloud helpers to prevent LegacyRandomSource thread violations.
## Unreleased - Wind push damping
- Added a push ramp to soften player wind force near the threshold.
## Unreleased - Wind particle bending
- Wind-bent particles now resample wind per tick and smoothly steer toward the current wind vector using a configurable bend strength.
- Cached per-tick wind samples by region for particle steering to reduce client overhead.
## Unreleased - Cloud culling optimization
- Simplified client cloud culling to a single pass per tick to avoid quadratic scans.
## Unreleased - Forecast region unification
- Merged legacy region aggregation into the region ForecastRegion model, updated registry/telemetry/orchestrator consumers, and removed the duplicate core class.
## Unreleased - Wind selection and pressure units
- Added a canonical wind selector (dynamic -> forecast -> safe default), deprecated ambiguous wind getters, and updated runtime wind consumers (HUD, particles, clouds, sandstorms, telemetry, ocean/cyclone) to use it.
- Converted wind pressure deltas from hPa to Pa in the speed equation so generated wind magnitudes use consistent physical units.
## Unreleased - Pressure and sunlight tuning
- Clamped generated pressures and live state pressures to stay above 900 hPa, keeping readings out of unrealistic lows.
- Reapplied seasonal temperature clamps after daily variation so winter highs stay within biome bounds.
- Updated sunlight curve/seasonal tilt to give more realistic midday strength and stronger winter dimming.
- Fixed cloud telemetry helpers to match the updated CloudTickSummary signature.
- Prevented forecast saves from being deleted on load so worlds reuse stored forecasts across restarts.
- Added wind bending for campfire and furnace smoke particles.
- Extended wind bending to ash, dust, snowflake, and cloud particles.
- Expanded telemetry to retain cloud history and log periodic region forecast/state samples for debugging.
- Guarded CloudManager sampling log behind debug mode.
- Limited missing-forecast warnings to log once per biome.
- Added crafting recipes for dust/sand layers and fixed the thermometer recipe output.
- Updated storm siren timing to warn on severity 7 storms and to stay active while tornados are within 500 blocks.
- Replaced per-particle wind mixins with a single Particle mixin to avoid shadow mapping crashes.
## Unreleased - Region forecast refactor blueprint
- Region key unified to `RegionInstanceKey` (removed `ForecastRegionId`); wind/spike/state/orchestrator/networking updated to the unified key.
- Added region orchestrator scaffolding (`modules/region`) and region-based sampling APIs in `ForecastOrchestrator`, `AtmoApi`, and `ForecastSampling`.
- Spikes are region-only (no biome-generation spikes); BiomeChangeManager now tracks regions (and last biome for compatibility) and regenerates when entering a new region or moving ~80% of region size.
- Cloud sampling uses region centers; far clouds culled; SimpleClouds spawn compatibility rejects spawns beyond 10k from players and biases closer spawns.
- Added a client-only telemetry collector with bounded buffers plus `/pa debug export` to serialize session JSONL files and zip them asynchronously with clickable chat links; exports respect a retention window and configurable enable flag.
## Unreleased — Async active-region scheduler
- Added `AtmosphericUpdateScheduler` to refresh only player-proximate states every 20 ticks and batch passive regions through a round-robin queue every 100 ticks using `AsyncAtmosphereService`.
- Sunlight/rain/relaxation now apply as clamped deltas on the main thread after async computation, with stronger sunlight blending and per-variable safety clamps (temperature floored at -273.15C, pressure limited to 870–1080 hPa).
- Cyclone updates now compute off-thread and apply capped deltas on the main thread, preventing runaway pressure/temperature spikes and keeping rain/cloud boosts within bounds.
- State mutators adjust relative to the current value instead of resetting to the biome base, so weather effects accumulate naturally while remaining clamped to realistic ceilings.
## 0.6.0.0-pre3.2 - Wind neighbor safety (2025-11-27)
- Guarded atmospheric state lookups against null neighbor keys so wind updates no longer crash when registry data is missing during regeneration.
- Rebuilt neighbor lists off-thread into immutable snapshots before swapping them into the registry, preventing ConcurrentModificationException while wind mixing iterates during active rebuilds.


## Unreleased – Biome naming and TFC coverage
- `BiomeTempConfig` now warns when biome keys are provided without a namespace (e.g., `minecraft:desert`, `biomesoplenty:bayou`) so config stays tied to the right mod IDs.
- Added TerraFirmaCraft main and technical biome temperature curves (oceans, plains, mountains, rivers, beaches, edges, and estuaries) to keep climate sampling consistent in modded worlds.

## 0.6.0.0-pre3 - Sky effects + season bridge (2025-11-24)
- Tornado shader now binds live SimpleClouds cloud color as its base texture (fallback to static) and densifies alpha/color to remove moving holes; `/spawnTornado` no longer blocks when `CloudTornadoes` SSBO is missing (spawns shader funnel unless legacy fallback is explicitly enabled).
- Added a pluggable season time helper (neutral default) and refactored client season consumers (auroras, leaves, hurricanes, temperature generation) to rely on it instead of Serene Seasons directly.
- Auroras render only on cold nights; rainbows trigger only when rain stops, and both now expose active flags/positions to the client for shader packs.
- In-game config buttons cover tornado debug logging and legacy fallback toggles.

## 0.6.0.0-pre2 – Tornado-aware SimpleClouds sync (2025-11-16)
- Reworked the `MultiRegionCloudMeshGenerator` tornado mixin to mirror the upstream region packing logic instead of calling
  compiler-generated lambda targets, restoring compatibility with SimpleClouds 0.7.3, using a dedicated `CloudMeshGenerator`
  accessor and standalone helper carriers to keep the mixin compliant with Sponge guidelines.
- Added shader capability detection so tornado uploads only run when the SimpleClouds compute shaders expose the new
  `CloudTornadoes` SSBO, preventing `NullPointerException`s in environments that still ship the vanilla shader pack.
- Updated `/spawnTornado` so it first tries to attach a tornado descriptor to the nearest cumulonimbus cloud (engaging the
  new shader-driven funnel) and only falls back to the legacy mesh-based tornado shape when no cloud-defined shape exists.
- `/spawnTornado` now queues retries instead of falling back immediately, waiting for a SimpleClouds cumulonimbus to appear
  so spawned tornados always use the shader-based funnel when one becomes available.
- Added a public `ITornadoRegion` contract (plus `TornadoDescriptor` and accessor helpers) so controller mods can attach funnel
  metadata to any SimpleClouds `CloudRegion` and trust the data to serialize across tags, packets, and API events.
- Extended the existing `CloudRegionMixin` and new API/event mixins to mirror tornado lists through `ScAPICloudRegion` and
  `CloudRegionTickEvent`, allowing other mods to add/remove funnels without bespoke casts.
- Injected a client-side SSBO writer for `MultiRegionCloudMeshGenerator` that streams tornado descriptors to a new
  `CloudTornadoes` buffer and advertises the total count to both compute shaders.
- Overrode `cloud_regions.comp` and `cube_mesh.comp` so tornado cylinders force full density/fade inside their footprint and
  punch through noise when voxels fall inside the declared column height.
- Fixed the `cube_mesh.comp` neighbor check so tornado interiors are treated as empty space, letting adjacent cubes emit faces
  and carve a visible funnel cavity.

## 0.6.0.0-pre2 – Ocean basin integration (2025-11-15)
- Added a modular ocean basin subsystem that detects contiguous oceanic forecast samples asynchronously and keeps long-lived energy reservoirs in sync with the dynamic core.
- Introduced polymorphic influence pipelines so basins adjust their own thermal/pressure memory before feeding humidity, pressure, temperature, and wind tendencies into nearby forecast cells.
- Hooked the new manager into the existing tick loop alongside cyclones and registered optional Continents/Tectonic geometry support, including Gradle dependencies for both mods.

## 0.6.0.0-pre1 – Cloud region unification (2025-11-14)
- Rebuilt the atmospheric cloud manager so each SimpleClouds `CloudRegion` now carries its own thickness, rain intensity, and lifecycle instead of duplicating data per biome sample.
- Region scans now run on `AsyncAtmosphereService`, averaging humidity/temperature for only the biomes under each cloud footprint and projecting the combined cover back to those biomes.
- Cloud growth and shrink follow humidity and temperature trends while spawn attempts reuse the old `trySpawnClouds` heuristics to find humid hotspots asynchronously before creating regions on the main thread.
- Sunlight now lerps toward forecast-derived daily min/max temperatures, preventing runaway heat spikes (e.g., 25C -> 139C swings in sparse jungles) while still letting rain, humidity, and wind modules nudge the live value.

## 0.5.5.7 – Cloud rave pacing (2025-11-14)
- Clouds no longer react every tick; they now require minutes/days of stability above a biome before the humidity-driven radius/lifetime adjustments kick in, producing a smoother, rave-like rhythm.
- Humidity biases those dwell timers so humid climates saturate quicker while arid, hot areas still need to linger for several minutes before they can shrink or disperse.

## 0.5.5.7 – Cloud persistence tuning (2025-11-13)
- Clouds now ease toward a humidity-driven target thickness instead of jumping immediately, so growth and dissipation happen over minutes rather than seconds.
- Dissipation speed scales with biome dryness, letting humid areas keep their systems intact while extreme deserts still erode storms after several minutes of exposure.
- Rain intensity ramps in slowly alongside thickness, preventing sudden downpours when a cloud first spawns.

## 0.5.5.7 – Cloud spawn throttling (2025-11-12)
- Added a respawn cooldown to the atmospheric cloud manager so SimpleClouds visuals are not re-created every tick when humidity rapidly crosses the storm threshold.
- Cloud data now persists through dissipation cycles and only attempts a new spawn once the cooldown elapses, preventing runaway "cloud rave" behaviour.

## 0.5.5.7 – Storm factor integration (2025-11-11)
- Removed the legacy storm chance forecast data in favour of live storm factors so gusts, cloud spawners, and SimpleClouds hooks follow the new cyclone/sunlight-driven core.
- Wind gust multipliers now scale smoothly with the measured storm factor instead of toggling at a fixed threshold.

## 0.5.5.7 – Biome-driven cloud evolution (2025-11-10)
- SimpleClouds regions now sample the biome beneath them to grow in cool, humid climates and dissipate over hot or arid zones.
- Cloud radius changes gradually each tick with matching lifetime adjustments so long-lived storm systems persist over wet areas and burn out faster in deserts.
- Cloud radius multipliers persist through sync/serialization and stay clamped, preventing abrupt pop-in while still allowing clouds to shrink back when conditions stabilise.

## 0.5.5.7 – Biome-aware sunlight tuning (2025-11-09)
- Sunlight intensity now scales with each biome’s seasonal temperature ranges, letting hotter climates receive stronger midday heating.
- Region states keep hourly daily curves sourced from the live controllers so commands and clients can still display day profiles.
- Build automation skips CurseForge uploads and Discord notifications automatically when their environment tokens are absent.

## 0.5.5.7 – Dynamic atmosphere simulation (2025-11-08)
- Replaced daily forecast regeneration with a live atmospheric state registry that evolves continuously.
- Added sunlight, cyclone, cloud, rain, and wind controllers so temperature, humidity, and pressure react to in-game forces.
- Updated commands and client helpers to report the new dynamic values and removed the legacy daily forecast generator.

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

## Unreleased — Forecast regions grid
- Introduced `RegionInstanceKey` grid mapping and `ForecastRegion` aggregates to replace biome-scoped forecasts.
- Atmospheric state registry and region state now operate per forecast region while keeping legacy biome lookups mapped to their owning regions.
- Forecast generation now groups biome samples into region forecasts before seeding atmospheric states; SimpleClouds and cyclone/cloud sampling apply updates against region states.
- Public API now exposes region-centric forecasts via `AtmoApi#getWeatherForecast`, aligning cloud speed sync with region identifiers.

## 0.5.5.2 — Imperial Units Mode (2025-10-19)
- Added config option `display.imperialUnits` to toggle display units.
- Overlay and commands now respect units:
  - Temperature shows as °F when enabled (°C otherwise).
  - Wind speed shows as mph when enabled (m/s otherwise).
  - Pressure shows as inHg when enabled (hPa otherwise).
- In-game config screen adds an “Imperial Units” toggle under Display.
- Regeneration safety: clearing/regenerating forecasts now pauses dependent ticks (wind physics, tornado/hurricane/snowstorm managers), and defers scheduled tornado checks until regeneration completes.

## Unreleased — Unified wind stack
- Rebuilt wind handling into a high/low layer model with gust-aware forecasts and runtime smoothing that mirrors the other environment modules.
- Added tornado-aware low wind forces plus helpers to apply combined wind, gust, and suction/rotation/lift to players.
- Wired SimpleClouds and forecast orchestration to consume the new wind API while preserving existing forecast generation inputs.
- Ground-level wind particles near players now receive directional pushes when the airflow is unobstructed, keeping leaves and streaks aligned with live wind samples.
- Server-side telemetry now records player weather samples, dominant chunk occupancy, forecast snapshots, cloud lifecycle events, precipitation gate decisions, and temperature anomalies for `/pa debug export`.

## 0.5.4.4 — Added weatherdebug cloud command (2025-10-17)
- Added command: `/weatherdebug cloud <id>`
  - Spawns the specified SimpleClouds cloud at the player’s position/biome.
  - Requires permission level 2.
  - Applies current wind sample; fails gracefully if SimpleClouds is not initialized.
