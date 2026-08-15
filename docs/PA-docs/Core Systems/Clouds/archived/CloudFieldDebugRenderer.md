# CloudField Debug Renderer

The CloudField debug renderer is a client-only visualization path for proving
that synced CloudFields exist on the client and that the future renderer input
path works.

It is not the final volumetric renderer.

Implemented flow:

```text
ClientCloudFieldCache
-> CloudFieldRendererInput
-> CloudFieldDebugRenderHook
-> CloudFieldDebugRenderer
-> world-space debug overlay
```

## Enablement

The overlay is disabled by default.

It renders when either condition is true:

```text
/pa system cloudFieldDebug on
```

or any existing cloud shader debug mode is active:

```text
/pa system cloudRenderDebug bounds
/pa system cloudRenderDebug primary_mass
...
```

Status:

```text
/pa system cloudFieldDebug status
```

Disable explicit CloudField debug:

```text
/pa system cloudFieldDebug off
```

If `cloudRenderDebug` is still active, the CloudField overlay can still show.

## Render Hook

The hook uses the same proven world-space debug stage as the existing cloud
wireframe and world-space test cube:

```text
RenderLevelStageEvent.Stage.AFTER_PARTICLES
poseStack.translate(-cameraPosition)
RenderType.lines
```

The renderer does not read backend simulation state. It consumes only:

```text
ClientCloudFieldCache.createRendererInput(cameraPosition, worldTime, partialTick)
```

## What It Draws

For each visible CloudField snapshot:

```text
base radius ring
top radius ring
vertical edge columns
center marker
base/top center line
previous-center to current-center line when movement exists
wind direction line when wind exists
sample cloudlet marker boxes
```

The overlay is intentionally simple and diagnostic.

## LOD Colors

Field boundary color indicates LOD:

```text
DYNAMIC        green
TRANSITION     yellow
FAR_PROCEDURAL blue
HAZE           gray
```

Hydration affects alpha. Low hydration is faint; high hydration is stronger.

## Cloudlet Markers

Cloudlet markers are generated client-side from the same deterministic layout
model as the future renderer:

```text
CloudletLayout.generate(snapshot, CloudletId.of(i))
```

The renderer samples:

```text
0 .. min(snapshot.dynamicCloudletCount, maxDebugMarkers)
```

Default cap:

```text
96 cloudlet markers per field
```

No cloudlets are synced over the network. No cloudlet entities are created.

Because layout derives from:

```text
field seed + cloudlet id
```

the marker positions remain stable across frames and sync updates.

## Hydration Behavior

The client cache owns local hydration state by field id. As the client camera
moves between LOD bands:

```text
HAZE/FAR_PROCEDURAL -> few or no cloudlet markers
TRANSITION          -> markers appear gradually
DYNAMIC             -> markers reach full active count
```

This verifies that CloudField hydration can be client-local and distance based.

## Not Implemented Here

This debug renderer intentionally does not implement:

```text
final volumetric rendering
shader changes
lighting
cloudlet collision
cloudlet merge
bridge density
absorption
erosion relation states
GPU feedback maps
GPU readback
weather rules
new cloud spawning logic
```

It exists only to prove the CloudField runtime and client input path visually.
