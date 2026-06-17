# Cloud Renderer Preparation Impact

## Which Modules Are Ready

- `modules/wind/` is already a strong source of backend wind data.
- `modules/weather/` is reasonably clear for coarse weather resolution.
- `modules/region/` and `modules/atmosphere/` already hold the climate state that a future renderer will need.
- `api/` already has value objects that can support a future render contract.
- `network/` already has snapshot-style sync objects for tornadoes, hurricanes, and atmosphere status.
- `client/hurricane/` already shows a useful cache pattern.

## Which Modules Are Confusing

- `manager/`
- `modules/atmosphere/`
- `modules/tornado/`
- `modules/hurricane/`
- `client/`
- `client/render/`
- `compat/`
- `util/` where it carries policy rather than helper logic

## Which Modules Need Documentation Only

- `modules/region/`
- `modules/wind/`
- `modules/temperature/`
- `modules/humidity/`
- `modules/pressure/`
- `api/`
- `resources/data/projectatmosphere/`
- `resources/data/simpleclouds/`

## Which Modules Need Cleanup Before PA Driven Clouds

- `manager/`
- `modules/atmosphere/`
- `modules/tornado/`
- `modules/hurricane/`
- `client/`
- `client/render/`
- `compat/`

## Which Modules Should Not Be Touched Before Fake Cloud

- `client/render/SimpleCloudsTornadoRenderer`
- `client/render/SimpleCloudsHurricaneRenderer`
- `compat/SimpleCloudsCompat`
- `manager/AtmosphereManager`
- `manager/ForecastOrchestrator`

## Hard To Trace Values Or Systems Because Of Organization

- Cloud cover and cloud density are represented in several places.
- Storm state appears in managers, snapshots, client caches, and network packets.
- Fog and whiteout logic are split between client state and render helpers.
- Simple Clouds compatibility has both adapter behavior and policy behavior.
- Debug render behavior is mixed into normal render packages.
- Some utility classes are effectively mini-services rather than utilities.

## Cloud Renderer Impact Summary

The current organization is good enough to support a future fake debug cloud, but not yet good enough to let a real PA-driven cloud renderer depend on the codebase without additional boundary work. The key issue is not missing data; it is that source-of-truth boundaries are not documented tightly enough yet.

