# Renderer Input Gap Analysis

If a future renderer reads only PA weather values, it will miss important cloud object state.

## Values the renderer would be missing

| Missing value | Why it matters |
|---|---|
| Cloud region id | Needed to track one cloud object across ticks and snapshots. |
| Center position | Needed to place the cloud in world space. |
| Horizontal radius | Needed to size the footprint. |
| Base height | Needed to anchor the cloud volume. |
| Top height | Needed to define the visible cloud thickness. |
| Velocity | Needed to show movement over time. |
| Wind drift | Needed to separate wind influence from the current center position. |
| Age | Needed for growth, decay, and animation decisions. |
| Lifetime | Needed to know when the cloud should persist or disappear. |
| Growth rate | Needed to show the cloud expanding over time. |
| Decay rate | Needed to show the cloud shrinking or dissipating. |
| Density | Needed to control thickness or opacity of the cloud body. |
| Coverage | Needed to know how much area the cloud covers. |
| Edge softness | Needed to control the visual falloff at the perimeter. |
| Vertical growth | Needed to represent cloud development through height. |
| Precipitation potential | Needed to decide how likely the cloud is to rain or storm. |
| Storm intensity | Needed to distinguish a weak cloud from a severe one. |
| Cloud type or profile | Needed to choose the correct profile or visual category. |

## What PA weather values can provide

PA weather values can provide:

- how favorable the atmosphere is for cloud formation
- how strong the storm signal is
- what temperature and humidity support a cloud
- whether rain or snow is plausible
- how wind should bias movement

That is enough to decide whether clouds should exist.

It is not enough to describe the object once it exists.

## What the current debug snapshot already covers

The debug snapshot path already shows the shape of a future cloud-region contract because it includes:

- region center
- region radius
- base height
- top height
- density
- coverage
- edge softness
- wind offsets
- tint

That is helpful, but it is still only a render snapshot.

It does not yet represent the full lifecycle or simulation ownership of an individual cloud region.

## The actual gap

The renderer gap is not just visual.

It is an ownership gap:

- weather state knows conditions
- SimpleClouds knows actual cloud regions
- the renderer wants a stable cloud-region snapshot

Without a cloud-region layer, a renderer has to guess from weather alone or read directly from SimpleClouds object state.

That makes the backend contract unclear and hard to evolve.

