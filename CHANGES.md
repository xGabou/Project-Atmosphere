# Project Atmosphere — Developer Change Log
This file records functionality additions/removals made during development sessions, annotated with the current version from `gradle.properties` at the time of change.
## Unreleased - Local Simple Clouds dependency
- Replaced the remote `nonamecrackers2:simpleclouds:0.7.3+1.20.1-forge` dependency with the local `libs/simpleclouds-0.7.4+1.20.1-forge-all.jar` artifact in `build.gradle`.
## Unreleased - Project Atmosphere crash screen
- Added a client-side Project Atmosphere crash interception flow that detects whether a thrown exception or crash report stack involves `net.Gabou.projectatmosphere`, saves a crash report into `crash-reports`, unloads the active client session when possible, and opens a dedicated support screen instead of falling straight into the normal fatal client crash path.
- Added a dedicated crash screen with a clear Discord support message plus buttons to close the game, copy a support summary to the clipboard, and open the Project Atmosphere Discord invite in the browser.
- Hooked the handler into the Forge 1.20.1 client loop via a new Minecraft mixin that wraps `runTick(...)` and intercepts delayed crash reports.
## Unreleased - MCP support bundle
- Added a read-only support bundle under `docs/mods/projectatmosphere/` and `data/mcp/mods/projectatmosphere/`, covering overview/install/troubleshooting/FAQ docs plus structured manifest, features, commands, config schema, compatibility, known issues, versions, and support-focused changelog data derived from repository sources.
- Separated strictly official compatibility notes from code-reviewed runtime behavior so support tooling can answer conservatively when release documentation and current source behavior differ.
## Unreleased - Standalone hurricane cloud renderer tranche
- Added a client tornado render-quality control and moved the tornado shader onto an adaptive LOD path that lowers raymarch step count, trims expensive material/noise work, and skips unnecessary inner-funnel sampling when the storm is far away or the user lowers tornado quality for better FPS.
- Darkened the tornado's upper wall-cloud/connection shading again so the top mass reads denser and less washed out against the surrounding storm canopy.
- Replaced the fullscreen tornado volume pass with a world-space proxy-box volume draw, so each tornado is now rasterized at a fixed in-world position and the shader uses scene depth only as a march limit instead of as the thing defining the effect.
- Mirrored the same proxy-box world-space volume approach into the hurricane cloud and fringe passes, keeping the existing storm shapes while removing the old fullscreen “mirage/post-process” anchoring behavior from the actual volume render path.
- Added a gated `/pa debug tornado render ...` investigation path with per-storm selection, frozen deterministic funnel sampling, grayscale mask modes (`aabb`, `funnel`, `height`, `radial`, `radius`, `density`, `alpha`, `wallcloud`, `connection`, `full`), and structured CPU-side diagnostics that log the tornado origin, sample position, local-space values, bounds/scaling, and active render state for root-cause analysis.
- Extended the tornado proxy debug path with ordered proof modes for the world-space renderer: `box` draws the proxy volume as a solid opaque color, `hit` shows ray-box intersection success, `fill` renders a constant-density box, and only the later modes reintroduce tornado shaping before normal depth stopping is enabled again.
- Fixed the shared world-space proxy-box draw submission so tornado and hurricane volume boxes now render with the real cloud-pass model-view and projection matrices instead of identity matrices, which could make even forced-opaque `box` mode disappear entirely.
- Darkened the tornado’s upper connection/top shading by about 30% and doubled the existing twist rotation speed in the shader, keeping the current silhouette and animation style while making the top feel denser and the spin read more strongly.
- Removed the client tornado debris particle spawn loop entirely and increased the tornado shader’s existing twist multiplier again, so the funnel keeps the grounded volumetric look without the expensive swirling debris particles and reads with a faster spin.
- Reworked tornado visual motion so the renderer now uses a continuously advancing spin value instead of the old capped twist, and changed client tornado interpolation to smooth toward snapshot targets with light extrapolation instead of directly stepping toward each sync update.
- Fixed `spawnTornadoNoClouds` so it now spawns a real standalone tornado immediately instead of still routing through the cloud-gated cumulonimbus path, and added a no-cloud server spawn mode that does not auto-dissipate just because no Simple Clouds region is attached.
- Corrected the tornado shader’s shaping math to evaluate funnel, wall-cloud, and connection terms in consistent world-unit space instead of mixing cloud-space coordinates with world-sized constants, which was producing oversized dome/ring artifacts near the cloud base.
- Re-integrated tornado opaque rendering with the real cloud-target depth buffer while keeping the horizon-safe raymarch distance path, and thickened the tornado shader density/tint so funnels now read as embedded storm mass instead of a translucent hologram laid over the world.
- Reworked the tornado raymarch from a blind full-range overlay into a scene-depth-clipped world-space volume, and changed the prepared top/bottom bounds to follow actual ground contact plus cloud-base attachment instead of an oversized fallback height band.
- Retuned tornado demolition so active funnels start destroying trees, leaves, wooden lightweight structures, and loose natural terrain much earlier in the wind curve, with stronger inner-core forcing and wider believable damage reach tied to storm intensity.
- Strengthened the dynamic fog application curve so humidity and wet-biome fog compresses view distance and tint more visibly instead of remaining too subtle to notice in normal gameplay.
- Added `/pa fog spawn [strength] [seconds]` and `/pa fog clear`, implemented as a client-side debug fog override packet/state path that lets you force visible fog for render testing without faking server weather.
- Fixed the tornado fullscreen render integration so both Simple Clouds pipelines now render the opaque pass on the cloud target in the correct translated cloud-space stage; this removes the bad extra shader-support translation and stops camera-position-dependent tornado popping/disappearance.
- Increased tornado persistence by extending the lifecycle timings and adding a cloud-detach grace window, so tornadoes no longer collapse almost immediately after a brief cloud-region handoff or wobble.
- Updated the tornado shader/render path so the funnel no longer hard-clips against the copied scene-depth horizon, which previously made tornadoes shear off around camera eye level over water or other long flat surfaces.
- Reworked tornado lifetime scaling to guarantee a roughly 2-10 minute active window based on storm strength, and changed water exposure so it only softens intensity instead of fast-forwarding or force-ending the tornado after a few seconds.
- Added a modular humidity-driven fog system with a lightweight server-to-client fog status sync, a client fog state cache/smoother, wet-biome classification heuristics, and integration into the existing fog event handler so fog now responds to humidity, rain, and moisture-heavy biomes without per-frame heavy sampling.
- Added a new `fog` common-config section plus in-game config screen controls for the main fog tuning values, including humidity thresholds, wet-biome strength, rain boost, fog distance, tint strength, and advanced biome id/keyword overrides in the config file.
- Added `weatherdebug fog` to expose the live humidity, wet-biome weighting, rain contribution, and resulting fog strength using the same shared heuristic that drives the client fog renderer.
- Added a dedicated Simple Clouds-style hurricane renderer and shader path that renders bounded volumetric storm masses as their own cloud body instead of extending the old flat ring overlay path.
- The new hurricane density field now supports a true empty eye, annular eyewall, upper canopy, outer shield, and early spiral-band structure while still reusing the Project Atmosphere / Simple Clouds texture samplers, fog, depth, and cloud-color language.
- Hooked the hurricane renderer into both Simple Clouds default and shader-support pipelines, added hurricane shader registration/resources, and disabled the old `SimpleCloudsRendererMixin` ring path from the active mixin config.
- Extended hurricane client snapshots/interpolation so prepared render data can be cached and reused per frame, and fixed tornado/hurricane motion/constructor mismatches so the current tree builds cleanly again.
## Unreleased - Gradle sync fix
- Removed the duplicate mid-script `import groovy.json.JsonOutput` from `build.gradle`, which could stop the Gradle script from compiling during IDE sync.
- Replaced legacy archive/version references with Gradle 8-safe values for the jar manifest plus the Modrinth and CurseForge artifact paths.
- Made the optional private GitHub Maven repository and publishing target conditional on GitHub package credentials so local sync does not fail when `GITHUB_USER`/`GITHUB_ACTOR` and `GITHUB_TOKEN` are unset.
- Restored the missing root Gradle wrapper files under `gradle/wrapper/` and pinned them to Gradle 8.8, preventing IDE sync from drifting to Gradle 9.
- Updated IntelliJ project settings to use the existing `temurin-17 (2)` SDK for Gradle import and changed the leftover module bytecode target from Java 21 back to Java 17.
- Stopped the forecast loading overlay from injecting into `ProgressScreen` and `GenericDirtMessageScreen`, so it no longer renders during save-world and generic dirt/progress screens while still appearing on actual world-loading screens.
- Moved the forecast loading overlay higher on the screen to avoid overlapping vanilla loading text and progress elements.
- Replaced every `tfc:*` biome temperature block in `BiomeTempConfig` with the new seasonal min/max dataset and removed obsolete TFC keys that were not part of the provided list.
## Unreleased - Release notes refresh
- Replaced `PAchangelog.md` with updated platform-ready release notes for Discord, CurseForge, and Modrinth covering the current `0.8.0.0` forecast/runtime refactor, telemetry, coupling, and compatibility work.
## Unreleased - Telemetry Instant serialization fix
- Registered explicit Gson adapters for `java.time.Instant` in `TelemetryCollector`, serializing timestamps as ISO-8601 strings instead of relying on blocked reflective field access under JDK 17.
- This fixes telemetry export/runtime failures caused by `InaccessibleObjectException` when exporting anomaly and precipitation trace records.
## Unreleased - Runtime atmosphere coupling phase D cyclone/cloud reconciliation
- Added retained cyclone visual floors on `RegionAtmosphereState` for cloud cover and rain intensity, so cyclone forcing now has an explicit ownership channel instead of relying on transient direct writes that later get overwritten.
- Updated `CycloneManager` to push cloud/rain floors into the region state while still applying the immediate pressure, humidity, and temperature deltas, and to seed cloud-water from that forcing.
- Updated `CloudManager` to merge sampled cloud/rain values against the retained cyclone floors and to preserve those floors during passive fade-out, preventing low-pressure cyclone regions from immediately losing their visible weather when SimpleClouds sampling runs afterward.
## Unreleased - Runtime atmosphere coupling phase C temperature anchor
- Added a forecast-temperature restore term and a soft excess-deviation guard in `AtmosphericUpdateScheduler`, so warm and cold regions now converge back toward the forecast temperature target instead of relying almost entirely on sunlight blending and tiny base relaxation.
- Added a `temperature_drift_from_target` anomaly marker for regions that remain far from their forecast temperature target under near-clear, low-rain conditions, making hidden temperature drift visible in telemetry exports.
- Kept the change scoped to scheduler temperature control only; cyclone/cloud ownership reconciliation remains a separate follow-up tranche.
## Unreleased - Runtime atmosphere coupling phase B pressure anchor
- Added a forecast-pressure restore term and a soft excess-deviation guard in `AtmosphericUpdateScheduler`, so runtime pressure now trends back toward the forecast climatology instead of free-drifting for long periods after dynamic forcing.
- Added a telemetry anomaly marker for `pressure_drift_no_visible_weather`, emitted when a region remains far from its target pressure while cloud cover and rain stay near zero, to surface hidden pressure/weather desynchronization directly in exports.
- Kept the intervention scoped to pressure only for this tranche, leaving the later temperature/cyclone-cloud ownership work for the next coupling phase.
## Unreleased - Runtime atmosphere coupling phase A instrumentation
- Added forecast-derived `getTargetTemperature(dayTime)` and `getTargetPressure(dayTime)` accessors in `RegionAtmosphereState`, alongside the existing humidity target, so runtime telemetry can compare current state against immutable climatology profiles instead of mutable daily snapshots.
- Added `atmosphere_coupling.jsonl` telemetry export with active-region samples capturing target vs current temperature, pressure, and humidity plus the scheduler-applied temperature and pressure deltas for each update.
- Wired `AtmosphericUpdateScheduler` active updates to emit the new coupling telemetry before anomaly recording, giving a direct diagnostic stream for temperature/pressure drift investigations.
## Unreleased - Runtime atmosphere coupling design study
- Added `doc/runtime-atmosphere-coupling-study.md`, a companion design study focused on the runtime coupling problem between forecast targets, dynamic temperature/pressure forcing, cyclone/ocean/wind effects, and the visible cloud/rain layer, including diagnosis from recent telemetry, solution tradeoffs, RDCU, MDD, UML diagrams, case studies, phased implementation, risks, and acceptance criteria.
## Unreleased - Humidity budget phase 4 cloud-water extension
- Added explicit condensed-moisture tracking via `cloudWater` on `RegionAtmosphereState`, plus `CloudWaterExchange` and `CloudWaterService` to model condensation, re-evaporation, and precipitation draw as named runtime terms.
- Integrated the cloud-water exchange step into `AtmosphericUpdateScheduler` after the Stage 3 humidity budget so live humidity now couples to condensed cloud moisture without rewriting the temperature/pressure update path.
- Seeded and faded regional `cloudWater` from `CloudManager` based on cloud cover and rain intensity, and expanded telemetry exports to include cloud-water state in both region forecast samples and humidity-budget diagnostics.
- Updated the humidity stage tracker so the rollout now stands at stage `4/4` completed.
- Added `doc/humidity-moisture-budget-verification.md` and updated the design study so the implementation state, verification results, and Stage 4 documentation are aligned.
## Unreleased - Humidity budget phase 3 ocean and wind integration
- Added explicit Stage 3 humidity-budget integration for ocean and wind by exposing `OceanBasinManager.estimateHumidityFlux(...)` and `WindVector.estimateHumidityTransport(...)`, then feeding those terms into `AtmosphericUpdateScheduler` as `oceanFlux` and `windTransport` for active-region humidity updates.
- Removed direct humidity mutation from `AtmosphereFluxInfluence` and `WindVector.update` so ocean and wind no longer double-apply humidity outside the scheduler budget while their other responsibilities remain intact.
- Updated the humidity stage tracker so the rollout now stands at stage `3/4` completed, leaving only the future cloud-water extension stage.
## Unreleased - Humidity budget phase 2 scheduler rewrite
- Reworked `AtmosphericUpdateScheduler` humidity updates to use an explicit Stage 2 humidity budget calculation instead of the old anonymous delta, adding named terms for solar drying, biome evaporation, rain exchange, forecast restore, and a weak precipitation sink.
- Added `HumiditySourceProfile` and `HumidityBudgetService` so humidity behavior is now derived from the regional climate target plus biome moisture bias rather than a single global drying rule.
- Split immutable forecast daily profiles from mutable runtime snapshot profiles in `RegionAtmosphereState`, ensuring `getTargetHumidity(dayTime)` remains anchored to the forecast curve instead of drifting as live humidity snapshots are recorded.
- Stopped the scheduler from restoring humidity a second time through `relaxTowardBase`; post-update base relaxation now only applies to temperature and pressure in the scheduler path.
## Unreleased - Humidity budget phase A instrumentation
- Added a runtime `HumidityBudget` scaffold plus `RegionAtmosphereState.getTargetHumidity(dayTime)` so the humidity rework now has an explicit diagnostic model and a forecast-derived target available at runtime.
- Added `humidity_budget.jsonl` telemetry export with per-region active-update humidity budget samples, including target humidity, before/after runtime humidity, and the current decomposition of solar drying, rain exchange, precipitation sink, and net delta.
- Added `doc/humidity-moisture-budget-stages.md` to track the rollout as a staged plan with the current status marked as stage `1/4` completed and the remaining stages explained.
## Unreleased - Humidity budget design study
- Added `doc/humidity-moisture-budget-study.md`, a dedicated design study for the runtime humidity rework covering the problem diagnosis from telemetry, the target product/architecture vision, solution tradeoffs, RDCU, MDD, UML diagrams, case studies, implementation phases, risks, and acceptance criteria for a hybrid forecast-anchored moisture-budget model.
## Unreleased - Forecast refactor phase 6 runtime cleanup
- Replaced biome-key cloud/weather area sampling with region-first sampling in `WeatherSampler`, and updated cloud spawn candidate selection to aggregate temperature, humidity, pressure, wind, and storm factors directly from `RegionInstanceKey` runtime state.
- Migrated SimpleClouds runtime integration to region-first helpers for cloud creation/spawning and cloud tick wind/storm sampling, keeping biome-key spawn entry points only as explicit compatibility edges where external APIs still require them.
- Removed dead biome-key runtime compatibility scaffolding that was no longer used in live server execution, including `ForecastPointerRegistry`, active-player biome fallback tracking in `ForecastOrchestrator`, and unused legacy biome views in `AtmosphericStateRegistry`.
## Unreleased - Forecast refactor phase 4/5 closure
- Switched forecast bootstrap, season regeneration, manual regeneration, and missing-forecast recovery to rebuild wind runtime state from primary `RegionInstanceKey` forecasts instead of the legacy biome forecast map.
- Removed the unused legacy biome-forecast save writer from `ForecastDataStorage`; region saves remain the only write path while `biome_forecasts.json` stays as a read-only migration/import fallback.
- Moved remaining server command/debug wind consumers (`weatherdebug`, `/windSpeed`, hurricane spawn, tornado spawn/debug cloud seeding) onto region-first wind sampling while preserving biome-key adapters only where external cloud spawn integration still requires them.
## Unreleased - Forecast refactor phase 5 region-first persistence
- Added primary region-first persistence for forecast saves under `region_forecasts`, including bulk region discovery and load-time integrity validation for weekly temperature, humidity, pressure, wind, and storm data.
- Migrated startup/shutdown forecast persistence to prefer region saves while keeping legacy `biome_forecasts.json` and legacy region fallback files as read-only compatibility/import paths.
- Hydrated legacy biome-key compatibility structures from loaded region forecasts so existing runtime systems can keep using biome-key adapters while persistence moves to `RegionInstanceKey` first.
- Added direct wind forecast rebuild support from region forecasts so server bootstrap no longer depends on legacy biome-save hydration when region saves are present.
- Moved active runtime scheduling toward region keys by switching wind ticking, ocean basin updates, tornado cooldown/risk flow, and nearby-player active tracking to `RegionInstanceKey`-first paths while keeping deprecated biome adapters for compatibility.
- Hardened seasonal tree Dynamic Trees integration so the accessor is loaded reflectively only when the `dynamictrees` mod is present, and downgraded the DT development dependency to `compileOnly` so missing DT no longer blocks normal launches.
- Converted sandstorm forecast detection/scheduling to use region forecasts internally, resolving a representative biome sample only at the compatibility edge where the external sandstorm API still requires biome keys.
- Updated humidity/pressure debug commands and tornado debug actions to stop reading player-position biome forecasts directly and prefer region-first forecast/runtime access.
## Unreleased - Forecast refactor phase 4 wind API definition
- Added a minimal region-first wind forecast API (`WindForecastApi`) with direction and speed accessors, plus a default server implementation (`RegionWindForecastApi`) backed by `ForecastOrchestrator`.
## Unreleased - Forecast refactor phase 3 region-first sampling
- Added region-key sampling APIs for temperature, humidity, and pressure in `ForecastOrchestrator`, and migrated `ForecastSampling` to prefer `RegionInstanceKey` resolution while keeping biome-key overloads as compatibility wrappers.
## Unreleased - Forecast refactor phase 2 foundations
- Started Phase 2 implementation by unifying region orchestrator bootstrap on `LegacyBiomeForecastGenerator`, centralizing region-local coordinate conversion, and hardening forecast regeneration to clear stale grouped/average caches before rebuilding dependent forecast passes.
## Unreleased - Forecast refactor phase 1 specification
- Added a complete Phase 1 technical specification for forecast refactoring, including current-state diagnosis, use-case catalog (RDCU), target domain model (MDD), UML class/sequence/activity diagrams, and a concrete migration plan toward RegionInstanceKey-first architecture.
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
## Unreleased â€” Cloud probe targeting
- Updated `CloudProbeItem` to prioritize clouds intersected by the forward probe ray instead of immediately reporting the cloud containing the player.
- The probe now uses SimpleClouds' actual cloud layer height for intersection checks and only falls back to the containing cloud when no targeted cloud is found ahead.
- Added an enchanted-glint stick presentation for the cloud probe item.

## Unreleased - Forecast loading overlay
- Added a dedicated client forecast loading state and staged status model for the Project Atmosphere sync lifecycle.
- Render the PA loading panel from the vanilla `LoadingOverlay` path so the standard Minecraft loading UI remains visible underneath.
- Hooked the existing login forecast sync into wait/receive/build/prepare/ready transitions and reset the client state automatically on disconnect.
- Extended the PA renderer onto the actual world-join loading screens and upgraded the overlay to a centered, dominant progress panel with determinate or animated indeterminate bar states.
- Reworked the loading overlay into a smaller top-centered status panel so it sits above the vanilla loading UI instead of covering it.
- Promoted the current PA loading stage to the primary on-screen label and tied progress updates to the real forecast snapshot, cache-build, and finalization milestones used during client sync.
- Forecast cache application now drains in client-side batches across ticks, allowing the overlay to report visible per-loop progress from the actual biome-profile apply path instead of jumping from wait to ready.
- Added server-side login preparation stage updates around nearby-region collection and local weather seeding so the overlay advances before the forecast snapshot packet is sent.
- Added an integrated-world loading bridge so local world startup can push forecast-design stages from the real server generation loops before the later client sync packet phase begins.
## Unreleased - Hurricane renderer replacement
- Removed the hurricane-specific `custom_cumulonimbus` spawn path so hurricane commands now create only the dedicated hurricane system instead of seeding a Simple Clouds cumulonimbus shell first.
- Added an explicit `HurricaneRenderDescriptor` to server snapshots and client interpolation so eye size, eye-clear radius, eyewall thickness, canopy radius, shield radius, vertical layer factors, and outer band controls travel as storm data instead of being reconstructed only from radius and intensity during rendering.
- Reworked `SimpleCloudsHurricaneRenderer` into a full dedicated hurricane pipeline with bounded opaque raymarching, weighted-transparency fringe rendering, and framebuffer eye-carve passes that clear unrelated ambient Simple Clouds content from both the opaque and transparent cloud targets before hurricane cloud content is added back.
- Replaced the single hurricane shader registration with dedicated opaque, opaque-mask, transparency, and transparency-mask shader programs plus new fragment shaders for the standalone hurricane volume and eye-protection passes.
- Deleted the abandoned flat-ring hurricane scaffold (`SimpleCloudsRendererMixin`, `HurricaneMeshRenderer`, `HurricaneStateProvider`, and `HurricaneState`) so there is no legacy ring renderer left in the active codebase.

## Unreleased - GPU-native hurricane mesh generation
- Removed the fullscreen hurricane pipeline hooks and their shader registration so hurricanes are no longer rendered as a separate post/cloud-target pass.
- Added `HurricaneMeshField` plus a new `MultiRegionCloudMeshGenerator` mixin that uploads prepared hurricane descriptors to the active Simple Clouds mesh compute shader each mesh-generation cycle.
- Extended chunk scheduling so Simple Clouds now allocates mesh generation work for hurricane volumes even when no ambient cloud region overlaps the chunk.
- Overrode the Simple Clouds `cube_mesh.comp` compute shader with a hurricane-aware density field that emits real mesh cubes for the eye wall, canopy, shield, and outer bands while using the same GPU block generation and face occlusion path as native Simple Clouds clouds.
- Added an eye-carve override directly inside the cube generation density sampling so the hurricane eye removes ambient cloud blocks in the same generated volume instead of relying on a later framebuffer cleanup pass.
- Added a `cloud_regions.comp` override that preserves an explicit "no ambient region" sentinel, preventing hurricane-only chunks from inheriting a bogus default cloud region during compute meshing.
