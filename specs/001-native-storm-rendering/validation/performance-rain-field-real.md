# T188 - the real field pays 1.1158x net, and it invents rain

Branch `experiment/cloud-descriptor-k`, parent `6f315fa` (T187). Nothing merged.
Two campaigns 08Sep2026, Ultra, raw scale 0.2500, cloud target 480x270,
framebuffer 1920x1080, 96 steps, 60 samples per cell, three anchor-bracketed
blocks per arm per pose, 25 arms x 2 poses. `T188_REJECTED count=0` in both.

Run C is the timing run. Run D repeats it with the rain-field forced onto the
diagnostic monolith, which is the only program that can read workload counters -
without that the ray-side fetch count and the post-field attribution both read
zero, not because the field did nothing but because the program that counts was
not using it.

## 1. Headline

**The field is real, it works, and T187's derivation was right.** T187 derived
1.13-1.15x from build arithmetic alone. Measured net, including the build:

| Pose | run | march ratio | **net ratio** | spread | verdict |
|---|---|---|---|---|---|
| **SIDE** | D | 1.1256 | **1.1158** | 2.24% | **accepted** |
| SIDE | C | 1.1404 | 1.1307 | - | 1 block |
| FAR | C | 1.1608 | **1.1450** | 0.49% | **accepted** |
| FAR | D | 1.0910 | 1.0776 | 3.58% | REJECTED_ratio_spread |

**SIDE lands at 1.1158, accepted on three blocks**, corroborated by run C's
1.1307. That is the brief's **1.10-1.15 strong production candidate** band, and
it is the derived prediction confirmed rather than merely not contradicted.

**The build is nearly free: 0.2266-0.2458 ms** for 262,144 cells, stable to
within 8% across every arm and both poses.

**And the field invents rain.** Missed rain is 0.75-1.02%; false rain is
**10.6-15.6%**, in both runs and at both poses. Rain IoU 0.858-0.895. That is a
quality result no previous campaign could have produced, and it is the reason
this is not a recommendation to ship.

## 2. Task 3 - the structural proof

The rain path's descriptor traversal does not shrink. It leaves the ray.

| Consumer, SIDE | pre-field (run C) | post-field (run D) |
|---|---|---|
| rain segment | 750,507 (55.26%) | **0** |
| rain shaft | 17,314 (1.27%) | **0** |
| light tap | 232,280 (17.10%) | 221,036 (38.41%) |
| empty-span probe | 214,205 (15.77%) | 218,431 (37.96%) |
| primary body | 99,186 (7.30%) | 98,082 (17.04%) |
| refinement | 44,659 (3.29%) | 37,853 (6.58%) |
| **tagged shape calls** | **1,358,167** | **575,422** |

**57.6% of the ray's descriptor-shape calls removed**, with
`shapeAccountingClosed=true`, residual 30, `shapeUntagged=0` on both sides.

Ray-side field use, measured rather than assumed:

| Counter | Value |
|---|---|
| `rainFieldFetches` | 2,098,025 - 2,098,366 |
| `rainFieldFallbacks` | **0** |

**Zero fallbacks.** Every rain-relevant column lies inside the existing weather
domain, so the exact-traversal path at the domain edge never fires. The domain
choice inherited from `sampleWeather` needs no extension.

Build side, from view 62 (one field cell per capture pixel, stratified):

| Per cell | SIDE | Scaled to 262,144 cells |
|---|---|---|
| shape calls | 1.000 | 262,144 |
| group walks | 0.180 | 47,180 |
| lobe visits | 1.800 | 471,900 |
| exact SDFs | 0.786 | 205,990 |

**8.00 ray fetches per cell built** (2,098,025 / 262,144). T187 derived 7.61
from T186's traversal count; the measured figure is 8.00.

## 3. Task 4 - cost

| Quantity | Value |
|---|---|
| cells per frame | 262,144 (512 x 512) |
| **field build GPU p50** | **0.2266 - 0.2458 ms** |
| field build GPU p95 | 0.2314 - 0.2529 ms |
| target format | RGBA32F |
| bytes per cell | 16 |
| **VRAM** | **4 MB** |

RGBA32F rather than the RGBA16F the neighbouring maps use, because channel G is
an attach height in world blocks and half float carries an ulp of 0.5 near a
1000-block storm top - that would add a storage error on top of the spatial one
this field has to be measured for, and confound the two.

**`t188_field_generate_only` is the control that prices the build directly.** It
pays the whole generation pass and still marches the exact descriptor path:

| Pose | run | net ratio | spread | verdict |
|---|---|---|---|---|
| FAR | C | 0.9967 | 1.50% | accepted |
| FAR | D | 0.9900 | 2.57% | accepted |
| SIDE | C | 0.9897 | 5.94% | REJECTED |
| SIDE | D | 0.9655 | 3.66% | REJECTED |

Building the field and not reading it costs **0.3-1.0%** at FAR, where the arm is
accepted twice. So the gain in `t188_field_real` is the lookup replacing
traversal, not an artefact, and the build gives back roughly 1.4 points of the
~16% march saving.

## 4. Task 5 - exactness, and the failure it exposes

Measured on the rain-only capture, not on cloud SSIM:

| Metric | SIDE (C) | SIDE (D) | FAR (C) | FAR (D) |
|---|---|---|---|---|
| rain IoU | 0.8834 | 0.8763 | 0.8951 | 0.8581 |
| **missed rain** | 1.01% | 0.75% | 1.02% | 0.80% |
| **false rain** | **12.07%** | **13.26%** | **10.58%** | **15.60%** |
| thin-rain retention | 0.9725 | 0.9827 | 0.9691 | 0.9787 |
| mean onset error | 5.58 | 5.11 | 3.06 | 4.68 |
| max onset error | 162.75 | - | 95.72 | - |
| shaft continuity | 0.9612 | 0.9655 | 0.9621 | 0.9355 |
| newly broken shafts | 42 | 32 | 11 | 16 |

**The error is asymmetric by an order of magnitude, consistently, in four
independent measurements: the field deletes almost no rain and invents a lot.**

That asymmetry identifies the mechanism. Ownership is a boolean; the bilinear
filter returns it as a fraction across a cell boundary, and thresholding at half
a cell still dilates the ownership union outward by up to half a texel - four
world blocks - in every direction. Rain attaches wherever the storm is deemed to
own the column, so a dilated ownership union produces exactly this signature:
a small ring of new rain around every shaft, no lost interior, and a handful of
shafts broken where an interpolated attach height crosses a threshold.

Thin-rain retention at 0.97-0.98 and missed rain under 1% confirm the rain that
should be there is essentially all there. The defect is entirely in what was
added.

## 5. Task 6 - performance decision

Primary metric, net including field generation, against the brief's thresholds:

| Threshold | SIDE result |
|---|---|
| >= 1.15x - excellent | no |
| **1.10-1.15x - strong production candidate** | **yes: 1.1158 accepted** |
| 1.03-1.10x - borderline | - |
| < 1.03x - reject | - |

FAR straddles the band across runs (1.1450 accepted, 1.0776 rejected) and is the
less stable pose this session.

**No stop condition fired.** Net SIDE is above 1.10; build cost is 1.4 points of
a 16-point saving rather than most of it; memory is 4 MB; and there is no
synchronisation - the generation pass is one more target in a per-frame pass that
already exists, with no readback.

## 6. Task 7 - stack

| Pose | run | arm net ratio | spread | verdict | session-local cloud / cloud+field p50 |
|---|---|---|---|---|---|
| SIDE | D | 2.5225 | 2.34% | accepted | 12.1842 / **12.4290** |
| SIDE | C | 2.2515-2.2692 | 0.78% | accepted | 14.1302 / 14.3759 |
| FAR | D | 3.1961 | 14.80% | REJECTED | 6.1024 / **6.3355** |
| FAR | C | 3.0388-3.2396 | 0.78% | accepted | 6.0488 / 6.2826 |

| Target | Result |
|---|---|
| SIDE <= 10 ms locally | **no** - 12.43 ms |
| SIDE <= 8 ms locally | no |
| FAR <= 8 ms locally | **yes** - 6.34 ms |

**Read the SIDE absolute with the session in mind.** This session's `lean_final`
anchors sit at 32-34 ms where T187's sat at 19.7 ms, so the whole session is
roughly 1.65x slower and session-local absolutes are not comparable across
sessions. The ratio protocol exists precisely because of that; the ratio is the
result and 12.43 ms is not.

The within-block stack composition against `t172_stack_pre` is **rejected in both
runs** (SIDE 1.4718 at 10.23%, 1.2899 at 3.19%; FAR 1.9800 at 14.85%, 1.1212 at
6.81%), because the control itself spread 12.38% and 3.5%. That is the same
instability that has blocked every stack comparison since T181, and it is not
evidence either way.

## 7. Task 11 - post-field attribution

With rain traversal gone from the ray, the workload is a different shape:

| Consumer | share |
|---|---|
| **light tap** | **38.41%** |
| **empty-span probe** | **37.96%** |
| primary body | 17.04% |
| refinement | 6.58% |
| rain | **0%** |

**Light and probe are now co-dominant at 76.4% together.** T187's percentages are
obsolete, as the brief anticipated. The next optimization must be chosen from
this table, not from the pre-field one - and the obvious question is whether the
light tap and the empty-span probe share an evaluation that the same field
treatment could hoist, since uniform work removal is the only shape that has paid
in this codebase.

## 8. What was not done, and why

**Task 8 (resolution sweep), Task 9 (update frequency) and Task 10 (camera and
domain behaviour) are not done.**

Task 8 is deliberately withheld rather than skipped. The sweep moves resolution
*downward*, and the measured defect is ownership dilation - which a coarser field
makes strictly worse, because the dilation is half a texel and the texel grows.
Sweeping resolution before fixing the ownership channel would produce a table
whose every row is dominated by an error that has a cheaper fix. The right next
move is to stop interpolating ownership, not to make the cells bigger.

Tasks 9 and 10 are optimisations of a field whose quality is not yet accepted. At
0.23 ms per frame, rebuild frequency is also not where the cost is: skipping
every other rebuild would save about 0.12 ms and risk temporal lag against a
budget of 12 ms.

## 9. Decision

**Architecture verdict: accepted on performance, not yet on quality.** Net SIDE
1.1158 accepted, build 0.23 ms, 4 MB, zero fallbacks, 57.6% of ray-side
descriptor work removed, and the rain path eliminated from the march entirely.

**Production recommendation: do not ship it as it stands.** 10.6-15.6% false rain
and 32-42 newly broken shafts are a visible change to precipitation, and the
brief's own stop list includes "rain quality cannot be preserved". It is not yet
established that it cannot be - only that this first, deliberately
highest-quality-practical implementation does not.

**Next task: fix the ownership channel, then re-measure.** In order:
1. Stop bilinearly interpolating ownership. The cheapest correct options are a
   `NEAREST`-fetched ownership channel while support and height stay filtered, or
   a conservative threshold well above 0.5 that erodes rather than dilates the
   union. Both are small changes to `paRainFieldSupportAt`.
2. Re-run the rain-mask pair. The target is false rain at or below missed rain,
   which would make the field's error symmetric and small.
3. Only then sweep resolution downward, with the rain metrics as the gate.
4. Then update frequency and camera behaviour, which are cheap by comparison.

If the ownership fix does not bring false rain down, the field is still a 1.12x
net gain that changes precipitation visibly, and that trade is the user's call
rather than a measurement question.

## 10. Two defects found and fixed

**A thirteenth JSON sampler crashed the volumetric pass.** Minecraft assigns
shader-JSON samplers to consecutive texture units from zero and tracks exactly
twelve; `RainFieldSampler` as a thirteenth threw
`ArrayIndexOutOfBoundsException: Index 12 out of bounds for length 12` inside
`ShaderInstance.apply`, which disabled the volumetric pass for the session. The
run then reported `descriptors=0`, discarded its cell as `fixture_changed`, and
sat for 32 minutes before timing out. Fixed by binding the field manually on
PA-owned unit 15 - the same mechanism `PuffCandidateMapSampler` and both noise
volumes already use - and asserted by an invariant that counts the JSON samplers
against `CloudTextureUnitContract`.

**The field GPU timer was sticky.** `CloudGpuTimer` holds its last resolved
result, so a `lean_final` anchor reported `rainFieldP50=0.2109` for a pass it
never ran. Left in, that would have charged every control for the candidate's
build cost and understated the gain. Now zero unless the frame actually
generated, and asserted.

Neither is a rain-field design problem; both would have silently corrupted the
result.

## 11. Harness

- `T188_REJECTED count=0` in both runs.
- `T188_FIELD_SOURCE definitions=1|generatedByProductionFunction=true|exactPathRetained=true|domainShared=true`
- `T188_FINAL_CLEAN bakedUniforms=2|fieldNotAJsonSampler=true|generationGated=true|separatelyTimed=true`
- `T188_RAIN_MASK_HARNESS maskVariants=2|metrics=8|missedAndFalseSeparated=true|rainHeavyFixture=true`
- `T170_WIRING campaigns=32|armMatrices=28|violations=0` (+5-mutation negative proof)
- `T175_WORKLOAD_VIEWS declared=40|allEnabled=true`
- `T182_CONSUMER_TAGS assigned=9|counted=9`, `T183_RAIN_RENDER programs=3`
- `T145_RAIN_GATE` false negatives 0 on both probes.
- `T123` instrumentation: 115 guarded counter mutations, 2 rain-mask mutations
  inside `#ifdef PA_RAIN_MASK_OUTPUT` - a stronger guarantee than the runtime
  guard, since that block is not compiled into any program that does not define
  the macro.

**Shader variants: 128 -> 133.** T188 adds five, all campaign-scoped: two field
arms, the generate-only control, and the rain-mask pair. The T187 report stated
127; the correct figure at that commit was 128. The startup-crash risk that
report flagged is correspondingly higher now, and the prune it recommended is
still its own task.

**FINAL is unchanged by T188.** It bakes both field uniforms to 0, so neither the
generation pass nor the lookup branch is compiled into it, and the field texture
is not one of its samplers.

## 12. Status

Not merged. T172 `85ff4ef`, T173 `81476b3`, T174 `3af2015`, T175 `3cfd1f1`,
T176 `b047bc3`, T177 `f868325`, T178 `cb1197b`, T179 `655bec1`, T180 `738b5d7`,
T181 `624d7f0`, T182 `1bbda6a`, T183 `6defa18`, T184 `88cf4f4`, T185 `4c2941e`,
T186 `443e742`, T187 `6f315fa`.
