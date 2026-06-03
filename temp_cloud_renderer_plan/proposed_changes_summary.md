# Proposed Changes Summary

This is a design-only summary. Nothing in the mod has been implemented for the future cloud renderer yet.

## What the current architecture already does well

- Weather simulation is separated from rendering.
- Forecasts are already region-based and persisted.
- Tornadoes and hurricanes already have snapshot-style client render data.
- Client-side fog and weather smoothing already exist.
- Simple Clouds hooks already provide a clear render boundary.

## What the future cloud renderer should build on

1. A server-owned cloud/weather snapshot.
2. A client-side immutable render snapshot.
3. A render controller that only reads that snapshot.
4. Shader uniforms for lighting, shadows, opacity, and fallback darkening.
5. A single place for render-stage selection, including DH or non-DH paths.

## Missing pieces that should be added later

- A dedicated cloud render snapshot type.
- A dedicated cloud lighting snapshot type.
- A dedicated cloud shadow snapshot type.
- A client cache for render-frame cloud state.
- A renderer entry point that reads the snapshot and emits GPU work.
- A uniform contract for cloud lighting and shadowing.

## What should not change yet

- No source files were modified for this architecture pass.
- No build files were changed.
- No packages or class names were renamed.
- No renderer implementation was added.

## Recommended implementation order later

1. Define the render snapshot contract.
2. Build the client cache that assembles that snapshot.
3. Wire the renderer to the snapshot only.
4. Add lighting and shadow uniforms.
5. Add fallback darkening and fog blending.
6. Add quality levels and low-end fallback behavior.

