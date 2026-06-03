# Minimal Fake Renderer Requirements

## Minimal Data Needed

- A fixed world position for the fake cloud.
- A fixed size or radius.
- A fake vertical extent.
- A simple opacity or density value.
- A debug mode flag.

## Minimal Classes Needed

- A future temporary cloud snapshot object.
- A future client cache object for the fake cloud state.
- A future render hook that draws the debug cloud.
- A tiny debug configuration holder.

## Minimal Client Side Cache Needed

- Store the current fake cloud snapshot.
- Optionally interpolate position very lightly if the camera moves.
- Keep the data immutable once handed to the renderer.
- Do not depend on live simulation state.

## Minimal Render Hook Needed

- One render hook that can draw the fake cloud at a known position.
- One place to choose the current render pass.
- One place to apply simple depth-safe drawing behavior.
- One place to toggle debug visibility.

## What Should Be Hardcoded

- The initial fake cloud location.
- The size and height of the fake cloud.
- The density scale.
- The color or basic tint.
- The debug mode identifiers.

## What Should Be Avoided

- Real PA weather integration.
- Forecast generation.
- Storm lifecycle logic.
- Simple Clouds spawn policy.
- Complex client smoothing.
- Any renderer code that directly queries `ForecastOrchestrator` or `CloudManager`.

## What Files Should Not Be Touched

- `src/main/java/net/Gabou/projectatmosphere/manager/AtmosphereManager.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/ForecastOrchestrator.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CloudManager.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/SimpleCloudSpawner.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/SimpleCloudsCompat.java`
- `src/main/java/net/Gabou/projectatmosphere/client/ClientTickHandler.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/SimpleCloudsTornadoRenderer.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/SimpleCloudsHurricaneRenderer.java`

## Exit Criteria

- A fake debug cloud can render at a fixed position.
- The cloud is driven by a tiny snapshot/cache boundary.
- The fake cloud does not require real weather data.
- The fake cloud can be toggled without disturbing the simulation layer.

