# Task 2 Implementation Report

Goal: persist the mutable live atmospheric state independently from immutable forecast data and native cloud-region persistence.

This task did not modify forecast generation, forecast regeneration, season handling, cloud spawning, cloud evolution, tornadoes, hurricanes, blizzards, rendering, or shaders.

## Modified Files For Task 2

### `src/main/java/net/Gabou/projectatmosphere/manager/ForecastOrchestrator.java`
Reason: added server lifecycle hooks only.

Changes:
- Restores live atmospheric state after saved forecast data has loaded and dynamic systems have initialized.
- Snapshots live atmospheric state on server stop before in-memory forecast data is cleared.

### `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericStateSavedData.java`
Reason: new Minecraft `SavedData` owner for the mutable atmosphere layer.

Changes:
- Saves live atmosphere data under `project_atmosphere_live_atmosphere`.
- Keeps this persistence separate from forecast storage and native cloud-region storage.
- Stores version, save timestamps, region mutable states, active regions, scheduler state, cyclone state, and ocean basin state.

### `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/RegionAtmosphereState.java`
Reason: added mutable-only snapshot and restore methods.

Saved mutable fields:
- Temperature
- Humidity
- Pressure
- Wind
- Cloud cover
- Cloud water
- Cyclone cloud floor
- Cyclone rain floor
- Sunlight
- Rain intensity
- Daily observed temperature profile
- Daily observed humidity profile
- Daily observed pressure profile

Forecast baseline fields are not saved here.

### `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/AtmosphericUpdateScheduler.java`
Reason: preserves scheduler timing needed for continuing atmospheric evolution.

Saved fields:
- Last active update tick
- Last passive update tick
- Passive region update queue

In-flight async flags are reset on load.

### `src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/CycloneManager.java`
Reason: active cyclones are mutable atmospheric agents that directly modify regional atmosphere state.

Saved fields:
- Active cyclone ids
- Center position
- Radius
- Intensity
- Core pressure drop
- Remaining lifetime
- Internal tick counter
- Last spawn tick
- Last midnight tick

This does not modify tornadoes or hurricanes.

### `src/main/java/net/Gabou/projectatmosphere/modules/ocean/OceanBasin.java`
Reason: ocean basins hold mutable ocean-atmosphere memory.

Saved fields:
- Basin id
- Ocean cells
- Influence weights
- Base surface temperature
- Base humidity
- Base pressure
- Surface temperature
- Deep temperature
- Humidity reservoir
- Thermal memory
- Multi-day anomaly
- Pressure offset
- Wind bias

### `src/main/java/net/Gabou/projectatmosphere/modules/ocean/OceanBasinManager.java`
Reason: restores saved ocean basins and prevents async basin detection from overwriting restored state.

Changes:
- Saves all current basins.
- Restores saved basins.
- Reattaches existing ocean influence modules after load.
- Adds a detection version guard so a late async scan cannot replace restored data.

## Mutable Atmospheric Data Identified

Per-region mutable state:
- Temperature
- Humidity
- Pressure
- Wind
- Cloud cover
- Cloud water
- Rain intensity
- Sunlight
- Cyclone cloud/rain floors
- Daily observed temperature/humidity/pressure profiles

Scheduler mutable state:
- Active update timestamp
- Passive update timestamp
- Passive update queue

Cyclone mutable state:
- Active cyclone list
- Cyclone position, strength, pressure drop, lifetime, and tick counter
- Cyclone spawn and midnight timing

Ocean mutable state:
- Basin reservoirs
- Thermal memory
- Pressure offset
- Multi-day anomaly
- Wind bias
- Influence weights

## Fields Previously Lost On Restart

Before this task, restart rebuilt live atmosphere from forecast defaults and runtime initialization.

Lost fields included:
- Current temperature drift
- Current humidity drift
- Current pressure drift
- Current wind value
- Current cloud cover
- Current cloud water
- Current rain intensity
- Current sunlight
- Cyclone visual floors
- Daily observed profiles
- Active/passive atmospheric update timing
- Passive update queue
- Active cyclone agents
- Ocean basin reservoirs and memory

Base forecast and native cloud regions were already persisted separately.

## Save Flow

On server stop:
1. `ForecastOrchestrator.onServerStop` calls `AtmosphericStateSavedData.snapshot(level)`.
2. The live atmosphere saved data snapshots all current `RegionAtmosphereState` instances.
3. Scheduler, cyclone, and ocean basin runtime state are saved into separate sections.
4. Forecast data is then saved by the existing forecast storage path.
5. Native cloud regions continue saving through their existing cloud-region saved data.

## Load Flow

On server start:
1. Forecast data loads exactly as before.
2. Dynamic systems initialize exactly as before.
3. `AtmosphericStateSavedData.restore(level)` overlays saved mutable state onto already-created forecast-backed `RegionAtmosphereState` instances.
4. Scheduler, cyclone, and ocean basin runtime state are restored.
5. Forecast generation is not invoked by the live atmosphere restore path.

## Migration Strategy

Old worlds have no `project_atmosphere_live_atmosphere` saved data.

Behavior for old worlds:
- Missing live atmosphere save is treated as empty.
- The world continues using the existing behavior: live atmosphere initializes from forecast.
- The new live atmosphere file is created on the next clean server stop.

No forecast data is duplicated into the live atmosphere save. The save contains region keys and mutable overlays only.

## Memory And Disk Impact

Memory:
- Restore reads one Minecraft `SavedData` payload.
- Region state restore iterates the existing atmospheric registry.
- No persistent extra runtime copy is kept after applying the payload.

Disk:
- Each atmospheric region stores scalar mutable fields, one wind vector, and three 240-float daily observed profiles.
- Cyclone and ocean sections scale with active cyclones and detected ocean basins.
- Forecast arrays are not duplicated, which keeps this smaller than forecast persistence.

## Validation

Code validation performed:

```powershell
.\gradlew.bat compileJava
```

Result:
- Build succeeded.
- Existing project warnings remain, unrelated to this task.

Runtime validation still needed in Minecraft:
- Stop server.
- Restart server.
- Confirm forecast remains identical.
- Confirm atmosphere state remains identical.
- Confirm cloud regions remain identical.
- Confirm no forecast regeneration occurs.
- Confirm no atmosphere reset occurs.

## Scope Boundary

Not started:
- Season drift
- Forecast redesign
- Storm abstraction
- Tornado migration
- Hurricane migration
- Blizzard work
- Rendering changes
- Cloud spawning changes
- Cloud evolution changes
