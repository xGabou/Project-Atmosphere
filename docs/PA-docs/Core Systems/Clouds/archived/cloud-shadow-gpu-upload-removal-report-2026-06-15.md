# Cloud Shadow GPU Upload Removal Report - 2026-06-15

## Problem Verified

The latest run produced a new JVM fatal error:

- File: `run/hs_err_pid68912.log`
- Time: `2026-06-15 13:11:25`
- Crash: `EXCEPTION_ACCESS_VIOLATION (0xc0000005)`
- Native module: `nvoglv64.dll`
- Thread: `Render thread`

The prior patch changed the failing call from `glTexSubImage2D` to `glTexImage2D`, but the new crash still occurred in the PA shadow upload path:

```text
CloudRenderer.render
CloudShadowRenderer.update
CloudShadowRenderer.uploadShadowTexture
GL11.glTexImage2D
nvoglv64.dll
```

Conclusion: the unsafe operation was the GPU shadow texture upload itself, not only the specific OpenGL upload function.

## Files Modified

- `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/CloudShadowRenderer.java`
- `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/CloudRenderer.java`
- `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/CloudRenderTargetManager.java`
- `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/FallbackDarkeningPass.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/shader/HurricaneShaders.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/shader/TornadoShaders.java`

## Fix Applied

### Cloud shadow crash fix

PA no longer uploads a cloud shadow texture to OpenGL during normal rendering.

Changes:

- Removed `CloudShadowRenderer.uploadShadowTexture`.
- Removed all `glTexImage2D`, `glTexSubImage2D`, pixel-unpack, and ByteBuffer upload logic from `CloudShadowRenderer`.
- `CloudShadowRenderer` still computes the 64x64 CPU shadow grid.
- `CloudShadowRenderer` still publishes `CloudShadowSnapshot`.
- Published shadow snapshots now use `textureId = -1`.
- `CloudRenderTargetManager` no longer creates `cloudShadowTarget`.
- `CloudRenderer` no longer requests or passes a shadow render target.
- `FallbackDarkeningPass.applyTerrainDarkening` now returns `false` because terrain overlay still requires a GPU shadow texture.

### Preserved behavior

The following remain active:

- PA native cloud rendering.
- CPU cloud shadow snapshot generation.
- `CloudShadowMapAccess.sampleShadowAt(...)`.
- Cloud lighting evaluation.
- Fog/player darkening driven by CPU shadow data.

The following is intentionally dormant:

- GPU terrain shadow overlay.

### Shader warning cleanup

Simple Clouds severe-weather shaders are now registered only when `simpleclouds` is loaded.

This avoids loading dormant hurricane/tornado shader programs in PA-native-only runs, reducing startup warnings such as missing hurricane/tornado uniforms.

## Validation

Commands run:

```powershell
.\gradlew compileJava
.\gradlew build
```

Results:

- `compileJava`: passed
- `build`: passed

Static checks:

- No remaining PA cloud shadow `glTexImage2D`.
- No remaining PA cloud shadow `glTexSubImage2D`.
- No remaining `uploadShadowTexture`.
- No remaining cloud shadow target creation.
- `CloudShadowRenderer.update(...)` remains active and CPU-only.

## Remaining Runtime Check

Run the client again with:

```toml
enableCloudShadowMap = true
```

Expected result:

- No new `hs_err_pid*.log`.
- `latest.log` should still show normal `[CloudRender]` entries.
- `shadowDepth` should remain `-1`.
- Hurricane/tornado shader uniform warning spam should be reduced when Simple Clouds is absent.

## Deferred

GPU terrain shadow overlay is disabled until a safe texture-backed implementation is designed.

This does not affect PA cloud simulation, cloud lifecycle, weather simulation, persistence, forecast, tornado logic, hurricane logic, blizzard logic, shaders, or Distant Horizons integration.
