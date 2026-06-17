# Recommended Documentation Targets

| Class | Why document it | What questions the documentation should answer | Priority |
|---|---|---|---|
| `AtmosphereManager` | It is one of the broadest coordination classes | What is it allowed to own, and what should stay elsewhere? | High |
| `ForecastOrchestrator` | It hides multiple source-of-truth boundaries | Which parts are runtime, storage, and sync? | High |
| `ForecastGenerator` | It is broader than the name suggests | What is generation, what is caching, and what is fallback behavior? | High |
| `CloudManager` | It mixes atmospheric application and cloud control | Is it a simulation worker or a cloud policy layer? | High |
| `SimpleCloudSpawner` | It contains spawn policy and weather selection | What decides cloud id, spawn location, and spawn timing? | High |
| `SimpleCloudsCompat` | It is the bridge to the external cloud system | What does PA own versus what Simple Clouds owns? | High |
| `TornadoManager` | It mixes live state, snapshots, and persistence | Which fields are authoritative and which are derived? | High |
| `TornadoInstance` | It is one of the most overloaded storm classes | What is simulation state versus render-adjacent state? | High |
| `HurricaneManager` | It carries simulation, sync, and debug flow | What is runtime-only, and what is render-facing? | High |
| `HurricaneInstance` | It contains lifecycle plus render metadata | Which values are part of the storm, and which are just visuals? | High |
| `ClientTickHandler` | It coordinates many client behaviors | Which actions belong here and which belong in dedicated clients? | Medium |
| `AtmosphereFogState` | It is a likely future cloud-darkening input | What is fog-only, and what should be derived later for clouds? | Medium |
| `ClientHurricaneStateCache` | It is a good cache boundary worth preserving | What is cached, interpolated, or fallback-only? | Medium |
| `SimpleCloudsTornadoRenderer` | It is the main current render bridge | Which parts are compatibility, which parts are debug, and which parts are core? | High |
| `SimpleCloudsHurricaneRenderer` | It is the hurricane render bridge | Same as the tornado renderer, but for hurricane passes | High |

