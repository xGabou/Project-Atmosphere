# 003 API / Network Structure

## Target Scope
- `src/main/java/net/Gabou/projectatmosphere/api/`
- `src/main/java/net/Gabou/projectatmosphere/network/`

## Files Reviewed
- 21 files

## Files Changed
- 21 files

## Exact Changes Made
- Added or clarified class-level ownership comments for API and packet classes.
- Added decode/handle or contract section comments where helpful.
- No packet payloads, encoding/decoding, handlers, or registration behavior changed.

## Classes Marked GOOD_AS_IS
- `WindVectorApi`
- `WeatherSnapshot`
- `ForecastSampling`
- `AtmosphereWorldEffect`
- `CropStressEvent`
- `CropStressType`
- `AtmosphereWeatherTickEvent`
- `CloudRegionTickEventTornadoAccess`
- `ScAPICloudRegionTornadoAccess`
- `ITornadoRegion`
- `AuthChallengePacket`
- `AuthChallengeReplyPacket`
- `BiomeDayTemperaturePacket`
- `FogDebugOverridePacket`
- `ForecastLoadingStatusPacket`
- `InstrumentReadoutPacket`
- `RemoveTornadoPacket`
- `SpawnTornadoPacket`
- `SyncAtmosphereStatusPacket`
- `SyncHurricaneStatePacket`
- `SyncTornadoesPacket`

## Classes Marked REORGANIZED
- `AtmoApi`
- `NetworkHandler`

## Classes Marked NEEDS_RENAME_LATER
- None recorded in the original batch summary.

## Classes Marked NEEDS_MOVE_LATER
- None recorded in the original batch summary.

## Classes Marked NEEDS_SPLIT_LATER
- `AtmoApi`
- `NetworkHandler`

## Classes Marked COULD_MERGE_LATER
- None recorded in the original batch summary.

## Classes Marked RISKY_LEAVE_AS_IS
- None recorded in the original batch summary.

## Legacy/Debug/Rarely Used Code Moved
- In `AtmoApi`:
  - `isRainningAt`
  - `isRainningLevel`
  - `getWeatherAlerts`
  - `getWeatherHistory`

## Files Skipped and Why
- Files outside the target modules were skipped by scope.
- Packet behavior, encoding/decoding, and registration were left untouched.

## Build Result
- `.\gradlew.bat build` succeeded.

## Remaining Risks
- `AtmoApi` still mixes several roles and may want a future split.
- `NetworkHandler` remains a central registry but was organized safely only.

## Recommended Next Batch
- `src/main/java/net/Gabou/projectatmosphere/modules/region/`
