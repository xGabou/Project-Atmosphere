# Phase 2A: authoritative PA source audit

Project Climate baseline: `b9e27263075ab8274c9206bb837e92c768c56a2e`.
User-specified PA repository: `G:/Project-Atmosphere` (Forge 1.20.1).
Integration worktree: `G:/Project-Atmosphere-phase2a`, frozen from `7f5ee73e`.
The original PA checkout advanced during inspection; its unrelated changes remain untouched.
The initially inspected NeoForge checkout and its older Forge branches are not integration targets.

Paths below are relative to PA's `src/main/java/net/Gabou/projectatmosphere/`.

| Quantity | Authoritative source / producer | Units and resolution | Lifetime and query behavior |
|---|---|---|---|
| Temperature | `modules/atmosphere/RegionAtmosphereState.getTemperature`; initialized from regional forecasts, modified by `AtmosphericUpdateScheduler`, sunlight, mixing and severe weather | Celsius, one value per 2000x2000 region | Server state; saved by `AtmosphericStateSavedData`; plain getter does no simulation |
| Precipitation | `RegionAtmosphereState.getRainIntensity`; moisture/cloud/weather systems modify state | Nonnegative dimensionless regional intensity; setter does not impose an upper bound; NOT mm/hour or mm/year | Server state and persisted; plain getter does no simulation |
| Humidity | `RegionAtmosphereState.getHumidity`; humidity budget, transport/restoration | Normalized fraction, clamp permits the exact widened 1.2f ceiling (1.2000000476837158); forecast curves use percent instead | Server state and persisted; plain getter |
| Wind | `RegionAtmosphereState.getWind`; PA wind engine/forecast and mixing | Base m/s, angle radians; `modules/wind/WindMath` maps X=-sin(angle), Z=cos(angle); gust is separate | Server state and persisted; getter does not advance wind engine |
| Pressure | `RegionAtmosphereState.getPressure`; PA pressure/atmosphere systems | hPa, regional | Server state and persisted; getter |
| Frozen precipitation | Public weather snapshot derives snow from temperature and cloud weather | Boolean classification, no measured snow-water equivalent | History records regional wet-and-temperature<=0 duration as a labelled proxy, not authoritative snowfall depth |
| Storm/cloud weather | `api/AtmoApi.getWeatherSnapshot`, `clouds/WeatherCloudQueries` | Combined regional plus spatial cloud state | Useful for gameplay; omitted from regional history to avoid mixing resolutions and cloud-query costs |
| Region identity | `util/RegionInstanceKey` | floorDiv(X/Z,2000), including negative coordinates | Native key lacks world identity; companion lease and history add explicit level/dimension scope |
| Observation time | `ServerLevel.getGameTime()` at read | Monotonic simulation ticks, not wall seconds/daylight time | Saved with history. PA does not expose an authoritative last-applied per-region simulation tick; do not invent one |

`AtmosphericUpdateScheduler` targets active regions within approximately 1000 blocks of players every 20 ticks; passive loaded regions are scheduled every 100 ticks in bounded batches. Async computation and application delay mean these are scheduling targets, not guaranteed sample timestamps. Main-thread callbacks apply state; reads for history occur only on the server thread.

PA forecast construction uses seed/position, biome and seasonal context and random variation. Live outcomes also depend on player activity, scheduler completion order, saved state, configured season delegates and interacting weather systems. There is no seed-only raw-bit determinism contract for runtime weather. Baseline PC generation retains its separate exact contract.

`AtmoApi.getWeatherSnapshot` is unsuitable as the low-cost history primitive: absent state falls back to `ForecastOrchestrator.getCurrentTemperature`, and forecast access can ensure/load/generate regions. It also combines cloud-local precipitation. The added versioned bridge only reads an already active `RegionAtmosphereState`, returning empty when unavailable. It does not scan all PA regions, query block heights, call biome sources, or advance simulation.

Client `AtmosphereClientState`, weather packets, cloud rendering and visual wind are not authoritative history inputs. No new client synchronization is needed for an operator command executed on the server.

## Geographic initialization boundary

PC captures only the fixed north-west grid corner (floorDiv(x,2000)*2000, floorDiv(z,2000)*2000) when that exact baseline sample is already requested by generation. Other completed coordinates are ignored. This replaces the initial first-completion rule, which was sensitive to parallel completion order. Capture observes the existing model reads and returned immutable result; it does not add terrain or profile queries. This is a representative point, not a regional mean. A bounded 4096-entry detached context store supplies PA through a callback; no Task 4/5 implementation classes cross the PA API.

PA `TemperatureGenerator` uses supplied baseline temperature in place of its biome/altitude initial temperature, before PA adds its existing seasonal, daily and random variation. Existing PA clamping and external temperature compatibility retain authority over PA forecasts. The PC baseline already includes elevation; it must not receive another lapse correction. Rainfall, latitude, elevation, marine/coast/ocean distances and prevailing direction are exposed as context, but rainfall mm/year is not assigned to humidity or rain intensity. PA can use these fields in later model calibration; Phase 2A invents no mm/intensity conversion.

If no captured context exists, the callback is explicitly empty and PA retains its existing initialization for that region. Existing persisted PA weather is restored normally, not overwritten by PC baseline. The diagnostic reports missing representative context rather than acquiring terrain. This coverage limitation is intentional and visible.

## Time and history

PA `SeasonTimeHelper` delegates to neutral, Serene Seasons, Ecliptic Seasons or other installed providers. `seasonCycleTicks` is a cycle position for some delegates, so it cannot be used as a year duration. History windows use `dayDuration`, `seasonDuration`, four seasons and sixteen seasons, recorded as explicit tick lengths. Neutral defaults are 24000/24000/96000/384000 ticks. These are window labels, not claims about real-world calendars or mature climate normals. Changed durations require an explicit persistence migration; history is not silently reinterpreted.

One history region per native 2000-block atmospheric cell avoids manufacturing finer-resolution weather. Four windows each have at most 17 aggregate buckets; the oldest partial bucket is excluded (up to one bucket of temporal boundary uncertainty). Mean temperature/humidity are time weighted; precipitation is integrated intensity*ticks; wet/dry/frozen-proxy durations use ticks. Coverage and observation count remain explicit. No per-block, per-chunk or per-weather-tick record series is stored.

At most four player-region candidates are visited per server tick. Observations are at least 200 ticks apart; only consecutive observations at most 400 ticks apart contribute duration. No player implies no sampling; absence/inactivity/downtime is missing coverage, not dry weather. No whole-world or PA-registry scan is added.

PA's current global Overworld state is not safe for multiple simultaneous server owners. The API uses an exclusive live-level identity lease and rejects a second owner instead of exposing another world's state. PC services are level-identity scoped and persisted with a save UUID plus generation fingerprint/dimension. The UUID is retained in data/project_climate_world_id.txt; same-seed new saves receive different identities, while moving a complete save preserves its identity. Nether/End are disabled. Static mode works without PA or with an unsupported API. This is not a general multi-dimension PA simulation rewrite.


## Resumed integration findings

PA forecast BiomeSampler previously constructed a detached RandomState from the Overworld settings. With Landscape this crashes when a retained cave biome queries a density field requiring its bound GeneratorContext. ForecastGenerator now supplies the live level and BiomeSampler reuses level.getChunkSource().randomState().sampler(). This is the existing forecast initialization path, not a new runtime observation acquisition.

Runtime observations require the server thread at both Landscape service and PA session boundaries. The baseline initialization callback may be invoked by PA forecast workers: it reads only synchronized detached context and performs no level/terrain access. Session.baselineInitializationCount reports successful baseline-backed initialization requests and is used by the integration harness before its explicit real TemperatureGenerator and callback probes. Startup may precede representative capture; this is reported, not treated as guaranteed baseline coverage.

A fixed coordinate removes completion-order selection of the baseline value, but availability still depends on whether that point was requested and retained before PA initializes. No runtime seed-only determinism is promised. The context store is not persisted; a reopened world may have existing PA weather/history and no retained representative point until ordinary generation requests it again. This is reported as missing context, not repaired by worldgen.
