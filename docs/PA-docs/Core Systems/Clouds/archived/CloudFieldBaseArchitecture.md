# CloudField Base Architecture

This document describes the neutral base system for the next Project Atmosphere
cloud architecture. It does not replace the current renderer, weather managers,
network packets, or shaders yet.

Target flow:

```text
weather / atmosphere data
-> CloudField
-> stable Cloudlets generated from field seed + cloudlet id
-> LOD and hydration/dehydration
-> render-safe snapshots
-> future renderer
-> future merge/feedback system later
```

## CloudField

`CloudField` is the high-level cloud mass state. It represents a cloud cluster
or field summary, not a render AABB.

It owns:

```text
field id
seed
dimension id
center
radius
baseY / topY
density
coverage
growth
decay
humidity influence
wind vector
vertical development
storm potential
target cloudlet count
age ticks
lifetime ticks
```

The field can move with wind and age independently of any renderer. Renderers
must not read mutable field objects directly; they should consume
`CloudFieldSnapshot`.

## CloudletId

`CloudletId` is a stable procedural slot inside a field. It is intentionally
small: a non-negative integer plus a deterministic seed mixer.

The identity rule is:

```text
cloudlet layout = field seed + cloudlet id
```

This means cloudlet `143` remains cloudlet `143` when a field hydrates, moves,
or transitions between LOD bands.

## CloudletLayout

`CloudletLayout` is a stateless deterministic generator. Given a
`CloudFieldSnapshot` and `CloudletId`, it produces the cloudlet local offset,
scale, coverage weight, density weight, and coherent age.

The generated local layout does not depend on random runtime allocation. A field
that formed far away can later hydrate near the player and generate cloudlets
that look as if they already existed.

## LOD Bands

`CloudLodBand` describes renderer intent:

```text
DYNAMIC        identifiable cloudlets, full detail
TRANSITION     identifiable cloudlets with fading detail and procedural blend
FAR_PROCEDURAL no individual cloudlets, procedural field rendering later
HAZE           no individual cloudlets, hidden by haze/fog/sky blending later
```

Distance classification is owned by `CloudFieldDistanceClassifier`, not by the
enum. The default ranges are:

```text
0-500 blocks: DYNAMIC
500-1200 blocks: TRANSITION
1200-2000 blocks: FAR_PROCEDURAL
2000+ blocks: HAZE
```

The ranges are configurable through `CloudFieldDistanceClassifier.Ranges`.

## Runtime State

`CloudFieldRuntimeState` stores client/runtime-only values that do not belong in
the pure field definition:

```text
current LOD band
previous LOD band
hydration state
hydration progress
last update world time
current active cloudlet count
previous center
```

This lets the system change render readiness without mutating the weather-level
CloudField definition.

## Hydration

Hydration controls whether stable individual cloudlets are available.

States:

```text
NOT_HYDRATED
HYDRATING
HYDRATED
DEHYDRATING
```

Progress is a smooth `0.0` to `1.0` value. `CloudFieldHydrationController`
updates it gradually:

```text
DYNAMIC        moves toward 1.0
TRANSITION     moves toward 1.0 more slowly
FAR_PROCEDURAL moves toward 0.0
HAZE           moves toward 0.0 faster
```

This avoids cloudlets popping into existence when a distant field enters the
simulated zone.

## Field Lifecycle

`CloudFieldLifecycleController` owns the neutral update flow:

```text
advance age
apply simple wind movement
classify LOD from camera distance
update hydration
return updated field + runtime state
```

`CloudFieldStore` owns active fields and runtime states. It can add, remove,
look up, tick, and expire fields. It does not call weather managers or renderers.

## Snapshots

`CloudFieldSnapshotFactory` converts:

```text
CloudField + CloudFieldRuntimeState + CloudFieldTickContext
```

into immutable render-safe `CloudFieldSnapshot` objects.

Snapshots include:

```text
field id
seed
center and previous center
radius
baseY / topY
density / coverage / growth / decay
humidity influence
wind
vertical development
storm potential
current and previous LOD bands
hydration state and progress
target and active cloudlet counts
field age
world time
partial tick
camera position
```

`CloudFieldRendererInput` is the future renderer boundary. It contains snapshots
only, never mutable simulation objects.

## Runtime Integration Status

The first backend-to-client runtime path now exists in code:

```text
CloudFieldBackendSourceCollector
-> CloudFieldBackendBridge
-> CloudFieldRuntimeManager
-> SyncCloudFieldsPacket
-> ClientCloudFieldCache
-> CloudFieldRendererInput
```

This path is still renderer-neutral. The current cloud renderer is not consuming
`CloudFieldRendererInput` yet.

## Far Fields Entering The Simulated Zone

A far field can exist as summary data only:

```text
CloudField exists
runtime state is NOT_HYDRATED
active cloudlet count is 0
LOD is FAR_PROCEDURAL or HAZE
```

When the field enters the transition or dynamic zone, hydration increases
gradually. Cloudlets are generated deterministically from `field seed +
cloudlet id`, with coherent age derived from the field age, so they appear as
stable existing parts of the field instead of random new objects.

## Fields Leaving The Simulated Zone

When a field leaves the dynamic zone:

```text
DYNAMIC -> TRANSITION keeps partial/visible hydration
TRANSITION -> FAR_PROCEDURAL dehydrates toward 0
FAR_PROCEDURAL -> HAZE dehydrates faster or remains empty
```

The field remains as summary data after dehydration, allowing a future renderer
to draw it procedurally at distance.

## Intentionally Not Implemented Yet

This base pass intentionally does not implement:

```text
cloudlet collision
merge
bridge density
absorption
erosion relation states
GPU feedback maps
GPU readback
real weather integration
real renderer integration
network packets
shader changes
broad manager refactors
```

Those systems should build on this snapshot contract later.
