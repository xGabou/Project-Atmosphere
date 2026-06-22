# CloudField Backend Integration Plan

This document describes the integration layer that lets existing Project
Atmosphere backend cloud/weather data become `CloudField` data.

The base snapshot network packet is implemented. Renderer, shader, merge,
collision, GPU feedback, and readback work are still intentionally not
implemented.

Target flow:

```text
existing PA weather / cloud state
-> CloudField source/adapters
-> CloudFieldStore
-> CloudFieldLifecycleController
-> CloudFieldSnapshotFactory
-> SyncCloudFieldsPacket
-> ClientCloudFieldCache
-> future renderer input
```

## Audited Existing Systems

### CloudManager

`CloudManager` currently samples Simple Clouds regions and surrounding
atmosphere state. It tracks cloud thickness, rain intensity, cloud type,
position observations, and spawn decisions.

For CloudField, this is a future upstream weather/atmosphere contributor, not
something to refactor in this pass. Its sampled humidity, rain, pressure,
temperature, cloud type, and wind decisions can eventually produce or influence
`CloudFieldSource`.

### CloudRegionState

`CloudRegionState` is the strongest current PA-native backend source. It owns:

```text
region id
dimension
aggregate center
aggregate radius
baseY / topY
density
coverage
velocity
growth
decay
cloud type
morphology family
cloud seed
age/lifetime
clusters
```

For the bridge, a whole region can become one `PA_REGION` source.

### CloudClusterState

`CloudClusterState` is closer to the future cloud-mass concept than
`CloudRegionState`. It owns:

```text
cluster id
center / previous center
radius
baseY / topY
velocity
density
coverage
growth
decay
cloud type
morphology family
cloud seed
age/lifetime
```

For the bridge, a cluster can become one `PA_CLUSTER` source. This is likely the
cleaner future mapping once the old region container is treated as atmosphere or
grouping data.

### CloudRegionManager

`CloudRegionManager` owns creation, ticking, lifecycle, motion, evolution,
merge controller calls, persistence, and current transport data exposure.

It is transitional. This pass does not modify it. A future integration can ask
it or `CloudRegionStateStore` for active regions and pass those states into
`CloudFieldBackendAdapter`.

### WeatherCloudQueries and CloudWeatherSample

`WeatherCloudQueries` currently samples active transport data for rain and
thunder behavior. `CloudWeatherSample` is a weather query result, not enough to
create a full CloudField on its own because it lacks full geometry and stable
field identity.

These remain weather consumers for now. Later they can read CloudField snapshots
or source summaries after CloudField sync exists.

### AtmosphereCloudPolicy

`AtmosphereCloudPolicy` decides whether PA owns clouds/weather in a dimension
and whether vanilla clouds should be suppressed.

It should gate future CloudField activation, but it is not modified here.

### CloudRegionRenderData

`CloudRegionRenderData` is transportable region data already sent to clients.
It contains enough geometry and lifecycle values to create a client-side
`PA_RENDER_DATA` CloudField source during the transition period.

It remains transitional because it is named and shaped around old region render
data rather than future field snapshots.

### SyncCloudRegionsPacket and Client Region Cache

`SyncCloudRegionsPacket` sends `CloudRegionRenderData`. `ClientCloudRegionDataCache`
stores those transport objects only.

This pass adds `SyncCloudFieldsPacket` beside the legacy region packet. Future
work should eventually replace or augment old region transport after the field
renderer is proven.

## New Bridge Concepts

### CloudFieldSource

`CloudFieldSource` is neutral source data that can become a `CloudField`.

It describes:

```text
source id
source type
dimension
center
radius
baseY / topY
density
coverage
humidity influence
wind
growth
decay
vertical development
storm potential
seed
age/lifetime hints
cloudlet count hint
cloud type / morphology labels
active flag
```

It is not a renderer object and does not own cloudlet runtime state.

### CloudFieldSourceType

Source types currently prepared:

```text
PA_REGION
PA_CLUSTER
PA_RENDER_DATA
BACKEND_BRIDGE_SNAPSHOT
MANUAL_DEBUG
WEATHER_SUMMARY
```

`WEATHER_SUMMARY` is reserved for future high-level weather system sources.

### CloudFieldSourceSnapshot

`CloudFieldSourceSnapshot` captures an immutable set of sources for one bridge
pass. It lets future code collect backend data first, then apply it to a
`CloudFieldStore` as a stable batch.

### CloudFieldFactory

`CloudFieldFactory` converts one source into one `CloudField`.

Rules:

```text
field id = deterministic UUID from source type + dimension + source id + seed
invalid or inactive sources return Optional.empty()
values are clamped before CloudField creation
cloudlet count can be hinted or derived from radius/density/coverage
no renderer code
no shader code
```

The identity rule means the same source id and same seed produce the same
CloudField identity across updates.

### CloudFieldUpdatePlan

`CloudFieldUpdatePlan` compares an existing `CloudField` to a new source-derived
field and records what changed:

```text
center
radius
vertical bounds
wind
density
coverage
growth
decay
vertical development
storm potential
cloudlet count
identity
create/remove state
```

CloudFields are immutable records, so an update returns a new record value, but
the stable field id is preserved when source id and seed are unchanged. The
store can keep runtime state for the same field id instead of treating every
tick as a brand-new field.

### CloudFieldBackendAdapter

`CloudFieldBackendAdapter` converts existing PA objects into sources:

```text
CloudRegionState -> PA_REGION source
CloudClusterState -> PA_CLUSTER source
CloudRegionRenderData -> PA_RENDER_DATA source
CloudBackendBridgeSnapshot -> BACKEND_BRIDGE_SNAPSHOT source
manual debug inputs -> MANUAL_DEBUG source
```

It does not read renderers, shaders, packets, or broad managers.

### CloudFieldBackendBridge

`CloudFieldBackendBridge` applies source snapshots to a `CloudFieldStore`.

It can:

```text
plan updates
create fields from new sources
replace immutable field values for existing stable ids
remove inactive/invalid sources
optionally remove fields missing from a source batch
```

It is now called by `CloudFieldRuntimeManager` when PA native cloud backend is
active.

### CloudFieldSyncPlan

`CloudFieldSyncPlan` documents sync ownership. The current implementation sends
`CloudFieldSnapshot` summaries through `SyncCloudFieldsPacket`.

Server should own:

```text
source identity
dimension
center
radius
baseY / topY
density / coverage
humidity influence
wind
growth / decay
vertical development
storm potential
seed
age/lifetime hints
```

Client can derive:

```text
stable CloudField UUID from source id + seed
cloudlet layout from field seed + CloudletId
LOD band from camera distance
hydration state/progress
active cloudlet count from LOD and hydration
```

Client can interpolate:

```text
center
radius
density
coverage
growth
decay
hydration progress
```

## Creation Path

PA-native creation now follows this path:

```text
CloudRegionStateStore.getActiveRegions(level)
-> CloudFieldBackendAdapter.fromRegions(...)
-> CloudFieldBackendBridge.applySnapshot(...)
-> CloudFieldStore.tickAll(...)
-> CloudFieldSnapshotFactory.createRendererInput(...)
-> SyncCloudFieldsPacket
```

`CloudFieldBackendSourceCollector` currently prefers active `CloudClusterState`
sources and falls back to whole-region sources only when a region has no active
clusters.

## Update Path

Existing fields should be updated rather than recreated:

```text
same source id + same seed
-> same CloudField UUID
-> CloudFieldUpdatePlan marks changed values
-> CloudFieldStore replaces immutable field value under same id
-> runtime hydration state can remain associated with that id
```

If the source disappears, becomes inactive, or becomes invalid, a removal plan
can remove it from the store. If the seed changes, identity changes by design,
and the old field can be removed when the bridge is applied with missing-source
removal enabled.

## Transitional Concepts

These remain transitional:

```text
CloudRegionState as final cloud model
CloudRegionRenderData as future sync format
SyncCloudRegionsPacket as final field packet
client region cache as final field cache
one-region / one-render-volume assumptions
```

They can feed `CloudFieldSource` for now without being destructively refactored.

## Intentionally Not Wired Yet

Not implemented:

```text
renderer integration
shader changes
cloudlet collision
merge/bridge/absorption/erosion relation states
GPU feedback maps
GPU readback
Simple Clouds renderer rewrite
broad manager refactors
```

Now implemented:

```text
server CloudField runtime manager
backend source collector
snapshot sync packet
client CloudField cache
future renderer input producer
/pa cloud fields runtime command
```

The next safe step is connecting a future CloudField renderer to
`ClientCloudFieldCache.createRendererInput(...)`.
