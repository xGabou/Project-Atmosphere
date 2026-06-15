# Latest Log Check - 2026-06-15

Inspected files:

- `run/logs/latest.log`
- `run/logs/debug.log`
- `run/hs_err_pid14212.log`

## Primary Finding

The game did not exit through a normal Java/Minecraft crash report. It hit a native JVM fatal error:

- Error: `EXCEPTION_ACCESS_VIOLATION (0xc0000005)`
- Thread: `Render thread`
- Native module: `nvoglv64.dll`
- Problematic frame: `C [nvoglv64.dll+0xb9e4ec]`
- JVM note: crash happened outside the Java VM in native code

## Project Atmosphere Render Path Involved

The native crash occurred while Project Atmosphere was uploading the cloud shadow texture:

```text
org.lwjgl.opengl.GL11C.nglTexSubImage2D
org.lwjgl.opengl.GL11C.glTexSubImage2D
org.lwjgl.opengl.GL11.glTexSubImage2D
net.Gabou.projectatmosphere.clouds.client.render.CloudShadowRenderer.uploadShadowTexture
net.Gabou.projectatmosphere.clouds.client.render.CloudShadowRenderer.update
net.Gabou.projectatmosphere.clouds.client.render.CloudRenderer.render
net.Gabou.projectatmosphere.clouds.client.render.CloudRenderHook.onRenderLevel
```

Relevant source path:

- `src/main/java/net/Gabou/projectatmosphere/clouds/client/render/CloudShadowRenderer.java`

The crash points at:

- `CloudShadowRenderer.uploadShadowTexture(...)`
- `GL11.glTexSubImage2D(...)`

## Last PA Render Logs Before Crash

The final PA render state in `latest.log` was:

```text
[CloudState] stage=AFTER_PARTICLES screen=PauseScreen worldTime=6000 adapter=projectatmosphere:vanilla quality=HIGH steps=64 scale=1.00000 main=854x480 mainColor=5 mainDepth=6 cloud=854x480 cloudColor=50 cloudDepth=51 shadowDepth=-1 intermediate=true downscaled=false
```

The last successful cloud render log before that showed:

```text
[CloudRender] quality=HIGH steps=64 scale=1.00 ... snapshots=1/1 rendered=1 ... composite=true ... lastCloud=cumulus_congestus/morphology=TOWER/.../radius=72.67
```

## Config State

Cloud shadow map publication is enabled:

```text
run/config/projectatmosphere-common.toml:
enableCloudShadowMap = true
```

Source default:

```text
AtmoCommonConfig.ENABLE_CLOUD_SHADOW_MAP = true
```

## Other Log Issues

These are present but do not appear to be the immediate crash cause:

- Missing Simple Clouds sound registry entries:
  - `simpleclouds:close_thunder`
  - `simpleclouds:distant_thunder`
- Missing datapack warning:
  - `Missing data pack mod:simpleclouds`
- Shader uniform warnings for hurricane/tornado shaders.
- Optional Fabric mixin class lookup warnings.
- `Sea level requested before initialization; defaulting to 60f.`

## Assessment

Confirmed:

- This is a native OpenGL/NVIDIA driver crash, not a caught Java exception.
- The active Java call stack was inside PA cloud rendering.
- The specific PA operation was uploading the cloud shadow map with `glTexSubImage2D`.
- Cloud shadow maps were enabled in config.

Not proven from logs alone:

- Whether the texture id was invalid.
- Whether the texture storage was incomplete.
- Whether the crash is caused by PA texture lifecycle, driver behavior, pause-screen timing, or another GL state interaction.

## Immediate Workaround

Set this in `run/config/projectatmosphere-common.toml`:

```toml
enableCloudShadowMap = false
```

That should bypass `CloudShadowRenderer.update(...)` shadow publication and avoid this crash path while keeping the rest of cloud rendering active.

## Likely Code Area To Fix

If you want this fixed in code, the next pass should focus on:

- Validating `cloudShadowTarget` color texture id before upload.
- Logging shadow target creation and texture ids.
- Checking GL errors around shadow target creation and upload.
- Avoiding `glTexSubImage2D` unless texture storage is known valid.
- Considering full `glTexImage2D` allocation or a safer managed upload path for the shadow map.
