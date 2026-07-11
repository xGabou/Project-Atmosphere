# Cloud Rendering Remediation Log

This log records rendering/compatibility iterations performed during the 2026-07-10
Project Atmosphere cloud remediation. A successful compile is not treated as visual
validation; items explicitly marked "in-game pending" still require a client run.

## Iteration 1 - backend ownership and optional integrations

- Attempt: centralize vanilla, Project Atmosphere and Simple Clouds ownership and
  cancel a foreign pass only when an active Project Atmosphere replacement owns it.
- Result: `compileJava` passed. A dedicated server without Simple Clouds or Serene
  Seasons reached the Forge `Done` state. Client/in-game matrix remains pending.

## Iteration 2 - framebuffer, depth and low-resolution composition

- Attempt: capture and restore the incoming Forge/OpenGL state, resolve/copy the
  active scene depth before Project Atmosphere changes targets, and perform a
  depth-guided low-resolution composite.
- Result: `compileJava`, Gradle tests and resource processing passed. No attached
  depth texture is sampled by the native ground-shadow or hurricane opaque pass.
  Fast/Fancy/Fabulous and external-pipeline visual validation remains pending.

## Iteration 3 - canonical camera density and lifecycle cleanup

- Attempt: publish CPU density from the representation that actually completed the
  composite, and release targets, queries, histories and caches on all client world,
  backend, resize, reload and shutdown transitions.
- Result: static call-path review and compilation passed. Resource reload and world
  transition behavior remains in-game pending.

## Iteration 4 - native tornado, datapack evolution, shadows and wind

- Attempt: provide a native tornado activation/lifecycle/effects path without Simple
  Clouds, parse datapack evolution rules atomically, re-enable shadows with detached
  depth, and replace the hard-coded visual wind with synchronized field/cell/forecast
  wind.
- First result: compilation found an explicit `Double` to `float` conversion error in
  the forecast wind fallback.
- Follow-up: use `Double.floatValue()` at the configuration boundary.
- Final result: `compileJava`, Gradle tests and resource processing passed. Tornado
  physics, shadow appearance and wind coherence remain in-game pending.

## Iteration 5 - strict cloudlet budget and LOD

- Attempt: honor zero-cloudlet LOD bands and distribute one strict quality budget with
  a deterministic weighted fair queue and stable cloudlet identifiers.
- Result: compilation and Gradle tests passed. Debug counters now report requested,
  accepted, rejected, remaining budget and visible fields. Popping behavior remains
  in-game pending.

## Iteration 6 - canonical field/cell contract and morphology transport

- Attempt: reconcile normal `CloudCell` objects from the current `CloudField`
  representation instead of autonomously spawning a hidden parallel population.
  Preserve explicit command cells and funnel state, and render funnels while fields
  own the base cloud mass.
- Attempt: carry type, morphology, anvil and precipitation data through fields,
  snapshots and packet version 3; splat an auxiliary morphology map and consume it in
  type-specific raymarch density profiles.
- Result: `compileJava` passed. Packet interoperability with the new version is
  backward-readable, but multiplayer and visual profile validation are in-game
  pending.

## Iteration 7 - weather map cache

- Planned attempt: reuse the primary and morphology maps while camera-snapped origin
  and quantized cloud/weather inputs remain unchanged. Regional animated layers must
  still invalidate periodically, and target recreation must always invalidate.
- Expected validation: compilation, resource processing, cache diagnostics, then
  in-game comparison for motion stepping or stale weather.

## Iteration 8 - temporal history validation

- Planned attempt: compare reprojected cloud depth and transmittance against the
  current raymarch, reduce CPU history confidence during rapid camera motion and
  while the camera is inside cloud, and reject invalid history on target/world/backend
  transitions through the existing lifecycle hooks.
- Expected validation: shader registration/resource checks and an in-game camera-cut,
  silhouette reveal, cloud-entry and resolution-change matrix.

## Iteration 9 - noise-domain repetition

- Planned attempt: rotate the world-stable 3D sampling domains and apply a slow,
  incommensurate analytic domain warp before the existing multi-frequency texture
  lookup. This keeps the existing texture sizes and sample count while breaking the
  obvious axis-aligned repeat period.
- Expected validation: shader resource checks, then long-distance flyover screenshots
  with history disabled/enabled to distinguish spatial repetition from temporal noise.

## Iteration 10 - runtime shader registration

- Attempt: launch the real Forge client without optional weather/cloud integrations so
  every Project Atmosphere core shader is compiled by the Minecraft 1.20.1 shader
  loader on the active OpenGL driver.
- First result: Java compilation and resource processing had passed, but the client
  rejected `cloud_weather_morphology.fsh` because `packed` is a reserved GLSL word.
- Follow-up: rename that local value without changing the encoded morphology data,
  then relaunch the complete client shader-registration sequence.
- Final result: the second client launch reached the menu and logged
  `[VolumetricClouds] shader programs registered`; no Project Atmosphere shader
  compilation or mixin-application exception remained. The mixin config's explicit
  minimum version was also aligned with Forge 47.4.20's bundled Mixin 0.8.5.
- Dedicated-server follow-up: a Forge server with neither Simple Clouds nor Serene
  Seasons reached `Done (9.952s)`, selected the native cloud service and initialized
  its simulation. No client renderer class-resolution or optional-dependency failure
  occurred.

## Iteration 11 - fallback morphology parity

- Planned attempt: carry the same canonical cloud profile, morphology family, anvil,
  precipitation and lifecycle metadata into the bounded CloudField fallback shader.
  The fallback must preserve structurally different sheet, cellular, tower, anvil and
  filament profiles instead of silently reverting every field to one cumulus shape.
- Expected validation: full build followed by real client shader registration.
- First runtime result: the strict GLSL compiler found one stale local identifier
  (`storm`) left in the refactored horizontal contour. Replace it with the canonical
  `FieldStormPotential` uniform and repeat the full shader registration.
- Final result: after the correction and rebuild, the Forge client reached the menu
  and registered all Project Atmosphere volumetric and fallback shaders. No shader
  compilation, Project Atmosphere mixin-application or client bootstrap exception was
  present in the final run.

## Iteration 12 - Simple Clouds 0.7.3 runtime contract

- Attempt: launch the client with the exact Simple Clouds 0.7.3 and CrackersLib
  runtime dependencies enabled, exercising every guarded PA mixin and replacement
  compute shader against the real classes.
- First result: all PA mixins applied and both mods reached the menu; Simple Clouds
  finished renderer initialization. Its uniform probe reported `TotalLodLevels` as
  inactive because the inherited 0.7.3 shader declared but never consumed it. Use it
  to clamp the LOD texture slice. The conditional `FadeStart`/`FadeEnd` warnings match
  the upstream 0.7.3 preprocessor contract for variants that compile those uniforms
  out and are not treated as a PA shader failure.
