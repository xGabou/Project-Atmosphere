# Cloud Formation Lab

The Cloud Formation Lab is a standalone browser tool for previewing how forecast
and atmosphere values can become persistent Project Atmosphere `CloudField`
state, then feeding that state into a miniature WebGL cloud shader preview. It
is not the Minecraft renderer and it does not launch Minecraft.

## Run

```powershell
.\gradlew.bat cloudFieldSandbox
```

Open:

```text
build/cloud-field-sandbox/cloud-formation-lab.html
```

The generated HTML can be opened directly in a browser. The sandbox also copies
editable preview shaders next to it:

```text
build/cloud-field-sandbox/shaders/cloud_preview.vert
build/cloud-field-sandbox/shaders/cloud_preview.frag
build/cloud-field-sandbox/shaders/cloud_preview_common.glsl
build/cloud-field-sandbox/shaders/cloud_preview_density.glsl
```

Browsers may block relative shader fetches from `file://`. If that happens, the
lab uses its built-in fallback shader and you can use the shader file picker to
import the same shader files manually. Serving the folder with any static local
server also enables relative shader reload.

## Data Path

The lab follows the intended architecture:

```text
ForecastParameterState
-> CloudFormationTargetModel
-> CloudFieldFormationTarget records
-> PersistentCloudFieldState
-> CloudFieldEvolutionController
-> CloudFieldPreviewSnapshotFactory
-> 2D preview renderer
-> WebGL uniform upload
-> miniature GLSL preview
```

This keeps the preview aligned with the later backend flow where real forecast,
weather, or region data will produce source-like targets, while existing fields
evolve gradually instead of being replaced every update.

## Parameters

- `Temperature`: context value for presets and later model tuning.
- `Humidity`: raises field count, density, coverage, hydration, and cloudlets.
- `Pressure`: low pressure raises storm potential and vertical growth.
- `Wind speed`: increases drift speed and adds stretch/shear hints.
- `Wind direction`: controls drift arrows and field displacement.
- `Cloud cover`: raises field count and coverage; high stable cover favors layers.
- `Rain intensity`: raises density and storm potential.
- `Storm chance`: raises storm potential, density, and taller storm-like fields.
- `Instability`: favors cumulus/congestus/storm growth.
- `Vertical development`: raises top height and vertical cloud type.
- `Base Y` / `Top Y`: define vertical preview bounds.
- `Forecast hour`: advances forecast context while persistent fields drift and
  evolve toward target values.
- `Time scale`: controls Play mode speed. Options are realtime, 10x, 100x, and
  1000x.
- `Seed`: controls deterministic placement/cloudlet layout.
- `Region allows clouds`: if false, no new formation targets spawn.
- `Biome moisture`: region-side moisture support for formation.
- `Terrain lift`: simple orographic lift contribution.
- `Front convergence`: simple frontal/convergence spawn contribution.
- `Spawn suppression`: region-side penalty that can block formation.

## Presets

- `Clear sky`: dry, high pressure, near-zero cloud formation.
- `Fair weather cumulus`: small scattered fair-weather fields.
- `Humid cumulus field`: more numerous, denser, hydrated cumulus fields.
- `Stratocumulus / low layer`: broad low stable fields with flatter vertical range.
- `Congestus growth`: tall convective fields with stronger growth.
- `Storm buildup`: low pressure, humid, unstable, dense, tall storm-like fields.
- `Dry dissipation`: weak decaying fields with low hydration.
- `High wind shear`: wind-stretched drifting fields.

## Forecast Mapping And Evolution

The formation model is deliberately simple and readable:

- high humidity increases density, coverage, field count, hydration, and cloudlets;
- high instability increases vertical development and can classify fields as
  congestus or storm buildup;
- low pressure increases storm potential and top-height growth;
- high cloud cover plus stable air creates broad low stratocumulus-like layers;
- high wind moves fields over forecast time and stretches the debug footprint;
- dry air increases decay and reduces density, coverage, and cloudlet counts.

The target model computes desired values only. The evolution controller keeps
persistent fields with stable `fieldId`, `seed`, `center`, `previousCenter`,
`velocity`, current values, target values, age, maturity, and lifetime. Current
radius, density, coverage, storm potential, vertical development, cloudlets, and
kind interpolate slowly toward targets. Storm buildup uses slower rates than
fair-weather cumulus, so storms must mature over time instead of snapping in.

Region simulation is deliberately simple: the target model checks whether the
region allows clouds, then applies biome moisture, terrain lift, front
convergence, and spawn suppression before deciding how many targets to create.

## Time Progression

`Play` advances `forecastHour` continuously using the selected time scale.
`Step 1 Hour` advances one forecast hour. Field centers move by:

```text
center += windVector * deltaTime * movementScale
```

Forecast changes update targets; the fields themselves keep their identity and
move/evolve through inertia.

## Visuals

The top-down map shows field centers, radius, optional placeholder cloudlets,
wind direction, LOD color, density/coverage opacity, decay/storm state, and wind
stretch. The side profile shows base/top height, maturity, current height, and
target height.

The WebGL preview is a separate shader-development viewport. It receives the
selected persistent `CloudField` by default, or all visible fields / one
synthetic forecast field depending on the preview mode. It renders a small soft
volumetric blob approximation with sky color, simple density, simple vertical
falloff, wind-driven noise motion, hydration, coverage, storm darkening, and
basic sun lighting.

Display toggles:

- Show field rings
- Show cloudlet dots
- Show wind arrows
- Show labels
- Show side profile

Cloudlet dots are hidden by default and are always shown for the selected field.
Click a field on the map or table to select it. The selected field also drives
the default WebGL preview. The side panel then shows
current-to-target values and a forecast influence breakdown:

- humidity contribution
- cloud cover contribution
- instability contribution
- low pressure contribution
- rain/storm contribution
- dryness penalty
- stability/layering score

## WebGL Preview Controls

- `Preview mode`: selected field, all visible fields, or one synthetic forecast
  field.
- `Render mode`: shaded preview or density-only debug mode.
- `Density multiplier`, `Coverage multiplier`, `Hydration multiplier`: preview
  only; these do not change forecast values.
- `Lighting strength`: preview-only lighting scale.
- `Preview speed`: controls shader animation time only.
- `Show bounds`: draws approximate debug base/top/radius hints in the shader.
- `Render WebGL`: hard stop/start for the WebGL viewport.
- `Play preview`, `Reset Time`, `Reset Camera`, `Recompile Shader`: local shader
  viewport controls.
- Shader file picker: imports `cloud_preview.vert`, `cloud_preview.frag`, and
  optional include files.

The WebGL uniform contract mirrors the current field snapshot data: resolution,
time, camera, sun direction, field count, center, radius, base/top Y, density,
coverage, growth, decay, humidity influence, wind, vertical development, storm
potential, seed, hydration, age, and cloudlet count. The standalone WebGL path
uses a compact `MAX_FIELDS = 8` array limit for browser compatibility.

The shader loader supports a tiny `#include "file.glsl"` preprocessor for the
preview files. This is a lab import mechanism only and is not final Minecraft
shader loading.

## Intentionally Not Implemented

This tool does not implement final cloud GLSL, raymarched cumulus rendering,
Minecraft renderer integration, Forge runtime code, Simple Clouds integration,
network packets, cloudlet merge/collision, GPU feedback/readback, shadows, or
Atmospheric Shaders integration.

The Canvas visuals and WebGL shader are placeholders. The WebGL preview is only
for appearance iteration and uniform-contract testing. Real Minecraft GLSL
belongs in a later renderer path.
