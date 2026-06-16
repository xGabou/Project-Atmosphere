# PA Simple Clouds Identity And Rollback Audit

Date: 2026-06-15

## 1. Files Modified

- `src/main/java/net/Gabou/projectatmosphere/compat/simpleclouds/SimpleCloudsTrackingIdentity.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/simpleclouds/SimpleCloudsRollbackDebugger.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/simpleclouds/SimpleCloudsAtmosphereCloudService.java`
- `src/main/java/net/Gabou/projectatmosphere/compat/SimpleCloudsCompat.java`
- `src/main/java/net/Gabou/projectatmosphere/clouds/backend/CloudBackendBridgeSnapshot.java`
- `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CloudManager.java`
- `src/main/java/net/Gabou/projectatmosphere/event/SimpleCloudsEventListener.java`
- `src/main/java/net/Gabou/projectatmosphere/manager/ForecastOrchestrator.java`

## 2. Does PA Write To Simple Clouds Clouds After World Load?

Before this pass: yes.

Found write paths:

- `ForecastOrchestrator.onPlayerLogin` called `SimpleCloudsCompat.doInitialGenWithWeather(...)` for new player centers.
- `ForecastOrchestrator.onPlayerLogin` always called `AtmosphereCloudServices.get().ensureCloudAtPosition(...)`; the Simple Clouds service delegated to `SimpleCloudsCompat.ensureCloudAtPosition(...)`.
- `SimpleCloudsAtmosphereCloudService.shouldTrySpawn(...)` allowed the PA Simple Clouds spawn loop to run.
- `SimpleCloudsCompat.ensureCloudAtPosition(...)` could remove a nearest or farthest cloud and add a replacement cloud.
- `SimpleCloudsEventListener.applyCloudShear(...)` changed Simple Clouds movement direction and max speed through the tick event.
- Real PA-native-to-Simple-Clouds migration could mirror PA snapshots into Simple Clouds.
- Explicit commands still call Simple Clouds spawn helpers by design.

After this pass:

- Automatic Simple Clouds service spawning is disabled.
- Automatic login Simple Clouds initial seeding is skipped.
- Automatic ensure-coverage writes are disabled through the Simple Clouds service no-op.
- Runtime PA movement overrides are disabled; PA no longer calls `setModifiedMovementDirection` or `setModifiedMaxSpeed`.
- Explicit command spawns and real backend migration remain allowed and are logged.

## 3. Are Bridge Snapshots Read-Only?

While backend remains `SIMPLE_CLOUDS`, bridge snapshot capture is read-only with respect to Simple Clouds cloud objects.

The capture path reads:

- cloud type
- world position
- radius
- derived density/coverage/severity
- game time
- new source tracking key

It then writes only PA migration saved data. It does not call Simple Clouds add, remove, movement, position, scroll, or sync APIs.

`mirrorPaNativeClouds(...)` remains a write path, but it is only used for PA-native-to-Simple-Clouds migration and now has a backend safety guard.

## 4. Simple Clouds Identity Source

Before this pass, PA telemetry and atmosphere sampling used `CloudManager.extractId(region)`.

That method used:

- `ICloudRegionId.projectatmosphere$getId()` when PA's mixin was present.
- `System.identityHashCode(region)` as fallback.

`projectatmosphere$getId()` came from `CloudRegionMixin`, where PA injects a random `int projectatmosphere$id` into each Simple Clouds `CloudRegion` and saves it to NBT as `projectatmosphere_id`.

This is not a true Simple Clouds instance identity. It is a PA-added random integer.

## 5. Identity Collision Root Cause

The telemetry example is consistent with a PA tracking collision:

```text
cloudId: -266392238
type: simpleclouds:severe_cumulonimbus
positions separated by thousands of blocks in the same sample window
```

Root cause:

- PA keyed `REGION_DATA`, cloud birth ticks, last positions, sample ticks, cloud types, and telemetry `cloudId` by one `int`.
- If two Simple Clouds regions had the same PA-injected id, the later sample reused the same map entry.
- `indexCloudRegions(...)` also keyed a map by the same int, so one region could overwrite another.
- The bridge snapshot format had no source tracking key, so snapshots could not explain or diagnose identity collisions.

The random `int` can collide in principle. It can also be duplicated if copied through saved cloud data or if another path constructs regions from identical persisted PA metadata.

## 6. Collision Fix

Added `SimpleCloudsTrackingIdentity`.

The new tracking key is a runtime composite identity:

```text
legacy PA source id
cloud type
initial nearest PA region bucket
capture/spawn time bucket
formation index for same composite base
```

Important properties:

- Two distant clouds with the same legacy PA id no longer merge.
- Telemetry `cloudId` now uses the composite tracking key.
- Atmosphere cloud sampling state maps now use the composite tracking key.
- Bridge snapshots now store `sourceTrackingKey`.
- The key is associated with the `CloudRegion` object through a weak map, so normal cloud movement does not change the key every time the cloud crosses a region boundary.

Added collision warnings:

```text
[Atmosphere] Simple Clouds tracking collision: id=...
[Atmosphere] Simple Clouds legacy id collision avoided: sourceId=...
```

The second warning is expected if two clouds share the old PA int id but are kept separate by the new composite key.

## 7. Rollback Audit Findings

The observed 10-second rollback has two plausible PA-side contributors:

- PA had a 200-tick Simple Clouds bridge snapshot capture loop.
- PA also had a 200-tick Simple Clouds spawn attempt loop.
- PA was overriding Simple Clouds movement direction and max speed on cloud tick events.

Snapshot capture itself was read-only and should not reposition clouds.

The movement override was the highest-risk rollback contributor because Simple Clouds can visually advance client-side while server state is authoritative. If PA changes server-side movement vectors differently from the client/render state, the client can appear to move forward and then snap back on server sync.

Implemented rollback-risk fixes:

- Disabled PA runtime movement overrides for Simple Clouds.
- Disabled automatic PA Simple Clouds service spawning.
- Disabled automatic PA login Simple Clouds seeding.
- Disabled automatic PA ensure-coverage writes through the Simple Clouds service.
- Kept explicit command spawns and real backend migration paths available.

Client/render position is not available from the server-side bridge capture path, so the debug log reports it as `unavailable`.

## 8. 10-Second Schedulers Found

Found 200-tick / roughly 10-second paths:

- `SimpleCloudsAtmosphereCloudService.SNAPSHOT_CAPTURE_INTERVAL_TICKS = 200`
- `CloudBackendMigrationManager.SNAPSHOT_CAPTURE_INTERVAL_TICKS = 200` for PA-native snapshots
- `CloudManager.SPAWN_ATTEMPT_INTERVAL_TICKS = 200`
- `ServerTelemetrySampler` atmospheric sampling at `gameTime % 200 == 0`
- `SeasonalAtmosphericDrift.TICK_INTERVAL = 200`

The Simple Clouds bridge snapshot capture is aligned with the observed rollback period but is read-only. The Simple Clouds spawn attempt loop was a write-capable 200-tick path and is now disabled for the Simple Clouds service.

## 9. Debug Logging Added

Added severe-cloud rollback audit logging from `SimpleCloudsRollbackDebugger`.

For one tracked severe Simple Clouds cloud, logs include:

- tracking key
- cloud type
- server position
- client/render position availability
- bridge snapshot position
- previous tracked position
- delta movement
- speed estimate
- movement direction
- max speed
- whether PA touched movement this tick
- whether PA spawned/removed/mirrored a Simple Clouds cloud this tick
- active backend
- migration status

Example prefix:

```text
[Atmosphere] Simple Clouds rollback audit key=...
```

Write path logs:

```text
[Atmosphere] Simple Clouds write path used: action=... key=... gameTime=...
```

Collision logs:

```text
[Atmosphere] Simple Clouds tracking collision: id=...
[Atmosphere] Simple Clouds legacy id collision avoided: sourceId=...
```

## 10. Required Questions

1. Does PA ever write back to Simple Clouds cloud positions?

No direct `setPosition`, `setWorldX`, or `setWorldZ` write was found. Before this pass, PA did write movement direction and max speed through Simple Clouds tick events. That runtime movement override is now disabled.

2. Does PA ever remove and recreate Simple Clouds clouds after world load?

Before this pass, yes through `ensureCloudAtPosition(...)`, regeneration, migration, commands, and some severe-event systems. After this pass, the automatic Simple Clouds service ensure-coverage path is disabled. Explicit commands, regeneration, real migration, and restricted severe-event systems remain outside this fix.

3. Does PA ever mirror bridge snapshots into Simple Clouds when migration status is `skipped, fresh world`?

No direct path was found in `CloudBackendMigrationManager`. Fresh-world first observation sets skipped status and returns without mirroring. `mirrorPaNativeClouds(...)` remains available only as a migration operation and now checks that the active backend is `SIMPLE_CLOUDS`.

4. Is bridge snapshot capture purely read-only while backend is `SIMPLE_CLOUDS`?

Yes. It reads Simple Clouds regions and saves PA bridge snapshot data. It does not mutate Simple Clouds regions.

5. Is `cloudId` derived from a stable Simple Clouds instance id?

Before this pass, no. It was derived from PA's injected random `int projectatmosphere$id`, not a Simple Clouds-provided stable instance id.

6. If not, what is `cloudId` derived from?

Before this pass, `cloudId` was `String.valueOf(projectatmosphere$id)` or `System.identityHashCode(region)` fallback. After this pass, telemetry `cloudId` is the composite tracking key from `SimpleCloudsTrackingIdentity`.

7. Can two Simple Clouds formations receive the same PA tracking id?

Before this pass, yes. After this pass, two clouds can still share the old legacy source id, but they should not share the final composite tracking key when they are distant or separately captured.

8. Can one tracking id be updated from two distant positions in the same time window?

Before this pass, yes. After this pass, this should be prevented by composite keys and logged if it ever still occurs.

9. Can migration snapshots collapse multiple Simple Clouds clouds into one PA cloud?

The migration loop created one PA region per snapshot, but snapshots had no tracking key and could not diagnose identity collisions. After this pass, each Simple Clouds snapshot carries `sourceTrackingKey`, so collision and duplicate-source cases are visible and separable.

10. Is there any 200 tick / 10 second scheduler that can explain periodic rollback?

Yes. The relevant 200-tick paths are Simple Clouds bridge capture, PA-native bridge capture, Simple Clouds spawn attempts, telemetry sampling, and seasonal drift. Of those, the Simple Clouds spawn attempt loop was write-capable and is now disabled for the Simple Clouds service. Bridge capture remains read-only.

11. Do server cloud positions and client visual scroll positions diverge?

The server-side code cannot directly read client/render cloud position. The previous PA movement override could plausibly cause divergence between client visual prediction and server-authoritative movement. The new rollback log reports server position and marks client/render position as unavailable.

12. Does PA modify Simple Clouds scroll, wind, velocity, or movement?

Before this pass, yes for movement direction and max speed through `CloudRegionTickEvent`. After this pass, PA no longer overrides Simple Clouds runtime movement while Simple Clouds owns the visual backend. PA does not modify Simple Clouds scroll. Explicitly created PA-command/migration clouds can still receive initial movement parameters at creation time.

## 11. Systems Confirmed Untouched

- Cloud rendering visuals
- PA native cloud morphology
- Atmosphere stabilization values
- WeatherCell thresholds
- Forecast generation data
- Shader source
- Distant Horizons integration
- Tornado systems
- Hurricane systems
- Blizzard systems

## 12. Build Results

- `.\gradlew compileJava`: passed
- `.\gradlew build`: passed

Notes:

- Existing deprecation and mixin target warnings remain.
- No compile, test, or build failures were observed.

## 13. Manual Test Checklist

With Simple Clouds loaded:

- Enter a world with backend `SIMPLE_CLOUDS`.
- Run `/pa debug verify`.
- Confirm:

```text
Migration Status: skipped, fresh world
PA Native Clouds Rendered: 0
Duplicate Visual Cloud Risk: no
```

- Find a Simple Clouds severe cumulonimbus.
- Watch it for 5 to 15 minutes.
- Confirm whether it still rolls forward then back.
- Check logs for:

```text
Simple Clouds tracking collision
Simple Clouds legacy id collision avoided
Simple Clouds rollback audit
Simple Clouds write path used
paTouchedThisTick=true
paSpawnRemoveMirrorThisTick=true
```

Expected after this pass:

- Telemetry should not show one `cloudId` alternating between distant cloud tracks.
- Bridge snapshots should store separate `sourceTrackingKey` values for distant Simple Clouds formations.
- Fresh-world Simple Clouds backend should not get automatic PA cloud spawn, remove, mirror, or movement overrides.
- If rollback continues with no PA touch/write logs, the remaining likely source is Simple Clouds server/client synchronization outside PA bridge mutation.
