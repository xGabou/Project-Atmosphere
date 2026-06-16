# Cloud Shadow Crash Fix Report - 2026-06-15

## Problem

`latest.log` ended without a normal Java crash report. The matching JVM fatal error file showed:

- Crash type: `EXCEPTION_ACCESS_VIOLATION (0xc0000005)`
- Native module: `nvoglv64.dll`
- Thread: `Render thread`
- PA path:

```text
CloudRenderer.render
CloudShadowRenderer.update
CloudShadowRenderer.uploadShadowTexture
GL11.glTexSubImage2D
nvoglv64.dll
```

The crash happened while Project Atmosphere uploaded the cloud shadow texture.

## Root Cause Assessment

The direct native crash was in the NVIDIA OpenGL driver, but the PA-side risky operation was:

```java
GL11.glTexSubImage2D(...)
```

The old upload path assumed:

- The target texture already had valid storage.
- No other renderer had left `GL_PIXEL_UNPACK_BUFFER` bound.
- Default unpack alignment was safe.
- It was safe to publish the shadow texture id even if upload state was invalid.

Those assumptions are unsafe in a modded render pipeline. If a pixel-unpack buffer is still bound, a CPU `ByteBuffer` texture upload can be interpreted incorrectly by the driver. That can produce exactly the kind of native crash seen in `nvoglv64.dll`.

## File Modified

- `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/CloudShadowRenderer.java`

## Fix Applied

The cloud shadow upload path now:

1. Validates the shadow target texture id.
2. Validates upload dimensions and backing value array size.
3. Rejects upload if the target is smaller than the requested shadow map.
4. Saves current GL texture binding.
5. Saves current unpack alignment.
6. Saves current pixel-unpack-buffer binding.
7. Explicitly unbinds `GL_PIXEL_UNPACK_BUFFER` before CPU `ByteBuffer` upload.
8. Sets `GL_UNPACK_ALIGNMENT` to `1`.
9. Uses `glTexImage2D` for explicit 64x64 RGBA8 texture allocation and upload instead of `glTexSubImage2D`.
10. Checks and logs GL errors after upload.
11. Restores previous texture binding.
12. Restores previous unpack alignment.
13. Restores previous pixel-unpack-buffer binding.
14. Publishes texture id `-1` if GPU upload fails, while keeping CPU shadow data available in the snapshot.

## Why This Fix Is Not Just A Workaround

Disabling `enableCloudShadowMap` avoids the path completely, but does not fix the unsafe upload.

This fix keeps cloud shadow map publication enabled while making the upload path safe in a shared OpenGL state environment.

## Behavior Impact

Gameplay simulation is unchanged.

Cloud simulation is unchanged.

Rendering visuals are intended to remain the same when upload succeeds.

If upload fails, PA now avoids exposing a bad/stale GPU texture id and logs the failure instead of continuing with unsafe state.

## Validation

Commands run:

```powershell
.\gradlew compileJava
.\gradlew build
```

Results:

- `compileJava`: passed
- `build`: passed

No build failures were introduced.

## Follow-Up Runtime Test

Run the client again with:

```toml
enableCloudShadowMap = true
```

Then confirm:

- No new `hs_err_pid*.log` is generated.
- `latest.log` has no `[CloudShadow] glError` warnings.
- Cloud rendering still works.

If a `[CloudShadow] glError` warning appears, the log should now identify the failing GL upload context instead of hard-crashing immediately.
