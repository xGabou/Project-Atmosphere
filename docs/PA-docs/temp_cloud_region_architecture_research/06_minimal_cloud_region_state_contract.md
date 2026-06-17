# Minimal Cloud Region State Contract

This is the smallest backend cloud-region contract that would make a first PA-driven cloud test meaningful.

It is intentionally minimal.

| Field | Type suggestion | Meaning | Source | Required for first backend test | Required for real renderer | Notes |
|---|---|---|---|---|---|---|
| `regionId` | `UUID` or `long` | Stable identity for one cloud region | Spawn or simulation layer | Yes | Yes | Needed for tracking across ticks and snapshots. |
| `dimension` | `ResourceLocation` or `ResourceKey<Level>` | Which world the cloud belongs to | World context | Yes | Yes | Required so clouds do not drift between dimensions. |
| `center` | `Vec3` or `BlockPos` | Current world-space center | Simulation | Yes | Yes | Core placement data. |
| `previousCenter` | `Vec3` | Previous world-space center | Simulation tick | No | Yes | Useful for motion interpolation and drift effects. |
| `velocity` | `Vec3` | Current movement vector | Wind + simulation | No | Yes | Needed for motion and camera-independent interpolation. |
| `radius` | `float` | Horizontal footprint size | Spawn policy or simulation | Yes | Yes | Minimum useful geometric size. |
| `baseY` | `float` | Lower visible bound | Spawn policy or simulation | Yes | Yes | Needed for volume placement. |
| `topY` | `float` | Upper visible bound | Spawn policy or simulation | Yes | Yes | Needed for cloud thickness. |
| `density` | `float` | Thickness / opacity proxy | Weather + object profile | No | Yes | Optional for first backend test, useful for real rendering. |
| `coverage` | `float` | Area coverage intensity | Weather + object profile | No | Yes | Helps distinguish sparse vs dense clouds. |
| `edgeSoftness` | `float` | Falloff at the perimeter | Object profile | No | Yes | Visual shaping value. |
| `ageTicks` | `int` | How long the region has existed | Simulation | No | Yes | Needed for lifecycle and transitions. |
| `lifetimeTicks` | `int` | Intended lifespan | Spawn policy or simulation | No | Yes | Needed for decay and despawn timing. |
| `growth` | `float` | Growth rate | Simulation | No | Yes | Can be derived from weather or profile. |
| `decay` | `float` | Decay rate | Simulation | No | Yes | Can be derived from weather or profile. |
| `active` | `boolean` | Whether the region is alive and renderable | Simulation | Yes | Yes | Minimal gating flag. |
| `weatherSourceKey` | `RegionInstanceKey` or `BiomeInstanceKey` | Weather region that produced this cloud | Weather layer | Yes | Yes | Connects cloud state back to PA weather. |

## Minimal first-backend-test subset

For a first backend validation pass, the smallest useful subset is:

- `regionId`
- `dimension`
- `center`
- `radius`
- `baseY`
- `topY`
- `active`
- `weatherSourceKey`

That is enough to prove the backend owns a cloud region position and can hand a render snapshot to the renderer.

## Real renderer subset

A real renderer will also want:

- `previousCenter`
- `velocity`
- `density`
- `coverage`
- `edgeSoftness`
- `ageTicks`
- `lifetimeTicks`
- `growth`
- `decay`

Those fields are what turn a debug box into an actual simulation-backed cloud object.

