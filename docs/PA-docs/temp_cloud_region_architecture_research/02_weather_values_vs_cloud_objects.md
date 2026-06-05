# Weather Values vs Cloud Objects

The current codebase clearly separates two concepts, even though they are still connected through SimpleClouds integration.

## Weather values

Weather values describe atmospheric conditions. They answer questions like:

- How humid is this region?
- What is the pressure?
- What is the temperature?
- What direction and speed is the wind moving?
- Is the area stormy, rainy, or snowy?
- What is the cloud cover or rain intensity?

In the current code, these values come from:

- `RegionAtmosphereState`
- `WeatherSnapshot`
- `ForecastSampling`
- `WindVectorApi`
- `WeatherSampler`
- `WindVector`
- `ForecastOrchestrator`
- `AtmosphereStatusSyncManager`

These values are enough to say:

- conditions are favorable for clouds
- the sky should look more overcast
- rain or thunder is likely
- a storm system is strong enough to spawn or intensify clouds

But they do not say that a cloud exists as a world object.

## Cloud region object state

Cloud object state answers different questions:

- Where does the cloud physically exist?
- How large is it?
- How high is its base and top?
- How fast is it moving?
- What is its direction?
- How old is it?
- How long will it live?
- Is it growing or decaying?
- What cloud profile/type does it belong to?

In the current code, this state lives primarily in SimpleClouds `CloudRegion` and the mixins/adapters around it:

- `SimpleCloudsCompat`
- `CloudManager`
- `CloudRegionMixin`
- `CloudRegionTickEventMixin`
- `CloudGeneratorHurricaneReservationMixin`
- `SimpleCloudsCloudManagerMixin`
- `CloudRegionQueue`
- `AtmosphereCloudRegionTracker`
- `HurricaneInstance`
- `TornadoInstance`

This is object state, not just weather.

## Render snapshot values

Render snapshot values are a smaller, renderer-friendly subset of world or cloud state.

The current debug snapshot path already carries:

- region center
- region radius
- base Y
- top Y
- density
- coverage
- edge softness
- wind offsets
- debug tint
- camera position
- dimension
- world time
- partial tick

That is enough for a debug box or future snapshot-based renderer input, but it is still a derived view, not the authoritative simulation layer.

## Renderer only values

Renderer-only values are things that exist only for drawing:

- camera-relative pose
- buffer source
- line / fill render type
- draw order
- shader binding
- vertex submission
- tint / alpha for debug visualization

These should stay out of the simulation layer.

## The key distinction

This is the architectural difference:

### Conditions are favorable for clouds

This means the weather system has enough humidity, pressure, wind, and storm signal to justify cloud spawning or cloud strengthening.

### A cloud exists here with position, size, velocity, density, age, and lifetime

This means there is a concrete object in the world that can move, grow, decay, and be rendered as a specific cloud region.

The first is weather state.
The second is cloud object state.

The current codebase has strong weather state and strong SimpleClouds object state, but not a dedicated PA-owned cloud region simulation layer between them.

