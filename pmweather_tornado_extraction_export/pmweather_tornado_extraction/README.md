# PMWeather Tornado Extraction

This folder contains the PMWeather tornado code path that matters most if you want to port the behavior into another mod.

Important: PMWeather does not render the tornado as a normal model or mesh.

- The close-range visual motion comes from particles and debris being advected by the tornado wind field.
- The large visible funnel and wall cloud are generated in the volumetric cloud shader.
- The tornado backend is mainly `Storm.java` plus `WindEngine.java`.

## Core Backend

- `dev/protomanly/pmweather/weather/Storm.java`
  - Storm lifecycle, widening, intensification, dying, chunk force-loading, pulling entities/particles, block damage, debris spawning.
  - Key methods:
    - `tick()`
    - `doDamage(...)`
    - `getRankine(...)`
    - `getWind(...)`
    - `pull(Particle, ...)`
    - `pull(Entity, ...)`
- `dev/protomanly/pmweather/weather/Vorticy.java`
  - Secondary subvortices orbiting the tornado.
- `dev/protomanly/pmweather/weather/WindEngine.java`
  - Blends ambient wind, storm inflow/rotation, and the tornado-specific wind field.
  - This is what drives particles and debris into a funnel-looking motion.
- `dev/protomanly/pmweather/weather/WeatherHandler.java`
- `dev/protomanly/pmweather/weather/WeatherHandlerClient.java`
  - Storm management and client sync for debris particles.

## Visual Funnel

- `dev/protomanly/pmweather/shaders/ModShaders.java`
  - Sends storm uniforms to the volumetric shader:
    - position
    - width
    - windspeed
    - touchdown speed
    - random tornado shape
    - spin
    - occlusion
- `assets/pmweather/shaders/program/clouds.fsh`
  - This is the actual funnel shape logic.
  - Search these sections:
    - `tornadic`
    - `torPerc`
    - `tornadoHeight`
    - `torShape`
    - `ropeMod`
    - `dust`
- `assets/pmweather/shaders/program/clouds.json`
- `assets/pmweather/shaders/post/clouds.json`
- `dev/protomanly/pmweather/render/RenderEvents.java`
  - Hooks the shader render pass into the level render.

## Particle And Debris Layer

- `dev/protomanly/pmweather/event/GameBusClientEvents.java`
  - Ticks custom particle managers and applies `WindEngine.getWind(...)` to particles every client tick.
- `dev/protomanly/pmweather/particle/ParticleManager.java`
  - Custom particle render manager.
- `dev/protomanly/pmweather/particle/EntityRotFX.java`
  - Base particle implementation with sorted translucent and block render types.
- `dev/protomanly/pmweather/particle/ParticleCube.java`
  - Renders spinning cube debris using block textures.
- `dev/protomanly/pmweather/particle/ParticleTexFX.java`
- `dev/protomanly/pmweather/particle/ParticleTexExtraRender.java`
- `dev/protomanly/pmweather/particle/ParticleRegistry.java`
- `dev/protomanly/pmweather/particle/behavior/ParticleBehavior.java`
- `dev/protomanly/pmweather/entity/MovingBlock.java`
- `dev/protomanly/pmweather/entity/client/MovingBlockRenderer.java`

## Assets Included

- `assets/pmweather/shaders/...`
- `assets/pmweather/textures/particle/...`
- `assets/minecraft/textures/effect/pmweather/...`
- `assets/minecraft/atlases/particles.json`

## What To Port First

1. Port `Storm.getRankine(...)`, `Storm.getWind(...)`, and the two `pull(...)` methods.
2. Port the `Vorticy` system if you want the small satellite swirls.
3. Port the `WindEngine.getWind(...)` blending, because PMWeather uses that everywhere for visual motion.
4. Port `ModShaders.java` plus `clouds.fsh` if you want the same volumetric funnel.
5. Port `ParticleCube` plus the client particle tick path if you want the same debris behavior.

## Expect Missing Dependencies

These extracted files are the tornado stack, not a standalone compile-ready module.

You will need to adapt references to PMWeather-specific infrastructure such as:

- config classes
- utility helpers
- networking sync
- sound registration
- NeoForge event wiring
- PMWeather random/logger helpers

If you want, I can do a second pass and turn this into a cleaner drop-in package for your own mod namespace.
