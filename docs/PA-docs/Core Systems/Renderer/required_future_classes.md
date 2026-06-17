# Required Future Classes

This architecture pass does not require source changes yet.
No rewritten class files were created because the request was to map and document the system first.

The classes below are the ones that should exist if the cloud renderer is implemented later.

## Recommended future classes

| Class | Suggested Package | Responsibility | Required Now |
| --- | --- | --- | --- |
| `CloudRenderSnapshot` | `net.Gabou.projectatmosphere.client.render` | Immutable per-frame cloud rendering input | Later |
| `CloudRenderSnapshotBuilder` | `net.Gabou.projectatmosphere.client.render` | Builds the snapshot from client caches and packet data | Later |
| `CloudLightingSnapshot` | `net.Gabou.projectatmosphere.client.render` | Sun / sky / fog / dimming inputs for cloud shading | Later |
| `CloudShadowSnapshot` | `net.Gabou.projectatmosphere.client.render` | Cloud shadow and occlusion data for shaders | Later |
| `CloudRenderController` | `net.Gabou.projectatmosphere.client.render` | Orchestrates render submission and chooses the active pipeline | Later |
| `CloudRenderProfiles` | `net.Gabou.projectatmosphere.modules.atmosphere` | Simulation-side render hints such as density bands and cloud strata | Later |
| `CloudRenderSyncPacket` | `net.Gabou.projectatmosphere.network` | Optional dedicated network payload for renderer-only data | Later |
| `CloudRenderStateCache` | `net.Gabou.projectatmosphere.client.render` | Client cache for the current immutable snapshot | Later |

## Why these are separated

- Simulation owns the source values.
- Client cache owns smoothing and short-term interpolation.
- Renderer owns final visuals and GPU state.

## Notes for later implementation

- Keep render snapshot generation on the client side whenever possible.
- Keep network payloads minimal if the values are already present in existing sync packets.
- Prefer derived fields over direct world queries in render code.
- Keep the snapshot immutable for the render pass.

