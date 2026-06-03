# Safe Cleanup Plan

| Phase | Goal | Classes involved | What to change later | What not to change | Risk level | Why this helps the cloud renderer |
|---|---|---|---|---|---|---|
| Phase 0 Documentation only | Lock in current ownership understanding | All major subsystems | Write docs, boundary notes, and diagrams | Source code behavior | Low | Prevents accidental refactors based on guesswork |
| Phase 1 Naming and ownership clarification | Make responsibility clearer before changing code | `AtmosphereManager`, `ForecastOrchestrator`, `CloudManager`, `SimpleCloudSpawner`, `SimpleCloudsCompat`, storm managers | Add documentation and comments, maybe later rename broad wrappers | Functional behavior | Low to medium | Makes it obvious where renderer inputs should come from |
| Phase 2 Snapshot and cache boundary | Define the render-facing state model | `TornadoSnapshot`, `HurricaneRenderSnapshot`, `ClientHurricaneStateCache`, future cloud cache | Introduce a documented render snapshot contract later | Simulation ownership | Medium | Gives the renderer one safe input path |
| Phase 3 Backend data cleanup | Reduce duplicated weather concepts | `RegionAtmosphereState`, `WeatherSnapshot`, `AtmosphereClientState`, `AtmosphereFogState`, `WindEngine` | Remove overlap and clarify source of truth later | Weather math itself | Medium | Prevents the renderer from reading inconsistent data |
| Phase 4 Client sync cleanup | Make sync and smoothing predictable | `ClientTickHandler`, packet handlers, client caches | Consolidate the client view-state flow later | Packet formats that are already stable | Medium to high | Stops render state from being mutated in too many places |
| Phase 5 Renderer entry point cleanup | Isolate visual translation from simulation | `SimpleCloudsTornadoRenderer`, `SimpleCloudsHurricaneRenderer`, pipeline mixins, shader wrappers | Separate pass setup, uniforms, and debug behaviors later | Compatibility behavior that already works | High | Gives the future renderer a stable bridge into PA data |
| Phase 6 Shadow and lighting bridge preparation | Prepare cloud shadows and fallback darkening | `AtmosphereFogState`, `SimpleCloudsWhiteoutFogHandler`, shader assets, future cloud render snapshot | Add explicit lighting/shadow hints later | Existing visuals that already work | High | Sets up realistic cloud appearance without mixing it into simulation |

