# CloudField Architecture Audit

This audit supersedes older notes that treat `CloudRegion` as the final render model.

Target flow:

```text
weather data -> CloudField -> stable GPU-generated cloudlets -> optional relation states later -> GPU visual response
```

No files are removed in this pass. The goal is to classify existing concepts before introducing the base field/cloudlet contract.

## KEEP

| Concept / file area | Reason |
| --- | --- |
| `AtmosphereCloudPolicy` | Still owns dimension/backend policy decisions. |
| `WeatherCloudQueries`, `CloudWeatherSample` | Useful weather/atmosphere sampling inputs for future CloudField creation. |
| `clouds/type/*Profile`, `CloudMorphologyFamily`, `CloudFamily` | Still useful as visual/material parameter sources, but should feed fields rather than hard object boxes. |
| `clouds/visual/*` | Good read-only visual metadata layer for future field snapshots and renderer decisions. |
| `CloudRenderFrameContext`, render targets, GPU timer, shader registration helpers | Render infrastructure remains useful independent of the final cloud source model. |
| `CloudShadowSnapshot`, `CloudShadowMapAccess` | Public shadow access remains a useful boundary. |

## CONVERT

| Concept / file area | Conversion target |
| --- | --- |
| `CloudRegionState`, `CloudClusterState` | Treat as transitional weather/cloud-mass state. Convert future output to `CloudField`, not renderer AABBs. |
| `CloudRegionManager`, spawners, lifecycle, motion, evolution controllers | Eventually produce/update CloudFields from weather data. Do not refactor yet. |
| `CloudRegionRenderData`, `SyncCloudRegionsPacket`, client region cache | Transitional transport. Future transport should carry `CloudFieldSnapshot`-style render-safe data. |
| `CloudRenderSnapshot`, `CloudRenderSnapshotBuilder`, render state caches | Legacy per-volume renderer input. Future renderer consumes `CloudFieldRendererInput`. |
| `CloudRenderLodTier`, `CloudRenderLodManager`, LOD snapshot factory | Convert from per-snapshot render budgeting to field LOD bands: dynamic, transition, far procedural, haze. |
| `CloudTypeDefinition`, `CloudTypeRegistry` | Convert rigid type role into broad family/visual-weight source for fields. |

## DEBUG_ONLY

| Concept / file area | Reason |
| --- | --- |
| `CloudRenderAabb` | AABB is useful for proving spatial setup, not final visible cloud architecture. |
| `CloudWireframeRenderer`, `CloudDebugRenderHook`, debug snapshots | Keep for diagnostics only. |
| Current one-snapshot/one-AABB `cloud_volume` path | Useful while validating morphology and ray setup, but not the final CloudField renderer. |
| `cloudRenderDebug` scalar/bounds modes | Keep as diagnostic tooling. |

## REMOVE

These are conceptual removals, not immediate file deletes:

| Concept | Reason |
| --- | --- |
| `CloudRegion` as final render model | Regions should describe atmosphere or transitional state, not final visible cloud containers. |
| One cloud object = one AABB = one fullscreen shader pass | Does not scale to realistic fields, merging, or far procedural rendering. |
| Immediate per-cloudlet CPU collision simulation everywhere | Too expensive and unnecessary outside the local dynamic zone. |

## IGNORE_FOR_NOW

| Concept / file area | Reason |
| --- | --- |
| Cloudlet collision/merge/bridge relation states | Future work after stable identities and field snapshots exist. |
| GPU feedback maps/readback | Future work; do not add until the base renderer contract is stable. |
| Broad manager refactors | Too risky before the CloudField input contract is proven. |
| Replacing current weather integration | Out of scope for the first implementation. |
| Replacing current shader/render path | Out of scope for this base architecture pass. |

## First Implementation Added

The base model should be introduced without wiring it into weather or rendering:

```text
CloudField
CloudletId
CloudletLayout
CloudFieldSnapshot
CloudLodBand
CloudFieldRendererInput
CloudFieldStore
CloudFieldLifecycleController
CloudFieldDistanceClassifier
CloudFieldHydrationState
CloudFieldHydrationController
CloudFieldSnapshotFactory
CloudFieldRuntimeState
CloudFieldTickContext
CloudFieldValidation
CloudFieldSource
CloudFieldSourceType
CloudFieldSourceSnapshot
CloudFieldFactory
CloudFieldUpdatePlan
CloudFieldBackendAdapter
CloudFieldBackendBridge
CloudFieldSyncPlan
CloudFieldBackendSourceCollector
CloudFieldRuntimeManager
CloudFieldPacketDispatcher
SyncCloudFieldsPacket
CloudFieldSyncManager
ClientCloudFieldCache
```

Rules:

```text
Renderer consumes snapshots only.
Cloudlet identity is stable: field seed + cloudletId.
Cloudlets can be generated near the player without CPU-heavy simulation.
Far fields remain summary/procedural until they enter the dynamic/transition bands.
Hydration/dehydration is gradual so cloudlets do not randomly pop into existence.
Existing PA region/cluster/transport data can be adapted into CloudFieldSource without wiring into renderers.
CloudField identity is stable for the same source id + seed.
PA native backend data now feeds a server CloudFieldStore and syncs CloudFieldSnapshot data to clients.
```

See `CloudFieldBaseArchitecture.md` for the base runtime layer contract.
See `CloudFieldBackendIntegrationPlan.md` for the backend bridge preparation layer.
See `CloudFieldRuntimeIntegration.md` for the live backend-to-client runtime path.
