# 008 Client Core Structure

## Target Scope
- `src/main/java/net/Gabou/projectatmosphere/client/`
- Excluding:
  - `src/main/java/net/Gabou/projectatmosphere/client/render/`
  - `src/main/java/net/Gabou/projectatmosphere/client/render/**`
  - `src/main/java/net/Gabou/projectatmosphere/client/rendering/`
  - `src/main/java/net/Gabou/projectatmosphere/client/rendering/**`

## Files Reviewed
- 20 files

## Files Changed
- None

## Exact Changes Made
- None.
- The pass was classification-only because the client core was already structurally acceptable and deeper reordering would have been noisy or risky.

## Classes Marked GOOD_AS_IS
- `ClientPacketHandlers`
- `ClientSyncLock`
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
- `SimpleCloudsWhiteoutFogHandler`
- `HUDOverlayRenderer`
- `TornadoClientEffects`
- `AtmosphereClientState`

## Classes Marked REORGANIZED
- None

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
- `ClientPacketHandlers` and `ClientSyncLock`
- `ClientForecastLoadingLifecycle` and `ClientForecastLoadingWorkQueue`

## Classes Marked RISKY_LEAVE_AS_IS
- `ClientTickHandler`
- `AtmosphereClientState`
- `AtmosphereFogState`
- `SimpleCloudsWhiteoutFogHandler`
- `ProjectAtmosphereCrashHandler`

## Legacy/Debug/Rarely Used Code Moved
- None.

## Files Skipped and Why
- Render and rendering folders were explicitly excluded by scope.
- `ClientRenderHook`, `SimpleCloudsTornadoRenderer`, and `SimpleCloudsHurricaneRenderer` were not touched.
- No structural edits were made because the core client layer was already reasonably organized and the remaining candidates were too central or render-adjacent.

## Build Result
- `.\gradlew.bat build` succeeded.

## Remaining Risks
- The client tick and atmosphere/fog caches remain broad.
- Render-related client files still need a separate pass later.

## Recommended Next Batch
- `src/main/java/net/Gabou/projectatmosphere/client/loading/`
