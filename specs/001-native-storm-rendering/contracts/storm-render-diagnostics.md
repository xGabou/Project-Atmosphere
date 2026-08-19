# Contract: Storm Render Diagnostics

**Feature**: `001-native-storm-rendering`  
**Audience**: maintainers and developers  
**Default behavior**: bounded counters only; no continuous logging

## Command Surface

Extend the existing client command tree. Do not create a second root.

### Summary

```text
/pa cloud volumetric diagnostics
```

Must include a compact storm line with:

- selected/effective quality and adaptive state;
- active/selected/omitted severe groups;
- descriptor count and active/overflow tiles;
- grid cache hit/rebuild/upload counts;
- current topology generation and fallback reason;
- latest CPU build/upload and GPU raymarch timing when available.

### Detailed storm capture

```text
/pa cloud volumetric diagnostics storm
```

Produces one bounded snapshot in chat and, when existing debug capture behavior supports it, the normal latest-log/capture destination. Required fields:

| Category | Fields |
|---|---|
| Ownership | dimension, backend, resolved owner, native active flag |
| Quality | configured mode, effective band, steps, resolution, adaptive enabled, GPU target/EWMA, last transition/reason |
| Distance | overall configured distance, configured/effective storm detail distance, transition width, map extent |
| Groups | active, selected, omitted by distance, omitted by capacity, missing membership |
| Roles | BASE, CORE, TOWER, ANVIL descriptor counts |
| Index | descriptor count/capacity, active tiles, overflow tiles, maximum raw candidates, retained role count |
| Cache | requests, hits, rebuilds, descriptor uploads, grid uploads, target changes, empty clears/skips |
| Async | submitted, completed, coalesced, saturated/skipped, stale discarded, failed, in-flight generation |
| Timing | selection CPU, worker queue wait, worker build, render upload, raymarch GPU, composite GPU where available |
| Generations | requested, completed, adopted, topology, resource target, descriptor signature, grid signature |
| LOD/fallback | analytic groups, cross-fading groups, map-only groups, direct-path state, last fallback/error reason |
| Stability | history valid/reject reason, adopted density generation, camera-density agreement sample when captured |

Formatting must be deterministic and avoid dumping every tile or every ray. Optional per-group detail is capped at the selected group capacity and emitted only on explicit capture.

### Debug views

```text
/pa cloud volumetric debug view storm_body
/pa cloud volumetric debug view storm_envelope
/pa cloud volumetric debug view storm_candidates
/pa cloud volumetric debug view precipitation
/pa cloud volumetric debug view storm_combined
```

Expected output:

| View | Required visualization |
|---|---|
| `storm_body` | Direct analytic severe-cloud density only, color-coded by role |
| `storm_envelope` | Conservative descriptor bounds and analytic/map LOD blend band |
| `storm_candidates` | Candidate count/overflow and selected descriptor identity per sampled tile |
| `precipitation` | Volumetric rain density only, with unsupported/invalid shafts absent |
| `storm_combined` | Final storm body plus precipitation before lighting/composite |

Selecting a debug view may intentionally bypass temporal history as existing diagnostic views do, but returning to `final` must restore normal history initialization predictably.

### Governor reset

```text
/pa cloud volumetric debug governor reset
```

Retain the command and extend its result with selected mode, effective band, target, floor, and transition generation. Reset returns to the selected mode's nominal band and invalidates history only if effective resolution changes.

## Counter Semantics

- `request`: a frame detected or queried a grid requirement.
- `hit`: current adopted grid signature and target matched; no worker build/upload needed.
- `rebuild`: a worker completed a new grid for a unique signature.
- `upload`: render thread transferred a descriptor or grid generation.
- `coalesced`: a pending request was replaced by a newer signature before starting.
- `staleDiscard`: completed result failed adoption token validation.
- `overflowTile`: more than eight conservative candidates intersected a tile before retention.
- `omittedGroup`: entire group used map LOD because distance or whole-group capacity prevented direct admission.
- `topologyGeneration`: changes only when selected group/member identity or order changes, not for interpolation.
- `rebuildFrequency`: adopted grid rebuilds per elapsed second over the existing bounded diagnostic window.

## Performance and Allocation Rules

- Frame recording uses primitive counters, reusable arrays, or the existing bounded ring-buffer pattern.
- Normal rendering must not format strings, enumerate tiles for logging, capture screenshots, or allocate per-descriptor diagnostic objects.
- Detailed formatting and per-group enumeration happen only when a user runs a command or an existing opt-in capture/log mode is active.
- GPU timing must use existing non-blocking query behavior; diagnostics must not force a synchronous pipeline stall.

## Acceptance Signals

Diagnostics are sufficient when a maintainer can answer, from one capture:

1. Which renderer owns clouds and whether direct storms are active?
2. Which quality band and distances were actually used?
3. How many complete groups/lobes were direct, cross-faded, omitted, or overflowed?
4. Did CPU geometry rebuild, why, how often, and on which generation?
5. Was time spent building, uploading, raymarching, or compositing?
6. Did history reset, and was the cause legitimate?
7. Was rain local to supported storm geometry and did CPU whiteout use the adopted generation?
8. Did the system fall back, and to which path?
