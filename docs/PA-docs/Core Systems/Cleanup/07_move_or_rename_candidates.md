# Move Or Rename Candidates

These are package-structure suggestions only. No real files should move yet.

| Class | Current package | Suggested package | Reason | Dependencies affected | Risk | Priority | Should move before cloud renderer |
|---|---|---|---|---|---|---|---|---|
| `SimpleCloudSpawner` | `manager` | `compat` or a dedicated `modules/cloud` package later | It is really spawn policy plus cloud selection | `AtmosphereManager`, `SimpleCloudsCompat`, cloud resources | High | High | No |
| `CloudManager` | `modules/atmosphere` | `modules/atmosphere/cloud` later | The current package is too broad for the amount of cloud policy inside | Atmosphere runtime helpers and cloud integration | High | Medium | No |
| `SimpleCloudsWhiteoutFogHandler` | `client` | `client/fog` | It is a fog composition class, not a generic client root class | Client fog state and renderers | Medium | High | Not now |
| `ClientTickHandler` | `client` | `client/tick` or `client/coordination` later | The current package is too flat for the amount of coordination inside | Client state, render helpers, audio, particles | High | High | No |
| `TornadoLateRenderDiagnostics` | `client/render` | `client/render/debug` | It is diagnostic code and should be visually separated | Tornado renderer and diagnostics | Medium | Medium | No |
| `HudRenderTest` | `client/render` | `client/render/debug` | It looks like a testing/debug helper | HUD render testing and diagnostics | Medium | Medium | No |
| `SimpleCloudsRenderDiagnostics` | `client/render` | `client/render/debug` | Diagnostics should be grouped away from normal render pipelines | Renderers and mixins | Medium | Medium | No |
| `TornadoRenderDebugState` | `client/render` | `client/render/debug` | It is debug state, not core render state | Tornado renderer and diagnostics | Low | Medium | No |
| `WeatherType` | `util` | `modules/core` or `compat` later | It behaves more like a cloud/weather mapping contract than a general utility | Cloud selection, Simple Clouds mappings | Medium | Medium | No |
| `AtmosphereFogState` | `client/fog` | Keep here, but document as the client fog cache | The package is already reasonable; the issue is mostly documentation and ownership clarity | Fog handler and renderers | Low | Low | No |
| `HurricaneCloudVolume` | `modules/hurricane` | `modules/hurricane/render` later | It is a render helper, not hurricane simulation core | Hurricane renderer and snapshot types | Medium | Low | No |
| `TornadoSnapshot` | `modules/tornado` | Keep or move to a dedicated `modules/tornado/state` later | It is a transport/read-only state object | Tornado manager and client sync | Low | Low | No |
| `HurricaneRenderSnapshot` | `modules/hurricane` | Keep or move to `modules/hurricane/render` later | Render snapshot concept is good, package is only slightly broad | Hurricane renderer and client cache | Low | Low | No |
| `CloudLibrary` | `modules/core` | `modules/cloud` later | The package name does not make the selection policy obvious | Simple Clouds spawner and compat | Medium | Medium | No |

