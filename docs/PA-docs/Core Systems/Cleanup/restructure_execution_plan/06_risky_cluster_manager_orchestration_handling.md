# Risky Cluster: Broad Manager Orchestration

This cluster was processed from the remaining risky manual review set. The goal was to separate reusable cloud-spawn severity rules from the broad orchestration classes without changing gameplay, forecast, spawn, or compatibility behavior.

## Cluster Files

| File | Risk cluster | Why risky | Can be safely refactored now | Allowed safe refactor | Forbidden refactor | Expected benefit | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `src/main/java/net/Gabou/projectatmosphere/manager/AtmosphereManager.java` | Broad manager/orchestration risk | Central server lifecycle coordinator with player login, tick, regeneration, season, and cloud queue responsibilities. | no | Future helper extraction only if the runtime lifecycle remains transparent. | Changing tick order, sync flow, or cloud queue behavior. | Lower orchestration density. | High |
| `src/main/java/net/Gabou/projectatmosphere/manager/ForecastOrchestrator.java` | Broad manager/orchestration risk | Owns forecast generation bootstrap, region orchestration, regeneration, and weather access policy. | no | Future split into bootstrap, region lookup, and regeneration helpers. | Changing forecast output or region resolution behavior. | Clearer forecast ownership boundary. | High |
| `src/main/java/net/Gabou/projectatmosphere/manager/ForecastGenerator.java` | Broad manager/orchestration risk | Large but currently stable forecast pipeline with heavy sampling, averaging, and legacy hydration logic. | yes | Leave as-is for this pass; future helper extraction only if a narrower boundary appears. | Altering forecast math, output, or save-data hydration. | None required in this pass. | Medium |
| `src/main/java/net/Gabou/projectatmosphere/manager/SimpleCloudSpawner.java` | Broad manager/orchestration risk | Mixed spawn application with cloud severity policy and async weather sampling. | yes | Extract cloud-spawn severity rules into a dedicated helper. | Changing spawn selection, severity output, or cloud creation behavior. | Reduce policy leakage from the spawner. | Highest |
| `src/main/java/net/Gabou/projectatmosphere/compat/SimpleCloudsCompat.java` | Compatibility orchestration risk | Adapter layer also carries cloud-region factory policy and spawn wiring. | yes | Redirect cloud severity math to the new helper and keep adapter behavior intact. | Changing SimpleClouds integration behavior or spawn region construction. | Reduce duplicate policy logic. | High |
| `src/main/java/net/Gabou/projectatmosphere/compat/CompatHandler.java` | Broad compatibility orchestration risk | Module detection and initialization logging are centralized here. | no | Future split only if compat boot policy grows further. | Altering compat detection or init order. | Better future separation of detection vs init logging. | Medium |
| `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CloudManager.java` | Broad manager/orchestration risk | Cloud lifecycle, telemetry, sampling, spawn attempts, and biome contribution application are all centralized here. | yes | Extract cloud spawn severity math to a shared helper; keep lifecycle behavior intact. | Changing render output, spawn frequency, or cloud update behavior. | Remove duplicated spawn policy from cloud management. | Highest |

## Result Summary

- Real restructure applied: `SimpleCloudSpawner`, `SimpleCloudsCompat`, and `CloudManager` now share a dedicated helper for cloud spawn severity rules.
- `ForecastGenerator`, `ForecastOrchestrator`, `AtmosphereManager`, and `CompatHandler` were reviewed but intentionally left for manual follow-up because they remain broad orchestration surfaces.
