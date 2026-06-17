# Humidity Moisture Budget - Stages

Current stage: **4/4 completed**

This file tracks the implementation rollout for the humidity moisture-budget rework described in `doc/humidity-moisture-budget-study.md`.

## Stage 1/4 - Instrumentation and scaffolding

Status: **Completed**

Goal:
- make the current system observable before changing the runtime physics.

Scope:
- introduce a stable `HumidityBudget` structure;
- expose the target forecast humidity at runtime from `RegionAtmosphereState`;
- export humidity-budget telemetry samples with the current humidity delta decomposition;
- establish stage tracking for the rework.

Delivered:
- `HumidityBudget` runtime scaffold;
- `RegionAtmosphereState.getTargetHumidity(dayTime)`;
- `humidity_budget.jsonl` telemetry export;
- stage tracking document.

## Stage 2/4 - Scheduler humidity rewrite

Status: **Completed**

Goal:
- replace the legacy humidity delta equation with an explicit budget update.

Scope:
- compute named source/sink terms instead of an anonymous humidity delta;
- introduce `forecastRestore` toward the daily target curve;
- stop treating rain as a net drying force by default;
- preserve current clamps while changing the internal equation.

Expected outcome:
- wet biomes stop collapsing far below their forecast envelope;
- dry biomes remain dry through their lower target curves and weaker source terms.

Delivered:
- split immutable forecast humidity targets from mutable runtime daily snapshots in `RegionAtmosphereState`;
- introduced `HumiditySourceProfile` and `HumidityBudgetService` for named humidity budget terms;
- replaced the legacy anonymous humidity delta in `AtmosphericUpdateScheduler` with a budget-driven update including `biomeEvaporation`, `rainExchange`, `forecastRestore`, `solarDrying`, and a weak `precipitationSink`;
- stopped the scheduler from restoring humidity a second time via `relaxTowardBase`, keeping the post-step base relaxation only for temperature and pressure.

## Stage 3/4 - Ocean and wind budget integration

Status: **Completed**

Goal:
- integrate cross-system humidity effects cleanly into the budget model.

Scope:
- promote ocean contribution to an explicit `oceanFlux` term;
- promote neighbor advection/mixing to an explicit `windTransport` term;
- avoid double counting against cloud and precipitation logic.

Expected outcome:
- coasts and ocean-facing regions recover humidity faster;
- inland dry regions stay distinct from marine climates.

Delivered:
- added `OceanBasinManager.estimateHumidityFlux(...)` so the scheduler can read basin-driven humidity exchange explicitly;
- added `WindVector.estimateHumidityTransport(...)` so neighbor advection is exposed as a named humidity-budget term;
- moved humidity ownership for ocean and wind out of `AtmosphereFluxInfluence` and `WindVector.update`, preventing double counting while keeping their temperature, pressure, and wind behaviors intact;
- integrated `oceanFlux` and `windTransport` into the scheduler budget for active regions with interval-aware accumulation.

## Stage 4/4 - Cloud-water extension

Status: **Completed**

Goal:
- prepare a richer humidity-to-cloud-to-precipitation pipeline.

Scope:
- optionally add a `cloudWater` stock;
- separate near-surface humidity from condensed cloud moisture;
- allow cloud formation and precipitation to consume and return moisture more explicitly.

Expected outcome:
- cleaner long-term evolution of cloud and rain behavior without collapsing the low-level humidity model.

Delivered:
- added explicit `cloudWater` state to `RegionAtmosphereState`;
- introduced `CloudWaterExchange` and `CloudWaterService` so condensation, re-evaporation, and precipitation draw are modeled as named terms;
- integrated cloud-water exchange into `AtmosphericUpdateScheduler` after the humidity budget step while keeping temperature and pressure behavior unchanged;
- seeded and decayed `cloudWater` through `CloudManager` so visible cloud activity and condensed regional moisture stay aligned;
- extended telemetry with cloud-water fields in `region_forecast_samples.jsonl` and the humidity-budget stream.
