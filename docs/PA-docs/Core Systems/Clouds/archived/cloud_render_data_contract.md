# Cloud Render Data Contract

This document defines the data a future Project Atmosphere cloud renderer should consume.
It separates authoritative simulation data from render-only derived data.

## Contract Goal

The renderer should receive a single immutable per-frame snapshot and should not read world simulation classes directly during rendering.

## Data Already Available In Project Atmosphere

| Data | Current Owner | Current Type / Source | Render Use |
| --- | --- | --- | --- |
| Cloud cover | `RegionAtmosphereState`, `AtmosphereClientState`, `WeatherSnapshot` | `float` | Base opacity / sky occlusion / fallback darkening |
| Rain intensity | `RegionAtmosphereState`, `AtmosphereClientState`, `WeatherSnapshot` | `float` | Wetness, darkening, rain-cloud blending |
| Temperature | `RegionAtmosphereState`, `WeatherSnapshot`, forecast curves | `float` | Cloud phase, snow/rain decisions, density heuristics |
| Humidity | `RegionAtmosphereState`, `AtmosphereClientState`, `WeatherSnapshot` | `float` or percent | Cloud growth, saturation, fog strength |
| Pressure | `RegionAtmosphereState`, forecast curves | `float` | Storm intensity / cloud formation support |
| Wind speed and angle | `WindVector`, `RegionAtmosphereState`, `WeatherSnapshot`, `WindVectorApi` | `float` speed + `float` angle | Cloud motion, shear, advection, shape distortion |
| Storm chance | `ForecastOrchestrator`, `ServerWeatherStateResolver`, `WindEngine` | `float` | Cloud classification / storm escalation |
| Tornado snapshot | `TornadoSnapshot`, `TornadoManager` | position, radius, height, phase, intensity | Tornado visual state |
| Hurricane snapshot | `HurricaneRenderSnapshot`, `HurricaneRenderDescriptor`, `ClientHurricaneStateCache` | position, anchor, radii, intensity, bands | Hurricane visual state |
| Fog / whiteout | `AtmosphereFogState`, `SimpleCloudsWhiteoutFogHandler` | smoothed fog profile | Lighting dimming and fog blending |
| Sky effect flags | `SkyEffectState` | per-frame booleans / positions | Rare sky overlays |
| Cloud type identity | `CloudLibrary`, `WeatherType`, Simple Clouds cloud types | `ResourceLocation` / cloud id | Type-specific render profiles |

## Data A Future Cloud Snapshot Should Contain

### Required fields

| Field | Suggested Source | Why the renderer needs it |
| --- | --- | --- |
| `dimension` | `ClientLevel.dimension()` | Keep render state isolated per dimension |
| `worldTime` | `level.getGameTime()` | Animate clouds and transitions deterministically |
| `cameraPosition` | client camera | View-relative render math |
| `cloudBaseY` | cloud manager / atmosphere state | Place the cloud layer in world space |
| `cloudTopY` | derived from cloud layer height / type profile | Define visible depth range |
| `cloudCover` | `RegionAtmosphereState` / `AtmosphereClientState` | Opacity and sky fill |
| `humidity` | `RegionAtmosphereState` / `AtmosphereClientState` | Density and fog support |
| `temperature` | `RegionAtmosphereState` / `WeatherSnapshot` | Cloud phase and precipitation logic |
| `pressure` | `RegionAtmosphereState` | Storm strength and vertical structure |
| `windVector` | `WindVector` | Motion and deformation |
| `rainIntensity` | `WeatherSnapshot` / `AtmosphereClientState` | Wet cloud appearance and fallback darkening |
| `stormChance` | `ForecastOrchestrator` / `ServerWeatherStateResolver` | Storm transition weighting |
| `cloudTypeId` | `CloudLibrary` / Simple Clouds cloud selection | Visual profile lookup |
| `shadowStrength` | derived from sun angle, thickness, and cloud cover | Cloud shadowing and scene darkening |
| `lightingStrength` | derived from sun/moon/sky light | Cloud brightening and highlights |
| `fallbackDarkening` | `AtmosphereFogState` / `SimpleCloudsWhiteoutFogHandler` | Visual fallback when clouds are dense or foggy |

### Optional but strongly recommended fields

- `sunDirection`
- `sunElevation`
- `moonLight`
- `skyLight`
- `cloudThickness`
- `cloudDensityScale`
- `cloudOpticalDepth`
- `cloudAlbedo`
- `cloudPhaseFunction`
- `verticalHumidityProfile`
- `verticalTemperatureProfile`
- `localPrecipitationRate`
- `shadowMapHint`
- `renderScaleHint`
- `detailLODHint`

## What Is Still Missing For Realistic Cloud Rendering

Project Atmosphere currently has enough data to drive weather logic and approximate cloud visuals, but it is missing several renderer-facing inputs:

- No explicit sun-direction / sun-elevation render snapshot.
- No cloud optical model.
- No per-layer cloud thickness or height profile.
- No cloud shadow map or scene-shadow hint.
- No camera-space lighting snapshot dedicated to clouds.
- No render-level level-of-detail hint for cloud thickness or softness.
- No explicit fallback darkening contract separate from fog.
- No vertical moisture profile beyond the region-average atmosphere state.

## Suggested Render Snapshot Shape

A future `CloudRenderSnapshot` should be a compact immutable object with:

- identity and dimension
- camera-relative and world-relative positions
- base/top height
- density and opacity factors
- humidity, temperature, pressure, rain, wind, storm values
- cloud type id
- lighting and shadow factors
- fallback darkening factor
- render quality / LOD hint

## Why This Split Matters

- Simulation can keep running on the server without caring about frame rate.
- Client interpolation can smooth the values before render time.
- The renderer can consume one contract instead of reaching into many systems.
- Lighting, shadows, and fog can evolve independently from cloud logic.

