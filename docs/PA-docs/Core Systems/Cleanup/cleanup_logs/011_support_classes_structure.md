# 011_support_classes_structure

## Target Scope
- `src/main/java/net/Gabou/projectatmosphere/client/screen/`
- `src/main/java/net/Gabou/projectatmosphere/client/crash/`
- `src/main/java/net/Gabou/projectatmosphere/registry/`
- `src/main/java/net/Gabou/projectatmosphere/config/`
- `src/main/java/net/Gabou/projectatmosphere/event/`
- `src/main/java/net/Gabou/projectatmosphere/blocks/`
- `src/main/java/net/Gabou/projectatmosphere/items/`
- `src/main/java/net/Gabou/projectatmosphere/tools/debug/`

## Files Reviewed
- `src/main/java/net/Gabou/projectatmosphere/client/screen/ProjectAtmosphereCrashScreen.java`
- `src/main/java/net/Gabou/projectatmosphere/client/screen/WeatherRadarScreen.java`
- `src/main/java/net/Gabou/projectatmosphere/client/crash/ProjectAtmosphereCrashHandler.java`
- `src/main/java/net/Gabou/projectatmosphere/registry/ClientOnlyRegistrar.java`
- `src/main/java/net/Gabou/projectatmosphere/registry/ModBlocks.java`
- `src/main/java/net/Gabou/projectatmosphere/registry/ModClient.java`
- `src/main/java/net/Gabou/projectatmosphere/registry/ModItems.java`
- `src/main/java/net/Gabou/projectatmosphere/registry/ModParticles.java`
- `src/main/java/net/Gabou/projectatmosphere/registry/ModSounds.java`
- `src/main/java/net/Gabou/projectatmosphere/registry/ModTabs.java`
- `src/main/java/net/Gabou/projectatmosphere/config/AtmoCommonConfig.java`
- `src/main/java/net/Gabou/projectatmosphere/config/AtmoConfigScreen.java`
- `src/main/java/net/Gabou/projectatmosphere/event/BiomeChangeManager.java`
- `src/main/java/net/Gabou/projectatmosphere/event/EclipticTracker.java`
- `src/main/java/net/Gabou/projectatmosphere/event/EventHandler.java`
- `src/main/java/net/Gabou/projectatmosphere/event/SimpleCloudsEventListener.java`
- `src/main/java/net/Gabou/projectatmosphere/event/TemperatureTickHandler.java`
- `src/main/java/net/Gabou/projectatmosphere/blocks/AnemometerBlock.java`
- `src/main/java/net/Gabou/projectatmosphere/blocks/BarometreBlock.java`
- `src/main/java/net/Gabou/projectatmosphere/blocks/BlockManager.java`
- `src/main/java/net/Gabou/projectatmosphere/blocks/DustLayerBlock.java`
- `src/main/java/net/Gabou/projectatmosphere/blocks/HumidimeterBlock.java`
- `src/main/java/net/Gabou/projectatmosphere/blocks/InstrumentBlock.java`
- `src/main/java/net/Gabou/projectatmosphere/blocks/InstrumentReader.java`
- `src/main/java/net/Gabou/projectatmosphere/blocks/SandLayerBlock.java`
- `src/main/java/net/Gabou/projectatmosphere/blocks/StormShieldBlock.java`
- `src/main/java/net/Gabou/projectatmosphere/blocks/StormSirenBlock.java`
- `src/main/java/net/Gabou/projectatmosphere/blocks/ThermometerBlock.java`
- `src/main/java/net/Gabou/projectatmosphere/blocks/WeatherVaneBlock.java`
- `src/main/java/net/Gabou/projectatmosphere/items/Anemometer.java`
- `src/main/java/net/Gabou/projectatmosphere/items/Balai.java`
- `src/main/java/net/Gabou/projectatmosphere/items/Barometre.java`
- `src/main/java/net/Gabou/projectatmosphere/items/CloudProbeItem.java`
- `src/main/java/net/Gabou/projectatmosphere/items/Humidimeter.java`
- `src/main/java/net/Gabou/projectatmosphere/items/InstrumentBlockItem.java`
- `src/main/java/net/Gabou/projectatmosphere/items/Thermometre.java`
- `src/main/java/net/Gabou/projectatmosphere/items/WeatherRadarItem.java`
- `src/main/java/net/Gabou/projectatmosphere/tools/debug/ShaderSnapshot.java`

## Files Changed
- `src/main/java/net/Gabou/projectatmosphere/registry/ClientOnlyRegistrar.java`
- `src/main/java/net/Gabou/projectatmosphere/registry/ModBlocks.java`
- `src/main/java/net/Gabou/projectatmosphere/registry/ModItems.java`
- `src/main/java/net/Gabou/projectatmosphere/registry/ModParticles.java`
- `src/main/java/net/Gabou/projectatmosphere/registry/ModSounds.java`
- `src/main/java/net/Gabou/projectatmosphere/registry/ModTabs.java`

## Exact Changes Made
- `ClientOnlyRegistrar.java`
  - Added a client registration section marker above `registerClient(...)`.
- `ModBlocks.java`
  - Added a registry grouping marker for weather and instrument blocks.
- `ModItems.java`
  - Added section markers for registration, block-linked items, and helpers.
- `ModParticles.java`
  - Added section markers for particle registrations and the register helper.
- `ModSounds.java`
  - Added section markers for registered sounds and the helper method.
- `ModTabs.java`
  - Added a creative tab registration section marker.

## Classes Marked GOOD_AS_IS
- `ProjectAtmosphereCrashScreen`
- `ProjectAtmosphereCrashHandler`
- `AtmoCommonConfig`
- `AtmoConfigScreen`
- `BiomeChangeManager`
- `EclipticTracker`
- `EventHandler`
- `SimpleCloudsEventListener`
- `TemperatureTickHandler`
- `AnemometerBlock`
- `BarometreBlock`
- `BlockManager`
- `DustLayerBlock`
- `HumidimeterBlock`
- `InstrumentBlock`
- `InstrumentReader`
- `SandLayerBlock`
- `StormShieldBlock`
- `StormSirenBlock`
- `ThermometerBlock`
- `WeatherVaneBlock`
- `Anemometer`
- `Balai`
- `Barometre`
- `CloudProbeItem`
- `Humidimeter`
- `InstrumentBlockItem`
- `Thermometre`
- `WeatherRadarItem`
- `ShaderSnapshot`

## Classes Marked REORGANIZED
- `ClientOnlyRegistrar`
- `ModBlocks`
- `ModItems`
- `ModParticles`
- `ModSounds`
- `ModTabs`

## Classes Marked NEEDS_RENAME_LATER
- None recorded in original batch summary.

## Classes Marked NEEDS_MOVE_LATER
- None recorded in original batch summary.

## Classes Marked NEEDS_SPLIT_LATER
- `AtmoConfigScreen`
- `BlockManager`
- `EventHandler`

## Classes Marked COULD_MERGE_LATER
- `ModBlocks` and `ModItems` only if registry creation is later consolidated around a shared registry helper.
- `EventHandler` and `SimpleCloudsEventListener` only if server event handling is later split into smaller handler classes.

## Classes Marked RISKY_LEAVE_AS_IS
- `AtmoCommonConfig`
- `AtmoConfigScreen`
- `BlockManager`
- `EventHandler`

## Legacy, Fallback, Diagnostic, or Rarely Used Code Moved
- None.

## Files Skipped and Why
- `client/render/` and `client/rendering/` were outside the scope of this batch.
- `AtmoCommonConfig` was reviewed but left unchanged because its constant/definition ordering is already stable and should not be reshaped without a config migration plan.
- `AtmoConfigScreen`, `EventHandler`, and `BlockManager` were reviewed but left unchanged because they are broad, behavior-sensitive classes and a deeper structure pass would be noisy for this support-only batch.
- `ProjectAtmosphereCrashScreen`, `ProjectAtmosphereCrashHandler`, `ShaderSnapshot`, and the item/block classes were reviewed and left unchanged because they are already readable enough or too sensitive for a low-risk structure pass.
- `BiomeChangeManager`, `EclipticTracker`, `SimpleCloudsEventListener`, and `TemperatureTickHandler` were reviewed and left unchanged because their current method ordering already matches event flow.

## Build Result
- `.\gradlew.bat build` succeeded

## Cleanup Log Files Updated
- `temp_project_reorganization_audit/cleanup_logs/cleanup_index.md`
- `temp_project_reorganization_audit/cleanup_logs/011_support_classes_structure.md`

## Recommended Next Batch
- `src/main/java/net/Gabou/projectatmosphere/tools/debug/`
