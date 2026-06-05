# Existing Cloud Lifecycle Ownership

## Project Atmosphere

PA currently owns:

- weather conditions
- forecast generation
- regional atmosphere state
- storm scoring
- spawn policy
- telemetry
- cloud-to-weather projection logic
- cloud debug snapshot input

PA does not currently own the full cloud-region lifecycle as a standalone simulation model.

What PA does not own:

- the authoritative cloud region entity type
- cloud region tick and removal semantics
- cloud region persistence format
- cloud region movement as the primary source of truth
- cloud region growth/decay as a dedicated PA state object

## SimpleClouds

SimpleClouds currently owns:

- `CloudRegion`
- region creation
- region ticking
- region movement
- region persistence
- region removal
- spawn configuration
- cloud type catalog behavior

This is the real lifecycle owner for actual cloud objects.

## CloudManager

`net.Gabou.projectatmosphere.modules.atmosphere.CloudManager` is a bridge/controller.

It owns:

- region-to-weather sampling
- cloud footprint projection back into atmosphere regions
- cloud birth and last-seen telemetry
- cloud type tracking
- spawn attempt timing

It does not own:

- the underlying cloud object lifecycle
- region creation semantics
- cloud persistence format

So it is a projection layer, not a region simulation layer.

## SimpleCloudSpawner

`SimpleCloudSpawner` owns:

- spawn selection policy
- weather sampling for spawn decisions
- cloud type choice from severity
- async spawn request preparation

It does not own:

- the actual cloud object model
- movement or age
- runtime lifetime
- radius evolution

It prepares spawns, then delegates to SimpleClouds via `SimpleCloudsCompat`.

## SimpleCloudsCompat

`SimpleCloudsCompat` owns:

- adapter logic into SimpleClouds
- `CloudRegion` creation
- movement direction and rotation setup
- speed and acceleration setup
- radius assignment
- spawn-region selection logic

It does not own:

- a PA-side cloud-region simulation contract
- cloud lifecycle policy as a durable model

It is a bridge and configuration layer, not a backend simulation layer.

## resources/data/simpleclouds

The SimpleClouds resources own:

- cloud type catalog data
- cloud spawn profile data
- grow and exist ticks
- radius and speed distributions
- `moves_to_player`
- storminess/weather_type mapping

These resources define how SimpleClouds clouds behave, but they are static definitions. They are not runtime cloud-region state.

## Bottom line

SimpleClouds currently owns cloud position and lifecycle instead of PA.

PA owns the weather that influences cloud behavior, but not a dedicated cloud-region simulation object layer that sits between weather values and rendering.

