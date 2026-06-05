# Safe Implementation Order

This is the conservative order to introduce a backend cloud-region layer if the project decides to do so.

| Step | Goal | Files likely involved later | Files not to touch | How to verify | Stop condition |
|---|---|---|---|---|---|
| 1 | Keep the current debug renderer as visual validation | `CloudDebugRenderer`, `CloudDebugRenderHook`, `CloudDebugSnapshotFactory` | `ForecastOrchestrator`, `AtmosphereManager`, `CloudManager`, `SimpleCloudsCompat` | A debug box renders in a stable world position | If the debug box already proves the transform path, do not expand scope. |
| 2 | Document the backend contract before coding it | New docs and design notes only | All source files | The contract fields are agreed in writing | Stop once the contract is clear. |
| 3 | Add a minimal `CloudRegionState` class | Later new backend package | Renderer code, shader code, SimpleClouds code | A single region can be represented without rendering | Stop if the backend shape is not stable. |
| 4 | Add a minimal cloud-region manager for debug regions only | Later backend manager package | Weather generators, forecast orchestration, SimpleClouds bridge code | A debug region can be created, moved, and queried | Stop if behavior starts duplicating SimpleClouds too early. |
| 5 | Convert cloud-region state into `CloudRenderSnapshot` | Snapshot builder later | Renderer implementation details | The snapshot shows correct center/radius/base/top | Stop if the snapshot is just mirroring weather scalars. |
| 6 | Render one backend-owned debug region | Debug renderer only | Shader and production renderer paths | The box stays anchored in world space | Stop if transform behavior becomes ambiguous. |
| 7 | Connect PA weather values as inputs | Weather/simulation bridge later | SimpleClouds internals | A weather change can influence cloud-region state | Stop if the bridge starts owning too much lifecycle logic. |
| 8 | Add motion, growth, decay, and density only after the contract is stable | Backend simulation layer later | Renderer and weather code paths | The region moves and evolves over time without breaking snapshots | Stop if the layer starts becoming a full engine before the contract is validated. |

## Why this order is safe

- It validates the render contract before committing to a full simulation layer.
- It keeps the backend layer small until the renderer proves it needs more.
- It avoids coupling weather generation directly to render submission.
- It makes it easy to stop after a minimal proof of concept if the future layer is not needed.

