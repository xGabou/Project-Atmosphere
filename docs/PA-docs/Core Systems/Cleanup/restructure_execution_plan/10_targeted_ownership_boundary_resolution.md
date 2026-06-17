# 10_targeted_ownership_boundary_resolution

## Step goal
Split clearly owned helper concerns out of the remaining broad support and runtime classes without changing behavior.

## Applied boundaries
- Compat module detection and init logging
- Weather debris token budgeting
- Atmosphere state lookup and legacy index resolution
- Atmosphere telemetry emission
- Region forecast corruption validation

## Remaining classes intentionally not fully split
- `AtmosphericUpdateScheduler`
  - Still broad because the core active/passive update flow remains central, but its telemetry path is now delegated.
- `RegionForecastOrchestrator`
  - Still broad because loading, fallback, and regeneration all live in one orchestration surface.

## Next rule
- Only split further if another clear helper boundary appears without needing behavior redesign.
