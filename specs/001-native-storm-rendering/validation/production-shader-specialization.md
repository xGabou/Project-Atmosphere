# T161 - Production FINAL shader specialization

Status: **BANKED** 2026-09-03

/ Implementation commit: `4844cc8`

/ Feature: 001-native-storm-rendering
/ Depends on: T139 (`3790752`)
/ Reference experiment: `e301494` on `experiment/core-cost` - evidence, not an
  implementation to merge.

## 1. Why the split exists

`cloud_atmosphere_volume.fsh` is a single ~6,950-line program that carries both
the normal renderer and every dormant diagnostic, oracle, trace, legacy and
alternate-output path built for T098, T121-T123, T133, T136, T141, T149 and
T153. Those paths are selected by uniforms. A uniform is not known at compile
time, so the driver cannot prove any of them unreachable: it keeps their code,
allocates registers for their live ranges, and preserves their control flow
inside the hot march loop even though an ordinary frame never enters one.

The core-cost experiment measured what that costs. On one PLAY_VIS_NEAR / Ultra
/ 480x270 / ten-descriptor fixture, replacing the diagnostic selectors with
constants before compilation produced a **pixel-identical** image at **2.984x**
the speed (216.15 ms p50 -> 72.45 ms p50). That establishes execution-context
specialization as a dominant cost class. It does not establish a hardware
mechanism: register pressure, occupancy and latency hiding remain hypotheses
until counters are available.

T161 ports that architecture into the production tree. It does not merge the
experiment branch.

## 2. Architecture before and after

**Before.** One program, `cloud_atmosphere_volume`, bound by every frame.
`VolumetricCloudRenderer` uploaded 19 diagnostic selector uniforms on every
draw, including ordinary FINAL frames, where all of them were inert.

**After.** The same source compiles twice.

| Program | Resource | Bound by | Diagnostic paths |
|---|---|---|---|
| `DIAGNOSTIC_MONOLITH` | `cloud_atmosphere_volume` | any frame with a diagnostic selector active | present, unchanged |
| `LEAN_FINAL` | `cloud_atmosphere_volume_final` (generated) | ordinary FINAL frames | statically unreachable, eliminated before register allocation |

The lean program is emitted by the `generateLeanFinalShader` Gradle task from
the unmodified production `.fsh`, so there is exactly one source of truth for
the rendering equations. A declaration the task cannot find fails the build
rather than silently emitting an unspecialized program.

## 3. What is compiled out of FINAL

Each uniform below becomes the constant an ordinary FINAL frame uploads.

| Uniform | Baked value | Owning diagnostic |
|---|---|---|
| `PaDiagnosticStepBudget` | `0` | T098 march-budget control arm |
| `PuffDensityStage` | `0` (FINAL) | density-stage cuts |
| `PuffTierFilter` | `-1` (ALL) | tier cuts |
| `DebugView` | `0` (FINAL) | debug views 1-23, incl. quadrature, lighting, workload, trace |
| `PaDiagnosticOptimizationMode` | `0` | T121/T122/T133/T147/T149/T153 arms |
| `PaDiagnosticEvalEpsilon` | `0.0` | T141 evaluation-amplify arm |
| `PaOraclePass` | `0` | T153 ground-truth pass |
| `PaOracleBaseSize` | `vec2(1.0)` | T153 ground-truth pass |
| `StormTraceOrigin` | `vec2(0.0)` | T128 centre-line material trace |
| `StormTraceYStart` | `0.0` | T128 |
| `StormTraceYInterval` | `1.0` | T128 |
| `StormTraceSamples` | `2` | T128 |
| `StormTraceStage` | `0` | T128 |
| `PaLegacyHitDepth` | `0` | T098 legacy depth evidence arm |
| `PaLegacyFinePromotion` | `0` | T098 legacy promotion evidence arm |
| `PaDiagnosticLightingMode` | `0` | T136 constant-lighting attribution |
| `PaRayTraceMode` | `0` | T098 production ray trace |
| `PaRayTraceNdc` | `vec2(0.0)` | T098 production ray trace |
| `PaRayTraceFragCoord` | `vec2(0.0)` | T098 production ray trace |

Five of these carry a runtime value in the monolith rather than a constant
(`PaOracleBaseSize`, `PaRayTraceNdc`, `PaRayTraceFragCoord`, and the two trace
shape values). Each is read only behind a gate that is itself baked off, so the
baked value is never observed. This was verified by reading every use site:
`PaOracleBaseSize` only under `paT153GroundTruthPass()`, the ray-trace pair only
under `paRayTraceActive()`, and the trace values only under `DebugView == 21`.

`PuffShapeMode` and `StormTopologyMode` are **not** specialized. They are
genuine production selectors that ordinary frames vary, and they remain uniforms
in both programs.

### Direct evidence that elimination actually happened

The linked lean program does not expose `OracleIntervalSampler`:

```
[Render thread/WARN] [minecraft/ShaderInstance]: Shader
projectatmosphere:cloud_atmosphere_volume_final could not find sampler named
OracleIntervalSampler in the specified shader program.
```

That texture is read only by the T153 ground-truth replay. With `PaOraclePass`
baked to 0 the driver removed the read, and with it the sampler - so the dead
paths are gone from the linked program, not merely unvisited. The generated
JSON now drops that sampler and the 18 specialized uniforms it no longer
declares, leaving 50 uniforms instead of 68.

## 4. Program selection and fallback

`VolumetricCloudRenderer.selectProgram` chooses before anything binds. The lean
program is eligible only when every uniform it bakes is genuinely at its baked
value:

- `debugView == FINAL` (covers `raymarchDebugView`, `StormMaterialRuntimeTrace`, `StormWorkloadRuntimeCapture`)
- `diagnosticStepBudget == 0`
- `puffDensityStage() == FINAL`, `puffTierFilter() == ALL`
- `optimizationDiagnosticMode() == NORMAL_PRODUCTION` (covers the T153 oracle and every T147/T149 arm)
- `!StormProductionRayTrace.active()`, `!StormMaterialRuntimeTrace.active()`
- `!t098LegacyHitDepth()`, `!t098LegacyFinePromotion()`, `!t136ConstantLighting()`

Anything else falls through to the monolith. Because every existing campaign
sets its selector through `VolumetricCloudDebugConfig` or one of those statics,
**no campaign needed changing**: each one automatically links the program that
still contains its code.

**Fallback.** If a FINAL frame cannot bind the lean program,
`LeanFinalProgramUnavailableException` propagates to the render hook, which
disables the volumetric pass for the session and logs the cause - the
established native fallback. The monolith is deliberately *not* substituted: it
would draw the identical image while silently costing what T161 removed, so
every image check would stay green and the regression would appear only as lost
frames. A link failure has to be visible rather than merely slow.

## 5. Build-time gate

`stormVolumetricGeometrySandbox` now runs
`T161 lean FINAL shader specializes and compiles`, which
1. asserts none of the 19 uniforms survives as a `uniform` in the generated
   program, and each was replaced by the expected `const` declaration, and
2. compiles the generated program on a real GL context.

A generator that silently stopped substituting would otherwise emit a program
identical to the monolith, keep every image check green, and give back the whole
gain. That failure mode is now a build failure.

## 6. Image A/B

Same-fixture, same-frame comparison between the two linked programs, driven by
the T161 arm of `StormT132AutoDriver` (marker `run/t161-final-specialization.txt`).
Both arms render the identical pose, fixture, quality mode and resolution, with
history bypassed and the world clock pinned to one value, so the only variable
is which program is bound.

Fixture: PLAY_VIS_NEAR, Ultra, 1920x1080 framebuffer, 480x270 cloud target,
10 descriptors, `effectiveReferenceWorldTime=2270.55981`.

```
T161_IMAGE arm=lean_final          boundProgram=lean_final          digest=d90e60c8881dec9b
T161_IMAGE arm=diagnostic_monolith boundProgram=diagnostic_monolith digest=d90e60c8881dec9b

T161_IMAGE_AB a=diagnostic_monolith b=lean_final
  evaluated=true passed=true
  maxAbsRGBA=0.000000e+00  meanAbsRGBA=0.000000e+00  rmsRGBA=0.000000e+00
  changedPixelCountAboveEpsilon=0  totalComparedPixels=129600
  epsilon=4.882813e-04 epsilonBasis=rgba16f_storage_ulp
  informationalDigestA=d90e60c8881dec9b informationalDigestB=d90e60c8881dec9b
  digestsEqual=true
```

| Quantity | Result |
|---|---|
| Changed pixels | **0 / 129,600** |
| Maximum error | **0.0** (exact, not within epsilon) |
| Reference digest | **identical** (`d90e60c8881dec9b`) |
| Uniform signature | identical (`a4e419de58a558a6`) |
| Weather-map input signature | identical (`303503cf3ecd3b98`) |

The output is bit-identical, not merely within the storage epsilon. That is the
expected result and the reason the specialization is safe: every baked constant
equals the value the renderer would have uploaded, so every expression in the
program evaluates identically.

`boundProgram` on each capture line is the program the renderer actually bound,
read back after the draw. It confirms the two arms really did link different
programs rather than measuring the same one twice.

### Depth, rain, history and reconstruction

These are not separately toggled equations; they are part of the single FINAL
output that compared bit-identical:

- **Depth** - `gl_FragDepth` is written by the same code path in both programs,
  and the depth-carrying composite is included in the compared target. The
  `PaLegacyHitDepth` arm, which is the only thing that alters hit depth, is
  baked off in the lean program and was inactive in both arms.
- **Rain** - the precipitation shaft domain is driven by `WorldTime`, which the
  capture pins identically for both arms; the shafts are part of the compared
  image and contributed zero changed pixels.
- **History / reconstruction** - the comparison itself bypasses history by
  design, so it isolates the marched result. History behaviour is preserved
  structurally instead: `HistoryValid` and `HistoryBlend` are untouched uniforms
  in both programs, and `renderedProductionFrame` - the flag that decides
  whether a frame becomes the next frame's history - now additionally requires
  `program.normalProductionOutput()`, so only the lean FINAL program can publish
  history. A diagnostic frame can no longer contaminate the temporal buffer.
  The one `DebugView` term in the history path,
  `(DebugView != 0 || HistoryBlend > 0.001)`, folds to `HistoryBlend > 0.001`
  under `DebugView = 0`, which is exactly what a FINAL frame evaluated before.

## 7. Performance

Same fixture, same run, 60 sampled frames per arm.

```
T161_PERF pose=PLAY_VIS_NEAR mode=ULTRA descriptors=10
          target=1920x1080 cloud=480x270 resolutionScale=0.2500
T161_PERF old=diagnostic_monolith cloudP50=110.9903 cloudP95=119.8828
T161_PERF new=lean_final          cloudP50=36.0653  cloudP95=38.4696
T161_PERF speedupP50=3.0775x speedupP95=3.1163x
```

| Arm | cloud p50 | cloud p95 | frame p50 | frame p95 |
|---|---|---|---|---|
| OLD - `diagnostic_monolith` | 110.9903 ms | 119.8828 ms | 112.2671 ms | 120.7202 ms |
| NEW - `lean_final` | **36.0653 ms** | **38.4696 ms** | **36.9509 ms** | **40.9054 ms** |
| Speedup | **3.0775x** | **3.1163x** | 3.038x | 2.951x |

**The gain is fully retained: 3.0775x against the experiment's 2.984x, or 103%
of it.** This is not a collapsed translation; the productionized split is
marginally better than the experiment measured.

One difference from `e301494` must be recorded honestly. The experiment's
monolithic arm was 216.15 ms p50 on its fixture; this tree's monolithic arm is
110.99 ms p50. The *absolute* baselines are not comparable across the two
branches and runs - and 110.99 ms is the figure consistent with T139's recorded
representative Ultra baseline of ~100.28 ms p50 on this ladder, so this tree's
old arm is the credible one. The transferable quantity is the ratio between two
arms measured back to back on one fixture in one run, and that ratio reproduced.

`remainderP50` also fell, 1.2768 ms to 0.8856 ms, and composite cost was
unchanged (0.0901 -> 0.0860 ms p50), which is what a change confined to the
raymarch program should look like.

## 8. Diagnostic campaign coverage

No campaign source changed. Every diagnostic sets its selector through
`VolumetricCloudDebugConfig` or one of the statics the eligibility predicate
reads, so each campaign automatically links the program that still contains its
code. The periodic render status now reports the program each frame bound
(`cloudProgram=`), which is how the switching below was observed.

### Ordinary rendering selects the lean program

In the unpinned autorun - no override, `finalProgram=auto` - the status samples
split by what the driver was doing:

| Run | `lean_final` | `diagnostic_monolith` |
|---|---|---|
| Suite + trace autorun | 28 | 7 |
| Ray trace + T098 capture set | 86 | 16 |

In the first run all seven monolith samples fall between 22:30:00 and 22:31:27,
inside the T121--T123 suite window that began at 22:29:40. That is the intended
behaviour: the suite's `T121_OFF` / `T122_OFF` arms need the dormant code, so
those frames link the monolith, while the suite's interleaved production
baseline frames link the lean program. The switch is per frame and automatic.

### Campaigns exercised end to end

| Campaign | Uniforms it needs | Program | Outcome |
|---|---|---|---|
| T121--T123 controlled optimization suite | `PaDiagnosticOptimizationMode` | monolith on OFF arms | `suite complete after 2556 frames` |
| T128 centre-line material trace | `DebugView 21`, `StormTrace*` | monolith | `trace complete` |
| T098 production ray trace | `PaRayTraceMode`, `PaRayTraceNdc`, `PaRayTraceFragCoord` | monolith | `T098_RAYTRACE_REPORT` produced, full waist/transport record |
| T098 capture set | `DebugView 1-3`, `PaLegacyHitDepth`, `PaLegacyFinePromotion` | monolith | 29 shots, `outcome=complete` |
| T161 program A/B | both, explicitly pinned | both | complete, section 6 |

The 29 T098 shots include exactly the arms that exercise the compiled-out
uniforms: `A_SIDE_CURRENT_ONLY`, `B_SIDE_HISTORY_ONLY`,
`C_SIDE_HISTORY_REJECTION` (debug views 1-3), `D_SIDE_LEGACY_HIT_DEPTH` and
`E_FAR_LEGACY_HIT_DEPTH` (`PaLegacyHitDepth`), and the four
`P_*_LEGACY_PROMOTION` / `P_*_CORRECTED_PROMOTION` pairs
(`PaLegacyFinePromotion`). Each ran and reported its controls
(`diagnosticStepBudget=0 legacyHitDepth=... legacyPromotion=...`), so the
diagnostic program still carries every path T161 removed from FINAL.

Nothing was deleted to make FINAL smaller.

### T098a

T098a is a property of the rendered image. The T161 A/B shows the lean and
monolithic programs produce **bit-identical** output - 0 changed pixels, maximum
error exactly 0.0, identical digest - so T098a cannot have moved: the image it
grades is the same image, bit for bit. The T098 capture infrastructure that
produces its evidence was additionally re-run in full and completed, confirming
the campaign remains runnable against the split.

## 9. Fallback safety - proven, not asserted

The guard was tested by fault injection rather than argued. The generated lean
program in `build/resources/main` was appended with deliberately invalid GLSL
and the client run with `-x generateLeanFinalShader -x processResources` so the
corruption survived to load time.

```
[Render thread/ERROR] [VolumetricClouds] lean FINAL program
  projectatmosphere:cloud_atmosphere_volume_final failed to load; the volumetric
  pass will session-disable rather than silently render FINAL with the
  diagnostic program
[Render thread/INFO]  [VolumetricClouds] shader programs registered
...
[Render thread/ERROR] [VolumetricClouds] render exception; volumetric pass
  disabled for this session
net.Gabou.projectatmosphere...VolumetricCloudRenderer$LeanFinalProgramUnavailableException:
  lean FINAL cloud program (cloud_atmosphere_volume_final) failed to link;
  refusing to fall back to the diagnostic monolith
    at ...VolumetricCloudRenderer.render(VolumetricCloudRenderer.java:282)
    at ...VolumetricCloudRenderHook.renderFrame(...)
```

Three things are confirmed by that run:

1. The lean program's failure did **not** abort registration of the other cloud
   programs - `shader programs registered` still ran.
2. The FINAL frame refused to draw and session-disabled through the established
   native fallback, naming the cause.
3. **`cloudProgram=diagnostic_monolith` appears zero times in the entire run.**
   The monolith was never silently substituted, which is the specific
   regression this guard exists to prevent: it would have produced a correct
   image at the pre-T161 cost, passing every image check while giving back the
   whole gain.

The injected corruption was reverted afterwards.

## 10. check / build

```
./gradlew check build
BUILD SUCCESSFUL in 4m 12s

PHASE4R_RESULT|T111 production storm shader compiles|PASSED|invariant satisfied
PHASE4R_RESULT|T161 lean FINAL shader specializes and compiles|PASSED|invariant satisfied
```

## 11. Banking

| Criterion | Result |
|---|---|
| Lean FINAL actually selected during normal rendering | **yes** - `cloudProgram=lean_final` on ordinary frames, unpinned |
| Dormant diagnostic state compile-time absent from FINAL | **yes** - 19 uniforms baked, 18 dropped from the JSON, `OracleIntervalSampler` gone from the linked program |
| Image semantics identical | **yes** - 0/129,600 changed pixels, max error 0.0, identical digest |
| T098a green | **yes** - image is bit-identical, and the T098 capture set re-ran complete |
| Diagnostics available through specialized programs | **yes** - suite, T128 trace, T098 ray trace and 29-shot capture set all completed |
| Fallback safety proven | **yes** - fault-injected; session-disabled, monolith never substituted |
| Substantial performance gain retained | **yes** - 3.0775x p50, against the experiment's 2.984x |
| check / build passes | **yes** |

**T161 is banked.**

### Scope note

This work was implemented on `worktree-t098-production-ray-trace`, not on
`Forge-1.20.1`. T161's stated prerequisites and its entire validation harness
live here: T139 and T160 are complete on this branch, the shader carries all 19
diagnostic uniform groups, and `StormT135PerformanceProfile`,
`StormFixtureVisibility` / PLAY_VIS_NEAR and the T098a evidence set exist only
here. On `Forge-1.20.1` the shader carries 6 of the 19 groups and none of the
harness, so none of the banking criteria above could have been produced there.

### What this does not claim

The result establishes execution-context specialization as a dominant cost
class. It does **not** identify the hardware mechanism. Register pressure,
occupancy and latency hiding remain hypotheses until counters are available;
T161 measured that the cost is real and where it comes from, not which unit
pays it.
