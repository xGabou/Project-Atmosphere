# Phase 0 Cloud Renderer Contract

## 1. Purpose

Phase 0 exists only to create a safe renderer boundary and a fake snapshot path.

It is not a real cloud renderer. It is a minimal contract that lets a future debug cloud render without pulling live PA weather, Simple Clouds policy, or broad manager logic into the renderer.

## 2. Non Goals

Phase 0 does **not**:

- read real PA weather
- sync cloud data
- render realistic volumetric clouds
- generate shadows
- integrate Atmospheric Shaders
- refactor existing broad managers
- touch SimpleClouds tornado or hurricane renderers

## 3. Ownership Rules

### `CloudRenderSnapshot`

Owns:

- one immutable render-ready view of a cloud
- debug-only fake cloud fields for phase 0
- a minimal set of renderer-facing values

Must never own:

- live simulation logic
- packet transport
- shader binding code
- render pass execution
- weather generation rules

### `CloudRenderStateCache`

Owns:

- the current cloud render snapshot
- optional client-only interpolation state
- a debug fake snapshot reference
- a stable read boundary for the renderer

Must never own:

- simulation authority
- forecast generation
- packet encoding or decoding
- render pipeline setup
- shadow or shader logic

### `Future CloudRenderSnapshotBuilder`

Owns:

- transformation from backend weather state into immutable render snapshot data
- validation of render-safe values
- any future conversion from climate state to visual state

Must never own:

- direct drawing
- packet transport
- simulation mutation
- compatibility policy for Simple Clouds

### `Future CloudRenderer`

Owns:

- reading the current snapshot
- turning snapshot data into draw calls
- debug cloud or placeholder cloud rendering
- render-pass decisions that are purely visual

Must never own:

- weather simulation
- packet sync
- region state mutation
- forecast generation
- source-of-truth ownership for weather

### `Future CloudDensityProvider`

Owns:

- mapping snapshot or backend values into density values
- simple density policy
- later PA-driven density interpretation

Must never own:

- geometry generation
- lighting and shadow composition
- packet sync
- simulation ownership

### `Future CloudShadowRenderer`

Owns:

- later cloud shadow visual output
- shadow-specific draw logic
- only shadow rendering concerns

Must never own:

- simulation data ownership
- density generation
- cloud spawn policy
- generic cloud rendering decisions

### `Future CloudLightingBridge`

Owns:

- translation from cloud state into lighting inputs
- later atmospheric lighting hints
- tint, brightness, and fallback darkening signals

Must never own:

- weather simulation
- spawn logic
- shadow map rendering itself
- generic renderer orchestration

## 4. Minimal Fake Snapshot Fields

The first fake snapshot must stay small. It should contain only the fields needed to draw one debug cloud.

| Field | Type | Owner | Purpose | Fake Value | Required Now | Required Later | Notes |
|---|---|---|---|---|---|---|---|
| `enabled` | `boolean` | `CloudRenderSnapshot` | Turns the fake cloud on or off | `true` | Yes | Yes | Core debug toggle |
| `dimension` | `String` | `CloudRenderSnapshot` | Identifies where the cloud belongs | `"minecraft:overworld"` | Yes | Yes | Keep as a simple identifier for phase 0 |
| `worldTime` | `long` | `CloudRenderSnapshot` | Lets the renderer know the current world time | `0L` | Yes | Later | Debug-only placeholder now |
| `partialTick` | `float` | `CloudRenderSnapshot` | Allows light interpolation during render | `0.0f` | Yes | Yes | Keep client-owned |
| `cameraPosition` | `Vec3`-like value | `CloudRenderSnapshot` | Gives the renderer a view-relative anchor | fixed value | Yes | Yes | Used only for view-relative debug drawing |
| `regionCenter` | `Vec3`-like value | `CloudRenderSnapshot` | Sets the fake cloud center in world space | fixed value | Yes | Yes | Fake cloud anchor |
| `regionRadius` | `float` | `CloudRenderSnapshot` | Controls overall fake size | fixed value | Yes | Yes | Simple volume radius |
| `cloudBaseY` | `float` | `CloudRenderSnapshot` | Lower bound of the visible cloud volume | fixed value | Yes | Yes | Minimal vertical placement |
| `cloudTopY` | `float` | `CloudRenderSnapshot` | Upper bound of the visible cloud volume | fixed value | Yes | Yes | Minimal vertical placement |
| `density` | `float` | `CloudRenderSnapshot` | Controls fake cloud opacity or fill | fixed value | Yes | Yes | Simple renderer input |
| `coverage` | `float` | `CloudRenderSnapshot` | Controls how much of the volume appears filled | fixed value | Yes | Later | Useful for later weather-driven clouds |
| `edgeSoftness` | `float` | `CloudRenderSnapshot` | Softens the cloud boundary | fixed value | Yes | Later | Good later for realistic clouds |
| `windOffsetX` | `float` | `CloudRenderSnapshot` | Fake horizontal drift on X | `0.0f` | Yes | Yes | Debug-only motion placeholder |
| `windOffsetZ` | `float` | `CloudRenderSnapshot` | Fake horizontal drift on Z | `0.0f` | Yes | Yes | Debug-only motion placeholder |
| `debugColorOrTint` | `int` or color tuple | `CloudRenderSnapshot` | Lets the debug cloud be visually obvious | fixed debug tint | Yes | Yes | Keep simple for phase 0 |

## 5. Access Rules

### Who writes the snapshot

- Phase 0: a client tick or debug initializer writes the fake snapshot.
- Later: a future snapshot builder writes the real snapshot.

### Who reads the snapshot

- Only the future cloud renderer reads the current snapshot.
- The renderer reads it through `CloudRenderStateCache.getCurrentSnapshot()`.

### When it can be mutated

- Only before the snapshot is published into the cache.
- Never while the renderer is actively reading it.

### When it must be immutable

- Once the snapshot is stored in `CloudRenderStateCache`.
- Once it is visible to the renderer.

### What thread should own render reads

- The render thread should own render reads.
- The cache can receive updates from the client side, but render access must be read-only and stable.

### What should never access simulation classes during render

- The future cloud renderer.
- The fake debug renderer.
- Any cloud lighting or shadow bridge used by the renderer.
- Any render-facing cache reader.

## 6. First Fake Renderer Path

The first flow must be:

1. Client tick or a debug initializer creates a hardcoded fake snapshot.
2. `CloudRenderStateCache` stores that snapshot.
3. The render hook reads `CloudRenderStateCache.getCurrentSnapshot()`.
4. The renderer draws one debug cloud or placeholder volume.
5. No real PA weather is used.
6. No packet is used.
7. No Simple Clouds integration is required.

This is the only acceptable phase 0 rendering flow.

## 7. Future PA Driven Path

The later real flow should be:

1. Server owns weather.
2. Client receives weather state.
3. Client cache smooths values.
4. `CloudRenderSnapshotBuilder` creates an immutable snapshot.
5. Renderer reads only the snapshot.

That path is documented now so the phase 0 fake cloud can later be replaced without changing the renderer boundary.

## 8. Safety Rules

The following rules are strict:

- No direct reads from `RegionAtmosphereState` in renderer
- No direct reads from `ForecastGenerator` in renderer
- No direct reads from `AtmosphereManager` in renderer
- No direct reads from `ForecastOrchestrator` in renderer
- No shader integration in Phase 0
- No shadow map in Phase 0
- No SimpleClouds renderer refactor in Phase 0

## 9. Phase 0 Exit Criteria

Phase 0 is complete only when all of the following are true:

- `phase0_contract.md` exists
- minimal snapshot fields are defined
- cache ownership is defined
- renderer access rules are defined
- fake renderer path is defined
- future PA driven path is documented
- no real source files were changed

## 10. Phase 1 Ready Checklist

| Checklist item | Why needed | Risk if skipped |
|---|---|---|
| Define the first fake snapshot class shape | The renderer needs one stable input object | The boundary becomes ambiguous |
| Define the client cache ownership | The renderer needs a single read source | State will be mutated from too many places |
| Define the debug snapshot factory | Phase 0 needs a safe hardcoded creator | Fake cloud setup will leak into other code |
| Define the render hook entry point | The future cloud draw path needs one place to start | Render logic will spread out immediately |
| Keep the snapshot immutable after publication | Read stability on the render thread depends on it | Race conditions and visual inconsistency |
| Keep real PA weather out of phase 0 | The fake cloud must stay isolated | The debug path will become coupled to simulation |
| Keep Simple Clouds renderers untouched | They are not part of phase 0 | The boundary work will turn into a compatibility refactor |

## 11. Verdict

Phase 0 contract is ready for code: **yes**

First implementation should touch existing PA weather: **no**

First implementation should touch SimpleClouds renderers: **no**

First implementation should create only isolated client render boundary classes: **yes**

