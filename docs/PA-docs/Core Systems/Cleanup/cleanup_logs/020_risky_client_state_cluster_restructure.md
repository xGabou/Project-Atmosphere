# Risky Client State Cluster Restructure

## Risk clusters created
- `docs/PA-docs/Core Systems/Cleanup/restructure_execution_plan/05_risky_cluster_client_state_handling.md`

## Cluster selected
- Client state or tick lifecycle risk

## Why selected
- `AtmosphereClientState` and `AtmosphereFogState` were the lowest-risk files in the cluster that still allowed a real internal refactor.
- Their tick methods could be made easier to read by extracting the target-update logic without changing timing or behavior.

## Files changed
- `src/main/java/net/Gabou/projectatmosphere/client/atmosphere/AtmosphereClientState.java`
- `src/main/java/net/Gabou/projectatmosphere/client/fog/AtmosphereFogState.java`
- `docs/PA-docs/Core Systems/Cleanup/restructure_execution_plan/05_risky_cluster_client_state_handling.md`
- `docs/PA-docs/Core Systems/Cleanup/cleanup_logs/020_risky_client_state_cluster_restructure.md`
- `docs/PA-docs/Core Systems/Cleanup/cleanup_logs/cleanup_index.md`

## Files moved
- None

## Classes split
- None

## Classes deleted because empty
- None

## Files resolved
- `src/main/java/net/Gabou/projectatmosphere/client/atmosphere/AtmosphereClientState.java` -> `RESOLVED_RESTRUCTURED`
- `src/main/java/net/Gabou/projectatmosphere/client/fog/AtmosphereFogState.java` -> `RESOLVED_RESTRUCTURED`

## Files still risky
- `src/main/java/net/Gabou/projectatmosphere/client/ClientTickHandler.java` -> `STILL_RISKY_MANUAL_REVIEW`
- `src/main/java/net/Gabou/projectatmosphere/client/hurricane/ClientHurricaneStateCache.java` -> `STILL_RISKY_MANUAL_REVIEW`
- `src/main/java/net/Gabou/projectatmosphere/client/crash/ProjectAtmosphereCrashHandler.java` -> `STILL_RISKY_MANUAL_REVIEW`

## Build result
- `.\gradlew.bat build` succeeded

## Remaining risky clusters
- Broad manager or orchestration risk
- Mixins and injection points
- Render pipeline and shader risk
- Tornado and hurricane lifecycle risk
- Atmosphere runtime risk
- Config or save format risk
- Broad data/model class risk

## Next recommended cluster
- Broad manager or orchestration risk
