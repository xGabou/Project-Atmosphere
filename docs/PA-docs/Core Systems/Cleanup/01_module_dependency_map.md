# Module Dependency Map

This map groups the project into architectural dependency zones rather than trying to list every class link.

| Module group | Depends on | Depended on by | Notes |
|---|---|---|---|
| Core climate | `api/`, `util/`, `modules/region/`, `modules/core/` | Atmosphere runtime, weather resolution, wind, storm systems | Source of weather/state concepts; should stay backend-owned |
| Atmosphere runtime | Core climate, wind, weather resolution, utilities | Client caches, cloud integration, tornado/hurricane systems | Runtime atmosphere is the bridge between climate and gameplay effects |
| Weather resolution | Core climate, storm, region model | Managers, cloud selection, client smoothing | Coarse weather phase logic should stay backend-owned |
| Wind | Core climate, pressure, temperature, region forecast | Tornado, hurricane, cloud integration, client sync | Wind is one of the cleanest future renderer inputs |
| Temperature | Region model, climate generators | Weather resolution, seasonal systems, client caches | Mostly backend-owned and mostly clean |
| Humidity | Region model, climate generators | Weather resolution, atmosphere runtime, cloud integration | Mostly backend-owned; visual consumers should not own it |
| Pressure | Region model, climate generators | Weather resolution, storm logic, wind | Should remain a backend signal, not a renderer concern |
| Storm | Wind, weather resolution, atmosphere runtime | Tornado, hurricane, cloud integration, client render state | Storm severity and lifecycle are good future snapshot inputs |
| Tornado | Storm, wind, cloud integration, network sync | Client renderers, debug paths, compatibility hooks | Broad but relevant to cloud renderer work |
| Hurricane | Storm, atmosphere runtime, cloud integration, network sync | Client renderers, Simple Clouds hooks, fog/lighting | Broad and currently one of the highest-risk zones |
| Cloud integration | Weather, atmosphere runtime, wind, resources | Client renderers, Simple Clouds compatibility, debug tooling | This is the future renderer seam, but ownership is still fuzzy |
| Simple Clouds compatibility | Cloud integration, wind, resources, client render hooks | Mixin hooks, renderers, cloud spawn logic | Important adapter layer, currently too policy-heavy |
| Client cache | Network sync, API, atmosphere runtime, tornado/hurricane state | Client rendering, HUD/effects, fog | A future cloud render cache should live here or beside it |
| Client rendering | Client cache, fog, mixins, resources/shaders, compat | Actual draw pipeline, diagnostics, HUD overlays | Current render path is a mixture of bridge logic and debug behavior |
| Fog | Atmosphere runtime, client cache, resources/shaders | Renderers, sky/whiteout effects, shader inputs | Fog is partly client-owned and partly render-owned; boundary is not final |
| Network sync | API, storm/tornado/hurricane state, atmosphere status | Client cache, client tick, render-state consumers | Packets should remain transport only |
| API | Core climate, wind, storm, weather values | External integrations, packets, client state, docs | The cleanest place for stable data contracts |
| Utilities | Cross-cutting helpers across almost all modules | Many subsystems | Useful but can hide ownership if overused |
| Mixins | Client rendering, Simple Clouds, engine hooks | Renderer integration and diagnostics | Necessary, but should stay thin |
| Resources and shaders | Renderers, cloud integration, fog | Client visuals, debug testing, compatibility features | Assets are a major future renderer dependency but not code ownership |

## Dependency Pattern Summary

- Cleanest dependencies: API, wind engine, snapshot objects, packet transport objects.
- Confusing dependencies: managers, compat, broad client tick coordination, render pipelines.
- Highest-risk dependencies: anything that lets render code reach back into live simulation classes.

