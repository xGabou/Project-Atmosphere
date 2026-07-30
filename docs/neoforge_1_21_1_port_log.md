# NeoForge 1.21.1 Port Log

## 2026-06-16 Rebuild Baseline

- Fast-forwarded `NeoForge-1.21.1` to `origin/NeoForge-1.21.1` at `54ee0d8`.
- Created safety branch: `backup/neoforge-1.21.1-before-rebuild-20260616-104117`.
- Backed up the old NeoForge `src` outside the worktree:
  `G:\Project-Atmosphere-neoforge-src-backup-20260616-104117`.
- Replaced the NeoForge worktree baseline with `Forge-1.20.1`, then restored NeoForge 1.21.1 Gradle/template metadata.
- Overlaid the old NeoForge `src/main/java` and `src/main/resources` back onto matching paths to reuse known API ports from the previous 1.21.1 work.

## Build Fixes Applied

- Added explicit UTF-8 Java compilation in `build.gradle`.
- Temporarily disabled the Mixin annotation processor.
  - Reason: Forge-era mixin targets fail annotation-processor target validation before the Java port can compile.
  - Restore once the excluded Forge-only mixin classes are ported to valid 1.21.1/NeoForge targets.
- Added `docs/neoforge_1_21_1_unported_sources.txt` and configured Gradle to exclude those sources from `compileJava`.
  - Count: 352 Java files.
  - These are Forge-baseline Java files that did not exist in the previous NeoForge source backup.

## Current Result

- `.\gradlew.bat build --stacktrace` succeeds.
- The successful build currently represents the previous NeoForge 1.21.1 source plus current Forge resources/files that do not break compilation.
- Full source parity is not complete yet because the Forge-only Java additions are preserved but excluded from compilation.
- This is a compile staging baseline only. It is not considered a completed port because the excluded Forge-only files have not gone through behavioral parity review.
- All future file removals from `docs/neoforge_1_21_1_unported_sources.txt` must follow `docs/neoforge_1_21_1_porting_requirements.md`.

## Unresolved Port Buckets

- Networking: Forge `SimpleChannel`, `NetworkEvent.Context`, and `PacketDistributor` code must be rewritten to NeoForge payload APIs.
- Optional integrations: Dynamic Trees, Desert Storms, Legendary Survival Overhaul, Ecliptic Seasons, Project Atmosphere for TFC, and related mixins need 1.21.1 NeoForge dependencies or guarded adapters.
- Mixins: Forge-only mixin classes need valid 1.21.1 target names and should be re-enabled only after each target compiles.
- Config/API drift: Forge-only config enums and diagnostics types need to be merged into the NeoForge `AtmoCommonConfig`, telemetry, and atmosphere scheduler classes.
- Registry types: Forge `RegistryObject` usages in excluded Forge-only classes should be ported to `DeferredHolder` or a loader-neutral supplier interface.

## Next Acceptance Target

- Port one excluded bucket at a time.
- Before changing code, inspect the Forge 1.20.1 implementation, the NeoForge/Mojang 1.21.1 replacement, and any vanilla/dependency callsites needed to prove behavioral equivalence.
- Remove files from `docs/neoforge_1_21_1_unported_sources.txt` only after adding a per-file evidence entry below.
- Re-run `.\gradlew.bat build --stacktrace` after each bucket, but treat build success as one verification point, not as acceptance by itself.
- Restore the Mixin annotation processor only when all included mixin classes have documented equivalent NeoForge targets.

## Per-File Port Evidence

Use this template for every file removed from `docs/neoforge_1_21_1_unported_sources.txt`:

```text
File:
<path>
Forge API Removed:
<old api>
NeoForge Replacement:
<new api>
Reason:
<why replacement is correct>
Behavior Change:
<none / describe>
Risk Level:
Low / Medium / High
Verification:
Build success / Runtime verified / Needs testing
```

### 2026-06-16 Storm Shield And Simple API Bucket

File:
`net/Gabou/projectatmosphere/api/Celsius.java`
Forge API Removed:
None.
NeoForge Replacement:
None.
Reason:
This record is pure Project Atmosphere data used to return snow eligibility and local Celsius temperature. It has no Forge, NeoForge, Mojang, registry, event, or lifecycle dependency.
Behavior Change:
None.
Risk Level:
Low.
Verification:
Build success; runtime testing not required beyond consumers.

File:
`net/Gabou/projectatmosphere/blocks/WeatherDebrisBudget.java`
Forge API Removed:
None.
NeoForge Replacement:
None.
Reason:
This package-private budget helper only tracks a per-tick integer token budget for weather debris. It has no loader API dependency and preserves the Forge 1.20.1 behavior exactly.
Behavior Change:
None.
Risk Level:
Low.
Verification:
Build success; needs runtime testing only when debris spawning files are ported.

File:
`net/Gabou/projectatmosphere/blocks/StormShieldBlock.java`
Forge API Removed:
None in this file; vanilla `Block#onPlace` and `Block#onRemove` signatures are still present in 1.21.1.
NeoForge Replacement:
None.
Reason:
The block still registers and unregisters shield positions only on the logical server and only when the block type actually changes. This preserves the original Forge behavior and delegates lifecycle indexing to `StormShieldManager`.
Behavior Change:
None.
Risk Level:
Low.
Verification:
Build success; needs in-game placement/break verification.

File:
`net/Gabou/projectatmosphere/modules/weather/StormShieldManager.java`
Forge API Removed:
`ForgeRegistries.BLOCKS.getValue(ResourceLocation)` and Forge `RegistryObject`-style block lookup.
NeoForge Replacement:
`ModBlocks.STORM_SHIELD.get()` through NeoForge `DeferredHolder<Block, Block>`.
Reason:
The Forge implementation resolved the storm shield block lazily during chunk scans and block events, after the block registry is available. NeoForge `DeferredHolder#get()` preserves that runtime lazy access pattern for registered entries. Using the mod's own `DeferredHolder` avoids string registry lookup drift and keeps initialization aligned with the existing NeoForge block registration lifecycle.
Behavior Change:
None intended. The lookup is now strongly tied to the registered holder instead of re-querying the global block registry by id.
Risk Level:
Medium.
Verification:
Build success; needs runtime verification for shield placement, break, chunk load scan, chunk unload cleanup, and storm/hurricane avoidance behavior.

### 2026-06-16 Networking, Auth, Fog, And Cloud Sync Bucket

File:
`net/Gabou/projectatmosphere/network/AuthChallengePacket.java`
Forge API Removed:
`SimpleChannel` packet registration and `Supplier<NetworkEvent.Context>` handler.
NeoForge Replacement:
`CustomPacketPayload`, `StreamCodec<FriendlyByteBuf, AuthChallengePacket>`, `IPayloadContext`, registered with `PayloadRegistrar.playToClient`.
Reason:
The original packet was server-to-client play traffic carrying only the auth nonce. NeoForge 1.21.1 moved play networking to typed custom payloads. The handler still enqueues client work and only calls `ClientAuth.handleChallenge` on the client dist.
Behavior Change:
None intended.
Risk Level:
Medium.
Verification:
Build success; needs multiplayer login test.

File:
`net/Gabou/projectatmosphere/network/AuthChallengeReplyPacket.java`
Forge API Removed:
`NetworkEvent.Context#getSender`.
NeoForge Replacement:
`IPayloadContext#player()` on a `playToServer` payload.
Reason:
The original behavior needed the sending `ServerPlayer` for nonce validation. NeoForge exposes that player through the payload context during serverbound play handling.
Behavior Change:
None intended.
Risk Level:
Medium.
Verification:
Build success; needs multiplayer login test.

File:
`net/Gabou/projectatmosphere/auth/ClientAuth.java`
Forge API Removed:
`NetworkHandler.CHANNEL.sendToServer`.
NeoForge Replacement:
`PacketDistributor.sendToServer`.
Reason:
The reply remains client-to-server play traffic; NeoForge sends typed payloads through `PacketDistributor`.
Behavior Change:
None intended.
Risk Level:
Medium.
Verification:
Build success; needs multiplayer login test.

File:
`net/Gabou/projectatmosphere/auth/ServerAuth.java`
Forge API Removed:
`NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(...), payload)`.
NeoForge Replacement:
`PacketDistributor.sendToPlayer`.
Reason:
The challenge remains targeted server-to-client play traffic for one player. NeoForge replaced the Forge distributor target builder with direct helper methods.
Behavior Change:
None intended.
Risk Level:
Medium.
Verification:
Build success; needs multiplayer login test.

File:
`net/Gabou/projectatmosphere/network/FogDebugOverridePacket.java`
Forge API Removed:
`SimpleChannel` handler signatures.
NeoForge Replacement:
`CustomPacketPayload`, `StreamCodec`, `IPayloadContext`, registered `playToClient`.
Reason:
The packet still carries strength and duration to client fog state only. NeoForge typed payloads preserve serialization order and client-thread enqueue semantics.
Behavior Change:
None intended.
Risk Level:
Low.
Verification:
Build success; needs `/pa fog spawn` and `/pa fog clear` runtime test.

File:
`net/Gabou/projectatmosphere/command/tree/service/CommandFogService.java`
Forge API Removed:
Forge channel send.
NeoForge Replacement:
`PacketDistributor.sendToPlayer`.
Reason:
The command still targets only the invoking player with a fog override payload.
Behavior Change:
None intended.
Risk Level:
Low.
Verification:
Build success; needs command runtime test.

File:
`net/Gabou/projectatmosphere/modules/fog/FogCommand.java`
Forge API Removed:
Forge channel send.
NeoForge Replacement:
`PacketDistributor.sendToPlayer`.
Reason:
The legacy fog command still targets only the invoking player with the same override packet.
Behavior Change:
None intended.
Risk Level:
Low.
Verification:
Build success; needs command runtime test.

File:
`net/Gabou/projectatmosphere/network/SyncAtmosphereStatusPacket.java`
Forge API Removed:
`SimpleChannel` handler signatures.
NeoForge Replacement:
`CustomPacketPayload`, `StreamCodec`, `IPayloadContext`, registered `playToClient`.
Reason:
The packet remains server-to-client play sync for humidity, rain intensity, and cloud cover. Client application still delegates to `ClientPacketHandlers`.
Behavior Change:
None intended.
Risk Level:
Medium.
Verification:
Build success; needs client fog/atmosphere sync runtime test.

File:
`net/Gabou/projectatmosphere/modules/atmosphere/AtmosphereStatusSyncManager.java`
Forge API Removed:
Forge channel send.
NeoForge Replacement:
`PacketDistributor.sendToPlayer`.
Reason:
The manager still sends one atmosphere snapshot to each player on the configured interval.
Behavior Change:
None intended.
Risk Level:
Medium.
Verification:
Build success; needs server-client sync runtime test.

File:
`net/Gabou/projectatmosphere/clouds/network/SyncCloudRegionsPacket.java`
Forge API Removed:
`SimpleChannel` handler signatures.
NeoForge Replacement:
`CustomPacketPayload`, `StreamCodec`, `IPayloadContext`, registered `playToClient`.
Reason:
The packet still transports only `CloudRegionRenderData` from server cloud state to client render cache. NeoForge payload registration preserves play-phase delivery and serialization order.
Behavior Change:
None intended.
Risk Level:
Medium.
Verification:
Build success; needs native cloud render sync runtime test.

File:
`net/Gabou/projectatmosphere/clouds/network/CloudRegionSyncManager.java`
Forge API Removed:
Forge channel send.
NeoForge Replacement:
`PacketDistributor.sendToPlayer`.
Reason:
The manager still sends native PA cloud render data only to the target player when Simple Clouds is not owning the backend.
Behavior Change:
None intended.
Risk Level:
Medium.
Verification:
Build success; needs native cloud render sync runtime test.

File:
`net/Gabou/projectatmosphere/network/RemoveTornadoPacket.java`
Forge API Removed:
`SimpleChannel` handler signatures.
NeoForge Replacement:
`CustomPacketPayload`, `StreamCodec`, `IPayloadContext`, registered `playToClient`.
Reason:
The packet remains server-to-client tornado removal. NeoForge typed payloads preserve delivery semantics.
Behavior Change:
The current NeoForge `TornadoInstance` model does not carry the UUID needed for exact client removal, so the port clears the client tornado cache on removal to avoid stale visuals. This is behaviorally safe but less granular than Forge 1.20.1.
Risk Level:
High.
Verification:
Build success; needs tornado spawn/despawn runtime test.

File:
`net/Gabou/projectatmosphere/network/SyncTornadoesPacket.java`
Forge API Removed:
`SimpleChannel` handler signatures.
NeoForge Replacement:
`CustomPacketPayload`, `StreamCodec`, `IPayloadContext`, registered `playToClient`.
Reason:
The packet still carries tornado render snapshots. The handler rebuilds client tornado visuals from the received snapshots.
Behavior Change:
Client snapshot application is approximate because the current `TornadoInstance` lacks the richer Forge 1.20.1 identity/lifecycle fields.
Risk Level:
High.
Verification:
Build success; needs tornado visual sync runtime test.

File:
`net/Gabou/projectatmosphere/network/SyncHurricaneStatePacket.java`
Forge API Removed:
`SimpleChannel` handler signatures.
NeoForge Replacement:
`CustomPacketPayload`, `StreamCodec`, `IPayloadContext`, registered `playToClient`.
Reason:
The packet still carries hurricane render snapshots and applies them to `ClientHurricaneStateCache`.
Behavior Change:
Hurricane snapshots are adapted from the older local `HurricaneInstance` model; render geometry should work, but semantic parity with the richer Forge hurricane lifecycle needs runtime validation.
Risk Level:
High.
Verification:
Build success; needs hurricane sync/render runtime test.

File:
`net/Gabou/projectatmosphere/network/NetworkHandler.java`
Forge API Removed:
`SimpleChannel#registerMessage`.
NeoForge Replacement:
`RegisterPayloadHandlersEvent` and `PayloadRegistrar.playToClient/playToServer`.
Reason:
NeoForge 1.21.1 payload registration is centralized during mod event registration and preserves explicit packet direction.
Behavior Change:
None intended.
Risk Level:
Medium.
Verification:
Build success.

File:
`net/Gabou/projectatmosphere/clouds/backend/CloudBackendMigrationSavedData.java`
Forge/Mojang API Removed:
Old `DimensionDataStorage#computeIfAbsent(load, create, id)` and `SavedData#save(CompoundTag)`.
NeoForge Replacement:
`SavedData.Factory` and `save(CompoundTag, HolderLookup.Provider)`.
Reason:
Minecraft 1.21.1 moved saved-data deserialization into `SavedData.Factory` and passes registry lookup context into saves. The stored NBT payload is unchanged.
Behavior Change:
None intended.
Risk Level:
Low.
Verification:
Build success; needs world save/load test.

File:
`net/Gabou/projectatmosphere/clouds/state/CloudRegionSavedData.java`
Forge/Mojang API Removed:
Old `DimensionDataStorage#computeIfAbsent(load, create, id)` and `SavedData#save(CompoundTag)`.
NeoForge Replacement:
`SavedData.Factory` and `save(CompoundTag, HolderLookup.Provider)`.
Reason:
Same 1.21.1 saved-data migration as backend migration state. Cloud region registry persistence still delegates to `CloudRegionStorage`.
Behavior Change:
None intended.
Risk Level:
Low.
Verification:
Build success; needs world save/load test.

File:
`net/Gabou/projectatmosphere/clouds/state/CloudRegionState.java`, `net/Gabou/projectatmosphere/clouds/state/CloudClusterState.java`, `net/Gabou/projectatmosphere/modules/core/WeatherType.java`
Forge/Mojang API Removed:
Public `ResourceLocation(String)` / `ResourceLocation(String, String)` constructors.
NeoForge Replacement:
`ResourceLocation.parse` and `ResourceLocation.fromNamespaceAndPath`.
Reason:
Mojang made constructors private in 1.21. The factory methods are the direct behavioral replacements for stored full IDs and namespace/path literals.
Behavior Change:
None intended.
Risk Level:
Low.
Verification:
Build success.

File:
`net/Gabou/projectatmosphere/config/AtmoCommonConfig.java`, `net/Gabou/projectatmosphere/modules/atmosphere/RegionAtmosphereState.java`, `net/Gabou/projectatmosphere/modules/core/WindVector.java`, `net/Gabou/projectatmosphere/modules/storm/GlobalStormHistoryData.java`, `net/Gabou/projectatmosphere/manager/ForecastOrchestrator.java`
Forge API Removed:
None; missing Forge-baseline behavior after the worktree rebuild.
NeoForge Replacement:
Restored NeoForge `ModConfigSpec` values and runtime helper methods using existing 1.21.1 classes.
Reason:
The re-enabled cloud/fog/weather code depends on Forge-baseline config keys, atmospheric cloud-water/cyclone fields, wind humidity transport, storm-history cooldown counters, and region-key forecast sampling. Restoring these preserves behavior instead of compiling by deleting those calls.
Behavior Change:
None intended, except existing tornado/hurricane adapters noted separately.
Risk Level:
Medium.
Verification:
Build success; needs cloud evolution/weather-cell runtime testing.

## 2026-07-29 Region-First Forecast Restoration

The June rebuild restored older NeoForge files over matching Forge paths. That kept
loader compatibility, but it also replaced the 0.9.1 region-first forecast runtime
with the legacy biome-map generator. This pass restores the authoritative
`Forge-1.20.1` forecast implementation from `84ab6b5`, the last forecast-equivalent
commit before later cloud-renderer work.

File:
`net/Gabou/projectatmosphere/manager/ForecastGenerator.java`,
`net/Gabou/projectatmosphere/manager/ForecastOrchestrator.java`,
`net/Gabou/projectatmosphere/manager/ForecastDataStorage.java`,
`net/Gabou/projectatmosphere/modules/region/DefaultRegionCurves.java`,
`net/Gabou/projectatmosphere/modules/region/FileRegionPersistence.java`,
`net/Gabou/projectatmosphere/modules/region/ForecastRegion.java`,
`net/Gabou/projectatmosphere/modules/region/GridRegionIndex.java`,
`net/Gabou/projectatmosphere/modules/region/RegionAdapters.java`,
`net/Gabou/projectatmosphere/modules/region/RegionCurves.java`,
`net/Gabou/projectatmosphere/modules/region/RegionForecastOrchestrator.java`,
`net/Gabou/projectatmosphere/modules/region/RegionIdCodec.java`,
`net/Gabou/projectatmosphere/modules/region/RegionIndex.java`,
`net/Gabou/projectatmosphere/modules/region/RegionOrchestratorBootstrap.java`,
`net/Gabou/projectatmosphere/modules/region/RegionPersistence.java`,
`net/Gabou/projectatmosphere/modules/region/RegionBiomeSample.java`,
`net/Gabou/projectatmosphere/modules/region/RegionForecastCorruptionValidator.java`
Forge API Removed:
Forge `SimpleChannel`/`PacketDistributor` send targets.
NeoForge Replacement:
NeoForge typed payload dispatch through `PacketDistributor.sendToPlayer` and
`PacketDistributor.sendToAllPlayers`.
Reason:
Forecast identity, persistence, migration, aggregation, sampling, and corruption
repair are region owned in 0.9.1. Typed payload dispatch preserves the existing
temperature snapshot delivery semantics on NeoForge 1.21.1.
Behavior Change:
Legacy biome-key callers use deprecated adapters that resolve through
`BiomeInstanceKey.samplePos()` into the owning `RegionInstanceKey`; no biome-owned
runtime forecast map is restored.
Risk Level:
High.
Verification:
`compileJava` success; full build success required; needs fresh-world generation,
legacy migration, login sync, regeneration, and save/reload runtime tests.

File:
`net/Gabou/projectatmosphere/client/loading/IntegratedForecastLoadingBridge.java`
Forge API Removed:
Forge `DistExecutor` and Forge dist marker imports.
NeoForge Replacement:
`FMLEnvironment.dist.isClient()` guards the integrated-client state update.
Reason:
The bridge only mirrors server generation progress into the in-process client.
Dedicated servers skip the client-only method before touching `Minecraft`.
Behavior Change:
None intended.
Risk Level:
Medium.
Verification:
Build success; needs integrated-server loading overlay test.

File:
`net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericStateLookup.java`,
`net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericStateSavedData.java`,
`net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericTelemetryReporter.java`,
`net/Gabou/projectatmosphere/modules/atmosphere/CloudWaterExchange.java`,
`net/Gabou/projectatmosphere/modules/atmosphere/CloudWaterService.java`,
`net/Gabou/projectatmosphere/modules/atmosphere/CycloneImpactApplier.java`,
`net/Gabou/projectatmosphere/modules/atmosphere/CycloneManager.java`,
`net/Gabou/projectatmosphere/modules/atmosphere/CycloneSnapshot.java`,
`net/Gabou/projectatmosphere/modules/atmosphere/HumidityBudget.java`,
`net/Gabou/projectatmosphere/modules/atmosphere/HumidityBudgetService.java`,
`net/Gabou/projectatmosphere/modules/atmosphere/HumiditySourceProfile.java`,
`net/Gabou/projectatmosphere/modules/atmosphere/SeasonalAtmosphericDrift.java`,
`net/Gabou/projectatmosphere/seasons/SeasonClimateProfile.java`
Forge/Mojang API Removed:
Minecraft 1.20 `SavedData` factory and single-argument `save` signature.
NeoForge Replacement:
Minecraft 1.21.1 `SavedData.Factory` and
`save(CompoundTag, HolderLookup.Provider)`.
Reason:
These classes are the mutable atmosphere layer driven by the restored immutable
region forecast. The registry lookup parameter is unused because the payload
contains primitives and Project Atmosphere region keys only.
Behavior Change:
None intended.
Risk Level:
High.
Verification:
Build success; needs live-state persistence, seasonal transition, cyclone, and
humidity/cloud-water runtime tests.

File:
`net/Gabou/projectatmosphere/modules/ocean/AtmosphericVolume.java`,
`net/Gabou/projectatmosphere/modules/ocean/AtmosVolumeInfluence.java`,
`net/Gabou/projectatmosphere/modules/ocean/influence/AtmosphereFluxInfluence.java`,
`net/Gabou/projectatmosphere/modules/ocean/influence/BasinPressureMemoryInfluence.java`,
`net/Gabou/projectatmosphere/modules/ocean/influence/BasinThermalMemoryInfluence.java`,
`net/Gabou/projectatmosphere/modules/ocean/OceanBasin.java`,
`net/Gabou/projectatmosphere/modules/ocean/OceanBasinManager.java`,
`net/Gabou/projectatmosphere/modules/ocean/OceanBiomeClassifier.java`,
`net/Gabou/projectatmosphere/modules/ocean/OceanInfluence.java`,
`net/Gabou/projectatmosphere/modules/ocean/OceanUpdateContext.java`
Forge API Removed:
Forge mod-list queries inherited from the Forge compatibility handler.
NeoForge Replacement:
NeoForge `ModList` checks exposed by `CompatHandler` for Tectonic and Continents.
Reason:
Ocean thermal and pressure memory are inputs to the 0.9.1 dynamic forecast.
The optional keyword expansion remains gated by the same mod IDs.
Behavior Change:
None intended.
Risk Level:
Medium.
Verification:
Build success; needs ocean/coastal region generation and persistence tests.

File:
`net/Gabou/projectatmosphere/modules/weather/RegionalWeatherPhase.java`,
`net/Gabou/projectatmosphere/modules/weather/ServerWeatherStateResolver.java`,
`net/Gabou/projectatmosphere/modules/weather/SnowTier.java`,
`net/Gabou/projectatmosphere/modules/weather/StormMotionModel.java`,
`net/Gabou/projectatmosphere/modules/weather/StormSeverityScale.java`,
`net/Gabou/projectatmosphere/modules/weather/WeatherSampler.java`,
`net/Gabou/projectatmosphere/modules/weathercell/WeatherCellFormationController.java`,
`net/Gabou/projectatmosphere/modules/weathercell/WeatherCellLifecycleController.java`,
`net/Gabou/projectatmosphere/modules/weathercell/WeatherCellManager.java`,
`net/Gabou/projectatmosphere/modules/weathercell/WeatherCellMotionController.java`,
`net/Gabou/projectatmosphere/modules/weathercell/WeatherCellSavedData.java`,
`net/Gabou/projectatmosphere/modules/weathercell/WeatherCellState.java`,
`net/Gabou/projectatmosphere/modules/weathercell/WeatherCellSupport.java`,
`net/Gabou/projectatmosphere/modules/weathercell/WeatherCellType.java`
Forge/Mojang API Removed:
Minecraft 1.20 `SavedData` construction and save signature.
NeoForge Replacement:
Minecraft 1.21.1 `SavedData.Factory` with registry-aware save.
Reason:
Weather phases and cells consume region forecast and live atmosphere values; they
are required for behavioral parity even when native cloud rendering is deferred.
Behavior Change:
None intended.
Risk Level:
High.
Verification:
Build success; needs cell formation, motion, lifecycle, weather phase, and
save/reload tests.

File:
`net/Gabou/projectatmosphere/modules/wind/RegionWindForecastApi.java`,
`net/Gabou/projectatmosphere/modules/wind/WindForecastApi.java`,
`net/Gabou/projectatmosphere/modules/temperature/util/LocalBiomeTemperatureResolver.java`,
`net/Gabou/projectatmosphere/modules/tornado/scheduling/TornadoSpawnScheduler.java`
Forge API Removed:
None.
NeoForge Replacement:
None; these are loader-neutral forecast consumers.
Reason:
They were staged only because they were absent from the old NeoForge overlay.
Their inputs now resolve through the restored region forecast and wind runtime.
Behavior Change:
None intended.
Risk Level:
Medium.
Verification:
Build success; needs wind API, local temperature, and tornado scheduling tests.

File:
`net/Gabou/projectatmosphere/manager/SandStormManager.java`,
`net/Gabou/projectatmosphere/modules/sandStorm/SandStormAPI.java`
Forge API Removed:
Direct Desert Storms `SandstormPhase` and sound API references, which have no
configured NeoForge 1.21.1 dependency.
NeoForge Replacement:
Internal phase bookkeeping and a no-op optional bridge.
Reason:
Region forecast generation must not fail to compile when the unavailable optional
integration is absent. The boundary is explicit so a future compatible dependency
can replace the bridge without changing forecast ownership.
Behavior Change:
Project Atmosphere computes sandstorm-eligible regions, but it does not start
external Desert Storms effects on NeoForge 1.21.1.
Risk Level:
Medium.
Verification:
Build success; external integration unavailable for runtime verification.

## 2026-07-29 Full Current-Branch Port

This section supersedes the June staging status and the earlier forecast-only
restoration notes.

- Ported the complete `Forge-1.20.1` source state at `e16523f` to NeoForge
  1.21.1. The source trees have identical Java path inventories: 616 Forge
  sources, 616 enabled NeoForge sources, zero missing paths, and zero extra
  stale paths.
- Removed all Java compilation exclusions. The build emits 948 class files.
- Ported lifecycle registration, configuration, game events, tick events,
  registries, typed payload networking, saved data, client screens, particles,
  render buffers, shader registration, and 1.21 resource-location APIs.
- Ported the current forecast, atmosphere, weather-cell, wind, temperature,
  tornado, hurricane, precipitation, cloud-region, cloud-field, and volumetric
  cloud implementations. Clouds are included even though they were not the
  priority.
- Updated optional integration boundaries for Dynamic Trees, Ecliptic Seasons,
  Legendary Survival Overhaul, Serene Seasons Plus, Distant Horizons, and
  Desert Storms. Desert Storms uses reflection because no compatible artifact
  is available to compile against; native sand movement remains active and an
  installed compatible external mod can be invoked without a hard dependency.
- Updated NeoForge metadata to the `type = "required"` / `type = "optional"`
  dependency schema. Simple Clouds and Gaboulibs remain required.
- Synced current assets, data, sounds, mixin configuration, and cloud shaders.
  Removed stale Forge metadata and superseded shader resources.

Verification:

- `gradlew clean build --console=plain --no-daemon`: successful, including
  tests.
- 179 resource JSON files parsed successfully.
- All 42 mixin configuration entries resolve to compiled classes.
- NeoForge client startup reached completed resource reload and renderer
  initialization. Project Atmosphere volumetric/cloud-field shaders registered,
  the replacement Simple Clouds cube-mesh shader reported valid, and Simple
  Clouds finished initialization without Project Atmosphere errors.
- NeoForge dedicated-server startup reached `Done` with Project Atmosphere
  common setup active and no distribution, payload, mixin, or classloading
  failures.
- Loaded the existing `New World (3)` integrated-server save and rendered live
  gameplay. The test exposed and fixed the remaining Simple Clouds 1.21
  two-matrix callback signatures in the default, shader-support, and Distant
  Horizons pipeline mixins. After rebuilding, the player joined successfully,
  `/pa status` returned live forecast/weather values, the world ticked without
  errors, and all dimensions saved cleanly on exit.
- Packaged artifact:
  `build/libs/NeoForge-projectatmosphere-0.9.1.1-alpha.jar`.
