# Version 1 companion climate API

Package: net.Gabou.projectatmosphere.api.climate. RuntimeClimateBridge.API_VERSION=1.

Open a Session on the Overworld server thread with a detached GeographicClimate callback. Only one session can own PA's process-global atmosphere. Session methods reject closed/stale or worker-thread access. Closing releases the lease and clears level/callback references.

Session.sample(x,z) reads only existing active RegionAtmosphereState. It returns empty while uninitialized/regenerating or when no active region exists. It performs no chunk load, biome query, forecast creation, weather simulation, or registry scan. Region size is PA's native 2000 blocks.

RegionalClimateObservation contains region coordinates/size, observedGameTick, Celsius temperature, dimensionless nonnegative precipitation intensity, normalized humidity, wind X/Z in m/s, and pressure hPa. PA wind direction maps X=-sin(angle), Z=cos(angle). Observation time is ServerLevel.getGameTime, not an internal simulation-update timestamp. Humidity's float ceiling is 1.2f.

GeographicClimate supplies the representative point, latitude, baseline Celsius temperature, annual rainfall mm/year, sea-relative elevation, coast/ocean distance, marine flag, and prevailing wind direction. It is baseline context, not weather and not necessarily a regional mean. Empty callback results preserve PA initialization. TemperatureGenerator uses only baseline temperature before existing variations and clamps; it does not convert rainfall into intensity/humidity or apply an extra elevation correction.

The detached initialization callback can run on forecast workers and must be thread-safe and generation-free. Session.baselineInitializationCount exposes successful baseline-backed initialization requests for diagnostics. The sample API remains server-thread authoritative.

ForecastGenerator's BiomeSampler now uses the actual level's bound RandomState sampler. Creating a new detached sampler was incompatible with Landscape density fields. Forecast setup retains its existing biome sampling behavior; the climate observation bridge does not call it.

Integration validation is maintained by Landscape's existing RuntimeIntegrationChecks. Its reproduction-only PaForecastRadius mixin bounds initial setup to radius 128; it does not fake PA weather or change this project's production default radius. See the companion phase2a report for runtime/save/reopen/disposal results and the full audit here.
