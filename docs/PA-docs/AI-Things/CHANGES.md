# Project Atmosphere — Developer Change Log
This file records functionality additions/removals made during development sessions, annotated with the current version from `gradle.properties` at the time of change.

## Unreleased - Phase 6A cloud lighting and fallback darkening (0.9.0.1-alpha)
- Added `CloudLightingEvaluation` for shared cloud darkness, storm darkness, shadow intensity, and fallback darkening candidate selection from `CloudVisualState`.
- Added `CloudLightingManager` with smoothed read-only player lighting accessors for future shader and Distant Horizons integration.
- Implemented `FallbackDarkeningPass` using conservative fog darkening for all pipelines and optional terrain overlay via existing `CloudTerrainShadowRenderer` when shader-safe paths are not active.
- Added `ClientShaderPipelineHelper` to respect `shaderSafeMode` and external shaderpack detection without modifying Iris or Oculus.
- Extended `CloudVisualStateManager` with `getFallbackDarkeningCandidates` and wired fallback darkening through `CloudRenderer`, `CloudRenderHook`, and `AtmosphereSkySampler`.

## Unreleased - Verification telemetry command (0.9.0.1-alpha)
- Added read-only `/pa debug verify` and `/pa debug verify snapshot` commands that audit forecast, atmosphere, wind, season, weather cells, clouds, morphology, evolution, and persistence state through the telemetry verification package.
- Introduced `VerificationCollector`, `VerificationReport`, `VerificationFormatter`, and `VerificationStatus` under `telemetry.verification` for structured runtime inspection without modifying gameplay systems.

## Unreleased - Project Atmosphere command tree refactor
- Split the `/pa` command surface into feature-first groups for forecast, temperature, humidity, pressure, wind, fog, cloud, tornado, hurricane, system, help, status, and debug.
- Kept the legacy `/pa weatherdebug`, `/pa spawnTornado`, `/pa spawnHurricane`, and `/pa windSpeed` paths alive as compatibility aliases while routing them through the new handlers.
- Standardized command naming and help output so new docs surface the current command tree first and legacy aliases stay at the bottom.

## Unreleased - AMD and Intel Simple Clouds compatibility
- Moved Simple Clouds tornado cloud-carving metadata from the `CloudStorms` SSBO to uniform arrays capped at 16 tornadoes, avoiding an extra SSBO dependency for the tornado path on strict AMD and Intel OpenGL drivers.
- Disabled Project Atmosphere's Simple Clouds storm SSBO allocation on GPUs exposing 16 or fewer shader storage buffer bindings, falling back to uniform tornado cloud carving and disabling hurricane cloud shaping instead of crashing.
- Added `storms.tornado.disableSimpleCloudsTornadoSSBO` so users can force the safer no-SSBO Simple Clouds storm integration path when diagnosing GPU driver issues.

## Unreleased - Ecliptic Seasons forecast integration
- Routed forecast/debug temperature season reads and Simple Clouds rain lifecycle notifications through the shared `SeasonTimeHelper` delegate instead of calling Serene Seasons directly.
- Preferred Ecliptic Seasons when both Ecliptic and Serene are loaded, and fixed the Ecliptic delegate's cycle tick reporting so forecast temperature curves advance from the current solar term position instead of a constant season duration.
- Moved Serene-specific season-change callbacks into the Serene season integration bridge and stopped registering them from the main mod bootstrap.

## Unreleased - Launcher auth guard
- Ported the Identity2 launcher auth guard into Project Atmosphere with a Forge SimpleChannel challenge/reply flow, client-side TLauncher marker detection, strict offline UUID rejection, timeout kicking, and player/IP ban handling when the marker is reported.

## Unreleased - Tornado render performance pass
- Kept client tornado interpolation ticking even while the forecast cache is not marked ready, preventing rendered funnels from freezing at an old position while server-side destruction keeps moving.
- Added rate-limited client tornado snapshot diagnostics behind the existing tornado debug logging option to verify received snapshot positions versus rendered positions.
- Made the non-DH tornado downsample composite stamp sampled tornado depth back into the full-resolution cloud target, preventing later terrain/depth composition from cutting through the already-drawn funnel.
- Moved non-DH downsample terrain occlusion from the low-resolution raymarch into the full-resolution composite, preserving the FPS path while preventing one low-res terrain-depth sample from cutting large chunks out of the funnel.
- Re-enabled non-DH tornado downscaling as a color-only resolution reduction: default/shader-support paths now always use copied transparency depth, while the low-resolution intermediate target stores straight alpha before compositing so non-DH funnels do not disappear from pre-weakened alpha.
- Moved the Simple Clouds tornado volume pass onto a configurable low-resolution render target with an upsample composite shader, defaulting to a 2.5x downsample so tornado raymarching shades far fewer pixels on mid-range GPUs.
- Fixed the downsampled tornado pass to set the viewport to the low-resolution target before raymarching and restore the cloud-target viewport before compositing, preventing camera-dependent tornado placement.
- Disabled the tornado downsample path automatically under Distant Horizons so the tornado follows the same full-resolution depth path Simple Clouds uses for its own DH cloud rendering.
- Softened near-terrain tornado depth rejection and biased close dusty intersections in front of the scene depth so ground-contact funnels no longer get visibly cut open by nearby terrain.
- Expanded in-tornado dusty volume/fog when the camera is inside or near the funnel and added a sparse client-only falling dust curtain on the far side of tornadoes.
- Removed camera-dependent funnel widening so tornado visual size stays fixed by tornado strength/radius, then increased ground-contact padding and inside-funnel whiteout so tornadoes touch terrain more reliably and feel dustier from within.
- Corrected inverted ground-contact height math, anchored the visual tornado base near sampled terrain, added a dense low dust skirt at touchdown, and pushed inside-tornado fog toward a short dusty brown-gray whiteout.
- Separated the tornado shader contributions into distinct wallcloud, connection, ground skirt, and main funnel paths, added a `groundskirt` render debug mode, reduced the terrain depth bias to a small local contact helper, and removed the downsample composite's alpha unpremultiply so semi-transparent dark pixels are no longer exaggerated.
- Changed debug-mask tornado modes to render as opaque field overlays so `density`, `wallcloud`, `connection`, and `groundskirt` remain readable even under Distant Horizons' alternate depth path.
- Changed tornado volume rendering to use depth-aware `LEQUAL` proxy rendering, cull proxy backfaces when the camera is outside the volume, and sample scene depth before raymarching so occluded pixels skip the expensive storm loop earlier.
- Reduced tornado raymarch and first-hit refinement work, tightened the shader influence radius, and shrank the Java-side proxy bounds so the shader spends less time marching empty air around the funnel.
- Removed the tornado transparency mixin hooks because the transparency renderer was a no-op but still copied depth and rebound targets every visible tornado frame.

## Unreleased - Storm spawn and despawn transitions
- Persisted active tornadoes, active hurricanes, and tornado cooldowns into world saved data on server stop and restored them on server start so storms survive world exit/reload.
- Synced restored tornadoes directly to players on login, matching the existing hurricane login sync path instead of waiting for the next periodic broadcast.
- Removed the matrix, altocumulus, altostratus, altostratus_dry, snow, and cumulus_humilis Simple Clouds variants from Project Atmosphere selection, weather mapping, and bundled Simple Clouds spawn definitions.
- Lowered hurricane cloud anchoring and vertical descriptor ranges so hurricane visuals sit closer to the playable weather layer instead of hundreds of blocks too high.
- Kept tornado command spawns in the forming lifecycle instead of forcing the no-cloud path active immediately, so standalone tornadoes now ease in and the removal command lets them dissipate before cleanup.
- Added a hurricane lifecycle with forming, active, and dissipating phases, then kept cyclone-linked hurricanes alive until the fade-out completes instead of dropping them the moment the cyclone disappears.
- Carried hurricane render intensity through the network snapshots and client cache so the custom hurricane volume can grow in and contract out instead of popping in at full size.
- Kept hurricane block destruction gated behind `enableHurricaneDestruction`, so the new lifecycle does not bypass the existing config-driven protection.
- Added `enableTornadoDestruction` and wired it through the config screen, tornado demolition sweep, and tornado glass break queue so tornado block breaking can be disabled separately from tornado spawning.
- Renamed generic shader `noise3` helpers to Project Atmosphere-specific names in the tornado and hurricane fragment shaders to avoid GPU driver GLSL overload conflicts during shader registration.
- Made the Simple Clouds tornado and hurricane render mixin hooks tolerant of released/custom pipeline differences by targeting the cloud final composite pass for transparency and preventing missing optional hooks from crashing the client.
- Merged tornado and hurricane Simple Clouds compute data into a single lazy `CloudStorms` SSBO so Project Atmosphere no longer exhausts 16-slot SSBO binding limits during Simple Clouds startup.
- Capped Simple Clouds 0.7.4 lightning SSBO buffering to the available positive binding slots and reserved binding `0` for Project Atmosphere's storm compute data on 16-binding GPUs.
- Targeted the Simple Clouds lightning buffer cap at both the named and SRG reload method names so it applies in packaged CurseForge/Forge client jars, not only the development runtime.

## Unreleased - Hurricane destructive behavior
- Split hurricane server-side interaction into `HurricaneWindField`, `HurricaneDestructionManager`, and `HurricaneBlockBreakRules`, so `HurricaneInstance` now only delegates wind and destruction work during its tick.
- Reworked hurricane entity forces into a rotating wind field with tangential circulation, inward pull toward the eye, light lift, and ambient storm drift scaling from distance to center and storm intensity.
- Added limited tag-driven hurricane block destruction with explicit protection rules: fragile vegetation can break naturally around the eyewall, leaves can strip more often than logs, trees stay optional behind config, and terrain, ores, portals, command blocks, chests, block entities, and protected areas are excluded.
- Added the `projectatmosphere:hurricane_fragile`, `projectatmosphere:hurricane_tree_damage`, and `projectatmosphere:hurricane_never_break` block tags plus four user-facing common config options: `enableHurricaneDestruction`, `hurricaneDestructionStrength`, `hurricaneDropBrokenBlocks`, and `hurricaneDamageTrees`.

## Unreleased - Distant Horizons storm volume rendering fix
- Fixed the current non-DH no-render regression by disabling the Simple Clouds pipeline frustum as a hard gate for Project Atmosphere tornado volume draws after diagnostics showed valid prepared tornadoes were being rejected before the renderer reached the shader.
- Added rate-limited `[TornadoPath]` diagnostics for the non-DH Simple Clouds tornado hooks and renderer early-return/draw-state decisions so the current non-DH no-render regression can be isolated before further render changes.
- Bound the tornado shader to Simple Clouds' DH-filled cloud target depth in the DH render path so the shader samples the same depth buffer that the cloud framebuffer uses for depth testing.
- Removed the tornado's secondary depth sampler from the DH render path so the shader no longer mixes in cloud-transparency depth while deciding terrain contact.
- Added a `depth` tornado render debug mode that colors shader depth acceptance, scene-depth rejection, low-alpha rejection, and missing-density paths for DH diagnosis.
- Added a `depth_nofb` tornado render debug mode that uses the same shader-side depth colors while disabling framebuffer depth testing, allowing DH tests to distinguish fixed-function depth rejection from shader raymarch/discard rejection.
- Added a `depth_mainfb` tornado render debug mode that temporarily restores Minecraft's main depth attachment for the DH tornado draw, allowing comparison against Simple Clouds' temporary `cloudTarget` depth attachment.
- Added an `occlusion` tornado render debug mode that detaches framebuffer depth and colors whether vanilla main depth, Simple Clouds/DH cloud depth, both, or neither would occlude each tornado pixel.
- Added a `late` tornado render diagnostic mode that skips the Simple Clouds DH hook and draws the tornado at Forge `AFTER_LEVEL`, testing whether terrain is being composited over the earlier DH tornado pass.
- Added a `coverage` tornado render debug mode that colors whether the single late DH pass is missing depth, below the raw-alpha cutoff, inside the DH opacity ramp, solid body coverage, or rejected by scene depth.
- Promoted the late `AFTER_LEVEL` path to the normal Distant Horizons tornado render path after diagnostics showed terrain was compositing over the earlier Simple Clouds DH hook, while keeping explicit depth debug modes on the old hook.
- Made the Distant Horizons tornado render path single-stage by routing debug and normal tornado draws through Forge `AFTER_LEVEL`, removing the old DH post-composite tornado draw, and guarding default/shader Simple Clouds hooks while DH is loaded.
- Limited the late Distant Horizons tornado pass to Minecraft's main scene depth sampler after `coverage` debug showed the remaining seam was shader scene-depth rejection, preventing Simple Clouds' older cloud/DH depth target from falsely cutting the funnel.
- Reintroduced tornado downscaling for the corrected Distant Horizons late pass by raymarching into the low-resolution tornado target and compositing directly back into the same final framebuffer, while keeping debug modes full-resolution.
- Added an alpha-aware 9-tap tornado upsample filter for the downsample composite so aggressive tornado downscaling keeps most of its FPS gain without exposing a blocky low-resolution alpha grid.
- Removed tornado camera whiteout from the fog handler while keeping normal cloud/dynamic fog behavior.
- Split DH and non-DH tornado shader behavior: DH keeps the corrected late main-depth path, while normal non-DH rendering uses the shader's `full` path without enabling Java debug filtering.
- Disabled tornado downscaling for the non-DH forced-full render path so vanilla rendering matches explicit `full` mode more closely, while keeping the confirmed DH late-path downscaling enabled.
- Kept non-DH tornado rendering on the confirmed full-resolution forced-full path after the downsample reintroduction hid the tornado, while preserving DH late-path downscaling.
- Restored the non-DH Simple Clouds hook depth-source behavior from the last known-good state, so the hook still passes cloud-target depth when Simple Clouds reports the tornado path as downsample-capable.
- Fixed DH pipeline selection to use Simple Clouds' active `dhLoaded()` state instead of only checking whether the Distant Horizons mod is installed, preventing the DH pipeline from being forced while the late DH tornado pass is disabled.
- Made tornado debug modes render all prepared tornadoes if no debug storm selection resolves instead of filtering the render order to zero storms.
- Strengthened the DH-only accumulated-body alpha floor so already-rendered far terrain lines do not bleed through dense tornado body pixels after the late render-order fix.
- Tightened the DH-only low-alpha discard and body opacity curve so thin surviving tornado pixels no longer reveal a hard sky/terrain background seam after the late render pass.
- Removed ground-skirt and broad dust-only contributions from the tornado shader's primary cloud density so local ground effects no longer create a large flattened volumetric blob beneath the funnel.
- Added a DH-only tornado alpha floor for depth-accepted body pixels so Simple Clouds' color-only final composite does not let terrain color bleed through the funnel.
- Moved the DH tornado volume draw to Simple Clouds' post-composite main-framebuffer depth-attachment window so it renders with the same DH-filled `cloudTarget` depth Simple Clouds uses for DH-aware world effects, while sampling the detached vanilla main depth texture in the shader so near terrain still occludes the tornado.
- Added tornado shader binding diagnostics that log the live shader name, GL program id, fragment program id/name, sampler names/locations, and requested depth texture ids so DH RenderDoc captures can be matched against the actual `ShaderInstance` state.
- Added GL debug groups around the tornado opaque render pass so RenderDoc and Nsight can isolate the storm frame more easily.
- Added frustum visibility gating to the tornado Simple Clouds pipeline hooks so off-screen tornadoes skip the depth-copy and volume draw work instead of submitting the pass whenever a tornado exists.
- Kept the tornado frustum gate disabled in the Simple Clouds Distant Horizons path because that pipeline's frustum can reject the PA tornado volume even when the storm should render.
- Stopped applying extra ground-contact extension when tornado terrain probing falls back due to missing client samples; in that case the renderer now trusts the synced tornado base instead of inventing a synthetic lower base and opening a visible gap to the touchdown.
- Reworked the non-DH tornado touchdown shaping so extra ground contact comes from dedicated touchdown and lower-stem density terms instead of shifting the main funnel cutoff, avoiding the slab-like lower-body regression while building a denser bridge from the ground contact back into the main funnel.
- Tightened the lower tornado stem profile after the first bridge pass oversized the touchdown, keeping the ground connection continuous while tapering the base back toward a funnel shape instead of a bulb.
- Replaced the separate lower touchdown/stem lobe with a continuous lower-funnel radius profile anchored to the real tornado base, so the column now stays one coherent funnel instead of faking ground contact with a distinct bottom blob.
- Grounded tornado `visualBottomY` from the server terrain heightmap at spawn time and during movement, so the synced funnel base follows actual terrain instead of staying locked to whatever `pos.y` the storm happened to spawn with.
- Removed the tornado shader's touchdown-progress-based lower cutoff lift, so the main funnel body now starts at the grounded base instead of visually hovering well above it even when the synced bottom is correct.
- Removed the tornado upper-body color lift that blended the funnel toward a brighter cloud tint, so the visible column now stays on the same body color instead of picking up a lighter sky-like veil.
- Removed the tornado body color's dependence on `CloudColor`, switching the funnel itself to a neutral dark body tone so thin upper/edge regions no longer inherit a blue sky hue from the cloud tint.
- Removed `BiomeChangeManager`'s nested `RegionTrack` record from the player tick path and replaced it with a direct `Pair<RegionInstanceKey, BlockPos>` state entry, avoiding the runtime nested-class load that was causing `NoClassDefFoundError: BiomeChangeManager$RegionTrack`.
- Rebalanced the tornado output alpha so thin regions can read less transparent without crushing the whole funnel darker; the final displayed alpha is now boosted separately from the raw accumulated body color normalization.
- Removed the experimental hard alpha clamp after it started rendering nearly empty tornado fragments as a full proxy-volume wall; the tornado now uses a curve-based alpha boost with no synthetic opacity floor, which keeps terrain visible and prevents full-screen slabs.
- Raised the tornado's final raw-alpha discard threshold and tied surviving pixels to real first hits before output, which cuts off the lingering distant proxy-volume sheet while allowing the actual funnel body to use a stronger opacity curve.
- Increased tornado opacity specifically in the upper funnel/wallcloud contribution by scaling per-step alpha from `storm.upper`, so the inverted-cone top reads as a solid storm body without thickening the whole lower funnel.
- Removed repeated hurricane semantic snapshot/shape list allocation from the hot path by caching interpolated client semantic snapshots per tick/partial tick and iterating hurricanes/snapshots directly in `HurricaneSemantics` instead of rebuilding `stream().map(...).toList()` lists on every query.
- Filtered out obviously invalid min-build heightmap samples from tornado terrain contact probing and clamped the resolved funnel base to stay near the sampled surface, preventing intermittent `-65` terrain spikes and keeping the tornado base from being buried below uphill terrain.
- Limited tornado secondary depth sampling to the default Simple Clouds pipeline where the cloud target lacks copied world depth; the DH and shader-support paths now rely on their copied cloud/world depth buffer directly instead of also comparing against the main framebuffer depth.
- Fixed the tornado depth source in the Simple Clouds volume pass by snapshotting cloud/depth state into the transparency target before rendering the tornado and sampling that copied depth texture, instead of sampling the same depth attachment the tornado pass is actively writing to.
- Stopped the Simple Clouds tornado volume renderer from inventing a second ground-contact offset below the synced tornado base; it now uses the server/client `renderBottomY` directly and logs `baseOffsetWorld`, which fixes funnels starting around 11-12 blocks under terrain even when the sampled terrain height is correct.
- Refined tornado first-hit depth writes by binary-searching the entry point of the occupied raymarch segment instead of dropping depth several blocks into the funnel body, restored the explicit scene-depth rejection for the `GL_ALWAYS` proxy pass, and added projected-vs-scene depth diagnostics so DH depth mismatches can be verified from the log.
- Made the tornado client ground-contact sampler ignore unloaded client columns and fall back to the server-synced tornado base when DH-visible terrain is outside vanilla client chunk ownership, preventing bogus `-65` terrain heights from stretching the funnel far below the real ground.
- Hooked the dedicated tornado volume render pass into Simple Clouds' `DhSupportPipeline.afterDistantHorizonsRender`, so PA tornado shaders render into the DH cloud framebuffer path instead of only the default/shader-support pipelines.
- Switched hurricanes back to the Simple Clouds compute-mesh integration path by restoring the `CloudHurricanes` uploader/chunk scheduler and disabling the standalone hurricane volume pipeline hooks.
- Made tornado volume rays screen-space stable and increased optical thickness so funnels read as depth-bearing storm bodies instead of flat hatched overlays, without relying on front-face culling that can hide the proxy volume on some pipelines.
- Synced the active `projectatmosphere` compute shader copies with the Simple Clouds-engine storm shaders, so the redirected cloud-region and cube-mesh programs expose the `CloudTornadoes`/`CloudHurricanes` SSBOs and uniforms they upload.
- Select Simple Clouds' DH support pipeline through `DetermineCloudRenderPipelineEvent` when Distant Horizons is installed, ensuring PA tornado and hurricane rendering use the DH-compatible path before mesh generation and render preparation run.
- Stopped the tornado volume raymarch from hard-clipping to copied scene depth, relying on the shader-written first-hit depth for occlusion instead so ground contact is not camera-dependent.
- Kept hurricane mesh samples opaque near/inside the camera by bypassing Simple Clouds' near-origin fade for hurricane volumes, and rotated the whole hurricane density field slowly around the storm center.
- Let tornado proxy-volume fragments run regardless of proxy-box depth, then manually discard only when the first real funnel hit is behind scene depth, preventing terrain behind the storm from cutting off ground contact.
- Stabilized hurricane visibility during camera movement by disabling Simple Clouds chunk dither-fade while hurricanes are active and generating all exposed hurricane mesh faces instead of camera-origin-selected faces.
- Restored terrain-driven tornado contact extension so the volumetric funnel is anchored back down to the sampled surface instead of floating at the synced storm base.
- Gave the tornado shader separate cloud/DH and main-world depth inputs, allowing it to occlude correctly against both DH LOD depth and vanilla chunk depth in the same frame.

## Unreleased - Hurricane reservation cache fix
- Cached client-side hurricane reservation `CloudRegion` objects instead of recreating them on every lookup, which should cut the runaway allocation pressure that was showing up after the tornado-to-hurricane merge.
- Moved the client hurricane render hooks over to the cached snapshot path so the renderer stops walking the live hurricane manager state every frame.

## Unreleased - IDE workspace cleanup
- Stopped tracking `.idea/workspace.xml` in Git so local IntelliJ workspace state stays local instead of showing up as a repo change.

## Unreleased - Hurricane custom volumetric cloud rewrite
- Abandoned the hurricane fake `CloudRegion` ring path and restored a dedicated one-object hurricane render path, so hurricanes no longer depend on injecting several regular Simple Clouds formations into the world cloud list.
- Added an explicit `HurricaneCloudVolume` representation for the custom hurricane cloud body and re-enabled the dedicated pipeline mixins that render hurricanes as bounded world-space volumetric formations.
- Reworked hurricane shape generation so the cloud body now comes from a torus-based volumetric density field in the hurricane shaders, with spiral band and veil layers built around that single density volume instead of approximating the shape through grouped cloud instances.
- Removed the synthetic hurricane cloud-region helpers and cloud-list injection mixin that were previously faking the donut structure through multiple managed cloud objects.

## Unreleased - Hurricane visibility logging and tornado log cleanup
- Removed the automatic tornado runtime, demolition, and scheduled tornado-check log spam so tornado debugging stays on-demand instead of constantly filling `latest.log`.
- Added targeted hurricane debug logging for client snapshot receipt, client synthetic-cloud cache state, and final Simple Clouds cloud-list injection, making it easier to see whether hurricanes are syncing, generating synthetic cloud regions, and actually entering the cloud pipeline.
- Removed the recurring forecast/region debug spam from forecast updates, client forecast readiness, and cloud-region sampling/cover updates so region forecasting no longer floods `latest.log` every few ticks.
- Synced managed synthetic hurricane regions back into the base `CloudRegion` position/radius/transform state that Simple Clouds uses during cloud lookup and mesh-generation heuristics, fixing hurricanes that were injected into the cloud list but still resolved as visually empty.
- Removed the remaining automatic tornado render-hook and tornado packet debug logs so `latest.log` stays focused on hurricane visibility diagnostics instead of unrelated tornado spam.
- Changed hurricanes to resolve their inner/outer cloud types from the active Simple Clouds cloud source instead of relying on hardcoded cloud IDs, and extended hurricane debug logs to report the active cloud mode, indexed cloud-type count, and the resolved synthetic cloud types.
- Reworked the hurricane storm-cloud field to use fewer, much larger Simple Clouds formations that better match the engine's coarse chunk-generation heuristics, and prioritized the nearest client hurricane when capping synthetic cloud formations to the Simple Clouds region limit.

## Unreleased - Hurricane cloud-pipeline refactor
- Replaced the hurricane's standalone PA volume renderer path with a Simple Clouds cloud-data integration, so hurricanes are now injected into `CloudManager.getClouds()` as synthetic storm cloud regions and render through the normal Simple Clouds mesh generator, chunk, shader, fog, depth, and lighting pipeline.
- Added a shared parametric storm-cloud field system with reusable parameters for radius, eye radius, band count, band width, rotation speed, density falloff, vertical thickness, noise scale, and spiral tightness, then used it to generate hurricane eyewall and spiral-band cloud cells instead of hardcoded hurricane mesh volumes.
- Added managed synthetic cloud-region wrappers plus hurricane-side cloud caches on both logical sides, allowing client hurricanes to rebuild matching storm cloud cells from PA snapshots while server queries and client rendering see the same hurricane-shaped cloud field through the normal Simple Clouds cloud getter path.
- Disabled the old hurricane pipeline mixins so the custom hurricane render pass no longer runs, and updated client cloud culling to operate on the combined cloud list that now includes injected hurricane storm cells.

## Unreleased - Tornado movement refactor
- Replaced the tornado's old per-tick heading recomputation with a persistent route-planning system that stores a waypoint, target heading, target speed, and route duration, so the server now chooses a path ahead of time and commits to it for several seconds instead of zigzagging every tick.
- Refactored `StormMotionModel` so tornado movement planning now happens as a low-frequency route selection pass, while `TornadoInstance` performs only smooth per-tick heading blending, speed blending, and one final authoritative motion application.
- Added leash-aware waypoint planning plus light shield-avoidance shortening, so tornadoes keep a coherent travel path, curve naturally into new routes, and stop stalling or visually shaking from unstable steering corrections.

## Unreleased - Command and utility fixes
- Refined tornado removal commands so `/pa removetornado` now removes the nearest tornado within a sane default range, supports an optional radius argument, and has a new spaced alias at `/pa remove tornado`; both also support `all` to clear every tornado quickly.
- Added a proper craftable recipe for the storm shield weather deflector, plus the missing blockstate, block model, item model, and lang entries so it can be crafted and shown in inventory without missing-model warnings.
- Kept the storm siren craftable and added the missing display-name lang entry so it shows up correctly in-game.

## Unreleased - Current systems recap
- Reworked Rainbows and Auroras compatibility so both effects now use Project Atmosphere as the main sky-state authority with shared humidity, cloud-cover, recent-rain, clarity, smoothing, and hysteresis logic.
- Added a humidity-driven fog system with synced atmospheric state, wet-biome heuristics, in-game tuning, and debug commands so fog reacts to PA moisture and rain instead of isolated vanilla checks.
- Rebuilt tornado rendering and gameplay around grounded world-space volumes, longer-lived storm lifecycles, stronger entity interaction, configurable debug tooling, and server-side destruction/debris behavior.
- Added the dedicated hurricane volumetric renderer/mesh work so large storms now render as bounded atmospheric structures instead of flat overlay rings.
## Unreleased - Aurora and Rainbows compatibility refactor
- Replaced the old Rainbows compatibility path that only watched rain-stop timing with a Project Atmosphere-driven client sky controller that evaluates humidity, recent rainfall, cloud breakup, sun visibility, sun angle, and hysteresis before exposing a smoothed rainbow state to the Rainbows renderer.
- Replaced the old Auroras compatibility path that only scaled vanilla brightness with the same shared Project Atmosphere sky controller, now driving aurora visibility from PA cloud cover, atmospheric clarity, humidity haze, night timing, biome temperature, and seasonal context instead of isolated vanilla checks.
- Added shared atmosphere status syncing plus a client atmospheric state cache/smoother so Rainbows, Auroras, and fog all consume the same humidity, rain, and cloud-cover authority from Project Atmosphere rather than maintaining duplicated packet trackers.
- Removed obsolete compatibility duplication by deleting the legacy rain-only rainbow bridge and folding the old rain/fog packets into one atmosphere status packet with thin mod-specific hooks.
## Unreleased - Tornado interaction rebuild
- Replaced the old tornado gameplay force model with a new server-side capture system built around three explicit components: inward suction toward the funnel, tangential orbit around the core, and strong upward lift once a target is captured, so players and entities spiral upward instead of just being shoved sideways.
- Reworked captured-entity handling so nearby mobs and other entities use the same funnel capture/orbit/lift path as players, including deterministic rotation direction, motion damping tuned for spiral ascent, and direct motion packet syncing for server players.
- Replaced the previous tornado destruction sweep with a new active funnel-zone block destruction pass that scans the actual destructive cylinder, tears through tree clusters with connected log/leaf breaking, and more aggressively breaks vegetation, weak terrain, fragile structures, grass, and glass on the server.
- Rebalanced tornado terrain scouring so the sweep now stays at the surface layer instead of digging down into multiple dirt layers, prefers turning grass and soil variants into plain dirt, and only rarely excavates topsoil outright during extreme core-strength hits.
- Added capped tornado falling-block debris spawns for destroyed wood, leaves, surface soil, and loose terrain blocks, so more of the tornado's block damage becomes visible moving debris caught in the funnel instead of disappearing instantly.
- Fixed standalone `spawnTornadoNoClouds` crashes in the Simple Clouds integration by treating cloudless tornadoes as position-based when boosting nearby cloud regions instead of blindly dereferencing a missing `CloudRegion`.
- Removed the old `WindForces` tornado push path for players so the legacy slowdown/side-push model no longer fights the new server-side tornado capture system.
- Standalone no-cloud tornado spawns now begin immediately in the active phase, and tornado interaction/destruction logic now starts during any non-terminal phase once intensity is high enough instead of waiting for the old active-only gate.
## Unreleased - Local Simple Clouds dependency
- Replaced the remote `nonamecrackers2:simpleclouds:0.7.3+1.20.1-forge` dependency with the local `libs/simpleclouds-0.7.4+1.20.1-forge-all.jar` artifact in `build.gradle`.
## Unreleased - Project Atmosphere crash screen
- Added a client-side Project Atmosphere crash interception flow that detects whether a thrown exception or crash report stack involves `net.Gabou.projectatmosphere`, saves a crash report into `crash-reports`, unloads the active client session when possible, and opens a dedicated support screen instead of falling straight into the normal fatal client crash path.
- Added a dedicated crash screen with a clear Discord support message plus buttons to close the game, copy a support summary to the clipboard, and open the Project Atmosphere Discord invite in the browser.
- Hooked the handler into the Forge 1.20.1 client loop via a new Minecraft mixin that wraps `runTick(...)` and intercepts delayed crash reports.
## Unreleased - MCP support bundle
- Added a read-only support bundle under `docs/mods/projectatmosphere/` and `data/mcp/mods/projectatmosphere/`, covering overview/install/troubleshooting/FAQ docs plus structured manifest, features, commands, config schema, compatibility, known issues, versions, and support-focused changelog data derived from repository sources.
- Separated strictly official compatibility notes from code-reviewed runtime behavior so support tooling can answer conservatively when release documentation and current source behavior differ.
## Unreleased - Standalone hurricane cloud renderer tranche
- Added a client tornado render-quality control and moved the tornado shader onto an adaptive LOD path that lowers raymarch step count, trims expensive material/noise work, and skips unnecessary inner-funnel sampling when the storm is far away or the user lowers tornado quality for better FPS.
- Darkened the tornado's upper wall-cloud/connection shading again so the top mass reads denser and less washed out against the surrounding storm canopy.
- Reworked tornado interaction gameplay so active funnels now demolish trees, leaves, vegetation, weak structures, grass, and loose terrain much more aggressively, and strengthened entity suction/capture so nearby mobs and players are pulled harder into the circulation and can take storm damage while trapped in the core.
- Replaced the tornado's deferred block-destruction queueing with a direct server-thread column/cluster sweep around the funnel, and flagged all affected entities for motion sync so non-player mobs and entities now receive the same tornado pull/lift updates instead of only the player visibly reacting.
- Added live tornado runtime instrumentation plus `weatherdebug tornado runtime` and `weatherdebug tornado logging <true|false>`, exposing real in-game pull force, upward force, eligible/captured entity counts, destruction sweep radius, candidate block counts, and destroyed block counts so tornado physics and block breaking can be debugged from actual runtime data instead of inferred behavior.
- Replaced the fullscreen tornado volume pass with a world-space proxy-box volume draw, so each tornado is now rasterized at a fixed in-world position and the shader uses scene depth only as a march limit instead of as the thing defining the effect.
- Mirrored the same proxy-box world-space volume approach into the hurricane cloud and fringe passes, keeping the existing storm shapes while removing the old fullscreen “mirage/post-process” anchoring behavior from the actual volume render path.
- Added a gated `/pa debug tornado render ...` investigation path with per-storm selection, frozen deterministic funnel sampling, grayscale mask modes (`aabb`, `funnel`, `height`, `radial`, `radius`, `density`, `alpha`, `wallcloud`, `connection`, `full`), and structured CPU-side diagnostics that log the tornado origin, sample position, local-space values, bounds/scaling, and active render state for root-cause analysis.
- Extended the tornado proxy debug path with ordered proof modes for the world-space renderer: `box` draws the proxy volume as a solid opaque color, `hit` shows ray-box intersection success, `fill` renders a constant-density box, and only the later modes reintroduce tornado shaping before normal depth stopping is enabled again.
- Fixed the shared world-space proxy-box draw submission so tornado and hurricane volume boxes now render with the real cloud-pass model-view and projection matrices instead of identity matrices, which could make even forced-opaque `box` mode disappear entirely.
- Darkened the tornado’s upper connection/top shading by about 30% and doubled the existing twist rotation speed in the shader, keeping the current silhouette and animation style while making the top feel denser and the spin read more strongly.
- Removed the client tornado debris particle spawn loop entirely and increased the tornado shader’s existing twist multiplier again, so the funnel keeps the grounded volumetric look without the expensive swirling debris particles and reads with a faster spin.
- Reworked tornado visual motion so the renderer now uses a continuously advancing spin value instead of the old capped twist, and changed client tornado interpolation to smooth toward snapshot targets with light extrapolation instead of directly stepping toward each sync update.
- Fixed `spawnTornadoNoClouds` so it now spawns a real standalone tornado immediately instead of still routing through the cloud-gated cumulonimbus path, and added a no-cloud server spawn mode that does not auto-dissipate just because no Simple Clouds region is attached.
- Corrected the tornado shader’s shaping math to evaluate funnel, wall-cloud, and connection terms in consistent world-unit space instead of mixing cloud-space coordinates with world-sized constants, which was producing oversized dome/ring artifacts near the cloud base.
- Re-integrated tornado opaque rendering with the real cloud-target depth buffer while keeping the horizon-safe raymarch distance path, and thickened the tornado shader density/tint so funnels now read as embedded storm mass instead of a translucent hologram laid over the world.
- Reworked the tornado raymarch from a blind full-range overlay into a scene-depth-clipped world-space volume, and changed the prepared top/bottom bounds to follow actual ground contact plus cloud-base attachment instead of an oversized fallback height band.
- Retuned tornado demolition so active funnels start destroying trees, leaves, wooden lightweight structures, and loose natural terrain much earlier in the wind curve, with stronger inner-core forcing and wider believable damage reach tied to storm intensity.
- Strengthened the dynamic fog application curve so humidity and wet-biome fog compresses view distance and tint more visibly instead of remaining too subtle to notice in normal gameplay.
- Added `/pa fog spawn [strength] [seconds]` and `/pa fog clear`, implemented as a client-side debug fog override packet/state path that lets you force visible fog for render testing without faking server weather.
- Fixed the tornado fullscreen render integration so both Simple Clouds pipelines now render the opaque pass on the cloud target in the correct translated cloud-space stage; this removes the bad extra shader-support translation and stops camera-position-dependent tornado popping/disappearance.
- Increased tornado persistence by extending the lifecycle timings and adding a cloud-detach grace window, so tornadoes no longer collapse almost immediately after a brief cloud-region handoff or wobble.
- Updated the tornado shader/render path so the funnel no longer hard-clips against the copied scene-depth horizon, which previously made tornadoes shear off around camera eye level over water or other long flat surfaces.
- Reworked tornado lifetime scaling to guarantee a roughly 2-10 minute active window based on storm strength, and changed water exposure so it only softens intensity instead of fast-forwarding or force-ending the tornado after a few seconds.
- Added a modular humidity-driven fog system with a lightweight server-to-client fog status sync, a client fog state cache/smoother, wet-biome classification heuristics, and integration into the existing fog event handler so fog now responds to humidity, rain, and moisture-heavy biomes without per-frame heavy sampling.
- Added a new `fog` common-config section plus in-game config screen controls for the main fog tuning values, including humidity thresholds, wet-biome strength, rain boost, fog distance, tint strength, and advanced biome id/keyword overrides in the config file.
- Added `weatherdebug fog` to expose the live humidity, wet-biome weighting, rain contribution, and resulting fog strength using the same shared heuristic that drives the client fog renderer.
- Added a dedicated Simple Clouds-style hurricane renderer and shader path that renders bounded volumetric storm masses as their own cloud body instead of extending the old flat ring overlay path.
- The new hurricane density field now supports a true empty eye, annular eyewall, upper canopy, outer shield, and early spiral-band structure while still reusing the Project Atmosphere / Simple Clouds texture samplers, fog, depth, and cloud-color language.
- Hooked the hurricane renderer into both Simple Clouds default and shader-support pipelines, added hurricane shader registration/resources, and disabled the old `SimpleCloudsRendererMixin` ring path from the active mixin config.
- Extended hurricane client snapshots/interpolation so prepared render data can be cached and reused per frame, and fixed tornado/hurricane motion/constructor mismatches so the current tree builds cleanly again.
## Unreleased - Distant Horizons Simple Clouds compatibility
- Added a DH-aware fallback that forces Simple Clouds onto its `DhSupportPipeline` when Distant Horizons is present, so the shared cloud renderer stays on the post-DH frame path instead of relying on the default after-sky stage.
- Added DH-pipeline diagnostics so the next client run can prove whether the DH cloud pass is reached, how many cloud elements it renders, and whether the geometry is being dropped before or after DH composition.
## Unreleased - Restore ordinary Simple Clouds cloud density
- Restored the working base Simple Clouds cloud profiles for `altocumulus`, `altostratus`, `cumulus_congestus`, `cumulus_humilis`, `cumulus_mediocris`, `custom_cumulonimbus`, and `stratocumulus_opacus` so the shared mesh generator once again gets the same density inputs as the last known good revision.
- Restored the `pattern` cloud type and spawn entry, and returned the ordinary cloud library / weather classification tables to the last known good selection semantics.
- Re-anchored hurricanes against the live Simple Clouds layer instead of a hardcoded Y=64 fallback, so they now sit roughly 200 blocks below the configured cloud height without dropping the volumetric mesh completely out of range.
- Switched the client hurricane cache to request a full Simple Clouds renderer reload when integrated-server hurricane snapshots appear, change, or clear, so local singleplayer hurricanes can force a fresh mesh pass instead of staying hidden with only rain semantics active.
- Simplified the tornado admin/debug command flow so `/pa spawnTornado` and `/pa spawnTornadoNoClouds` no longer sit in cloud-seeding wait loops; if cloud attachment fails, they now fall back immediately to a force-spawned standalone tornado.
- Re-synced `TornadoCommand` and `TornadoManager` to `Dynamic-Forge-1.20.1-Tornado` so the tornado spawn path matches the Tornado branch behavior instead of the newer hurricane-branch force-spawn flow.
- Restored the tornado branch integration points that the hurricane branch had lost: tornado client snapshot packets are registered again, the Simple Clouds tornado renderer mixins/shaders are back, and client tornado overlays/effects now read from the client tornado list instead of the server-only list.
- Fixed the local singleplayer hurricane render path by making `ClientHurricaneStateCache` fall back to live integrated-server hurricane snapshots whenever the synced snapshot cache is empty, which lets debug-spawned hurricanes render again in local worlds.
- Lowered the hurricane anchor by roughly 200 blocks and expanded the outer cumulonimbus storm extent by about 5x, with a broader edge fade and larger hurricane cloud-noise scales so the storm reads as a much larger regional system instead of a compact ring high in the sky.
- Changed the tornado admin/debug spawn commands to force-spawn a visible tornado immediately instead of timing out on cloud seeding, while still attaching to a nearby severe cloud when one is available and broadening cloud lookup to a larger severe-cloud fallback radius.
- Moved the hurricane core-to-cumulonimbus recovery inward so the outer storm body now begins from the eyewall region instead of from far out on the core radius, and added a dedicated inner bridge envelope/noise pass to close the remaining dead ring between the eye wall and the outer storm mass.
- Optimized `StormShieldManager` to stop hammering chunk-load tick time: shield tracking now uses a chunk-aware primitive index, only scans chunk sections whose palettes can actually contain the storm shield block, updates from block place/break events, and queries nearby chunk buckets instead of mutating/iterating a global concurrent boxed set.
- Diagnosed the tornado regression against Dynamic-Forge-1.20.1-Tornado: the dynamic branch diverged from merge base 7c30affb6c9a1f46c5526df5bbb7455e4b14a6c0 and never merged the newer tornado stack, so it had drifted back to the old local tornado implementation.
- Restored the source-of-truth tornado pipeline from Dynamic-Forge-1.20.1-Tornado, including the tornado manager/instance/snapshot/spawner flow, regional storm phase integration, standalone spawn/remove/sync packets, the Simple Clouds tornado renderer/shaders, tornado client effects, and the config/UI hooks needed for render quality and client cleanup on the dynamic branch.
- Reworked hurricanes to intensify out of the existing cyclone system instead of acting like isolated local storms: CycloneManager now exposes active cyclone snapshots, HurricaneManager tracks cyclone formation eligibility over warm ocean plus convective cloud coverage, and cyclone-linked hurricanes inherit the cyclone's regional disruption while adding stronger wind fields, tree/block destruction, entity pushing, eyewall lightning, and native hurricane rendering/sync.
- Stabilized hurricane semantic ownership in the eye by keeping the eye visually empty/dry while still reporting `projectatmosphere:hurricane` to Simple Clouds query paths, which should stop the F3 overlay from flipping to `simpleclouds:empty` when crossing the eye.
- Reworked the hurricane core-to-outer transition again so the inner spiral persists farther out, the outer cumulonimbus mass begins earlier, and new broad outer spiral rainbands give the storm a more cyclone-like top-down silhouette instead of a smooth circular disk.
- Rebalanced `altocumulus`, `altostratus`, `cumulus_humilis`, `cumulus_mediocris`, `cumulus_congestus`, `custom_cumulonimbus`, and `stratocumulus_opacus` to reduce geometric fill and total cube output while improving vertical anisotropy, contour, and layered/puffy sculpting.
- Fixed the command-spawn tornado path so `/pa spawnTornado` now creates a managed tornado instance in addition to attaching the cloud descriptor, and relaxed supporting-cloud lookup to use a nearby fallback cloud when strict intersection misses on the client.
- Added a shared CPU hurricane semantic sampler and wired it into Simple Clouds cloud-type, precipitation, and rain-level queries, so hurricanes now report `projectatmosphere:hurricane`, force visible rain outside the eye, and keep the eye dry without relying on fake cloud regions.
- Added query-only hurricane reservation regions plus spawn/reconciliation hooks in the Simple Clouds generator, preventing normal cloud formations from spawning into or drifting through the hurricane footprint while keeping the hurricane render path native.
- Tightened the hurricane core-to-cumulonimbus blend so the outer storm body starts overlapping before the inner spiral fully fades, removing the remaining visible handoff between the core structure and the outer mass.
- Added per-hurricane vertical anchoring at Y=256, split the preserved eye/core radius from a new world-scale outer storm extent, and updated the Simple Clouds mesh path so hurricane chunks render at the lowered altitude without moving the global cloud layer.
- Reworked the hurricane region mask into a core-to-cumulonimbus blend, keeping the eye/eyewall near the center while expanding the outer storm body into a much larger continuous cloud shield with smoother radial transitions.
- Slowed hurricane rotation to long-period large-storm motion and expanded hurricane weather forcing so nearby atmospheric regions get stronger rain/cloud floors while the server now spawns explicit eyewall lightning near players.
- Slowed hurricane rotation down to large-scale storm pacing, reshaped the mask so the core keeps a clear eye while the outer radius blends into cumulonimbus-style storm mass, and retuned the hurricane cloud profile to borrow a more vertical cumulonimbus volumetric structure instead of a flatter outer shelf.
- Reworked the hurricane eye mask back into a true open center, replaced the oversized flat outer shelf with a tighter cumulonimbus-style outer mass, and added direct hurricane forcing into nearby atmospheric regions so hurricanes now drive rain/thunder conditions instead of only rendering visually.
- Removed the artificial spinning eye-core from the hurricane mask, raised the hurricane cloud body higher above the terrain, and expanded the connected cumulonimbus envelope so the storm spans a much larger continuous cloud mass.
- Fixed the disappearing hurricane regression by bringing projectatmosphere:hurricane back under Simple Clouds' 4-layer noise limit; the outer cavity effect now stays in the hurricane shader mask instead of a fifth cloud noise layer.
- Mapped projectatmosphere:hurricane into the thunderstorm weather path so hurricane clouds count as rainy/thunderous, and thickened the outer hurricane density with cavity-cut cumulonimbus-style mass instead of a cleaner ring shell.
- Slowed hurricane rotation, added rotating inner-core coverage, widened connected outer cumulonimbus mass with blended transitions, and switched the native hurricane cloud type identifier to projectatmosphere:hurricane.
- Moved storm mesh-generator helper DTOs out of the mixin package so RegionUpload/TornadoUpload are no longer loaded as direct mixin-owned classes at runtime.
- Rebalanced the native Simple Clouds hurricane profile so storms render much larger, sit lower in the cloud layer with deeper base offsets, and use smoother band coverage plus softer lower noise to reduce underside streak artifacts.
- Replaced the old hurricane ring overlay with a native Simple Clouds integration path driven by explicit hurricane render snapshots synced from the server to the client.
- Added a client hurricane state cache plus SyncHurricaneStatePacket, so hurricane cloud rendering no longer reaches into server-only hurricane state.
- Extended the overridden cloud_regions.comp compute shader and MultiRegionCloudMeshGenerator mixin with a dedicated hurricane formation primitive, including a true hollow eye, eyewall banding, spiral coverage, and conservative CPU chunk meshing support.
- Added a dedicated simpleclouds:hurricane cloud type for hurricane noise/lighting identity instead of reusing the old custom_cumulonimbus shortcut.
- Removed the fake hurricane render hook/classes and stopped /spawnHurricane from spawning standalone Simple Clouds cumulonimbus regions outside the native hurricane system.
- Forced Simple Clouds mesh and region compute shaders to load Project Atmosphere-owned shader resources directly, so the hurricane/tornado SSBO extensions no longer depend on cross-mod asset override order at runtime.
- Added gated Simple Clouds runtime diagnostics for the shared client pipeline, including player cloud sampling, selected cloud type/profile logging, mesh-region upload counts, chunk-generation decision logs, mesh finalize counts, and per-pass draw counters so a client run can prove whether the failure is in the inputs, the compute path, or the draw path.
- Relaxed the diagnostics gate so the shared Simple Clouds probes now emit a one-time runtime proof line by default, instead of staying silent unless `-Dprojectatmosphere.simpleclouds.debugRender=true` is present.

## Unreleased - Gradle sync fix
- Removed the duplicate mid-script `import groovy.json.JsonOutput` from `build.gradle`, which could stop the Gradle script from compiling during IDE sync.
- Replaced legacy archive/version references with Gradle 8-safe values for the jar manifest plus the Modrinth and CurseForge artifact paths.
- Made the optional private GitHub Maven repository and publishing target conditional on GitHub package credentials so local sync does not fail when `GITHUB_USER`/`GITHUB_ACTOR` and `GITHUB_TOKEN` are unset.
- Restored the missing root Gradle wrapper files under `gradle/wrapper/` and pinned them to Gradle 8.8, preventing IDE sync from drifting to Gradle 9.
- Updated IntelliJ project settings to use the existing `temurin-17 (2)` SDK for Gradle import and changed the leftover module bytecode target from Java 21 back to Java 17.
- Stopped the forecast loading overlay from injecting into `ProgressScreen` and `GenericDirtMessageScreen`, so it no longer renders during save-world and generic dirt/progress screens while still appearing on actual world-loading screens.
- Moved the forecast loading overlay higher on the screen to avoid overlapping vanilla loading text and progress elements.
- Replaced every `tfc:*` biome temperature block in `BiomeTempConfig` with the new seasonal min/max dataset and removed obsolete TFC keys that were not part of the provided list.
## Unreleased - Release notes refresh
- Replaced `PAchangelog.md` with updated platform-ready release notes for Discord, CurseForge, and Modrinth covering the current `0.8.0.0` forecast/runtime refactor, telemetry, coupling, and compatibility work.
## Unreleased - Telemetry Instant serialization fix
- Registered explicit Gson adapters for `java.time.Instant` in `TelemetryCollector`, serializing timestamps as ISO-8601 strings instead of relying on blocked reflective field access under JDK 17.
- This fixes telemetry export/runtime failures caused by `InaccessibleObjectException` when exporting anomaly and precipitation trace records.
## Unreleased - Runtime atmosphere coupling phase D cyclone/cloud reconciliation
- Added retained cyclone visual floors on `RegionAtmosphereState` for cloud cover and rain intensity, so cyclone forcing now has an explicit ownership channel instead of relying on transient direct writes that later get overwritten.
- Updated `CycloneManager` to push cloud/rain floors into the region state while still applying the immediate pressure, humidity, and temperature deltas, and to seed cloud-water from that forcing.
- Updated `CloudManager` to merge sampled cloud/rain values against the retained cyclone floors and to preserve those floors during passive fade-out, preventing low-pressure cyclone regions from immediately losing their visible weather when SimpleClouds sampling runs afterward.
## Unreleased - Runtime atmosphere coupling phase C temperature anchor
- Added a forecast-temperature restore term and a soft excess-deviation guard in `AtmosphericUpdateScheduler`, so warm and cold regions now converge back toward the forecast temperature target instead of relying almost entirely on sunlight blending and tiny base relaxation.
- Added a `temperature_drift_from_target` anomaly marker for regions that remain far from their forecast temperature target under near-clear, low-rain conditions, making hidden temperature drift visible in telemetry exports.
- Kept the change scoped to scheduler temperature control only; cyclone/cloud ownership reconciliation remains a separate follow-up tranche.
## Unreleased - Runtime atmosphere coupling phase B pressure anchor
- Added a forecast-pressure restore term and a soft excess-deviation guard in `AtmosphericUpdateScheduler`, so runtime pressure now trends back toward the forecast climatology instead of free-drifting for long periods after dynamic forcing.
- Added a telemetry anomaly marker for `pressure_drift_no_visible_weather`, emitted when a region remains far from its target pressure while cloud cover and rain stay near zero, to surface hidden pressure/weather desynchronization directly in exports.
- Kept the intervention scoped to pressure only for this tranche, leaving the later temperature/cyclone-cloud ownership work for the next coupling phase.
## Unreleased - Runtime atmosphere coupling phase A instrumentation
- Added forecast-derived `getTargetTemperature(dayTime)` and `getTargetPressure(dayTime)` accessors in `RegionAtmosphereState`, alongside the existing humidity target, so runtime telemetry can compare current state against immutable climatology profiles instead of mutable daily snapshots.
- Added `atmosphere_coupling.jsonl` telemetry export with active-region samples capturing target vs current temperature, pressure, and humidity plus the scheduler-applied temperature and pressure deltas for each update.
- Wired `AtmosphericUpdateScheduler` active updates to emit the new coupling telemetry before anomaly recording, giving a direct diagnostic stream for temperature/pressure drift investigations.
## Unreleased - Runtime atmosphere coupling design study
- Added `doc/runtime-atmosphere-coupling-study.md`, a companion design study focused on the runtime coupling problem between forecast targets, dynamic temperature/pressure forcing, cyclone/ocean/wind effects, and the visible cloud/rain layer, including diagnosis from recent telemetry, solution tradeoffs, RDCU, MDD, UML diagrams, case studies, phased implementation, risks, and acceptance criteria.
## Unreleased - Humidity budget phase 4 cloud-water extension
- Added explicit condensed-moisture tracking via `cloudWater` on `RegionAtmosphereState`, plus `CloudWaterExchange` and `CloudWaterService` to model condensation, re-evaporation, and precipitation draw as named runtime terms.
- Integrated the cloud-water exchange step into `AtmosphericUpdateScheduler` after the Stage 3 humidity budget so live humidity now couples to condensed cloud moisture without rewriting the temperature/pressure update path.
- Seeded and faded regional `cloudWater` from `CloudManager` based on cloud cover and rain intensity, and expanded telemetry exports to include cloud-water state in both region forecast samples and humidity-budget diagnostics.
- Updated the humidity stage tracker so the rollout now stands at stage `4/4` completed.
- Added `doc/humidity-moisture-budget-verification.md` and updated the design study so the implementation state, verification results, and Stage 4 documentation are aligned.
## Unreleased - Humidity budget phase 3 ocean and wind integration
- Added explicit Stage 3 humidity-budget integration for ocean and wind by exposing `OceanBasinManager.estimateHumidityFlux(...)` and `WindVector.estimateHumidityTransport(...)`, then feeding those terms into `AtmosphericUpdateScheduler` as `oceanFlux` and `windTransport` for active-region humidity updates.
- Removed direct humidity mutation from `AtmosphereFluxInfluence` and `WindVector.update` so ocean and wind no longer double-apply humidity outside the scheduler budget while their other responsibilities remain intact.
- Updated the humidity stage tracker so the rollout now stands at stage `3/4` completed, leaving only the future cloud-water extension stage.
## Unreleased - Humidity budget phase 2 scheduler rewrite
- Reworked `AtmosphericUpdateScheduler` humidity updates to use an explicit Stage 2 humidity budget calculation instead of the old anonymous delta, adding named terms for solar drying, biome evaporation, rain exchange, forecast restore, and a weak precipitation sink.
- Added `HumiditySourceProfile` and `HumidityBudgetService` so humidity behavior is now derived from the regional climate target plus biome moisture bias rather than a single global drying rule.
- Split immutable forecast daily profiles from mutable runtime snapshot profiles in `RegionAtmosphereState`, ensuring `getTargetHumidity(dayTime)` remains anchored to the forecast curve instead of drifting as live humidity snapshots are recorded.
- Stopped the scheduler from restoring humidity a second time through `relaxTowardBase`; post-update base relaxation now only applies to temperature and pressure in the scheduler path.
## Unreleased - Humidity budget phase A instrumentation
- Added a runtime `HumidityBudget` scaffold plus `RegionAtmosphereState.getTargetHumidity(dayTime)` so the humidity rework now has an explicit diagnostic model and a forecast-derived target available at runtime.
- Added `humidity_budget.jsonl` telemetry export with per-region active-update humidity budget samples, including target humidity, before/after runtime humidity, and the current decomposition of solar drying, rain exchange, precipitation sink, and net delta.
- Added `doc/humidity-moisture-budget-stages.md` to track the rollout as a staged plan with the current status marked as stage `1/4` completed and the remaining stages explained.
## Unreleased - Humidity budget design study
- Added `doc/humidity-moisture-budget-study.md`, a dedicated design study for the runtime humidity rework covering the problem diagnosis from telemetry, the target product/architecture vision, solution tradeoffs, RDCU, MDD, UML diagrams, case studies, implementation phases, risks, and acceptance criteria for a hybrid forecast-anchored moisture-budget model.
## Unreleased - Forecast refactor phase 6 runtime cleanup
- Replaced biome-key cloud/weather area sampling with region-first sampling in `WeatherSampler`, and updated cloud spawn candidate selection to aggregate temperature, humidity, pressure, wind, and storm factors directly from `RegionInstanceKey` runtime state.
- Migrated SimpleClouds runtime integration to region-first helpers for cloud creation/spawning and cloud tick wind/storm sampling, keeping biome-key spawn entry points only as explicit compatibility edges where external APIs still require them.
- Removed dead biome-key runtime compatibility scaffolding that was no longer used in live server execution, including `ForecastPointerRegistry`, active-player biome fallback tracking in `ForecastOrchestrator`, and unused legacy biome views in `AtmosphericStateRegistry`.
## Unreleased - Forecast refactor phase 4/5 closure
- Switched forecast bootstrap, season regeneration, manual regeneration, and missing-forecast recovery to rebuild wind runtime state from primary `RegionInstanceKey` forecasts instead of the legacy biome forecast map.
- Removed the unused legacy biome-forecast save writer from `ForecastDataStorage`; region saves remain the only write path while `biome_forecasts.json` stays as a read-only migration/import fallback.
- Moved remaining server command/debug wind consumers (`weatherdebug`, `/windSpeed`, hurricane spawn, tornado spawn/debug cloud seeding) onto region-first wind sampling while preserving biome-key adapters only where external cloud spawn integration still requires them.
## Unreleased - Forecast refactor phase 5 region-first persistence
- Added primary region-first persistence for forecast saves under `region_forecasts`, including bulk region discovery and load-time integrity validation for weekly temperature, humidity, pressure, wind, and storm data.
- Migrated startup/shutdown forecast persistence to prefer region saves while keeping legacy `biome_forecasts.json` and legacy region fallback files as read-only compatibility/import paths.
- Hydrated legacy biome-key compatibility structures from loaded region forecasts so existing runtime systems can keep using biome-key adapters while persistence moves to `RegionInstanceKey` first.
- Added direct wind forecast rebuild support from region forecasts so server bootstrap no longer depends on legacy biome-save hydration when region saves are present.
- Moved active runtime scheduling toward region keys by switching wind ticking, ocean basin updates, tornado cooldown/risk flow, and nearby-player active tracking to `RegionInstanceKey`-first paths while keeping deprecated biome adapters for compatibility.
- Hardened seasonal tree Dynamic Trees integration so the accessor is loaded reflectively only when the `dynamictrees` mod is present, and downgraded the DT development dependency to `compileOnly` so missing DT no longer blocks normal launches.
- Converted sandstorm forecast detection/scheduling to use region forecasts internally, resolving a representative biome sample only at the compatibility edge where the external sandstorm API still requires biome keys.
- Updated humidity/pressure debug commands and tornado debug actions to stop reading player-position biome forecasts directly and prefer region-first forecast/runtime access.
## Unreleased - Forecast refactor phase 4 wind API definition
- Added a minimal region-first wind forecast API (`WindForecastApi`) with direction and speed accessors, plus a default server implementation (`RegionWindForecastApi`) backed by `ForecastOrchestrator`.
## Unreleased - Forecast refactor phase 3 region-first sampling
- Added region-key sampling APIs for temperature, humidity, and pressure in `ForecastOrchestrator`, and migrated `ForecastSampling` to prefer `RegionInstanceKey` resolution while keeping biome-key overloads as compatibility wrappers.
## Unreleased - Forecast refactor phase 2 foundations
- Started Phase 2 implementation by unifying region orchestrator bootstrap on `LegacyBiomeForecastGenerator`, centralizing region-local coordinate conversion, and hardening forecast regeneration to clear stale grouped/average caches before rebuilding dependent forecast passes.
## Unreleased - Forecast refactor phase 1 specification
- Added a complete Phase 1 technical specification for forecast refactoring, including current-state diagnosis, use-case catalog (RDCU), target domain model (MDD), UML class/sequence/activity diagrams, and a concrete migration plan toward RegionInstanceKey-first architecture.
## Unreleased - Wind force tuning
- Apply Weather2-style wind steering (velocity targeting) after player input, using base wind above 11.1 m/s with capped drift.
- Apply exposure checks (sky visibility, water/lava, horizontal collision) before influencing players or other living entities.
- Use the canonical wind selector at the entity position and align wind direction vectors across particles, clouds, and forces.
- Added a ramp factor so near-threshold winds apply negligible steering and no noticeable slow-down.
- Wind drift now adds a directional component without slowing existing player movement.
- Players now receive intermittent gust impulses instead of continuous wind steering, preserving control in normal winds.
- Creative/spectator players are immune to wind gusts, and gust impulses no longer reduce current player speed.
- Sprinting players now ignore gusts unless winds reach extreme thresholds.
- Surface wind sampling now uses the low-wind layer (not high aloft) and no longer treats gust headroom as always-on speed.
- Reduced default wind push scales and player gust caps by ~3x for gentler movement impact.
## Unreleased - Stability and sync fixes
- Active region detection now uses region membership (plus accurate radius checks) so player-owned regions are always marked active.
- Instruments now read live atmospheric state values on the server to keep temperature, humidity, and pressure consistent with wind and effects.
- Client temperature cache updates are now atomic to prevent transient stale reads.
- Season changes now regenerate forecasts without wiping cloud entities, and server ticks detect season transitions across providers.
- Freezing and snow placement now follow Project Atmosphere temperatures so ice/snow match displayed readings.
## Unreleased - Weatherdebug readout alignment
- /pa weatherdebug forecast now uses the region-sampled temperature, humidity, and pressure so values match the thermometer readout.
## Unreleased - Weather world effects
- Added a weather snapshot API plus world-effects manager that samples Project Atmosphere conditions near players.
- Dense cloud cover now suppresses sun-burning mob ignition and speeds fire cooldown under heavy overcast.
- Rain intensity sampling can extinguish fire/campfires and fill cauldrons (snow or water) without chunk-wide scans.
- Added modder hooks via `AtmosphereWorldEffect` registration and `AtmosphereWeatherTickEvent`.
- Skips per-tick world-effect sampling when no rain is present near players and no custom effects are registered.
## Unreleased - Forecast region fallback recovery
- Guarded region slice generation to ensure biome keys are present on forecasts.
- Detect fallback regions containing only min/max clamp values and regenerate them from the initial biome forecasts.
## Unreleased - Thermometer temperature sync
- Restored client temperature day forecast sync so thermometer displays real values instead of the fallback 0.5.
## Unreleased - Instrument readouts on servers
- Instrument items/blocks now send server-side readouts to the client overlay so multiplayer no longer shows default values.
## Unreleased - Storm siren throttle
- Storm sirens now play the severe-storm sound only once per continuous storm event instead of looping every cooldown tick.
## Unreleased - Cloud drift divergence
- Added per-cloud direction bias, slow wobble, and slight speed variance during CloudRegion ticks to prevent visual stacking of parallel cloud trajectories.
## Unreleased - Temperature clamp diagnostics
- Log a debug warning with a stack trace when temperature clamps hit the safety ceiling to pinpoint runaway sources.
## Unreleased - Command namespace
- Moved Project Atmosphere server commands under the `/pa` root (for example `/pa temperature`, `/pa windSpeed`, `/pa spawnTornado`).
## Unreleased - Wind mixing clamp
- Clamped wind-driven neighbor mixing factors and deltas to prevent extreme temperature spikes from propagating.
## Unreleased - Async random safety
- Replaced off-thread uses of server-level random state with local RandomSource instances in storm/sand/tornado/cloud helpers to prevent LegacyRandomSource thread violations.
## Unreleased - Wind push damping
- Added a push ramp to soften player wind force near the threshold.
## Unreleased - Wind particle bending
- Wind-bent particles now resample wind per tick and smoothly steer toward the current wind vector using a configurable bend strength.
- Cached per-tick wind samples by region for particle steering to reduce client overhead.
## Unreleased - Cloud culling optimization
- Simplified client cloud culling to a single pass per tick to avoid quadratic scans.
## Unreleased - Forecast region unification
- Merged legacy region aggregation into the region ForecastRegion model, updated registry/telemetry/orchestrator consumers, and removed the duplicate core class.
## Unreleased - Wind selection and pressure units
- Added a canonical wind selector (dynamic -> forecast -> safe default), deprecated ambiguous wind getters, and updated runtime wind consumers (HUD, particles, clouds, sandstorms, telemetry, ocean/cyclone) to use it.
- Converted wind pressure deltas from hPa to Pa in the speed equation so generated wind magnitudes use consistent physical units.
## Unreleased - Pressure and sunlight tuning
- Clamped generated pressures and live state pressures to stay above 900 hPa, keeping readings out of unrealistic lows.
- Reapplied seasonal temperature clamps after daily variation so winter highs stay within biome bounds.
- Updated sunlight curve/seasonal tilt to give more realistic midday strength and stronger winter dimming.
- Fixed cloud telemetry helpers to match the updated CloudTickSummary signature.
- Prevented forecast saves from being deleted on load so worlds reuse stored forecasts across restarts.
- Added wind bending for campfire and furnace smoke particles.
- Extended wind bending to ash, dust, snowflake, and cloud particles.
- Expanded telemetry to retain cloud history and log periodic region forecast/state samples for debugging.
- Guarded CloudManager sampling log behind debug mode.
- Limited missing-forecast warnings to log once per biome.
- Added crafting recipes for dust/sand layers and fixed the thermometer recipe output.
- Updated storm siren timing to warn on severity 7 storms and to stay active while tornados are within 500 blocks.
- Replaced per-particle wind mixins with a single Particle mixin to avoid shadow mapping crashes.
## Unreleased - Region forecast refactor blueprint
- Region key unified to `RegionInstanceKey` (removed `ForecastRegionId`); wind/spike/state/orchestrator/networking updated to the unified key.
- Added region orchestrator scaffolding (`modules/region`) and region-based sampling APIs in `ForecastOrchestrator`, `AtmoApi`, and `ForecastSampling`.
- Spikes are region-only (no biome-generation spikes); BiomeChangeManager now tracks regions (and last biome for compatibility) and regenerates when entering a new region or moving ~80% of region size.
- Cloud sampling uses region centers; far clouds culled; SimpleClouds spawn compatibility rejects spawns beyond 10k from players and biases closer spawns.
- Added a client-only telemetry collector with bounded buffers plus `/pa debug export` to serialize session JSONL files and zip them asynchronously with clickable chat links; exports respect a retention window and configurable enable flag.
## Unreleased — Async active-region scheduler
- Added `AtmosphericUpdateScheduler` to refresh only player-proximate states every 20 ticks and batch passive regions through a round-robin queue every 100 ticks using `AsyncAtmosphereService`.
- Sunlight/rain/relaxation now apply as clamped deltas on the main thread after async computation, with stronger sunlight blending and per-variable safety clamps (temperature floored at -273.15C, pressure limited to 870–1080 hPa).
- Cyclone updates now compute off-thread and apply capped deltas on the main thread, preventing runaway pressure/temperature spikes and keeping rain/cloud boosts within bounds.
- State mutators adjust relative to the current value instead of resetting to the biome base, so weather effects accumulate naturally while remaining clamped to realistic ceilings.
## 0.6.0.0-pre3.2 - Wind neighbor safety (2025-11-27)
- Guarded atmospheric state lookups against null neighbor keys so wind updates no longer crash when registry data is missing during regeneration.
- Rebuilt neighbor lists off-thread into immutable snapshots before swapping them into the registry, preventing ConcurrentModificationException while wind mixing iterates during active rebuilds.


## Unreleased – Biome naming and TFC coverage
- `BiomeTempConfig` now warns when biome keys are provided without a namespace (e.g., `minecraft:desert`, `biomesoplenty:bayou`) so config stays tied to the right mod IDs.
- Added TerraFirmaCraft main and technical biome temperature curves (oceans, plains, mountains, rivers, beaches, edges, and estuaries) to keep climate sampling consistent in modded worlds.

## 0.6.0.0-pre3 - Sky effects + season bridge (2025-11-24)
- Tornado shader now binds live SimpleClouds cloud color as its base texture (fallback to static) and densifies alpha/color to remove moving holes; `/spawnTornado` no longer blocks when `CloudTornadoes` SSBO is missing (spawns shader funnel unless legacy fallback is explicitly enabled).
- Added a pluggable season time helper (neutral default) and refactored client season consumers (auroras, leaves, hurricanes, temperature generation) to rely on it instead of Serene Seasons directly.
- Auroras render only on cold nights; rainbows trigger only when rain stops, and both now expose active flags/positions to the client for shader packs.
- In-game config buttons cover tornado debug logging and legacy fallback toggles.

## 0.6.0.0-pre2 – Tornado-aware SimpleClouds sync (2025-11-16)
- Reworked the `MultiRegionCloudMeshGenerator` tornado mixin to mirror the upstream region packing logic instead of calling
  compiler-generated lambda targets, restoring compatibility with SimpleClouds 0.7.3, using a dedicated `CloudMeshGenerator`
  accessor and standalone helper carriers to keep the mixin compliant with Sponge guidelines.
- Added shader capability detection so tornado uploads only run when the SimpleClouds compute shaders expose the new
  `CloudTornadoes` SSBO, preventing `NullPointerException`s in environments that still ship the vanilla shader pack.
- Updated `/spawnTornado` so it first tries to attach a tornado descriptor to the nearest cumulonimbus cloud (engaging the
  new shader-driven funnel) and only falls back to the legacy mesh-based tornado shape when no cloud-defined shape exists.
- `/spawnTornado` now queues retries instead of falling back immediately, waiting for a SimpleClouds cumulonimbus to appear
  so spawned tornados always use the shader-based funnel when one becomes available.
- Added a public `ITornadoRegion` contract (plus `TornadoDescriptor` and accessor helpers) so controller mods can attach funnel
  metadata to any SimpleClouds `CloudRegion` and trust the data to serialize across tags, packets, and API events.
- Extended the existing `CloudRegionMixin` and new API/event mixins to mirror tornado lists through `ScAPICloudRegion` and
  `CloudRegionTickEvent`, allowing other mods to add/remove funnels without bespoke casts.
- Injected a client-side SSBO writer for `MultiRegionCloudMeshGenerator` that streams tornado descriptors to a new
  `CloudTornadoes` buffer and advertises the total count to both compute shaders.
- Overrode `cloud_regions.comp` and `cube_mesh.comp` so tornado cylinders force full density/fade inside their footprint and
  punch through noise when voxels fall inside the declared column height.
- Fixed the `cube_mesh.comp` neighbor check so tornado interiors are treated as empty space, letting adjacent cubes emit faces
  and carve a visible funnel cavity.

## 0.6.0.0-pre2 – Ocean basin integration (2025-11-15)
- Added a modular ocean basin subsystem that detects contiguous oceanic forecast samples asynchronously and keeps long-lived energy reservoirs in sync with the dynamic core.
- Introduced polymorphic influence pipelines so basins adjust their own thermal/pressure memory before feeding humidity, pressure, temperature, and wind tendencies into nearby forecast cells.
- Hooked the new manager into the existing tick loop alongside cyclones and registered optional Continents/Tectonic geometry support, including Gradle dependencies for both mods.

## 0.6.0.0-pre1 – Cloud region unification (2025-11-14)
- Rebuilt the atmospheric cloud manager so each SimpleClouds `CloudRegion` now carries its own thickness, rain intensity, and lifecycle instead of duplicating data per biome sample.
- Region scans now run on `AsyncAtmosphereService`, averaging humidity/temperature for only the biomes under each cloud footprint and projecting the combined cover back to those biomes.
- Cloud growth and shrink follow humidity and temperature trends while spawn attempts reuse the old `trySpawnClouds` heuristics to find humid hotspots asynchronously before creating regions on the main thread.
- Sunlight now lerps toward forecast-derived daily min/max temperatures, preventing runaway heat spikes (e.g., 25C -> 139C swings in sparse jungles) while still letting rain, humidity, and wind modules nudge the live value.

## 0.5.5.7 – Cloud rave pacing (2025-11-14)
- Clouds no longer react every tick; they now require minutes/days of stability above a biome before the humidity-driven radius/lifetime adjustments kick in, producing a smoother, rave-like rhythm.
- Humidity biases those dwell timers so humid climates saturate quicker while arid, hot areas still need to linger for several minutes before they can shrink or disperse.

## 0.5.5.7 – Cloud persistence tuning (2025-11-13)
- Clouds now ease toward a humidity-driven target thickness instead of jumping immediately, so growth and dissipation happen over minutes rather than seconds.
- Dissipation speed scales with biome dryness, letting humid areas keep their systems intact while extreme deserts still erode storms after several minutes of exposure.
- Rain intensity ramps in slowly alongside thickness, preventing sudden downpours when a cloud first spawns.

## 0.5.5.7 – Cloud spawn throttling (2025-11-12)
- Added a respawn cooldown to the atmospheric cloud manager so SimpleClouds visuals are not re-created every tick when humidity rapidly crosses the storm threshold.
- Cloud data now persists through dissipation cycles and only attempts a new spawn once the cooldown elapses, preventing runaway "cloud rave" behaviour.

## 0.5.5.7 – Storm factor integration (2025-11-11)
- Removed the legacy storm chance forecast data in favour of live storm factors so gusts, cloud spawners, and SimpleClouds hooks follow the new cyclone/sunlight-driven core.
- Wind gust multipliers now scale smoothly with the measured storm factor instead of toggling at a fixed threshold.

## 0.5.5.7 – Biome-driven cloud evolution (2025-11-10)
- SimpleClouds regions now sample the biome beneath them to grow in cool, humid climates and dissipate over hot or arid zones.
- Cloud radius changes gradually each tick with matching lifetime adjustments so long-lived storm systems persist over wet areas and burn out faster in deserts.
- Cloud radius multipliers persist through sync/serialization and stay clamped, preventing abrupt pop-in while still allowing clouds to shrink back when conditions stabilise.

## 0.5.5.7 – Biome-aware sunlight tuning (2025-11-09)
- Sunlight intensity now scales with each biome’s seasonal temperature ranges, letting hotter climates receive stronger midday heating.
- Region states keep hourly daily curves sourced from the live controllers so commands and clients can still display day profiles.
- Build automation skips CurseForge uploads and Discord notifications automatically when their environment tokens are absent.

## 0.5.5.7 – Dynamic atmosphere simulation (2025-11-08)
- Replaced daily forecast regeneration with a live atmospheric state registry that evolves continuously.
- Added sunlight, cyclone, cloud, rain, and wind controllers so temperature, humidity, and pressure react to in-game forces.
- Updated commands and client helpers to report the new dynamic values and removed the legacy daily forecast generator.

## 0.5.5.7 – Aurora & rainbow integration (2025-11-07)
- Added optional compatibility hooks for the Auroras and Rainbows mods.
  - Aurora brightness now scales with Serene Seasons data and is boosted in freezing biomes.
  - Rainbows rely on the Project Atmosphere / Serene Seasons Plus rain helper so they only trigger after custom storms clear.
- Introduced guarded client mixins plus a rain-state tracker so these integrations activate only when the companion mods are installed.
- Refined aurora and rainbow compatibility syncing.
  - Aurora brightness now queries Project Atmosphere’s live temperature data (or active temperature mods) instead of static biome values.
  - Rainbows receive server-synchronised rainfall intensity from SimpleClouds spawns/despawns, allowing accurate rain stop triggers across dimensions and for joining players.

## 0.5.5.4 – Non-vanilla biome resolution (2025-10-24)
- BiomeTempConfig now resolves un-namespaced biome keys by scanning the biome registry.
  - Non-vanilla biomes defined without a namespace (e.g., `bog`) resolve to their mod ids when uniquely found (e.g., `biomesoplenty:bog`).
  - If multiple mods provide the same path, mappings apply to all matches and an info log is emitted.
  - If no match is found, falls back to `minecraft:<path>` and logs a warning.
  - Applies to `putAllSeasons`, `putConstSeasons`, and `mirrorBiome`.

## Unreleased — Forecast regions grid
- Introduced `RegionInstanceKey` grid mapping and `ForecastRegion` aggregates to replace biome-scoped forecasts.
- Atmospheric state registry and region state now operate per forecast region while keeping legacy biome lookups mapped to their owning regions.
- Forecast generation now groups biome samples into region forecasts before seeding atmospheric states; SimpleClouds and cyclone/cloud sampling apply updates against region states.
- Public API now exposes region-centric forecasts via `AtmoApi#getWeatherForecast`, aligning cloud speed sync with region identifiers.

## 0.5.5.2 — Imperial Units Mode (2025-10-19)
- Added config option `display.imperialUnits` to toggle display units.
- Overlay and commands now respect units:
  - Temperature shows as °F when enabled (°C otherwise).
  - Wind speed shows as mph when enabled (m/s otherwise).
  - Pressure shows as inHg when enabled (hPa otherwise).
- In-game config screen adds an “Imperial Units” toggle under Display.
- Regeneration safety: clearing/regenerating forecasts now pauses dependent ticks (wind physics, tornado/hurricane/snowstorm managers), and defers scheduled tornado checks until regeneration completes.

## Unreleased — Unified wind stack
- Rebuilt wind handling into a high/low layer model with gust-aware forecasts and runtime smoothing that mirrors the other environment modules.
- Added tornado-aware low wind forces plus helpers to apply combined wind, gust, and suction/rotation/lift to players.
- Wired SimpleClouds and forecast orchestration to consume the new wind API while preserving existing forecast generation inputs.
- Ground-level wind particles near players now receive directional pushes when the airflow is unobstructed, keeping leaves and streaks aligned with live wind samples.
- Server-side telemetry now records player weather samples, dominant chunk occupancy, forecast snapshots, cloud lifecycle events, precipitation gate decisions, and temperature anomalies for `/pa debug export`.

## 0.5.4.4 — Added weatherdebug cloud command (2025-10-17)
- Added command: `/weatherdebug cloud <id>`
  - Spawns the specified SimpleClouds cloud at the player’s position/biome.
  - Requires permission level 2.
  - Applies current wind sample; fails gracefully if SimpleClouds is not initialized.
## Unreleased â€” Cloud probe targeting
- Updated `CloudProbeItem` to prioritize clouds intersected by the forward probe ray instead of immediately reporting the cloud containing the player.
- The probe now uses SimpleClouds' actual cloud layer height for intersection checks and only falls back to the containing cloud when no targeted cloud is found ahead.
- Added an enchanted-glint stick presentation for the cloud probe item.

## Unreleased - Forecast loading overlay
- Added a dedicated client forecast loading state and staged status model for the Project Atmosphere sync lifecycle.
- Render the PA loading panel from the vanilla `LoadingOverlay` path so the standard Minecraft loading UI remains visible underneath.
- Hooked the existing login forecast sync into wait/receive/build/prepare/ready transitions and reset the client state automatically on disconnect.
- Extended the PA renderer onto the actual world-join loading screens and upgraded the overlay to a centered, dominant progress panel with determinate or animated indeterminate bar states.
- Reworked the loading overlay into a smaller top-centered status panel so it sits above the vanilla loading UI instead of covering it.
- Promoted the current PA loading stage to the primary on-screen label and tied progress updates to the real forecast snapshot, cache-build, and finalization milestones used during client sync.
- Forecast cache application now drains in client-side batches across ticks, allowing the overlay to report visible per-loop progress from the actual biome-profile apply path instead of jumping from wait to ready.
- Added server-side login preparation stage updates around nearby-region collection and local weather seeding so the overlay advances before the forecast snapshot packet is sent.
- Added an integrated-world loading bridge so local world startup can push forecast-design stages from the real server generation loops before the later client sync packet phase begins.
## Unreleased - Hurricane renderer replacement
- Removed the hurricane-specific `custom_cumulonimbus` spawn path so hurricane commands now create only the dedicated hurricane system instead of seeding a Simple Clouds cumulonimbus shell first.
- Added an explicit `HurricaneRenderDescriptor` to server snapshots and client interpolation so eye size, eye-clear radius, eyewall thickness, canopy radius, shield radius, vertical layer factors, and outer band controls travel as storm data instead of being reconstructed only from radius and intensity during rendering.
- Reworked `SimpleCloudsHurricaneRenderer` into a full dedicated hurricane pipeline with bounded opaque raymarching, weighted-transparency fringe rendering, and framebuffer eye-carve passes that clear unrelated ambient Simple Clouds content from both the opaque and transparent cloud targets before hurricane cloud content is added back.
- Replaced the single hurricane shader registration with dedicated opaque, opaque-mask, transparency, and transparency-mask shader programs plus new fragment shaders for the standalone hurricane volume and eye-protection passes.
- Deleted the abandoned flat-ring hurricane scaffold (`SimpleCloudsRendererMixin`, `HurricaneMeshRenderer`, `HurricaneStateProvider`, and `HurricaneState`) so there is no legacy ring renderer left in the active codebase.

## Unreleased - GPU-native hurricane mesh generation
- Removed the fullscreen hurricane pipeline hooks and their shader registration so hurricanes are no longer rendered as a separate post/cloud-target pass.
- Added `HurricaneMeshField` plus a new `MultiRegionCloudMeshGenerator` mixin that uploads prepared hurricane descriptors to the active Simple Clouds mesh compute shader each mesh-generation cycle.
- Extended chunk scheduling so Simple Clouds now allocates mesh generation work for hurricane volumes even when no ambient cloud region overlaps the chunk.
- Overrode the Simple Clouds `cube_mesh.comp` compute shader with a hurricane-aware density field that emits real mesh cubes for the eye wall, canopy, shield, and outer bands while using the same GPU block generation and face occlusion path as native Simple Clouds clouds.
- Added an eye-carve override directly inside the cube generation density sampling so the hurricane eye removes ambient cloud blocks in the same generated volume instead of relying on a later framebuffer cleanup pass.
- Added a `cloud_regions.comp` override that preserves an explicit "no ambient region" sentinel, preventing hurricane-only chunks from inheriting a bogus default cloud region during compute meshing.





# 2026-04-23

- Added shared Simple Clouds runtime diagnostics for the client mesh generator base class and both render pipeline branches.
- Split the pass-summary logger state so an early fallback or finalize event cannot suppress draw-pass evidence.
- Added pipeline-entry logs so we can prove whether the active path is `DefaultPipeline` or `ShaderSupportPipeline` at runtime.
- No behavioral render change was made in this step; this is investigation instrumentation to isolate the remaining cloud visibility regression.
- Restored the Tornado-branch hurricane render stack: `HurricaneShaders`, `SimpleCloudsHurricaneRenderer`, the hurricane mixin hooks, and the `hurricane_*` shader assets.
- Added compatibility accessors on `HurricaneInstance` and `HurricaneManager` so the restored branch renderer can consume the current hurricane state model without changing the existing sync path.
- Added a targeted Simple Clouds 0.7.4 compatibility cap for lightning SSBO buffering on low-binding GPUs and moved Project Atmosphere's `CloudStorms` SSBO onto binding 0 there, preventing reload crashes without disabling tornado/hurricane cloud shaping.
