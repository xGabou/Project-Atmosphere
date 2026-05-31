# Tornado DH Depth Investigation

This log tracks Distant Horizons tornado depth attempts. Check this file before trying another fix.

## Problem
- With Distant Horizons enabled, terrain color still appears through or over the Project Atmosphere tornado volume.
- Without Distant Horizons, the tornado depth behavior is reported as fine.
- The tornado physical/simulation position appears correct because terrain destruction happens at the expected location.

## Tried Fixes
- Disabled the tornado downsample path under Distant Horizons.
  - Result: Simple Clouds cloud color now renders behind the tornado correctly, but terrain still appears over/through the tornado.
- Used the raw Distant Horizons API depth texture as the tornado shader primary depth sampler.
  - Result: did not fix the terrain issue.
- Removed the secondary cloud transparency depth sampler from the DH tornado path.
  - Result: did not fix the terrain issue.
- Switched the DH tornado shader depth sampler to Simple Clouds' DH-filled `cloudTarget` depth texture.
  - Result: did not fix the terrain issue.
- Added a separate local footing/contact shape in the shader.
  - Result: rejected. It changes the tornado shape instead of fixing DH depth.

## Current Suspects
- The DH path relies on the tornado shader discarding against scene depth before Simple Clouds' final color-only composite. If the scene-depth comparison is wrong, terrain will appear through the tornado.
- The shader ray/depth logic may be comparing ray distance in cloud space against reconstructed depth positions in a different space.
- The render injection timing may be before the correct DH/main depth attachment swap that Simple Clouds uses later for DH-aware world effects.

## Findings
- Simple Clouds' DH path blits `dhFbo` depth into `cloudTarget` and `cloudTransparencyTarget` before cloud rendering.
- Simple Clouds opaque clouds use normal framebuffer depth testing against that copied depth.
- Simple Clouds final composite is color-only and disables depth testing. Therefore Project Atmosphere tornado pixels must already be correct before final composite.
- Project Atmosphere tornado rendering uses both framebuffer depth testing and a shader-side reconstructed scene-depth discard.
- The shader-side discard is at `tornado_round.fsh`: `nearestT > sceneDistance + SOFT_TERRAIN_OCCLUSION_BIAS`.
- This extra shader-side discard is the most likely DH-only failure point because non-DH rendering does not have the same DH terrain depth in the cloud target at that point.

## Next Proposal
- Add a DH-only tornado depth debug mode before another visual fix. It should show whether each pixel is rejected by framebuffer depth, shader scene-distance discard, low alpha, or missing volume hit.
- If debug confirms shader scene-distance rejection is the cause, replace the DH path with a depth-buffer based comparison using `firstHitDepth` vs `sceneDepth`, or disable the reconstructed-distance discard under DH and rely on `gl_FragDepth` plus `GL_LEQUAL`.

## Debug Modes Added
- `/tornado render mode depth`
  - Green: tornado pixel accepted by shader depth checks.
  - Red: rejected by shader scene-distance depth check.
  - Yellow: rejected because accumulated tornado alpha is below the normal discard threshold.
  - Blue: ray hit the tornado proxy box, but no tornado density was sampled.

## Depth Debug Result
- Screenshot below ground: main body is green, edges/base are yellow, surrounding proxy is blue, and there is little to no red.
- Screenshot above ground: main body remains green, lower edge has yellow, surrounding proxy is blue, and there is little to no red.
- Interpretation: the shader scene-distance discard is not the primary failure. Tornado pixels are accepted by the depth checks.
- The visible terrain in normal rendering is more likely coming from final color blending/composite alpha, not from depth rejection.
- Simple Clouds final composite blends `cloudTarget` color over the main scene color using cloud alpha; it does not depth-test during final composite. Therefore accepted tornado pixels with alpha below 1.0 will still show already-rendered terrain color through them.

## Current Attempt
- Added a DH-only `DistantHorizonsDepthMode` shader uniform.
- When enabled by the DH mixin, accepted tornado pixels receive a higher alpha floor derived from accumulated `rawAlpha`.
- This does not add a separate geometry piece and does not apply to non-DH rendering.
- Hypothesis: accepted tornado pixels need stronger alpha during Simple Clouds' color-only final composite so already-rendered terrain color does not bleed through.
- Result: failed. Terrain is still visible over/through the tornado in DH normal rendering.

## Updated Suspects
- Since the accepted-pixel alpha floor did not fix the issue, the terrain may be rendered after the tornado cloud target composite in the DH pipeline, or the tornado is composited into a target that is later overwritten by DH/main terrain output.
- The next investigation should inspect whether the tornado should render in the post-composite DH main-framebuffer section where Simple Clouds temporarily attaches `cloudTarget` depth to the main framebuffer for DH-aware world effects.

## Next Attempt
- Move the DH tornado draw out of the pre-composite `cloudTarget` pass.
- Render it after `SimpleCloudsRenderer.doFinalCompositePass(...)`, directly into Minecraft's main render target while Simple Clouds has temporarily attached `cloudTarget` depth to the main framebuffer.
- Reason: Simple Clouds uses this exact post-composite depth attachment window for DH-aware world effects such as lightning. If terrain is appearing over the tornado because the earlier cloud-target composite is not the final DH color order, this should prove it without adding fake tornado geometry.
- The DH post-composite pass must not sample the same depth texture that is attached as the framebuffer depth attachment. In DH mode, skip the shader-side scene-depth sampler and rely on `GL_LEQUAL` against the attached DH depth plus `gl_FragDepth` from the tornado first hit.
- Implementation status: build passes. In-game DH result is pending user test.
- Result: failed. From less than one block below terrain, the tornado still renders through the ground and far-side grass is visible.

## Current Finding
- The post-composite hook runs while Simple Clouds has bound Minecraft's main render target for color, but has replaced its depth attachment with `cloudTarget` depth.
- That attached `cloudTarget` depth is DH/Cloud depth, not necessarily the live vanilla near-terrain depth under the camera.
- The shader was also changed to skip depth sampling in DH mode to avoid sampling the attached `cloudTarget` depth texture.
- Combined effect: the pass can respect DH LOD depth while ignoring the vanilla main terrain depth. This matches the below-ground screenshot, where nearby terrain should occlude the tornado but does not.

## Next Attempt
- Keep rendering in the post-composite main framebuffer window.
- Keep `cloudTarget` depth attached for framebuffer testing against DH LOD depth.
- Pass Minecraft's detached main depth texture to the tornado shader as `DepthSampler`.
- Re-enable shader-side scene-depth sampling in DH mode so vanilla/near terrain can reject tornado pixels while the framebuffer depth test still handles DH LOD terrain.
- Implementation status: build passes. In-game DH result is pending user test.
- Result: failed. The below-ground test still shows the tornado when terrain should fully occlude it.

## Current Finding
- The failure is no longer safe to treat as a simple source-depth selection problem.
- The observed cutoff/visibility pattern suggests part of the tornado may be rejected before shader output, or the shader is discarding/sampling no density for the region below the camera/player height.
- Existing shader debug modes cannot distinguish fixed-function framebuffer depth rejection from shader rejection because framebuffer depth can kill fragments before the fragment shader runs.

## Next Diagnostic
- Add `/tornado render mode depth_nofb`.
- This mode uses the same colors as `depth`, but disables framebuffer depth testing for the tornado pass.
- Expected interpretation:
  - If the missing lower/below-player tornado region appears in `depth_nofb`, fixed-function framebuffer depth is the culprit.
  - If it is still missing or blue, the raymarch/density field is not sampling tornado volume there.
  - If it is red, shader-side scene-depth discard is the culprit.
  - If it is yellow, alpha thresholding is the culprit.
- Result: `depth_nofb` shows green and yellow from the below-ground/spectator flat-world test. This proves the shader can generate the lower tornado volume when framebuffer depth testing is disabled.

## Current Finding
- The missing lower/below-player tornado portion is caused by fixed-function framebuffer depth testing or by `gl_FragDepth` compared against the currently attached framebuffer depth.
- It is not primarily caused by the density field being absent.
- In the current DH post-composite hook, that framebuffer depth attachment is Simple Clouds' `cloudTarget` depth, not Minecraft's normal main depth texture.

## Next Diagnostic
- Add `/tornado render mode depth_mainfb`.
- This keeps the same shader-side debug colors as `depth`, but temporarily restores Minecraft's main depth attachment for the tornado draw during the DH post-composite hook.
- Expected interpretation:
  - If the lower/below-player tornado region appears in `depth_mainfb`, the Simple Clouds/DH `cloudTarget` depth attachment is clipping it.
  - If it remains missing, the main depth buffer or `gl_FragDepth` projection is also incompatible.

## Ground Skirt Artifact
- `depth_nofb` showed a large green/yellow flattened shape beneath the funnel.
- Shader inspection found `groundSkirt` is promoted into `outSample.cloud` in both frozen and normal storm sampling.
- Because raymarch opacity is driven by `storm.cloud`, this makes the ground skirt behave like a real volumetric tornado body instead of a light local ground effect.
- Next change: remove `groundSkirt` and broad dust-only terms from the primary `outSample.cloud` field so they cannot create the large under-ground volume. Keep `groundskirt` debug visibility separate.
- Result: fixed. The large flattened under-ground blob is gone in `depth_nofb`.

## Next Diagnostic
- Add `/tornado render mode occlusion`.
- In this mode, the DH post-composite pass detaches framebuffer depth and disables framebuffer depth testing so both main depth and Simple Clouds/DH cloud depth can be sampled safely in the shader.
- The shader then reports which source would occlude the first real tornado hit:
  - Green: accepted by both sampled depths.
  - Red: rejected by vanilla main depth.
  - Magenta: rejected by Simple Clouds/DH cloud depth.
  - White: rejected by both.
  - Yellow: low alpha.
  - Blue: no tornado density hit.
- This is diagnostic only and does not change normal rendering.
- Result: the pass is green, meaning both sampled depth sources accept the tornado. Terrain still visually cuts/overlays the tornado.

## Current Finding
- The remaining problem is render order/compositing, not tornado density and not sampled depth rejection.
- If green debug pixels are still visually cut by terrain, a terrain/color pass is being drawn or composited over the tornado after the current Simple Clouds DH hook.

## Next Diagnostic
- Add `/tornado render mode late`.
- In this mode, skip the current Simple Clouds DH tornado draw and render the tornado from Forge `RenderLevelStageEvent.Stage.AFTER_LEVEL` instead.
- Disable framebuffer depth for this late debug draw and keep shader-side sampled depth available.
- Expected interpretation:
  - If terrain stops cutting the tornado, the final fix should move the DH tornado composite to a late level stage.
  - If terrain still cuts it, something after `AFTER_LEVEL` or the hand/final post chain is overwriting it.
- Result: major improvement. Terrain no longer broadly renders over the tornado. A thin horizontal terrain line remains near the terrain/horizon contact.

## Current Finding
- The primary DH bug is render order/compositing. The Simple Clouds DH hook renders too early for the Project Atmosphere tornado; terrain/color can be composed over it afterward.
- The remaining horizontal line is not the same broad terrain-over-tornado failure. It is likely caused by transparent lower-body pixels allowing already-rendered terrain color to show through, or by shader-side contact/depth rejection right at the ground/horizon transition.

## Next Attempt
- Promote the late `AFTER_LEVEL` path from debug-only to the normal DH tornado render path.
- Keep the old Simple Clouds DH hook skipped while the late path owns DH tornado rendering to avoid double draws.
- Then address the remaining line with a lower-body/contact-only opacity or occlusion adjustment, not a global opacity increase.
- Implementation: normal DH tornado rendering now uses the late `AFTER_LEVEL` path when no explicit tornado debug mode is active. Explicit debug modes other than `late` continue using the old DH hook for diagnostics.
- Implementation: the DH alpha floor now targets accumulated body pixels more strongly (`rawAlpha`-based), reducing already-rendered terrain color bleed through the dense funnel without adding the removed ground-skirt volume back into cloud density.
- Result: major terrain-overdraw issue remains fixed, but a hard horizontal sky/terrain background transition is still visible through low-alpha tornado body pixels.

## Current Finding
- The remaining seam is background color bleed through translucent tornado pixels, not a depth-order failure.
- Because the late pass renders after terrain, any surviving low-alpha tornado pixel will blend with the already-rendered terrain/sky color behind it.

## Next Attempt
- Tighten the DH-only low-alpha body curve:
  - Raise the DH discard threshold slightly so extremely thin pixels at the horizon/contact boundary do not show as a terrain-colored band.
  - Make valid accumulated body pixels reach near-opaque alpha earlier.
- Keep this DH-only and `rawAlpha`-based so it does not reintroduce the removed ground-skirt blob.

## Next Attempt
- User report: a visible horizontal separation remains and looks like two tornado render stages meeting.
- Checked current draw owners:
  - Default Simple Clouds pipeline tornado hook.
  - Shader-support Simple Clouds pipeline tornado hook.
  - DH support pipeline post-composite tornado hook.
  - Forge `AFTER_LEVEL` DH tornado handler.
- Next change: make DH tornado rendering single-owner and single-stage.
  - Render DH tornadoes only from `RenderLevelStageEvent.Stage.AFTER_LEVEL`.
  - Skip the old DH post-composite tornado draw entirely.
  - Guard default/shader-support pipeline tornado hooks when DH is loaded, in case Simple Clouds falls back to those pipelines.
  - Let debug modes render through the same `AFTER_LEVEL` path instead of switching back to the old DH hook.
- Implementation:
  - Removed the old DH post-composite tornado draw injection body.
  - Added DH-loaded guards to the default and shader-support Simple Clouds tornado hooks.
  - Changed the `AFTER_LEVEL` handler to render for all DH tornado modes, not only normal/`late`.
  - Temporarily detaches the main framebuffer depth attachment while the late pass samples that same depth texture, avoiding read/write feedback.
- Build result: `.\gradlew.bat build` passed.
- Result: failed. The visible horizontal gap/separation is still present after forcing DH tornado rendering to a single `AFTER_LEVEL` owner.

## Current Finding
- The remaining gap is not caused by the tornado being rendered in two Project Atmosphere stages.
- Since the single-pass late draw still shows the seam, the remaining cause is likely inside the single render pass:
  - shader-side depth rejection around terrain/contact pixels,
  - the alpha discard/opacity curve creating a hard transition,
  - `gl_FragDepth` or first-hit depth being written inconsistently across the lower body,
  - or the lower funnel density field itself dropping out near the terrain/horizon line.

## Next Diagnostic
- Inspect the current tornado fragment shader's DH path before another visual fix.
- Specifically check:
  - where `wroteDepth` becomes false,
  - DH-only `rawAlpha` discard thresholds,
  - terrain/contact depth bias scope,
  - final alpha/opacity curve,
  - and whether `gl_FragDepth` is skipped for low-alpha lower-body pixels.
- Inspection result:
  - The remaining hard DH cutoff is `rawAlpha < 0.045`, which discards pixels before they can write color/depth.
  - DH opacity then ramps from `rawAlpha` 0.045 to 0.105 and forces surviving pixels near opaque.
  - If the seam aligns with the terrain/sky boundary, it can be caused by low-accumulation rays at the lower funnel edge being discarded or classified as very thin, letting the already-rendered background show through.
- Next change: add a `coverage` debug render mode that does not discard normal low-coverage pixels and instead colors the exact output classification:
  - Blue: no storm depth was written.
  - Yellow: would be discarded by `rawAlpha < minBodyAlpha`.
  - Cyan: survives but is in the DH opacity ramp.
  - Green: solid body coverage.
  - Red: rejected by scene depth.
- Implementation: added `/tornado render mode coverage`.
- Build result: `.\gradlew.bat build` passed.
- Result: the remaining horizontal line is red in `coverage`, meaning it is rejected by the shader scene-depth check.

## Current Finding
- The seam is caused by `sceneReject`, not alpha cutoff and not two render stages.
- In the late DH pass, `sampleSceneDepth()` currently uses the nearest of:
  - Minecraft main depth texture,
  - Simple Clouds/DH `cloudTarget` depth texture.
- A false reject from the secondary Simple Clouds/DH depth can create exactly a horizontal terrain/horizon cut even after the tornado renders late.

## Next Attempt
- Keep the late single-stage render path.
- For that late path, stop passing the Simple Clouds/DH cloud depth as a secondary scene-depth sampler.
- Reason: the late pass should reject against the main scene depth it is compositing over, not against Simple Clouds' earlier cloud/DH depth target that can contain a different horizon/depth representation.
- Expected result:
  - If the red seam disappears, secondary DH/cloud depth was the false reject source.
  - If the red seam remains, the main depth reconstruction/comparison is the source.
- Implementation: late DH tornado pass now passes `-1` for `secondaryDepthTextureId`, so `UseSecondaryDepthSampler` is disabled.
- Build result: `.\gradlew.bat build` passed.
- Result: fixed. The red scene-depth seam disappeared when the late DH pass stopped using Simple Clouds' `cloudTarget` depth as a secondary scene-depth sampler.

## Confirmed Fix
- The final DH tornado render path must be:
  - single-stage at Forge `RenderLevelStageEvent.Stage.AFTER_LEVEL`,
  - sampling Minecraft's main scene depth only,
  - not sampling Simple Clouds' `cloudTarget` depth as secondary depth in the late pass,
  - and not drawing from the older Simple Clouds DH post-composite tornado hook.
- The remaining issue was not two-stage rendering after the single-owner fix. It was a false shader scene-depth rejection caused by mixing the late pass with Simple Clouds' earlier DH/cloud depth target.

## Next Enhancement Reintroduction
- Reintroduce tornado performance enhancements one at a time.
- First target: DH late-path downscaling.
- Constraint: the downscaled pass must preserve the confirmed depth fix by sampling only the main scene depth and compositing only after the late pass, not through the old Simple Clouds cloud target path.
- Planned implementation:
  - Allow the `AFTER_LEVEL` target-override path to use the existing low-resolution tornado render target.
  - Size that downsample target from the final destination target, not from Simple Clouds' cloud target.
  - Composite the downsample result back into the same destination target passed by the late handler.
  - Keep debug modes full-resolution so `coverage`, `depth`, and related diagnostics remain readable and exact.
- Implementation:
  - `renderOpaqueToTarget(...)` now supports downsampling even when rendering to an override target, but only for the DH late path or the original non-override path.
  - The downsample target is sized from the final destination framebuffer.
  - The composite pass writes back to that same destination framebuffer.
  - Debug modes keep full-resolution rendering.
- Build result: `.\gradlew.bat build` passed.
- Result: worked. FPS improved significantly, but the tornado looks pixelated/low-quality at the current downsample factor.

## Next Enhancement Attempt
- Add a cheap alpha-aware upsample filter to the tornado composite shader.
- Reason: the expensive part is the tornado raymarch, not a small fullscreen upscale shader. A 9-tap alpha-weighted filter should smooth low-resolution tornado edges and internal blockiness while preserving most of the downscale FPS gain.
- Constraint:
  - Do not change depth handling.
  - Do not reintroduce the old Simple Clouds DH depth target.
  - Keep the filter local to the existing downsample composite pass.
- Implementation: replaced the one-sample tornado composite with a 9-tap alpha-weighted upsample filter.
- Build result: `.\gradlew.bat build` passed.
- Result: pending user test.

## Result
- Failed. With non-DH downscaling enabled, the tornado is hidden/not visible.
- User also reported switching modes did not recover it.

## Verification
- The only intentional difference from the last known good non-DH state is `useDownsample == true`.
- Full-resolution non-DH was confirmed working before reintroducing downscaling.
- Debug modes can also render nothing if `resolvedDebugStormIndex` remains `-1`, because the render loop filters all tornadoes when debug mode is active but no storm was resolved.

## Next Attempt
- Restore last known good non-DH behavior by disabling downscaling for non-DH normal rendering again.
- Keep DH downscaling enabled because it was confirmed working.
- Fix debug-mode fallback so if no selected storm resolves, debug modes render all prepared tornadoes instead of zero tornadoes.
- Implementation:
  - Re-added the non-DH forced-full downsample guard.
  - Debug render ordering now filters to the selected tornado only if a selected tornado actually resolves.
- Build result: `.\gradlew.bat build` passed.
- Result: failed. User still reports the tornado is broken/not rendering, so the previous downscale changes are still contaminating the non-DH path.

## Current Decision
- Stop adapting the current DH/target-override downsample path for non-DH.
- Hard-disable non-DH downsample at the renderer level to restore the known-good vanilla path.
- Any future non-DH downscale must be a separate implementation and must not share the DH late-path assumptions.

## Current Finding
- Verification found another changed variable after the known-good non-DH state:
  - The known-good non-DH path still let the pipeline hook think it was "downsampled" through `usesDownsamplePath()`, so it skipped `copyDepthFromCloudsToTransparency()` and passed `cloudTarget` depth.
  - Later, the hook was changed to always copy and always pass `cloudTransparencyTarget` depth.
- Since non-DH downscaling is now disabled inside the renderer but the tornado is still broken, restore the previous hook depth-source behavior first.

## Next Attempt
- Keep renderer-level non-DH downscaling disabled.
- Restore default/shader-support mixins to their prior conditional depth-copy/depth-source logic.
- This should match the state that the user reported as good for non-DH.

## User Report
- Non-DH still renders no tornadoes.
- User asks to verify the actual facts and stop guessing.

## Current Suspect To Verify
- The non-DH pipeline hooks now contain broad `SimpleCloudsMod.dhLoaded()` guards.
- If DH is installed/loaded but the active Simple Clouds pipeline is not the DH support pipeline, those guards skip the default/shader-support tornado draw.
- That would produce exactly "no tornadoes without DH" because the non-DH hook returns before rendering.

## Verified Cause
- Found a DH state mismatch:
  - `SimpleCloudsDhPipelineSelector` and `SimpleCloudsRendererDhFallbackMixin` force `DhSupportPipeline` based on `ModList.isLoaded("distanthorizons")`.
  - `TornadoLateRenderDiagnostics` only draws the late DH tornado path when `SimpleCloudsMod.dhLoaded()` is true.
  - Default/shader-support tornado hooks also skip when `SimpleCloudsMod.dhLoaded()` is true.
- If Distant Horizons is installed but Simple Clouds does not consider DH active, Project Atmosphere can force the DH pipeline while the late DH tornado renderer refuses to run.
- This can produce no tornadoes in a "without DH active" test even when the tornado renderer itself is fine.

## Next Fix
- Use `SimpleCloudsMod.dhLoaded()` consistently for choosing/forcing the Simple Clouds DH support pipeline.
- Do not force `DhSupportPipeline` based only on the mod being installed.
- Implementation:
  - `SimpleCloudsDhPipelineSelector` now checks `SimpleCloudsMod.dhLoaded()`.
  - `SimpleCloudsRendererDhFallbackMixin` now checks `SimpleCloudsMod.dhLoaded()`.
- Build result: `.\gradlew.bat build` passed.
- Result: pending user test.

## New Issue
- User report:
  - The inside-tornado fog/whiteout looks bad and should be removed.
  - DH rendering now works with the late path and main-depth-only sampling.
  - Non-DH/vanilla rendering regressed: the lower tornado is hidden by terrain.
  - Non-DH `late` mode does not fix it, but `full` render mode does.
- Interpretation:
  - DH and non-DH rendering should not share all behavior.
  - DH should keep the confirmed late path.
  - Non-DH normal rendering should use the shader behavior that `full` mode triggers, without requiring the user to enable debug mode.

## Next Attempt
- Remove camera whiteout/fog from the tornado interior effect path.
- Keep DH rendering unchanged.
- For non-DH normal tornado rendering, send the shader the same mode value as `full` while leaving Java debug state inactive so:
  - all tornadoes still render,
  - downscaling can still work,
  - debug filtering is not enabled,
  - and the shader uses the full-detail path that fixes the vanilla bottom clipping.
- Implementation:
  - Removed tornado whiteout contribution from `SimpleCloudsWhiteoutFogHandler`; cloud and dynamic atmosphere fog remain untouched.
  - Non-DH normal rendering now sends the shader `DEBUG_FULL` while Java-side debug mode remains `OFF`.
  - DH rendering still uses the corrected late path and does not receive this non-DH shader override.
- Build result: `.\gradlew.bat build` passed.
- Result: failed. Non-DH normal rendering is still broken, while explicit `/tornado render mode full` still works.

## Current Finding
- Sending the shader `DEBUG_FULL` is not sufficient to reproduce explicit full mode.
- Explicit `full` mode also changes Java-side behavior because debug mode is active:
  - it disables the tornado downsample path through `canUseDownsamplePath()`,
  - it changes visibility handling through debug-mode behavior,
  - and it still renders with the normal non-DH target/depth path.
- Since non-DH normal still has the bottom terrain clipping while explicit full does not, the next most likely difference is the non-DH downsample/composite path.

## Next Attempt
- Keep DH downscaling because it was confirmed working and fast.
- Disable downscaling for non-DH normal tornado rendering when using the forced full shader behavior.
- This makes non-DH normal closer to explicit `full` mode without forcing Java debug filtering or affecting DH.
- Implementation: non-DH normal forced-full path now bypasses downscaling; DH late path can still downscale.
- Build result: `.\gradlew.bat build` passed.
- Result: fixed. Non-DH normal rendering now matches explicit `full` mode, but FPS is lower because non-DH downscaling is disabled.

## Next Enhancement Attempt
- User wants non-DH downscaling back because the DH downscaled path has much better FPS.
- Previous non-DH downscaling broke the lower tornado, so do not simply re-enable the old path unchanged.
- Hypothesis:
  - The old non-DH downsample render target does not carry the same terrain/cloud framebuffer depth state as the full-resolution cloud target.
  - Fixed-function framebuffer depth during the low-resolution raymarch can therefore clip or classify the lower body differently.
- Next change:
  - Allow non-DH forced-full normal rendering to use the downsample target again.
  - When rendering to the downsample target, disable framebuffer depth and rely on the shader's sampled scene depth instead.
  - This matches the safer DH late-path strategy while still compositing the result back into the normal Simple Clouds target.
- Implementation:
  - Re-enabled downsampling for non-DH normal forced-full rendering.
  - Disabled fixed-function framebuffer depth whenever the tornado renders into the low-resolution downsample target.
- Build result: `.\gradlew.bat build` passed.
- Result: failed. Non-DH tornado is hidden/not visible, and switching debug render modes did not recover the expected view.

## Current Finding
- The non-DH downsample attempt broke visibility more severely than the previous full-resolution non-DH fix.
- The likely broken part is not the shader `full` behavior itself, because full-resolution non-DH was confirmed fixed before downscaling was reintroduced.
- The non-DH pipeline mixins decide whether to copy depth before calling the renderer:
  - when `usesDownsamplePath()` is true, they skip `renderer.copyDepthFromCloudsToTransparency()`;
  - they then pass `cloudTarget` depth to the shader.
- The confirmed full-resolution non-DH path used the copied transparency depth instead.

## Next Attempt
- Keep non-DH downsample enabled.
- But change the default and shader-support pipeline hooks so they still copy depth to the transparency target before tornado rendering.
- Always pass the copied `cloudTransparencyTarget` depth as the shader scene-depth sampler for non-DH tornadoes.
- Reason: downsampling should only change color resolution, not the depth source.
- Result: failed. User still reports no visible non-DH tornado.

## Current Finding
- The pipeline-selection theory was rejected by the latest user test.
- Stop changing render behavior until the actual failing point is proven.
- The current diagnostic gap is that normal non-DH rendering does not emit path logs unless a tornado render debug mode is active.

## Next Diagnostic
- Add targeted non-DH path logging around:
  - default/shader-support hook entry and early returns,
  - prepared tornado count,
  - `hasVisibleTornado`,
  - hook-selected downsample/depth texture ids,
  - renderer early returns,
  - renderer-selected shader/debug/downsample/framebuffer-depth state,
  - render-order count and submitted draw count.
- Gate these logs behind the existing Tornado Debug Logging config and rate-limit them to once per second.
- Do not change render behavior in this step.
- Result: confirmed root cause for the current non-DH no-render regression.
  - Runtime log shows `DefaultPipeline prepared tornadoes=1 hasVisible=false`.
  - The hook then exits with `DefaultPipeline skipped: no visible prepared tornado`.
  - Therefore the non-DH renderer is not failing in the shader, depth, or downsample composite. It is being skipped before draw because the Simple Clouds pipeline frustum rejects the prepared tornado volume.

## Next Fix
- Stop using the Simple Clouds pipeline frustum as a hard gate for Project Atmosphere tornado volume rendering in non-DH paths.
- Reason:
  - Explicit debug/full mode works because debug mode bypasses frustum visibility by returning true when prepared tornadoes exist.
  - DH already needed frustum handling relaxed because the pipeline frustum can reject the PA tornado volume incorrectly.
  - The shader proxy and depth checks still constrain actual pixels; correctness is more important than skipping this pass from a bad frustum.
- Implementation plan:
  - Add a direct `hasPreparedTornadoes()` check.
  - Use that check in default/shader-support hooks.
  - Pass `null` as the renderer frustum for those non-DH tornado draws so the renderer does not cull the same valid tornado again inside the draw loop.

## Reset And Non-DH Downscale Investigation
- Reset local `Dynamic-Forge-1.20.1-Hurricane` to `c08213f`, the origin hurricane commit containing the `tornado-render-findings-1-5` squash.
- Verified the render-mode tests are present again: `density`, `wallcloud`, `connection`, and `groundskirt` modes exist in `TornadoRenderDebugState` and `tornado_round.fsh`.
- Verified DH still has its dedicated late path through `TornadoLateRenderDiagnostics`, rendering after `AFTER_LEVEL` into the main target with main-depth sampling and `distantHorizonsDepthMode=true`.
- Current non-DH downscale issue:
  - The non-DH mixins call `usesDownsamplePath()` and can choose cloud-target depth / skip transparency-depth copy based on that prediction.
  - The renderer then blocks actual non-DH downscale with `forceNonDhFullPath`, so mixin depth decisions and renderer behavior can disagree.
  - When downscale is enabled, the low-resolution target is transparent, but the tornado pass uses normal alpha blending while drawing into it. That weakens alpha before the composite shader's discard. DH can survive because DH mode raises dense-body alpha, while non-DH can become invisible or clipped.
- Next fix:
  - Keep DH late rendering behavior intact.
  - Make non-DH depth source independent of downscale: always copy and pass `cloudTransparencyTarget` depth before tornado rendering.
  - Re-enable non-DH downscale only as a color-resolution change.
  - For non-DH downsample renders, disable blending into the intermediate target so the composite receives straight color/alpha instead of already-weakened alpha.
- Implementation:
  - Removed the renderer-side `forceNonDhFullPath` block from downsample selection.
  - Default and shader-support non-DH hooks now always copy cloud depth to the transparency target and pass that copied depth to the tornado shader, whether or not color downsample is active.
  - Non-DH downsample rendering now disables blending while writing the low-resolution intermediate target; the existing composite shader remains responsible for blending into the cloud target.
- Build result: `.\gradlew.bat build` passed.
- Result: pending user test.

## Non-DH Downscale Terrain Cut Regression
- User result:
  - DH still works.
  - Non-DH downscale renders again, but terrain depth cuts a large horizontal/diagonal chunk through the tornado.
- Finding from code inspection:
  - The low-resolution tornado pass samples the full-resolution scene depth at one normalized UV per low-res pixel.
  - If that one sampled terrain depth is in front of the first tornado hit, the shader discards the entire low-res tornado pixel.
  - After upscaling, that discarded low-res pixel covers many screen pixels, so the terrain cut becomes much wider than the real full-resolution terrain edge.
  - This is why the problem looks like depth, but the damaging part is the low-resolution pre-discard before composite.
- Next fix:
  - For non-DH downscale only, defer scene-depth rejection out of the low-resolution raymarch.
  - Still write the tornado first-hit depth into the low-resolution depth attachment.
  - During full-resolution composite, sample the low-res tornado color/depth and compare against the full-resolution scene depth per final screen pixel.
  - Keep DH late rendering behavior unchanged.
- Implementation:
  - Added `DeferSceneDepthReject` to the tornado volume shader and enable it only for non-DH downsample rendering.
  - Non-DH downsample now renders the low-resolution tornado with depth test `ALWAYS` and depth writes enabled, so the downsample target stores first-hit tornado depth without letting low-res terrain depth discard the color.
  - The composite shader now samples low-res tornado color, low-res tornado depth, and full-resolution scene depth; when enabled, it performs the final scene-depth rejection at full output resolution.
  - DH downsample/late behavior keeps the old composite behavior by leaving composite scene-depth testing disabled.
- Build result: `.\gradlew.bat build` passed.
- Result: pending user test.
