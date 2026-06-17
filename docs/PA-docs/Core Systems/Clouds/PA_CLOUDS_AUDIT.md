# Project Atmosphere PA Clouds Mode Audit

This audit is based on inspected code only. It does not rely on class names, assumptions, or guessed architecture.

I did not run Minecraft, so runtime visibility that depends on actual shader/device behavior is marked unverified where needed.

## Runtime Wiring

| Path | What Runs Today | Evidence |
|---|---|---|
| Server start | Forecast system starts, native/disabled cloud service starts, season bootstrap/state is initialized. | `ProjectAtmosphere.java`, `AtmosphereManager.java`, `ForecastOrchestrator.java` |
| Player login | Forecast region loading and snapshot sync run, runtime atmosphere sync runs, native cloud region sync runs when Simple Clouds is absent. | `AtmosphereManager.java`, `CloudRegionSyncManager.java` |
| World tick | Native cloud regions tick before overworld-only weather logic when Simple Clouds is absent. Forecast, snowstorm manager, world effects, sync, and spawn attempt hooks run afterward. | `EventHandler.java`, `CloudRegionManager.java` |
| Client tick | Client localized weather and audio updates run; native cloud render hooks are registered only when Simple Clouds is absent. | `ClientOnlyRegistrar.java`, `ClientTickHandler.java` |
| Render tick | Native cloud rendering runs from `RenderLevelStageEvent.AFTER_PARTICLES` when PA cloud policy allows it. | `CloudRenderHook.java`, `CloudRenderer.java` |
| Save/load | Base forecasts are saved separately from PA cloud region `SavedData`. Native cloud regions persist through Minecraft saved data. Live dynamic atmospheric state is not fully persisted. | `ForecastDataStorage.java`, `CloudRegionSavedData.java` |
| Commands | `/pa cloud ...` can create, list, clear, sync, and freeze native clouds when Simple Clouds is absent. Vanilla `/weather` is bridged into PA cloud spawning. | `PaCloudCommand.java`, `CommandCloudService.java`, `WeatherCommandBridge.java` |
| Config | Native cloud mode, movement, shadows, precipitation rendering flags, quality, and dimension policy exist. Some config values are not fully wired into inspected render paths. | `AtmoCommonConfig.java`, `AtmosphereCloudPolicy.java` |

## PA Native Cloud Systems

| System | What It Currently Does | Called From / Path | PA Native or Simple Clouds | Status | Visual / Mechanical | Exact Next Step |
|---|---|---|---|---|---|---|
| Cloud creation | Native clouds can be created through command/weather bridge using `CloudGroupSpawner`. No normal automatic native spawning exists. `CloudRegionSpawner` is empty and `NativeAtmosphereCloudService` is no-op. | Command path: `/pa cloud spawn`, `/pa cloud rain`, `/pa cloud thunder`, bridged `/weather`. Normal path calls service spawn hooks, but they do nothing. | PA native for command-created regions. | Implemented but not plugged in yet | Mechanically works through commands; normal gameplay creation is neither. | Implement `NativeAtmosphereCloudService.trySpawnClouds` and/or `CloudRegionSpawner` to create regions from live atmospheric state. |
| Cloud lifecycle | Existing native regions age, grow, decay, deactivate, and remove inactive clusters. | Normal server tick through `EventHandler` -> `CloudRegionManager.tickCloudRegions`. | PA native | Working and plugged in for existing native regions | Mechanically working; visual effect depends on sync/rendered clouds. | Keep as-is, then feed it normal spawned regions. |
| Cloud movement | Existing clusters drift using forecast wind and `CLOUD_WIND_DRIFT_SCALE`, gated by movement/freeze config. | Normal server tick through `CloudRegionMotionController`. | PA native | Working and plugged in for existing native regions | Mechanically working; visually reflected after sync. | Verify render interpolation and runtime smoothness in-game. |
| Cloud merging | Existing clusters and regions merge based on overlap and merge pressure. | Normal server tick through `CloudRegionMergeController`. | PA native | Working and plugged in for existing native regions | Mechanically working; visually reflected after sync. | Add gameplay constraints once automatic spawning exists, so merging cannot over-collapse local weather. |
| Cloud evolution | Existing regions evaluate type evolution from forecast humidity, temperature, pressure, storm chance, and live atmosphere state. | Normal server tick through `CloudRegionEvolutionController`. | PA native | Working and plugged in for existing native regions | Mechanically working; visual type/profile changes are render-path dependent. | Connect evolved severe cloud states to PA-native storm, tornado, and hurricane modules. |
| Cloud rendering | Client receives cloud region data, builds render snapshots, filters them, raymarches volume clouds, and composites to the main target. | Client render path through `CloudRenderHook` -> `CloudRenderer`. | PA native | Integration gap for normal gameplay, because no automatic native creation; renderer itself is plugged for synced native regions. | Visually unverified at runtime; draw path exists. | Runtime-test command-created native clouds, then fix any shader or target issues found. |
| Cloud shadows | Shadow map is generated from render snapshots, uploaded to a shadow target, then terrain shadow shader is drawn full-screen using scene depth. | `CloudRenderer` calls `CloudShadowRenderer.update` then `CloudTerrainShadowRenderer.render`. | PA native | Integration gap for normal gameplay; plugged for renderable native snapshots. | Visually unverified; mechanically wired. | Runtime-test with native clouds, then tune strength and resolution and respect light/high shader modes. |
| Sounds | Rain sound mixin scales vanilla rain sounds from localized cloud rain level. Client weather audio exists but full audio behavior was not completely inspected. Tornado audio is Simple Clouds oriented. | Client tick and `LevelRenderer.tickRain` mixin. | Mixed | Unverified for full PA-native sound system | Partially mechanical; visual not applicable. | Audit `WeatherAudioClient` and define PA-native thunder, rain, and wind sound ownership separately from Simple Clouds tornado audio. |
| Precipitation | Localized cloud weather can affect rain and thunder levels, `isRainingAt`, trident thunder checks, snow/freeze, and server-side block precipitation. Custom visual precipitation renderer currently returns `false`, so it does not replace vanilla rendering. | Client/server mixins and `WeatherCloudQueries`; custom renderer from `MixinLevelRenderer`. | PA native for localized weather queries; custom visual renderer unfinished. | Integration gap | Mechanical path is partial; visual custom precipitation is neither. | Finish `CustomPrecipitationRenderer` or explicitly make vanilla precipitation the supported light-mode renderer. |
| Sync | Server sends active native cloud render data to players every 20 ticks and on player login/manual sync when Simple Clouds is absent. Client stores it in `ClientCloudRegionDataCache`. | Normal gameplay path through `EventHandler`, `AtmosphereManager.onPlayerLogin`, commands. | PA native | Working and plugged in | Mechanically working for existing regions; visual depends on renderer. | Add delta or chunked sync later if region counts grow. |
| Persistence | Native cloud regions save and load through `CloudRegionSavedData`. Base forecast saves through forecast storage. Live mutable atmospheric state is not fully saved. | Minecraft saved data and forecast storage on server stop/start. | PA native for cloud regions and base forecast | Working and plugged in for cloud regions; integration gap for saved dynamic atmosphere | Mechanical persistence works for cloud regions. | Add saved dynamic state for `RegionAtmosphereState`, active events, drift offsets, cyclone and ocean state. |
| Player localized behavior | Client and server sample cloud regions around positions for rain, thunder, and cloud cover. This drives localized weather APIs and some world interactions. | `WeatherCloudQueries`, `ClientLocalizedWeatherState`, server mixins. | PA native | Working and plugged in for existing native regions | Mechanically working; visual weather depends on vanilla or custom precipitation path. | Use the same query layer for PA-native storm hazards and localized forecast UI. |
| Automatic spawning | Normal tick asks cloud service whether to spawn and then calls spawn, but native service does nothing. | `EventHandler` -> `AtmosphereCloudServices.get().shouldTrySpawn/trySpawnClouds`. | Intended PA native | Intentional work in progress | Neither | Implement atmospheric-state-driven native spawn scheduling. |
| Forecast interaction | Movement and evolution consume forecast and live atmosphere. Command spawning uses cloud type definitions. Automatic creation does not yet use forecast or dynamic state. | `CloudRegionMotionController`, `CloudRegionEvolutionController`, commands. | PA native | Integration gap | Mechanically partial | Make cloud birth depend on live humidity, pressure, wind convergence, temperature lapse, cloud water, and forecast storm chance. |
| Dynamic atmospheric state interaction | Forecast and dynamic state influence movement and evolution. Clouds influence localized weather queries. There is not yet a closed loop where native clouds consume and produce cloud water, storms, and persistent drift. | `AtmosphericStateRegistry`, `ForecastOrchestrator`, `WeatherCloudQueries`. | PA native | Integration gap | Mechanically partial | Add bidirectional coupling: condensation creates clouds, precipitation drains humidity and cloud water, storms modify pressure, wind, and temperature. |

## Rendering And Shadow Chain

| Requirement | Verified State |
|---|---|
| Config allows it | `CLOUD_MODE` policy controls PA cloud rendering; `ENABLE_CLOUD_SHADOW_MAP` gates shadow drawing. |
| Server creates or updates state | Yes for command-created and saved native clouds; no for automatic native clouds. |
| State saved if needed | Native regions use `CloudRegionSavedData`. |
| State synced | `CloudRegionSyncManager` sends `SyncCloudRegionsPacket` when Simple Clouds is absent. |
| Client receives valid data | Packet handler writes to `ClientCloudRegionDataCache`. |
| Render hook called | `CloudRenderHook` runs at `AFTER_PARTICLES`. |
| Render target exists | `CloudRenderTargetManager` creates cloud color/history targets and a 64x64 cloud shadow target. |
| Shader registered or compiled | `CloudShaders` registers `cloud_volume`, `cloud_composite`, and `cloud_terrain_shadow`. Runtime compilation not verified. |
| Uniforms and samplers valid | Code binds scene depth, shadow sampler, inverse matrices, bounds, and strength for terrain shadows. Runtime validity not verified. |
| Draw call stage | Cloud shadows, cloud raymarch, and composite run inside the PA cloud render pass after particles. |
| Blend and depth state | Terrain shadow disables depth test and depth write and enables blending. Cloud raymarch uses blend and depth controls around the draw. |
| Actually visible | Unverified. Code can produce a visible terrain shadow only when valid native snapshots exist. Normal gameplay currently lacks automatic native cloud creation. |

## Simple Clouds Separation

| System | Current Reality | Status |
|---|---|---|
| Simple Clouds service | `SimpleCloudsAtmosphereCloudService` exists, but service selection returns `DisabledAtmosphereCloudService` when Simple Clouds is loaded. | Implemented but not plugged in yet / integration gap |
| Tornadoes | `TornadoManager` uses Simple Clouds `CloudRegion`, `CloudManager`, and `SpawnRegion`; scheduled probability also expects cloud-service severe-cloud checks. | Simple Clouds only |
| Tornado auto probability | `TornadoProbabilityManager` runs only when Simple Clouds is loaded, but `AtmosphereCloudServices.get().hasSevereCloudNearby` returns false because the selected service is disabled. | Actual bug for Simple Clouds tornado automation |
| Hurricanes | `HurricaneManager`, `HurricaneSemantics`, and `HurricaneEnvironmentAnalyzer` use Simple Clouds cloud regions, generation, and reservation assumptions. | Simple Clouds only |
| Blizzards / snowstorms | `SnowstormManager` stores Simple Clouds `CloudRegion`; PA native `/pa cloud snowstorm` creates a native `nimbostratus` cloud but does not attach a native blizzard system. | Simple Clouds only for storm effects; PA native cloud spawn is separate |
| Cloud attachment logic | Tornado, hurricane, and snowstorm attachment assumes Simple Clouds regions, not PA native `CloudRegionState` or clusters. | Simple Clouds only |
| Native severe storms | Cloud evolution can classify and evolve cloud types, but tornado, hurricane, and blizzard production is not PA-native yet. | Intentional work in progress |

## Forecast System

| Layer | What Exists Today |
|---|---|
| Base forecast | Region forecasts are generated, cached, saved, loaded, and reused across server restarts. |
| Live atmospheric state | `RegionAtmosphereState` holds mutable temperature, humidity, pressure, wind, cloud cover, cloud water, rain, and cyclone floors. |
| Weather events | Cyclones, ocean influence, and wind transport update live state. Tornadoes and hurricanes are still Simple Clouds linked. |
| Forecast drift | Live state drifts from base forecast through scheduler updates, ocean flux, wind transport, pressure restoration, sunlight, rain cooling, and cyclone floors. |
| Saved dynamic state | Base forecasts are saved. PA cloud regions are saved. Full live atmospheric state and event drift are not fully saved. |

Current season handling conflicts with the final architecture goal. `ForecastOrchestrator.regenerateForSeason` clears forecast data and active forecast regions, then regenerates them. That is not the desired "forecast generated once, saved, reused, moved, and modified" model.

How to drive forecast forward without deleting the original forecast:

| Driver | Correct Architecture |
|---|---|
| Season changes | Keep base forecast immutable. Apply seasonal drift offsets or target-curve modifiers to live state over time. Save those modifiers. |
| Storms | Represent storms as saved dynamic events that lower pressure, raise humidity and cloud water, increase wind convergence, and decay over time. |
| Pressure systems | Use pressure gradients to move wind and storm cells; relax pressure gradually back toward base forecast instead of regenerating. |
| Ocean influence | Keep ocean reservoirs and influence maps and apply humidity and temperature flux into nearby live states. Save reservoir state. |
| Wind transport | Move humidity, pressure, temperature, and cloud water between neighboring live regions using wind vectors. Save live region state and timestamps. |
| Humidity / temperature | Convert humidity and cloud water into cloud creation and evolution when thresholds are met; precipitation drains humidity and cools air. |
| Forecast reuse | Base forecast remains the stable climatological plan; live state and saved events are the mutable weather layer. |

## What Is Solid

| Area | Assessment |
|---|---|
| Native cloud data model | `CloudRegionState`, clusters, render data, persistence, sync packets, and client cache are coherent. |
| Native server ticking | Lifecycle, movement, merging, and evolution are actually called from normal server tick when Simple Clouds is absent. |
| Native render pipeline | Render hook, targets, shaders, snapshots, raymarch, composite, and shadow pass are wired for synced native cloud data. |
| Local weather queries | The query layer is useful and already connects clouds to localized rain, thunder, and block behavior. |
| Base forecast persistence | Forecast generation and storage are separated enough to support the final architecture. |

## What Blocks The Final Goal

| Blocker | Type |
|---|---|
| No PA-native automatic cloud spawning | Intentional work in progress / integration gap |
| Tornadoes, hurricanes, and blizzards depend on Simple Clouds cloud-region APIs | Simple Clouds only |
| Simple Clouds service implementation is not selected when Simple Clouds is loaded | Actual bug / integration gap |
| Full live atmospheric state is not persisted | Integration gap |
| Season changes regenerate forecasts instead of drifting live state | Integration gap |
| Custom precipitation renderer returns `false` and does not draw | Implemented but not plugged in yet |
| Render and shadow visibility has not been runtime validated | Unverified |
| PA native clouds do not yet form a closed atmospheric feedback loop | Integration gap |

## Implementation Priority Order

1. Fix cloud service ownership first: either select `SimpleCloudsAtmosphereCloudService` when Simple Clouds is loaded, or fully isolate or remove Simple Clouds dependent automation from PA native mode.

2. Implement PA-native automatic spawning in `NativeAtmosphereCloudService` and `CloudRegionSpawner`, using live humidity, pressure, temperature, wind convergence, cloud water, biome and ocean influence, and forecast storm chance.

3. Persist dynamic atmospheric state separately from base forecast: save live `RegionAtmosphereState`, active events, drift offsets, cyclone and ocean state, and timestamps.

4. Replace season forecast regeneration with seasonal drift applied to live state and saved modifiers.

5. Create a PA-native storm-cell abstraction over `CloudRegionState` and clusters, then attach supercell, tornado, blizzard, and hurricane behavior to that abstraction instead of Simple Clouds `CloudRegion`.

6. Port tornadoes, hurricanes, and blizzards away from Simple Clouds assumptions, or explicitly keep them Simple Clouds only until the native storm abstraction exists.

7. Finish precipitation rendering policy: either implement `CustomPrecipitationRenderer` fully or make vanilla precipitation the intentional light-mode path.

8. Runtime-test native rendering and cloud shadows with command-created clouds, then validate shader-safe, light, and high modes and shader-pack compatibility.

9. After automatic spawning and dynamic persistence work, add forecast drift tooling and debug commands to inspect base forecast versus live atmosphere versus events without regenerating everything.
