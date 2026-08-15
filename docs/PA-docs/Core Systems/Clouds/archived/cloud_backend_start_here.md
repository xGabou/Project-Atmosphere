# Cloud Backend Start Here

If you are trying to build the backend for clouds before touching the renderer, this is the first document to read.

## Short Answer

Project Atmosphere already owns weather values:

- temperature
- humidity
- pressure
- wind
- cloud cover
- rain intensity
- storm chance / storm severity
- forecast and region atmosphere state

It does not yet own a dedicated cloud-region simulation layer that describes an individual cloud as a world object with:

- identity
- position
- movement
- size
- base/top height
- density
- coverage
- age
- lifetime
- growth / decay

That means the backend path should be:

`PA weather values -> CloudRegionState -> CloudRenderSnapshot -> renderer`

not:

`PA weather values -> renderer directly`

## What To Read First

Read these in order:

1. `docs/PA-docs/Core Systems/Renderer/codebase_audit/backend_readiness_for_cloud_renderer.md`
2. `docs/PA-docs/Core Systems/Renderer/phase0_cloud_boundary/phase0_verdict.md`
3. `docs/PA-docs/Core Systems/Renderer/phase0_cloud_boundary/phase0_contract.md`
4. `docs/PA-docs/Core Systems/Renderer/cloud_render_data_contract.md`
5. `docs/PA-docs/Core Systems/Renderer/cloud_renderer_integration_points.md`
6. `docs/PA-docs/temp_cloud_region_architecture_research/01_existing_cloud_data_inventory.md`
7. `docs/PA-docs/temp_cloud_region_architecture_research/02_weather_values_vs_cloud_objects.md`
8. `docs/PA-docs/temp_cloud_region_architecture_research/03_existing_cloud_lifecycle_ownership.md`
9. `docs/PA-docs/temp_cloud_region_architecture_research/04_renderer_input_gap_analysis.md`
10. `docs/PA-docs/temp_cloud_region_architecture_research/05_should_add_cloud_region_layer.md`
11. `docs/PA-docs/temp_cloud_region_architecture_research/06_minimal_cloud_region_state_contract.md`
12. `docs/PA-docs/temp_cloud_region_architecture_research/07_safe_implementation_order.md`

## Current Ownership Summary

### PA already owns

- weather state and forecast state
- storm severity and storm motion helpers
- atmospheric region state
- wind state
- cloud selection policy
- cloud spawn policy
- client-side weather caches and sync data

### SimpleClouds still owns

- the actual `CloudRegion` object
- cloud region lifecycle
- cloud movement and persistence
- cloud spawning config and type catalog

### The renderer should own

- draw order
- camera transforms
- buffers and shader submission
- visual density and lighting application
- debug or production drawing only

## What The Missing Layer Should Do

A `CloudRegionState` layer would sit between weather and rendering.

It should own:

- region identity
- center and previous center
- velocity
- radius
- base and top height
- density
- coverage
- edge softness
- age and lifetime
- growth and decay
- source weather key

It should not own:

- packet transport
- shader logic
- render pass execution
- SimpleClouds lifecycle internals

## What Not To Use As The Backend Contract

Do not use weather values alone as the cloud contract.

Weather values can say:

- the atmosphere is favorable
- the storm is strengthening
- the wind is pushing clouds

But they cannot say:

- which cloud exists
- where it is
- how large it is
- how old it is
- how fast it is moving

That information belongs in the cloud-region layer.

## Safe Next Step

The smallest safe backend step is:

1. Define `CloudRegionState` on paper first.
2. Decide the minimal fields.
3. Build a snapshot path from that state.
4. Keep the renderer consuming snapshots only.

## Do Not Touch Yet

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

## Where To Start If You Only Read One File

Read this file first, then read `phase0_contract.md`.

