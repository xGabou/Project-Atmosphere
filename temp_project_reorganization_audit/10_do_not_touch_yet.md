# Do Not Touch Yet

These files and systems are risky to move, rename, split, or refactor before the cloud renderer boundary is proven.

| File or system | Why it should not be touched yet |
|---|---|
| `src/main/java/net/Gabou/projectatmosphere/client/render/SimpleCloudsTornadoRenderer.java` | It is already the main tornado render bridge and is too close to the future boundary to refactor blindly |
| `src/main/java/net/Gabou/projectatmosphere/client/render/SimpleCloudsHurricaneRenderer.java` | Same reason as the tornado renderer; it is a key render integration point |
| `src/main/java/net/Gabou/projectatmosphere/compat/SimpleCloudsCompat.java` | It is the main Simple Clouds adapter and already carries too much policy to change casually |
| `src/main/java/net/Gabou/projectatmosphere/manager/AtmosphereManager.java` | It is a broad bootstrap coordinator; moving pieces now would create churn before the renderer contract exists |
| `src/main/java/net/Gabou/projectatmosphere/manager/ForecastOrchestrator.java` | It is a source-of-truth hub for forecast state and should remain stable until the render boundary is settled |
| `src/main/java/net/Gabou/projectatmosphere/manager/ForecastGenerator.java` | It is broader than its name, but not yet a safe refactor target because it feeds the forecast pipeline |
| `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoManager.java` | Tornado lifecycle, sync, and attachment logic are too entangled to move now |
| `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoInstance.java` | It mixes simulation and render-adjacent state; changing it too early could break storm behavior |
| `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneManager.java` | Broad hurricane coordination should stay stable until snapshots and render boundaries are proven |
| `src/main/java/net/Gabou/projectatmosphere/modules/hurricane/HurricaneInstance.java` | It contains lifecycle and render descriptor logic that should not be disturbed yet |
| `src/main/java/net/Gabou/projectatmosphere/client/ClientTickHandler.java` | It is a catch-all client coordinator; splitting it before the new renderer exists could create more confusion |
| `src/main/java/net/Gabou/projectatmosphere/client/fog/AtmosphereFogState.java` | Fog is a future render input; changing its ownership too early risks coupling it to the wrong boundary |

