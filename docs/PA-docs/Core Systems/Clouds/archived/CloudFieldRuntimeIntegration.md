# CloudField Runtime Integration

This document describes the first real backend-to-client CloudField runtime
path now present in code.

Implemented flow:

```text
existing PA backend cloud state
-> CloudFieldSourceSnapshot
-> CloudFieldBackendBridge
-> server CloudFieldStore
-> CloudFieldSnapshot list
-> SyncCloudFieldsPacket
-> ClientCloudFieldCache
-> CloudFieldRendererInput for future renderer
```

## Server Runtime Flow

`CloudFieldRuntimeManager` owns the server-side CloudField runtime for each
level dimension.

It stores:

```text
CloudFieldStore per dimension
last CloudFieldSourceSnapshot per dimension
last backend bridge apply result per dimension
current CloudFieldRendererInput per dimension
field id -> source debug mapping
```

On tick:

```text
CloudFieldBackendSourceCollector.collect(level)
-> CloudFieldBackendBridge.applySnapshot(store, sources, removeMissing=true)
-> CloudFieldStore.tickAll(context)
-> CloudFieldSnapshotFactory.createRendererInput(...)
```

The runtime manager does not render, does not know about shaders, and does not
implement cloudlet relation logic.

## Backend Source Collection

`CloudFieldBackendSourceCollector` reads existing active PA-native backend data:

```text
CloudRegionStateStore.getActiveRegions(level)
```

It uses active `CloudClusterState` as the primary source because clusters are
closer to the future cloud-mass model and the existing legacy transport path
already emits one render-data object per active cluster.

If an active region has no active clusters, the collector falls back to a whole
`CloudRegionState` source.

The collector does not duplicate spawn rules, weather sampling, lifecycle logic,
or merge logic. It only converts existing state into `CloudFieldSource`.

## Sync Packet Contract

`SyncCloudFieldsPacket` sends render-safe `CloudFieldSnapshot` data only.

Synced:

```text
field id
seed
dimension
center / previous center
radius
baseY / topY
density / coverage
growth / decay
humidity influence
wind
vertical development
storm potential
LOD and hydration hints
age / lifetime
target and active cloudlet counts
world time
```

Not synced:

```text
individual cloudlets
cloudlet positions
cloudlet collision state
merge/bridge relation state
GPU feedback data
renderer-specific buffers
shader uniforms
```

Cloudlet layout remains deterministic:

```text
field seed + CloudletId
```

## Client Cache Ownership

`ClientCloudFieldCache` stores the latest packet snapshots.

It also owns client-local runtime hydration state:

```text
field id -> CloudFieldRuntimeState
```

When future rendering asks for input, the cache can produce:

```text
CloudFieldRendererInput
```

using:

```text
latest synced snapshots
client camera position
client world time
local distance classification
local hydration controller
```

This lets each client hydrate/dehydrate cloudlets by its own camera distance
without syncing per-cloudlet data.

## Wiring Now Present

The path is now wired in these places:

```text
NetworkHandler registers SyncCloudFieldsPacket
ClientOnlyRegistrar registers CloudFieldPacketDispatcher -> ClientCloudFieldCache
EventHandler ticks CloudFieldRuntimeManager when PA_NATIVE backend is active
EventHandler syncs CloudField snapshots beside legacy region sync
Cloud command sync sends both legacy region data and CloudField snapshots
/pa cloud fields prints current CloudField runtime state
```

The old `SyncCloudRegionsPacket` remains in place. It is not replaced yet.

## Debug Visualization

The first client-side CloudField visualization is now implemented as a debug
overlay:

```text
ClientCloudFieldCache
-> CloudFieldRendererInput
-> CloudFieldDebugRenderHook
-> CloudFieldDebugRenderer
```

It uses the `AFTER_PARTICLES` world-space debug render path and draws field
radius rings, base/top vertical extent, center markers, movement/wind lines, and
deterministic sample cloudlet markers.

It is enabled with:

```text
/pa system cloudFieldDebug on
```

or implicitly while an existing `cloudRenderDebug` mode is active.

The debug renderer consumes only `CloudFieldRendererInput`. It does not read
`CloudRegionState`, `CloudManager`, or backend simulation state.

See `CloudFieldDebugRenderer.md` for details.

## Callable But Not Final Renderer Integration

Future renderer code should read:

```text
ClientCloudFieldCache.createRendererInput(cameraPosition, worldTime, partialTick)
```

This pass does not connect that method to the current cloud renderer.

## Still Future Work

Not implemented:

```text
final renderer integration
shader changes
cloudlet collision
cloudlet merge
bridge density
absorption
erosion relation states
GPU feedback map
GPU readback
CloudField-native server packets beyond snapshots
replacement of legacy region transport
Simple Clouds renderer rewrite
```

The current runtime path is a bridge. It lets the existing PA-native backend
feed stable CloudFields without destructively refactoring the old cloud system.
