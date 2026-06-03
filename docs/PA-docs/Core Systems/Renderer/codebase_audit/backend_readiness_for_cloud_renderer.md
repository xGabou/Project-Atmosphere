# Backend Readiness For Cloud Renderer

## What Backend Values Are Solid

- Temperature forecasts and region-level temperature curves.
- Humidity state and humidity budgets.
- Pressure curves and pressure-based weather shaping.
- Wind vectors and wind forecast state.
- Storm chance and coarse weather phase.
- Tornado and hurricane lifecycle state.
- Region-atmosphere state and region lookup.
- Weather snapshot style transport objects.

## What Backend Values Are Unclear

- How to describe cloud optical thickness in a renderer-friendly way.
- How to express cloud vertical envelope without leaking simulation internals.
- How to represent shadow strength or lighting attenuation cleanly.
- How much of a storm’s appearance should be derived versus explicitly authored.

## What Backend Values Are Missing

- A dedicated cloud render snapshot object.
- A lighting and shadow hint contract for cloud visuals.
- A single source of truth for render LOD and visual density.
- A clear fallback darkening model for cloud rendering.
- A stable per-dimension cloud render state cache.

## What Backend Values Are Duplicated

- Cloud cover and rain intensity.
- Wind vectors and wind-derived storm motion.
- Weather phase and storm severity labels.
- Render-adjacent hurricane and tornado metadata.

## What Values Should Be Backend-Owned

- Forecasts.
- Region atmospheric state.
- Storm lifecycle.
- Wind field and weather phase.
- Cloud type choice and spawn eligibility.

## What Values Should Be Renderer-Owned

- Visual density.
- Geometry resolution and downsampling.
- Lighting and shadow application.
- Depth and pass configuration.
- Fallback darkening and visual blending.

## What Values Should Be Client-Derived

- Camera-relative LOD.
- Screen-space downsample decisions.
- Frustum culling.
- Frame interpolation for visuals.
- Debug visualization toggles.

## Readiness Verdict

The backend is **close enough to support a future cloud renderer**, but it is **not yet clean enough to be the direct source of renderer truth**. The missing piece is not more simulation complexity. The missing piece is a stable, documented render data contract.

