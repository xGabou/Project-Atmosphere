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
