# Classes To Document Before Touching

## `AtmosphereManager`

- Current observed role: global server atmosphere coordinator.
- Why it is confusing: it mixes startup, sync, regen, commands, and cloud queueing.
- What it should own: top-level orchestration only.
- What it should not own: forecast internals, render policy, or compatibility policy.
- Whether it blocks fake debug cloud: no, but it is a source of confusion.
- Whether it blocks PA driven clouds: yes, indirectly, because it hides source-of-truth boundaries.
- Whether to touch now or later: later.

## `ForecastOrchestrator`

- Current observed role: forecast runtime and storage coordinator.
- Why it is confusing: it handles storage, regeneration, login sync, region lookup, and scheduling.
- What it should own: runtime forecast coordination and access.
- What it should not own: renderer logic or storm render metadata.
- Whether it blocks fake debug cloud: no.
- Whether it blocks PA driven clouds: yes, because the future snapshot boundary must know where forecast truth lives.
- Whether to touch now or later: later.

## `CloudManager`

- Current observed role: atmospheric cloud lifecycle and region scanning.
- Why it is confusing: it mixes climate application, spawn decisions, and telemetry-like behavior.
- What it should own: cloud lifecycle state and atmosphere projection helpers.
- What it should not own: renderer pass policy or client cache ownership.
- Whether it blocks fake debug cloud: no.
- Whether it blocks PA driven clouds: yes, because it is too broad to be an unquestioned source.
- Whether to touch now or later: later.

## `SimpleCloudSpawner`

- Current observed role: weather-driven cloud selection and spawn search.
- Why it is confusing: it owns policy, search behavior, and telemetry, not just spawning.
- What it should own: spawn strategy helper behavior.
- What it should not own: climate truth or renderer behavior.
- Whether it blocks fake debug cloud: no.
- Whether it blocks PA driven clouds: yes, because cloud selection heuristics should not leak into render logic.
- Whether to touch now or later: later.

## `ClientTickHandler`

- Current observed role: client orchestration for fog, tornadoes, audio, sky, particles, culling, and smoothing.
- Why it is confusing: it is a large catch-all client loop.
- What it should own: client tick coordination only.
- What it should not own: simulation ownership or shader setup.
- Whether it blocks fake debug cloud: no, but it is a likely place for confusion.
- Whether it blocks PA driven clouds: yes, because a future render cache should not be hidden inside a general tick hook.
- Whether to touch now or later: later.

## `SimpleCloudsCompat`

- Current observed role: bridge between PA cloud logic and Simple Clouds.
- Why it is confusing: it owns adapter behavior plus radius policy, wind coupling, and cloud creation.
- What it should own: adapter behavior only.
- What it should not own: core weather policy or render state policy.
- Whether it blocks fake debug cloud: no, because fake debug cloud can be separate.
- Whether it blocks PA driven clouds: yes, because it is currently too much of a policy hub.
- Whether to touch now or later: later.

