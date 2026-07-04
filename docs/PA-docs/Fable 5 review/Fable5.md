\---

name: Dynamic Cloud System Architecture

overview: "A full technical design for a hybrid GPU/CPU cloud system: CPU-authoritative cloud cells splatted into a GPU weather map that drives one global raymarched density field, with GPU analytics read back for merging/classification, real ground shadows, sunset lighting, interior rendering, quality tiers, and a tornado-ready density pipeline."

todos:

&#x20; - id: phase1-noise-lighting

&#x20;   content: Bake Perlin-Worley/Worley 3D noise + blue noise textures; rebuild raymarch shader with real sun lighting (Beer-powder, HG phase, multi-scatter, sky ambient, sunset colors)

&#x20;   status: pending

&#x20; - id: phase2-weather-map

&#x20;   content: Implement weather-map rasterization from cells and single global raymarch pass replacing per-field AABB rendering

&#x20;   status: pending

&#x20; - id: phase3-temporal-tiers

&#x20;   content: Add temporal reprojection, blue-noise jitter, quality profiles (LOW-ULTRA), and frame-time governor

&#x20;   status: pending

&#x20; - id: phase4-cell-sim

&#x20;   content: Build CloudCell model with lifecycle (form/grow/dissolve), altitude-sheared wind motion, and delta network sync

&#x20;   status: pending

&#x20; - id: phase5-analytics-classify

&#x20;   content: Add GPU analytics compute pass with async fence+PBO readback, server-side merge/split hysteresis, and shape-derived cloud classifier

&#x20;   status: pending

&#x20; - id: phase6-shadows

&#x20;   content: Implement GPU cloud shadow map, depth-based post-process ground darkening, and shader pack API exposure

&#x20;   status: pending

&#x20; - id: phase7-interior

&#x20;   content: Couple in-cloud whiteout fog with raymarch, add near-camera detail boost, expose CloudDensityAt gameplay API

&#x20;   status: pending

&#x20; - id: phase8-tornado-hooks

&#x20;   content: Add cell rotation property, funnel SDF slot in density function, and native-backend tornado eligibility

&#x20;   status: pending

isProject: false

\---



\# Dynamic Cloud System — Full Technical Design



\## Why the current system can't get there



The active renderer (\[CloudFieldVolumeRenderer.java](src/main/java/net/Gabou/projectatmosphere/clouds/client/render/field/CloudFieldVolumeRenderer.java) + \[cloud\_field\_volume.fsh](src/main/resources/assets/projectatmosphere/shaders/core/cloud\_field\_volume.fsh)) draws \*\*one AABB box per CloudField\*\*. This is the root cause of every symptom you listed:



\- \*\*Consistency / teleporting\*\*: each field is an isolated box; when fields spawn, despawn, or get culled by the per-frame field cap, whole clouds pop. Merging two boxes can never look seamless.

\- \*\*Shape quality\*\*: density is pure inline hash/value-noise FBM — no Worley/Perlin-Worley, so no billowy cauliflower structure; lighting is a hardcoded light direction with fixed gray/white colors (no sun, no sunset).

\- \*\*Performance ceiling\*\*: N fields = N raymarch passes with overlapping pixels, no temporal reuse, and noise recomputed per sample from scratch.

\- \*\*Tornado dead-end\*\*: a funnel can never "grow out of" a box-bounded field; it needs a shared continuous density field.



\## Core architectural shift



Replace per-field boxes with \*\*one global raymarched density field driven by a weather map that cloud cells are splatted into\*\*. This is the proven Nubis/Horizon-Zero-Dawn architecture, adapted to be \*cell-driven\* so the CPU keeps identity and simulation authority.



```mermaid

flowchart TB

&#x20;   subgraph server \[Server - CPU authority]

&#x20;       Atmo\[RegionAtmosphereState\\nhumidity, instability, wind]

&#x20;       Cells\[CloudCell simulation\\ngrow, shrink, move, merge, split]

&#x20;       Classify\[Classifier\\nshape metrics to cloud type]

&#x20;       Atmo --> Cells

&#x20;       Cells --> Classify

&#x20;   end

&#x20;   subgraph net \[Network]

&#x20;       Sync\[Delta cell sync \~1s\\ninterpolated client-side]

&#x20;   end

&#x20;   subgraph client \[Client - GPU]

&#x20;       Splat\[Weather map rasterization\\ncells splatted to 2D RGBA texture]

&#x20;       Noise\[Baked 3D noise textures\\nPerlin-Worley base plus Worley detail]

&#x20;       March\[Single raymarch pass\\nhalf or quarter res plus temporal]

&#x20;       Analytics\[Cell analytics compute pass\\nasync readback to CPU]

&#x20;       Shadow\[Cloud shadow map pass\\ntop-down density integral]

&#x20;       Splat --> March

&#x20;       Noise --> March

&#x20;       Splat --> Shadow

&#x20;       Splat --> Analytics

&#x20;   end

&#x20;   Cells --> Sync --> Splat

&#x20;   Analytics -.async metrics.-> Sync

&#x20;   Shadow --> Ground\[Post-process ground darkening\\nplus shader pack API]

```



The GPU never invents clouds; it \*amplifies\* CPU cell data into detailed volumes. The CPU never draws; it decides identity, lifecycle, and weather meaning. This keeps multiplayer-consistent clouds (all clients see the same cells) while shapes stay GPU-cheap.



\## 1. Data model — CPU cell simulation (server)



Extend the existing `clouds/` package; keep `CloudRegionManager` orchestration but make \*\*CloudCell\*\* the primary entity (evolving the current `CloudField`/cluster split):



\- \*\*CloudRegion\*\*: a weather-scale mass (km-scale), owns N cells, carries regional humidity/instability budget pulled from \[RegionAtmosphereState.java](src/main/java/net/Gabou/projectatmosphere/modules/atmosphere/RegionAtmosphereState.java) and `WeatherCellManager`.

\- \*\*CloudCell\*\* (new record, \~64 bytes serialized): UUID, position (XZ + baseY), horizontal radii (elliptical, 2 axes + rotation), vertical extent (baseY..topY), density core, edge softness, growthRate, verticalDevelopmentRate, rotation (mesocyclone scalar, for tornado future), age, lifecycle phase (FORMING, MATURE, DISSIPATING), parent region ID.

\- \*\*Lifecycle\*\* (reuse controller pattern from `CloudRegionLifecycleController` / `CloudFieldEvolutionController`):

&#x20; - \*Formation\*: humidity + instability above threshold spawns FORMING cells with small radius, density ramping in over 30–90 s — clouds fade in, never pop.

&#x20; - \*Growth\*: instability drives `topY` upward (vertical development); humidity drives radius; cells sample their region budget so total condensed water is conserved (one growing storm cell starves neighbors — natural spacing).

&#x20; - \*Movement\*: integrate wind from `WindEngine` per-cell with altitude shear (cells at higher `topY` drift faster/rotated) — kills the "sliding sheet" feel.

&#x20; - \*Merge\*: when GPU analytics (section 4) report sustained density-bridge overlap between two cells, CPU merges identities: larger cell absorbs, radii/mass conserved, smooth because the \*visual\* merge already happened in the density field.

&#x20; - \*Split\*: when analytics report a cell's density field has two disconnected lobes for N seconds, spawn a child cell at the second lobe centroid.

&#x20; - \*Dissipation\*: density core decays toward 0 over 60–180 s, edge softness rises — smooth dissolve, then despawn when contribution < epsilon.



\## 2. Weather map — the CPU→GPU bridge



Each frame the client rasterizes interpolated cells (via the existing \[ClientCloudFieldCache.java](src/main/java/net/Gabou/projectatmosphere/clouds/client/ClientCloudFieldCache.java) Hermite interpolation/extrapolation, retargeted to cells) into a \*\*weather map\*\*: a 512×512 RGBA16F texture covering \~8–16 km around the camera, camera-snapped to texel grid to avoid swimming.



\- \*\*R = coverage\*\* (max of all cell footprints, smooth-max so overlaps merge seamlessly)

\- \*\*G = cloud base height\*\* (normalized)

\- \*\*B = cloud top height\*\* (vertical development)

\- \*\*A = "energy"\*\* (storminess/turbulence: darker bases, more erosion, anvil spread)



Splatting: instanced quads with a falloff fragment shader on GL 3.2 (Low mode), or a small compute pass on 4.3+. Cells are \*elliptical Gaussians with FBM-perturbed edges seeded per-cell UUID\*, so each cell has a stable, unique silhouette that translates coherently with the cell — this is what makes motion read as "cloud drifting" instead of noise scrolling.



A second low-frequency channel (or mip) encodes region-scale coverage for stratus sheets/overcast, so the same pipeline does both puffy cumulus (cell-driven) and layered decks (region-driven).



\## 3. Rendering — single raymarch pass



New shader pair replacing the per-field volume shaders; the legacy fullscreen path (\[cloud\_volume.fsh](src/main/resources/assets/projectatmosphere/shaders/core/cloud\_volume.fsh), `CloudRaymarchRenderer`, `CloudRenderTargetManager` with its ping-pong history buffers, `CloudLightingBridge`) is the skeleton to resurrect and rebuild on.



\*\*Density function\*\* (the fix for shape quality):

\- Bake once at startup: 128³ RGBA8 \*\*Perlin-Worley base\*\* texture + 32³ \*\*Worley detail\*\* texture (generated on GPU into a 3D texture via layered FBO passes; cached to disk optional). Texture fetches replace \~90% of current per-sample ALU noise — a large speedup \*and\* a large quality gain (Worley gives the cauliflower look value noise cannot).

\- `density(p) = heightGradient(weatherMap.GB, p.y) × remap(baseNoise, 1 - coverage) − detailErosion(edge-only)`, with erosion strength scaled by weather map A. Detail texture scrolls with local wind + a curl-noise offset for internal churn.

\- Height gradient shapes profiles: flat stratus, rounded cumulus, anvil top when B (top) is high and A (energy) is high — shape emerges from cell data, not from a type enum (your "shape defines type" requirement, enforced at the density level).



\*\*Raymarch loop\*\*:

\- Ray from camera through the cloud slab (baseY..maxTopY from the weather map region), clipped by scene depth.

\- Cheap coverage pre-test: sample weather map along ray at 4–6 points; skip empty rays entirely (most of the sky most of the time).

\- Adaptive stepping: large steps in empty space, drop to fine steps on first density hit, re-expand after exiting (standard Nubis "cloud march" pattern).

\- Blue-noise jitter per pixel per frame (bake a 128² blue-noise texture) instead of white-noise hash — this is what makes low step counts look clean under temporal filtering.



\*\*Lighting model\*\* (the fix for realism and sunsets):

\- \*\*Sun transmittance\*\*: 5–6 tap secondary march toward the real sun/moon direction (from `CloudLightingBridge.resolveSunDirection`, already written), with cone-spread taps.

\- \*\*Beer–powder\*\*: `exp(-τ) × (1 − exp(-2τ))` for the bright-edge/dark-crease look.

\- \*\*Multi-scattering approximation\*\* (Hillaire/Frostbite octaves): 3 octaves of attenuated extinction — this is what makes thick storm clouds glow instead of going black.

\- \*\*Phase\*\*: dual-lobe Henyey–Greenstein (forward + backward) — silver lining around the sun, soft backscatter opposite it.

\- \*\*Ambient\*\*: top lit by sky color, bottom by ground/horizon color, both sampled from `AtmosphereSkySampler` — clouds automatically follow biome/time-of-day sky.

\- \*\*Sunsets/sunrises\*\*: sun color from the existing sunset-phase model in `CloudLightingBridge` + vanilla `getSunriseColor`; because low-angle sun light-marches \*through\* more cloud, undersides catch orange/pink while tops stay gray — the physically-motivated sunset look falls out of the model rather than being tinted on. Add a per-sample atmospheric transmittance term (cheap analytic Rayleigh/Mie toward sun) so distant clouds redden more than overhead ones.



\*\*Resolution + temporal\*\*:

\- Render at ¼–½ resolution into the existing `CloudFieldRenderTargetManager`-style FBO.

\- \*\*Temporal reprojection\*\*: reproject last frame's cloud buffer with the previous view-projection matrix, blend \~90/10, reject on depth/coverage disocclusion. The legacy `resolveTemporalTarget()` scaffolding exists; finish it. This is the single biggest performance win and also smooths jitter noise.

\- Depth-aware upsample to full res (keep the existing `cloud\_field\_composite.fsh` approach, add history).



\## 4. GPU analytics → CPU decisions (the hybrid loop)



A small compute pass (GL 4.3+, Medium+ only) runs every \~10–20 ticks, \*not\* per frame:



\- Input: weather map + cell list (UBO/SSBO of up to \~256 cells).

\- Per cell, accumulate: integrated density (mass proxy), actual footprint area vs nominal radius, centroid drift, per-pair overlap density with its K nearest neighbor cells, max column height reached, lobe count (2-pass connected-component-lite on a 64×64 crop around the cell).

\- Output: one 32–64 byte struct per cell into a small SSBO.

\- \*\*Async readback\*\*: copy to a PBO, insert a fence, poll the fence on later frames (never glGetTexImage-stall — the docs show an NVidia crash history here; the removed GPU shadow upload is the cautionary tale). Latency of 3–10 frames is fine because the CPU consumes this at simulation cadence.

\- Client sends a compact digest to the server (or in single-player, hands it over directly); server treats analytics as \*advisory evidence\* with hysteresis — e.g. "overlap density > threshold for 5 consecutive reports → commit merge". Server stays authoritative so multiplayer clients can't diverge; without any client reports (dedicated server, no players nearby), the CPU falls back to analytic overlap estimates from cell geometry.



On Low mode (no compute), skip analytics entirely and use the CPU analytic fallback — merging still works, just less shape-aware.



\## 5. Classification — types derived from shape



New `CloudClassifier` (server, runs on cell/region metrics every few seconds). Types are \*\*labels computed from measured properties\*\*, never inputs to shape:



\- Inputs per cell: base altitude, vertical extent ratio (top−base)/radius, density, growth rate, footprint area, region coverage fraction, instability, rotation.

\- Rules (fuzzy scoring, highest wins): small + shallow + scattered → \*Cumulus humilis\*; growing vertical extent → \*Cumulus congestus\*; tall + energetic + anvil → \*Cumulonimbus\*; wide + flat + high regional coverage → \*Stratus/Stratocumulus\*; high base + thin → \*Cirrus-like\* (rendered by the region-scale channel).

\- Classification feeds \*consumers only\*: weather gameplay (rain/lightning from cumulonimbus cells via `WeatherCellManager`), audio, HUD/forecast text, tornado eligibility, and the weather map A-channel energy value. Replaces the current `CloudTypeRegistry`-drives-morphology direction (\[CloudMorphologyGenerators.java](src/main/java/net/Gabou/projectatmosphere/clouds/simulation/CloudMorphologyGenerators.java)); the JSON registry survives as classifier thresholds + per-type gameplay hooks.



\## 6. Cloud shadows



\- \*\*Shadow map pass\*\* (GPU, cheap): render the weather map's density integrated top-down into a 512² single-channel transmittance texture, offset along the sun direction projection. On Low mode: derived analytically from the weather map coverage channel (no extra pass). Update every 2–4 frames.

\- \*\*Ground application (vanilla-safe)\*\*: a fullscreen post pass after terrain — reconstruct world position from the depth buffer, project onto the shadow map along sun dir, multiply scene color by transmittance (tinted slightly blue, floor \~0.55 so shadows never go black). Spatially correct soft moving shadows with zero terrain re-render, and no lightmap hacks. Replaces the disabled `CloudTerrainShadowRenderer`/`FallbackDarkeningPass` GPU path — but this time GPU-resident end to end (no CPU grid upload, avoiding the documented NVidia crash).

\- \*\*Shader pack compatibility\*\*: when Iris/Oculus is active (`ClientShaderPipelineHelper` already detects this), disable PA's post pass and instead expose the shadow/transmittance texture + sun projection via a small public API (extend \[CloudShadowMapAccess](src/main/java/net/Gabou/projectatmosphere/clouds/api/CloudShadowMapAccess.java) with a GPU texture handle + docs), so packs can consume real coverage instead of faking it. The CPU 64×64 grid stays for gameplay queries (entity effects, temperature).



\## 7. Interior rendering (flying through clouds)



\- Raymarch already starts at the near plane when the camera is inside the slab — no special case for entry, so no pop at the boundary.

\- \*\*In-cloud density boost\*\*: within \~200 m of the camera, add one extra detail-noise octave and shorten step size (budget shifted from far steps, which are fogged anyway) so the interior reads as churning volume, not uniform fog.

\- \*\*Whiteout integration\*\*: compute local extinction at the camera each frame (one density sample); drive Minecraft fog start/end + fog color (reuse the Simple Clouds whiteout handler pattern in `SimpleCloudsWhiteoutFogHandler` for the native path) so terrain/entities fade correctly \*inside\* the visual cloud, matching the raymarch exactly.

\- Interior lighting: inside dense cloud, ambient term dominates and directional light collapses — done by the multi-scattering model automatically; add subtle brightness pulsing near storm cells (lightning glow hook already exists in config).

\- Expose `CloudDensityAt(worldPos)` CPU-side (analytic cell evaluation, no GPU roundtrip) for gameplay: plane turbulence, icing, visibility AI, audio muffling.



\## 8. Performance modes



Quality profile table (extend \[CloudFieldQualityProfile.java](src/main/java/net/Gabou/projectatmosphere/clouds/client/render/field/CloudFieldQualityProfile.java)), auto-detected default via the existing `SystemProfile`:



\- \*\*LOW\*\* (GL 3.2, integrated GPUs): ¼ res, 24 steps, 3 light taps, no temporal (or naive smear), no compute analytics, analytic shadows, 256² weather map, 64³ base noise.

\- \*\*MEDIUM\*\* (default): ½ res, 40 steps, 5 light taps, temporal reprojection, compute analytics, shadow pass every 4 frames, 512² weather map.

\- \*\*HIGH\*\*: ½ res, 64 steps, 6 light taps + cone spread, 3 scattering octaves, shadow pass every 2 frames.

\- \*\*ULTRA\*\*: ¾–full res, 96 steps, higher-res noise (256³ base), per-frame shadows, extra detail octave.

\- Cross-cutting: coverage pre-test early-out (biggest saver on clear days), distance-based step growth, and a frame-time governor that drops one tier's step count when cloud GPU time (existing `CloudGpuTimer`) exceeds budget for N frames.



\## 9. Tornado readiness (design now, build later)



Because all clouds share one density field, a funnel is just \*\*another density contributor\*\*, not a separate object:



\- `CloudCell.rotation` accumulates on tall, energetic cells (mesocyclone). High rotation + classifier says cumulonimbus → eligible for tornado (replaces the Simple-Clouds-region gate in `TornadoManager` for the native backend).

\- Funnel = analytic SDF (curved axis spline from cell base to ground, radius profile) evaluated in the \*same\* raymarch density function, combined with the cell's base via \*\*smooth-min union\*\* — the wall-cloud lowering and funnel visually grow out of the parent cell, and the funnel inherits the cell's noise/erosion so the material matches.

\- Precursor stages fall out for free: increase rotation → lowered rotating base (density pulled down by the SDF blend) → condensation funnel → touchdown. Parent cell moves → funnel base moves with it.

\- The existing `TornadoInstance`/`TornadoWindModel` simulation plugs in as the SDF's spline/intensity source; only the \*visual\* ownership changes for the PA-native backend.



\## 10. Backend coexistence and networking



\- Simple Clouds loaded → SC owns visuals exactly as today (\[CloudBackendResolver](src/main/java/net/Gabou/projectatmosphere/clouds/backend/CloudBackendResolver.java) unchanged); all of the above is the PA\_NATIVE path. The cell simulation still runs server-side data-only where SC needs weather inputs, matching current behavior.

\- Networking: replace `SyncCloudFieldsPacket` payload with cell records; send full snapshot on login/dimension change, then deltas (changed/added/removed cells) every 20 ticks — cheaper than today and scales to more cells. Client interpolation layer (Hermite + wind extrapolation) is retained nearly as-is.

\- Migration: build the new renderer behind a config flag alongside the current one; the CloudField store already isolates simulation from rendering, so the weather-map renderer can consume existing snapshots on day one, then the cell model replaces fields underneath.



\## Implementation roadmap (each phase is shippable)



1\. \*\*Noise + lighting upgrade in place\*\* — bake Perlin-Worley/Worley 3D textures + blue noise; wire real sun direction/color + Beer-powder + HG phase + ambient sky into a rebuilt raymarch shader. Immediate visual jump, no architecture change.

2\. \*\*Weather-map renderer\*\* — cell splatting pass + single global raymarch replacing per-field boxes; coverage early-out; depth composite. Fixes consistency/merging visuals.

3\. \*\*Temporal + performance tiers\*\* — reprojection, blue-noise jitter, quality profiles, frame-time governor.

4\. \*\*Cell simulation v2\*\* — CloudCell model, lifecycle (form/grow/dissolve), altitude-sheared wind motion, delta sync.

5\. \*\*GPU analytics + merge/split + classifier\*\* — compute pass, async readback, server hysteresis decisions, shape-derived typing.

6\. \*\*Shadows\*\* — GPU shadow map + depth-based post darkening + shader pack API.

7\. \*\*Interior polish\*\* — whiteout fog coupling, near-camera detail, density query API.

8\. \*\*Tornado hooks\*\* — rotation property, funnel SDF slot in the density function, native-backend eligibility (full tornado visuals as its own later project).



\## Key risks



\- \*\*Async readback portability\*\* (AMD/Intel/old NVidia): strictly fence+PBO, never stall; analytics are advisory so a broken readback degrades to the CPU fallback silently.

\- \*\*Iris/Oculus interaction\*\*: core-shader replacement may conflict with pack pipelines; keep `shaderSafeMode` as the master off-switch for post passes and temporal history.

\- \*\*Weather map scrolling artifacts\*\*: must be camera-texel-snapped and cells splatted in world space, or clouds will swim; addressed in phase 2 acceptance criteria.

