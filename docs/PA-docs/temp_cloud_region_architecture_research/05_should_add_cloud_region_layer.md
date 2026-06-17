# Should PA Add a Cloud Region Layer?

## Verdict

Yes.

PA should add a dedicated `CloudRegionState` style layer before PA-driven clouds.

## Why

The current architecture separates weather from cloud objects, but it does not separate weather from cloud-region simulation.

PA already has:

- weather values
- region forecasts
- wind and storm scoring
- cloud spawning policy
- bridge code into SimpleClouds

What it does not have is a PA-owned object model for:

- where one cloud region lives
- how it moves
- how old it is
- how it grows or decays
- how it maps weather conditions to a concrete region

That missing layer is exactly the boundary a future PA-driven cloud system needs.

## What problem it solves

It solves the gap between:

- atmospheric conditions
- and a cloud region that can be rendered or simulated as a distinct object

It also gives a stable place for:

- region identity
- motion over time
- lifecycle aging
- density / coverage / softness
- source-weather linkage
- renderer snapshot generation

## What problem it does not solve

It does not solve:

- actual renderer implementation
- cloud mesh generation
- shader behavior
- camera transforms
- SimpleClouds integration details

It also does not replace weather modeling.

## Should this happen before filled volume rendering?

Yes, ideally.

If the debug or filled-volume renderer is going to represent actual cloud regions, the backend contract should exist first so the renderer is not built directly on weather scalars.

## Should this happen before real PA-driven rendering?

Definitely yes.

A real PA-driven renderer needs a stable backend contract:

`PA weather values -> CloudRegionState -> CloudRenderSnapshot -> renderer`

That is the cleaner architecture for long-term maintenance.

