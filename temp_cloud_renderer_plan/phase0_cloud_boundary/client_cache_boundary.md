# Client Cache Boundary

## What A Future `CloudRenderStateCache` Should Own

- The latest immutable cloud render snapshot.
- A small interpolation state for position and density.
- A cached debug fake cloud snapshot.
- A client-only visibility flag.
- A derived render readiness flag.

## Should It Smooth Values?

Yes, but only for visual continuity. It should smooth visual state, not gameplay state.

## Should It Interpolate Values?

Yes, for render position, scale, and density where needed. Interpolation should be a client-only concern.

## Should It Store Immutable Snapshots?

Yes. The cache should store snapshot objects or snapshot-like records, not live simulation objects.

## Should It Know About Simulation?

No. It should not know about forecast generation, storm spawning, or region management.

## Should It Know About Shaders?

No. It may store a few shader-facing numbers, but it should not own shader setup or uniform binding logic.

## Should It Own Debug Fake Cloud Data?

Yes. Debug fake cloud data belongs in the client cache because it is a visual testing tool, not simulation state.

## Boundary Rule

The cache is a **consumer-side state holder**, not a source of truth. It should not decide what the weather is. It should only hold what the renderer needs to draw.

