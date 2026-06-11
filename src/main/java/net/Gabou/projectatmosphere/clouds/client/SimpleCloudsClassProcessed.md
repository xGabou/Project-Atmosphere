# Simple Clouds Integration Gap Audit

Scope: vanilla Minecraft linking and weather integration only. Excludes optional visual work like cloud textures, fog, or shader styling.

## What Is Already Covered

- `isRainingAt`, `isRaining`, `isThundering`, `getRainLevel`, and `getThunderLevel` are already bridged on the client side through `ClientLevelWeatherMixin` and `ClientLocalizedWeatherState`.
- Server-side weather queries are already dimension-aware through `WeatherCloudQueries`, including localized rain and thunder sampling.
- Weather commands are already intercepted through `WeatherCommandMixin` and `WeatherCommandBridge`.
- Rain/snow and freeze logic is already partially covered through `WeatherStateMixin`, `BiomeFreezingMixin`, `SimpleCloudsCloudManagerMixin`, and `ServerLevelSnowStormMixin`.
- Crash interception exists through `MinecraftCrashHandlerMixin`, so the project is not missing basic crash safety.

## Missing Or Partial Compared To Simple Clouds

### 1. Window resize and renderer lifecycle

- Missing: the simpleclouds `GameRenderer.resize` hook that explicitly notifies the renderer when the window changes size.
- Missing: the simpleclouds `GameRenderer.close` shutdown path that closes renderer-owned GPU resources.
- Missing: the simpleclouds `Minecraft.setLevel` hook that resets renderer state when the client world changes.
- Partial: `CloudRenderTargetManager` can recreate targets lazily, but there is no equivalent explicit resize callback or level-change reset path.

Relevant project files:
- `clouds/client/render/CloudRenderTargetManager.java`
- `mixin/client/MixinLevelRenderer.java`
- `mixin/client/MinecraftCrashHandlerMixin.java`

### 2. Dimension gating

- Missing: a `canRenderInDimension` equivalent.
- Simple clouds only renders in allowed dimensions and supports whitelist/blacklist behavior.
- Project Atmosphere currently cancels vanilla cloud rendering unconditionally in `mixin/client/MixinLevelRenderer.java`, so there is no per-dimension allow/deny path yet.
- Partial: the weather sampler is dimension-aware through region data, but the actual render pipeline is not gated by dimension.

### 3. Custom rain rendering

- Missing: the simpleclouds `renderSnowAndRain` override that replaces vanilla rain rendering with custom rain visuals when enabled.
- Missing: the related `tickRain` rain-sound scaling hooks that make vanilla rain audio follow the localized rain level.
- Project Atmosphere currently has rain/thunder state, but it does not have the equivalent vanilla weather rendering suppression or the rain-sound constant tweaks.

### 4. Custom rain sounds

- Missing: the sound registration replacement path from `MixinSoundManagerPreperations`.
- Missing: the simpleclouds `customRainSounds` behavior that swaps vanilla rain sounds for modded ones.
- Project Atmosphere has other weather sounds, but no direct equivalent to the simpleclouds rain-sound replacement pipeline.

### 5. Thunder compatibility

- Missing: the simpleclouds trident/channeling redirect in `MixinThrownTrident`.
- Effect: channeling lightning does not get redirected to cloud-local thunder logic the way simpleclouds does.
- Partial: thunder state itself is already exposed on both client and server, but vanilla thunder-linked entity behavior is not fully tied to it.

### 6. Vanilla weather lifecycle control

- Present but different: simpleclouds only blocks vanilla weather cycle changes when its manager says vanilla weather should not be used.
- Project Atmosphere currently disables `advanceWeatherCycle` unconditionally in `ServerLevelWeatherCycleMixin`.
- That is not a missing hook, but it is a behavioral divergence from simpleclouds and should be called out because it affects how broadly the feature applies.

### 7. Precipitation block updates

- Partial: Project Atmosphere already handles rain/snow compatibility through biome freezing and precipitation queries.
- Missing or not found: the simpleclouds-style chunk tick helper that applies vanilla precipitation side effects on chunks, such as cauldron filling and snow accumulation during localized weather.
- If this behavior exists elsewhere, it is not surfaced through a simpleclouds-equivalent mixin path.

## Short Verdict

- The project already covers the core weather queries and precipitation detection.
- The biggest gaps versus simpleclouds are client lifecycle wiring, dimension gating, custom rain rendering, custom rain sounds, and thunder/channeling compatibility.
- The rain/snow side is partially covered, but simpleclouds still has more explicit vanilla compatibility plumbing for chunk tick effects and rain-related client presentation.

