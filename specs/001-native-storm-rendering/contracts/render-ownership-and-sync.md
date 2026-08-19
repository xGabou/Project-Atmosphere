# Contract: Render Ownership and Synchronization

**Feature**: `001-native-storm-rendering`  
**Status**: Design contract; no public network API change

## Ownership Contract

| Concern | Owner | Allowed consumers | Prohibited behavior |
|---|---|---|---|
| Persistent weather and storm lifecycle | Logical server | Existing simulation, forecast, field derivation, gameplay | Client render code mutating or replacing weather truth |
| Cloud field synchronization | Existing field sync manager/packets | Existing client cache | Storm renderer adding a parallel packet stream |
| Interpolated presentation snapshot | `ClientCloudFieldCache` | Native render input and diagnostics | Worker retaining mutable game/cache objects |
| Backend selection | `CloudBackendResolver` and `ClientCloudRenderOwnership` | Render hooks and compatibility diagnostics | Native renderer bypassing selected owner |
| Simple Clouds managed clouds | Simple Clouds when selected | Existing optional PA compatibility adapters | Project Atmosphere allocating/updating managed SC systems |
| CPU storm index build | Client worker from copied primitives | Render-thread adoption | Minecraft, level, entity, shader, target, RenderSystem, or GL access off-thread |
| GPU resources and rendering | Minecraft render thread | Native volumetric pipeline | Upload, allocation, binding, uniform updates, or target access on worker |
| Visual density/whiteout | Client from adopted render snapshot | Camera density tracker/effects | Using newer/unadopted worker output or becoming gameplay truth |

## Frame Input Contract

The native storm path may run only when all conditions are true:

1. a client level exists;
2. `ClientCloudRenderOwnership` selects the native Project Atmosphere renderer for that level;
3. native volumetric rendering is enabled by existing configuration/runtime switches;
4. the current resource generation has valid broad weather/morphology resources;
5. an existing `ClientCloudFieldCache` input is available, or broad fallback behavior applies.

Simple Clouds ownership short-circuits before storm descriptor selection, async requests, target preparation, or rendering.

## Server-to-Client Data Flow

```text
server state
  -> existing CloudField derivation
  -> existing full/delta field packets
  -> existing client field cache
  -> existing interpolation/extrapolation
  -> native storm descriptor derivation
```

There is no client-to-server geometry or quality message. Geometry generation is deterministic presentation derived from the received snapshot; it does not affect forecast, damage, precipitation gameplay, or saved data.

## Async Publication Contract

### Submission

- Create a `StormGeometryBuildInput` on the render thread by copying only finite, validated primitives and stable IDs.
- Permit at most one in-flight build and one coalesced latest pending signature.
- Submit through a non-blocking client-executor operation. If it cannot be accepted immediately, keep the last valid render generation and record saturation; never execute the expensive build inline on the render thread.

### Worker

- Read only the immutable input.
- Perform selection, conservative bounds, tile assignment, deterministic overflow, packing, and primitive diagnostics.
- Do not query config, cache, clock state used for rendering decisions, Minecraft singleton, world, entities, shaders, targets, or GL.
- Publish either one complete generation or a bounded failure record. Never publish partially filled shared arrays.

### Adoption

The render thread adopts a result only if all tokens still match:

- world session and dimension;
- selected backend/owner;
- resource/target generation;
- snapped map origin and extent;
- requested geometry signature and generation;
- effective detail-distance policy.

A mismatch is a stale discard. The renderer continues with the last matching generation or broad map LOD. Upload and all Minecraft/GL operations occur after validation on the render thread.

## Cache and History Contract

- Interpolated center/radius/density changes update descriptors without forcing topology invalidation.
- Candidate-grid rebuild is driven by quantized conservative bounds, membership/order, origin/extent, detail distance, or target changes.
- Temporal history invalidates for owner/world/dimension/resource changes, target resolution changes, camera cuts, and adopted topology-generation changes.
- Ordinary motion/advection and descriptor interpolation use existing reprojection and do not invalidate every frame.
- Published CPU visual-density state must identify the same adopted generation that the successfully composited GPU frame used.

## Fallback Contract

| Failure | Required result |
|---|---|
| Missing/invalid membership | Render severe material through broad map LOD; report missing membership |
| Descriptor/group capacity | Admit complete groups only; omitted groups use map LOD |
| Tile overflow | Apply deterministic role-preserving selection; conservative map remains available |
| Async saturation/stale result | Keep last matching generation or map LOD; do not block |
| Candidate/descriptor texture failure | Disable direct storm subpath for resource/session generation; keep broad native clouds |
| Wider native shader/pipeline failure | Use existing session-disable and legacy-field/vanilla fallback policy |
| Simple Clouds selected | Do not run native storm resources; Simple Clouds remains owner |
| Optional SC adapter failure | Report adapter failure without taking ownership from Simple Clouds |

## Compatibility Assertions

- Packet and saved-data formats are byte-for-byte unchanged by this feature.
- Forecast orchestration and networking cadence are unchanged.
- Default native development launch selects native behavior according to existing policy.
- Optional Simple Clouds launch selects Simple Clouds according to existing policy and shows zero active native storm builds/uploads.
- Dedicated-server startup must not load any new client renderer, shader, or LWJGL class.
- The developer-only legacy CloudField rollback path remains available.
