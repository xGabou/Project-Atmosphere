# T187 - The rain-field ceiling is 1.1756x, and the build arithmetic is 7.6 to 1

Branch `experiment/cloud-descriptor-k`, parent `443e742` (T186). Nothing merged.
One campaign 08Sep2026, Ultra, raw scale 0.2500, cloud target 480x270,
framebuffer 1920x1080, 96 steps, 60 samples per cell, three anchor-bracketed
blocks per arm per pose. `T187_RAIN_FIELD_BEGIN poses=2 arms=19` - 38 cells,
`T187_REJECTED count=0` at cell level, two FAR blocks rejected on block-level
anchor drift (section 10).

**This is a feasibility pass, not an implementation.** No field was built. Tasks
3, 4, 8, 9 and 10 are deliberately not done - see section 8.

## 1. Headline

**The ceiling clears the stop condition: 1.1756x at SIDE** (accepted, 2.28%
spread) and **1.1129x at FAR** (accepted, 2.93%). The brief's gate was 1.10x.

**The build arithmetic is the enabling number.** The field is 512x512 = **262,144
cells** on the existing weather-map domain, against **1,994,647** rain-segment
descriptor traversals per frame. That is **7.61 traversals saved per cell built**.

Combining the two, a real field retains roughly 87% of the ceiling's saving
before lookup cost, which puts a realistic estimate at **~1.13-1.15x SIDE** -
the **strong candidate** band, not the major-win band. That figure is **DERIVED**
from two measured quantities, not measured itself.

**And the ceiling stack reads 7.7483 ms p50 at SIDE** - below the 8 ms stretch
target - though its composition ratio is rejected (section 7).

## 2. Task 1 (items 1-3) - what the field must store

`localRainSupportAt` is mostly cheap already. `sampleWeather` and
`sampleMorphology` are two texture fetches; the coverage, base-height, profile
and family terms are scalar arithmetic on their results. **One call is
expensive**: `directStormRainSupportAt`, which runs `directStormLocalBaseAt`'s
descriptor walk and then `directStormFinalDensity`'s full candidate/group union.

It returns exactly three values:

| Field value | Type | Source |
|---|---|---|
| `directSupport` | float | `directStormFinalDensity` body union |
| `stormBaseY` (attach height) | float | `directStormLocalBaseAt` BASE-weighted average |
| `ownsDescriptorGroup` | bool | `directStormShape`'s ownership union |

**That is the whole field.** `localSupport`, `attachY`, `precipitation`,
`familyStrength`, `weather` and `morphology` all stay cheaply computed after the
lookup - storing them would add bandwidth for arithmetic that costs less than the
fetch.

**Dependencies (T184, re-confirmed):** every input is `worldXZ` plus frame
uniforms. No `Y`, no segment endpoints, no camera, no jitter. So:

**One field serves both Gauss positions with no approximation.** They differ only
in XZ, and the field is a function of XZ. The two-sample rule needs no special
handling.

**Exactness limitation:** the only approximation a field introduces is **spatial
quantisation** - a cell covers a finite XZ area. Nothing else about the function
is approximated.

## 3. Task 2 (items 4-7) - the domain already exists

`sampleWeather` and `stormCandidatesAt` both use the same mapping:

```glsl
vec2 uv = (worldXZ - WeatherOrigin) / WeatherExtent;
```

**Reusing it preserves exact semantics and requires no new coordinate work** - no
scrolling logic, no recentering rule, no edge policy to invent. `sampleWeather`
already defines the out-of-domain behaviour (a fixed fallback vec4) and an edge
fade over the outer 5.5%.

| Property | Value |
|---|---|
| coordinate system | `WeatherOrigin` / `WeatherExtent`, shared with weather and candidate maps |
| resolution at ULTRA | **512 x 512** (`VolumetricQualityProfile` weatherMapSize) |
| cells | **262,144** |
| blocks per texel | `WeatherExtent / 512` - the same texel size `ClientCloudVisualDensity` already computes |
| memory, RG16F | 512 x 512 x 4 B = **1 MB** |
| memory, RGBA16F (flag in a third channel) | **2 MB** |
| scrolling / recentering | inherited - none to design |
| edge behaviour | inherited from `sampleWeather` |

Memory is not a constraint at any resolution worth considering.

## 4. Task 6 (item 15) - GPU, and the pass already exists

`VolumetricCloudRenderer` already builds **weather, morphology, cumulus stage
support and cumulus stage base** targets per frame through
`VolumetricCloudRenderTargets.prepare*Target(profile.weatherMapSize())`.

**So GPU field generation is an additional target in an existing per-frame pass
over the identical domain, not a new architecture.** The descriptor payload is
already GPU-resident, there is no upload round trip, and no new synchronisation
point is introduced.

CPU generation is rejected on the evidence rather than by preference: the field
must reproduce `directStormFinalDensity`'s union **exactly**, and this codebase
has a documented history of CPU/GPU formula drift - T172 exists precisely because
a CPU precompute had to be proved bit-identical to the shader, and the T172
invariant still checks that pairing every build. Duplicating the union on the CPU
would create a second, larger instance of that problem.

## 5. Task 7 (items 20-25) - the measured ceiling

`t187_field_oracle` makes `directStormRainSupportAt` cost nothing while leaving
every other part of the function exactly as production computes it. A real
lookup costs more than zero, so this is a strict upper bound.

| Pose | Arm | blocks | ratioMean | spread | verdict |
|---|---|---|---|---|---|
| SIDE | **`t187_field_oracle`** | 3 | **1.1756** | 2.28% | **accepted** |
| FAR | `t187_field_oracle` | 3 | **1.1129** | 2.93% | **accepted** |
| SIDE | `t187_stack_field` | 3 | 2.5232 | 0.26% | accepted |
| FAR | `t187_stack_field` | 2 | 3.0148 | 5.40% | REJECTED_ratio_spread |

Traversals removed: **1,994,647 per frame at SIDE** (15.39/pixel), which is
44.12% of `directStormShape` calls. Group walks and exact SDFs fall in the same
proportion, since the rain path's share of each was measured together in T182 and
T186.

**Image cost of the ceiling.** Read this as *the rain a real field must
reproduce*, not as damage a field would cause - the oracle deletes
descriptor-owned rain outright, which is the whole point of the bound:

| Arm | pose | meanAbs | changed px | cloud SSIM | silhouette IoU | thin ret. | hole ret. |
|---|---|---|---|---|---|---|---|
| `t187_field_oracle` | SIDE | 2.649e-05 | 292 | 0.999917 | 0.998656 | 0.889344 | 1.000000 |
| `t187_field_oracle` | FAR | 4.788e-06 | 85 | 0.999900 | 0.999486 | 0.964286 | 1.000000 |
| `t187_stack_field` | SIDE | 2.160e-03 | 20117 | 0.988584 | 0.995423 | 0.725410 | 0.992701 |
| `t187_stack_field` | FAR | 4.704e-04 | 3892 | 0.973137 | 0.990508 | 0.535714 | 0.950000 |

The stack rows are dominated by the stack's own components - footprint LOD, early
termination, light steps 4 - not by the oracle; the oracle's isolated cost is the
first two rows, and even there the only metric that moves is thin retention,
consistent with removing rain wisps rather than cloud body. **These are cloud
metrics and they are not the rain gate.** Task 4's rain-specific metrics do not
exist yet, so nothing here licenses a quality claim about rain.

## 6. Task 5 (items 16-19) - build cost, derived

**Not measured. Derived from two measured quantities, and labelled as such.**

| Quantity | Value | Source |
|---|---|---|
| rain-segment traversals per frame | 1,994,647 | measured, T186 SIDE |
| field cells at 512x512 | 262,144 | `VolumetricQualityProfile` ULTRA |
| **traversals saved per cell built** | **7.61** | derived |

Each cell costs one `directStormRainSupportAt` - the same work a traversal costs
- so the field build is roughly **13.1%** of the traversal work it removes,
leaving **~86.9%** of the ceiling before lookup cost.

Applying that to the measured ceiling: the ceiling removes
`1 - 1/1.1756 = 14.94%` of SIDE frame time; retaining 86.9% of that is 12.98%,
i.e. **~1.149x**. Lookup cost then subtracts from it - roughly 18 texture fetches
per pixel replacing 15.39 traversals, and T170 measured descriptor fetch
bandwidth at approximately 1.0x, so this term is small but not zero.

**Realistic estimate: 1.13-1.15x SIDE. DERIVED, not measured.** Lower resolutions
improve the build ratio further (a 256x256 field is 30.4 traversals per cell) at
the cost of spatial quantisation, which is exactly the trade Task 8 would sweep.

## 7. Task 12 (items 31-37) - stack

| Pose | Arm | ratioMean | spread | verdict | session-local p50/p95 |
|---|---|---|---|---|---|
| SIDE | `t172_stack_pre` (control) | 1.8202 | 15.08% | REJECTED_ratio_spread | 10.8411 / 12.8686 |
| SIDE | **`t187_stack_field`** | 2.5232 | **0.26%** | **accepted** | **7.7483 / 8.4357** |
| SIDE | within-block vs control | 1.3988 | 15.41% | REJECTED_ratio_spread | - |
| FAR | `t187_stack_field` | 3.0148 | 5.40% | REJECTED | 4.2173 / 4.5619 |

| Target | Status at the ceiling |
|---|---|
| SIDE <= 10 ms locally | **yes** - 7.7483 session-local, arm accepted at 0.26% spread |
| SIDE <= 8 ms locally | **yes** - 7.7483, marginally under |
| FAR <= 8 ms locally | **yes** - 4.2173 |

**Read this carefully.** The stack arm's own ratio is accepted with an unusually
tight 0.26% spread, and 7.7483 ms clears both targets. But the *composition*
ratio against the control is rejected at 15.41%, because the control itself
spread 15.08% - the same instability that has blocked every stack comparison
since T181. So the correct statement is: **at the ceiling, with rain traversal
free, the stack reaches 7.75 ms session-local; the gain attributable to the
change is not established this session.**

And this is the ceiling, not a candidate. A real field lands above 7.75 ms.

## 8. What was not done, and why

The brief's Tasks 3, 4, 8, 9 and 10 - the resolution oracle, rain-specific
quality metrics, the resolution sweep, temporal update frequency and camera
scrolling - are **not done**. That is a deliberate ordering decision, not an
omission:

- The brief's own stop conditions gate everything on the ceiling clearing 1.10x.
  Building a rain-metrics harness and a rain-heavy fixture for an architecture
  that might be dead on cost would have been wasted work.
- The rain metrics need a **rain-only capture path** - rain and cloud are
  composited in the reference frames, so a rain mask cannot be extracted from
  them reliably - plus a fixture with enough rain for continuity and onset
  statistics to mean anything. T186 measured 11,028 rain-positive segments per
  frame on this fixture, which is far too sparse.

Both are now justified work, because the ceiling passed.

## 9. Decision (items 38-41)

**38. Architecture verdict: proceed.** The ceiling is 1.1756x SIDE, above the
1.10x gate; the domain and the per-frame GPU pass already exist; the field is
three values in 1-2 MB; and the build arithmetic is 7.61 traversals saved per
cell built. None of the brief's stop conditions fired.

**39. Production recommendation: build the field, but do not treat 1.1756x as the
expected gain.** The defensible expectation is **1.13-1.15x**, derived. It is a
strong candidate, not the 1.2-1.3x structural win the strategic target asks for -
so it should be pursued on its merits and not as the thing that closes the 10 ms
gap by itself.

**40. Remaining dominant workload if it were rejected:** light tap at 26.45% and
empty-span probe at 15.69%, which together are 42% - a plausible next target for
the same field treatment if they share an evaluation, which is the brief's
architecture question 3.

**41. Next task: build the GPU rain-support field.** In order:
1. Add the rain-only capture view and the rain-specific metrics (rain IoU, missed
   and false rain, onset and termination height, shaft continuity), plus a
   rain-heavy fixture. These gate acceptance and cannot be retrofitted after.
2. Add the field target to the existing per-frame pass at 512x512, storing the
   three values, and read it in `localRainSupportAt`.
3. Measure the real gain against the derived 1.13-1.15x, then sweep resolution
   downward while the rain metrics hold.
4. Only then consider update frequency and scrolling - both are optimisations of
   a field that must first be shown to pay.

## 10. Harness

- `T187_REJECTED count=0` at cell level. **Two FAR blocks were rejected at block
  level on anchor drift** (0.0666 and 0.0315 against the 0.030 tolerance), which
  is why both FAR arms report `blocks=2` while SIDE reports 3. SIDE's nine blocks
  all passed, with drift between 0.0015 and 0.0181.
- `T145_RAIN_GATE verticalFalseNegatives=0|ellipseFalseNegatives=0`.
- `T183_RAIN_RENDER programs=3|rainReachablePostSpecialization=true`.
- `T182_CONSUMER_TAGS assigned=9|counted=9|untaggedCounterPresent=true`.
- `T175_WORKLOAD_VIEWS declared=38|allEnabled=true`.
- `T170_WIRING campaigns=31|violations=0`.
- FINAL verified free of the arm: `#define PA_ARM_RAIN_FIELD_ORACLE` count 0,
  rain render call still present.
- **The first campaign launch crashed the client** during shader-variant loading
  (exit -805306369) after six minutes of compilation, with 127 variants now
  declared. A clean relaunch completed all 38 cells, so it was transient rather
  than a hard limit - but 127 full-size fragment programs compiled at every
  startup is close enough to a cliff to be worth pruning before it becomes a
  recurring blocker. The invariants reference roughly 90 of them, so that prune
  is its own task.

## 11. Status

Not merged. Productionization `4b34b12`; T170 `9239077`, T171 `aff6fc7`,
T172 `85ff4ef`, T173 `81476b3`, T174 `3af2015`, T175 `3cfd1f1`, T176 `b047bc3`,
T177 `f868325`, T178 `cb1197b`, T179 cleanup `42e655d`, T179 `655bec1`,
T180 `738b5d7`, T181 `624d7f0`, T182 `1bbda6a`, T183 `6defa18`, T184 `88cf4f4`,
T185 `4c2941e`, T186 `443e742`.
