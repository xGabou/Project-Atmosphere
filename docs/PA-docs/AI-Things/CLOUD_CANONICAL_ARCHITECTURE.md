# Canonical Cloud Architecture

## State before remediation

Project Atmosphere had three parallel cloud representations:

1. `CloudRegionState` / `CloudClusterState` were persistent weather simulation
   objects. They owned cloud type, morphology, regional weather and severe-weather
   semantics.
2. `CloudFieldRuntimeManager` collected those regions/clusters (plus weather and
   manual sources), created persistent `CloudField` objects, evolved them toward the
   source every server tick, and synchronized `CloudFieldSnapshot` objects. The
   volumetric renderer preferred these snapshots.
3. `CloudCellSimulationManager` independently spawned, moved, merged, split and
   synchronized another population of clouds from regional atmosphere values. These
   cells were normally hidden whenever fields rendered, but server density queries,
   GPU analytics and native tornadoes still consumed them.

Consequently, the visible cloud, the server density result and the tornado parent
could describe different weather at the same position.

## Canonical contract selected

- Persistent weather truth: `CloudRegionState` / `CloudClusterState` and the
  atmospheric region sampled by the backend collector.
- Render derivation: `CloudField` and `CloudFieldSnapshot`. A field may smooth its
  source for presentation, but it may not invent an unrelated weather population.
- Render detail: deterministic `CloudletLayout` instances derived from a field seed.
  Cloudlets are not independently synchronized or simulated.
- Severe-convection derivative: `CloudCell` objects are reconciled from the current
  fields and retain only the additional short-lived state needed for funnels,
  classification, physics and analytics. Explicit command/debug tornado cells may
  exist without a field, but normal autonomous cell spawning is not a second weather
  simulation.
- Client density/whiteout truth: the representation that successfully completed the
  current frame composite, published through `ClientCloudVisualDensity`.
- Server density truth: the reconciled severe-convection/cell derivative until a
  direct field-density evaluator is shared server-side.

## Data ownership table

| Concern | Owner | Derived consumers |
| --- | --- | --- |
| Persistent weather/type | region and cluster state | field backend source |
| Temperature/humidity/pressure | atmospheric region | field source and evolution |
| Render mass and wind | `CloudField` | snapshots, cloudlets, visual density |
| GPU volume | `VolumetricRenderCell` | weather map and raymarch |
| Funnel lifecycle/physics | reconciled `CloudCell` derivative | funnel uniforms and entity forces |
| Client camera whiteout | last composited visual frame | fog and camera effects |

## Synchronization contract

- Field snapshots carry the render-authoritative mass state.
- Cell packets carry only the reconciled severe-convection derivative and explicit
  debug phenomena; clients must not select cells over available fields for the base
  cloud mass.
- Interest filtering and delta suppression are applied independently to both packet
  streams. A full snapshot remains mandatory on join, dimension change and explicit
  resynchronization.

## Transitional limitations

- The field type/morphology contract still needs to be propagated through
  `CloudField`, `CloudFieldSnapshot`, packet encoding and the volumetric weather map.
- Server density currently evaluates reconciled cells rather than the exact cloudlet
  envelope used by the GPU.
- Hurricane/Simple Clouds integrations keep their own renderer-specific structures;
  they are adapters, not additional Project Atmosphere weather truth.
