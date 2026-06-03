# Snapshot Boundary Design

## Needed Now

- `dimensionId`
- `position`
- `radius`
- `height`
- `density`
- `colorTint`
- `debugMode`
- `renderEnabled`

## Needed For PA Driven Clouds Later

- cloud cover
- storm severity
- weather phase
- wind vector
- rain intensity
- humidity influence
- cloud type id
- compatibility flags

## Needed For Shadows Later

- shadow strength
- shadow softness
- optical depth hint
- sun attenuation hint
- vertical darkening profile

## Needed For Atmospheric Shaders Later

- shader density multiplier
- lighting blend factor
- translucency hint
- sky tint influence
- fallback darkening factor

## Not Needed Yet

- region forecast history
- biome sampling history
- storm genesis metadata
- spawn heuristics
- cloud region allocation details
- server persistence metadata
- client camera history beyond minimal interpolation

## Boundary Note

The first snapshot should be intentionally small. For phase 0, the snapshot is only a **render contract**, not a simulation contract. The renderer should not be able to mutate it, and the snapshot should not know how it was produced.

