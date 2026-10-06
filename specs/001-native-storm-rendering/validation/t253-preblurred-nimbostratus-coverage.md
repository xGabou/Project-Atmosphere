# T253 - Pre-blurred nimbostratus coverage

Branch `quality/t217-deep-interior-lighting`, from `f7b1e6b5` (T252). Evidence: `validation/t253-campaign/t253-results.txt`.

**Result: CASE B.** The pre-blur is exact to the renderer's noise floor and a clear win on every pose (median
-0.4 ms on side / close / low-angle, -1.8..-2.1 ms under the deck; mean per pose 6.20 -> 5.39 ms). The under-deck
view still exceeds 10 ms on three of four instances (9.97 / 11.14 / 10.29 / 11.86 ms). The pre-blur is kept; the
remaining cost is T249's underside workload (section 13), and T249 morphology / lighting were not touched.

## 1. Coverage path (traced)

T248 averaged nine taps of `saturate(sampleWeather(p.xz + d * 40).r * CoverageMul)` (d in {-1,0,1}^2) per
nimbostratus density sample; `sampleWeather` is bilinear with a map-border `edgeFade`. T252 skipped the eight
neighbours where the sample is already zero (exact). The averaged value `nsCoverage` feeds the deck rim taper
(`nsRimThin`), the envelope, T249's fractus depth (`smoothstep(0.03, 0.35, nsCoverage)`) and hang envelope. Light
taps (consumers 2, 9) and the field build keep the local value.

## 2. Design

* One full-screen pass `cloud_coverage_blur` writes the same nine-tap mean - per tap `saturate(r * edgeFade *
  CoverageMul)`, the map's out-of-range taps 0 - into a 512 x 512 RGBA16F target (`VolumetricCloudRenderTargets.
  prepareNsCoverageTarget`, linear, clamped) with the weather map's extent and origin, i.e. 8-block texels: the
  40-block offsets are whole texels and the kernel support is unchanged (40 blocks).
* World-space stable: the target shares the weather map's origin, which T250 snaps to the 16-block field lattice, so
  a re-centre moves both maps by whole texels together - no new phase, no seam.
* The per-tap clamp must be inside the pass (CoverageMul is 1.25 at runtime: taps above 0.8 coverage clamp), so
  the multiplier is a pass input.
* Rebuild rule (`CloudWeatherMapRenderer.refreshNsCoverage`, called by the volume renderer before the march target
  is bound): when the weather raster was re-baked (`cacheMisses` generation), CoverageMul changed, or either texture
  was recreated. Never stale: the volume draw always follows the refresh in the same frame.
* Volume shader: inside the same consumer / zero-sample gate, `nsCoverage = texture(NsCoverageSampler, (p.xz -
  WeatherOrigin) / WeatherExtent).r` - one fetch instead of eight `sampleWeather` calls.

## 3. Format and filtering

RGBA16F (only r used; the existing `createHalfFloatMap` helper), `GL_LINEAR`, clamp to edge. Half float holds the
[0,1] mean to ~5e-4. Bilinear interpolation of the pre-averaged map equals the nine-tap average of bilinear taps
wherever the per-tap clamp and the border fade are inactive (both piecewise-linear on the 8-block lattice at the
512 weather-map preset); where a tap clamps, the difference is sub-texel and below the noise floor (section 5).

## 4. Implementation (one production path)

| file | change |
|---|---|
| `shaders/core/cloud_coverage_blur.{json,fsh}` | new pass (vertex `cloud_volume`, `WeatherMapSampler`, `WeatherExtent`, `CoverageMul`) |
| `VolumetricCloudShaders` | registers `cloud_coverage_blur`, `coverageBlurShader()` |
| `VolumetricCloudRenderTargets` | `prepareNsCoverageTarget()`, `nsCoverageTextureId()`, released with the other maps |
| `CloudWeatherMapRenderer` | `refreshNsCoverage(weatherTarget, coverageMul)`, `nsCoverageBuilds()` |
| `VolumetricCloudRenderer` | refresh before the march; `NsCoverageSampler` bound on unit 18 in `bindManualTextures`, unbound after |
| `CloudTextureUnitContract` | `NS_COVERAGE_UNIT = 18` = `MAX_PA_TOUCHED_UNIT` (18 -> 19 required fragment units), contiguity check |
| `cloud_atmosphere_volume.fsh` | `uniform sampler2D NsCoverageSampler`; the nine-tap loop replaced by one fetch |
| `StormVolumetricGeometrySandbox` | `NsCoverageSampler` in the manual-sampler list (JSON must not name it); unit 18 pinned |

## 5. Output equivalence (seed 5000, 4 instances x side / close / below / low_angle)

Same session, monolith arms on identical frames: M (pre-blur), M2 (pre-blur repeat = noise floor), O (the old
nine-tap loop, temporary `#ifdef PA_SHAPE_EXPERIMENT` arm, removed). Arms are shot in order M, M2, O, so the fair
pair is adjacent M2-O against M-M2, measured on the cloud mask:

| metric (16 still poses) | M-M2 (noise) | M2-O (old vs new) |
|---|---|---|
| median RMS luminance, cloud pixels | 2.30 | 2.14 |
| mean RMS, cloud pixels | 6.07 | 2.90 |
| mean silhouette IoU | 0.969 | 0.991 |

Silhouette (IoU), mean brightness over the mask (M-O |d| <= 1.7 on 15 / 16 poses), deck top row (0 px on 15 / 16;
-3 px on i2 low_angle, whose own M-M2 IoU is 0.73 because terrain streamed into that frame),
underside and tail extents agree within the noise floor. The few outliers in the full-frame metric are terrain
chunks that streamed in between arms and sky drift (`t253_diff_nimbostratus_i2_side.png`); the one cloud-mask
outlier (i2 / i3 side, rain virga under the deck) fades monotonically M -> M2 -> O with capture time - rain-time
drift, not the coverage path (`t253_crop_nimbostratus_i3_side.png`).

## 6. Morphology invariance

Deck height (top row unchanged), underside / fractus depth, tail extent and the T248 rim taper are unchanged
(section 5). `cloudMorphologyTopologySandbox` and `stormMorphologySandbox` pass unchanged.

## 7. Grid and motion stability

`CG_MOTION` sequences (6 frames per instance, across re-centres): frame-to-frame RMS M 3.90 / 7.71 / 4.64, O 3.86 /
7.68 / 4.72 - identical motion behaviour; per-frame M-O 1.89 / 1.31 / 1.75 vs M-M2 2.07 / 1.49 / 1.50. The pre-blur
shares the weather map's T250-snapped origin, so it cannot introduce a phase pop.

## 8. Performance, build vs build

Same Java (T253) in both builds; only the volume shader differs (H = `f7b1e6b5`, T = T253). Ten sessions
alternating h1 t1 f1 h2 t2 f2 h3 t3 h4 t4 (f = T plus a GPU timer on the blur pass). Lean FINAL, 120 presented frames
per pose. Session GPU speed varies by up to 15 % (HEAD drifted slower later in the day), so per-pose **medians over
sessions** are reported (4 H, 6 T):

| pose | H (HEAD) | T (T253) | delta |
|---|---|---|---|
| side (4 instances) | 3.42-4.54 | 3.19-4.10 | -0.23..-0.43 |
| close | 3.46-5.30 | 3.13-4.77 | -0.27..-0.52 |
| low_angle | 3.28-4.15 | 2.72-3.58 | -0.46..-0.57 |
| **below** | **11.78 / 13.27 / 12.19 / 13.65** | **9.97 / 11.14 / 10.29 / 11.86** | **-1.8..-2.1** |
| mean per pose (16) | 6.20 | 5.39 | -0.81 (-13 %) |

Every pose is faster in T by median; per-session sums H 92.8-108.1, T 82.4-97.2 ms. Adjacent pairs (16-pose sums):
h1/t1 -8.4, h2/t2 +2.5, h3/t3 -17.5, h4/t4 -23.0 ms - t2 (and f1) were slow sessions (their SPAWN pose ran at HEAD
speed too), so one pair alone would not resolve the saving. Workload counters are equal between builds (under-deck primary
density 18.1-21.4 M, light taps 41-55 M, material runs within 2 %), so the saving is per call.

**Blur pass, separately:** 0.0153 / 0.0156 ms GPU per build (clear + draw, 26 k samples each in f1 / f2). The weather
raster re-bakes on almost every frame in these scenes (every refresh matched exactly one new weather generation), so
the pass runs ~once per frame: 0.015 ms against a 0.4-2.1 ms raymarch saving. Not inside `lastGpuMilliseconds`
(which times only the march draw) - reported here instead.

## 9. Shader and binding impact

Main monolith and lean FINAL: one sampler declaration, one fetch replacing a loop. JSON unchanged (the sampler is
bound manually; the JSON list is at Minecraft's 12-sampler limit). Required fragment texture units 18 -> 19
(`hasTextureUnitCapacity` checks it at start-up; log: `fragment texture units available=32 required=19`). Shader reload: the pass is registered with
the other cloud shaders; targets are released with the other weather maps and recreated on demand, and the rebuild
rule keys on texture ids, so a reload or resize rebuilds before use. Lean FINAL compiled in every timing session; all
production variants compile in the sandbox.

## 10. Storm regression

t208 storm benchmark (lean FINAL), seven sessions sh1 st1 sh2 st2 st3 sh3 st4 (sh = HEAD shader, st = T253):

| pose | HEAD raw phase ms | T253 raw phase ms | HEAD total ms | T253 total ms |
|---|---|---|---|---|
| SIDE | 3.76 / 3.84 / 3.62 | 3.79 / 3.67 / 3.84 / 3.93 | 5.55 / 5.63 / 5.78 | 5.86 / 5.87 / 5.64 / 5.72 |
| PLAY_VIS_NEAR | 2.53 / 2.42 / 2.89 | n/a (st1: route lost its cells) / 2.67 / 2.34 / 2.36 | 4.46 / 4.34 / 4.95 | 4.68 / 4.24 / 3.95 |

Raw march flat. The total moves with the field build: `fieldMs` / `gateBuildMs` sit per session at either 0.86 / 0.12
or 1.21-1.26 / 0.33 ms in every build - st1 and st2 landed high (5.86 / 5.87 total), sh3 high on HEAD (5.78), st3 / st4
low (5.64 / 5.72) - the same bimodality as T252's builds A, B and C. No storm regression (the SIDE sky includes nimbostratus; its raw march is unchanged), and
the blur pass is outside these timers (0.015 ms).

## 11. Failure cases

A (under 10 ms): no - one of four under-deck instances. **B (clear win, still over budget): yes** - kept, stopped.
C (visually different): no (section 5). D (pass overhead erases the gain): no (0.015 ms vs >= 0.4 ms).

## 12. Checks

`campaignGate` 0, `check` 0, `stormMorphologySandbox` 0, `cloudMorphologyTopologySandbox` 0,
`stormVolumetricGeometrySandbox` 0 (final tree, one run: `check-build-t253.log`); T250 grid checks are part of `check`.

## 13. Remaining under-deck cost (stopped here - next steps change T249 appearance)

Under-deck pose (camera 90 blocks under the deck base, looking up), T253 vs pre-T248 (T252 build A, same seed and
poses, different day):

| instance | A pre-T248 ms | T253 ms | light taps A -> T253 | primary density A -> T253 | material rays A -> T253 |
|---|---|---|---|---|---|
| 1 | 7.54 | 9.97 | 32.4 -> 42.2 M (+30 %) | 15.9 -> 18.3 M (+15 %) | 633 -> 759 k (+20 %) |
| 2 | 8.16 | 11.14 | 37.9 -> 50.8 M (+34 %) | 17.4 -> 20.4 M (+17 %) | 758 -> 914 k (+21 %) |
| 3 | 8.18 | 10.29 | 33.8 -> 46.0 M (+36 %) | 17.5 -> 20.3 M (+16 %) | 679 -> 827 k (+22 %) |
| 4 | 8.70 | 11.86 | 41.5 -> 55.3 M (+33 %) | 18.1 -> 21.4 M (+18 %) | 750 -> 905 k (+21 %) |

T248's per-call code cost is gone (one fetch). The remaining +2.1..+3.2 ms over pre-T248 is T249's fuller fractus
underside: about a third more lit light-cone taps and a fifth more material rays, at an unchanged per-tap cost.
Options, each of which changes accepted T249 appearance or lighting and therefore needs a decision: a cheaper light
cone for the hanging fractus layer (fewer taps below the deck base), a shallower fractus depth, or accepting
~10-12 ms for this view at 0.75 scale.

## 14. Limitations

* Session-to-session GPU speed varies up to 15 %; medians over 4 / 6 sessions are robust to it, single pairs are not.
* The pre-T248 comparison in section 13 uses T252's build-A numbers from a different day.
* Exactness argument holds bit-for-bit only at the 512 weather-map preset; at 256 / 384 the pre-blur samples the
  same bilinear field at 8-block spacing (sub-texel interpolation difference, below the 16-block weather texel).
* The gallery runs at 0.75 render scale (1200 x 675 cloud target); the 1080p under-deck cost is higher.
