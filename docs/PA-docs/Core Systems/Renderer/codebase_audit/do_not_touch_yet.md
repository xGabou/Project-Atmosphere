# Do Not Touch Yet

These systems are too risky to refactor before the future cloud renderer boundary is designed.

- `src/main/java/net/Gabou/projectatmosphere/manager/AtmosphereManager.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/ForecastOrchestrator.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/ForecastGenerator.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CloudManager.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/SimpleCloudSpawner.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/SimpleCloudsCompat.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoInstance.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneManager.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneInstance.java`
- `src/main/java/net/Gabou/projectatmosphere/client/ClientTickHandler.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/SimpleCloudsTornadoRenderer.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/SimpleCloudsHurricaneRenderer.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/DefaultPipelineTornadoMixin.java`
- `src/main/java/net/Gabou/projectatmosphere/mixin/client/DefaultPipelineHurricaneMixin.java`

## Why These Should Wait

- They already work well enough to serve as reference points.
- They contain multiple responsibilities that need a stable target design first.
- They are likely to be touched by renderer work, so touching them early risks creating churn.
- Some of them are already the best place to document current behavior instead of changing it.

