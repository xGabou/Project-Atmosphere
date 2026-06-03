# Classes To Avoid For Now

These classes should not be refactored in phase 0.

## `SimpleCloudsTornadoRenderer`

Why not now:

- It is already the most likely place to prove the render boundary.
- It mixes several concerns, but the future snapshot design is not stable yet.
- Refactoring it before the boundary exists risks moving confusion around instead of removing it.

## `SimpleCloudsHurricaneRenderer`

Why not now:

- Same reason as the tornado renderer.
- It is too close to the final render path to change without a stable snapshot contract.
- It should be used as a reference for integration points first.

## `SimpleCloudsCompat`

Why not now:

- It is already a key bridge to the external cloud system.
- Changing it before the render boundary is documented could break the only stable adapter path.
- It needs documentation more than immediate refactoring.

## `AtmosphereManager`

Why not now:

- It is broad, but it is still a central bootstrap coordinator.
- Refactoring it before the fake renderer boundary is built would add churn without clarifying the renderer contract.

## `ForecastOrchestrator`

Why not now:

- It is a source-of-truth hub for forecast state.
- The renderer does not need this refactored first.
- The more important step is to define what data can pass through a snapshot boundary.

