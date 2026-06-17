# 010_client_non_render_structure

## Target Scope
- `src/main/java/net/Gabou/projectatmosphere/client/`
- Excluding `src/main/java/net/Gabou/projectatmosphere/client/render/`
- Excluding `src/main/java/net/Gabou/projectatmosphere/client/render/**`
- Excluding `src/main/java/net/Gabou/projectatmosphere/client/rendering/`
- Excluding `src/main/java/net/Gabou/projectatmosphere/client/rendering/**`

## Files Reviewed
- `src/main/java/net/Gabou/projectatmosphere/client/BiomeClientTemperatureCache.java`
- `src/main/java/net/Gabou/projectatmosphere/client/ClientPacketHandlers.java`
- `src/main/java/net/Gabou/projectatmosphere/client/ClientSyncLock.java`
- `src/main/java/net/Gabou/projectatmosphere/client/ClientTickHandler.java`
- `src/main/java/net/Gabou/projectatmosphere/client/HUDOverlayRenderer.java`
- `src/main/java/net/Gabou/projectatmosphere/client/SimpleCloudsWhiteoutFogHandler.java`
- `src/main/java/net/Gabou/projectatmosphere/client/TornadoClientEffects.java`
- `src/main/java/net/Gabou/projectatmosphere/client/atmosphere/AtmosphereClientState.java`
- `src/main/java/net/Gabou/projectatmosphere/client/crash/ProjectAtmosphereCrashHandler.java`
- `src/main/java/net/Gabou/projectatmosphere/client/fog/AtmosphereFogState.java`
- `src/main/java/net/Gabou/projectatmosphere/client/hurricane/ClientHurricaneStateCache.java`
- `src/main/java/net/Gabou/projectatmosphere/client/loading/ClientForecastLoadingLifecycle.java`
- `src/main/java/net/Gabou/projectatmosphere/client/loading/ClientForecastLoadingWorkQueue.java`
- `src/main/java/net/Gabou/projectatmosphere/client/loading/ForecastLoadingOverlayRenderer.java`
- `src/main/java/net/Gabou/projectatmosphere/client/loading/ForecastLoadingStage.java`
- `src/main/java/net/Gabou/projectatmosphere/client/loading/ForecastLoadingState.java`
- `src/main/java/net/Gabou/projectatmosphere/client/loading/IntegratedForecastLoadingBridge.java`
- `src/main/java/net/Gabou/projectatmosphere/client/sound/TornadoAudioClient.java`
- `src/main/java/net/Gabou/projectatmosphere/client/sound/TornadoRoarLoop.java`

## Files Changed
- `src/main/java/net/Gabou/projectatmosphere/client/BiomeClientTemperatureCache.java`
- `src/main/java/net/Gabou/projectatmosphere/client/ClientSyncLock.java`

## Exact Changes Made
- `BiomeClientTemperatureCache.java`
  - Added a `Cache updates` section marker.
  - Added a `Lookups` section marker.
  - Added a `Reset` section marker.
  - Kept the existing cache logic unchanged.
- `ClientSyncLock.java`
  - Added a `State access` section marker.
  - Added a `State updates` section marker.
  - Added a `Reset` section marker.
  - Kept the existing lock logic unchanged.

## Classes Marked GOOD_AS_IS
- `ClientPacketHandlers`
- `ClientHurricaneStateCache`
- `ClientForecastLoadingLifecycle`
- `ClientForecastLoadingWorkQueue`
- `ForecastLoadingOverlayRenderer`
- `ForecastLoadingStage`
- `ForecastLoadingState`
- `IntegratedForecastLoadingBridge`
- `TornadoAudioClient`
- `TornadoRoarLoop`
- `ProjectAtmosphereCrashHandler`
- `FogBiomeClassifier`
- `HUDOverlayRenderer`
- `TornadoClientEffects`
- `AtmosphereClientState`

## Classes Marked REORGANIZED
- `BiomeClientTemperatureCache`
- `ClientSyncLock`

## Classes Marked NEEDS_RENAME_LATER
- `BiomeClientTemperatureCache`
- `ClientTickHandler`
- `ClientSyncLock`

## Classes Marked NEEDS_MOVE_LATER
- `BiomeClientTemperatureCache`
- `HUDOverlayRenderer`

## Classes Marked NEEDS_SPLIT_LATER
- `ClientTickHandler`
- `AtmosphereClientState`
- `AtmosphereFogState`
- `ClientHurricaneStateCache`
- `ProjectAtmosphereCrashHandler`

## Classes Marked COULD_MERGE_LATER
- `ClientPacketHandlers` and `ClientSyncLock` only if the client sync surface is later collapsed into a single client session boundary.
- `ClientForecastLoadingLifecycle` and `ClientForecastLoadingWorkQueue` only if loading orchestration is simplified later.

## Classes Marked RISKY_LEAVE_AS_IS
- `ClientTickHandler`
- `AtmosphereClientState`
- `AtmosphereFogState`
- `SimpleCloudsWhiteoutFogHandler`
- `ProjectAtmosphereCrashHandler`

## Legacy, Fallback, Diagnostic, or Rarely Used Code Moved
- None.

## Files Skipped and Why
- `src/main/java/net/Gabou/projectatmosphere/client/render/` and `src/main/java/net/Gabou/projectatmosphere/client/rendering/` were excluded by request.
- `ClientRenderHook`, `SimpleCloudsTornadoRenderer`, and `SimpleCloudsHurricaneRenderer` were excluded by request.
- `ClientTickHandler`, `AtmosphereClientState`, `AtmosphereFogState`, `SimpleCloudsWhiteoutFogHandler`, and `ProjectAtmosphereCrashHandler` were reviewed but left unchanged because deeper structural edits would have been noisy or risky for this pass.
- The loading subsystem files were reviewed and left unchanged because they were already structurally acceptable for this batch.

## Build Result
- `.\gradlew.bat build` succeeded

## Cleanup Log Files Updated
- `temp_project_reorganization_audit/cleanup_logs/cleanup_index.md`
- `temp_project_reorganization_audit/cleanup_logs/010_client_non_render_structure.md`

## Recommended Next Batch
- `src/main/java/net/Gabou/projectatmosphere/client/screen/`
