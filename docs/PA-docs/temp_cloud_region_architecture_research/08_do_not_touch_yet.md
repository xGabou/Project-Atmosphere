# Do Not Touch Yet

While researching or designing the future cloud-region layer, do not modify these files:

- `AtmosphereManager`
- `ForecastOrchestrator`
- `ForecastGenerator`
- `CloudManager`
- `SimpleCloudSpawner`
- `SimpleCloudsCompat`
- `SimpleCloudsTornadoRenderer`
- `SimpleCloudsHurricaneRenderer`
- `TornadoManager`
- `HurricaneManager`
- `ClientTickHandler`

## Also leave these renderer-adjacent files alone for now

- `CloudDebugRenderer`
- `CloudDebugRenderHook`
- `CloudRenderSnapshot`
- `CloudRenderStateCache`
- `CloudRenderStateHolder`
- `CloudDebugSnapshotFactory`
- `CloudDebugStateInitializer`
- `CloudRegionMixin`
- `CloudRegionTickEventMixin`
- `SimpleCloudsCloudManagerMixin`
- `CloudGeneratorHurricaneReservationMixin`
- `MultiRegionCloudMeshGeneratorMixin`

## Why

These files are either:

- broad orchestration points
- renderer or debug wiring
- SimpleClouds integration points
- mixin targets that must remain stable while the backend contract is being designed

Changing them now would blur the research result and make the backend contract harder to define cleanly.

